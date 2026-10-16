package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface TranzilaChallenges {

  int open(TranzilaChallenge challenge);

  Optional<TranzilaChallenge> find(String trackId);

  Optional<TranzilaChallenge> find(TestRunId testRunId, String trackId);

  Optional<TranzilaChallenge> lockedUntilCompleted(
      TestRunId testRunId, String accountId, String trackId);

  int answer(TranzilaChallenge answered);

  int complete(TranzilaChallenge completed);

  List<TranzilaChallenge> allById(TestRunId testRunId, List<String> trackIds);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String trackId);

  List<ArrivalOf<TranzilaChallenge>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();
}
