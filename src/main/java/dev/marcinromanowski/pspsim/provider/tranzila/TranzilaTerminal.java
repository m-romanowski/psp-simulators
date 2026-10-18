package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

record TranzilaTerminal(
    TestRunId testRunId,
    String accountId,
    String terminalName,
    String appKey,
    String appSecret,
    TranzilaAccessToken accessToken,
    TranzilaAuthentication authentication,
    TranzilaVerificationApprovalCode verificationApprovalCode,
    Duration j5Validity) {

  static final String TERMINAL_NAME_PROPERTY = "terminalName";

  static final String APP_KEY_PROPERTY = "appKey";

  static final String APP_SECRET_PROPERTY = "appSecret";

  static final String A_TERMINAL_NAME_SHAPE = "^[a-z][a-z0-9]{2,15}$";

  private static final Pattern THE_TERMINAL_NAME_SHAPE = Pattern.compile(A_TERMINAL_NAME_SHAPE);

  static final String A_TERMINAL_NAME_TRANZILA_WOULD_TAKE = "myterminal";

  static final String AN_APP_KEY_TRANZILA_WOULD_TAKE = "tranzila-app-key";

  static final String AN_APP_SECRET_TRANZILA_WOULD_TAKE = "tranzila-app-secret";

  static final ProviderAccountFields ACCOUNT_FIELDS =
      new ProviderAccountFields(
          TranzilaApi.TRANZILA,
          List.of(
              new ConfigurableField(
                  TERMINAL_NAME_PROPERTY,
                  "terminal name",
                  "The terminal_name every create request carries in its body, which must name"
                      + " the terminal the app key belongs to. Lowercase letters and digits,"
                      + " starting with a letter, three to sixteen characters. No two accounts of"
                      + " one test run may name the same one.",
                  true,
                  false,
                  List.of(),
                  A_TERMINAL_NAME_TRANZILA_WOULD_TAKE),
              new ConfigurableField(
                  APP_KEY_PROPERTY,
                  "app key",
                  "The value of X-tranzila-api-app-key, which is what a call names the account"
                      + " on. No two accounts of one test run may name the same one.",
                  true,
                  false,
                  List.of(),
                  AN_APP_KEY_TRANZILA_WOULD_TAKE),
              new ConfigurableField(
                  APP_SECRET_PROPERTY,
                  "app secret",
                  "The secret the access token is an HMAC-SHA256 of, never sent on the wire.",
                  true,
                  true,
                  List.of(),
                  AN_APP_SECRET_TRANZILA_WOULD_TAKE),
              new ConfigurableField(
                  TranzilaAccessToken.SETTING,
                  "how the access token is built",
                  "Which of the two published constructions of X-tranzila-api-access-token a"
                      + " call of this account must use. Leaving it empty expects the one the"
                      + " authentication pages publish.",
                  false,
                  false,
                  TranzilaAccessToken.acceptedValues()),
              new ConfigurableField(
                  TranzilaAuthentication.SETTING,
                  "what a call is authenticated by",
                  "Whether the access token of every call of this account is verified or only"
                      + " the app key is. Leaving it empty verifies the access token.",
                  false,
                  false,
                  TranzilaAuthentication.acceptedValues()),
              new ConfigurableField(
                  TranzilaVerificationApprovalCode.SETTING,
                  "code of an approved J2 or J5",
                  "Which processor code an approved verification of this account answers."
                      + " Leaving it empty answers 777, the published code table's.",
                  false,
                  false,
                  TranzilaVerificationApprovalCode.acceptedValues()),
              ConfigurableField.aDuration(
                  TranzilaJ5Validity.SETTING,
                  "how long a J5 hold lives",
                  "How long a J5 of this account can be forced or reversed, on the run clock. A"
                      + " form states it as a number and a unit; a control API call writes the"
                      + " same span as an ISO-8601 duration such as "
                      + TranzilaJ5Validity.AN_EXAMPLE
                      + ". It must be longer than nothing and at most a year. Leaving it empty"
                      + " keeps a J5 for seven days.",
                  false,
                  TranzilaJ5Validity.AN_EXAMPLE)));

  static final Set<String> CONFIGURATION_KEYS = Set.copyOf(ACCOUNT_FIELDS.keys());

  static final String TERMINAL_NAME_IS_IN_EVERY_BODY =
      TERMINAL_NAME_PROPERTY
          + " is required by provider tranzila, because terminal_name is required on every"
          + " request of this contract and must name the terminal the app key belongs to";

  static final String APP_KEY_IS_THE_ACCOUNT =
      APP_KEY_PROPERTY
          + " is required by provider tranzila, because X-tranzila-api-app-key is the header a"
          + " call names its account on and nothing else on a call names one";

  static final String APP_SECRET_IS_WHAT_THE_TOKEN_IS_MADE_WITH =
      APP_SECRET_PROPERTY
          + " is required by provider tranzila, because the access token is an HMAC-SHA256 made"
          + " with it, and an account with none could never produce a token to compare against";

  static final String TERMINAL_NAME_IS_THE_PUBLISHED_SHAPE =
      TERMINAL_NAME_PROPERTY
          + " must match "
          + A_TERMINAL_NAME_SHAPE
          + ": lowercase letters and digits, starting with a letter, three to sixteen characters."
          + " The bound is this simulator's own, because provider tranzila prints terminal names"
          + " of that shape and publishes no rule for one, and the payment page puts the name in"
          + " its address";

  static TranzilaTerminal of(
      String propertyPrefix, TestRunId testRunId, ConfiguredAccount account) {
    Map<String, String> configuration = account.configuration();
    String terminalName = required(propertyPrefix, configuration, TERMINAL_NAME_PROPERTY);
    String appKey = required(propertyPrefix, configuration, APP_KEY_PROPERTY);
    String appSecret = required(propertyPrefix, configuration, APP_SECRET_PROPERTY);
    if (!THE_TERMINAL_NAME_SHAPE.matcher(terminalName).matches()) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + TERMINAL_NAME_IS_THE_PUBLISHED_SHAPE);
    }
    return new TranzilaTerminal(
        testRunId,
        account.accountId(),
        terminalName,
        appKey,
        appSecret,
        refused(
            () ->
                TranzilaAccessToken.of(
                    propertyPrefix + TranzilaAccessToken.SETTING,
                    configuration.get(TranzilaAccessToken.SETTING))),
        refused(
            () ->
                TranzilaAuthentication.of(
                    propertyPrefix + TranzilaAuthentication.SETTING,
                    configuration.get(TranzilaAuthentication.SETTING))),
        refused(
            () ->
                TranzilaVerificationApprovalCode.of(
                    propertyPrefix + TranzilaVerificationApprovalCode.SETTING,
                    configuration.get(TranzilaVerificationApprovalCode.SETTING))),
        TranzilaJ5Validity.of(
            propertyPrefix + TranzilaJ5Validity.SETTING,
            configuration.get(TranzilaJ5Validity.SETTING)));
  }

  private static String required(
      String propertyPrefix, Map<String, String> configuration, String property) {
    String value = configuration.get(property);
    if (value == null || value.isBlank()) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + theRefusalOfAMissing(property));
    }
    return value;
  }

  private static String theRefusalOfAMissing(String property) {
    return switch (property) {
      case TERMINAL_NAME_PROPERTY -> TERMINAL_NAME_IS_IN_EVERY_BODY;
      case APP_KEY_PROPERTY -> APP_KEY_IS_THE_ACCOUNT;
      default -> APP_SECRET_IS_WHAT_THE_TOKEN_IS_MADE_WITH;
    };
  }

  DisclosedAccount disclosure() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put(TERMINAL_NAME_PROPERTY, List.of(terminalName));
    disclosed.put(APP_KEY_PROPERTY, List.of(appKey));
    disclosed.put(APP_SECRET_PROPERTY, List.of(appSecret));
    disclosed.put(TranzilaAccessToken.SETTING, List.of(accessToken.name()));
    disclosed.put(TranzilaAuthentication.SETTING, List.of(authentication.name()));
    disclosed.put(
        TranzilaVerificationApprovalCode.SETTING, List.of(verificationApprovalCode.code()));
    disclosed.put(TranzilaJ5Validity.SETTING, List.of(j5Validity.toString()));
    return new DisclosedAccount(
        accountId, Redaction.redact(disclosed, ACCOUNT_FIELDS.secretKeys()));
  }

  private static <T> T refused(Supplier<T> reading) {
    try {
      return reading.get();
    } catch (IllegalArgumentException rejected) {
      throw new ProviderConfigurationRejectedException(String.valueOf(rejected.getMessage()));
    }
  }
}
