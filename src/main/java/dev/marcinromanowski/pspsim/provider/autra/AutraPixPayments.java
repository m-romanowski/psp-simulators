package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface AutraPixPayments {

  boolean insertIfAbsent(AutraPixPayment payment);

  Optional<AutraPixPayment> findById(TestRunId testRunId, String pixId);

  List<AutraPixPayment> allById(TestRunId testRunId, List<String> pixIds);

  boolean moveFromPendingTo(AutraPixPayment payment, String nativeStatus, Instant at);

  boolean moveFromAuthorizedToSettled(AutraPixPayment payment, Instant at);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String pixId);

  List<ArrivalOf<AutraPixPayment>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();
}
