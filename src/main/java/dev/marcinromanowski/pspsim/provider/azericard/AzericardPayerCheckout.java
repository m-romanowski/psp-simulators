package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
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
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class AzericardPayerCheckout {

  static final int SHOWN = 200;

  static final int REFUSED = 422;

  static final int NO_SUCH_PAGE = 404;

  static final int BUSY = 409;

  static final String BEING_COMPLETED =
      "this payment page is being completed by another submission right now; nothing more was"
          + " authorized, and reloading the page shows the outcome once it is reached";

  static final String POST = "POST";

  static final Set<String> WHAT_THE_PAYER_TYPED =
      Set.of(AzericardPayerPage.CARD_NUMBER, AzericardPayerPage.EXPIRY, AzericardPayerPage.CVC);

  static final String WHAT_TAKING_A_STEP_ON_THE_PAYMENT_PAGE_DOES =
      "it runs the payer's own step on azericard's payment page, authorizes the card it names"
          + " against the terminal this test run configures, and sends whatever callback azericard"
          + " sends for the step taken";

  static final PayerSteps THE_PAYER_STEPS =
      new PayerSteps(
          AzericardGateway.AZERICARD,
          Map.of(
              PayerOutcome.PAID,
              "the payer paid on the page",
              PayerOutcome.DECLINED,
              "the payer met a decline on the page",
              PayerOutcome.ABANDONED,
              "the payer left the page without paying"),
          Map.of(),
          WHAT_TAKING_A_STEP_ON_THE_PAYMENT_PAGE_DOES);

  static final String ALREADY_COMPLETED =
      "this payment page has already reached an outcome. A payer completes the page once, and a"
          + " second completion would authorize a card the merchant has already been told about;"
          + " a repeated callback for it is what POST /__simulator/azericard/webhooks is for";

  static final String NO_SUCH_CARD = "no published azericard test card is named ";

  static final String THE_CARDS_OFFERED = ". The cards a payer may name here are: ";

  static final String NO_ONE_TO_TELL =
      "this payment was refused before it changed anything, because the callback it would cause"
          + " has nowhere to go: ";

  static final String STILL_COMPLETABLE =
      ". Configure that callback target for this test run and complete this page again: the"
          + " payment page has reached no outcome and is still completable.";

  static final String NOTHING_TO_CALL_BACK =
      "this payment page was not opened, because the callback it would cause has nowhere to"
          + " go: ";

  static final String NO_TERMINAL =
      "the terminal this payment page was opened for is no longer configured in this test run, and"
          + " provider azericard authorizes and signs with that terminal's own keys";

  static final String ANSWERED_INSTEAD =
      "a scenario rule of this test run answered in place of the authorization, so nothing was"
          + " authorized and no callback was queued; the payment page has reached no outcome and is"
          + " still completable";

  private static final int AUDIT_ID_LENGTH = 24;

  private final AzericardPayerSessions sessions;
  private final AzericardPayments payments;
  private final AzericardTerminals terminals;
  private final Authorize authorize;
  private final AzericardCallbacks callbacks;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final UnitOfWork unitOfWork;

  AzericardPayerCheckout(
      AzericardPayerSessions sessions,
      AzericardPayments payments,
      AzericardTerminals terminals,
      Authorize authorize,
      AzericardCallbacks callbacks,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      UnitOfWork unitOfWork) {
    this.sessions = sessions;
    this.payments = payments;
    this.terminals = terminals;
    this.authorize = authorize;
    this.callbacks = callbacks;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.unitOfWork = unitOfWork;
  }

  sealed interface Step {

    record Refused(String inItsOwnWords) implements Step {}

    record AnsweredInstead(AzericardOperationResult result) implements Step {}

    record BeingCompleted() implements Step {}

    record AlreadyReached(AzericardPayerSession session) implements Step {}

    record Gone() implements Step {}

    record Settled(
        AzericardPayerSession session,
        AzericardOperationResult result,
        PayerOutcome reached,
        @Nullable AzericardPayment payment,
        List<ScheduledWebhook> callbacks)
        implements Step {}
  }

  private sealed interface Opening {

    record Opened(AzericardPayerSession session) implements Opening {}

    record Held(@Nullable AzericardPayerSession byAnOpenPage) implements Opening {}
  }

  AzericardOperationResult handOff(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now) {
    if (callbacks.targetOf(
            testRunId, terminal.accountId(), AzericardPayerFlow.callbackTargetOf(message))
        instanceof AzericardCallbacks.Target.Unaddressable unaddressable) {
      return refusedForItsCallback(unaddressable);
    }
    return switch (opening(message, headers, testRunId, terminal, now)) {
      case Opening.Opened opened -> offering(opened.session());
      case Opening.Held held -> {
        AzericardPayerSession page = held.byAnOpenPage();
        yield page != null && page.wasOpenedBy(message)
            ? offering(page)
            : authorize.aDuplicate(message, testRunId, terminal, now);
      }
    };
  }

  AzericardOperationResult authorizeCallingBack(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now) {
    AzericardCallbacks.Target target =
        callbacks.targetOf(
            testRunId, terminal.accountId(), AzericardPayerFlow.callbackTargetOf(message));
    return unitOfWork.keepingOnly(
        AzericardOperationResult.class::isInstance,
        () -> authorizedTowards(message, headers, testRunId, terminal, now, target));
  }

  private AzericardOperationResult authorizedTowards(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now,
      AzericardCallbacks.Target target) {
    AzericardOperationResult result =
        authorize.handle(message, headers, testRunId, terminal, now, ObjectOrigin.PROVIDER_API);
    String intRef = result.providerObjectId();
    if (result.insteadOfTheOperation() == null && intRef != null) {
      payments
          .findByIntRef(testRunId, intRef)
          .ifPresent(paid -> calledBack(paid, message, terminal, target));
    }
    return result;
  }

  private void calledBack(
      AzericardPayment payment,
      AzericardMessage message,
      AzericardTerminal terminal,
      AzericardCallbacks.Target target) {
    String backref = AzericardPayerFlow.callbackTargetOf(message);
    switch (target) {
      case AzericardCallbacks.Target.Addressed addressed ->
          callbacks.deliver(payment, terminal, addressed.url(), null, null);
      case AzericardCallbacks.Target.Unaddressable unaddressable when backref == null ->
          callbacks.recordUnaddressable(payment, terminal, unaddressable.inItsOwnWords());
      case AzericardCallbacks.Target.Unaddressable _ ->
          callbacks.deliver(payment, terminal, backref, null, null);
    }
  }

  private static AzericardOperationResult refusedForItsCallback(
      AzericardCallbacks.Target.Unaddressable unaddressable) {
    return AzericardOperationResult.failed(
        RefusalSubject.noObjectWasNamed(NOTHING_TO_CALL_BACK + unaddressable.inItsOwnWords()),
        AzericardErrorPage.naming(AzericardFields.REQUIRED_BACKREF));
  }

  private static AzericardOperationResult offering(AzericardPayerSession session) {
    return AzericardOperationResult.succeeded(
        session.sessionId(), AzericardPayerPage.offering(cardFormOf(session)));
  }

  private Opening opening(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now) {
    Map<String, List<String>> kept = Redaction.redact(headers, AzericardFields.SENSITIVE_HEADERS);
    LocalDate day = AzericardOrderTrace.dayOf(now);
    String trace = AzericardOrderTrace.of(message.order());
    return unitOfWork.keepingOnly(
        Opening.Opened.class::isInstance,
        () -> {
          AzericardPayerSession session = aSessionOpened(message, kept, testRunId, terminal, now);
          if (payments.holdOrderTrace(
              testRunId, terminal.terminalId(), day, trace, session.sessionId())) {
            return new Opening.Opened(session);
          }
          return new Opening.Held(
              payments
                  .theSessionHoldingOrderTrace(testRunId, terminal.terminalId(), day, trace)
                  .flatMap(holder -> sessions.find(testRunId, holder))
                  .orElse(null));
        });
  }

  private AzericardPayerSession aSessionOpened(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      AzericardTerminal terminal,
      Instant now) {
    for (int attempt = 0; attempt < Authorize.ID_ATTEMPTS; attempt++) {
      AzericardPayerSession session =
          AzericardPayerSession.opened(
              ids.hex(AzericardPayerSession.SESSION_ID_LENGTH),
              testRunId,
              message,
              terminal,
              headers,
              now);
      if (sessions.open(session)) {
        return session;
      }
    }
    throw new IllegalStateException(
        "no Azericard payer session id could be claimed in " + Authorize.ID_ATTEMPTS + " attempts");
  }

  AzericardApiResponse submit(AzericardApiCall call) {
    AzericardForm form = AzericardForm.of(call.form());
    String sessionId = form.first(AzericardPayerPage.SESSION);
    Optional<AzericardPayerSession> found =
        sessionId == null ? Optional.empty() : sessions.find(sessionId);
    if (found.isEmpty()) {
      return unanswered(
          TestRunId.UNKNOWN,
          NO_SUCH_PAGE,
          AzericardPayerPage.refusing(AzericardPayerPage.NO_SUCH_PAGE));
    }
    AzericardPayerSession session = found.get();
    TestRunId testRunId = session.testRunId();
    Instant now = clock.now(testRunId);
    recordInbound(session, call, now);
    if (session.isSettled()) {
      return showingTheOutcomeOf(session, now);
    }
    boolean cancelled = AzericardPayerPage.CANCEL.equals(form.first(AzericardPayerPage.ACTION));
    AzericardPresentedCard presented =
        cancelled
            ? new AzericardPresentedCard.Declined(null)
            : AzericardCardCheck.ofThePage(
                form.first(AzericardPayerPage.CARD_NUMBER),
                form.first(AzericardPayerPage.EXPIRY),
                form.first(AzericardPayerPage.CVC),
                now);
    Step step =
        settle(
            session,
            cancelled ? PayerOutcome.ABANDONED : PayerOutcome.PAID,
            presented,
            ObjectOrigin.PROVIDER_API,
            now);
    return switch (step) {
      case Step.Refused refused ->
          answered(
              session,
              now,
              ProviderOutcome.FAILURE,
              REFUSED,
              AzericardPayerPage.refusing(refused.inItsOwnWords()),
              null);
      case Step.AnsweredInstead instead -> answeredInstead(session, now, instead.result());
      case Step.BeingCompleted _ ->
          answered(
              session,
              now,
              ProviderOutcome.UNKNOWN,
              BUSY,
              AzericardPayerPage.inProgress(BEING_COMPLETED),
              null);
      case Step.AlreadyReached reached -> showingTheOutcomeOf(reached.session(), now);
      case Step.Gone _ ->
          unanswered(
              testRunId,
              NO_SUCH_PAGE,
              AzericardPayerPage.refusing(AzericardPayerPage.NO_SUCH_PAGE));
      case Step.Settled settled ->
          answered(
              settled.session(),
              now,
              settled.result().outcome(),
              SHOWN,
              AzericardPayerPage.reporting(resultOf(settled.session(), settled.payment())),
              settled.result().fault());
    };
  }

  private AzericardApiResponse showingTheOutcomeOf(AzericardPayerSession session, Instant now) {
    AzericardPayerPage.Result shown = resultOf(session);
    return answered(
        session,
        now,
        shown.successful() ? ProviderOutcome.SUCCESS : ProviderOutcome.FAILURE,
        SHOWN,
        AzericardPayerPage.reporting(shown),
        null);
  }

  HostedCompletionOffer offeredOn(AzericardPayerSession session) {
    if (session.isSettled()) {
      return new HostedCompletionOffer.NotOffered(AzericardGateway.AZERICARD, ALREADY_COMPLETED);
    }
    return new HostedCompletionOffer.Offered(
        AzericardGateway.AZERICARD, AzericardTestCard.asPayerCards(), THE_PAYER_STEPS);
  }

  HostedCompletionOutcome complete(AzericardPayerSession session, HostedCompletionRequest request) {
    if (session.isSettled()) {
      return new HostedCompletionOutcome.RefusedByTheProvider(ALREADY_COMPLETED);
    }
    Instant now = clock.now(session.testRunId());
    AzericardPresentedCard presented = new AzericardPresentedCard.Declined(null);
    if (request.outcome().namesACard()) {
      String alias = request.card();
      Optional<AzericardTestCard> card =
          alias == null ? Optional.empty() : AzericardTestCard.byAlias(alias);
      if (card.isEmpty()) {
        return new HostedCompletionOutcome.RefusedByTheProvider(
            NO_SUCH_CARD + alias + THE_CARDS_OFFERED + AzericardTestCard.aliases());
      }
      presented = AzericardCardCheck.ofATestCard(card.get(), now);
    }
    return switch (settle(session, request.outcome(), presented, request.askedBy(), now)) {
      case Step.Refused refused ->
          new HostedCompletionOutcome.RefusedByTheProvider(refused.inItsOwnWords());
      case Step.AnsweredInstead _ ->
          new HostedCompletionOutcome.RefusedByTheProvider(ANSWERED_INSTEAD);
      case Step.BeingCompleted _ ->
          new HostedCompletionOutcome.RefusedByTheProvider(BEING_COMPLETED);
      case Step.AlreadyReached _ ->
          new HostedCompletionOutcome.RefusedByTheProvider(ALREADY_COMPLETED);
      case Step.Gone _ -> new HostedCompletionOutcome.NoSuchObject();
      case Step.Settled settled ->
          new HostedCompletionOutcome.Completed(
              session.sessionId(),
              settled.session().intRef(),
              settled.reached(),
              jobIdsOf(settled.callbacks()));
    };
  }

  private Step settle(
      AzericardPayerSession session,
      PayerOutcome asked,
      AzericardPresentedCard presented,
      ObjectOrigin askedBy,
      Instant now) {
    TestRunId testRunId = session.testRunId();
    Optional<AzericardTerminal> terminal =
        terminals.ofTheRunHolding(testRunId, session.terminalId());
    if (terminal.isEmpty()) {
      return new Step.Refused(NO_TERMINAL);
    }
    AzericardMessage message = session.message();
    return switch (callbacks.targetOf(
        testRunId, session.merchantId(), AzericardPayerFlow.callbackTargetOf(message))) {
      case AzericardCallbacks.Target.Unaddressable unaddressable ->
          new Step.Refused(NO_ONE_TO_TELL + unaddressable.inItsOwnWords() + STILL_COMPLETABLE);
      case AzericardCallbacks.Target.Addressed addressed ->
          unitOfWork.keepingOnly(
              AzericardPayerCheckout::isKept,
              () ->
                  settleTowards(
                      session,
                      message,
                      terminal.get(),
                      addressed.url(),
                      asked,
                      presented,
                      askedBy,
                      now));
    };
  }

  private Step settleTowards(
      AzericardPayerSession session,
      AzericardMessage message,
      AzericardTerminal terminal,
      String url,
      PayerOutcome asked,
      AzericardPresentedCard presented,
      ObjectOrigin askedBy,
      Instant now) {
    TestRunId testRunId = session.testRunId();
    String sessionId = session.sessionId();
    if (sessions.lockedWhileOpen(testRunId, sessionId).isEmpty()) {
      return whereItStands(sessionId);
    }
    AzericardOperationResult result =
        authorize.handle(
            message,
            session.headers(),
            testRunId,
            terminal,
            now,
            askedBy,
            presented,
            () -> payments.takeOverOrderTrace(testRunId, sessionId));
    if (result.insteadOfTheOperation() != null) {
      return new Step.AnsweredInstead(result);
    }
    String intRef = result.providerObjectId();
    AzericardPayment payment =
        intRef == null ? null : payments.findByIntRef(testRunId, intRef).orElse(null);
    PayerOutcome reached = reachedBy(asked, payment);
    String settledUnder = payment == null ? null : payment.intRef();
    sessions.settle(testRunId, sessionId, reached, settledUnder, now);
    List<ScheduledWebhook> queued =
        payment == null ? List.of() : callbacks.deliver(payment, terminal, url, null, null);
    return new Step.Settled(
        session.settledAs(reached, settledUnder, now), result, reached, payment, queued);
  }

  private static boolean isKept(Step step) {
    return step instanceof Step.Settled || step instanceof Step.AnsweredInstead;
  }

  private Step whereItStands(String sessionId) {
    return sessions
        .find(sessionId)
        .<Step>map(
            current ->
                current.isSettled() ? new Step.AlreadyReached(current) : new Step.BeingCompleted())
        .orElseGet(Step.Gone::new);
  }

  private static PayerOutcome reachedBy(PayerOutcome asked, @Nullable AzericardPayment payment) {
    if (payment != null && payment.wasApproved()) {
      return PayerOutcome.PAID;
    }
    return asked == PayerOutcome.ABANDONED ? PayerOutcome.ABANDONED : PayerOutcome.DECLINED;
  }

  private AzericardPayerPage.Result resultOf(AzericardPayerSession session) {
    String intRef = session.intRef();
    AzericardPayment payment =
        intRef == null ? null : payments.findByIntRef(session.testRunId(), intRef).orElse(null);
    return resultOf(session, payment);
  }

  private static AzericardPayerPage.Result resultOf(
      AzericardPayerSession session, @Nullable AzericardPayment payment) {
    AzericardMessage message = session.message();
    return new AzericardPayerPage.Result(
        payment != null && payment.wasApproved(),
        message.amount(),
        message.currency(),
        message.order(),
        payment == null ? AzericardOutcomes.ABSENT : payment.rrn(),
        orNothing(message.merchantName()),
        AzericardPayerFlow.returnTargetOf(message));
  }

  static AzericardPayerPage.CardForm cardFormOf(AzericardPayerSession session) {
    AzericardMessage message = session.message();
    return new AzericardPayerPage.CardForm(
        session.sessionId(),
        orNothing(message.merchantName()),
        orNothing(message.description()),
        message.amount(),
        message.currency(),
        message.order());
  }

  private static String orNothing(@Nullable String value) {
    return value == null ? AzericardOutcomes.ABSENT : value;
  }

  private static List<String> jobIdsOf(List<ScheduledWebhook> scheduled) {
    List<String> jobIds = new ArrayList<>();
    for (ScheduledWebhook webhook : scheduled) {
      jobIds.add(webhook.webhookJobId());
    }
    return List.copyOf(jobIds);
  }

  private AzericardApiResponse answered(
      AzericardPayerSession session,
      Instant now,
      ProviderOutcome outcome,
      int httpStatus,
      AzericardBody page,
      @Nullable TransportFault fault) {
    RenderedResponse rendered =
        new RenderedResponse(
            httpStatus, page.contentType(), page.text().getBytes(StandardCharsets.UTF_8));
    FaultedDelivery delivery = fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
    String providerObjectId = session.intRef() == null ? session.sessionId() : session.intRef();
    recordOutbound(session, now, outcome, delivery, providerObjectId, page);
    return AzericardApiResponse.ofThePayerPage(
        session.testRunId(), delivery, outcome, providerObjectId);
  }

  private AzericardApiResponse answeredInstead(
      AzericardPayerSession session, Instant now, AzericardOperationResult result) {
    FaultedDelivery delivery = result.insteadOfTheOperation();
    if (delivery == null) {
      throw new IllegalStateException(
          "a result answered in place of the authorization carries the delivery it answered with");
    }
    recordOutbound(
        session,
        now,
        result.outcome(),
        delivery,
        session.sessionId(),
        AzericardBody.nothingRendered());
    return AzericardApiResponse.ofThePayerPage(
        session.testRunId(), delivery, result.outcome(), session.sessionId());
  }

  private static AzericardApiResponse unanswered(
      TestRunId testRunId, int httpStatus, AzericardBody page) {
    FaultedDelivery delivery =
        new RenderedResponse(
                httpStatus, page.contentType(), page.text().getBytes(StandardCharsets.UTF_8))
            .asDelivered();
    return AzericardApiResponse.ofThePayerPage(testRunId, delivery, ProviderOutcome.FAILURE, null);
  }

  private void recordInbound(AzericardPayerSession session, AzericardApiCall call, Instant now) {
    audit.record(
        record(
            session,
            now,
            AuditDirection.INBOUND_REQUEST,
            null,
            null,
            null,
            null,
            Redaction.redact(call.form(), WHAT_THE_PAYER_TYPED)));
  }

  private void recordOutbound(
      AzericardPayerSession session,
      Instant now,
      ProviderOutcome outcome,
      FaultedDelivery delivery,
      String providerObjectId,
      AzericardBody page) {
    audit.record(
        record(
            session,
            now,
            AuditDirection.OUTBOUND_RESPONSE,
            outcome,
            delivery.httpStatus(),
            providerObjectId,
            outcome == ProviderOutcome.FAILURE ? RefusalSubject.theObject(providerObjectId) : null,
            Redaction.redact(page.fields(), AzericardFields.SENSITIVE_RESPONSE_FIELDS)));
  }

  private AuditRecord record(
      AzericardPayerSession session,
      Instant now,
      AuditDirection direction,
      @Nullable ProviderOutcome outcome,
      @Nullable Integer httpStatus,
      @Nullable String providerObjectId,
      @Nullable RefusalSubject refusal,
      Map<String, List<String>> body) {
    return new AuditRecord(
        "aud_" + ids.hex(AUDIT_ID_LENGTH),
        session.testRunId(),
        null,
        AzericardGateway.AZERICARD,
        session.merchantId(),
        ObjectOrigin.PROVIDER_API,
        direction,
        outcome,
        POST,
        AzericardPayerPage.PATH,
        httpStatus,
        providerObjectId,
        refusal,
        null,
        1,
        now,
        Map.of(),
        body,
        null);
  }
}
