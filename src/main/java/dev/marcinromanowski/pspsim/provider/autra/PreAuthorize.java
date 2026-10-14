package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class PreAuthorize {

  static final int PAYMENT_ID_ATTEMPTS = 5;

  static final int AUTHORIZATION_CODE_LENGTH = 6;

  static final int RRN_DIGITS = 12;

  static final int DEFAULT_INSTALLMENTS = 1;

  static final int MOST_INSTALLMENTS = 12;

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_MERCHANT_DOCUMENT_IS_REQUIRED =
      "documentId is required and names the merchant the reservation is taken for";

  static final String THE_SESSION_TOKEN_IS_REQUIRED =
      "transactionalToken is required and names the acquiring session this call spends";

  static final String THE_SESSION_TOKEN_NAMES_NO_SESSION =
      "transactionalToken names no acquiring session this tenant opened";

  static final String NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT =
      "no merchant of this tenant answers to the documentId this call carries";

  static final String ONLY_CREDIT_IS_PRE_AUTHORIZED =
      "transactionType is required and CREDIT is the only value this endpoint accepts";

  static final String THE_CURRENCY_IS_REQUIRED = "currency is required";

  static final String THE_AMOUNT_MUST_BE_ABOVE_ZERO =
      "amount is required and must be a number above zero";

  static final String INSTALLMENTS_ARE_ONE_TO_TWELVE =
      "installments is required and is 1 for a single payment or 2 to 12 for an instalment plan";

  static final String ONE_INSTRUMENT_AND_ONLY_ONE =
      "send card or tokenData, never both, and one of the two is required";

  static final String THE_ORDER_ALREADY_HOLDS_A_PRE_AUTHORIZATION =
      "this orderId already has an active pre-authorization";

  private final AutraPayments payments;
  private final AutraSessions sessions;
  private final AutraStoredCards storedCards;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;

  PreAuthorize(
      AutraPayments payments,
      AutraSessions sessions,
      AutraStoredCards storedCards,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    this.payments = payments;
    this.sessions = sessions;
    this.storedCards = storedCards;
    this.ids = ids;
    this.scenarios = scenarios;
  }

  AutraOperationResult handle(
      AutraApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy) {
    Optional<AutraJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.INVALID_JSON, A_BODY_THIS_ENDPOINT_CANNOT_READ);
    }
    AutraJson.JsonObject body = parsed.get();
    boolean schema = merchant.preAuthorizationShape().isTheSchema();
    AutraOperationResult refused = theRefusalOf(body, testRunId, merchant, schema);
    if (refused != null) {
      return refused;
    }
    String orderId = theOrderIn(body);
    if (orderId != null) {
      AutraOrderClaim claim =
          payments.claimOrder(testRunId, merchant.accountId(), merchant.documentId(), orderId, now);
      if (!(claim instanceof AutraOrderClaim.Fresh)) {
        return refusedAsADuplicate(call, testRunId, merchant, now, orderId, claim);
      }
    }
    return decided(call, body, headers, testRunId, merchant, now, askedBy, orderId);
  }

  private @Nullable AutraOperationResult theRefusalOf(
      AutraJson.JsonObject body, TestRunId testRunId, AutraMerchant merchant, boolean schema) {
    Optional<String> documentId = textIn(body, AutraFields.DOCUMENT_ID);
    if (documentId.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_DOCUMENT_ID, THE_MERCHANT_DOCUMENT_IS_REQUIRED);
    }
    if (!merchant.documentId().equals(documentId.get())) {
      return AutraAnswers.noSuchMerchant(
          merchant.merchantNotFoundStatus(), NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
    }
    Optional<String> transactionalToken = textIn(body, AutraFields.TRANSACTIONAL_TOKEN);
    if (transactionalToken.isEmpty() && schema) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_TRANSACTIONAL_TOKEN, THE_SESSION_TOKEN_IS_REQUIRED);
    }
    if (transactionalToken.isPresent()
        && sessions.session(testRunId, transactionalToken.get()).isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.BAD_REQUEST, THE_SESSION_TOKEN_NAMES_NO_SESSION);
    }
    Optional<String> transactionType = textIn(body, AutraFields.TRANSACTION_TYPE);
    if (transactionType.isPresent() ? !AutraFields.CREDIT.equals(transactionType.get()) : schema) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, ONLY_CREDIT_IS_PRE_AUTHORIZED);
    }
    if (textIn(body, AutraFields.CURRENCY).isEmpty()) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, THE_CURRENCY_IS_REQUIRED);
    }
    Optional<String> amount = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT);
    if (amount.filter(AutraAmounts::isAboveZero).isEmpty()) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_AMOUNT, THE_AMOUNT_MUST_BE_ABOVE_ZERO);
    }
    Integer installments = theInstallmentsIn(body);
    if (installments == null && schema) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, INSTALLMENTS_ARE_ONE_TO_TWELVE);
    }
    if (installments != null && (installments < 1 || installments > MOST_INSTALLMENTS)) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, INSTALLMENTS_ARE_ONE_TO_TWELVE);
    }
    AutraOperationResult instrument = theInstrumentRefusalOf(body, schema);
    if (instrument != null) {
      return instrument;
    }
    if (body.has(AutraFields.TOKEN_DATA)
        && !storedCards.holds(testRunId, theTokenSlugIn(body), theStoredCardSlugIn(body))) {
      return AutraAnswers.tokenNotAvailable(
          ChargeStoredCard.THE_TOKEN_DATA_NAMES_NO_CARD_OF_THIS_RUN);
    }
    return null;
  }

  private static @Nullable AutraOperationResult theInstrumentRefusalOf(
      AutraJson.JsonObject body, boolean schema) {
    boolean card = body.has(AutraFields.CARD);
    boolean tokenData = body.has(AutraFields.TOKEN_DATA);
    if (card && tokenData) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, ONE_INSTRUMENT_AND_ONLY_ONE);
    }
    if (!card && !tokenData && schema) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, ONE_INSTRUMENT_AND_ONLY_ONE);
    }
    if (tokenData && theSlugTokenIn(body) == null) {
      return AutraAnswers.refusedRequest(
          AutraErrors.BAD_REQUEST, ChargeStoredCard.THE_TOKEN_DATA_NAMES_NO_CARD);
    }
    return null;
  }

  private AutraOperationResult decided(
      AutraApiCall call,
      AutraJson.JsonObject body,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      @Nullable String orderId) {
    String paymentId = aPaymentIdClaimedFor(testRunId);
    ScenarioDecision decision =
        scenarios.decide(requestOf(body, headers, testRunId, merchant, paymentId, orderId));
    if (decision instanceof ScenarioDecision.Preempt preempted) {
      if (orderId != null) {
        payments.releaseOrder(testRunId, merchant.documentId(), orderId);
      }
      return AutraOperationResult.answeredInsteadOf(preempted.scenarioId(), preempted.answer());
    }
    return AutraOperationResult.decidedBy(
        decision,
        () -> held(call, body, testRunId, merchant, now, askedBy, orderId, paymentId),
        code -> declined(call, body, testRunId, merchant, now, askedBy, orderId, paymentId, code));
  }

  private static ScenarioRequest requestOf(
      AutraJson.JsonObject body,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AutraMerchant merchant,
      String paymentId,
      @Nullable String orderId) {
    return new ScenarioRequest(
        AutraApi.AUTRA,
        testRunId,
        AutraStoryKey.ofPayment(paymentId),
        merchant.accountId(),
        orderId,
        null,
        null,
        theSlugTokenIn(body),
        Amount.of(AutraAmounts.lexicalIn(body, AutraFields.AMOUNT).orElse("")),
        textIn(body, AutraFields.CURRENCY).orElse(null),
        AutraOperations.PRE_AUTHORIZATION,
        headers);
  }

  private AutraOperationResult held(
      AutraApiCall call,
      AutraJson.JsonObject body,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      @Nullable String orderId,
      String paymentId) {
    String amount = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT).orElseThrow();
    String currency = textIn(body, AutraFields.CURRENCY).orElseThrow();
    int installments = theInstallmentsOrTheGuidesDefault(body);
    AutraPayment payment =
        stored(
            testRunId,
            merchant,
            paymentId,
            orderId,
            amount,
            currency,
            installments,
            AutraStatus.ACCEPTED,
            theReservationOf(amount, now),
            theTokenSlugIn(body),
            theStoredCardSlugIn(body),
            now,
            askedBy);
    theOrderThisAttemptLeaves(testRunId, merchant, orderId, payment);
    payments.recordEvent(
        AutraPaymentEvent.of(
            testRunId,
            paymentId,
            AutraTimeline.PREAUTH,
            AutraStatus.ACCEPTED,
            Amount.of(amount),
            now));
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    answer.put(AutraFields.PAYMENT_ID_UPPERCASE_D, AutraJson.text(paymentId));
    answer.put(AutraFields.STATUS, AutraJson.text(AutraStatus.ACCEPTED));
    answer.put(AutraFields.AMOUNT, AutraJson.number(amount));
    answer.put(AutraFields.CURRENCY, AutraJson.text(currency));
    AutraAcquirerTransaction.underBothPublishedNames(answer, payment);
    answer.put(AutraFields.RRN, AutraJson.text(payment.rrn()));
    answer.put(AutraFields.AUTHORIZATION_CODE, AutraJson.text(payment.authorizationCode()));
    AutraOperationResult result =
        AutraOperationResult.succeeded(
            paymentId, AutraBody.of(AutraAnswers.OK, AutraJson.object(answer)));
    record(
        call,
        testRunId,
        merchant,
        orderId,
        now,
        KeyedOutcome.RAN_THE_OPERATION,
        paymentId,
        true,
        result);
    return result;
  }

  private AutraOperationResult declined(
      AutraApiCall call,
      AutraJson.JsonObject body,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      @Nullable String orderId,
      String paymentId,
      String code) {
    String amount = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT).orElseThrow();
    String currency = textIn(body, AutraFields.CURRENCY).orElseThrow();
    AutraPayment payment =
        stored(
            testRunId,
            merchant,
            paymentId,
            orderId,
            amount,
            currency,
            theInstallmentsOrTheGuidesDefault(body),
            AutraStatus.DECLINED,
            Ledger.empty(),
            theTokenSlugIn(body),
            theStoredCardSlugIn(body),
            now,
            askedBy);
    theOrderThisAttemptLeaves(testRunId, merchant, orderId, payment);
    payments.recordEvent(
        AutraPaymentEvent.refused(
            testRunId,
            paymentId,
            AutraTimeline.PREAUTH,
            AutraStatus.DECLINED,
            Amount.of(amount),
            code,
            AutraDeclineMessages.of(code, merchant),
            now));
    AutraOperationResult result =
        AutraOperationResult.failed(
            AutraRefusals.aDeclinedPreAuthorization(),
            AutraBody.of(
                AutraAnswers.UNPROCESSABLE,
                AutraErrors.envelope(code, AutraDeclineMessages.of(code, merchant))));
    record(
        call,
        testRunId,
        merchant,
        orderId,
        now,
        KeyedOutcome.RAN_THE_OPERATION,
        paymentId,
        true,
        result);
    return result;
  }

  private AutraOperationResult refusedAsADuplicate(
      AutraApiCall call,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      String orderId,
      AutraOrderClaim claim) {
    AutraOperationResult result =
        AutraAnswers.orderAlreadyHeld(THE_ORDER_ALREADY_HOLDS_A_PRE_AUTHORIZATION);
    if (claim instanceof AutraOrderClaim.AlreadyPreAuthorized held) {
      record(
          call,
          testRunId,
          merchant,
          orderId,
          now,
          KeyedOutcome.REFUSED_AS_A_DUPLICATE,
          held.paymentId(),
          false,
          result);
      return result;
    }
    record(
        call,
        testRunId,
        merchant,
        orderId,
        now,
        KeyedOutcome.REFUSED_WHILE_THE_KEY_WAS_HELD,
        null,
        false,
        result);
    return result;
  }

  private void theOrderThisAttemptLeaves(
      TestRunId testRunId, AutraMerchant merchant, @Nullable String orderId, AutraPayment payment) {
    if (orderId == null) {
      return;
    }
    if (AutraStatus.releasesTheOrderForReuse(payment.nativeStatus())) {
      payments.releaseOrder(testRunId, merchant.documentId(), orderId);
      return;
    }
    payments.settleOrder(testRunId, merchant.documentId(), orderId, payment.paymentId());
  }

  private AutraPayment stored(
      TestRunId testRunId,
      AutraMerchant merchant,
      String paymentId,
      @Nullable String orderId,
      String amount,
      String currency,
      int installments,
      String nativeStatus,
      Ledger ledger,
      @Nullable String slugToken,
      @Nullable String slugStoredCard,
      Instant now,
      ObjectOrigin askedBy) {
    AutraPayment payment =
        new AutraPayment(
            testRunId,
            paymentId,
            merchant.accountId(),
            merchant.documentId(),
            orderId,
            AutraTimeline.PREAUTH,
            AutraFields.CREDIT,
            Amount.of(amount),
            currency,
            installments,
            nativeStatus,
            ids.hex(AUTHORIZATION_CODE_LENGTH).toUpperCase(Locale.ROOT),
            ids.digits(RRN_DIGITS),
            AutraAcquirerTransaction.asTheAcquirerReportsIt(merchant, ids),
            slugToken,
            slugStoredCard,
            ledger,
            null,
            askedBy,
            now,
            now);
    if (!payments.insertIfAbsent(payment)) {
      throw new IllegalStateException(
          "the Autra paymentID this decision was taken under was claimed by another request before"
              + " the payment could be stored");
    }
    return payment;
  }

  private static Ledger theReservationOf(String amount, Instant now) {
    return Ledger.empty()
        .with(
            new MonetaryOperation(
                1,
                OperationKind.AUTHORIZATION,
                Amount.of(amount),
                Boolean.FALSE,
                null,
                AutraStatus.ACCEPTED,
                NormalizedStatus.READY_FOR_CAPTURE,
                now));
  }

  private void record(
      AutraApiCall call,
      TestRunId testRunId,
      AutraMerchant merchant,
      @Nullable String orderId,
      Instant now,
      KeyedOutcome outcome,
      @Nullable String providerObjectId,
      boolean stateChanged,
      AutraOperationResult result) {
    if (orderId == null) {
      return;
    }
    payments.observed(
        new KeyedObservation(
            testRunId,
            merchant.accountId(),
            AutraKeyMechanisms.ORDER_ID,
            orderId,
            AutraOperations.PRE_AUTHORIZATION,
            outcome,
            new FingerprintedRequest(
                call.method(),
                AutraOperations.PRE_AUTHORIZATION,
                call.body().orElse(AutraJson.empty()).flatten()),
            result.body().asAKeyedAnswer(),
            providerObjectId,
            stateChanged,
            null,
            now));
  }

  private String aPaymentIdClaimedFor(TestRunId testRunId) {
    for (int attempt = 0; attempt < PAYMENT_ID_ATTEMPTS; attempt++) {
      String paymentId = AutraIdentifiers.uuidOf(ids);
      if (payments.claimPaymentId(testRunId, paymentId)) {
        return paymentId;
      }
    }
    throw new IllegalStateException(
        "no Autra paymentID could be claimed in " + PAYMENT_ID_ATTEMPTS + " attempts");
  }

  static @Nullable String theOrderIn(AutraJson.JsonObject body) {
    return textIn(body, AutraFields.ORDER_ID).orElse(null);
  }

  static @Nullable String theSlugTokenIn(AutraJson.JsonObject body) {
    String slugToken = theTokenSlugIn(body);
    return slugToken == null ? theStoredCardSlugIn(body) : slugToken;
  }

  static @Nullable String theTokenSlugIn(AutraJson.JsonObject body) {
    return theSlugIn(body, AutraFields.SLUG_TOKEN);
  }

  static @Nullable String theStoredCardSlugIn(AutraJson.JsonObject body) {
    return theSlugIn(body, AutraFields.SLUG_STORED_CARD);
  }

  private static @Nullable String theSlugIn(AutraJson.JsonObject body, String name) {
    return body.object(AutraFields.TOKEN_DATA)
        .flatMap(tokenData -> tokenData.text(name))
        .filter(slug -> !slug.isBlank())
        .orElse(null);
  }

  static Optional<String> textIn(AutraJson.JsonObject body, String name) {
    return body.text(name).filter(sent -> !sent.isBlank());
  }

  static @Nullable Integer theInstallmentsIn(AutraJson.JsonObject body) {
    Optional<String> sent = AutraAmounts.lexicalIn(body, AutraFields.INSTALLMENTS);
    if (sent.isEmpty()) {
      return null;
    }
    try {
      return Integer.valueOf(sent.get());
    } catch (NumberFormatException notAWholeNumber) {
      return Integer.valueOf(0);
    }
  }

  static int theInstallmentsOrTheGuidesDefault(AutraJson.JsonObject body) {
    Integer sent = theInstallmentsIn(body);
    return sent == null ? DEFAULT_INSTALLMENTS : sent.intValue();
  }
}
