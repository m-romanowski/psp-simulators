package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface AuditReads {

  AuditPage pageOf(ProviderId provider, AuditQuery query);

  List<AuditRecord> latestOf(
      ProviderId provider, TestRunId testRunId, @Nullable ObjectOrigin origin, int limit);

  List<TestRunActivity> theRunsNewestTouchFirst();
}
