package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ApproveTransaction {

  private final GrowPaymentProcesses processes;
  private final WebhookDispatcher deliveries;
  private final ScenarioEngine scenarios;
  private final GrowScalarType scalarType;

  ApproveTransaction(
      GrowPaymentProcesses processes,
      WebhookDispatcher deliveries,
      ScenarioEngine scenarios,
      GrowScalarType scalarType) {
    this.processes = processes;
    this.deliveries = deliveries;
    this.scenarios = scenarios;
    this.scalarType = scalarType;
  }

  GrowOperationResult handle(
      GrowForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      List<GrowAccount> credentialMatches) {
    if (!form.has(GrowFields.PAGE_CODE)) {
      return failure(
          GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(GrowFields.PAGE_CODE));
    }
    if (credentialMatches.size() != 1) {
      return failure(GrowRefusals.theCredentialsReachNoMerchant(), GrowErrors.recordNotFound());
    }

    String transactionId = form.first(GrowFields.TRANSACTION_ID);
    String transactionToken = form.first(GrowFields.TRANSACTION_TOKEN);
    if (transactionId == null || transactionToken == null) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.recordNotFound());
    }

    String merchantId = credentialMatches.get(0).accountId();
    Optional<GrowPaymentProcess> approved =
        processes.findByTransaction(testRunId, merchantId, transactionId, transactionToken);
    if (approved.isEmpty()) {
      return failure(
          RefusalSubject.noObjectAnsweredTo(GrowFields.TRANSACTION_ID),
          GrowErrors.recordNotFound());
    }

    GrowPaymentProcess process = approved.get();
    return GrowOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, merchantId, process.processId())),
        () -> acknowledge(process, testRunId),
        code ->
            failure(
                RefusalSubject.theObject(process.processId()), GrowErrors.chosenByScenario(code)));
  }

  private GrowOperationResult acknowledge(GrowPaymentProcess process, TestRunId testRunId) {
    acknowledgeTheCallbacksOf(process, testRunId);
    return GrowOperationResult.succeeded(process.processId(), successBody());
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
        GrowOperations.APPROVE_TRANSACTION,
        headers);
  }

  private void acknowledgeTheCallbacksOf(GrowPaymentProcess process, TestRunId testRunId) {
    deliveries.acknowledgeTheDeliveriesOf(
        GrowLightApi.GROW, testRunId, List.of(process.processId()));
  }

  private GrowJson successBody() {
    return GrowEnvelope.success(GrowJson.text(""), scalarType);
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }
}
