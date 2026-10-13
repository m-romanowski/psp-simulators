package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class SettleSuspendedTransaction {

  private final GrowTransactions transactions;
  private final ScenarioEngine scenarios;
  private final GrowScalarType scalarType;

  SettleSuspendedTransaction(
      GrowTransactions transactions, ScenarioEngine scenarios, GrowScalarType scalarType) {
    this.transactions = transactions;
    this.scenarios = scenarios;
    this.scalarType = scalarType;
  }

  GrowOperationResult handle(
      GrowForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      List<GrowAccount> credentialMatches,
      Instant now) {
    Amount requested = Amount.of(orEmpty(form.first(GrowFields.SUM)));
    Optional<BigDecimal> released = positive(requested);
    if (released.isEmpty()) {
      return failure(GrowRefusals.theFormWasRefused(), GrowErrors.amountNotValid());
    }

    for (String required : GrowFields.REQUIRED_ON_SETTLE_SUSPENDED_TRANSACTION) {
      if (!form.has(required)) {
        return failure(GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(required));
      }
    }

    if (credentialMatches.size() != 1) {
      return failure(
          GrowRefusals.theCredentialsReachNoMerchant(), GrowErrors.noBusinessForTheUserId());
    }
    GrowAccount account = credentialMatches.get(0);

    Optional<GrowTransaction> found =
        transactions.find(
            testRunId,
            account.accountId(),
            form.required(GrowFields.TRANSACTION_ID),
            form.required(GrowFields.TRANSACTION_TOKEN));
    if (found.isEmpty()) {
      return failure(
          RefusalSubject.noObjectAnsweredTo(GrowFields.TRANSACTION_ID), GrowErrors.notCarriedOut());
    }
    GrowTransaction transaction = found.get();

    if (!transaction.holdsAnAuthorization()) {
      return failure(theTransaction(transaction), GrowErrors.notOfAReleasableType());
    }
    if (transaction.isReleased()) {
      return failure(theTransaction(transaction), GrowErrors.alreadyReleased());
    }
    if (transaction.isPastEitherDeadline(now, account.holdWindow())) {
      return failure(theTransaction(transaction), GrowErrors.holdExpired());
    }

    GrowSettlementCeiling ceiling = account.settlementCeiling();
    if (!admits(ceiling, released.get(), transaction.amount())) {
      return failure(theTransaction(transaction), ceiling.rejection());
    }

    ScenarioRequest scenarioRequest =
        new ScenarioRequest(
            GrowLightApi.GROW,
            testRunId,
            GrowStoryKey.ofTransaction(transaction),
            account.accountId(),
            null,
            transaction.transactionId(),
            null,
            null,
            requested,
            null,
            GrowOperations.SETTLE_SUSPENDED_TRANSACTION,
            headers);
    String token = form.required(GrowFields.TRANSACTION_TOKEN);
    return GrowOperationResult.decidedBy(
        scenarios.decide(scenarioRequest),
        () -> release(transaction, token, requested, now),
        code -> failure(theTransaction(transaction), GrowErrors.chosenByScenario(code)));
  }

  private static Optional<BigDecimal> positive(Amount amount) {
    return amount.number().filter(value -> value.signum() > 0);
  }

  private static boolean admits(GrowSettlementCeiling ceiling, BigDecimal released, Amount held) {
    return held.number().map(amount -> ceiling.admits(released, amount)).orElse(false);
  }

  private GrowOperationResult release(
      GrowTransaction transaction, String token, Amount released, Instant now) {
    MonetaryOperation capture =
        new MonetaryOperation(
            transaction.ledger().nextSequence(),
            OperationKind.CAPTURE,
            released,
            true,
            null,
            GrowNativeStatus.PAID_CODE,
            NormalizedStatus.SUCCEEDED,
            now);
    GrowTransaction settled = transaction.with(capture, now);
    return switch (transactions.append(settled, capture)) {
      case APPLIED ->
          GrowOperationResult.succeeded(
              settled.transactionId(), successBody(settled, token, released));
      case CLAIM_TAKEN -> failure(theTransaction(transaction), GrowErrors.alreadyReleased());
      case OBJECT_GONE ->
          failure(
              RefusalSubject.noObjectAnsweredTo(GrowFields.TRANSACTION_ID),
              GrowErrors.notCarriedOut());
    };
  }

  private GrowJson successBody(GrowTransaction settled, String token, Amount released) {
    GrowRecordedCard card = settled.card();
    return GrowEnvelope.success(
        GrowJson.object(
            GrowResponseFields.ASMACHTA, GrowJson.text(settled.asmachta()),
            GrowResponseFields.CARD_SUFFIX, GrowJson.text(card.suffix()),
            GrowResponseFields.CARD_TYPE, GrowJson.text(card.type()),
            GrowResponseFields.CARD_TYPE_CODE, GrowJson.text(card.typeCode()),
            GrowResponseFields.CARD_BRAND, GrowJson.text(card.brand()),
            GrowResponseFields.CARD_BRAND_CODE, GrowJson.text(card.brandCode()),
            GrowResponseFields.CARD_EXP, GrowJson.text(card.expiry()),
            GrowResponseFields.FIRST_PAYMENT_SUM, GrowJson.text(ZERO),
            GrowResponseFields.PERIODICAL_PAYMENT_SUM, GrowJson.text(ZERO),
            GrowResponseFields.STATUS, GrowJson.text(GrowNativeStatus.PAID),
            GrowResponseFields.STATUS_CODE, GrowJson.text(GrowNativeStatus.PAID_CODE),
            GrowResponseFields.TRANSACTION_TYPE_ID, GrowJson.text(CARD_TRANSACTION_TYPE_ID),
            GrowResponseFields.PAYMENT_TYPE, GrowJson.text(REGULAR_PAYMENT_TYPE),
            GrowResponseFields.SUM, GrowJson.text(released.value()),
            GrowResponseFields.PAYMENTS_NUM, GrowJson.text(ZERO),
            GrowResponseFields.ALL_PAYMENTS_NUM, GrowJson.text(ONE),
            GrowResponseFields.PAYMENT_DATE, GrowJson.text(settled.paymentDate()),
            GrowResponseFields.DESCRIPTION, GrowJson.text(settled.description()),
            GrowResponseFields.FULL_NAME, GrowJson.text(settled.payerFullName()),
            GrowResponseFields.PAYER_PHONE, GrowJson.text(settled.payerPhone()),
            GrowResponseFields.PAYER_EMAIL, GrowJson.text(orEmpty(settled.payerEmail())),
            GrowResponseFields.TRANSACTION_ID, GrowJson.text(settled.transactionId()),
            GrowResponseFields.TRANSACTION_TOKEN, GrowJson.text(token)),
        scalarType);
  }

  private static RefusalSubject theTransaction(GrowTransaction transaction) {
    return RefusalSubject.theObject(transaction.transactionId());
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }

  private static final String ZERO = "0";

  private static final String ONE = "1";

  private static final String CARD_TRANSACTION_TYPE_ID = "1";

  private static final String REGULAR_PAYMENT_TYPE = "2";

  private static String orEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }
}
