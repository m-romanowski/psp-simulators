package dev.marcinromanowski.pspsim.provider.stripe;

import java.time.Duration;
import java.time.Instant;

@FunctionalInterface
interface StripeScheduledOutcomes {

  void theOutcomeScheduledFor(StripePaymentIntent settling, Instant landing, Duration after);
}
