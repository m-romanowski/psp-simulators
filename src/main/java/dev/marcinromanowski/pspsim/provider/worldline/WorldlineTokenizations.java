package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface WorldlineTokenizations {

  boolean insertIfAbsent(WorldlineTokenization session);

  Optional<WorldlineTokenization> findById(TestRunId testRunId, String hostedTokenizationId);

  List<WorldlineTokenization> allById(TestRunId testRunId, List<String> hostedTokenizationIds);

  Optional<WorldlineTokenization> findByIdInAnyRun(String hostedTokenizationId);

  Optional<WorldlineTokenization> holdingTheToken(TestRunId testRunId, String token);

  boolean moveFromAwaitingThePayerTo(WorldlineTokenization moved);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String hostedTokenizationId);

  List<ArrivalOf<WorldlineTokenization>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();
}
