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
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolution;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

final class TranzilaThreeDSecureChallenge {

  static final int SHOWN = 200;

  static final int NO_SUCH_CHALLENGE = 404;

  static final int NO_LONGER_WAITING = 410;

  private static final int AUDIT_ID_LENGTH = 24;

  private final TranzilaChallenges challenges;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;

  TranzilaThreeDSecureChallenge(
      TranzilaChallenges challenges, AuditTrail audit, SimulatorClock clock, IdGenerator ids) {
    this.challenges = challenges;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
  }

  TranzilaApiResponse show(TranzilaPageCall call, String trackId) {
    Optional<TranzilaChallenge> found = challenges.find(trackId);
    if (found.isEmpty()) {
      return unanswered(NO_SUCH_CHALLENGE, TranzilaChallengePage.NO_SUCH_CHALLENGE);
    }
    TranzilaChallenge challenge = found.get();
    Instant now = clock.now(challenge.testRunId());
    recordInbound(call, challenge, now);
    if (challenge.isAnswered()) {
      return alreadyAnswered(call, challenge, now);
    }
    return answered(
        call,
        challenge,
        now,
        ProviderOutcome.SUCCESS,
        null,
        SHOWN,
        TranzilaChallengePage.asking(challenge),
        TranzilaChallengePage.DEFENCE_HEADERS,
        Map.of());
  }

  TranzilaApiResponse answer(TranzilaPageCall call, String trackId) {
    Optional<TranzilaChallenge> found = challenges.find(trackId);
    if (found.isEmpty()) {
      return unanswered(NO_SUCH_CHALLENGE, TranzilaChallengePage.NO_SUCH_CHALLENGE);
    }
    TranzilaChallenge challenge = found.get();
    Instant now = clock.now(challenge.testRunId());
    recordInbound(call, challenge, now);
    String code = call.parameter(TranzilaChallengePage.CODE);
    boolean passed = code != null && TranzilaThreeDSecureCards.passes(code);
    TranzilaChallenge answered =
        challenge.answered(
            passed ? TranzilaChallenge.Verdict.PASSED : TranzilaChallenge.Verdict.FAILED, now);
    if (challenges.answer(answered) != 1) {
      return alreadyAnswered(call, challenge, now);
    }
    ProviderOutcome outcome = passed ? ProviderOutcome.SUCCESS : ProviderOutcome.FAILURE;
    RefusalSubject refusal = passed ? null : RefusalSubject.theObject(trackId);
    if (challenge.isOnTheHostedFields()) {
      return answered(
          call,
          challenge,
          now,
          outcome,
          refusal,
          SHOWN,
          TranzilaChallengeAnsweredPage.telling(trackId, passed),
          TranzilaChallengeAnsweredPage.DEFENCE_HEADERS,
          Map.of());
    }
    String address = challenge.redirectUrl();
    if (address == null) {
      return answered(
          call,
          challenge,
          now,
          outcome,
          refusal,
          SHOWN,
          TranzilaChallengePage.reporting(passed),
          TranzilaChallengePage.DEFENCE_HEADERS,
          Map.of());
    }
    SequencedMap<String, String> fields = theFieldsPostedTo(challenge);
    return answered(
        call,
        challenge,
        now,
        outcome,
        refusal,
        SHOWN,
        TranzilaReturnPage.posting(address, fields),
        TranzilaReturnPage.DEFENCE_HEADERS,
        fields);
  }

  static SequencedMap<String, String> theFieldsPostedTo(TranzilaChallenge challenge) {
    SequencedMap<String, String> fields = new LinkedHashMap<>();
    challenge
        .redirectParams()
        .forEach(
            (name, value) -> {
              if (!TranzilaThreeDSecureAnswers.TRACK_ID.equals(name)) {
                fields.put(name, value);
              }
            });
    fields.put(TranzilaThreeDSecureAnswers.TRACK_ID, challenge.trackId());
    return fields;
  }

  private TranzilaApiResponse alreadyAnswered(
      TranzilaPageCall call, TranzilaChallenge challenge, Instant now) {
    return answered(
        call,
        challenge,
        now,
        ProviderOutcome.FAILURE,
        RefusalSubject.theObject(challenge.trackId()),
        NO_LONGER_WAITING,
        TranzilaChallengePage.refusing(TranzilaChallengePage.ALREADY_ANSWERED),
        TranzilaChallengePage.DEFENCE_HEADERS,
        Map.of());
  }

  private static TranzilaApiResponse unanswered(int httpStatus, String inItsOwnWords) {
    FaultedDelivery delivery =
        new RenderedResponse(
                httpStatus,
                TranzilaChallengePage.CONTENT_TYPE,
                TranzilaChallengePage.refusing(inItsOwnWords).getBytes(StandardCharsets.UTF_8))
            .asDelivered();
    return new TranzilaApiResponse(
        TestRunId.UNKNOWN,
        delivery,
        ProviderOutcome.FAILURE,
        null,
        TranzilaChallengePage.DEFENCE_HEADERS);
  }

  private TranzilaApiResponse answered(
      TranzilaPageCall call,
      TranzilaChallenge challenge,
      Instant now,
      ProviderOutcome outcome,
      @Nullable RefusalSubject refusal,
      int httpStatus,
      String page,
      Map<String, List<String>> defences,
      Map<String, String> fields) {
    FaultedDelivery delivery =
        new RenderedResponse(
                httpStatus,
                TranzilaChallengePage.CONTENT_TYPE,
                page.getBytes(StandardCharsets.UTF_8))
            .asDelivered();
    Map<String, List<String>> answeredFields = new LinkedHashMap<>();
    fields.forEach((name, value) -> answeredFields.put(name, List.of(value)));
    record(
        challenge,
        call,
        AuditDirection.OUTBOUND_RESPONSE,
        outcome,
        Integer.valueOf(delivery.httpStatus()),
        refusal,
        DeliveredHeaders.of(delivery),
        Redaction.redact(answeredFields, TranzilaFields.SENSITIVE_RESPONSE_FIELDS),
        now);
    return new TranzilaApiResponse(
        challenge.testRunId(), delivery, outcome, challenge.trackId(), defences);
  }

  private void recordInbound(TranzilaPageCall call, TranzilaChallenge challenge, Instant now) {
    record(
        challenge,
        call,
        AuditDirection.INBOUND_REQUEST,
        null,
        null,
        null,
        Redaction.redact(call.headers(), TranzilaFields.SENSITIVE_HEADERS),
        Redaction.redact(call.parameters(), TranzilaFields.SENSITIVE_REQUEST_FIELDS),
        now);
  }

  private void record(
      TranzilaChallenge challenge,
      TranzilaPageCall call,
      AuditDirection direction,
      @Nullable ProviderOutcome outcome,
      @Nullable Integer httpStatus,
      @Nullable RefusalSubject refusal,
      Map<String, List<String>> headers,
      Map<String, List<String>> body,
      Instant now) {
    TestRunResolution resolution =
        new TestRunResolution(challenge.testRunId(), TestRunResolutionRule.OBJECT_CORRELATION);
    audit.record(
        new AuditRecord(
            "aud_" + ids.hex(AUDIT_ID_LENGTH),
            resolution.testRunId(),
            resolution.rule(),
            TranzilaApi.TRANZILA,
            challenge.accountId(),
            ObjectOrigin.PROVIDER_API,
            direction,
            outcome,
            call.method(),
            call.path(),
            httpStatus,
            direction == AuditDirection.INBOUND_REQUEST ? null : challenge.trackId(),
            refusal,
            null,
            1,
            now,
            headers,
            body,
            null));
  }
}
