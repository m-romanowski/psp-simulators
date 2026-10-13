package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class CreatePaymentProcess {

  static final int ID_ATTEMPTS = 5;

  private static final int PROCESS_ID_DIGITS = 6;

  private static final int PROCESS_TOKEN_LENGTH = 32;
  private static final int HOSTED_LINK_LENGTH = 32;

  private final GrowPaymentProcesses processes;
  private final IdGenerator ids;
  private final String hostedPageBaseUrl;
  private final ScenarioEngine scenarios;
  private final GrowScalarType scalarType;

  CreatePaymentProcess(
      GrowPaymentProcesses processes,
      IdGenerator ids,
      String hostedPageBaseUrl,
      ScenarioEngine scenarios,
      GrowScalarType scalarType) {
    this.processes = processes;
    this.ids = ids;
    this.hostedPageBaseUrl = hostedPageBaseUrl;
    this.scenarios = scenarios;
    this.scalarType = scalarType;
  }

  GrowOperationResult handle(
      GrowForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      List<GrowAccount> credentialMatches,
      Instant now,
      ObjectOrigin askedBy) {
    if (!isAnAmountGrowWouldTake(form.first(GrowFields.SUM))) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.amountNotValid());
    }

    for (String required : GrowFields.REQUIRED_ON_CREATE_PAYMENT_PROCESS) {
      if (!form.has(required)) {
        return failure(GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(required));
      }
    }

    if (registersACardOnly(form)
        && !GrowFields.SAVED_CARD_TOKEN.equals(form.first(GrowFields.SAVE_CARD_TOKEN))) {
      return failure(
          GrowRefusals.theFormWasRefused(),
          GrowErrors.missingRequiredField(GrowFields.SAVE_CARD_TOKEN));
    }

    if (credentialMatches.size() != 1) {
      return failure(GrowRefusals.theCredentialsReachNoMerchant(), GrowErrors.invalidCredentials());
    }

    if (!GrowFields.CHARGE_TYPES.contains(form.required(GrowFields.CHARGE_TYPE))) {
      return failure(
          GrowRefusals.theFormWasRefused(),
          GrowErrors.missingRequiredField(GrowFields.CHARGE_TYPE));
    }

    if (form.has(GrowFields.PAYMENT_NUM) && form.has(GrowFields.MAX_PAYMENT_NUM)) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.bothInstalmentFields());
    }

    GrowAccount account = credentialMatches.get(0);
    String processId = aProcessIdClaimedForThisDecision(testRunId);
    return GrowOperationResult.decidedBy(
        scenarios.decide(requestOf(form, headers, testRunId, account, processId)),
        () -> create(form, testRunId, account, now, processId, askedBy),
        code ->
            failure(
                RefusalSubject.noObjectWasNamed(
                    GrowRefusals.A_SCENARIO_DECLINED_BEFORE_A_PROCESS_WAS_STORED),
                GrowErrors.chosenByScenario(code)));
  }

  private static boolean registersACardOnly(GrowForm form) {
    return GrowFields.CHARGE_TYPE_TOKEN_ONLY.equals(form.first(GrowFields.CHARGE_TYPE));
  }

  private String aProcessIdClaimedForThisDecision(TestRunId testRunId) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String processId = ids.digits(PROCESS_ID_DIGITS);
      if (processes.claimProcessId(testRunId, processId)) {
        return processId;
      }
    }
    throw new IllegalStateException(
        "no Grow processId could be claimed in " + ID_ATTEMPTS + " attempts");
  }

  private static ScenarioRequest requestOf(
      GrowForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      GrowAccount account,
      String processId) {
    return new ScenarioRequest(
        GrowLightApi.GROW,
        testRunId,
        GrowStoryKey.ofProcess(processId),
        account.accountId(),
        null,
        null,
        null,
        null,
        Amount.of(form.required(GrowFields.SUM)),
        null,
        GrowOperations.CREATE_PAYMENT_PROCESS,
        headers);
  }

  private static boolean isAnAmountGrowWouldTake(@Nullable String amount) {
    if (amount == null) {
      return false;
    }
    try {
      return new BigDecimal(amount).signum() > 0;
    } catch (NumberFormatException notANumber) {
      return false;
    }
  }

  private GrowOperationResult create(
      GrowForm form,
      TestRunId testRunId,
      GrowAccount account,
      Instant now,
      String processId,
      ObjectOrigin askedBy) {
    String processToken = ids.hex(PROCESS_TOKEN_LENGTH);
    String hostedLink = ids.hex(HOSTED_LINK_LENGTH);

    GrowPaymentProcess process =
        new GrowPaymentProcess(
            testRunId,
            processId,
            processToken,
            account.accountId(),
            form.required(GrowFields.CHARGE_TYPE),
            form.required(GrowFields.SUM),
            form.required(GrowFields.DESCRIPTION),
            "1".equals(form.first(GrowFields.SAVE_CARD_TOKEN)),
            form.required(GrowFields.SUCCESS_URL),
            form.required(GrowFields.CANCEL_URL),
            form.first(GrowFields.NOTIFY_URL),
            form.required(GrowFields.FULL_NAME),
            form.required(GrowFields.PHONE),
            form.first(GrowFields.EMAIL),
            customFieldsOf(form),
            hostedLink,
            GrowNativeStatus.NOT_PAID_CODE,
            GrowNativeStatus.NOT_PAID,
            NormalizedStatus.NOT_INITIALIZED,
            null,
            askedBy,
            now,
            now);
    if (!processes.insertIfAbsent(process)) {
      throw new IllegalStateException(
          "the Grow processId this decision was taken under was claimed by another request before"
              + " the process could be stored");
    }
    return GrowOperationResult.succeeded(processId, successBody(process));
  }

  private GrowJson successBody(GrowPaymentProcess process) {
    return GrowEnvelope.success(
        GrowJson.object(
            GrowResponseFields.PROCESS_ID, GrowJson.text(process.processId()),
            GrowResponseFields.PROCESS_TOKEN, GrowJson.text(process.processToken()),
            GrowResponseFields.URL,
                GrowJson.text(GrowHostedPage.urlFor(hostedPageBaseUrl, process.hostedLink()))),
        scalarType);
  }

  private static Map<String, String> customFieldsOf(GrowForm form) {
    Map<String, String> customFields = new LinkedHashMap<>();
    for (String name : GrowFields.CUSTOM_FIELDS) {
      String value = form.first(name);
      if (value != null) {
        customFields.put(name, value);
      }
    }
    return customFields;
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }
}
