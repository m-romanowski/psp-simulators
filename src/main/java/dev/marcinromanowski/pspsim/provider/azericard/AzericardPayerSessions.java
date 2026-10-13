package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface AzericardPayerSessions {

  boolean open(AzericardPayerSession session);

  Optional<AzericardPayerSession> find(String sessionId);

  Optional<AzericardPayerSession> find(TestRunId testRunId, String sessionId);

  List<AzericardPayerSession> allById(TestRunId testRunId, List<String> sessionIds);

  Optional<AzericardPayerSession> lockedWhileOpen(TestRunId testRunId, String sessionId);

  void settle(
      TestRunId testRunId,
      String sessionId,
      PayerOutcome outcome,
      @Nullable String intRef,
      Instant at);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String sessionId);

  List<ArrivalOf<AzericardPayerSession>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();
}
