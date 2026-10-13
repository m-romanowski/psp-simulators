package dev.marcinromanowski.pspsim.provider.worldline;

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
import java.util.function.Supplier;
import java.util.regex.Pattern;

record WorldlineMerchant(
    TestRunId testRunId,
    String accountId,
    String merchantId,
    String apiKey,
    String apiSecret,
    WorldlineSignedPathVersion signedPathVersion,
    WorldlineCaptureLanding captureLanding,
    WorldlineAuthentication authentication) {

  static final int LONGEST_MERCHANT_ID_THE_STORE_HOLDS = 128;

  static final String MERCHANT_ID_PROPERTY = "merchantId";

  static final String API_KEY_PROPERTY = "apiKey";

  static final String API_SECRET_PROPERTY = "apiSecret";

  static final String A_PATH_SEGMENT = "[^/?#]+";

  private static final Pattern ONE_PATH_SEGMENT = Pattern.compile(A_PATH_SEGMENT);

  static final String A_MERCHANT_ID_WORLDLINE_PUBLISHES = "merchant-1";

  static final String AN_API_KEY_WORLDLINE_WOULD_TAKE = "worldline-api-key";

  static final String AN_API_SECRET_WORLDLINE_WOULD_TAKE = "worldline-api-secret";

  static final ProviderAccountFields ACCOUNT_FIELDS =
      new ProviderAccountFields(
          WorldlineApi.WORLDLINE,
          List.of(
              new ConfigurableField(
                  MERCHANT_ID_PROPERTY,
                  "merchant id (PSPID)",
                  "The PSPID every v2 route of this contract carries as its first path segment"
                      + " after the version, so a request of an account with none could address no"
                      + " route at all. No two accounts of one test run may name the same one.",
                  true,
                  false,
                  List.of(),
                  A_MERCHANT_ID_WORLDLINE_PUBLISHES),
              new ConfigurableField(
                  API_KEY_PROPERTY,
                  "api key",
                  "The middle field of \"Authorization: GCS v1HMAC:<your-api-key>:"
                      + "<base64-encoded-signature>\", which is what a signed call names the"
                      + " account on. No two accounts of one test run may name the same one.",
                  true,
                  false,
                  List.of(),
                  AN_API_KEY_WORLDLINE_WOULD_TAKE),
              new ConfigurableField(
                  API_SECRET_PROPERTY,
                  "api secret",
                  "The key the canonical string is hashed with using HMAC-SHA256 and UTF-8 before"
                      + " the result is Base64-encoded.",
                  true,
                  true,
                  List.of(),
                  AN_API_SECRET_WORLDLINE_WOULD_TAKE),
              new ConfigurableField(
                  WorldlineSignedPathVersion.SETTING,
                  "which path version is signed",
                  "Which of the two path versions worldline publishes inside the signed string is"
                      + " the one a request of this account must have signed. Leaving it empty"
                      + " expects worldline's own default.",
                  false,
                  false,
                  WorldlineSignedPathVersion.acceptedValues()),
              new ConfigurableField(
                  WorldlineCaptureLanding.SETTING,
                  "where a capture lands",
                  "Which of the three landings worldline publishes for a successful capture this"
                      + " account answers with. Leaving it empty answers with worldline's own"
                      + " default.",
                  false,
                  false,
                  WorldlineCaptureLanding.acceptedValues()),
              new ConfigurableField(
                  WorldlineAuthentication.SETTING,
                  "what a call is authenticated by",
                  "Whether the HMAC signature of every call of this account is verified or only"
                      + " the apiKey is. Leaving it empty verifies the signature.",
                  false,
                  false,
                  WorldlineAuthentication.acceptedValues())));

  static final Set<String> CONFIGURATION_KEYS = Set.copyOf(ACCOUNT_FIELDS.keys());

  static final String MERCHANT_ID_IS_IN_EVERY_PATH =
      MERCHANT_ID_PROPERTY
          + " is required by provider worldline, because every route of this contract spells it"
          + " into the path — \"/v2/merchantId/hostedcheckouts\" — and a request of an account"
          + " with none could address no route at all";

  static final String API_KEY_IS_THE_ACCOUNT =
      API_KEY_PROPERTY
          + " is required by provider worldline, because the Authorization header carries the"
          + " account on it and nothing else on a signed call names one";

  static final String API_SECRET_IS_WHAT_THE_SIGNATURE_IS_MADE_WITH =
      API_SECRET_PROPERTY
          + " is required by provider worldline, because the published instruction is to \"Hash"
          + " the signature subject (string-to-hash) using HMAC-SHA256 (UTF-8 encoding) with your"
          + " API Secret\", and an account with none could never produce a signature to compare"
          + " against";

  static final String MERCHANT_ID_IS_ONE_PATH_SEGMENT =
      MERCHANT_ID_PROPERTY
          + " must match "
          + A_PATH_SEGMENT
          + ", because provider worldline puts it in the path and a value carrying a slash, a"
          + " question mark or a hash would be configured here and then address a different route"
          + " from the one it named. The bound is this simulator's own: worldline publishes no"
          + " shape for a merchantId anywhere";

  static final String MERCHANT_ID_IS_KEPT_WITH_EVERY_OBJECT =
      MERCHANT_ID_PROPERTY
          + " must be at most "
          + LONGEST_MERCHANT_ID_THE_STORE_HOLDS
          + " characters, because this simulator keeps the merchant of every payment and of every"
          + " tokenization session in a column that wide, and a wider one is accepted here and"
          + " then refused by that store on every request of the account. The bound is the"
          + " simulator's own: provider worldline publishes no length for this field at all";

  static WorldlineMerchant of(
      String propertyPrefix, TestRunId testRunId, ConfiguredAccount account) {
    Map<String, String> configuration = account.configuration();
    String merchantId = required(propertyPrefix, configuration, MERCHANT_ID_PROPERTY);
    String apiKey = required(propertyPrefix, configuration, API_KEY_PROPERTY);
    String apiSecret = required(propertyPrefix, configuration, API_SECRET_PROPERTY);
    if (!ONE_PATH_SEGMENT.matcher(merchantId).matches()) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + MERCHANT_ID_IS_ONE_PATH_SEGMENT);
    }
    if (merchantId.length() > LONGEST_MERCHANT_ID_THE_STORE_HOLDS) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + MERCHANT_ID_IS_KEPT_WITH_EVERY_OBJECT);
    }
    return new WorldlineMerchant(
        testRunId,
        account.accountId(),
        merchantId,
        apiKey,
        apiSecret,
        refused(
            () ->
                WorldlineSignedPathVersion.of(
                    propertyPrefix + WorldlineSignedPathVersion.SETTING,
                    configuration.get(WorldlineSignedPathVersion.SETTING))),
        refused(
            () ->
                WorldlineCaptureLanding.of(
                    propertyPrefix + WorldlineCaptureLanding.SETTING,
                    configuration.get(WorldlineCaptureLanding.SETTING))),
        refused(
            () ->
                WorldlineAuthentication.of(
                    propertyPrefix + WorldlineAuthentication.SETTING,
                    configuration.get(WorldlineAuthentication.SETTING))));
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
      case MERCHANT_ID_PROPERTY -> MERCHANT_ID_IS_IN_EVERY_PATH;
      case API_KEY_PROPERTY -> API_KEY_IS_THE_ACCOUNT;
      default -> API_SECRET_IS_WHAT_THE_SIGNATURE_IS_MADE_WITH;
    };
  }

  DisclosedAccount disclosure() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put(MERCHANT_ID_PROPERTY, List.of(merchantId));
    disclosed.put(API_KEY_PROPERTY, List.of(apiKey));
    disclosed.put(API_SECRET_PROPERTY, List.of(apiSecret));
    disclosed.put(WorldlineSignedPathVersion.SETTING, List.of(signedPathVersion.name()));
    disclosed.put(WorldlineCaptureLanding.SETTING, List.of(captureLanding.name()));
    disclosed.put(WorldlineAuthentication.SETTING, List.of(authentication.name()));
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
