package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface WorldlinePayments {

  boolean claimIdentifier(
      TestRunId testRunId, String identifier, String paymentId, int payIdSub, Instant at);

  boolean insertIfAbsent(WorldlinePayment payment);

  Optional<WorldlinePayment> findById(TestRunId testRunId, String anyIdentifier);

  List<WorldlinePayment> allById(TestRunId testRunId, List<String> anyIdentifiers);

  LedgerClaim applyMaintenance(WorldlinePayment payment, WorldlineMaintenance maintenance);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentId);

  List<ArrivalOf<WorldlinePayment>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();

  record WorldlineMaintenance(
      String mintedIdentifier,
      int payIdSub,
      @Nullable MonetaryOperation operation,
      String nativeStatus,
      String statusCategory,
      int statusCode,
      boolean authorizationClosed,
      Instant at) {}
}
