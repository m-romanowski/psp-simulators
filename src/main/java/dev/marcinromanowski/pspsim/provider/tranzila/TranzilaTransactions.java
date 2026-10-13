package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface TranzilaTransactions {

  TranzilaInsert insert(TranzilaTransaction transaction);

  Optional<TranzilaPayment> paymentHolding(TestRunId testRunId, long transactionId);

  List<TranzilaPayment> paymentsHolding(TestRunId testRunId, List<Long> transactionIds);

  Optional<TranzilaTransaction> carryingTheToken(
      TestRunId testRunId, String accountId, String token);

  Optional<TranzilaTransaction> approvedUnderDcDisable(
      TestRunId testRunId, String accountId, String dcDisable, Instant since);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, long transactionId);

  List<ArrivalOf<TranzilaObject>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();
}
