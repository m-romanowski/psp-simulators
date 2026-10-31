package dev.marcinromanowski.pspsim.provider.stripe;

sealed interface StripePublishableAccess {

  record NoPublishableKeyReachesThisRoute() implements StripePublishableAccess {}

  record ItsOwnAccountWithoutAClientSecret() implements StripePublishableAccess {}

  record ThePaymentIntentItsClientSecretOpens(String intentId) implements StripePublishableAccess {}

  record TheSetupIntentItsClientSecretOpens(String setupIntentId)
      implements StripePublishableAccess {}
}
