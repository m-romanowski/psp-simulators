package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class GetTokenTransactionsByExternalIdentifiers {

  private static final Pattern UNSIGNED_WHOLE_NUMBER = Pattern.compile("[1-9][0-9]{0,9}");

  private static final long WIDEST_THIRTY_TWO_BIT_VALUE = 4_294_967_295L;

  private final GrowPaymentProcesses processes;
  private final GrowTransactions transactions;
  private final ScenarioEngine scenarios;
  private final GrowScalarType scalarType;

  GetTokenTransactionsByExternalIdentifiers(
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
    for (String required : GrowFields.REQUIRED_ON_GET_TOKEN_TRANSACTIONS) {
      if (!form.has(required)) {
        return failure(GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(required));
      }
    }

    String uniqueIdentifier = form.first(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER);
    if (uniqueIdentifier == null) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.theUserDoesNotExist());
    }
    if (!isAWholeNumberGrowCanHold(uniqueIdentifier)) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.invalidUniqueIdentifier());
    }

    if (credentialMatches.size() != 1) {
      return failure(
          GrowRefusals.theCredentialsReachNoMerchant(),
          GrowErrors.noBusinessForTheUserIdOfAStoredCardCharge());
    }
    GrowAccount account = credentialMatches.get(0);

    String cardToken = form.required(GrowFields.CARD_TOKEN);
    if (processes.findByCardToken(testRunId, account.accountId(), cardToken).isEmpty()) {
      return failure(
          RefusalSubject.noObjectAnsweredTo(GrowFields.CARD_TOKEN), GrowErrors.unknownCardToken());
    }

    Optional<GrowTransaction> retraced =
        theChargeOf(testRunId, account.accountId(), uniqueIdentifier, form);
    if (retraced.isEmpty()) {
      return GrowOperationResult.succeededAbout(
          null, GrowEnvelope.success(theRows(List.of()), scalarType));
    }

    GrowTransaction charge = retraced.get();
    return GrowOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, account, cardToken, charge)),
        () ->
            GrowOperationResult.succeeded(
                charge.transactionId(),
                GrowEnvelope.success(
                    theRows(List.of(GrowTransactionRecord.chargedRow(charge))), scalarType)),
        code ->
            failure(
                RefusalSubject.theObject(charge.transactionId()),
                GrowErrors.chosenByScenario(code)));
  }

  private static GrowJson theRows(List<GrowJson> rows) {
    return GrowJson.object(GrowResponseFields.TRANSACTIONS, GrowJson.array(rows));
  }

  private Optional<GrowTransaction> theChargeOf(
      TestRunId testRunId, String merchantId, String uniqueIdentifier, GrowForm form) {
    return transactions
        .submissionUnder(testRunId, merchantId, uniqueIdentifier)
        .filter(submission -> groupMatches(submission, form))
        .map(GrowStoredCardCharge::transactionId)
        .flatMap(transactionId -> transactions.findByTransactionId(testRunId, transactionId));
  }

  private static boolean groupMatches(GrowStoredCardCharge submission, GrowForm form) {
    String asked = form.first(GrowFields.TRANSACTION_GROUP_IDENTIFIER);
    return asked == null || asked.equals(submission.groupIdentifier());
  }

  private static boolean isAWholeNumberGrowCanHold(String uniqueIdentifier) {
    if (!UNSIGNED_WHOLE_NUMBER.matcher(uniqueIdentifier).matches()) {
      return false;
    }
    return Long.parseLong(uniqueIdentifier) <= WIDEST_THIRTY_TWO_BIT_VALUE;
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      GrowAccount account,
      String cardToken,
      GrowTransaction charge) {
    return new ScenarioRequest(
        GrowLightApi.GROW,
        testRunId,
        GrowStoryKey.ofTransaction(charge),
        account.accountId(),
        null,
        charge.transactionId(),
        null,
        cardToken,
        null,
        null,
        GrowOperations.GET_TOKEN_TRANSACTIONS_BY_EXTERNAL_IDENTIFIERS,
        headers);
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }
}
