package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface GrowPaymentProcesses {

  boolean insertIfAbsent(GrowPaymentProcess process);

  boolean claimProcessId(TestRunId testRunId, String processId);

  Optional<GrowPaymentProcess> find(TestRunId testRunId, String processId, String processToken);

  Optional<GrowPaymentProcess> findByProcessId(TestRunId testRunId, String processId);

  List<GrowPaymentProcess> allByProcessId(TestRunId testRunId, List<String> processIds);

  Optional<GrowPaymentProcess> findByHostedLink(String hostedLink);

  Optional<GrowPaymentProcess> findByCardToken(
      TestRunId testRunId, String merchantId, String cardToken);

  List<GrowPaymentProcess> theProcessesThatSavedACard(TestRunId testRunId, String merchantId);

  Optional<GrowPaymentProcess> findByTransaction(
      TestRunId testRunId, String merchantId, String transactionId, String transactionToken);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String processId);

  List<ArrivalOf<GrowPaymentProcess>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  boolean reachOutcome(GrowPaymentProcess process);

  int clear(TestRunId testRunId);

  int clearAll();
}
