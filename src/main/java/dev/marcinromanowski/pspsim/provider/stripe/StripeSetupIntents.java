package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface StripeSetupIntents {

  boolean insertIfAbsent(StripeSetupIntent intent);

  Optional<StripeSetupIntent> find(TestRunId testRunId, String setupIntentId);

  List<StripeSetupIntent> allById(TestRunId testRunId, List<String> setupIntentIds);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String setupIntentId);

  List<ArrivalOf<StripeSetupIntent>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  Setup succeeded(
      StripeSetupIntent intent, String paymentMethodId, @Nullable String customerId, Instant at);

  Setup failed(StripeSetupIntent intent, StripeDecline decline, String paymentMethodId, Instant at);

  int clear(TestRunId testRunId);

  int clearAll();

  enum Setup {
    APPLIED,

    THE_SETUP_MOVED_ON,

    THE_METHOD_IS_NO_LONGER_ATTACHABLE,

    OBJECT_GONE
  }
}
