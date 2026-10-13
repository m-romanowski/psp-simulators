package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GetPaymentProcessInfo {

  private final GrowPaymentProcesses processes;
  private final GrowTransactions transactions;
  private final ScenarioEngine scenarios;
  private final GrowScalarType scalarType;

  GetPaymentProcessInfo(
      GrowPaymentProcesses processes,
      GrowTransactions transactions,
      ScenarioEngine scenarios,
      GrowScalarType scalarType) {
    this.processes = processes;
    this.transactions = transactions;
    this.scenarios = scenarios;
    this.scalarType = scalarType;
  }

  GrowOperationResult handle(
      GrowForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      List<GrowAccount> credentialMatches) {
    for (String required : GrowFields.REQUIRED_ON_GET_PAYMENT_PROCESS_INFO) {
      if (!form.has(required)) {
        return failure(GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(required));
      }
    }

    if (credentialMatches.size() != 1) {
      return failure(
          GrowRefusals.theCredentialsReachNoMerchant(), GrowErrors.invalidLookupCredentials());
    }
    GrowAccount account = credentialMatches.get(0);

    Optional<GrowPaymentProcess> found =
        processes.find(
            testRunId,
            form.required(GrowFields.PROCESS_ID),
            form.required(GrowFields.PROCESS_TOKEN));
    if (found.isEmpty()) {
      return failure(
          RefusalSubject.noObjectAnsweredTo(GrowFields.PROCESS_ID),
          account.processLookupRejection().rejection());
    }

    GrowPaymentProcess process = found.get();
    if (!process.merchantId().equals(account.accountId())) {
      return failure(
          RefusalSubject.anObjectItWillNotName(
              GrowRefusals.THE_PROCESS_BELONGS_TO_ANOTHER_MERCHANT),
          GrowErrors.invalidLookupCredentials());
    }
    return GrowOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, account.accountId(), process.processId())),
        () -> GrowOperationResult.succeeded(process.processId(), successBody(process, testRunId)),
        code ->
            failure(
                RefusalSubject.theObject(process.processId()), GrowErrors.chosenByScenario(code)));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers, TestRunId testRunId, String merchantId, String processId) {
    return new ScenarioRequest(
        GrowLightApi.GROW,
        testRunId,
        GrowStoryKey.ofProcess(processId),
        merchantId,
        null,
        processId,
        null,
        null,
        null,
        null,
        GrowOperations.GET_PAYMENT_PROCESS_INFO,
        headers);
  }

  private GrowJson successBody(GrowPaymentProcess process, TestRunId testRunId) {
    GrowJson row =
        theChargeOf(process, testRunId)
            .map(GrowTransactionRecord::chargedRow)
            .orElseGet(() -> GrowTransactionRecord.unpaidRow(process));
    return GrowEnvelope.success(
        GrowJson.object(
            GrowResponseFields.PROCESS_ID, GrowJson.text(process.processId()),
            GrowResponseFields.PROCESS_TOKEN, GrowJson.text(process.processToken()),
            GrowResponseFields.CUSTOM_FIELD, GrowTransactionRecord.customField(process),
            GrowResponseFields.TRANSACTIONS, GrowJson.array(List.of(row))),
        scalarType);
  }

  private Optional<GrowTransaction> theChargeOf(GrowPaymentProcess process, TestRunId testRunId) {
    GrowProcessOutcome outcome = process.outcome();
    return Optional.ofNullable(outcome == null ? null : outcome.transactionId())
        .flatMap(transactionId -> transactions.findByTransactionId(testRunId, transactionId));
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }
}
