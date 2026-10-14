package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

final class CreateTransaction {

  static final int TRANSACTION_ID_ATTEMPTS = 5;

  static final Set<String> THE_CARD_FIELDS_NO_FINGERPRINT_READS =
      Set.of(
          TranzilaFields.CARD_NUMBER,
          TranzilaFields.CVV,
          TranzilaFields.CARD_HOLDER_ID,
          TranzilaFields.EXPIRE_MONTH,
          TranzilaFields.EXPIRE_YEAR);

  static final int LONGEST_DC_DISABLE = 254;

  static final Duration THE_DUPLICATE_CHECK_LOOKS_BACK = Duration.ofHours(24);

  static final String NOT_A_JSON_OBJECT = "the request body is not a JSON object";

  static final String ANOTHER_TERMINAL =
      "terminal_name names a terminal other than the one the X-tranzila-api-app-key belongs to";

  static final String ENTERED_AND_VALID = "1";

  static final String NOT_ENTERED = "0";

  private final TranzilaTransactions transactions;
  private final TranzilaPages pages;
  private final TranzilaPayerCards cards;
  private final TranzilaExchanges exchanges;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;
  private final TranzilaChallenges challenges;
  private final String publicBaseUrl;

  CreateTransaction(
      TranzilaTransactions transactions,
      TranzilaPages pages,
      TranzilaPayerCards cards,
      TranzilaExchanges exchanges,
      IdGenerator ids,
      ScenarioEngine scenarios,
      TranzilaChallenges challenges,
      String publicBaseUrl) {
    this.transactions = transactions;
    this.pages = pages;
    this.cards = cards;
    this.exchanges = exchanges;
    this.ids = ids;
    this.scenarios = scenarios;
    this.challenges = challenges;
    this.publicBaseUrl = publicBaseUrl;
  }

  record Asked(
      TranzilaApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      TranzilaTerminal terminal,
      TranzilaCreateRequest request,
      TranzilaJson originalRequest,
      Instant now,
      ObjectOrigin askedBy,
      Map<String, TranzilaCard> cardsThePageOffers,
      @Nullable TranzilaThreeDSecureOutcome theTypedCardsOutcome,
      @Nullable TranzilaHostedFieldsEcho hostedFields) {

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
    return handle(call, headers, testRunId, terminal, now, askedBy, Map.of());
  }

  TranzilaOperationResult handle(
      TranzilaApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      TranzilaTerminal terminal,
      Instant now,
      ObjectOrigin askedBy,
      Map<String, TranzilaCard> cardsThePageOffers) {
    return handle(call, headers, testRunId, terminal, now, askedBy, cardsThePageOffers, null, null);
  }

  TranzilaOperationResult handle(
      TranzilaApiCall call,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      TranzilaTerminal terminal,
      Instant now,
      ObjectOrigin askedBy,
      Map<String, TranzilaCard> cardsThePageOffers,
      @Nullable TranzilaThreeDSecureOutcome theTypedCardsOutcome,
      @Nullable TranzilaHostedFieldsEcho hostedFields) {
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
            askedBy,
            cardsThePageOffers,
            theTypedCardsOutcome,
            hostedFields);
    Optional<Answered> inAnotherCurrency = theCurrencyRefusalOf(asked);
    if (inAnotherCurrency.isPresent()) {
      return inAnotherCurrency.get().result();
    }
    String dcDisable = request.dcDisable();
    if (dcDisable == null) {
      return transactions.together(() -> observedUnderTheReference(asked, answered(asked)));
    }
    if (dcDisable.length() > LONGEST_DC_DISABLE) {
      return TranzilaAnswers.applicationError(
          TranzilaErrors.INVALID_DC_DISABLE, TranzilaRefusals.theBodyWasRefused());
    }
    TranzilaDcDisableKey key =
        new TranzilaDcDisableKey(
            testRunId, terminal.accountId(), dcDisable, request.type(), request.verifyMode());
    return transactions.oneAtATimeUnder(key, () -> underTheDcDisable(asked, key));
  }

  private TranzilaOperationResult underTheDcDisable(Asked asked, TranzilaDcDisableKey key) {
    Optional<TranzilaTransaction> original =
        transactions.approvedUnderDcDisable(key, asked.now().minus(THE_DUPLICATE_CHECK_LOOKS_BACK));
    if (original.isPresent()) {
      Answered duplicate =
          Answered.refused(TranzilaAnswers.aDuplicateOf(original.get(), asked.originalRequest()));
      observe(
          asked,
          key.exchangeKeyName(),
          key.dcDisable(),
          KeyedOutcome.REFUSED_AS_A_DUPLICATE,
          duplicate);
      return duplicate.result();
    }
    Answered answered = answered(asked);
    observe(
        asked, key.exchangeKeyName(), key.dcDisable(), KeyedOutcome.RAN_THE_OPERATION, answered);
    return observedUnderTheReference(asked, answered);
  }

  private TranzilaOperationResult observedUnderTheReference(Asked asked, Answered answered) {
    Long reference = asked.request().referenceTxnId();
    if (reference != null) {
      String named = reference.toString();
      observe(
          asked, TranzilaFields.REFERENCE_TXN_ID, named, KeyedOutcome.RAN_THE_OPERATION, answered);
    }
    return answered.result();
  }

  private void observe(
      Asked asked, String keyName, String keyValue, KeyedOutcome outcome, Answered answered) {
    TranzilaOperationResult result = answered.result();
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return;
    }
    exchanges.observed(
        new KeyedObservation(
            asked.testRunId(),
            asked.accountId(),
            keyName,
            keyValue,
            asked.request().type().wire(),
            outcome,
            new FingerprintedRequest(
                asked.call().method(),
                asked.request().type().wire(),
                theFieldsAFingerprintReads(asked.call())),
            result.body().asAKeyedAnswer(),
            result.providerObjectId(),
            answered.stored(),
            null,
            asked.now()));
  }

  static Map<String, List<String>> theFieldsAFingerprintReads(TranzilaApiCall call) {
    Map<String, List<String>> read =
        new LinkedHashMap<>(
            call.body()
                .map(TranzilaUserField::withTheIdentitiesWithheld)
                .map(TranzilaJson::flatten)
                .orElseGet(Map::of));
    read.keySet().removeAll(THE_CARD_FIELDS_NO_FINGERPRINT_READS);
    return read;
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
    Optional<Presented> presented = thePresentedCard(asked);
    if (presented.isEmpty()) {
      return theTokenIsUnknown();
    }
    long drawn = TranzilaIdentifiers.aTransactionId(ids);
    TranzilaThreeDSecure secure = asked.request().threeDSecure();
    if (secure == null) {
      return charged(asked, presented.get(), drawn);
    }
    return authenticated(asked, presented.get(), secure, drawn);
  }

  private Answered charged(Asked asked, Presented presented, long drawn) {
    TranzilaCreateRequest request = asked.request();
    TranzilaCard card = presented.card();
    String amount = theAmountOf(request);
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
        presented.token(),
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
                        presented.tokenFor(ids),
                        card,
                        expires)),
        decline -> unauthenticated(asked, presented, drawn, decline));
  }

  private Answered unauthenticated(Asked asked, Presented presented, long drawn, String code) {
    TranzilaCreateRequest request = asked.request();
    String amount = theAmountOf(request);
    return stored(
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
                null,
                null,
                presented.card(),
                null));
  }

  private static String theAmountOf(TranzilaCreateRequest request) {
    return String.valueOf(request.amount() == null ? BigDecimal.ZERO : request.amount());
  }

  private Answered authenticated(
      Asked asked, Presented presented, TranzilaThreeDSecure secure, long drawn) {
    Optional<TranzilaThreeDSecureOutcome> published = thePublishedOutcomeOf(asked, presented);
    TranzilaThreeDSecureOutcome asked3ds =
        published.orElse(
            secure.forceChallenge()
                ? TranzilaThreeDSecureOutcome.CHALLENGED
                : TranzilaThreeDSecureOutcome.AUTHENTICATED);
    if (published.isPresent()) {
      return afterTheAuthentication(
          asked, presented, secure, drawn, asked3ds, TranzilaResponseCodes.THREE_D_SECURE_FAILED);
    }
    ScenarioDecision decision =
        scenarios.decide(
            theScenarioRequest(
                asked,
                drawn,
                null,
                theAmountOf(asked.request()),
                asked.request().currencyCode(),
                presented.token(),
                TranzilaOperations.THREE_D_SECURE));
    return switch (decision) {
      case ScenarioDecision.NoScenario _ ->
          afterTheAuthentication(
              asked,
              presented,
              secure,
              drawn,
              asked3ds,
              TranzilaResponseCodes.THREE_D_SECURE_FAILED);
      case ScenarioDecision.Proceed matched ->
          afterTheAuthentication(
                  asked,
                  presented,
                  secure,
                  drawn,
                  asked3ds,
                  TranzilaResponseCodes.THREE_D_SECURE_FAILED)
              .mapped(result -> result.proceededUnder(matched.scenarioId()));
      case ScenarioDecision.Disrupt disrupted ->
          afterTheAuthentication(
                  asked,
                  presented,
                  secure,
                  drawn,
                  asked3ds,
                  TranzilaResponseCodes.THREE_D_SECURE_FAILED)
              .mapped(result -> result.faultedBy(disrupted.scenarioId(), disrupted.fault()));
      case ScenarioDecision.Preempt preempted ->
          Answered.refused(
              TranzilaOperationResult.answeredInsteadOf(
                  preempted.scenarioId(), preempted.answer()));
      case ScenarioDecision.Fail declined ->
          afterTheAuthentication(
                  asked,
                  presented,
                  secure,
                  drawn,
                  TranzilaThreeDSecureOutcome.FAILED,
                  declined.providerCode())
              .mapped(result -> result.declinedBy(declined.scenarioId()));
      case ScenarioDecision.Pend pending -> throw pending.theOperationAnswersNoProcessingState();
    };
  }

  Optional<TranzilaThreeDSecureOutcome> thePublishedOutcomeOf(Asked asked, Presented presented) {
    TranzilaThreeDSecureOutcome typed = asked.theTypedCardsOutcome();
    if (typed != null) {
      return Optional.of(typed);
    }
    if (!asked.request().namesAToken()) {
      return TranzilaThreeDSecureCards.ofThePan(String.valueOf(asked.request().cardNumber()));
    }
    return TranzilaThreeDSecureCards.ofTheCard(presented.card());
  }

  private Answered afterTheAuthentication(
      Asked asked,
      Presented presented,
      TranzilaThreeDSecure secure,
      long drawn,
      TranzilaThreeDSecureOutcome outcome,
      String failure) {
    return switch (outcome) {
      case AUTHENTICATED ->
          charged(asked, presented, drawn)
              .mapped(
                  result ->
                      TranzilaThreeDSecureAnswers.withTheAuthentication(
                          result, TranzilaThreeDSecureAnswers.theAuthentication(ids)));
      case FAILED ->
          secure.forceTxnOnFail()
              ? charged(asked, presented, drawn)
              : unauthenticated(asked, presented, drawn, failure);
      case CHALLENGED -> challenged(asked, presented, secure, drawn);
    };
  }

  private Answered challenged(
      Asked asked, Presented presented, TranzilaThreeDSecure secure, long drawn) {
    TranzilaCreateRequest request = asked.request();
    String token = presented.tokenFor(ids);
    for (int attempt = 0; attempt < TRANSACTION_ID_ATTEMPTS; attempt++) {
      TranzilaChallenge challenge =
          new TranzilaChallenge(
              TranzilaChallenge.ID_LEAD + ids.hex(TranzilaChallenge.ID_HEX),
              asked.testRunId(),
              asked.accountId(),
              asked.terminal().terminalName(),
              drawn,
              request.type(),
              request.verifyMode(),
              theAmountOf(request),
              request.currencyCode(),
              token,
              presented.card(),
              request.cvv() == null ? NOT_ENTERED : ENTERED_AND_VALID,
              request.cardHolderId() == null ? NOT_ENTERED : ENTERED_AND_VALID,
              request.dcDisable(),
              secure.forceTxnOnFail(),
              TranzilaJsonWriter.toText(asked.originalRequest()),
              secure.redirectUrl(),
              secure.redirectParams(),
              asked.hostedFields(),
              asked.askedBy(),
              asked.now(),
              null,
              null,
              null);
      if (challenges.open(challenge) == 1) {
        return new Answered(
            TranzilaThreeDSecureAnswers.aChallenge(
                TranzilaChallengePage.addressOf(publicBaseUrl, challenge.trackId()),
                challenge.trackId()),
            true);
      }
    }
    throw new IllegalStateException(
        "no Tranzila track_id could be claimed in " + TRANSACTION_ID_ATTEMPTS + " attempts");
  }

  TranzilaOperationResult completing(
      TranzilaApiCall call,
      Map<String, List<String>> headers,
      TranzilaTerminal terminal,
      TranzilaChallenge challenge,
      Instant now) {
    TranzilaJson.JsonObject originalRequest = CompleteThreeDSecure.theOriginalRequestOf(challenge);
    Asked asked =
        new Asked(
            call,
            headers,
            challenge.testRunId(),
            terminal,
            TranzilaCreateRequest.ofTheChallenge(challenge, originalRequest),
            originalRequest,
            now,
            challenge.origin(),
            Map.of(challenge.token(), challenge.card()),
            null,
            challenge.hostedFields());
    Presented presented = new Presented(challenge.card(), challenge.token());
    Answered answered =
        challenge.passed() || challenge.forceTxnOnFail()
            ? charged(asked, presented, challenge.transactionId())
            : unauthenticated(
                asked,
                presented,
                challenge.transactionId(),
                TranzilaResponseCodes.THREE_D_SECURE_FAILED);
    return answered.result();
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
      return uncreditable(reference.longValue());
    }
    String authorization = request.authorizationNumber();
    if (authorization != null
        && !authorization.isEmpty()
        && !authorization.equals(hold.authNumber())) {
      return refusedOn(TranzilaErrors.AUTHORIZATION_NUMBER_DIFFERS, reference.longValue());
    }
    if (!payment.isAJ5StillHeldAt(asked.now())) {
      return uncreditable(reference.longValue());
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
      return uncreditable(hold.transactionId());
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
      return uncreditable(original.transactionId());
    }
    if (payment.isCancelled(original)) {
      return refusedOn(TranzilaErrors.ALREADY_CANCELED, original.transactionId());
    }
    if (!presented.card().isTheSameCardAs(original.card())) {
      return uncreditable(original.transactionId());
    }
    if (payment.theCreditOf(original).isPresent()) {
      return alreadyCredited(original.transactionId());
    }
    String amount = String.valueOf(asked.request().amount());
    if (TranzilaAmounts.isAbove(amount, original.amount())) {
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
    if (!cancellable || payment.isCancelled(target) || payment.theCreditOf(target).isPresent()) {
      return uncreditable(target.transactionId());
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
      return new Reference.Refused(uncreditable(reference.longValue()));
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
    int month = Objects.requireNonNull(request.expireMonth()).intValue();
    int year = Objects.requireNonNull(request.expireYear()).intValue();
    if (!request.namesAToken()) {
      return Optional.of(new Presented(TranzilaCard.ofThePan(cardNumber, month, year), null));
    }
    Optional<TranzilaCard> known =
        transactions
            .carryingTheToken(asked.testRunId(), asked.accountId(), cardNumber)
            .map(TranzilaTransaction::card)
            .or(() -> Optional.ofNullable(asked.cardsThePageOffers().get(cardNumber)))
            .or(
                () ->
                    pages
                        .savingTheToken(asked.testRunId(), asked.accountId(), cardNumber)
                        .flatMap(page -> TranzilaBrowserReturn.theCardOf(page, null)))
            .or(() -> cards.holdingTheToken(asked.testRunId(), cardNumber));
    return known.map(card -> new Presented(card.expiringIn(month, year), cardNumber));
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
            theScenarioRequest(
                asked, drawn, original, amount, currency, token, asked.request().type().wire()));
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

  private static ScenarioRequest theScenarioRequest(
      Asked asked,
      long drawn,
      @Nullable TranzilaTransaction original,
      String amount,
      String currency,
      @Nullable String token,
      String operation) {
    return new ScenarioRequest(
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
        operation,
        asked.headers());
  }

  private Answered stored(
      Asked asked, long drawn, LongFunction<TranzilaTransaction> transactionOf) {
    return stored(asked, drawn, transactionOf, () -> theLoserOfARace(asked));
  }

  private Answered stored(
      Asked asked,
      long drawn,
      LongFunction<TranzilaTransaction> transactionOf,
      Supplier<Answered> closedMeanwhile) {
    long id = drawn;
    for (int attempt = 0; attempt < TRANSACTION_ID_ATTEMPTS; attempt++) {
      TranzilaTransaction transaction = transactionOf.apply(id);
      TranzilaInsert inserted =
          transactions.insert(
              transaction, asked.request().userDefinedFields(), asked.request().items());
      if (inserted == TranzilaInsert.STORED) {
        return new Answered(
            transaction.approved()
                ? TranzilaAnswers.approved(transaction, asked.originalRequest())
                : TranzilaAnswers.declined(transaction),
            true);
      }
      if (inserted == TranzilaInsert.CLOSED_BY_ANOTHER_REQUEST_FIRST) {
        return closedMeanwhile.get();
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

  private Answered theLoserOfARace(Asked asked) {
    Long reference = Objects.requireNonNull(asked.request().referenceTxnId());
    if (asked.request().type() != TranzilaTxnType.CREDIT) {
      return uncreditable(reference.longValue());
    }
    boolean cancelled =
        thePaymentHolding(asked, reference.longValue())
            .flatMap(
                payment -> payment.transaction(reference.longValue()).flatMap(payment::theCancelOf))
            .isPresent();
    return cancelled
        ? refusedOn(TranzilaErrors.ALREADY_CANCELED, reference.longValue())
        : alreadyCredited(reference.longValue());
  }

  private Optional<Answered> theCurrencyRefusalOf(Asked asked) {
    Long reference = asked.request().referenceTxnId();
    if (reference == null) {
      return Optional.empty();
    }
    return thePaymentHolding(asked, reference.longValue())
        .flatMap(payment -> payment.transaction(reference.longValue()))
        .filter(original -> !asked.request().isInTheCurrencyOf(original))
        .map(CreateTransaction::anotherCurrency);
  }

  private static Answered anotherCurrency(TranzilaTransaction original) {
    return Answered.refused(
        TranzilaAnswers.refusedSchema(
            TranzilaFields.TXN_CURRENCY_CODE
                + " must be "
                + original.currencyCode()
                + ", the currency of the transaction reference_txn_id names"));
  }

  private static Answered alreadyCredited(long transactionId) {
    return Answered.refused(
        TranzilaAnswers.applicationError(
            TranzilaErrors.ALREADY_CREDITED,
            TranzilaErrors.ALREADY_CREDITED_TEXT,
            TranzilaRefusals.theTransaction(transactionId)));
  }

  private static Answered uncreditable(long transactionId) {
    return Answered.refused(
        TranzilaAnswers.applicationError(
            TranzilaErrors.ORIGINAL_NOT_FOUND,
            TranzilaErrors.AN_UNCREDITABLE_ORIGIN_TEXT,
            TranzilaRefusals.theTransaction(transactionId)));
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

  private static Answered applicationError(int code, RefusalSubject refusal) {
    return Answered.refused(TranzilaAnswers.applicationError(code, refusal));
  }
}
