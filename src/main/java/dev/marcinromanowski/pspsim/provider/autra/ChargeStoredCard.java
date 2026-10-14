package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ChargeStoredCard {

  static final String A_BODY_THIS_ENDPOINT_CANNOT_READ =
      "the request body is not a JSON object this endpoint can read";

  static final String THE_MERCHANT_DOCUMENT_IS_REQUIRED =
      "documentId is required and names the merchant the sale is charged for, not the cardholder";

  static final String THE_SESSION_TOKEN_IS_REQUIRED =
      "transactionalToken is required and names the acquiring session this call spends";

  static final String THE_SESSION_TOKEN_NAMES_NO_SESSION =
      "transactionalToken names no acquiring session this tenant opened";

  static final String ONLY_CREDIT_OR_DEBIT_IS_SOLD =
      "transactionType is required and CREDIT and DEBIT are the only values this endpoint accepts";

  static final String THE_CURRENCY_IS_REQUIRED = "currency is required";

  static final String A_RECURRING_SERIES_IS_NOT_SERVED =
      "recurringData asks for a recurring series, which provider autra publishes on this endpoint"
          + " and this simulator does not serve. The vendor answers brandReferenceId to a first"
          + " sale carrying isFirstRecurring and takes that identifier back on the sales after"
          + " it; this simulator keeps no series, so it refuses the member rather than accepting"
          + " it and answering a sale with no brandReferenceId in it, which would leave the"
          + " second charge of the series impossible and say so nowhere. Send the sale without"
          + " recurringData";

  static final String THE_AMOUNT_MAY_BE_ZERO_AND_NEVER_BELOW_IT =
      "amount is required and must be a number that is not below zero: provider autra publishes"
          + " zero as meaningful on this endpoint alone — \"Com amount: 0 funciona como validacao"
          + " de cartao (zero-dollar): verifica o cartao junto ao emissor sem cobrar\" — and"
          + " publishes INVALID_AMOUNT for an amount that is not above zero on the endpoints where"
          + " it requires one";

  static final String INSTALLMENTS_ARE_ONE_TO_TWELVE =
      "installments is required and is 1 for a single payment or 2 to 12 for an instalment plan";

  static final String THE_TOKEN_DATA_NAMES_NO_CARD =
      "tokenData must name slugToken or slugStoredCard: "
          + AutraStoredCard.A_STORED_CARD_NAMES_AT_LEAST_ONE_SLUG;

  static final String THE_TOKEN_DATA_NAMES_NO_CARD_OF_THIS_RUN =
      "tokenData names no card this test run tokenized on its hosted card page or seeded as a"
          + " payment-method fixture";

  static final String THE_ORDER_ALREADY_HAS_AN_APPROVED_PAYMENT =
      "this orderId already has an approved payment";

  private final AutraPayments payments;
  private final AutraSessions sessions;
  private final AutraStoredCards storedCards;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;

  ChargeStoredCard(
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
    AutraOperationResult refused = theRefusalOf(body, testRunId, merchant);
    if (refused != null) {
      return refused;
    }
    String orderId = PreAuthorize.theOrderIn(body);
    if (orderId != null) {
      Optional<String> approved =
          payments.theApprovedPaymentUnder(testRunId, merchant.documentId(), orderId);
      if (approved.isPresent()) {
        return refusedAsADuplicate(call, testRunId, merchant, now, orderId, approved.get());
      }
    }
    return decided(call, body, headers, testRunId, merchant, now, askedBy, orderId);
  }

  private @Nullable AutraOperationResult theRefusalOf(
      AutraJson.JsonObject body, TestRunId testRunId, AutraMerchant merchant) {
    Optional<String> documentId = PreAuthorize.textIn(body, AutraFields.DOCUMENT_ID);
    if (documentId.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_DOCUMENT_ID, THE_MERCHANT_DOCUMENT_IS_REQUIRED);
    }
    if (!merchant.documentId().equals(documentId.get())) {
      return AutraAnswers.noSuchMerchant(
          merchant.merchantNotFoundStatus(), PreAuthorize.NO_MERCHANT_ANSWERS_TO_THAT_DOCUMENT);
    }
    Optional<String> transactionalToken =
        PreAuthorize.textIn(body, AutraFields.TRANSACTIONAL_TOKEN);
    if (transactionalToken.isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.MISSING_TRANSACTIONAL_TOKEN, THE_SESSION_TOKEN_IS_REQUIRED);
    }
    if (sessions.session(testRunId, transactionalToken.get()).isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.BAD_REQUEST, THE_SESSION_TOKEN_NAMES_NO_SESSION);
    }
    Optional<String> transactionType = PreAuthorize.textIn(body, AutraFields.TRANSACTION_TYPE);
    if (transactionType.filter(ChargeStoredCard::isACardProduct).isEmpty()) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, ONLY_CREDIT_OR_DEBIT_IS_SOLD);
    }
    if (PreAuthorize.textIn(body, AutraFields.CURRENCY).isEmpty()) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, THE_CURRENCY_IS_REQUIRED);
    }
    Optional<String> amount = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT);
    if (amount.filter(ChargeStoredCard::isZeroOrAbove).isEmpty()) {
      return AutraAnswers.refusedRequest(
          AutraErrors.INVALID_AMOUNT, THE_AMOUNT_MAY_BE_ZERO_AND_NEVER_BELOW_IT);
    }
    Integer installments = PreAuthorize.theInstallmentsIn(body);
    if (installments == null
        || installments.intValue() < 1
        || installments.intValue() > PreAuthorize.MOST_INSTALLMENTS) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, INSTALLMENTS_ARE_ONE_TO_TWELVE);
    }
    if (body.has(AutraFields.RECURRING_DATA)) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, A_RECURRING_SERIES_IS_NOT_SERVED);
    }
    AutraOperationResult instrument = theInstrumentRefusalOf(body);
    if (instrument != null) {
      return instrument;
    }
    if (body.has(AutraFields.TOKEN_DATA)
        && !storedCards.holds(
            testRunId, PreAuthorize.theTokenSlugIn(body), PreAuthorize.theStoredCardSlugIn(body))) {
      return AutraAnswers.tokenNotAvailable(THE_TOKEN_DATA_NAMES_NO_CARD_OF_THIS_RUN);
    }
    return null;
  }

  private static @Nullable AutraOperationResult theInstrumentRefusalOf(AutraJson.JsonObject body) {
    boolean card = body.has(AutraFields.CARD);
    boolean tokenData = body.has(AutraFields.TOKEN_DATA);
    if (card == tokenData) {
      return AutraAnswers.refusedRequest(
          AutraErrors.BAD_REQUEST, PreAuthorize.ONE_INSTRUMENT_AND_ONLY_ONE);
    }
    if (tokenData && PreAuthorize.theSlugTokenIn(body) == null) {
      return AutraAnswers.refusedRequest(AutraErrors.BAD_REQUEST, THE_TOKEN_DATA_NAMES_NO_CARD);
    }
    return null;
  }

  static boolean isACardProduct(String transactionType) {
    return AutraFields.CREDIT.equals(transactionType) || AutraFields.DEBIT.equals(transactionType);
  }

  static boolean isZeroOrAbove(String lexical) {
    return AutraAmounts.isTypedAsANumber(lexical)
        && AutraAmounts.numberOf(lexical).filter(value -> value.signum() >= 0).isPresent();
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
      return AutraOperationResult.answeredInsteadOf(preempted.scenarioId(), preempted.answer());
    }
    return AutraOperationResult.decidedBy(
        decision,
        () -> accepted(call, body, testRunId, merchant, now, askedBy, orderId, paymentId),
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
        PreAuthorize.theSlugTokenIn(body),
        Amount.of(AutraAmounts.lexicalIn(body, AutraFields.AMOUNT).orElse("")),
        PreAuthorize.textIn(body, AutraFields.CURRENCY).orElse(null),
        AutraOperations.SALE,
        headers);
  }

  private AutraOperationResult accepted(
      AutraApiCall call,
      AutraJson.JsonObject body,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      ObjectOrigin askedBy,
      @Nullable String orderId,
      String paymentId) {
    String amount = AutraAmounts.lexicalIn(body, AutraFields.AMOUNT).orElseThrow();
    String currency = PreAuthorize.textIn(body, AutraFields.CURRENCY).orElseThrow();
    AutraPayment payment =
        stored(
            body,
            testRunId,
            merchant,
            paymentId,
            orderId,
            amount,
            currency,
            AutraStatus.ACCEPTED,
            now,
            askedBy);
    payments.recordEvent(
        AutraPaymentEvent.of(
            testRunId,
            paymentId,
            AutraTimeline.SALE,
            AutraStatus.ACCEPTED,
            Amount.of(amount),
            now));
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    answer.put(AutraFields.PAYMENT_ID_UPPERCASE_D, AutraJson.text(paymentId));
    answer.put(AutraFields.STATUS, AutraJson.text(AutraStatus.ACCEPTED));
    answer.put(AutraFields.AMOUNT, AutraJson.number(amount));
    answer.put(AutraFields.CURRENCY, AutraJson.text(currency));
    answer.put(
        AutraFields.INSTALLMENTS, AutraJson.number(Integer.toString(payment.installments())));
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
        ExchangeVerdict.FIRST_REQUEST,
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
    String currency = PreAuthorize.textIn(body, AutraFields.CURRENCY).orElseThrow();
    stored(
        body,
        testRunId,
        merchant,
        paymentId,
        orderId,
        amount,
        currency,
        AutraStatus.DECLINED,
        now,
        askedBy);
    AutraSaleDecline where = merchant.saleDecline();
    String msg = AutraDeclineMessages.of(code, merchant);
    payments.recordEvent(
        AutraPaymentEvent.refused(
            testRunId,
            paymentId,
            AutraTimeline.SALE,
            AutraStatus.DECLINED,
            Amount.of(amount),
            code,
            msg,
            now));
    AutraOperationResult result =
        where.namesThePayment()
            ? AutraOperationResult.failed(
                RefusalSubject.theObject(paymentId),
                AutraBody.of(where.httpStatus(), theDeclinedBodyNaming(paymentId, code, msg)))
            : AutraOperationResult.failed(
                AutraRefusals.aDeclinedSaleAt422(),
                AutraBody.of(where.httpStatus(), AutraErrors.envelope(code, msg)));
    record(
        call,
        testRunId,
        merchant,
        orderId,
        now,
        ExchangeVerdict.FIRST_REQUEST,
        paymentId,
        true,
        result);
    return result;
  }

  private static AutraJson.JsonObject theDeclinedBodyNaming(
      String paymentId, String code, String msg) {
    Map<String, AutraJson> declined = new LinkedHashMap<>();
    declined.put(AutraFields.PAYMENT_ID_UPPERCASE_D, AutraJson.text(paymentId));
    declined.put(AutraFields.ERRORS, AutraJson.array(List.of(AutraErrors.item(code, msg))));
    return AutraJson.object(declined);
  }

  private AutraOperationResult refusedAsADuplicate(
      AutraApiCall call,
      TestRunId testRunId,
      AutraMerchant merchant,
      Instant now,
      String orderId,
      String approvedPaymentId) {
    AutraOperationResult result =
        AutraAnswers.orderAlreadyApproved(THE_ORDER_ALREADY_HAS_AN_APPROVED_PAYMENT);
    record(
        call,
        testRunId,
        merchant,
        orderId,
        now,
        ExchangeVerdict.DUPLICATE_KEY_REFUSAL,
        approvedPaymentId,
        false,
        result);
    return result;
  }

  private AutraPayment stored(
      AutraJson.JsonObject body,
      TestRunId testRunId,
      AutraMerchant merchant,
      String paymentId,
      @Nullable String orderId,
      String amount,
      String currency,
      String nativeStatus,
      Instant now,
      ObjectOrigin askedBy) {
    AutraPayment payment =
        new AutraPayment(
            testRunId,
            paymentId,
            merchant.accountId(),
            merchant.documentId(),
            orderId,
            AutraTimeline.SALE,
            PreAuthorize.textIn(body, AutraFields.TRANSACTION_TYPE).orElseThrow(),
            Amount.of(amount),
            currency,
            PreAuthorize.theInstallmentsOrTheGuidesDefault(body),
            nativeStatus,
            ids.hex(PreAuthorize.AUTHORIZATION_CODE_LENGTH).toUpperCase(Locale.ROOT),
            ids.digits(PreAuthorize.RRN_DIGITS),
            AutraAcquirerTransaction.asTheAcquirerReportsIt(merchant, ids),
            PreAuthorize.theTokenSlugIn(body),
            PreAuthorize.theStoredCardSlugIn(body),
            theMoneyItTook(amount, nativeStatus, now),
            null,
            askedBy,
            now,
            now);
    if (!payments.insertIfAbsent(payment)) {
      throw new IllegalStateException(
          "the Autra paymentID this sale was decided under was claimed by another request before"
              + " the payment could be stored");
    }
    return payment;
  }

  private static Ledger theMoneyItTook(String amount, String nativeStatus, Instant now) {
    if (!AutraStatus.ACCEPTED.equals(nativeStatus)) {
      return Ledger.empty();
    }
    BigDecimal charged = AutraAmounts.numberOf(amount).orElse(BigDecimal.ZERO);
    if (charged.signum() == 0) {
      return Ledger.empty();
    }
    return Ledger.empty()
        .with(
            new MonetaryOperation(
                1,
                OperationKind.CAPTURE,
                Amount.of(amount),
                null,
                null,
                AutraStatus.ACCEPTED,
                NormalizedStatus.SUCCEEDED,
                now));
  }

  private void record(
      AutraApiCall call,
      TestRunId testRunId,
      AutraMerchant merchant,
      @Nullable String orderId,
      Instant now,
      ExchangeVerdict verdict,
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
            AutraOperations.SALE,
            verdict,
            new FingerprintedRequest(
                call.method(),
                AutraOperations.SALE,
                call.body().orElse(AutraJson.empty()).flatten()),
            result.body().asAKeyedAnswer(),
            providerObjectId,
            stateChanged,
            null,
            now));
  }

  private String aPaymentIdClaimedFor(TestRunId testRunId) {
    for (int attempt = 0; attempt < PreAuthorize.PAYMENT_ID_ATTEMPTS; attempt++) {
      String paymentId = AutraIdentifiers.uuidOf(ids);
      if (payments.claimPaymentId(testRunId, paymentId)) {
        return paymentId;
      }
    }
    throw new IllegalStateException(
        "no Autra paymentID could be claimed in " + PreAuthorize.PAYMENT_ID_ATTEMPTS + " attempts");
  }
}
