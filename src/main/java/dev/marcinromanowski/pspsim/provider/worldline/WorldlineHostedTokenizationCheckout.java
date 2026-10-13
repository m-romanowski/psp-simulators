package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionControl;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class WorldlineHostedTokenizationCheckout {

  static final int OFFERED = 200;

  static final int FINISHED = 200;

  static final int NO_SUCH_SESSION = 404;

  static final int NO_LONGER_WAITING = 410;

  static final int REFUSED = 422;

  static final String POST = "POST";

  static final String AN_OFFER_THIS_PAGE_CANNOT_RENDER =
      "provider worldline's hosted tokenization page tokenizes a card and nothing else, and no"
          + " module of this simulator has ever offered it without naming one; an offer that"
          + " names no card is a shape this page was never built to render";

  private static final int AUDIT_ID_LENGTH = 24;

  record Rendered(int httpStatus, String body) {

    Map<String, String> headers() {
      return WorldlineHostedTokenizationPage.DEFENCE_HEADERS;
    }
  }

  private final WorldlineTokenizations sessions;
  private final SimulatorProvider provider;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;

  WorldlineHostedTokenizationCheckout(
      WorldlineTokenizations sessions,
      SimulatorProvider provider,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    this.sessions = sessions;
    this.provider = provider;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
  }

  Rendered open(String hostedTokenizationId) {
    Optional<WorldlineTokenization> found = sessions.findByIdInAnyRun(hostedTokenizationId);
    if (found.isEmpty()) {
      return unknownSession();
    }
    WorldlineTokenization session = found.get();
    return switch (provider.hostedCompletion(session.testRunId(), hostedTokenizationId)) {
      case HostedCompletionOffer.Offered offered ->
          new Rendered(
              OFFERED, WorldlineHostedTokenizationPage.offering(hostedTokenizationId, offered));
      case HostedCompletionOffer.NotOffered _ -> noLongerWaiting(session);
      case HostedCompletionOffer.OfferedWithoutACard _ ->
          throw new IllegalStateException(AN_OFFER_THIS_PAGE_CANNOT_RENDER);
    };
  }

  Rendered submit(
      String hostedTokenizationId, @Nullable String outcome, @Nullable String cardAlias) {
    Optional<WorldlineTokenization> found = sessions.findByIdInAnyRun(hostedTokenizationId);
    if (found.isEmpty()) {
      return unknownSession();
    }
    WorldlineTokenization session = found.get();
    TestRunId testRunId = session.testRunId();
    recordInbound(session, outcome, cardAlias);
    HostedCompletionOffer offer = provider.hostedCompletion(testRunId, hostedTokenizationId);
    switch (offer) {
      case HostedCompletionOffer.Offered _ -> {}
      case HostedCompletionOffer.NotOffered _ -> {
        recordOutbound(session, NO_LONGER_WAITING, ProviderOutcome.FAILURE);
        return noLongerWaiting(session);
      }
      case HostedCompletionOffer.OfferedWithoutACard _ ->
          throw new IllegalStateException(AN_OFFER_THIS_PAGE_CANNOT_RENDER);
    }
    HostedCompletionRequest request;
    try {
      PayerOutcome declared = PayerOutcome.declared(outcome == null ? "" : outcome.strip());
      request =
          HostedCompletionControl.asked(
              offer,
              testRunId,
              hostedTokenizationId,
              declared.name(),
              declared.namesACard() ? cardAlias : null,
              ObjectOrigin.PROVIDER_API);
    } catch (IllegalArgumentException refused) {
      return refusal(session, String.valueOf(refused.getMessage()));
    }
    return switch (provider.complete(request)) {
      case HostedCompletionOutcome.Completed completed -> {
        recordOutbound(session, FINISHED, ProviderOutcome.SUCCESS);
        yield new Rendered(FINISHED, WorldlineHostedTokenizationPage.finished(completed.outcome()));
      }
      case HostedCompletionOutcome.RefusedByTheProvider refused ->
          refusedOrNoLongerWaiting(
              session, testRunId, hostedTokenizationId, refused.inItsOwnWords());
      case HostedCompletionOutcome.NotOffered notOffered ->
          refusal(session, notOffered.whatThisProviderHasInstead());
      case HostedCompletionOutcome.NoSuchObject _ -> {
        recordOutbound(session, NO_SUCH_SESSION, ProviderOutcome.FAILURE);
        yield unknownSession();
      }
    };
  }

  private Rendered refusedOrNoLongerWaiting(
      WorldlineTokenization session,
      TestRunId testRunId,
      String hostedTokenizationId,
      String inItsOwnWords) {
    WorldlineTokenization current =
        sessions.findById(testRunId, hostedTokenizationId).orElse(session);
    if (current.isAwaitingThePayerAt(clock.now(testRunId))) {
      return refusal(session, inItsOwnWords);
    }
    recordOutbound(session, NO_LONGER_WAITING, ProviderOutcome.FAILURE);
    return noLongerWaiting(current);
  }

  private Rendered refusal(WorldlineTokenization session, String inItsOwnWords) {
    recordOutbound(session, REFUSED, ProviderOutcome.FAILURE);
    return new Rendered(REFUSED, WorldlineHostedTokenizationPage.refusing(inItsOwnWords));
  }

  private Rendered noLongerWaiting(WorldlineTokenization session) {
    return new Rendered(
        NO_LONGER_WAITING,
        WorldlineHostedTokenizationPage.refusing(
            session.whyItIsNoLongerWaitingAt(clock.now(session.testRunId()))));
  }

  private static Rendered unknownSession() {
    return new Rendered(
        NO_SUCH_SESSION,
        WorldlineHostedTokenizationPage.refusing(WorldlineHostedTokenizationPage.NO_SUCH_SESSION));
  }

  private void recordInbound(
      WorldlineTokenization session, @Nullable String outcome, @Nullable String cardAlias) {
    Map<String, List<String>> submitted = new LinkedHashMap<>();
    submitted.put(WorldlineHostedTokenizationPage.OUTCOME, List.of(outcome == null ? "" : outcome));
    if (cardAlias != null) {
      submitted.put(WorldlineHostedTokenizationPage.CARD, List.of(cardAlias));
    }
    audit.record(
        record(session, AuditDirection.INBOUND_REQUEST, null, null, null, null, submitted));
  }

  private void recordOutbound(
      WorldlineTokenization session, int httpStatus, ProviderOutcome outcome) {
    String id = session.hostedTokenizationId();
    audit.record(
        record(
            session,
            AuditDirection.OUTBOUND_RESPONSE,
            outcome,
            Integer.valueOf(httpStatus),
            id,
            outcome == ProviderOutcome.FAILURE ? RefusalSubject.theObject(id) : null,
            Redaction.redact(Map.of(), WorldlineFields.SENSITIVE_RESPONSE_FIELDS)));
  }

  private AuditRecord record(
      WorldlineTokenization session,
      AuditDirection direction,
      @Nullable ProviderOutcome outcome,
      @Nullable Integer httpStatus,
      @Nullable String providerObjectId,
      @Nullable RefusalSubject refusal,
      Map<String, List<String>> body) {
    TestRunId testRunId = session.testRunId();
    return new AuditRecord(
        "aud_" + ids.hex(AUDIT_ID_LENGTH),
        testRunId,
        null,
        WorldlineApi.WORLDLINE,
        session.accountId(),
        ObjectOrigin.PROVIDER_API,
        direction,
        outcome,
        POST,
        WorldlineHostedTokenizationPage.pathOf(session.hostedTokenizationId()),
        httpStatus,
        providerObjectId,
        refusal,
        null,
        1,
        clock.now(testRunId),
        Map.of(),
        body,
        null);
  }
}
