package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

record AzericardTerminal(
    TestRunId testRunId,
    String accountId,
    String terminalId,
    String merchantKeyPem,
    String gatewayKeyPem,
    String duplicateResponseCode,
    AzericardPartialCompletion partialCompletion,
    AzericardCallbackSignature callbackSignature,
    AzericardSettlementMac settlementMac,
    List<String> storedCardSourceTerminalIds) {

  AzericardTerminal {
    storedCardSourceTerminalIds = List.copyOf(storedCardSourceTerminalIds);
  }

  static final int LONGEST_TERMINAL_THE_STORE_HOLDS = 32;

  static final String TERMINAL_PROPERTY = "terminalId";

  static final String MERCHANT_KEY_PROPERTY = "apiKey";

  static final String GATEWAY_KEY_PROPERTY = "apiSecret";

  static final String STORED_CARD_SOURCE_TERMINALS_PROPERTY = "storedCardSourceTerminalIds";

  static final String SETTLEMENT_MAC_PROPERTY = AzericardSettlementMac.SETTING;

  static final String TWO_TERMINALS_SEPARATED_THE_WAY_THIS_BOX_READS_THEM = "70000013,70000014";

  static final String A_TERMINAL_AZERICARD_WOULD_TAKE = "77777777";

  static final ProviderAccountFields ACCOUNT_FIELDS =
      new ProviderAccountFields(
          AzericardGateway.AZERICARD,
          List.of(
              new ConfigurableField(
                  TERMINAL_PROPERTY,
                  "terminal id",
                  "The TERMINAL an azericard request carries the account it belongs to on, at most "
                      + LONGEST_TERMINAL_THE_STORE_HOLDS
                      + " characters.",
                  true,
                  false,
                  List.of(),
                  A_TERMINAL_AZERICARD_WOULD_TAKE),
              ConfigurableField.aBlockOfManyLines(
                  MERCHANT_KEY_PROPERTY,
                  "merchant public key (PEM)",
                  "The merchant's public key, which this simulator verifies the merchant's own"
                      + " signatures against. It must hold "
                      + AzericardKeys.PUBLIC_KEY_SHAPE
                      + ". "
                      + AzericardKeys.HOW_A_KEY_PAIR_IS_WRITTEN,
                  true,
                  false,
                  AzericardKeys.THE_LINE_A_PUBLIC_KEY_BEGINS_WITH,
                  AzericardKeys.THE_LINES_A_PUBLIC_KEY_MAY_END_WITH),
              ConfigurableField.aBlockOfManyLines(
                  GATEWAY_KEY_PROPERTY,
                  "gateway private key (PEM)",
                  "The gateway's private key, which this simulator signs its answers and its"
                      + " callbacks with. It must hold "
                      + AzericardKeys.PRIVATE_KEY_SHAPE
                      + ". "
                      + AzericardKeys.HOW_A_KEY_PAIR_IS_WRITTEN,
                  true,
                  true,
                  AzericardKeys.THE_LINE_A_PRIVATE_KEY_BEGINS_WITH,
                  AzericardKeys.THE_LINES_A_PRIVATE_KEY_MAY_END_WITH),
              new ConfigurableField(
                  AzericardDuplicateCode.SETTING,
                  "the duplicate-order code",
                  "Which response code a duplicate order of this terminal answers, as the one or"
                      + " two characters azericard sizes RC at, optionally signed. Leaving it"
                      + " empty answers with azericard's own default.",
                  false,
                  false,
                  List.of(),
                  AzericardDuplicateCode.DEFAULT),
              new ConfigurableField(
                  AzericardPartialCompletion.SETTING,
                  "what a partial completion leaves",
                  "What a partial completion of this terminal leaves of the authorization."
                      + " Leaving it empty behaves as azericard's own default does.",
                  false,
                  false,
                  AzericardPartialCompletion.acceptedValues()),
              new ConfigurableField(
                  AzericardCallbackSignature.SETTING,
                  "the merchant callback signature",
                  "Which signature the merchant callback of this terminal carries. Leaving it"
                      + " empty carries azericard's own default.",
                  false,
                  false,
                  AzericardCallbackSignature.acceptedValues()),
              new ConfigurableField(
                  SETTLEMENT_MAC_PROPERTY,
                  "settlement MAC order",
                  "Which field order a completion or a reversal is signed in. Leaving it empty"
                      + " signs in the order the gateway a real merchant reaches accepts, which"
                      + " is not the order the manual publishes.",
                  false,
                  false,
                  AzericardSettlementMac.acceptedValues()),
              new ConfigurableField(
                  STORED_CARD_SOURCE_TERMINALS_PROPERTY,
                  "stored-card source terminals",
                  "A comma-separated list of TERMINAL values whose saved-card TOKENs this"
                      + " terminal may use. Leaving it empty only allows cards this terminal"
                      + " stored itself.",
                  false,
                  false,
                  List.of(),
                  TWO_TERMINALS_SEPARATED_THE_WAY_THIS_BOX_READS_THEM)));

  static final Set<String> CONFIGURATION_KEYS = Set.copyOf(ACCOUNT_FIELDS.keys());

  static final String TERMINAL_IS_THE_ACCOUNT =
      TERMINAL_PROPERTY
          + " is required by provider azericard, because TERMINAL is the field an Azericard"
          + " request carries the account it belongs to on";

  static final String A_SOURCE_TERMINAL_IS_KEPT_BESIDE_THE_ONE_IT_LENDS_A_CARD_TO =
      STORED_CARD_SOURCE_TERMINALS_PROPERTY
          + " names a terminal of more than "
          + LONGEST_TERMINAL_THE_STORE_HOLDS
          + " characters, and the simulator keeps every terminal it is told about in a column"
          + " that wide, so one accepted here would be refused by that store the moment this"
          + " account is written. The bound is the same one terminalId is held to and for the"
          + " same reason";

  static final String TERMINAL_IS_KEPT_WITH_EVERY_PAYMENT =
      TERMINAL_PROPERTY
          + " must be at most "
          + LONGEST_TERMINAL_THE_STORE_HOLDS
          + " characters, because the simulator keeps the terminal of every payment and of every"
          + " order trace claimed against it in a column that wide, and a wider one is accepted"
          + " here and then refused by that store on every request of the account. The bound is"
          + " the simulator's own: provider azericard sizes TERMINAL at 8 and contradicts itself"
          + " with a seven-character example, so neither width is enforced";

  static AzericardTerminal of(
      String propertyPrefix, TestRunId testRunId, ConfiguredAccount account) {
    Map<String, String> configuration = account.configuration();
    String terminalId = configuration.get(TERMINAL_PROPERTY);
    if (terminalId == null || terminalId.isBlank()) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + TERMINAL_IS_THE_ACCOUNT);
    }
    if (terminalId.codePointCount(0, terminalId.length()) > LONGEST_TERMINAL_THE_STORE_HOLDS) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + TERMINAL_IS_KEPT_WITH_EVERY_PAYMENT);
    }
    String merchantKeyPem = configuration.get(MERCHANT_KEY_PROPERTY);
    String gatewayKeyPem = configuration.get(GATEWAY_KEY_PROPERTY);
    try {
      AzericardKeys.publicKeyOf(propertyPrefix + MERCHANT_KEY_PROPERTY, merchantKeyPem);
      AzericardKeys.privateKeyOf(propertyPrefix + GATEWAY_KEY_PROPERTY, gatewayKeyPem);
    } catch (IllegalArgumentException rejected) {
      throw new ProviderConfigurationRejectedException(String.valueOf(rejected.getMessage()));
    }
    return new AzericardTerminal(
        testRunId,
        account.accountId(),
        terminalId,
        String.valueOf(merchantKeyPem),
        String.valueOf(gatewayKeyPem),
        refused(
            () ->
                AzericardDuplicateCode.of(
                    propertyPrefix + AzericardDuplicateCode.SETTING,
                    configuration.get(AzericardDuplicateCode.SETTING))),
        refused(
            () ->
                AzericardPartialCompletion.of(
                    propertyPrefix + AzericardPartialCompletion.SETTING,
                    configuration.get(AzericardPartialCompletion.SETTING))),
        refused(
            () ->
                AzericardCallbackSignature.of(
                    propertyPrefix + AzericardCallbackSignature.SETTING,
                    configuration.get(AzericardCallbackSignature.SETTING))),
        refused(
            () ->
                AzericardSettlementMac.of(
                    propertyPrefix + SETTLEMENT_MAC_PROPERTY,
                    configuration.get(SETTLEMENT_MAC_PROPERTY))),
        storedCardSourceTerminalIds(
            propertyPrefix, configuration.get(STORED_CARD_SOURCE_TERMINALS_PROPERTY)));
  }

  boolean mayUseStoredCardFrom(String sourceTerminalId) {
    return terminalId.equals(sourceTerminalId)
        || storedCardSourceTerminalIds.contains(sourceTerminalId);
  }

  DisclosedAccount disclosure() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put(TERMINAL_PROPERTY, List.of(terminalId));
    disclosed.put(MERCHANT_KEY_PROPERTY, List.of(merchantKeyPem));
    disclosed.put(GATEWAY_KEY_PROPERTY, List.of(gatewayKeyPem));
    disclosed.put(AzericardDuplicateCode.SETTING, List.of(duplicateResponseCode));
    disclosed.put(AzericardPartialCompletion.SETTING, List.of(partialCompletion.name()));
    disclosed.put(AzericardCallbackSignature.SETTING, List.of(callbackSignature.name()));
    disclosed.put(SETTLEMENT_MAC_PROPERTY, List.of(settlementMac.name()));
    disclosed.put(
        STORED_CARD_SOURCE_TERMINALS_PROPERTY,
        List.of(String.join(",", storedCardSourceTerminalIds)));
    return new DisclosedAccount(
        accountId, Redaction.redact(disclosed, ACCOUNT_FIELDS.secretKeys()));
  }

  PublicKey merchantKey() {
    return AzericardKeys.publicKeyOf(MERCHANT_KEY_PROPERTY, merchantKeyPem);
  }

  PrivateKey gatewayKey() {
    return AzericardKeys.privateKeyOf(GATEWAY_KEY_PROPERTY, gatewayKeyPem);
  }

  private static <T> T refused(Supplier<T> reading) {
    try {
      return reading.get();
    } catch (IllegalArgumentException rejected) {
      throw new ProviderConfigurationRejectedException(String.valueOf(rejected.getMessage()));
    }
  }

  private static List<String> storedCardSourceTerminalIds(
      String propertyPrefix, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return List.of();
    }
    List<String> named =
        Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    for (String sourceTerminalId : named) {
      if (sourceTerminalId.codePointCount(0, sourceTerminalId.length())
          > LONGEST_TERMINAL_THE_STORE_HOLDS) {
        throw new ProviderConfigurationRejectedException(
            propertyPrefix + A_SOURCE_TERMINAL_IS_KEPT_BESIDE_THE_ONE_IT_LENDS_A_CARD_TO);
      }
    }
    return named;
  }
}
