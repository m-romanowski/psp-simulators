package dev.marcinromanowski.pspsim.kernel.webhook;

import org.jspecify.annotations.Nullable;

public final class AttemptWithheldException extends AttemptRefusedException {

  public static final String THE_PROVIDER_WITHHELD_IT =
      "the provider withheld the attempt this retry asked for at another target, so nothing was"
          + " posted and the delivery keeps its state, its schedule and its attempts. The"
          + " provider's reason: ";

  private static final long serialVersionUID = 1L;

  public AttemptWithheldException(@Nullable String providerObjectId, String reason) {
    super(providerObjectId, THE_PROVIDER_WITHHELD_IT + reason);
  }
}
