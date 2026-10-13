package dev.marcinromanowski.pspsim.provider.azericard;

import org.jspecify.annotations.Nullable;

sealed interface AzericardPresentedCard {

  @Nullable String last4();

  record NoCardCarried() implements AzericardPresentedCard {

    @Override
    public @Nullable String last4() {
      return null;
    }
  }

  record Accepted(String last4) implements AzericardPresentedCard {}

  record Declined(@Nullable String last4) implements AzericardPresentedCard {}
}
