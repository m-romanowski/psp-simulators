package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.RequestFingerprint;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class CaptureHold {

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_MERCHANT_DOCUMENT_IS_REQUIRED =
      "documentId is required and names the merchant the capture is made for";

  static final String THE_SESSION_TOKEN_IS_REQUIRED =
      "transactionalToken is required and names the acquiring session this call spends";

  static final String NO_INSTRUMENT_ON_A_CAPTURE =
      "do not send card or tokenData on a capture; the pre-authorization already names the card";

  static final String NO_SUCH_PAYMENT = "paymentId names no payment of this tenant";

  static final String THE_AMOUNT_MUST_BE_ABOVE_ZERO = "amount must be a number above zero";

  static final String MORE_THAN_WAS_RESERVED =
      "amount is higher than the value this pre-authorization reserved";

  private final AutraPayments payments;
  private final ScenarioEngine scenarios;

  CaptureHold(AutraPayments payments, ScenarioEngine scenarios) {
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
    Optional<AutraJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.INVALID_JSON, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    AutraJson.JsonObject body = parsed.get();
    AutraOperationResult refused = theBodyRefusalOf(body, merchant);
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
    if (!payment.isAHoldNothingHasClosed()) {
      return AutraAnswers.refusedObject(
          paymentId, AutraErrors.INVALID_STATUS, payment.whyItIsNotEligible());
    }
    Optional<String> asked = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT);
    if (asked.isPresent() && !AutraAmounts.isAboveZero(asked.get())) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_AMOUNT, THE_AMOUNT_MUST_BE_ABOVE_ZERO);
    }
    String amount = asked.orElse(payment.amount().value());
    if (AutraAmounts.compare(amount, payment.capturableRemainder()) > 0) {
      return AutraAnswers.refusedObject(
          paymentId, AutraErrors.INVALID_AMOUNT, MORE_THAN_WAS_RESERVED);
    }
    return decided(call, headers, testRunId, merchant, payment, amount, now);
  }

  private static @Nullable AutraOperationResult theBodyRefusalOf(
      AutraJson.JsonObject body, AutraMerchant merchant) {
    Optional<String> documentId = PreAuthorize.textIn(body, AutraFields.DOCUMENT_ID);
    if (documentId.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_DOCUMENT_ID, THE_MERCHANT_DOCUMENT_IS_REQUIRED);
    }
    if (PreAuthorize.textIn(body, AutraFields.TRANSACTIONAL_TOKEN).isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_TRANSACTIONAL_TOKEN, THE_SESSION_TOKEN_IS_REQUIRED);
    }
    if (body.has(AutraFields.CARD) || body.has(AutraFields.TOKEN_DATA)) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_JSON, NO_INSTRUMENT_ON_A_CAPTURE);
    }
    if (!merchant.documentId().equals(documentId.get())) {
      return AutraAnswers.noSuchMerchant(
          merchant.merchantNotFoundStatus(), PreAuthorize.NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
    }
    return null;
  }

  private AutraOperationResult decided(
      AutraApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      AutraPayment payment,
      String amount,
      Instant now) {
    ScenarioDecision decision =
        scenarios.decide(requestOf(headers, testRunId, merchant, payment, amount));
    if (decision instanceof ScenarioDecision.Preempt preempted) {
      return AutraOperationResult.answeredInsteadOf(preempted.scenarioId(), preempted.answer());
    }
    return AutraOperationResult.decidedBy(
        decision,
        () -> captured(call, testRunId, merchant, payment, amount, now),
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
        null,
        AutraOperations.CAPTURE,
        headers);
  }

  private AutraOperationResult captured(
      AutraApiCall call,
      TestRunId testRunId,
      AutraMerchant merchant,
      AutraPayment payment,
      String amount,
      Instant now) {
    MonetaryOperation operation =
        new MonetaryOperation(
            payment.ledger().nextSequence(),
            OperationKind.CAPTURE,
            Amount.of(amount),
            Boolean.TRUE,
            null,
            AutraStatus.CAPTURED,
            NormalizedStatus.SUCCEEDED,
            now);
    LedgerClaim applied = payments.capture(payment, operation, now);
    if (applied != LedgerClaim.APPLIED) {
      return AutraAnswers.refusedObject(
          payment.paymentId(), AutraErrors.INVALID_STATUS, payment.whyItIsNotEligible());
    }
    payments.recordEvent(
        AutraPaymentEvent.of(
            testRunId,
            payment.paymentId(),
            AutraTimeline.CAPTURE,
            AutraStatus.CAPTURED,
            Amount.of(amount),
            now));
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    answer.put(AutraFields.PAYMENT_ID_UPPERCASE_D, AutraJson.text(payment.paymentId()));
    answer.put(AutraFields.STATUS, AutraJson.text(AutraStatus.CAPTURED));
    answer.put(AutraFields.AMOUNT, AutraJson.number(amount));
    answer.put(AutraFields.CURRENCY, AutraJson.text(payment.currency()));
    AutraAcquirerTransaction.underBothPublishedNames(answer, payment);
    answer.put(AutraFields.RRN, AutraJson.text(payment.rrn()));
    answer.put(AutraFields.AUTHORIZATION_CODE, AutraJson.text(payment.authorizationCode()));
    AutraOperationResult result =
        AutraOperationResult.succeeded(
            payment.paymentId(), AutraBody.of(AutraAnswers.OK, AutraJson.object(answer)));
    record(call, testRunId, merchant, payment, now, true, result);
    return result;
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
            AutraTimeline.CAPTURE,
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
    boolean seen =
        payments.hasSeen(
            testRunId, merchant.accountId(), AutraKeyMechanisms.PAYMENT_ID, payment.paymentId());
    payments.observed(
        new AutraObservedRequest(
            testRunId,
            merchant.accountId(),
            AutraKeyMechanisms.PAYMENT_ID,
            payment.paymentId(),
            AutraOperations.CAPTURE,
            RequestFingerprint.of(call.method(), AutraOperations.CAPTURE, theFieldsOf(call)),
            seen ? ExchangeVerdict.CORRELATED_RETRY : ExchangeVerdict.FIRST_REQUEST,
            result.body().httpStatus(),
            result.body().contentType(),
            result.body().text(),
            payment.paymentId(),
            stateChanged,
            now));
  }

  static Map<String, List<String>> theFieldsOf(AutraApiCall call) {
    return call.body().map(AutraJson::flatten).orElseGet(Map::of);
  }
}
