package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface StripePaymentIntents {

  boolean claimIntentId(TestRunId testRunId, String intentId);

  boolean insertIfAbsent(StripePaymentIntent intent, @Nullable StripeCharge charge);

  Optional<StripePaymentIntent> find(TestRunId testRunId, String intentId);

  List<StripePaymentIntent> allById(TestRunId testRunId, List<String> intentIds);

  Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String intentId);

  List<ArrivalOf<StripePaymentIntent>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit);

  LedgerClaim confirmed(
      StripePaymentIntent intent,
      StripeIntentStatus reached,
      String paymentMethod,
      MonetaryOperation operation,
      StripeCharge charge,
      Instant at);

  LedgerClaim declined(
      StripePaymentIntent intent,
      StripeDecline decline,
      @Nullable String paymentMethod,
      StripeCharge charge,
      Instant at);

  LedgerClaim pending(
      StripePaymentIntent intent,
      String paymentMethod,
      Instant settlesAt,
      @Nullable StripeDecline settlesWith,
      Instant at);

  LedgerClaim captured(
      StripePaymentIntent intent,
      StripeIntentStatus reached,
      MonetaryOperation operation,
      Instant at);

  LedgerClaim cancelled(
      StripePaymentIntent intent, @Nullable StripeCancellationReason reason, Instant at);

  int clear(TestRunId testRunId);

  int clearAll();
}
