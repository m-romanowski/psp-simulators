package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredReset(@Nullable String testRunId, @Nullable String scope) {

  public static final String THE_RESET_IT_ASKED_THIS_PROVIDER_TO_PERFORM =
      "the reset this request asked this provider to perform";
}
