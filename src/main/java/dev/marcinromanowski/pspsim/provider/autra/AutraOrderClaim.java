package dev.marcinromanowski.pspsim.provider.autra;

sealed interface AutraOrderClaim {

  record Fresh() implements AutraOrderClaim {}

  record HeldByARequestStillRunning() implements AutraOrderClaim {}

  record AlreadyPreAuthorized(String paymentId) implements AutraOrderClaim {}
}
