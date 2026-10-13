package dev.marcinromanowski.pspsim.provider.stripe;

sealed interface StripeShortLivedAccess {

  record NoShortLivedKeyReachesThisRoute() implements StripeShortLivedAccess {}

  record TheCustomerTheCallNames(String customerId) implements StripeShortLivedAccess {}
}
