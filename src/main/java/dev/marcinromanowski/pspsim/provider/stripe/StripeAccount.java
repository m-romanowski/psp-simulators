package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record StripeAccount(
    TestRunId testRunId,
    String accountId,
    String secretKey,
    StripeCaptureWindow captureWindow,
    StripeWebhookSigning webhookSigning,
    StripeRefundOutcome refundOutcome) {

  static final String SECRET_KEY_PROPERTY = "secretKey";

  static final String TEST_MODE_PREFIX = "sk_test_";

  static final int LONGEST_SECRET_KEY_THE_STORE_HOLDS = 255;

  static final String A_SECRET_KEY_STRIPE_WOULD_TAKE = TEST_MODE_PREFIX + "51Simulator";

  static final ProviderAccountFields ACCOUNT_FIELDS =
      new ProviderAccountFields(
          StripeApi.STRIPE,
          List.of(
              new ConfigurableField(
                  SECRET_KEY_PROPERTY,
                  "test-mode secret key",
                  "The "
                      + TEST_MODE_PREFIX
                      + " key, which is the only thing a Stripe request carries the account it"
                      + " belongs to on. No two accounts of one test run may name the same one.",
                  true,
                  true,
                  List.of(),
                  A_SECRET_KEY_STRIPE_WOULD_TAKE),
              ConfigurableField.aDuration(
                  StripeCaptureWindow.SETTING,
                  "how long an intent is capturable",
                  "How long an uncaptured intent of this account stays capturable. A form states"
                      + " it as a number and a unit; a control API call writes the same span as an"
                      + " ISO-8601 duration such as "
                      + StripeCaptureWindow.AN_EXAMPLE_WINDOW
                      + ". Stripe names seven days as its default and never names the set it draws"
                      + " from, so this simulator will not adopt that number on a fixture's behalf"
                      + " and refuses an account that leaves this empty.",
                  true,
                  StripeCaptureWindow.AN_EXAMPLE_WINDOW),
              new ConfigurableField(
                  StripeRefundOutcome.SETTING,
                  "which status a refund reaches",
                  "Which status a refund of this account reaches. Leaving it empty reaches"
                      + " Stripe's own default.",
                  false,
                  false,
                  StripeRefundStatus.acceptedValues()),
              new ConfigurableField(
                  StripeWebhookSigning.SECRET_SETTING,
                  "webhook signing secret",
                  "The "
                      + StripeWebhookSigning.PUBLISHED_PREFIX
                      + " secret this account's delivered events are signed with. Leaving it"
                      + " empty signs nothing, and the two settings beside it are then refused.",
                  false,
                  true,
                  List.of(),
                  StripeWebhookSigning.A_SECRET_STRIPE_WOULD_TAKE),
              new ConfigurableField(
                  StripeWebhookSigning.EMISSION_SETTING,
                  "which signature an event carries",
                  "Which signature a delivered event of this account carries. Stripe sends"
                      + " Stripe-Signature only where a signing secret is configured, so naming"
                      + " this on an account with no webhook signing secret is refused.",
                  false,
                  false,
                  StripeSignatureEmission.acceptedValues()),
              ConfigurableField.aDuration(
                  StripeWebhookSigning.AGE_SETTING,
                  "how old the signed timestamp is",
                  "How far in the past the timestamp a delivered event is signed under is dated."
                      + " A form states it as a number and a unit; a control API call writes the"
                      + " same span as an ISO-8601 duration such as "
                      + StripeWebhookSigning.AN_EXAMPLE_AGE
                      + ". Like the signature beside it, naming this on an account with no webhook"
                      + " signing secret is refused.",
                  false,
                  StripeWebhookSigning.AN_EXAMPLE_AGE)));

  static final Set<String> CONFIGURATION_KEYS = Set.copyOf(ACCOUNT_FIELDS.keys());

  static final String SECRET_KEY_IS_THE_ACCOUNT =
      SECRET_KEY_PROPERTY
          + " is required by provider stripe, because the secret key is the only thing a Stripe"
          + " request carries the account it belongs to on: it is the HTTP Basic username, or the"
          + " bearer token, and no merchant id, terminal id or key pair is published anywhere on"
          + " this contract";

  static final String SECRET_KEY_IS_A_TEST_MODE_KEY =
      SECRET_KEY_PROPERTY
          + " must begin with "
          + TEST_MODE_PREFIX
          + ", the prefix Stripe publishes for a test-mode secret key; this simulator answers no"
          + " live-mode traffic, so a key of any other published prefix would authenticate a"
          + " request no test should be able to make";

  static final String SECRET_KEY_IS_KEPT_WITH_EVERY_ACCOUNT =
      SECRET_KEY_PROPERTY
          + " must be at most "
          + LONGEST_SECRET_KEY_THE_STORE_HOLDS
          + " characters, which is the simulator's bound on the column every request of the"
          + " account is matched against and not a length the vendor publishes";

  static StripeAccount of(String propertyPrefix, TestRunId testRunId, ConfiguredAccount account) {
    Map<String, String> configuration = account.configuration();
    return new StripeAccount(
        testRunId,
        account.accountId(),
        theSecretKey(propertyPrefix, configuration.get(SECRET_KEY_PROPERTY)),
        StripeCaptureWindow.of(propertyPrefix, configuration.get(StripeCaptureWindow.SETTING)),
        StripeWebhookSigning.of(propertyPrefix, configuration),
        StripeRefundOutcome.of(propertyPrefix, configuration.get(StripeRefundOutcome.SETTING)));
  }

  DisclosedAccount disclosure() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put(SECRET_KEY_PROPERTY, List.of(secretKey));
    disclosed.put(StripeCaptureWindow.SETTING, List.of(captureWindow.storedForm()));
    disclosed.put(StripeRefundOutcome.SETTING, List.of(refundOutcome.storedForm()));
    String signingSecret = webhookSigning.secret();
    if (signingSecret == null) {
      disclosed.put(StripeWebhookSigning.SECRET_SETTING, List.of());
      disclosed.put(StripeWebhookSigning.EMISSION_SETTING, List.of());
      disclosed.put(StripeWebhookSigning.AGE_SETTING, List.of());
    } else {
      disclosed.put(StripeWebhookSigning.SECRET_SETTING, List.of(signingSecret));
      disclosed.put(
          StripeWebhookSigning.EMISSION_SETTING, List.of(webhookSigning.emission().name()));
      disclosed.put(StripeWebhookSigning.AGE_SETTING, List.of(webhookSigning.storedAge()));
    }
    return new DisclosedAccount(
        accountId, Redaction.redact(disclosed, ACCOUNT_FIELDS.secretKeys()));
  }

  private static String theSecretKey(String propertyPrefix, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + SECRET_KEY_IS_THE_ACCOUNT);
    }
    if (!configured.startsWith(TEST_MODE_PREFIX)) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + SECRET_KEY_IS_A_TEST_MODE_KEY);
    }
    if (configured.codePointCount(0, configured.length()) > LONGEST_SECRET_KEY_THE_STORE_HOLDS) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + SECRET_KEY_IS_KEPT_WITH_EVERY_ACCOUNT);
    }
    return configured;
  }
}
