package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.audit.RefusedActionStatus;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.function.Supplier;

public final class RecordedRefusals implements AuditTrail {

  private final AuditTrail trail;
  private final Supplier<ProviderRegistry> providers;

  public RecordedRefusals(AuditTrail trail, Supplier<ProviderRegistry> providers) {
    this.trail = trail;
    this.providers = providers;
  }

  @Override
  public void record(AuditRecord record) {
    RefusalSubject refusal = record.refusal();
    if (refusal == null) {
      if (answersARefusalOnTheWire(record)) {
        throw new IllegalArgumentException(
            "provider "
                + record.provider().value()
                + " answered "
                + record.method()
                + " "
                + record.path()
                + " with a refusal and named no subject for it, and a refusal that names none is"
                + " absent from the timeline of every object, so a tester who opened that timeline"
                + " because the operation was refused would be shown a payment nothing was ever"
                + " refused on");
      }
      if (refusesAnActionOfAControlSurface(record)) {
        throw new IllegalArgumentException(
            "provider "
                + record.provider().value()
                + " refused the action "
                + record.method()
                + " "
                + record.path()
                + " and named no subject for it, and an action row that names none says only that"
                + " something was turned away, so a tester who read the trail to find out what"
                + " their call did would be shown a test run in which nothing was refused and"
                + " nothing took effect");
      }
    } else {
      if (nothingDecidedAnActionOfAControlSurface(record)) {
        throw new IllegalArgumentException(
            "provider "
                + record.provider().value()
                + " recorded the action "
                + record.method()
                + " "
                + record.path()
                + " under a status nothing decided while naming what it refused, and a failure"
                + " nothing on the way to the operation answered turned nothing away, so a"
                + " tester reading that row is shown a decision of this provider's contract"
                + " where this simulator is what broke");
      }
      if (reportsAnActionOfAControlSurface(record) && !refusesAnActionOfAControlSurface(record)) {
        throw new IllegalArgumentException(
            "provider "
                + record.provider().value()
                + " recorded the action "
                + record.method()
                + " "
                + record.path()
                + " under a status that reports it as having taken effect while naming what it"
                + " refused, and a tester reading that row is told at once that their call was"
                + " applied and that it was turned away, so whichever of the two they act on the"
                + " trail has already agreed with them");
      }
      String named = refusal.theObjectItNames().orElse(null);
      if (named != null && !holds(record.provider(), record.testRunId(), named)) {
        throw new IllegalArgumentException(
            "provider "
                + record.provider().value()
                + " answered "
                + record.method()
                + " "
                + record.path()
                + " with a refusal that says it names an object of this test run, and this run"
                + " holds no object under the id it named, so the row would either sit on no"
                + " timeline at all or on the timeline of whichever object a caller guessed");
      }
    }
    trail.record(record);
  }

  private static boolean answersARefusalOnTheWire(AuditRecord record) {
    return record.direction() == AuditDirection.OUTBOUND_RESPONSE
        && record.outcome() == ProviderOutcome.FAILURE;
  }

  private static boolean reportsAnActionOfAControlSurface(AuditRecord record) {
    return record.origin() != ObjectOrigin.PROVIDER_API
        && record.direction() == AuditDirection.INBOUND_REQUEST;
  }

  private static boolean refusesAnActionOfAControlSurface(AuditRecord record) {
    Integer status = record.httpStatus();
    return reportsAnActionOfAControlSurface(record)
        && status != null
        && RefusedActionStatus.refuses(status);
  }

  private static boolean nothingDecidedAnActionOfAControlSurface(AuditRecord record) {
    Integer status = record.httpStatus();
    return reportsAnActionOfAControlSurface(record)
        && status != null
        && RefusedActionStatus.nothingDecided(status);
  }

  private boolean holds(ProviderId provider, TestRunId testRunId, String providerObjectId) {
    return providers
        .get()
        .find(provider.value())
        .map(module -> module.state(testRunId, providerObjectId).isPresent())
        .orElse(false);
  }

  @Override
  public List<AuditRecord> of(ProviderId provider, TestRunId testRunId) {
    return trail.of(provider, testRunId);
  }

  @Override
  public int clear(ProviderId provider, TestRunId testRunId) {
    return trail.clear(provider, testRunId);
  }

  @Override
  public int clearAll(ProviderId provider) {
    return trail.clearAll(provider);
  }
}
