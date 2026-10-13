package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.RequestFingerprint;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

final class CreateTransaction {

  static final int TRANSACTION_ID_ATTEMPTS = 5;

  static final int LONGEST_DC_DISABLE = 254;

  static final Duration THE_DUPLICATE_CHECK_LOOKS_BACK = Duration.ofHours(24);

  static final String NOT_A_JSON_OBJECT = "the request body is not a JSON object";

  static final String ANOTHER_TERMINAL =
      "terminal_name names a terminal other than the one the X-tranzila-api-app-key belongs to";

  static final String ENTERED_AND_VALID = "1";

  static final String NOT_ENTERED = "0";

  private final TranzilaTransactions transactions;
  private final TranzilaPayerCards cards;
  private final TranzilaExchanges exchanges;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;

  CreateTransaction(
      TranzilaTransactions transactions,
      TranzilaPayerCards cards,
      TranzilaExchanges exchanges,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    this.transactions = transactions;
    this.cards = cards;
    this.exchanges = exchanges;
    this.ids = ids;
    this.scenarios = scenarios;
  }

  record Asked(
      TranzilaApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      TranzilaTerminal terminal,
      TranzilaCreateRequest request,
      TranzilaJson originalRequest,
      Instant now,
      ObjectOrigin askedBy) {

    String accountId() {
      return terminal.accountId();
    }
  }

  record Answered(TranzilaOperationResult result, boolean stored) {

    static Answered refused(TranzilaOperationResult result) {
      return new Answered(result, false);
    }

    Answered mapped(UnaryOperator<TranzilaOperationResult> change) {
      return new Answered(change.apply(result), stored);
    }
  }

  TranzilaOperationResult handle(
      TranzilaApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      TranzilaTerminal terminal,
      Instant now,
      ObjectOrigin askedBy) {
    Optional<TranzilaJson.JsonObject> parsed = call.body();
    if (parsed.isEmpty()) {
      return TranzilaAnswers.refusedBody(
          TranzilaErrors.INVALID_JSON_FORMAT,
          TranzilaErrors.INVALID_JSON_FORMAT_TEXT,
          NOT_A_JSON_OBJECT);
    }
    TranzilaCreateRequest request;
    try {
      request = TranzilaCreateRequest.read(parsed.get());
    } catch (TranzilaSchemaMismatch notTheSchema) {
      return TranzilaAnswers.refusedSchema(notTheSchema.why());
    }
    if (!request.terminalName().equals(terminal.terminalName())) {
      return TranzilaAnswers.refusedCredential(
          TranzilaErrors.AUTHORIZATION_FAILED,
          TranzilaErrors.AUTHORIZATION_FAILED_TEXT,
          ANOTHER_TERMINAL);
    }
    Asked asked =
        new Asked(
            call,
            headers,
            testRunId,
            terminal,
            request,
            TranzilaOriginalRequest.of(parsed.get()),
            now,
            askedBy);
    String dcDisable = request.dcDisable();
    if (dcDisable == null) {
      return observedUnderTheReference(asked, answered(asked));
    }
    if (dcDisable.length() > LONGEST_DC_DISABLE) {
      return TranzilaAnswers.applicationError(
          TranzilaErrors.INVALID_DC_DISABLE, TranzilaRefusals.theBodyWasRefused());
    }
    boolean seen =
        exchanges.howOftenSeen(
                testRunId, terminal.accountId(), TranzilaFields.DC_DISABLE, dcDisable)
            > 0;
    Optional<TranzilaTransaction> original =
        transactions.approvedUnderDcDisable(
            testRunId, terminal.accountId(), dcDisable, now.minus(THE_DUPLICATE_CHECK_LOOKS_BACK));
    if (original.isPresent()) {
      Answered duplicate =
          Answered.refused(TranzilaAnswers.aDuplicateOf(original.get(), asked.originalRequest()));
      observe(
          asked,
          TranzilaFields.DC_DISABLE,
          dcDisable,
          ExchangeVerdict.DUPLICATE_KEY_REFUSAL,
          duplicate);
      return duplicate.result();
    }
    Answered answered = answered(asked);
    observe(
        asked,
        TranzilaFields.DC_DISABLE,
        dcDisable,
        seen ? ExchangeVerdict.REPEAT_ADMITTED : ExchangeVerdict.FIRST_REQUEST,
        answered);
    return observedUnderTheReference(asked, answered);
  }

  private TranzilaOperationResult observedUnderTheReference(Asked asked, Answered answered) {
    Long reference = asked.request().referenceTxnId();
    if (reference != null) {
      String named = reference.toString();
      boolean seen =
          exchanges.howOftenSeen(
                  asked.testRunId(), asked.accountId(), TranzilaFields.REFERENCE_TXN_ID, named)
              > 0;
      observe(
          asked,
          TranzilaFields.REFERENCE_TXN_ID,
          named,
          seen ? ExchangeVerdict.CORRELATED_RETRY : ExchangeVerdict.FIRST_REQUEST,
          answered);
    }
    return answered.result();
  }

  private void observe(
      Asked asked, String keyName, String keyValue, ExchangeVerdict verdict, Answered answered) {
    TranzilaOperationResult result = answered.result();
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return;
    }
    TranzilaBody body = result.body();
    exchanges.observed(
        new TranzilaObservedRequest(
            asked.testRunId(),
            asked.accountId(),
            keyName,
            keyValue,
            asked.request().type().wire(),
            RequestFingerprint.of(
                asked.call().method(),
                asked.request().type().wire(),
                asked.call().body().map(TranzilaJson::flatten).orElseGet(Map::of)),
            verdict,
            body.httpStatus(),
            TranzilaFields.JSON,
            body.text(),
            result.providerObjectId(),
            answered.stored(),
            asked.now()));
  }

  private Answered answered(Asked asked) {
    return switch (asked.request().type()) {
      case DEBIT, VERIFY -> anOriginal(asked);
      case FORCE -> aForce(asked);
      case REVERSAL -> aReversal(asked);
      case CREDIT -> aCredit(asked);
      case CANCEL -> aCancel(asked);
    };
  }

  private Answered anOriginal(Asked asked) {
    TranzilaCreateRequest request = asked.request();
    Optional<Presented> presented = thePresentedCard(asked);
    if (presented.isEmpty()) {
      return theTokenIsUnknown();
    }
    TranzilaCard card = presented.get().card();
    long drawn = TranzilaIdentifiers.aTransactionId(ids);
    String amount = String.valueOf(request.amount() == null ? BigDecimal.ZERO : request.amount());
    String code =
        request.type() == TranzilaTxnType.VERIFY
            ? asked.terminal().verificationApprovalCode().code()
            : TranzilaResponseCodes.APPROVED;
    Instant expires =
        Integer.valueOf(TranzilaTxnType.J5).equals(request.verifyMode())
            ? asked.now().plus(asked.terminal().j5Validity())
            : null;
    return decided(
        asked,
        drawn,
        null,
        amount,
        request.currencyCode(),
        presented.get().token(),
        () ->
            stored(
                asked,
                drawn,
                id ->
                    aTransaction(
                        asked,
                        id,
                        id,
                        null,
                        code,
                        amount,
                        request.currencyCode(),
                        TranzilaIdentifiers.anAuthNumber(ids),
                        presented.get().tokenFor(ids),
                        card,
                        expires)),
        decline ->
            stored(
                asked,
                drawn,
                id ->
                    aTransaction(
                        asked,
                        id,
                        id,
                        null,
                        decline,
                        amount,
                        request.currencyCode(),
                        null,
                        null,
                        card,
                        null)));
  }

  private Answered aForce(Asked asked) {
    TranzilaCreateRequest request = asked.request();
    Optional<Presented> presented = thePresentedCard(asked);
    if (presented.isEmpty()) {
      return theTokenIsUnknown();
    }
    Long reference = request.referenceTxnId();
    if (reference == null) {
      return applicationError(TranzilaErrors.INDEX_WAS_EMPTY, TranzilaRefusals.theBodyWasRefused());
    }
    Optional<TranzilaPayment> found = thePaymentHolding(asked, reference.longValue());
    if (found.isEmpty()) {
      return originalNotFound();
    }
    TranzilaPayment payment = found.get();
    TranzilaTransaction hold = payment.root();
    if (hold.transactionId() != reference.longValue() || !hold.isAJ5() || !hold.approved()) {
      return refusedOn(TranzilaErrors.ORIGINAL_NOT_FOUND, reference.longValue());
    }
    String authorization = request.authorizationNumber();
    if (authorization != null
        && !authorization.isEmpty()
        && !authorization.equals(hold.authNumber())) {
      return refusedOn(TranzilaErrors.AUTHORIZATION_NUMBER_DIFFERS, reference.longValue());
    }
    if (!payment.isAJ5StillHeldAt(asked.now())) {
      return refusedOn(TranzilaErrors.ORIGINAL_NOT_FOUND, reference.longValue());
    }
    String amount = String.valueOf(request.amount());
    TranzilaCard card = presented.get().card();
    long drawn = TranzilaIdentifiers.aTransactionId(ids);
    if (TranzilaAmounts.isAbove(amount, hold.amount())) {
      return stored(
          asked,
          drawn,
          id ->
              aTransaction(
                  asked,
                  id,
                  hold.transactionId(),
                  reference,
                  TranzilaResponseCodes.HIGHER_THAN_THE_APPROVED_AMOUNT,
                  amount,
                  hold.currencyCode(),
                  null,
                  null,
                  card,
                  null));
    }
    return decided(
        asked,
        drawn,
        hold,
        amount,
        hold.currencyCode(),
        presented.get().token(),
        () ->
            stored(
                asked,
                drawn,
                id ->
                    aTransaction(
                        asked,
                        id,
                        hold.transactionId(),
                        reference,
                        TranzilaResponseCodes.APPROVED,
                        amount,
                        hold.currencyCode(),
                        TranzilaIdentifiers.anAuthNumber(ids),
                        presented.get().tokenFor(ids),
                        card,
                        null)),
        decline ->
            stored(
                asked,
                drawn,
                id ->
                    aTransaction(
                        asked,
                        id,
                        hold.transactionId(),
                        reference,
                        decline,
                        amount,
                        hold.currencyCode(),
                        null,
                        null,
                        card,
                        null)));
  }

  private Answered aReversal(Asked asked) {
    return switch (theReferencedOriginal(asked)) {
      case Reference.Refused refused -> refused.answer();
      case Reference.Found found -> aReversalOf(asked, found.payment(), found.target());
    };
  }

  private Answered aReversalOf(Asked asked, TranzilaPayment payment, TranzilaTransaction hold) {
    if (!hold.isTheOriginal() || !hold.isAJ5() || !payment.isAJ5StillHeldAt(asked.now())) {
      return refusedOn(TranzilaErrors.ORIGINAL_NOT_FOUND, hold.transactionId());
    }
    return aFollowOnOf(asked, payment, hold, hold.amount(), hold.card(), hold.token());
  }

  private Answered aCredit(Asked asked) {
    Optional<Presented> presented = thePresentedCard(asked);
    if (presented.isEmpty()) {
      return theTokenIsUnknown();
    }
    return switch (theReferencedOriginal(asked)) {
      case Reference.Refused refused -> refused.answer();
      case Reference.Found found ->
          aCreditOf(asked, found.payment(), found.target(), presented.get());
    };
  }

  private Answered aCreditOf(
      Asked asked, TranzilaPayment payment, TranzilaTransaction original, Presented presented) {
    boolean creditable =
        (original.isTheOriginal() && original.type() == TranzilaTxnType.DEBIT)
            || original.type() == TranzilaTxnType.FORCE;
    if (!creditable) {
      return refusedOn(TranzilaErrors.ORIGINAL_NOT_FOUND, original.transactionId());
    }
    if (payment.isCancelled(original)) {
      return refusedOn(TranzilaErrors.ALREADY_CANCELED, original.transactionId());
    }
    if (!presented.card().isTheSameCardAs(original.card())) {
      return refusedOn(TranzilaErrors.ORIGINAL_NOT_FOUND, original.transactionId());
    }
    String amount = String.valueOf(asked.request().amount());
    BigDecimal total = payment.theCreditsAgainst(original).add(new BigDecimal(amount));
    if (total.compareTo(original.theAmount()) > 0) {
      return refusedOn(TranzilaErrors.ALREADY_CREDITED, original.transactionId());
    }
    return aFollowOnOf(asked, payment, original, amount, presented.card(), presented.token());
  }

  private Answered aCancel(Asked asked) {
    return switch (theReferencedOriginal(asked)) {
      case Reference.Refused refused -> refused.answer();
      case Reference.Found found -> aCancelOf(asked, found.payment(), found.target());
    };
  }

  private Answered aCancelOf(Asked asked, TranzilaPayment payment, TranzilaTransaction target) {
    boolean cancellable =
        (target.isTheOriginal() && target.type() == TranzilaTxnType.DEBIT)
            || target.type() == TranzilaTxnType.FORCE
            || target.type() == TranzilaTxnType.CREDIT;
    if (!cancellable || payment.isCancelled(target)) {
      return refusedOn(TranzilaErrors.ORIGINAL_NOT_FOUND, target.transactionId());
    }
    return aFollowOnOf(asked, payment, target, target.amount(), target.card(), target.token());
  }

  private Answered aFollowOnOf(
      Asked asked,
      TranzilaPayment payment,
      TranzilaTransaction target,
      String amount,
      TranzilaCard card,
      @Nullable String token) {
    long drawn = TranzilaIdentifiers.aTransactionId(ids);
    long root = payment.root().transactionId();
    Long reference = Long.valueOf(target.transactionId());
    String currency = target.currencyCode();
    return decided(
        asked,
        drawn,
        payment.root(),
        amount,
        currency,
        token,
        () ->
            stored(
                asked,
                drawn,
                id ->
                    aTransaction(
                        asked,
                        id,
                        root,
                        reference,
                        TranzilaResponseCodes.APPROVED,
                        amount,
                        currency,
                        TranzilaIdentifiers.anAuthNumber(ids),
                        token,
                        card,
                        null)),
        decline ->
            stored(
                asked,
                drawn,
                id ->
                    aTransaction(
                        asked, id, root, reference, decline, amount, currency, null, null, card,
                        null)));
  }

  private Reference theReferencedOriginal(Asked asked) {
    TranzilaCreateRequest request = asked.request();
    Long reference = request.referenceTxnId();
    if (reference == null) {
      return new Reference.Refused(
          applicationError(TranzilaErrors.INDEX_WAS_EMPTY, TranzilaRefusals.theBodyWasRefused()));
    }
    String authorization = request.authorizationNumber();
    if (authorization == null || authorization.isEmpty()) {
      return new Reference.Refused(
          applicationError(
              TranzilaErrors.AUTHORIZATION_NUMBER_EMPTY, TranzilaRefusals.theBodyWasRefused()));
    }
    Optional<TranzilaPayment> found = thePaymentHolding(asked, reference.longValue());
    if (found.isEmpty()) {
      return new Reference.Refused(originalNotFound());
    }
    TranzilaTransaction target = found.get().transaction(reference.longValue()).orElseThrow();
    if (!target.approved()) {
      return new Reference.Refused(
          refusedOn(TranzilaErrors.ORIGINAL_NOT_FOUND, reference.longValue()));
    }
    if (!authorization.equals(target.authNumber())) {
      return new Reference.Refused(
          refusedOn(TranzilaErrors.AUTHORIZATION_NUMBER_DIFFERS, reference.longValue()));
    }
    return new Reference.Found(found.get(), target);
  }

  sealed interface Reference {

    record Found(TranzilaPayment payment, TranzilaTransaction target) implements Reference {}

    record Refused(Answered answer) implements Reference {}
  }

  private Optional<TranzilaPayment> thePaymentHolding(Asked asked, long transactionId) {
    return transactions
        .paymentHolding(asked.testRunId(), transactionId)
        .filter(payment -> payment.root().accountId().equals(asked.accountId()));
  }

  record Presented(TranzilaCard card, @Nullable String token) {

    String tokenFor(IdGenerator ids) {
      String named = token;
      return named == null ? TranzilaIdentifiers.aTokenFor(ids, card.last4()) : named;
    }
  }

  private Optional<Presented> thePresentedCard(Asked asked) {
    TranzilaCreateRequest request = asked.request();
    String cardNumber = String.valueOf(request.cardNumber());
    int month = java.util.Objects.requireNonNull(request.expireMonth()).intValue();
    int year = java.util.Objects.requireNonNull(request.expireYear()).intValue();
    if (!request.namesAToken()) {
      return Optional.of(new Presented(TranzilaCard.ofThePan(cardNumber, month, year), null));
    }
    Optional<TranzilaCard> known =
        transactions
            .carryingTheToken(asked.testRunId(), asked.accountId(), cardNumber)
            .map(TranzilaTransaction::card)
            .or(() -> cards.holdingTheToken(asked.testRunId(), cardNumber));
    return known.map(
        card ->
            new Presented(
                new TranzilaCard(card.cardMask(), card.cardType(), month, year % 100), cardNumber));
  }

  private Answered decided(
      Asked asked,
      long drawn,
      @Nullable TranzilaTransaction original,
      String amount,
      String currency,
      @Nullable String token,
      Supplier<Answered> approve,
      Function<String, Answered> decline) {
    ScenarioDecision decision =
        scenarios.decide(
            new ScenarioRequest(
                TranzilaApi.TRANZILA,
                asked.testRunId(),
                TranzilaStoryKey.ofPayment(original == null ? drawn : original.transactionId()),
                asked.accountId(),
                null,
                original == null ? null : String.valueOf(asked.request().referenceTxnId()),
                null,
                token,
                Amount.of(amount),
                currency,
                asked.request().type().wire(),
                asked.headers()));
    return switch (decision) {
      case ScenarioDecision.NoScenario _ -> approve.get();
      case ScenarioDecision.Proceed matched ->
          approve.get().mapped(result -> result.proceededUnder(matched.scenarioId()));
      case ScenarioDecision.Disrupt disrupted ->
          approve
              .get()
              .mapped(result -> result.faultedBy(disrupted.scenarioId(), disrupted.fault()));
      case ScenarioDecision.Preempt preempted ->
          Answered.refused(
              TranzilaOperationResult.answeredInsteadOf(
                  preempted.scenarioId(), preempted.answer()));
      case ScenarioDecision.Fail declined ->
          decline
              .apply(declined.providerCode())
              .mapped(result -> result.declinedBy(declined.scenarioId()));
      case ScenarioDecision.Pend pending -> throw pending.theOperationAnswersNoProcessingState();
    };
  }

  private Answered stored(
      Asked asked, long drawn, LongFunction<TranzilaTransaction> transactionOf) {
    long id = drawn;
    for (int attempt = 0; attempt < TRANSACTION_ID_ATTEMPTS; attempt++) {
      TranzilaTransaction transaction = transactionOf.apply(id);
      TranzilaInsert inserted = transactions.insert(transaction);
      if (inserted == TranzilaInsert.STORED) {
        return new Answered(
            transaction.approved()
                ? TranzilaAnswers.approved(transaction, asked.originalRequest())
                : TranzilaAnswers.declined(transaction),
            true);
      }
      if (inserted == TranzilaInsert.CLOSED_BY_ANOTHER_REQUEST_FIRST) {
        Long reference = transaction.referenceTxnId();
        return refusedOn(
            TranzilaErrors.ORIGINAL_NOT_FOUND, reference == null ? id : reference.longValue());
      }
      id = TranzilaIdentifiers.aTransactionId(ids);
    }
    throw new IllegalStateException(
        "no Tranzila transaction_id could be claimed in " + TRANSACTION_ID_ATTEMPTS + " attempts");
  }

  private TranzilaTransaction aTransaction(
      Asked asked,
      long transactionId,
      long rootTxnId,
      @Nullable Long referenceTxnId,
      String code,
      String amount,
      String currency,
      @Nullable String authNumber,
      @Nullable String token,
      TranzilaCard card,
      @Nullable Instant holdExpiresAt) {
    TranzilaCreateRequest request = asked.request();
    return new TranzilaTransaction(
        asked.testRunId(),
        transactionId,
        rootTxnId,
        asked.accountId(),
        asked.terminal().terminalName(),
        request.type(),
        request.verifyMode(),
        referenceTxnId,
        code,
        amount,
        currency,
        authNumber,
        token,
        card,
        request.cvv() == null ? NOT_ENTERED : ENTERED_AND_VALID,
        request.cardHolderId() == null ? NOT_ENTERED : ENTERED_AND_VALID,
        request.dcDisable(),
        holdExpiresAt,
        asked.askedBy(),
        asked.now());
  }

  private static Answered theTokenIsUnknown() {
    return applicationError(
        TranzilaErrors.TOKEN_CHECK_FAILURE, TranzilaRefusals.theBodyWasRefused());
  }

  private Answered originalNotFound() {
    return applicationError(
        TranzilaErrors.ORIGINAL_NOT_FOUND, TranzilaRefusals.noOriginalAnswered());
  }

  private static Answered refusedOn(int code, long transactionId) {
    return applicationError(code, TranzilaRefusals.theTransaction(transactionId));
  }

  private static Answered applicationError(
      int code, dev.marcinromanowski.pspsim.kernel.RefusalSubject refusal) {
    return Answered.refused(TranzilaAnswers.applicationError(code, refusal));
  }
}
