package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveryRequest;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.support.TransactionTemplate;

final class GrowCallbacks {

  static final String EVENT_COMPLETED = "payment_process.completed";

  static final String EVENT_FAILED = "payment_process.failed";

  static final String NOTIFY_URL_IS_NOT_A_CONFIGURED_TARGET =
      "the payment process notifyUrl is not among the callback targets configured for this test"
          + " run";

  static final String NO_SINGLE_TARGET_FOR_THE_MERCHANT =
      "the payment process carries no notifyUrl and its merchant has no single configured callback"
          + " target";

  static final String AN_ABANDONED_PROCESS_TELLS_NOBODY =
      "a payer who left the hosted page without paying causes no server update: provider grow"
          + " publishes a callback for a completed process and one for a failed process and none"
          + " for an abandoned one, and a merchant reconciles an abandoned process by calling"
          + " getPaymentProcessInfo, which answers the zeroed transaction row";

  static final String A_DIFFERENT_OUTCOME_CANNOT_BE_CHANGED =
      "the payment process has already reached a different outcome, and eventType cannot change"
          + " it";

  sealed interface CallbackTarget {

    record Addressed(String url) implements CallbackTarget {}

    record Unaddressable(String inItsOwnWords) implements CallbackTarget {}
  }

  sealed interface Reached {

    record AtTheOutcomeAsked(GrowPaymentProcess process) implements Reached {}

    record AtADifferentOutcome() implements Reached {}
  }

  private static final DateTimeFormatter PAYMENT_DATE =
      DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT).withZone(ZoneOffset.UTC);

  static final int ID_ATTEMPTS = 5;

  private static final int TRANSACTION_ID_DIGITS = 6;

  private static final int ASMACHTA_DIGITS = 8;

  private static final int TRANSACTION_TOKEN_LENGTH = 18;

  private static final int CARD_TOKEN_LENGTH = 32;

  private final GrowPaymentProcesses processes;
  private final GrowTransactions transactions;
  private final ProviderAccounts accounts;
  private final WebhookDispatcher dispatcher;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final TransactionTemplate unitOfWork;

  GrowCallbacks(
      GrowPaymentProcesses processes,
      GrowTransactions transactions,
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      SimulatorClock clock,
      IdGenerator ids,
      TransactionTemplate unitOfWork) {
    this.processes = processes;
    this.transactions = transactions;
    this.accounts = accounts;
    this.dispatcher = dispatcher;
    this.clock = clock;
    this.ids = ids;
    this.unitOfWork = unitOfWork;
  }

  List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    TestRunId testRunId = request.testRunId();
    GrowPaymentProcess process = theProcess(testRunId, request.providerObjectId());
    GrowProcessOutcome.Kind kind = kindOf(request.eventType());
    String target = addressedOrRefused(theCallbackTargetOf(process));
    GrowPaymentProcess atOutcome =
        theOutcomeAskedForOrRefused(
            reach(
                process,
                kind,
                GrowPayerCards.theCardAProcessWithNoPayerCharges(),
                request.askedBy()));
    return deliver(atOutcome, target, request.reuseEventId(), request.delay());
  }

  GrowPaymentProcess theProcess(TestRunId testRunId, String processId) {
    return processes
        .findByProcessId(testRunId, processId)
        .orElseThrow(
            () ->
                new UnknownProviderObjectException(
                    "no grow payment process with that providerObjectId in this test run"));
  }

  Reached reach(
      GrowPaymentProcess process,
      GrowProcessOutcome.Kind kind,
      @Nullable GrowPayerCard card,
      ObjectOrigin askedBy) {
    if (process.outcome() != null) {
      return whereItStands(process, kind);
    }
    Instant now = clock.now(process.testRunId());
    GrowTransaction transaction =
        kind == GrowProcessOutcome.Kind.COMPLETED
            ? theTransactionFor(process, now, askedBy, theCardCharged(card))
            : null;
    GrowPaymentProcess reaching = reaching(process, kind, transaction, card, now);
    write(reaching, transaction);
    return whereItStands(theProcess(process.testRunId(), process.processId()), kind);
  }

  static GrowPaymentProcess theOutcomeAskedForOrRefused(Reached reached) {
    return switch (reached) {
      case Reached.AtTheOutcomeAsked asked -> asked.process();
      case Reached.AtADifferentOutcome _ ->
          throw new WebhookNotSchedulableException(A_DIFFERENT_OUTCOME_CANNOT_BE_CHANGED);
    };
  }

  List<ScheduledWebhook> deliver(
      GrowPaymentProcess atOutcome,
      String target,
      @Nullable String reuseEventId,
      @Nullable Duration delay) {
    GrowProcessOutcome outcome = outcomeOf(atOutcome);
    if (outcome.kind() == GrowProcessOutcome.Kind.ABANDONED) {
      throw new WebhookNotSchedulableException(AN_ABANDONED_PROCESS_TELLS_NOBODY);
    }
    TestRunId testRunId = atOutcome.testRunId();
    List<WebhookDelivery> scheduled =
        dispatcher.schedule(
            new WebhookDeliveryRequest(
                GrowLightApi.GROW,
                testRunId,
                atOutcome.merchantId(),
                nameOf(outcome.kind()),
                atOutcome.processId(),
                target,
                GrowCallback.of(atOutcome, outcome),
                reuseEventId,
                null,
                delay));
    return theWebhooksScheduled(scheduled);
  }

  private static List<ScheduledWebhook> theWebhooksScheduled(List<WebhookDelivery> scheduled) {
    List<ScheduledWebhook> jobs = new ArrayList<>();
    for (WebhookDelivery delivery : scheduled) {
      jobs.add(new ScheduledWebhook(delivery.deliveryId(), delivery.eventId()));
    }
    return List.copyOf(jobs);
  }

  CallbackTarget theCallbackTargetOf(GrowPaymentProcess process) {
    List<WebhookTarget> configured =
        accounts.webhookTargetsOf(GrowLightApi.GROW, process.testRunId());
    String notifyUrl = process.notifyUrl();
    if (notifyUrl != null) {
      for (WebhookTarget target : configured) {
        if (target.url().equals(notifyUrl)) {
          return new CallbackTarget.Addressed(target.url());
        }
      }
      return new CallbackTarget.Unaddressable(NOTIFY_URL_IS_NOT_A_CONFIGURED_TARGET);
    }
    List<WebhookTarget> ofMerchant =
        configured.stream().filter(t -> t.accountId().equals(process.merchantId())).toList();
    if (ofMerchant.size() != 1) {
      return new CallbackTarget.Unaddressable(NO_SINGLE_TARGET_FOR_THE_MERCHANT);
    }
    return new CallbackTarget.Addressed(ofMerchant.get(0).url());
  }

  static String addressedOrRefused(CallbackTarget target) {
    return switch (target) {
      case CallbackTarget.Addressed addressed -> addressed.url();
      case CallbackTarget.Unaddressable unaddressable ->
          throw new WebhookNotSchedulableException(unaddressable.inItsOwnWords());
    };
  }

  private void write(GrowPaymentProcess reaching, @Nullable GrowTransaction transaction) {
    unitOfWork.executeWithoutResult(
        status -> {
          if (processes.reachOutcome(reaching) && transaction != null) {
            record(transaction);
          }
        });
  }

  private static Reached whereItStands(GrowPaymentProcess process, GrowProcessOutcome.Kind kind) {
    GrowProcessOutcome reached = process.outcome();
    if (reached != null && reached.kind() != kind) {
      return new Reached.AtADifferentOutcome();
    }
    return new Reached.AtTheOutcomeAsked(process);
  }

  private static GrowPayerCard theCardCharged(@Nullable GrowPayerCard card) {
    if (card == null) {
      throw new IllegalStateException(
          "a completed payment process is a charge on a card, and this completion reached the"
              + " provider naming none");
    }
    return card;
  }

  private GrowTransaction theTransactionFor(
      GrowPaymentProcess process, Instant now, ObjectOrigin askedBy, GrowPayerCard card) {
    Amount amount = Amount.of(process.chargeSum());
    return new GrowTransaction(
        process.testRunId(),
        GrowTransactionKind.PAYMENT,
        aTransactionIdClaimedForThisCompletion(process.testRunId()),
        ids.hex(TRANSACTION_TOKEN_LENGTH),
        process.merchantId(),
        process.processId(),
        null,
        ids.digits(ASMACHTA_DIGITS),
        PAYMENT_DATE.format(now),
        process.description(),
        process.payerFullName(),
        process.payerPhone(),
        process.payerEmail(),
        GrowRecordedCard.chosenBy(card),
        amount,
        process.holdsFunds() ? Ledger.empty().with(authorization(amount, now)) : Ledger.empty(),
        askedBy,
        now,
        now);
  }

  private String aTransactionIdClaimedForThisCompletion(TestRunId testRunId) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String transactionId = ids.digits(TRANSACTION_ID_DIGITS);
      if (transactions.claimTransactionId(testRunId, transactionId)) {
        return transactionId;
      }
    }
    throw new IllegalStateException(
        "no Grow transactionId could be claimed in " + ID_ATTEMPTS + " attempts");
  }

  private static MonetaryOperation authorization(Amount amount, Instant now) {
    return new MonetaryOperation(
        1,
        OperationKind.AUTHORIZATION,
        amount,
        false,
        null,
        GrowNativeStatus.SUSPENDED_CODE,
        NormalizedStatus.READY_FOR_CAPTURE,
        now);
  }

  private void record(GrowTransaction transaction) {
    if (!transactions.insertIfAbsent(transaction)) {
      throw new IllegalStateException("the drawn Grow transactionId is already taken in this run");
    }
  }

  private GrowPaymentProcess reaching(
      GrowPaymentProcess process,
      GrowProcessOutcome.Kind kind,
      @Nullable GrowTransaction transaction,
      @Nullable GrowPayerCard card,
      Instant now) {
    return switch (kind) {
      case COMPLETED -> completed(process, requireTransaction(transaction), card, now);
      case FAILED ->
          process.reaching(
              GrowProcessOutcome.failed(),
              GrowNativeStatus.NOT_PAID_CODE,
              GrowNativeStatus.NOT_PAID,
              NormalizedStatus.FAILED,
              now);
      case ABANDONED ->
          process.reaching(
              GrowProcessOutcome.abandoned(),
              GrowNativeStatus.NOT_PAID_CODE,
              GrowNativeStatus.NOT_PAID,
              NormalizedStatus.NOT_INITIALIZED,
              now);
    };
  }

  private static GrowTransaction requireTransaction(@Nullable GrowTransaction transaction) {
    if (transaction == null) {
      throw new IllegalStateException(
          "a completed payment process is the transaction the payer created, so one is drawn"
              + " before the outcome is written and this outcome was written without one");
    }
    return transaction;
  }

  private GrowPaymentProcess completed(
      GrowPaymentProcess process,
      GrowTransaction transaction,
      @Nullable GrowPayerCard card,
      Instant now) {
    GrowProcessOutcome outcome =
        new GrowProcessOutcome(
            GrowProcessOutcome.Kind.COMPLETED,
            transaction.transactionId(),
            transaction.transactionToken(),
            transaction.asmachta(),
            process.saveCardToken() ? ids.hex(CARD_TOKEN_LENGTH) : null,
            transaction.paymentDate(),
            card);
    NormalizedStatus normalized =
        process.holdsFunds() ? NormalizedStatus.READY_FOR_CAPTURE : NormalizedStatus.SUCCEEDED;
    return process.reaching(
        outcome, GrowNativeStatus.SUSPENDED_CODE, GrowNativeStatus.SUSPENDED, normalized, now);
  }

  private static GrowProcessOutcome outcomeOf(GrowPaymentProcess process) {
    GrowProcessOutcome outcome = process.outcome();
    if (outcome == null) {
      throw new IllegalStateException("a process that reached no outcome has no callback to send");
    }
    return outcome;
  }

  private static GrowProcessOutcome.Kind kindOf(@Nullable String eventType) {
    if (eventType == null || EVENT_COMPLETED.equals(eventType)) {
      return GrowProcessOutcome.Kind.COMPLETED;
    }
    if (EVENT_FAILED.equals(eventType)) {
      return GrowProcessOutcome.Kind.FAILED;
    }
    throw new WebhookNotSchedulableException(
        "eventType must be one of " + EVENT_COMPLETED + ", " + EVENT_FAILED);
  }

  private static String nameOf(GrowProcessOutcome.Kind kind) {
    return kind == GrowProcessOutcome.Kind.COMPLETED ? EVENT_COMPLETED : EVENT_FAILED;
  }
}
