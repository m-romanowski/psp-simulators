package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.RenderedResponse;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunCandidates;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolution;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class TranzilaHostedFields {

  static final int OK = 200;

  static final String A_SPACE = " ";

  static final String BETWEEN_REFUSALS = "; ";

  static final String SCRIPT_CONTENT_TYPE = "text/javascript; charset=utf-8";

  static final String ANSWER_CONTENT_TYPE = "application/json";

  static final String REVALIDATED_ON_EVERY_LOAD = "no-cache";

  static final Map<String, List<String>> A_SCRIPTS_HEADERS = scriptHeaders();

  static final Map<String, List<String>> AN_ANSWERS_HEADERS = answerHeaders();

  private static final int AUDIT_ID_LENGTH = 24;

  private final TranzilaTerminals terminals;
  private final TranzilaHandshakes handshakes;
  private final TranzilaTransactions transactions;
  private final CreateTransaction createTransaction;
  private final TranzilaHostedCards cards;
  private final TranzilaNotify notify;
  private final TestRuns testRuns;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final TestRunId defaultTestRun;
  private final byte[] script;

  TranzilaHostedFields(
      TranzilaTerminals terminals,
      TranzilaHandshakes handshakes,
      TranzilaTransactions transactions,
      CreateTransaction createTransaction,
      TranzilaHostedCards cards,
      TranzilaNotify notify,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      TestRunId defaultTestRun,
      byte[] script) {
    this.terminals = terminals;
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
    this.script = script.clone();
  }

  sealed interface Charged {

    record Refused(List<TranzilaHostedFieldsRequest.Refusal> refusals) implements Charged {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }

    record AnsweredInstead(TranzilaOperationResult result) implements Charged {}

    record Made(
        TranzilaTransaction transaction,
        TranzilaHostedFieldsRequest request,
        @Nullable TransportFault fault,
        @Nullable String scenarioId)
        implements Charged {}
  }

  TranzilaApiResponse script() {
    return served(SCRIPT_CONTENT_TYPE, script, A_SCRIPTS_HEADERS);
  }

  TranzilaApiResponse field() {
    return served(
        TranzilaHostedFieldsPage.CONTENT_TYPE,
        TranzilaHostedFieldsPage.theDocument(),
        TranzilaHostedFieldsPage.DEFENCE_HEADERS);
  }

  TranzilaApiResponse control() {
    return served(
        TranzilaHostedFieldsPage.CONTENT_TYPE,
        TranzilaHostedControlPage.theDocument(),
        TranzilaHostedControlPage.DEFENCE_HEADERS);
  }

  private static TranzilaApiResponse served(
      String contentType, byte[] body, Map<String, List<String>> headers) {
    return new TranzilaApiResponse(
        TestRunId.UNKNOWN,
        new RenderedResponse(OK, contentType, body).asDelivered(),
        ProviderOutcome.SUCCESS,
        null,
        headers);
  }

  TranzilaApiResponse charge(TranzilaPageCall call) {
    String terminalName = call.parameter(TranzilaFields.TERMINAL_NAME);
    List<TranzilaTerminal> named = terminalName == null ? List.of() : terminals.named(terminalName);
    TestRunResolution resolution = resolve(call, named, call.parameter(TranzilaFields.THTK));
    TestRunId testRunId = resolution.testRunId();
    Instant now = clock.now(testRunId);
    Optional<TranzilaTerminal> terminal =
        named.stream().filter(held -> held.testRunId().equals(testRunId)).findFirst();
    String account = terminal.map(TranzilaTerminal::accountId).orElse(null);
    record(
        resolution,
        account,
        call,
        AuditDirection.INBOUND_REQUEST,
        null,
        null,
        null,
        null,
        Redaction.redact(call.headers(), TranzilaFields.SENSITIVE_HEADERS),
        Redaction.redact(call.parameters(), TranzilaFields.SENSITIVE_REQUEST_FIELDS),
        null,
        null,
        now);
    Charged charged =
        switch (TranzilaHostedFieldsRequest.read(call.parameters())) {
          case TranzilaHostedFieldsRequest.Read.Refused refused ->
              new Charged.Refused(refused.refusals());
          case TranzilaHostedFieldsRequest.Read.Admitted admitted ->
              terminal
                  .map(found -> chargedOn(found, admitted.request(), now))
                  .orElseGet(
                      () ->
                          new Charged.Refused(
                              List.of(
                                  TranzilaHostedFieldsErrors.of(
                                      TranzilaHostedFieldsErrors.TERMINAL_NOT_FOUND,
                                      TranzilaFields.TERMINAL_NAME))));
        };
    return answered(
        resolution,
        call,
        account,
        terminal.map(TranzilaTerminal::appSecret).orElse(null),
        now,
        charged);
  }

  Charged chargedOn(TranzilaTerminal terminal, TranzilaHostedFieldsRequest request, Instant now) {
    TestRunId testRunId = terminal.testRunId();
    Optional<TranzilaHandshake> locking =
        handshakes
            .find(testRunId, request.thtk())
            .filter(handshake -> handshake.isLiveAt(now))
            .filter(handshake -> handshake.locks(terminal.terminalName(), request.theAmount()));
    if (locking.isEmpty()) {
      return new Charged.Refused(List.of(TranzilaHostedFieldsErrors.theHandshakeDoesNotLockIt()));
    }
    TranzilaNotify.Target target =
        notify.targetOf(testRunId, terminal.accountId(), request.notifyUrl());
    if (target instanceof TranzilaNotify.Target.Unaddressable unaddressable) {
      return new Charged.Refused(
          List.of(
              TranzilaHostedFieldsErrors.theNotifyUrlIsNoTarget(unaddressable.inItsOwnWords())));
    }
    TranzilaHostedCards.Named card =
        cards.theCardNamedBy(
            testRunId, request.card(), request.expiryMonth(), request.expiryYear());
    TranzilaOperationResult result =
        createTransaction.handle(
            TranzilaHostedFieldsCharge.callOf(request, card.token()),
            Map.of(),
            testRunId,
            terminal,
            now,
            ObjectOrigin.PROVIDER_API,
            Map.of(card.token(), card.card()));
    if (result.insteadOfTheOperation() != null) {
      return new Charged.AnsweredInstead(result);
    }
    Optional<TranzilaTransaction> made =
        Optional.ofNullable(result.providerObjectId())
            .flatMap(TranzilaIdentifiers::transactionIdIn)
            .flatMap(
                id ->
                    transactions
                        .paymentHolding(testRunId, id.longValue())
                        .flatMap(payment -> payment.transaction(id.longValue())));
    if (made.isEmpty()) {
      throw theCardApiRefusedWhatItAdmitted(result);
    }
    TranzilaTransaction transaction = made.get();
    if (!TranzilaAnswers.isADuplicate(result)) {
      if (transaction.approved()) {
        handshakes.retainUntil(
            testRunId, request.thtk(), now.plus(TranzilaHandshake.IT_IS_RETAINED_FOR));
      }
      notify.deliverOnce(
          testRunId,
          terminal.accountId(),
          transaction.transactionIdAsText(),
          TranzilaHostedFieldsNotify.theFieldsOf(
              transaction, request, locking.get().requestParams()),
          target);
    }
    return new Charged.Made(transaction, request, result.fault(), result.scenarioId());
  }

  static IllegalStateException theCardApiRefusedWhatItAdmitted(TranzilaOperationResult result) {
    return new IllegalStateException(
        "the card API refused a charge the hosted fields admitted, which the hosted fields' own"
            + " checks are there to make impossible: "
            + result.body().text());
  }

  private TranzilaApiResponse answered(
      TestRunResolution resolution,
      TranzilaPageCall call,
      @Nullable String accountId,
      @Nullable String secret,
      Instant now,
      Charged charged) {
    return switch (charged) {
      case Charged.Refused refused ->
          delivered(
              resolution,
              call,
              accountId,
              now,
              ProviderOutcome.FAILURE,
              null,
              RefusalSubject.noObjectWasNamed(theWordsOf(refused.refusals())),
              TranzilaHostedFieldsAnswer.answered(
                  TranzilaHostedFieldsAnswer.theErrorsOf(refused.refusals()), null, secret),
              null,
              null);
      case Charged.AnsweredInstead instead ->
          answeredInstead(resolution, call, accountId, now, instead);
      case Charged.Made made -> {
        TranzilaTransaction transaction = made.transaction();
        boolean approved = transaction.approved();
        String objectId = transaction.transactionIdAsText();
        yield delivered(
            resolution,
            call,
            accountId,
            now,
            approved ? ProviderOutcome.SUCCESS : ProviderOutcome.FAILURE,
            objectId,
            approved ? null : RefusalSubject.theObject(objectId),
            TranzilaHostedFieldsAnswer.answered(
                null,
                TranzilaHostedFieldsAnswer.theResponseOf(transaction, made.request()),
                secret),
            made.fault(),
            made.scenarioId());
      }
    };
  }

  static String theWordsOf(List<TranzilaHostedFieldsRequest.Refusal> refusals) {
    List<String> words = new ArrayList<>();
    for (TranzilaHostedFieldsRequest.Refusal refusal : refusals) {
      words.add(
          String.join(A_SPACE, String.valueOf(refusal.code()), refusal.param(), refusal.message()));
    }
    return String.join(BETWEEN_REFUSALS, words);
  }

  private TranzilaApiResponse answeredInstead(
      TestRunResolution resolution,
      TranzilaPageCall call,
      @Nullable String accountId,
      Instant now,
      Charged.AnsweredInstead instead) {
    FaultedDelivery delivery = TranzilaApi.deliveryOf(instead.result());
    record(
        resolution,
        accountId,
        call,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.UNKNOWN,
        Integer.valueOf(delivery.httpStatus()),
        null,
        null,
        DeliveredHeaders.of(delivery),
        Map.of(),
        instead.result().scenarioId(),
        theDeliveryOf(delivery),
        now);
    return new TranzilaApiResponse(
        resolution.testRunId(), delivery, ProviderOutcome.UNKNOWN, null, AN_ANSWERS_HEADERS);
  }

  private TranzilaApiResponse delivered(
      TestRunResolution resolution,
      TranzilaPageCall call,
      @Nullable String accountId,
      Instant now,
      ProviderOutcome outcome,
      @Nullable String providerObjectId,
      @Nullable RefusalSubject refusal,
      TranzilaJson.JsonObject body,
      @Nullable TransportFault fault,
      @Nullable String scenarioId) {
    RenderedResponse rendered =
        new RenderedResponse(OK, ANSWER_CONTENT_TYPE, TranzilaJsonWriter.toBytes(body));
    FaultedDelivery delivery = fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
    record(
        resolution,
        accountId,
        call,
        AuditDirection.OUTBOUND_RESPONSE,
        outcome,
        Integer.valueOf(delivery.httpStatus()),
        providerObjectId,
        refusal,
        DeliveredHeaders.of(delivery),
        Redaction.redact(body.flatten(), TranzilaFields.SENSITIVE_RESPONSE_FIELDS),
        scenarioId,
        theDeliveryOf(delivery),
        now);
    return new TranzilaApiResponse(
        resolution.testRunId(), delivery, outcome, providerObjectId, AN_ANSWERS_HEADERS);
  }

  private static DeliveredResponse theDeliveryOf(FaultedDelivery delivery) {
    return new DeliveredResponse(
        delivery.fault(),
        delivery.declaredContentLength(),
        delivery.writtenBodyBytes(),
        delivery.delay());
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

  private void record(
      TestRunResolution resolution,
      @Nullable String accountId,
      TranzilaPageCall call,
      AuditDirection direction,
      @Nullable ProviderOutcome outcome,
      @Nullable Integer httpStatus,
      @Nullable String providerObjectId,
      @Nullable RefusalSubject refusal,
      Map<String, List<String>> headers,
      Map<String, List<String>> body,
      @Nullable String scenarioId,
      @Nullable DeliveredResponse delivered,
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
            call.method(),
            call.path(),
            httpStatus,
            providerObjectId,
            refusal,
            scenarioId,
            1,
            now,
            headers,
            body,
            delivered));
  }

  private static Map<String, List<String>> scriptHeaders() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(WhatAPayerPageMayLoad.CACHE_CONTROL, List.of(REVALIDATED_ON_EVERY_LOAD));
    headers.put(
        WhatAPayerPageMayLoad.CONTENT_TYPE_OPTIONS,
        List.of(WhatAPayerPageMayLoad.NOTHING_IS_SNIFFED));
    return Collections.unmodifiableMap(headers);
  }

  private static Map<String, List<String>> answerHeaders() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(
        WhatAPayerPageMayLoad.CACHE_CONTROL, List.of(WhatAPayerPageMayLoad.NOTHING_IS_STORED));
    headers.put(
        WhatAPayerPageMayLoad.CONTENT_TYPE_OPTIONS,
        List.of(WhatAPayerPageMayLoad.NOTHING_IS_SNIFFED));
    return Collections.unmodifiableMap(headers);
  }
}
