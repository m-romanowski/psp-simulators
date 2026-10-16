package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface AutraPayments {

  boolean claimPaymentId(TestRunId testRunId, String paymentId);

  AutraOrderClaim claimOrder(
      TestRunId testRunId, String accountId, String documentId, String orderId, Instant at);

  void releaseOrder(TestRunId testRunId, String documentId, String orderId);

  Optional<String> theApprovedPaymentUnder(TestRunId testRunId, String documentId, String orderId);

  void settleOrder(TestRunId testRunId, String documentId, String orderId, String paymentId);

  boolean insertIfAbsent(AutraPayment payment);

  Optional<AutraPayment> findById(TestRunId testRunId, String paymentId);

  List<AutraPayment> ofMerchant(TestRunId testRunId, String documentId);

  void recordEvent(AutraPaymentEvent event);

  List<AutraPaymentEvent> timelineOf(TestRunId testRunId, String paymentId);

  List<AutraPaymentEvent> everyEventOf(TestRunId testRunId, List<String> paymentIds);

  List<AutraPayment> allById(TestRunId testRunId, List<String> paymentIds);

  LedgerClaim capture(AutraPayment payment, MonetaryOperation operation, Instant at);

  LedgerClaim reverse(
      AutraPayment payment, MonetaryOperation operation, String nativeStatus, Instant at);

  LedgerClaim releaseTheHold(AutraPayment payment, Instant at);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentId);

  List<ArrivalOf<AutraPayment>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  void observed(KeyedObservation observation);

  KeyedExchangePage exchanges(KeyedExchangeQuery query);

  int clear(TestRunId testRunId);

  int clearAll();
}
