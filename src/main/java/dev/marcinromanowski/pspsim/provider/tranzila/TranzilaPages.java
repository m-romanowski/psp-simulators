package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface TranzilaPages {

  int open(TranzilaPage page);

  Optional<TranzilaPage> find(String pageId);

  Optional<TranzilaPage> find(TestRunId testRunId, String pageId);

  Optional<TranzilaPage> lockedWhileOpen(TestRunId testRunId, String pageId);

  Optional<TranzilaPage> ofTheTransaction(TestRunId testRunId, long transactionId);

  List<TranzilaPage> allById(TestRunId testRunId, List<String> pageIds);

  void settle(TranzilaPage settled);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String pageId);

  List<ArrivalOf<TranzilaPage>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();
}
