package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredFollowUp(@Nullable String testRunId) {

  public static @Nullable String theRunNamedIn(@Nullable DeclaredFollowUp body) {
    return body == null ? null : body.testRunId();
  }
}
