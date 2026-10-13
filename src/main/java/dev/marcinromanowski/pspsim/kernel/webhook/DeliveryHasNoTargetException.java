package dev.marcinromanowski.pspsim.kernel.webhook;

import org.jspecify.annotations.Nullable;

public final class DeliveryHasNoTargetException extends AttemptRefusedException {

  public static final String IT_CANNOT_BE_ATTEMPTED =
      "this delivery has no target: the provider found no address to post it to when it was"
          + " scheduled, so it is undeliverable for good and no attempt can be made on it, to any"
          + " address. The provider has to schedule the callback again once a target is"
          + " configured";

  private static final long serialVersionUID = 1L;

  public DeliveryHasNoTargetException(@Nullable String providerObjectId) {
    super(providerObjectId, IT_CANNOT_BE_ATTEMPTED);
  }
}
