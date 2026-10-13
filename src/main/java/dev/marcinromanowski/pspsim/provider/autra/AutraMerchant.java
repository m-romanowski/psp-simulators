package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.AShapeSaidInWords;
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
import org.jspecify.annotations.Nullable;

record AutraMerchant(
    TestRunId testRunId,
    String accountId,
    String clientId,
    String clientSecret,
    String documentId,
    AutraOauthFailure oauthFailure,
    AutraOauthFieldCasing oauthFieldCasing,
    AutraRequestShape preAuthorizationShape,
    AutraRequestShape voidShape,
    String authorizerRejectedMessage,
    AutraMerchantNotFound merchantNotFoundStatus,
    AutraSaleDecline saleDecline,
    AutraAcquirerReport acquirerReport,
    @Nullable String webhookSecret) {

  static final int LONGEST_DOCUMENT_THE_STORE_HOLDS = 32;

  static final String CLIENT_ID_PROPERTY = "clientId";

  static final String CLIENT_SECRET_PROPERTY = "clientSecret";

  static final String DOCUMENT_ID_PROPERTY = "documentId";

  static final String DIGITS_ONLY_AS_WIDE_AS_THE_STORE_HOLDS =
      "[0-9]{1," + LONGEST_DOCUMENT_THE_STORE_HOLDS + "}";

  static final String A_DOCUMENT_AUTRA_PUBLISHES = "12345678000190";

  static final AShapeSaidInWords A_DOCUMENT =
      AShapeSaidInWords.of(DIGITS_ONLY_AS_WIDE_AS_THE_STORE_HOLDS, A_DOCUMENT_AUTRA_PUBLISHES);

  static final String A_CLIENT_ID_AUTRA_WOULD_TAKE = "autra-client-id";

  static final String A_CLIENT_SECRET_AUTRA_WOULD_TAKE = "autra-client-secret";

  static final ProviderAccountFields ACCOUNT_FIELDS =
      new ProviderAccountFields(
          AutraApi.AUTRA,
          List.of(
              new ConfigurableField(
                  CLIENT_ID_PROPERTY,
                  "client id",
                  "The first half of the Basic credential autra's token endpoint takes. The token"
                      + " endpoint reads the account out of that credential, and nothing else on"
                      + " the call names one. No two accounts of one test run may name the same"
                      + " one.",
                  true,
                  false,
                  List.of(),
                  A_CLIENT_ID_AUTRA_WOULD_TAKE),
              new ConfigurableField(
                  CLIENT_SECRET_PROPERTY,
                  "client secret",
                  "The second half of that Basic credential.",
                  true,
                  true,
                  List.of(),
                  A_CLIENT_SECRET_AUTRA_WOULD_TAKE),
              new ConfigurableField(
                  DOCUMENT_ID_PROPERTY,
                  "document id",
                  "The merchant's CPF or CNPJ, digits only, which every acquiring call of this"
                      + " contract carries. No two accounts of one test run may name the same"
                      + " one.",
                  true,
                  false,
                  List.of(),
                  A_DOCUMENT_AUTRA_PUBLISHES),
              new ConfigurableField(
                  AutraOauthFailure.SETTING,
                  "how a refused credential reads",
                  "Which of the two answers autra publishes a refused credential with this account"
                      + " answers. Leaving it empty answers with autra's own default.",
                  false,
                  false,
                  AutraOauthFailure.acceptedValues()),
              new ConfigurableField(
                  AutraOauthFieldCasing.SETTING,
                  "how the token response is spelt",
                  "Which of the two spellings autra publishes the token response in this account"
                      + " answers. Leaving it empty answers with autra's own default.",
                  false,
                  false,
                  AutraOauthFieldCasing.acceptedValues()),
              new ConfigurableField(
                  AutraRequestShape.PRE_AUTHORIZATION_SETTING,
                  "which pre-authorization shape",
                  "Which of the two published pre-authorization request shapes this account"
                      + " enforces. Leaving it empty enforces autra's own default.",
                  false,
                  false,
                  AutraRequestShape.acceptedValues()),
              new ConfigurableField(
                  AutraRequestShape.VOID_SETTING,
                  "which void shape",
                  "Which of the two published void request shapes this account enforces. Leaving"
                      + " it empty enforces autra's own default.",
                  false,
                  false,
                  AutraRequestShape.acceptedValues()),
              new ConfigurableField(
                  AutraAuthorizerMessage.SETTING,
                  "the AUTHORIZER_REJECTED msg",
                  "Which msg accompanies an AUTHORIZER_REJECTED decline from this account, written"
                      + " as a two-digit issuer code, a space, a hyphen, a space and the text."
                      + " Leaving it empty sends autra's own default.",
                  false,
                  false,
                  List.of(),
                  AutraAuthorizerMessage.DEFAULT),
              new ConfigurableField(
                  AutraMerchantNotFound.SETTING,
                  "the MERCHANT_NOT_FOUND status",
                  "Which of the three statuses autra publishes MERCHANT_NOT_FOUND at this account"
                      + " answers with. Leaving it empty answers with autra's own default.",
                  false,
                  false,
                  AutraMerchantNotFound.acceptedValues()),
              new ConfigurableField(
                  AutraSaleDecline.SETTING,
                  "how a declined sale reads",
                  "Which of the two answers autra publishes a declined card sale with this account"
                      + " answers. Leaving it empty answers with autra's own default.",
                  false,
                  false,
                  AutraSaleDecline.acceptedValues()),
              new ConfigurableField(
                  AutraAcquirerReport.SETTING,
                  "whether the acquirer names it",
                  "Whether the acquirer names the transaction it took for this account. Autra"
                      + " publishes that name as trackingNumber, repeats it as nsu for"
                      + " compatibility, and publishes it as absent when the acquirer reports"
                      + " none. Leaving it empty names it.",
                  false,
                  false,
                  AutraAcquirerReport.acceptedValues()),
              new ConfigurableField(
                  AutraWebhookSecret.SETTING,
                  "webhook secret",
                  "The tenant callback secret every acquiring webhook of this account is signed"
                      + " with, "
                      + AutraWebhookSecret.SHORTEST_SECRET
                      + " characters or more. Leaving it empty signs nothing.",
                  false,
                  true,
                  List.of(),
                  AutraWebhookSecret.A_SECRET_AUTRA_WOULD_TAKE)));

  static final Set<String> CONFIGURATION_KEYS = Set.copyOf(ACCOUNT_FIELDS.keys());

  static final String CLIENT_ID_IS_THE_ACCOUNT =
      CLIENT_ID_PROPERTY
          + " is required by provider autra, because the token endpoint reads the account out of"
          + " the Basic credential and nothing else on that call names one";

  static final String CLIENT_SECRET_IS_REQUIRED =
      CLIENT_SECRET_PROPERTY
          + " is required by provider autra, because the published refusal \"invalid"
          + " clientSecret\" is the answer to a secret that does not match a stored one, and an"
          + " account with none could never produce it";

  static final String DOCUMENT_ID_IS_THE_ACQUIRING_IDENTITY =
      DOCUMENT_ID_PROPERTY
          + " is required by provider autra, because documentId is the field every acquiring call"
          + " of this contract carries the merchant on, and the pre-authorization idempotency"
          + " scope is \"por merchant (documentId) dentro do seu tenant\"";

  static final String DOCUMENT_ID_IS_DIGITS_ONLY =
      A_DOCUMENT.theRefusalOf(DOCUMENT_ID_PROPERTY)
          + ". Provider autra publishes it as \"CPF ou CNPJ, sem mascara\", and a masked value"
          + " would be configured here and then match no request a client sends. The longest is"
          + " the simulator's own, because it keeps the merchant document of every payment and of"
          + " every order it claims in a column that wide, and a wider one accepted here would be"
          + " refused by that store on every request of the account: provider autra publishes no"
          + " length for this field at all";

  static AutraMerchant of(String propertyPrefix, TestRunId testRunId, ConfiguredAccount account) {
    Map<String, String> configuration = account.configuration();
    String clientId = required(propertyPrefix, configuration, CLIENT_ID_PROPERTY);
    String clientSecret = required(propertyPrefix, configuration, CLIENT_SECRET_PROPERTY);
    String documentId = required(propertyPrefix, configuration, DOCUMENT_ID_PROPERTY);
    if (!A_DOCUMENT.admits(documentId)) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + DOCUMENT_ID_IS_DIGITS_ONLY);
    }
    return new AutraMerchant(
        testRunId,
        account.accountId(),
        clientId,
        clientSecret,
        documentId,
        refused(
            () ->
                AutraOauthFailure.of(
                    propertyPrefix + AutraOauthFailure.SETTING,
                    configuration.get(AutraOauthFailure.SETTING))),
        refused(
            () ->
                AutraOauthFieldCasing.of(
                    propertyPrefix + AutraOauthFieldCasing.SETTING,
                    configuration.get(AutraOauthFieldCasing.SETTING))),
        refused(
            () ->
                AutraRequestShape.of(
                    propertyPrefix + AutraRequestShape.PRE_AUTHORIZATION_SETTING,
                    configuration.get(AutraRequestShape.PRE_AUTHORIZATION_SETTING))),
        refused(
            () ->
                AutraRequestShape.of(
                    propertyPrefix + AutraRequestShape.VOID_SETTING,
                    configuration.get(AutraRequestShape.VOID_SETTING))),
        refused(
            () ->
                AutraAuthorizerMessage.of(
                    propertyPrefix + AutraAuthorizerMessage.SETTING,
                    configuration.get(AutraAuthorizerMessage.SETTING))),
        refused(
            () ->
                AutraMerchantNotFound.of(
                    propertyPrefix + AutraMerchantNotFound.SETTING,
                    configuration.get(AutraMerchantNotFound.SETTING))),
        refused(
            () ->
                AutraSaleDecline.of(
                    propertyPrefix + AutraSaleDecline.SETTING,
                    configuration.get(AutraSaleDecline.SETTING))),
        refused(
            () ->
                AutraAcquirerReport.of(
                    propertyPrefix + AutraAcquirerReport.SETTING,
                    configuration.get(AutraAcquirerReport.SETTING))),
        theSecretItSignsWith(propertyPrefix, configuration.get(AutraWebhookSecret.SETTING)));
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
      case CLIENT_ID_PROPERTY -> CLIENT_ID_IS_THE_ACCOUNT;
      case CLIENT_SECRET_PROPERTY -> CLIENT_SECRET_IS_REQUIRED;
      default -> DOCUMENT_ID_IS_THE_ACQUIRING_IDENTITY;
    };
  }

  DisclosedAccount disclosure() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put(CLIENT_ID_PROPERTY, List.of(clientId));
    disclosed.put(CLIENT_SECRET_PROPERTY, List.of(clientSecret));
    disclosed.put(DOCUMENT_ID_PROPERTY, List.of(documentId));
    disclosed.put(AutraOauthFailure.SETTING, List.of(oauthFailure.name()));
    disclosed.put(AutraOauthFieldCasing.SETTING, List.of(oauthFieldCasing.name()));
    disclosed.put(
        AutraRequestShape.PRE_AUTHORIZATION_SETTING, List.of(preAuthorizationShape.name()));
    disclosed.put(AutraRequestShape.VOID_SETTING, List.of(voidShape.name()));
    disclosed.put(AutraAuthorizerMessage.SETTING, List.of(authorizerRejectedMessage));
    disclosed.put(AutraMerchantNotFound.SETTING, List.of(merchantNotFoundStatus.name()));
    disclosed.put(AutraSaleDecline.SETTING, List.of(saleDecline.name()));
    disclosed.put(AutraAcquirerReport.SETTING, List.of(acquirerReport.name()));
    String secret = webhookSecret;
    disclosed.put(AutraWebhookSecret.SETTING, secret == null ? List.of() : List.of(secret));
    return new DisclosedAccount(
        accountId, Redaction.redact(disclosed, ACCOUNT_FIELDS.secretKeys()));
  }

  private static @Nullable String theSecretItSignsWith(
      String propertyPrefix, @Nullable String configured) {
    try {
      return AutraWebhookSecret.of(propertyPrefix, configured);
    } catch (IllegalArgumentException rejected) {
      throw new ProviderConfigurationRejectedException(String.valueOf(rejected.getMessage()));
    }
  }

  private static <T> T refused(Supplier<T> reading) {
    try {
      return reading.get();
    } catch (IllegalArgumentException rejected) {
      throw new ProviderConfigurationRejectedException(String.valueOf(rejected.getMessage()));
    }
  }
}
