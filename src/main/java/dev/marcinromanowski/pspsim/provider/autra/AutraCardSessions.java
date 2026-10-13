package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface AutraCardSessions {

  boolean insertIfAbsent(AutraCardSession session);

  Optional<AutraCardSession> findById(TestRunId testRunId, String cardSessionId);

  List<AutraCardSession> allById(TestRunId testRunId, List<String> cardSessionIds);

  Optional<AutraCardSession> findByPage(String cardSessionId, String pageToken);

  List<AutraStoredCard> tokenizedIn(TestRunId testRunId);

  boolean moveFromPendingTo(AutraCardSession moved);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String cardSessionId);

  List<ArrivalOf<AutraCardSession>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();
}
