package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface AzericardPayments {

  boolean claimIntRef(TestRunId testRunId, String intRef);

  boolean claimOrderTrace(TestRunId testRunId, String terminalId, LocalDate day, String trace);

  boolean holdOrderTrace(
      TestRunId testRunId, String terminalId, LocalDate day, String trace, String sessionId);

  Optional<String> theSessionHoldingOrderTrace(
      TestRunId testRunId, String terminalId, LocalDate day, String trace);

  boolean takeOverOrderTrace(TestRunId testRunId, String sessionId);

  boolean insertIfAbsent(AzericardPayment payment);

  Optional<AzericardPayment> findByIntRef(TestRunId testRunId, String intRef);

  List<AzericardPayment> byOrder(TestRunId testRunId, String terminalId, String order);

  List<AzericardPayment> allByIntRef(TestRunId testRunId, List<String> intRefs);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String intRef);

  List<ArrivalOf<AzericardPayment>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  Optional<AzericardPayment> findAddressed(
      TestRunId testRunId, String terminalId, String order, String rrn, String intRef);

  boolean hasSeen(TestRunId testRunId, String accountId, String keyName, String keyValue);

  void observed(AzericardObservedRequest request);

  KeyedExchangePage exchanges(KeyedExchangeQuery query);

  LedgerClaim append(AzericardPayment payment, MonetaryOperation operation, Instant at);

  LedgerClaim reverseTheHold(AzericardPayment payment, Instant at);

  int clear(TestRunId testRunId);

  int clearAll();
}
