package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

final class Authorize {

  static final int ID_ATTEMPTS = 5;

  static final int INT_REF_LENGTH = 16;

  static final int RRN_DIGITS = 12;

  static final int APPROVAL_DIGITS = 6;

  private final AzericardPayments payments;
  private final AzericardStoredCards cards;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;

  Authorize(
      AzericardPayments payments,
      AzericardStoredCards cards,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    this.payments = payments;
    this.cards = cards;
    this.ids = ids;
    this.scenarios = scenarios;
  }

  AzericardOperationResult handle(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      ObjectOrigin askedBy) {
    return handle(
        message,
        headers,
        testRunId,
        terminal,
        now,
        askedBy,
        message.presentedCard(now),
        () -> claimTheTraceNumber(message, testRunId, terminal, now));
  }

  AzericardOperationResult aDuplicate(
      AzericardMessage message, TestRunId testRunId, AzericardTerminal terminal, Instant now) {
    return duplicate(message, testRunId, terminal, now);
  }

  AzericardOperationResult handle(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      ObjectOrigin askedBy,
      AzericardPresentedCard presented,
      BooleanSupplier traceClaim) {
    String storedCard = null;
    if (message.paysWithAStoredCard()) {
      Optional<AzericardStoredCard> usable = theCardItWasSent(message, testRunId, terminal);
      if (usable.isEmpty()) {
        return refused(
            RefusalSubject.noObjectAnsweredTo(AzericardFields.TOKEN),
            message,
            terminal,
            now,
            AzericardOutcomes.RC_NO_SUCH_TOKEN);
      }
      if (!theReferenceItWasStoredWithMatches(usable.get(), message)) {
        return refused(
            RefusalSubject.noObjectAnsweredTo(AzericardFields.EXT_NET_REF),
            message,
            terminal,
            now,
            AzericardOutcomes.RC_NO_SUCH_TOKEN);
      }
      storedCard = usable.get().token();
    }
    String paidWith = storedCard;
    String last4 = presented.last4();
    if (presented instanceof AzericardPresentedCard.Declined) {
      return decline(
          message,
          testRunId,
          terminal,
          now,
          AzericardOutcomes.RC_CARD_NOT_ACCEPTED,
          paidWith,
          askedBy,
          last4,
          traceClaim);
    }
    return AzericardOperationResult.decidedBy(
        scenarios.decide(requestOf(message, headers, testRunId, terminal, paidWith)),
        () -> approve(message, testRunId, terminal, now, paidWith, askedBy, last4, traceClaim),
        code ->
            decline(message, testRunId, terminal, now, code, paidWith, askedBy, last4, traceClaim));
  }

  private static boolean theReferenceItWasStoredWithMatches(
      AzericardStoredCard card, AzericardMessage message) {
    String stored = card.extNetRef();
    return !message.isMerchantInitiated() || stored == null || stored.equals(message.extNetRef());
  }

  private Optional<AzericardStoredCard> theCardItWasSent(
      AzericardMessage message, TestRunId testRunId, AzericardTerminal terminal) {
    return cards
        .findByToken(testRunId, message.token())
        .filter(card -> terminal.mayUseStoredCardFrom(card.terminalId()));
  }

  private static ScenarioRequest requestOf(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      @Nullable String paidWith) {
    return new ScenarioRequest(
        AzericardGateway.AZERICARD,
        testRunId,
        AzericardStoryKey.ofPayment(terminal.terminalId(), message.order()),
        terminal.accountId(),
        message.order(),
        null,
        null,
        paidWith,
        Amount.of(message.amount()),
        message.currency(),
        message.operationName(),
        headers);
  }

  private AzericardOperationResult approve(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      @Nullable String paidWith,
      ObjectOrigin askedBy,
      @Nullable String last4,
      BooleanSupplier traceClaim) {
    if (!traceClaim.getAsBoolean()) {
      return duplicate(message, testRunId, terminal, now);
    }
    String intRef = anIntRefClaimedForThisPayment(testRunId);
    String rrn = ids.digits(RRN_DIGITS);
    String approval = ids.digits(APPROVAL_DIGITS);
    String extNetRef =
        message.storesACardForMerchantInitiatedPayments()
            ? AzericardStoredCard.anExtNetRef(ids)
            : null;
    String storedCard =
        message.storesACard()
            ? aTokenClaimedForTheCardItStores(message, testRunId, terminal, intRef, now, extNetRef)
            : paidWith;
    stored(
        message,
        testRunId,
        terminal,
        now,
        intRef,
        rrn,
        approval,
        AzericardOutcomes.ACTION_SUCCEEDED,
        AzericardOutcomes.RC_APPROVED,
        storedCard,
        message.storesACard() ? storedCard : null,
        askedBy,
        last4,
        extNetRef);
    AzericardOperationResult result =
        AzericardOperationResult.succeeded(
            intRef,
            answer(
                message,
                terminal,
                now,
                AzericardOutcomes.DESC_APPROVED,
                AzericardOutcomes.ACTION_SUCCEEDED,
                AzericardOutcomes.RC_APPROVED,
                approval,
                rrn,
                intRef));
    theTraceItClaimed(message, testRunId, terminal, now, intRef, result);
    return result;
  }

  private AzericardOperationResult decline(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      String responseCode,
      @Nullable String paidWith,
      ObjectOrigin askedBy,
      @Nullable String last4,
      BooleanSupplier traceClaim) {
    if (!traceClaim.getAsBoolean()) {
      return duplicate(message, testRunId, terminal, now);
    }
    String intRef = anIntRefClaimedForThisPayment(testRunId);
    String rrn = ids.digits(RRN_DIGITS);
    stored(
        message,
        testRunId,
        terminal,
        now,
        intRef,
        rrn,
        null,
        AzericardOutcomes.ACTION_DECLINED,
        responseCode,
        paidWith,
        null,
        askedBy,
        last4,
        null);
    AzericardOperationResult result =
        AzericardOperationResult.failed(
            RefusalSubject.theObject(intRef),
            answer(
                message,
                terminal,
                now,
                AzericardOutcomes.NOTHING_PUBLISHED,
                AzericardOutcomes.ACTION_DECLINED,
                responseCode,
                AzericardOutcomes.ABSENT,
                rrn,
                intRef));
    theTraceItClaimed(message, testRunId, terminal, now, intRef, result);
    return result;
  }

  private AzericardOperationResult duplicate(
      AzericardMessage message, TestRunId testRunId, AzericardTerminal terminal, Instant now) {
    AzericardOperationResult result =
        AzericardOperationResult.failed(
            RefusalSubject.anObjectItWillNotName(
                AzericardRefusals.THE_TRACE_NUMBER_IS_ALREADY_CLAIMED),
            answer(
                message,
                terminal,
                now,
                AzericardOutcomes.NOTHING_PUBLISHED,
                AzericardOutcomes.ACTION_DUPLICATE,
                terminal.duplicateResponseCode(),
                AzericardOutcomes.ABSENT,
                AzericardOutcomes.ABSENT,
                AzericardOutcomes.ABSENT));
    theTraceItCollidedWith(message, testRunId, terminal, now, result);
    return result;
  }

  private void theTraceItClaimed(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      String intRef,
      AzericardOperationResult result) {
    record(message, testRunId, terminal, now, ExchangeVerdict.FIRST_REQUEST, intRef, true, result);
  }

  private void theTraceItCollidedWith(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      AzericardOperationResult result) {
    record(
        message,
        testRunId,
        terminal,
        now,
        ExchangeVerdict.DUPLICATE_KEY_REFUSAL,
        null,
        false,
        result);
  }

  private void record(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      ExchangeVerdict verdict,
      @Nullable String providerObjectId,
      boolean stateChanged,
      AzericardOperationResult result) {
    AzericardObservation.recorded(
        payments::observed,
        new AzericardObservedRequest(
            testRunId,
            terminal.accountId(),
            AzericardKeyMechanisms.ORDER,
            AzericardOrderTrace.of(message.order()),
            message.operationName(),
            message.fingerprint(),
            verdict,
            AzericardGateway.GATEWAY_HTTP_STATUS,
            result.body().contentType(),
            result.body().text(),
            providerObjectId,
            stateChanged,
            now));
  }

  private static AzericardOperationResult refused(
      RefusalSubject refusal,
      AzericardMessage message,
      AzericardTerminal terminal,
      Instant now,
      String responseCode) {
    return AzericardOperationResult.failed(
        refusal,
        answer(
            message,
            terminal,
            now,
            AzericardOutcomes.NOTHING_PUBLISHED,
            AzericardOutcomes.ACTION_ERROR,
            responseCode,
            AzericardOutcomes.ABSENT,
            AzericardOutcomes.ABSENT,
            AzericardOutcomes.ABSENT));
  }

  private boolean claimTheTraceNumber(
      AzericardMessage message, TestRunId testRunId, AzericardTerminal terminal, Instant now) {
    return payments.claimOrderTrace(
        testRunId,
        terminal.terminalId(),
        AzericardOrderTrace.dayOf(now),
        AzericardOrderTrace.of(message.order()));
  }

  private String anIntRefClaimedForThisPayment(TestRunId testRunId) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String intRef = ids.hex(INT_REF_LENGTH).toUpperCase(Locale.ROOT);
      if (payments.claimIntRef(testRunId, intRef)) {
        return intRef;
      }
    }
    throw new IllegalStateException(
        "no Azericard INT_REF could be claimed in " + ID_ATTEMPTS + " attempts");
  }

  private String aTokenClaimedForTheCardItStores(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      String intRef,
      Instant now,
      @Nullable String extNetRef) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String token = AzericardStoredCard.aToken(ids);
      AzericardStoredCard card =
          AzericardStoredCard.storedBy(
              testRunId,
              token,
              terminal.terminalId(),
              terminal.accountId(),
              intRef,
              message.order(),
              now,
              extNetRef);
      if (cards.claim(card)) {
        return token;
      }
    }
    throw new IllegalStateException(
        "no Azericard TOKEN could be claimed in " + ID_ATTEMPTS + " attempts");
  }

  private void stored(
      AzericardMessage message,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      String intRef,
      String rrn,
      @Nullable String approval,
      String action,
      String responseCode,
      @Nullable String token,
      @Nullable String createdCard,
      ObjectOrigin askedBy,
      @Nullable String last4,
      @Nullable String extNetRef) {
    AzericardTransactionType type = message.transactionType();
    Amount amount = Amount.of(message.amount());
    String nativeStatus = action + AzericardPayment.NATIVE_STATUS_SEPARATOR + responseCode;
    Ledger ledger =
        approval == null
            ? Ledger.empty()
            : Ledger.empty()
                .with(
                    new MonetaryOperation(
                        1,
                        type.movement().writes(),
                        amount,
                        type.movement().closesAuthorization(),
                        createdCard,
                        nativeStatus,
                        type.movement().whenApproved(),
                        now));
    AzericardPayment payment =
        new AzericardPayment(
            testRunId,
            intRef,
            terminal.terminalId(),
            terminal.accountId(),
            message.order(),
            type,
            amount,
            message.currency(),
            approval,
            rrn,
            action,
            responseCode,
            ledger,
            null,
            message.backref(),
            message.email(),
            token,
            last4,
            extNetRef,
            askedBy,
            now,
            now);
    if (!payments.insertIfAbsent(payment)) {
      throw new IllegalStateException(
          "the Azericard INT_REF this decision was taken under was claimed by another request"
              + " before the payment could be stored");
    }
  }

  private static AzericardBody answer(
      AzericardMessage message,
      AzericardTerminal terminal,
      Instant now,
      String desc,
      String action,
      String responseCode,
      String approval,
      String rrn,
      String intRef) {
    return new AzericardResponse(
            desc,
            message.terminal(),
            message.trtype(),
            message.order(),
            message.amount(),
            message.currency(),
            action,
            responseCode,
            approval,
            rrn,
            intRef,
            AzericardTimestamp.write(now),
            message.nonce())
        .signedWith(terminal.gatewayKey());
  }
}
