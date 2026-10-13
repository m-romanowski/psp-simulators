package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;

public interface AuditTrail {

  void record(AuditRecord record);

  List<AuditRecord> of(ProviderId provider, TestRunId testRunId);

  int clear(ProviderId provider, TestRunId testRunId);

  int clearAll(ProviderId provider);
}
