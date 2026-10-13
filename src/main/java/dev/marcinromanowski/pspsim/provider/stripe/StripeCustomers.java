package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface StripeCustomers {

  boolean insertIfAbsent(StripeCustomer customer);

  Optional<StripeCustomer> find(TestRunId testRunId, String customerId);

  List<StripeCustomer> allById(TestRunId testRunId, List<String> customerIds);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String customerId);

  List<ArrivalOf<StripeCustomer>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  Changed updated(StripeCustomer customer, @Nullable String email, Instant at);

  Changed deleted(StripeCustomer customer, Instant at);

  int clear(TestRunId testRunId);

  int clearAll();

  enum Changed {
    APPLIED,

    ALREADY_DELETED,

    OBJECT_GONE
  }
}
