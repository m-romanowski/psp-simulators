package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GetTransactionInfo {

  private static final String NO_HOSTED_PROCESS = "";

  private final GrowTransactions transactions;
  private final GrowPaymentProcesses processes;
  private final ScenarioEngine scenarios;
  private final GrowScalarType scalarType;

  GetTransactionInfo(
      GrowTransactions transactions,
      GrowPaymentProcesses processes,
      ScenarioEngine scenarios,
      GrowScalarType scalarType) {
    this.transactions = transactions;
    this.processes = processes;
    this.scenarios = scenarios;
    this.scalarType = scalarType;
  }

  private record Addressed(
      GrowTransaction transaction, String processId, String processToken, GrowJson customField) {}

  GrowOperationResult handle(
      GrowForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      List<GrowAccount> credentialMatches,
      Instant now) {
    for (String required : GrowFields.REQUIRED_ON_GET_TRANSACTION_INFO) {
      if (!form.has(required)) {
        return failure(GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(required));
      }
    }

    if (credentialMatches.size() != 1) {
      return failure(
          GrowRefusals.theCredentialsReachNoMerchant(), GrowErrors.invalidLookupCredentials());
    }
    GrowAccount account = credentialMatches.get(0);
    String merchantId = account.accountId();

    Optional<Addressed> addressed =
        addressed(
            testRunId,
            merchantId,
            form.required(GrowFields.TRANSACTION_ID),
            form.required(GrowFields.TRANSACTION_TOKEN));
    if (addressed.isEmpty()) {
      return failure(
          RefusalSubject.noObjectAnsweredTo(GrowFields.TRANSACTION_ID),
          GrowErrors.invalidLookupCredentials());
    }

    Addressed found = addressed.get();
    GrowOperationResult result =
        GrowOperationResult.decidedBy(
            scenarios.decide(requestOf(headers, testRunId, merchantId, found.transaction())),
            () -> lookedUp(found),
            code ->
                failure(
                    RefusalSubject.theObject(found.transaction().transactionId()),
                    GrowErrors.chosenByScenario(code)));
    correlated(form, account, testRunId, found.transaction().transactionId(), now, result);
    return result;
  }

  private void correlated(
      GrowForm form,
      GrowAccount account,
      TestRunId testRunId,
      String transactionId,
      Instant now,
      GrowOperationResult result) {
    transactions.observed(
        new KeyedObservation(
            testRunId,
            account.accountId(),
            GrowKeyMechanisms.TRANSACTION_ID,
            transactionId,
            GrowOperations.GET_TRANSACTION_INFO,
            ExchangeVerdict.FIRST_REQUEST,
            new FingerprintedRequest(
                GrowLightApi.WIRE_METHOD, GrowOperations.GET_TRANSACTION_INFO, form.values()),
            new KeyedAnswer(
                GrowLightApi.GROW_HTTP_STATUS,
                account.wireForm().contentType(),
                result.body().flatten()),
            transactionId,
            false,
            null,
            now));
  }

  private GrowOperationResult lookedUp(Addressed found) {
    return GrowOperationResult.succeeded(
        found.transaction().transactionId(),
        GrowEnvelope.success(
            GrowTransactionRecord.lookedUp(
                found.transaction(), found.processId(), found.processToken(), found.customField()),
            scalarType));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      String merchantId,
      GrowTransaction transaction) {
    return new ScenarioRequest(
        GrowLightApi.GROW,
        testRunId,
        GrowStoryKey.ofTransaction(transaction),
        merchantId,
        null,
        transaction.transactionId(),
        null,
        null,
        null,
        null,
        GrowOperations.GET_TRANSACTION_INFO,
        headers);
  }

  private Optional<Addressed> addressed(
      TestRunId testRunId, String merchantId, String transactionId, String transactionToken) {
    return transactions
        .find(testRunId, merchantId, transactionId, transactionToken)
        .flatMap(transaction -> whatItWasCreatedBy(testRunId, transaction));
  }

  private Optional<Addressed> whatItWasCreatedBy(TestRunId testRunId, GrowTransaction transaction) {
    String processId = transaction.processId();
    if (processId == null) {
      return transactions
          .submissionOf(testRunId, transaction.transactionId())
          .map(
              submission ->
                  new Addressed(
                      transaction,
                      NO_HOSTED_PROCESS,
                      NO_HOSTED_PROCESS,
                      GrowTransactionRecord.customFieldsOf(submission.customFields())));
    }
    return processes
        .findByProcessId(testRunId, processId)
        .map(
            process ->
                new Addressed(
                    transaction,
                    process.processId(),
                    process.processToken(),
                    GrowTransactionRecord.customField(process)));
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }
}
