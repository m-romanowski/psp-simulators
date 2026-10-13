package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface StripeRefunds {

  Refunded refund(
      StripePaymentIntent intent,
      @Nullable MonetaryOperation operation,
      StripeRefund refund,
      Instant at);

  Optional<StripeRefund> find(TestRunId testRunId, String refundId);

  List<StripeRefund> allById(TestRunId testRunId, List<String> refundIds);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String refundId);

  List<ArrivalOf<StripeRefund>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  int clear(TestRunId testRunId);

  int clearAll();

  enum Refunded {
    APPLIED,

    ID_TAKEN,

    CLAIM_TAKEN,

    OBJECT_GONE
  }
}
