package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ReverseOrRelease {

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_MERCHANT_DOCUMENT_IS_REQUIRED =
      "documentId is required and names the merchant the cancellation is made for";

  static final String THE_SESSION_TOKEN_IS_REQUIRED =
      "transactionalToken is required and names the acquiring session this call spends";

  static final String THE_AMOUNT_MUST_BE_ABOVE_ZERO =
      "amount is required and must be a number above zero";

  static final String NO_SUCH_PAYMENT = "paymentId names no payment of this tenant";

  static final String HIGHER_THAN_THE_REMAINING_BALANCE =
      "amount is higher than the remaining balance of this payment";

  static final String A_RESERVATION_IS_RELEASED_WHOLE =
      "a pre-authorization that has captured nothing is released whole, and amount must equal the"
          + " value it reserved";

  static final String NOTHING_REMAINS_TO_REVERSE =
      "this payment has already been reversed in full and has no remaining balance";

  static final String IT_NEVER_RESERVED_ANYTHING =
      "this payment failed and reserved nothing that could be cancelled";

  private final AutraPayments payments;
  private final ScenarioEngine scenarios;

  ReverseOrRelease(AutraPayments payments, ScenarioEngine scenarios) {
    this.payments = payments;
    this.scenarios = scenarios;
  }

  AutraOperationResult handle(
      AutraApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      String paymentId,
      Instant now) {
    boolean schema = merchant.voidShape().isTheSchema();
    Optional<AutraJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty() && (schema || call.carriesABody())) {
      return AutraAnswers.refusedRequest(
          AutraErrors.INVALID_JSON, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    AutraJson.JsonObject body = parsed.orElseGet(AutraJson::empty);
    AutraOperationResult refused = theBodyRefusalOf(body, merchant, schema);
    if (refused != null) {
      return refused;
    }
    Optional<AutraPayment> found =
        payments
            .findById(testRunId, paymentId)
            .filter(payment -> payment.accountId().equals(merchant.accountId()));
    if (found.isEmpty()) {
      return AutraAnswers.noSuchPayment(NO_SUCH_PAYMENT);
    }
    AutraPayment payment = found.get();
    BigDecimal remaining = theRemainingBalanceOf(payment);
    if (remaining.signum() == 0) {
      return AutraAnswers.refusedObject(
          paymentId, AutraErrors.INVALID_STATUS, theIneligibilityOf(payment));
    }
    String amount =
        AutraAmounts.lexicalIn(body, AutraFields.AMOUNT).orElse(remaining.toPlainString());
    int against = AutraAmounts.compare(amount, remaining);
    if (against > 0) {
      return AutraAnswers.refusedObject(
          paymentId,
          AutraErrors.AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE,
          HIGHER_THAN_THE_REMAINING_BALANCE);
    }
    if (against < 0 && payment.isAHoldNothingHasClosed()) {
      return AutraAnswers.refusedObject(
          paymentId, AutraErrors.INVALID_AMOUNT, A_RESERVATION_IS_RELEASED_WHOLE);
    }
    return decided(call, headers, testRunId, merchant, payment, amount, against == 0, now);
  }

  private static @Nullable AutraOperationResult theBodyRefusalOf(
      AutraJson.JsonObject body, AutraMerchant merchant, boolean schema) {
    Optional<String> documentId = PreAuthorize.textIn(body, AutraFields.DOCUMENT_ID);
    if (documentId.isEmpty() && schema) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_DOCUMENT_ID, THE_MERCHANT_DOCUMENT_IS_REQUIRED);
    }
    if (documentId.isPresent() && !merchant.documentId().equals(documentId.get())) {
      return AutraAnswers.noSuchMerchant(
          merchant.merchantNotFoundStatus(), PreAuthorize.NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
    }
    if (PreAuthorize.textIn(body, AutraFields.TRANSACTIONAL_TOKEN).isEmpty() && schema) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_TRANSACTIONAL_TOKEN, THE_SESSION_TOKEN_IS_REQUIRED);
    }
    Optional<String> amount = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT);
    if (amount.isEmpty() && schema) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_AMOUNT, THE_AMOUNT_MUST_BE_ABOVE_ZERO);
    }
    if (amount.isPresent() && !AutraAmounts.isAboveZero(amount.get())) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_AMOUNT, THE_AMOUNT_MUST_BE_ABOVE_ZERO);
    }
    return null;
  }

  static BigDecimal theRemainingBalanceOf(AutraPayment payment) {
    if (payment.isAHoldNothingHasClosed()) {
      return payment.amount().number().orElse(BigDecimal.ZERO);
    }
    return payment.reversibleRemainder();
  }

  static String theIneligibilityOf(AutraPayment payment) {
    if (AutraStatus.DECLINED.equals(payment.nativeStatus())) {
      return IT_NEVER_RESERVED_ANYTHING;
    }
    if (payment.holdReleasedAt() != null) {
      return "this pre-authorization has already been cancelled";
    }
    return NOTHING_REMAINS_TO_REVERSE;
  }

  private AutraOperationResult decided(
      AutraApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      AutraPayment payment,
      String amount,
      boolean inFull,
      Instant now) {
    ScenarioDecision decision =
        scenarios.decide(requestOf(headers, testRunId, merchant, payment, amount));
    if (decision instanceof ScenarioDecision.Preempt preempted) {
      return AutraOperationResult.answeredInsteadOf(preempted.scenarioId(), preempted.answer());
    }
    return AutraOperationResult.decidedBy(
        decision,
        () -> reversed(call, testRunId, merchant, payment, amount, inFull, now),
        code -> declined(call, testRunId, merchant, payment, now, code));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      AutraPayment payment,
      String amount) {
    return new ScenarioRequest(
        AutraApi.AUTRA,
        testRunId,
        AutraStoryKey.ofPayment(payment.paymentId()),
        merchant.accountId(),
        null,
        payment.paymentId(),
        null,
        null,
        Amount.of(amount),
        payment.currency(),
        AutraOperations.VOID,
        headers);
  }

  private AutraOperationResult reversed(
      AutraApiCall call,
      TestRunId testRunId,
      AutraMerchant merchant,
      AutraPayment payment,
      String amount,
      boolean inFull,
      Instant now) {
    String status = inFull ? AutraStatus.VOIDED : AutraStatus.PARTIALLY_VOIDED;
    LedgerClaim applied =
        payment.isAHoldNothingHasClosed()
            ? payments.releaseTheHold(payment, now)
            : payments.reverse(
                payment, theReversalOf(payment, amount, status, inFull, now), status, now);
    if (applied != LedgerClaim.APPLIED) {
      return AutraAnswers.refusedObject(
          payment.paymentId(), AutraErrors.INVALID_STATUS, theIneligibilityOf(payment));
    }
    payments.recordEvent(
        AutraPaymentEvent.of(
            testRunId, payment.paymentId(), AutraTimeline.VOID, status, Amount.of(amount), now));
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    answer.put(AutraFields.PAYMENT_ID_LOWERCASE_D, AutraJson.text(payment.paymentId()));
    answer.put(AutraFields.STATUS, AutraJson.text(status));
    answer.put(AutraFields.AMOUNT, AutraJson.number(amount));
    answer.put(AutraFields.CURRENCY, AutraJson.text(payment.currency()));
    answer.put(AutraFields.RRN, AutraJson.text(payment.rrn()));
    AutraAcquirerTransaction.underThePrimaryNameAlone(answer, payment);
    AutraOperationResult result =
        AutraOperationResult.succeeded(
            payment.paymentId(), AutraBody.of(AutraAnswers.OK, AutraJson.object(answer)));
    record(call, testRunId, merchant, payment, now, true, result);
    return result;
  }

  private static MonetaryOperation theReversalOf(
      AutraPayment payment, String amount, String status, boolean inFull, Instant now) {
    return new MonetaryOperation(
        payment.ledger().nextSequence(),
        OperationKind.REFUND,
        Amount.of(amount),
        null,
        null,
        status,
        inFull ? NormalizedStatus.REFUNDED : NormalizedStatus.PARTIALLY_REFUNDED,
        now);
  }

  private AutraOperationResult declined(
      AutraApiCall call,
      TestRunId testRunId,
      AutraMerchant merchant,
      AutraPayment payment,
      Instant now,
      String code) {
    payments.recordEvent(
        AutraPaymentEvent.refused(
            testRunId,
            payment.paymentId(),
            AutraTimeline.VOID,
            payment.nativeStatus(),
            payment.amount(),
            code,
            AutraDeclineMessages.of(code, merchant),
            now));
    AutraOperationResult result =
        AutraAnswers.refusedObject(
            payment.paymentId(), code, AutraDeclineMessages.of(code, merchant));
    record(call, testRunId, merchant, payment, now, false, result);
    return result;
  }

  private void record(
      AutraApiCall call,
      TestRunId testRunId,
      AutraMerchant merchant,
      AutraPayment payment,
      Instant now,
      boolean stateChanged,
      AutraOperationResult result) {
    payments.observed(
        new KeyedObservation(
            testRunId,
            merchant.accountId(),
            AutraKeyMechanisms.PAYMENT_ID,
            payment.paymentId(),
            AutraOperations.VOID,
            KeyedOutcome.RAN_THE_OPERATION,
            new FingerprintedRequest(
                call.method(), AutraOperations.VOID, CaptureHold.theFieldsOf(call)),
            result.body().asAKeyedAnswer(),
            payment.paymentId(),
            stateChanged,
            null,
            now));
  }
}
