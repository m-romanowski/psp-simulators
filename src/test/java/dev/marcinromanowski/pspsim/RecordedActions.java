package dev.marcinromanowski.pspsim;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RecordedActions implements AuditTrail, IdGenerator, SimulatorClock {

  private final List<AuditRecord> written = new ArrayList<>();

  public ActionTrail asTrail() {
    return new ActionTrail(this, this, this);
  }

  public List<AuditRecord> written() {
    return List.copyOf(written);
  }

  @Override
  public void record(AuditRecord record) {
    written.add(record);
  }

  @Override
  public List<AuditRecord> of(ProviderId provider, TestRunId testRunId) {
    List<AuditRecord> found = new ArrayList<>();
    for (AuditRecord record : written) {
      if (record.provider().equals(provider) && record.testRunId().equals(testRunId)) {
        found.add(record);
      }
    }
    return List.copyOf(found);
  }

  @Override
  public int clear(ProviderId provider, TestRunId testRunId) {
    return written.removeAll(of(provider, testRunId)) ? 1 : 0;
  }

  @Override
  public int clearAll(ProviderId provider) {
    int held = written.size();
    written.clear();
    return held;
  }

  @Override
  public String hex(int length) {
    return "action" + (written.size() + 1);
  }

  @Override
  public String digits(int length) {
    return String.valueOf(written.size() + 1);
  }

  @Override
  public Instant now(TestRunId testRunId) {
    return Instant.EPOCH;
  }
}
