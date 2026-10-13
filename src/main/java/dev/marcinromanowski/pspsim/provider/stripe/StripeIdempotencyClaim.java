package dev.marcinromanowski.pspsim.provider.stripe;

sealed interface StripeIdempotencyClaim {

  record Fresh() implements StripeIdempotencyClaim {}

  record Replayed(StripeStoredAnswer answer) implements StripeIdempotencyClaim {}

  record InProgressElsewhere() implements StripeIdempotencyClaim {}

  record ReusedWithOtherParameters() implements StripeIdempotencyClaim {}
}
