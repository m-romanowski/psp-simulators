package dev.marcinromanowski.pspsim.provider.autra;

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
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class PayerOnTheHostedCardPage {

  static final int OFFERED = 200;

  static final int NO_SUCH_PAGE = 404;

  static final int GONE = 410;

  static final int REFUSED = 422;

  static final String POST = "POST";

  private static final int AUDIT_ID_LENGTH = 24;

  record Rendered(int httpStatus, String body) {

    Map<String, String> headers() {
      return AutraCardPage.DEFENCE_HEADERS;
    }
  }

  private final AutraCardSessions sessions;
  private final SimulatorProvider provider;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;

  PayerOnTheHostedCardPage(
      AutraCardSessions sessions,
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

  Rendered open(String cardSessionId, @Nullable String pageToken) {
    Optional<AutraCardSession> found = theSessionOf(cardSessionId, pageToken);
    if (found.isEmpty()) {
      return noSuchPage();
    }
    AutraCardSession session = found.get();
    if (provider.hostedCompletion(session.testRunId(), session.cardSessionId())
        instanceof HostedCompletionOffer.Offered offered) {
      return new Rendered(
          OFFERED,
          AutraCardPage.offering(
              session.cardSessionId(), session.pageToken(), offered.cardsAPayerMayName()));
    }
    return new Rendered(
        GONE,
        AutraCardPage.refusing(session.whyItIsNoLongerWaitingAt(clock.now(session.testRunId()))));
  }

  Rendered submit(
      String cardSessionId,
      @Nullable String pageToken,
      @Nullable String outcome,
      @Nullable String card) {
    Optional<AutraCardSession> found = theSessionOf(cardSessionId, pageToken);
    if (found.isEmpty()) {
      return noSuchPage();
    }
    AutraCardSession session = found.get();
    audit.record(inbound(session, outcome, card));
    Rendered rendered = completed(session, outcome, card);
    audit.record(outbound(session, rendered.httpStatus()));
    return rendered;
  }

  private Rendered completed(
      AutraCardSession session, @Nullable String outcome, @Nullable String card) {
    HostedCompletionRequest request;
    try {
      request =
          HostedCompletionControl.asked(
              provider.hostedCompletion(session.testRunId(), session.cardSessionId()),
              session.testRunId(),
              session.cardSessionId(),
              outcome,
              card,
              ObjectOrigin.PROVIDER_API);
    } catch (IllegalArgumentException refused) {
      return refused(String.valueOf(refused.getMessage()));
    }
    return switch (provider.complete(request)) {
      case HostedCompletionOutcome.Completed _ -> afterwards(session);
      case HostedCompletionOutcome.RefusedByTheProvider refused -> refused(refused.inItsOwnWords());
      case HostedCompletionOutcome.NotOffered notOffered ->
          refused(notOffered.whatThisProviderHasInstead());
      case HostedCompletionOutcome.NoSuchObject _ -> noSuchPage();
    };
  }

  private Rendered afterwards(AutraCardSession session) {
    AutraCardSession finished =
        sessions.findById(session.testRunId(), session.cardSessionId()).orElse(session);
    AutraStoredCard card = finished.storedCard();
    if (card == null) {
      return new Rendered(OFFERED, AutraCardPage.left());
    }
    return new Rendered(
        OFFERED, AutraCardPage.tokenized(AutraCardTokenizedResult.of(finished, card)));
  }

  private Optional<AutraCardSession> theSessionOf(
      String cardSessionId, @Nullable String pageToken) {
    if (pageToken == null || pageToken.isBlank()) {
      return Optional.empty();
    }
    return sessions.findByPage(cardSessionId, pageToken);
  }

  private static Rendered refused(String inItsOwnWords) {
    return new Rendered(REFUSED, AutraCardPage.refusing(inItsOwnWords));
  }

  private static Rendered noSuchPage() {
    return new Rendered(NO_SUCH_PAGE, AutraCardPage.refusing(AutraCardPage.NO_SUCH_PAGE));
  }

  private AuditRecord inbound(
      AutraCardSession session, @Nullable String outcome, @Nullable String card) {
    Map<String, List<String>> submitted = new LinkedHashMap<>();
    if (outcome != null) {
      submitted.put(AutraCardPage.OUTCOME, List.of(outcome));
    }
    if (card != null) {
      submitted.put(AutraCardPage.CARD, List.of(card));
    }
    return record(session, AuditDirection.INBOUND_REQUEST, null, null, null, null, submitted);
  }

  private AuditRecord outbound(AutraCardSession session, int httpStatus) {
    boolean offered = httpStatus == OFFERED;
    return record(
        session,
        AuditDirection.OUTBOUND_RESPONSE,
        offered ? ProviderOutcome.SUCCESS : ProviderOutcome.FAILURE,
        httpStatus,
        session.cardSessionId(),
        offered ? null : RefusalSubject.theObject(session.cardSessionId()),
        Redaction.redact(Map.of(), AutraFields.SENSITIVE_RESPONSE_FIELDS));
  }

  private AuditRecord record(
      AutraCardSession session,
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
        AutraApi.AUTRA,
        session.accountId(),
        ObjectOrigin.PROVIDER_API,
        direction,
        outcome,
        POST,
        AutraCardPage.pathOf(session.cardSessionId()),
        httpStatus,
        providerObjectId,
        refusal,
        null,
        1,
        clock.now(session.testRunId()),
        Map.of(),
        body,
        null);
  }
}
