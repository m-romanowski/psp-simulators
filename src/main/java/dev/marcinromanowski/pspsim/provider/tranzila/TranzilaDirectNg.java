package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.RenderedResponse;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerSteps;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunCandidates;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolution;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class TranzilaDirectNg {

  static final int SHOWN = 200;

  static final int REFUSED = 400;

  static final int NO_SUCH_PAGE = 404;

  static final int BUSY = 409;

  static final int NO_LONGER_WAITING = 410;

  static final int NOT_COMPLETED = 422;

  static final String WHAT_TAKING_A_STEP_ON_THE_PAGE_DOES =
      "it runs the payer's own step on tranzila's payment page: paying charges the card it names"
          + " against the terminal the page was opened for, under the tranmode the page was opened"
          + " with and the scenario rules of this test run, and sends the notify the page sends";

  static final PayerSteps THE_PAYER_STEPS =
      new PayerSteps(
          TranzilaApi.TRANZILA,
          Map.of(
              PayerOutcome.PAID,
              "the payer paid on the page",
              PayerOutcome.ABANDONED,
              "the payer left the page without paying"),
          Map.of(
              PayerOutcome.DECLINED,
              "provider tranzila's page has no decline a payer chooses: a card is declined by the"
                  + " processor, which in this test run is a scenario rule failing the debit or"
                  + " the verify the page performs"),
          WHAT_TAKING_A_STEP_ON_THE_PAGE_DOES);

  static final String NO_TERMINAL =
      "the terminal this payment page was opened for is no longer configured in this test run, so"
          + " there is no terminal to charge the card against";

  static final String NO_SUCH_CARD = "no card this page offers is named ";

  static final String THE_CARDS_OFFERED = ". The cards a payer may name here are: ";

  static final String STILL_COMPLETABLE =
      " The payment page has reached no outcome and is still completable.";

  static final String ANSWERED_INSTEAD =
      "a scenario rule of this test run answered in place of the charge, so nothing was charged"
          + " and no notify was queued; the payment page has reached no outcome and is still"
          + " completable";

  static final String THE_HANDSHAKE_NO_LONGER_HOLDS =
      "Illegal Operation 912791: the thtk this page was opened with no longer locks its sum, because"
          + " handshake data is retained for 20 minutes and no successful charge renewed it";

  static final String THE_THTK_DOES_NOT_LOCK_THIS_PAGE =
      "Illegal Operation 912791: the thtk names no live handshake of this terminal locking this"
          + " sum, so no transaction was executed";

  static final String NOT_OFFERED_HERE =
      " is not offered on this payment page: the page offers a wallet when it is opened with"
          + " that wallet's parameter set to 1 and a tranmode of A, V, AK or VK, the ones the"
          + " wallets pay.";

  private static final int AUDIT_ID_LENGTH = 24;

  private final TranzilaTerminals terminals;
  private final TranzilaPages pages;
  private final TranzilaHandshakes handshakes;
  private final TranzilaTransactions transactions;
  private final CreateTransaction createTransaction;
  private final TranzilaPageCards cards;
  private final TranzilaNotify notify;
  private final TestRuns testRuns;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final TestRunId defaultTestRun;
  private final UnitOfWork unitOfWork;

  TranzilaDirectNg(
      TranzilaTerminals terminals,
      TranzilaPages pages,
      TranzilaHandshakes handshakes,
      TranzilaTransactions transactions,
      CreateTransaction createTransaction,
      TranzilaPageCards cards,
      TranzilaNotify notify,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      TestRunId defaultTestRun,
      UnitOfWork unitOfWork) {
    this.terminals = terminals;
    this.pages = pages;
    this.handshakes = handshakes;
    this.transactions = transactions;
    this.createTransaction = createTransaction;
    this.cards = cards;
    this.notify = notify;
    this.testRuns = testRuns;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.defaultTestRun = defaultTestRun;
    this.unitOfWork = unitOfWork;
  }

  sealed interface Opening {

    record Opened(TranzilaPage page) implements Opening {}

    record Refused(int httpStatus, String inItsOwnWords) implements Opening {}
  }

  sealed interface Step {

    record Refused(String inItsOwnWords) implements Step {}

    record AnsweredInstead(TranzilaOperationResult result) implements Step {}

    record ADuplicate() implements Step {}

    record BeingCompleted() implements Step {}

    record AlreadyReached(TranzilaPage page) implements Step {}

    record Gone() implements Step {}

    record Left(TranzilaPage page) implements Step {}

    record Settled(
        TranzilaPage page,
        @Nullable TranzilaTransaction transaction,
        @Nullable TransportFault fault,
        @Nullable String scenarioId,
        List<ScheduledWebhook> notified)
        implements Step {}
  }

  TranzilaApiResponse open(TranzilaPageCall call, String terminalName) {
    List<TranzilaTerminal> named = terminals.named(terminalName);
    String thtk = call.parameter(TranzilaFields.THTK);
    TestRunResolution resolution = resolve(call, named, thtk);
    TestRunId testRunId = resolution.testRunId();
    Instant now = clock.now(testRunId);
    Optional<TranzilaTerminal> terminal =
        named.stream().filter(held -> held.testRunId().equals(testRunId)).findFirst();
    String account = terminal.map(TranzilaTerminal::accountId).orElse(null);
    record(
        resolution,
        account,
        call.method(),
        call.path(),
        AuditDirection.INBOUND_REQUEST,
        null,
        null,
        null,
        null,
        Redaction.redact(call.headers(), TranzilaFields.SENSITIVE_HEADERS),
        Redaction.redact(call.parameters(), TranzilaFields.SENSITIVE_REQUEST_FIELDS),
        null,
        now);
    Opening opening =
        terminal.isEmpty()
            ? new Opening.Refused(NO_SUCH_PAGE, TranzilaDirectNgPage.NO_SUCH_TERMINAL)
            : opening(terminal.get(), call.parameters(), ObjectOrigin.PROVIDER_API, now);
    return switch (opening) {
      case Opening.Refused refused ->
          answered(
              resolution,
              account,
              call,
              now,
              ProviderOutcome.FAILURE,
              null,
              RefusalSubject.noObjectWasNamed(refused.inItsOwnWords()),
              refused.httpStatus(),
              TranzilaDirectNgPage.refusing(refused.inItsOwnWords()),
              TranzilaDirectNgPage.DEFENCE_HEADERS,
              Map.of(),
              null,
              null);
      case Opening.Opened opened ->
          answered(
              resolution,
              account,
              call,
              now,
              ProviderOutcome.SUCCESS,
              opened.page().pageId(),
              null,
              SHOWN,
              TranzilaDirectNgPage.offering(
                  opened.page(), cards.asPayerCards(opened.page().testRunId())),
              TranzilaDirectNgPage.DEFENCE_HEADERS,
              Map.of(),
              null,
              null);
    };
  }

  Opening opening(
      TranzilaTerminal terminal,
      Map<String, List<String>> parameters,
      ObjectOrigin origin,
      Instant now) {
    return switch (TranzilaPageRequest.read(parameters)) {
      case TranzilaPageRequest.Read.Refused refused -> new Opening.Refused(REFUSED, refused.why());
      case TranzilaPageRequest.Read.Admitted admitted ->
          openedFor(terminal, admitted.request(), origin, now);
    };
  }

  private Opening openedFor(
      TranzilaTerminal terminal, TranzilaPageRequest request, ObjectOrigin origin, Instant now) {
    String requestParams = null;
    String thtk = request.thtk();
    if (thtk != null) {
      Optional<TranzilaHandshake> locking =
          handshakes
              .find(terminal.testRunId(), thtk)
              .filter(handshake -> handshake.isLiveAt(now))
              .filter(
                  handshake ->
                      handshake.locks(terminal.terminalName(), new BigDecimal(request.sum())));
      if (locking.isEmpty()) {
        return new Opening.Refused(REFUSED, THE_THTK_DOES_NOT_LOCK_THIS_PAGE);
      }
      requestParams = locking.get().requestParams();
    }
    if (notify.targetOf(terminal.testRunId(), terminal.accountId(), request.notifyUrl())
        instanceof TranzilaNotify.Target.Unaddressable unaddressable) {
      return new Opening.Refused(REFUSED, unaddressable.inItsOwnWords());
    }
    for (int attempt = 0; attempt < CreateTransaction.TRANSACTION_ID_ATTEMPTS; attempt++) {
      TranzilaPage page =
          TranzilaPage.opened(
              TranzilaPage.ID_LEAD + ids.hex(TranzilaPage.ID_HEX),
              terminal,
              request,
              requestParams,
              origin,
              now);
      if (pages.open(page) == 1) {
        return new Opening.Opened(page);
      }
    }
    throw new IllegalStateException(
        "no Tranzila page id could be claimed in "
            + CreateTransaction.TRANSACTION_ID_ATTEMPTS
            + " attempts");
  }

  TranzilaApiResponse submit(TranzilaPageCall call, String terminalName, String pageId) {
    Optional<TranzilaPage> found =
        pages.find(pageId).filter(page -> page.terminalName().equals(terminalName));
    if (found.isEmpty()) {
      return unanswered(NO_SUCH_PAGE, TranzilaDirectNgPage.NO_SUCH_PAGE);
    }
    TranzilaPage page = found.get();
    Instant now = clock.now(page.testRunId());
    TestRunResolution resolution = received(call, page, now);
    if (page.isSettled()) {
      return showing(resolution, call, page, now, new Step.AlreadyReached(page));
    }
    PayerOutcome asked =
        PayerOutcome.ABANDONED.name().equals(call.parameter(TranzilaDirectNgPage.OUTCOME))
            ? PayerOutcome.ABANDONED
            : PayerOutcome.PAID;
    String named = call.parameter(TranzilaDirectNgPage.WALLET);
    Optional<TranzilaWallet> wallet = Optional.ofNullable(named).flatMap(TranzilaWallet::named);
    Step step =
        named != null && wallet.isEmpty()
            ? new Step.Refused(TranzilaDirectNgPage.NO_SUCH_WALLET + STILL_COMPLETABLE)
            : settle(
                page,
                asked,
                call.parameter(TranzilaDirectNgPage.CARD),
                ObjectOrigin.PROVIDER_API,
                now,
                wallet.orElse(null));
    return showing(resolution, call, page, now, step);
  }

  TranzilaApiResponse shown(TranzilaPageCall call, String terminalName, String pageId) {
    Optional<TranzilaPage> found =
        pages.find(pageId).filter(page -> page.terminalName().equals(terminalName));
    if (found.isEmpty()) {
      return unanswered(NO_SUCH_PAGE, TranzilaDirectNgPage.NO_SUCH_PAGE);
    }
    TranzilaPage page = found.get();
    Instant now = clock.now(page.testRunId());
    TestRunResolution resolution = received(call, page, now);
    if (page.isSettled()) {
      return showing(resolution, call, page, now, new Step.AlreadyReached(page));
    }
    return answered(
        resolution,
        page.accountId(),
        call,
        now,
        ProviderOutcome.SUCCESS,
        page.pageId(),
        null,
        SHOWN,
        TranzilaDirectNgPage.offering(page, cards.asPayerCards(page.testRunId())),
        TranzilaDirectNgPage.DEFENCE_HEADERS,
        Map.of(),
        null,
        null);
  }

  TranzilaApiResponse sheet(
      TranzilaPageCall call, String terminalName, String pageId, String walletNamed) {
    Optional<TranzilaPage> found =
        pages.find(pageId).filter(page -> page.terminalName().equals(terminalName));
    if (found.isEmpty()) {
      return unanswered(NO_SUCH_PAGE, TranzilaDirectNgPage.NO_SUCH_PAGE);
    }
    TranzilaPage page = found.get();
    Instant now = clock.now(page.testRunId());
    TestRunResolution resolution = received(call, page, now);
    if (page.isSettled()) {
      return showing(resolution, call, page, now, new Step.AlreadyReached(page));
    }
    Optional<TranzilaWallet> wallet = TranzilaWallet.named(walletNamed);
    if (wallet.isEmpty()) {
      return refusal(
          resolution, call, page, now, NO_SUCH_PAGE, TranzilaDirectNgPage.NO_SUCH_WALLET);
    }
    if (!wallet.get().isOfferedOn(page)) {
      return refusal(
          resolution, call, page, now, NOT_COMPLETED, wallet.get().label() + NOT_OFFERED_HERE);
    }
    return answered(
        resolution,
        page.accountId(),
        call,
        now,
        ProviderOutcome.SUCCESS,
        page.pageId(),
        null,
        SHOWN,
        TranzilaDirectNgPage.aWalletSheet(page, wallet.get(), cards.asPayerCards(page.testRunId())),
        TranzilaDirectNgPage.DEFENCE_HEADERS,
        Map.of(),
        null,
        null);
  }

  private TestRunResolution received(TranzilaPageCall call, TranzilaPage page, Instant now) {
    TestRunResolution resolution =
        new TestRunResolution(page.testRunId(), TestRunResolutionRule.OBJECT_CORRELATION);
    record(
        resolution,
        page.accountId(),
        call.method(),
        call.path(),
        AuditDirection.INBOUND_REQUEST,
        null,
        null,
        null,
        null,
        Redaction.redact(call.headers(), TranzilaFields.SENSITIVE_HEADERS),
        Redaction.redact(call.parameters(), TranzilaFields.SENSITIVE_REQUEST_FIELDS),
        null,
        now);
    return resolution;
  }

  private TranzilaApiResponse showing(
      TestRunResolution resolution,
      TranzilaPageCall call,
      TranzilaPage page,
      Instant now,
      Step step) {
    String account = page.accountId();
    return switch (step) {
      case Step.Refused refused ->
          refusal(resolution, call, page, now, NOT_COMPLETED, refused.inItsOwnWords());
      case Step.ADuplicate _ ->
          refusal(resolution, call, page, now, SHOWN, TranzilaDirectNgPage.THE_DUPLICATE);
      case Step.BeingCompleted _ ->
          refusal(resolution, call, page, now, BUSY, TranzilaDirectNgPage.BEING_COMPLETED);
      case Step.AlreadyReached _ ->
          refusal(
              resolution, call, page, now, NO_LONGER_WAITING, TranzilaDirectNgPage.ALREADY_REACHED);
      case Step.Gone _ ->
          refusal(resolution, call, page, now, NO_SUCH_PAGE, TranzilaDirectNgPage.NO_SUCH_PAGE);
      case Step.AnsweredInstead instead -> answeredInstead(resolution, call, page, now, instead);
      case Step.Left left ->
          answered(
              resolution,
              account,
              call,
              now,
              ProviderOutcome.SUCCESS,
              left.page().pageId(),
              null,
              SHOWN,
              TranzilaDirectNgPage.left(),
              TranzilaDirectNgPage.DEFENCE_HEADERS,
              Map.of(),
              null,
              null);
      case Step.Settled settled -> returned(resolution, call, now, settled);
    };
  }

  private TranzilaApiResponse returned(
      TestRunResolution resolution, TranzilaPageCall call, Instant now, Step.Settled settled) {
    TranzilaPage page = settled.page();
    TranzilaTransaction transaction = settled.transaction();
    Map<String, String> fields = TranzilaBrowserReturn.theFieldsOf(page, transaction);
    boolean approved = TranzilaBrowserReturn.isApproved(page, transaction);
    String address = approved ? page.successUrl() : page.failUrl();
    String objectId = transaction == null ? page.pageId() : transaction.transactionIdAsText();
    ProviderOutcome outcome = approved ? ProviderOutcome.SUCCESS : ProviderOutcome.FAILURE;
    RefusalSubject refusal = approved ? null : RefusalSubject.theObject(objectId);
    if (address == null) {
      return answered(
          resolution,
          page.accountId(),
          call,
          now,
          outcome,
          objectId,
          refusal,
          SHOWN,
          TranzilaDirectNgPage.reporting(page, fields, approved),
          TranzilaDirectNgPage.DEFENCE_HEADERS,
          fields,
          settled.fault(),
          settled.scenarioId());
    }
    return answered(
        resolution,
        page.accountId(),
        call,
        now,
        outcome,
        objectId,
        refusal,
        SHOWN,
        TranzilaReturnPage.posting(address, fields),
        TranzilaReturnPage.DEFENCE_HEADERS,
        fields,
        settled.fault(),
        settled.scenarioId());
  }

  private TranzilaApiResponse refusal(
      TestRunResolution resolution,
      TranzilaPageCall call,
      TranzilaPage page,
      Instant now,
      int httpStatus,
      String inItsOwnWords) {
    return answered(
        resolution,
        page.accountId(),
        call,
        now,
        ProviderOutcome.FAILURE,
        page.pageId(),
        RefusalSubject.theObject(page.pageId()),
        httpStatus,
        TranzilaDirectNgPage.refusing(inItsOwnWords),
        TranzilaDirectNgPage.DEFENCE_HEADERS,
        Map.of(),
        null,
        null);
  }

  private TranzilaApiResponse answeredInstead(
      TestRunResolution resolution,
      TranzilaPageCall call,
      TranzilaPage page,
      Instant now,
      Step.AnsweredInstead instead) {
    FaultedDelivery delivery = instead.result().insteadOfTheOperation();
    if (delivery == null) {
      throw new IllegalStateException(
          "a result answered in place of the charge carries the delivery it answered with");
    }
    record(
        resolution,
        page.accountId(),
        call.method(),
        call.path(),
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.UNKNOWN,
        Integer.valueOf(delivery.httpStatus()),
        page.pageId(),
        null,
        DeliveredHeaders.of(delivery),
        Map.of(),
        instead.result().scenarioId(),
        now);
    return new TranzilaApiResponse(
        page.testRunId(), delivery, ProviderOutcome.UNKNOWN, page.pageId());
  }

  HostedCompletionOffer offeredOn(TranzilaPage page) {
    if (page.isSettled()) {
      return new HostedCompletionOffer.NotOffered(
          TranzilaApi.TRANZILA, TranzilaDirectNgPage.ALREADY_REACHED);
    }
    return new HostedCompletionOffer.Offered(
        TranzilaApi.TRANZILA, cards.asPayerCards(page.testRunId()), THE_PAYER_STEPS);
  }

  HostedCompletionOutcome complete(TranzilaPage page, HostedCompletionRequest request) {
    if (page.isSettled()) {
      return new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNgPage.ALREADY_REACHED);
    }
    Instant now = clock.now(page.testRunId());
    return switch (settle(page, request.outcome(), request.card(), request.askedBy(), now, null)) {
      case Step.Refused refused ->
          new HostedCompletionOutcome.RefusedByTheProvider(refused.inItsOwnWords());
      case Step.AnsweredInstead _ ->
          new HostedCompletionOutcome.RefusedByTheProvider(ANSWERED_INSTEAD);
      case Step.ADuplicate _ ->
          new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNgPage.THE_DUPLICATE);
      case Step.BeingCompleted _ ->
          new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNgPage.BEING_COMPLETED);
      case Step.AlreadyReached _ ->
          new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNgPage.ALREADY_REACHED);
      case Step.Gone _ -> new HostedCompletionOutcome.NoSuchObject();
      case Step.Left left ->
          new HostedCompletionOutcome.Completed(
              left.page().pageId(), null, PayerOutcome.ABANDONED, List.of());
      case Step.Settled settled ->
          new HostedCompletionOutcome.Completed(
              page.pageId(),
              settled.transaction() == null ? null : settled.transaction().transactionIdAsText(),
              Objects.requireNonNull(settled.page().outcome()),
              jobIdsOf(settled.notified()));
    };
  }

  Step settle(
      TranzilaPage page,
      PayerOutcome asked,
      @Nullable String cardAlias,
      ObjectOrigin askedBy,
      Instant now,
      @Nullable TranzilaWallet wallet) {
    TestRunId testRunId = page.testRunId();
    Optional<TranzilaTerminal> terminal = terminals.find(testRunId, page.accountId());
    if (terminal.isEmpty()) {
      return new Step.Refused(NO_TERMINAL);
    }
    if (asked == PayerOutcome.ABANDONED) {
      return unitOfWork.keepingOnly(Step.class::isInstance, () -> left(page, now));
    }
    if (wallet != null && !wallet.isOfferedOn(page)) {
      return new Step.Refused(wallet.label() + NOT_OFFERED_HERE + STILL_COMPLETABLE);
    }
    Optional<TranzilaPageCards.Offered> card =
        Optional.ofNullable(cardAlias).flatMap(alias -> cards.resolve(testRunId, alias));
    if (card.isEmpty()) {
      return new Step.Refused(
          NO_SUCH_CARD + cardAlias + THE_CARDS_OFFERED + cards.aliasesOffered(testRunId));
    }
    String thtk = page.thtk();
    if (thtk != null
        && handshakes.find(testRunId, thtk).filter(held -> held.isLiveAt(now)).isEmpty()) {
      return new Step.Refused(THE_HANDSHAKE_NO_LONGER_HOLDS + "." + STILL_COMPLETABLE);
    }
    TranzilaNotify.Target target = notify.targetOf(testRunId, page.accountId(), page.notifyUrl());
    if (target instanceof TranzilaNotify.Target.Unaddressable unaddressable) {
      return new Step.Refused(unaddressable.inItsOwnWords() + "." + STILL_COMPLETABLE);
    }
    return unitOfWork.keepingOnly(
        Step.class::isInstance,
        () -> charged(page, terminal.get(), card.get(), target, askedBy, now, wallet));
  }

  private Step left(TranzilaPage page, Instant now) {
    if (pages.lockedWhileOpen(page.testRunId(), page.pageId()).isEmpty()) {
      return whereItStands(page);
    }
    TranzilaPage left = page.settledAs(PayerOutcome.ABANDONED, null, null, null, null, now);
    pages.settle(left);
    return new Step.Left(left);
  }

  private Step charged(
      TranzilaPage page,
      TranzilaTerminal terminal,
      TranzilaPageCards.Offered card,
      TranzilaNotify.Target target,
      ObjectOrigin askedBy,
      Instant now,
      @Nullable TranzilaWallet wallet) {
    TestRunId testRunId = page.testRunId();
    if (pages.lockedWhileOpen(testRunId, page.pageId()).isEmpty()) {
      return whereItStands(page);
    }
    if (!page.tranmode().recordsATransaction()) {
      TranzilaPage saved =
          page.settledAs(
              PayerOutcome.PAID,
              null,
              terminal.verificationApprovalCode().code(),
              card.token(),
              card.card(),
              now);
      return settledAs(saved, null, null, null, target, now);
    }
    TranzilaOperationResult result =
        createTransaction.handle(
            TranzilaPageCharge.callOf(page, card),
            Map.of(),
            testRunId,
            terminal,
            now,
            askedBy,
            Map.of(card.token(), card.card()),
            wallet);
    if (result.insteadOfTheOperation() != null) {
      return new Step.AnsweredInstead(result);
    }
    if (TranzilaAnswers.isADuplicate(result)) {
      return new Step.ADuplicate();
    }
    TranzilaTransaction transaction =
        Optional.ofNullable(result.providerObjectId())
            .flatMap(TranzilaIdentifiers::transactionIdIn)
            .flatMap(
                id ->
                    transactions
                        .paymentHolding(testRunId, id.longValue())
                        .flatMap(payment -> payment.transaction(id.longValue())))
            .orElseThrow();
    TranzilaPage reached =
        page.settledAs(
            transaction.approved() ? PayerOutcome.PAID : PayerOutcome.DECLINED,
            Long.valueOf(transaction.transactionId()),
            null,
            null,
            null,
            now);
    return settledAs(reached, transaction, result.fault(), result.scenarioId(), target, now);
  }

  private Step settledAs(
      TranzilaPage reached,
      @Nullable TranzilaTransaction transaction,
      @Nullable TransportFault fault,
      @Nullable String scenarioId,
      TranzilaNotify.Target target,
      Instant now) {
    pages.settle(reached);
    String thtk = reached.thtk();
    if (thtk != null && TranzilaBrowserReturn.isApproved(reached, transaction)) {
      handshakes.retainUntil(
          reached.testRunId(), thtk, now.plus(TranzilaHandshake.IT_IS_RETAINED_FOR));
    }
    List<ScheduledWebhook> notified = notify.deliverOnce(reached, transaction, target);
    return new Step.Settled(reached, transaction, fault, scenarioId, notified);
  }

  private Step whereItStands(TranzilaPage page) {
    return pages
        .find(page.testRunId(), page.pageId())
        .<Step>map(
            current ->
                current.isSettled() ? new Step.AlreadyReached(current) : new Step.BeingCompleted())
        .orElseGet(Step.Gone::new);
  }

  private static List<String> jobIdsOf(List<ScheduledWebhook> scheduled) {
    List<String> jobIds = new ArrayList<>();
    for (ScheduledWebhook webhook : scheduled) {
      jobIds.add(webhook.webhookJobId());
    }
    return List.copyOf(jobIds);
  }

  private TestRunResolution resolve(
      TranzilaPageCall call, List<TranzilaTerminal> named, @Nullable String thtk) {
    List<TestRunId> holding = new ArrayList<>();
    for (TranzilaTerminal terminal : named) {
      holding.add(terminal.testRunId());
    }
    if (thtk != null) {
      List<TestRunId> locked =
          holding.stream()
              .filter(testRunId -> handshakes.find(testRunId, thtk).isPresent())
              .toList();
      if (!locked.isEmpty()) {
        holding = locked;
      }
    }
    List<TestRunCandidates> evaluated = new ArrayList<>();
    evaluated.add(TestRunHeader.in(call.headers(), testRuns::exists));
    evaluated.add(call.route().candidatesFor(TranzilaApi.TRANZILA));
    evaluated.add(TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, holding));
    return TestRunResolution.of(evaluated, defaultTestRun);
  }

  private TranzilaApiResponse answered(
      TestRunResolution resolution,
      @Nullable String accountId,
      TranzilaPageCall call,
      Instant now,
      ProviderOutcome outcome,
      @Nullable String providerObjectId,
      @Nullable RefusalSubject refusal,
      int httpStatus,
      String page,
      Map<String, List<String>> defences,
      Map<String, String> fields,
      @Nullable TransportFault fault,
      @Nullable String scenarioId) {
    RenderedResponse rendered =
        new RenderedResponse(
            httpStatus, TranzilaDirectNgPage.CONTENT_TYPE, page.getBytes(StandardCharsets.UTF_8));
    FaultedDelivery delivery = fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
    Map<String, List<String>> answeredFields = new LinkedHashMap<>();
    fields.forEach((name, value) -> answeredFields.put(name, List.of(value)));
    record(
        resolution,
        accountId,
        call.method(),
        call.path(),
        AuditDirection.OUTBOUND_RESPONSE,
        outcome,
        Integer.valueOf(delivery.httpStatus()),
        providerObjectId,
        refusal,
        DeliveredHeaders.of(delivery),
        Redaction.redact(answeredFields, TranzilaFields.SENSITIVE_RESPONSE_FIELDS),
        scenarioId,
        now);
    return new TranzilaApiResponse(
        resolution.testRunId(), delivery, outcome, providerObjectId, defences);
  }

  private static TranzilaApiResponse unanswered(int httpStatus, String inItsOwnWords) {
    FaultedDelivery delivery =
        new RenderedResponse(
                httpStatus,
                TranzilaDirectNgPage.CONTENT_TYPE,
                TranzilaDirectNgPage.refusing(inItsOwnWords).getBytes(StandardCharsets.UTF_8))
            .asDelivered();
    return new TranzilaApiResponse(
        TestRunId.UNKNOWN,
        delivery,
        ProviderOutcome.FAILURE,
        null,
        TranzilaDirectNgPage.DEFENCE_HEADERS);
  }

  private void record(
      TestRunResolution resolution,
      @Nullable String accountId,
      String method,
      String path,
      AuditDirection direction,
      @Nullable ProviderOutcome outcome,
      @Nullable Integer httpStatus,
      @Nullable String providerObjectId,
      @Nullable RefusalSubject refusal,
      Map<String, List<String>> headers,
      Map<String, List<String>> body,
      @Nullable String scenarioId,
      Instant now) {
    audit.record(
        new AuditRecord(
            "aud_" + ids.hex(AUDIT_ID_LENGTH),
            resolution.testRunId(),
            resolution.rule(),
            TranzilaApi.TRANZILA,
            accountId,
            ObjectOrigin.PROVIDER_API,
            direction,
            outcome,
            method,
            path,
            httpStatus,
            providerObjectId,
            refusal,
            scenarioId,
            1,
            now,
            headers,
            body,
            null));
  }
}
