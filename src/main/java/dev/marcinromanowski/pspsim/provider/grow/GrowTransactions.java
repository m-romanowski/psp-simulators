package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface GrowTransactions {

  boolean insertIfAbsent(GrowTransaction transaction);

  boolean claimTransactionId(TestRunId testRunId, String transactionId);

  Charged charge(GrowTransaction transaction, GrowStoredCardCharge submission);

  enum Charged {
    APPLIED,

    UNIQUE_IDENTIFIER_TAKEN,

    GROUP_ALREADY_CHARGED,

    TRANSACTION_ID_TAKEN
  }

  Submitted submit(GrowStoredCardCharge submission);

  enum Submitted {
    APPLIED,

    UNIQUE_IDENTIFIER_TAKEN,

    GROUP_ALREADY_CHARGED
  }

  void observed(KeyedObservation observation);

  KeyedExchangePage exchanges(KeyedExchangeQuery query);

  Optional<GrowStoredCardCharge> submissionOf(TestRunId testRunId, String transactionId);

  Optional<GrowStoredCardCharge> submissionUnder(
      TestRunId testRunId, String merchantId, String uniqueIdentifier);

  Optional<GrowTransaction> find(
      TestRunId testRunId, String merchantId, String transactionId, String transactionToken);

  Optional<GrowTransaction> findByTransactionId(TestRunId testRunId, String transactionId);

  List<GrowTransaction> allByTransactionId(TestRunId testRunId, List<String> transactionIds);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String transactionId);

  List<ArrivalOf<GrowTransaction>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  LedgerClaim append(GrowTransaction transaction, MonetaryOperation operation);

  Refunded refund(GrowTransaction refunded, MonetaryOperation refund, GrowTransaction row);

  enum Refunded {
    APPLIED,

    ID_TAKEN,

    CLAIM_TAKEN,

    OBJECT_GONE
  }

  int clear(TestRunId testRunId);

  int clearAll();
}
