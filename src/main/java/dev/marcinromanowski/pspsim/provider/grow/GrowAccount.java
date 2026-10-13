package dev.marcinromanowski.pspsim.provider.grow;

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
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

record GrowAccount(
    TestRunId testRunId,
    String accountId,
    String userId,
    String pageCode,
    GrowAcknowledgementMode acknowledgement,
    GrowSettlementCeiling settlementCeiling,
    GrowRefundAllowance refundAllowance,
    GrowRefundExcessRejection refundExcessRejection,
    GrowMerchantBalance merchantBalance,
    GrowProcessLookupRejection processLookupRejection,
    GrowWireForm wireForm,
    GrowHoldWindow holdWindow) {

  static final String USER_ID = "userId";

  static final String PAGE_CODE = "pageCode";

  static final String A_USER_ID_GROW_WOULD_TAKE = "grow-user-1";

  static final String A_PAGE_CODE_GROW_WOULD_TAKE = "grow-page-1";

  static final ProviderAccountFields ACCOUNT_FIELDS =
      new ProviderAccountFields(
          GrowLightApi.GROW,
          List.of(
              new ConfigurableField(
                  USER_ID,
                  "Grow user id",
                  "The userId grow names this merchant by on every call. No two accounts of one"
                      + " test run may name the same one.",
                  true,
                  true,
                  List.of(),
                  A_USER_ID_GROW_WOULD_TAKE),
              new ConfigurableField(
                  PAGE_CODE,
                  "Grow page code",
                  "The pageCode grow names this account's hosted page by. No two accounts of one"
                      + " test run may name the same one.",
                  true,
                  true,
                  List.of(),
                  A_PAGE_CODE_GROW_WOULD_TAKE),
              new ConfigurableField(
                  GrowAcknowledgementMode.SETTING,
                  "when a callback is acknowledged",
                  "When a callback this account posts counts as acknowledged. Leaving it empty"
                      + " behaves as grow's own default does.",
                  false,
                  false,
                  GrowAcknowledgementMode.acceptedValues()),
              new ConfigurableField(
                  GrowSettlementCeiling.SETTING,
                  "how far a settlement may go",
                  "How far above the held amount a settlement of this account may go. Leaving it"
                      + " empty behaves as grow's own default does.",
                  false,
                  false,
                  GrowSettlementCeiling.acceptedValues()),
              new ConfigurableField(
                  GrowRefundAllowance.SETTING,
                  "how many refunds are allowed",
                  "How many refunds one transaction of this account allows. Leaving it empty"
                      + " behaves as grow's own default does.",
                  false,
                  false,
                  GrowRefundAllowance.acceptedValues()),
              new ConfigurableField(
                  GrowRefundExcessRejection.SETTING,
                  "what a refund is measured on",
                  "What a refund amount of this account is measured against. Leaving it empty"
                      + " behaves as grow's own default does.",
                  false,
                  false,
                  GrowRefundExcessRejection.acceptedValues()),
              new ConfigurableField(
                  GrowMerchantBalance.SETTING,
                  "the merchant balance",
                  "Whether the merchant balance funds a refund of this account. Leaving it empty"
                      + " behaves as grow's own default does.",
                  false,
                  false,
                  GrowMerchantBalance.acceptedValues()),
              new ConfigurableField(
                  GrowProcessLookupRejection.SETTING,
                  "how a process lookup refuses",
                  "Which code a process lookup of this account refuses with. Leaving it empty"
                      + " refuses with grow's own default.",
                  false,
                  false,
                  GrowProcessLookupRejection.acceptedValues()),
              new ConfigurableField(
                  GrowWireForm.SETTING,
                  "which wire form answers carry",
                  "Which of the two forms provider grow answers in a response of this account is"
                      + " written in. Leaving it empty answers in grow's own default.",
                  false,
                  false,
                  GrowWireForm.acceptedValues()),
              ConfigurableField.aDuration(
                  GrowHoldWindow.CAPTURE_WINDOW,
                  "how long a hold is capturable",
                  "How long a hold of this account stays capturable. A form states it as a number"
                      + " and a unit; a control API call writes the same span as an ISO-8601"
                      + " duration such as "
                      + GrowHoldWindow.AN_EXAMPLE_CAPTURE_WINDOW
                      + ". It must be longer than nothing and at most a year. Leaving it empty"
                      + " holds for grow's own default.",
                  false,
                  GrowHoldWindow.AN_EXAMPLE_CAPTURE_WINDOW),
              ConfigurableField.aDuration(
                  GrowHoldWindow.AUTOMATIC_VOID_AFTER,
                  "when an uncaptured hold voids",
                  "When an uncaptured hold of this account voids itself. A form states it as a"
                      + " number and a unit; a control API call writes the same span as an"
                      + " ISO-8601 duration such as "
                      + GrowHoldWindow.AN_EXAMPLE_AUTOMATIC_VOID
                      + ". It must be longer than nothing and at most a year, and a hold stops"
                      + " being capturable at whichever of the two spans closes first. Leaving it"
                      + " empty voids on grow's own default.",
                  false,
                  GrowHoldWindow.AN_EXAMPLE_AUTOMATIC_VOID)));

  static final Set<String> CONFIGURATION_KEYS = Set.copyOf(ACCOUNT_FIELDS.keys());

  static GrowAccount of(String propertyPrefix, TestRunId testRunId, ConfiguredAccount account) {
    Map<String, String> configuration = account.configuration();
    return new GrowAccount(
        testRunId,
        account.accountId(),
        theCredential(propertyPrefix, USER_ID, configuration.get(USER_ID)),
        theCredential(propertyPrefix, PAGE_CODE, configuration.get(PAGE_CODE)),
        chosen(
            propertyPrefix,
            GrowAcknowledgementMode.SETTING,
            configuration,
            GrowAcknowledgementMode::of),
        chosen(
            propertyPrefix,
            GrowSettlementCeiling.SETTING,
            configuration,
            GrowSettlementCeiling::of),
        chosen(propertyPrefix, GrowRefundAllowance.SETTING, configuration, GrowRefundAllowance::of),
        chosen(
            propertyPrefix,
            GrowRefundExcessRejection.SETTING,
            configuration,
            GrowRefundExcessRejection::of),
        chosen(propertyPrefix, GrowMerchantBalance.SETTING, configuration, GrowMerchantBalance::of),
        chosen(
            propertyPrefix,
            GrowProcessLookupRejection.SETTING,
            configuration,
            GrowProcessLookupRejection::of),
        chosen(propertyPrefix, GrowWireForm.SETTING, configuration, GrowWireForm::of),
        GrowHoldWindow.of(
            propertyPrefix,
            configuration.get(GrowHoldWindow.CAPTURE_WINDOW),
            configuration.get(GrowHoldWindow.AUTOMATIC_VOID_AFTER)));
  }

  DisclosedAccount disclosure() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put(USER_ID, List.of(userId));
    disclosed.put(PAGE_CODE, List.of(pageCode));
    disclosed.put(GrowAcknowledgementMode.SETTING, List.of(acknowledgement.name()));
    disclosed.put(GrowSettlementCeiling.SETTING, List.of(settlementCeiling.name()));
    disclosed.put(GrowRefundAllowance.SETTING, List.of(refundAllowance.name()));
    disclosed.put(GrowRefundExcessRejection.SETTING, List.of(refundExcessRejection.name()));
    disclosed.put(GrowMerchantBalance.SETTING, List.of(merchantBalance.name()));
    disclosed.put(GrowProcessLookupRejection.SETTING, List.of(processLookupRejection.name()));
    disclosed.put(GrowWireForm.SETTING, List.of(wireForm.name()));
    disclosed.put(GrowHoldWindow.CAPTURE_WINDOW, List.of(holdWindow.captureWindow().toString()));
    disclosed.put(
        GrowHoldWindow.AUTOMATIC_VOID_AFTER, List.of(holdWindow.automaticVoidAfter().toString()));
    return new DisclosedAccount(
        accountId, Redaction.redact(disclosed, ACCOUNT_FIELDS.secretKeys()));
  }

  private static String theCredential(
      String propertyPrefix, String key, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + key + " is required by provider grow");
    }
    return configured;
  }

  private static <T> T chosen(
      String propertyPrefix,
      String key,
      Map<String, String> configuration,
      Function<@Nullable String, T> reading) {
    try {
      return reading.apply(configuration.get(key));
    } catch (IllegalArgumentException unrecognised) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + key + " " + unrecognised.getMessage());
    }
  }
}
