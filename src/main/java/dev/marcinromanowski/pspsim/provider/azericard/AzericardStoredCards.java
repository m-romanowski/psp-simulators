package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface AzericardStoredCards {

  boolean claim(AzericardStoredCard card);

  int forgetSeeded(TestRunId testRunId, String fixtureId);

  Optional<AzericardStoredCard> findByToken(TestRunId testRunId, String token);

  List<AzericardStoredCard> allByToken(TestRunId testRunId, List<String> tokens);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String token);

  List<ArrivalOf<AzericardStoredCard>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();
}
