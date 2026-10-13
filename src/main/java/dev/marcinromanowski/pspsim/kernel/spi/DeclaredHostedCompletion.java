package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredHostedCompletion(
    @Nullable String testRunId, @Nullable String outcome, @Nullable String card) {

  public PayerOutcome payerOutcome() {
    String named = outcome;
    if (named == null || named.isBlank()) {
      throw new IllegalArgumentException(
          "outcome is required to complete a hosted page: name what the payer met, one of PAID,"
              + " DECLINED, ABANDONED");
    }
    return PayerOutcome.declared(named);
  }
}
