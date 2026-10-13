package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface StripePaymentMethods {

  boolean insertIfAbsent(StripePaymentMethod method);

  Optional<StripePaymentMethod> find(TestRunId testRunId, String paymentMethodId);

  List<StripePaymentMethod> allById(TestRunId testRunId, List<String> paymentMethodIds);

  List<StripePaymentMethod> of(
      TestRunId testRunId,
      String accountId,
      @Nullable String customerId,
      @Nullable StripePaymentMethodType type);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentMethodId);

  List<ArrivalOf<StripePaymentMethod>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  Detachment detached(StripePaymentMethod method, Instant at);

  int detachedEveryMethodOf(TestRunId testRunId, String accountId, String customerId, Instant at);

  void forget(TestRunId testRunId, String paymentMethodId);

  int clear(TestRunId testRunId);

  int clearAll();

  enum Detachment {
    APPLIED,

    ALREADY_DETACHED,

    OBJECT_GONE
  }
}
