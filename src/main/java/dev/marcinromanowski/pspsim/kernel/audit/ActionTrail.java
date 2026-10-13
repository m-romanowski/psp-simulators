package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.util.Map;

public final class ActionTrail {

  private static final int AUDIT_ID_LENGTH = 24;

  private final AuditTrail trail;
  private final SimulatorClock clock;
  private final IdGenerator ids;

  public ActionTrail(AuditTrail trail, SimulatorClock clock, IdGenerator ids) {
    this.trail = trail;
    this.clock = clock;
    this.ids = ids;
  }

  public void record(AuditedAction action) {
    trail.record(
        new AuditRecord(
            "aud_" + ids.hex(AUDIT_ID_LENGTH),
            action.testRunId(),
            null,
            action.provider(),
            action.accountId(),
            action.origin(),
            AuditDirection.INBOUND_REQUEST,
            null,
            action.method(),
            action.path(),
            action.httpStatus(),
            action.providerObjectId(),
            action.refusal(),
            action.scenarioId(),
            1,
            clock.now(action.testRunId()),
            Map.of(),
            action.facts(),
            null));
  }

  public void record(AFailureNothingAnswered failure) {
    trail.record(
        new AuditRecord(
            "aud_" + ids.hex(AUDIT_ID_LENGTH),
            failure.testRunId(),
            null,
            failure.provider(),
            null,
            failure.origin(),
            AuditDirection.INBOUND_REQUEST,
            null,
            failure.method(),
            failure.path(),
            failure.httpStatus(),
            null,
            null,
            null,
            1,
            clock.now(failure.testRunId()),
            Map.of(),
            ActionFacts.none()
                .andEach(TheCausesOfAFailure.THE_CAUSES, failure.causes())
                .asRecorded(),
            null));
  }
}
