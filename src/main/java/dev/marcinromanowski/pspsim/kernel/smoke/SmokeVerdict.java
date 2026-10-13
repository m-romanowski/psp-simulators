package dev.marcinromanowski.pspsim.kernel.smoke;

import dev.marcinromanowski.pspsim.kernel.ProviderId;

public sealed interface SmokeVerdict {

  ProviderId provider();

  String operation();

  Provenance provenance();

  record Passed(ProviderId provider, String operation, Provenance provenance, int held)
      implements SmokeVerdict {

    public Passed {
      if (held < 1) {
        throw new IllegalArgumentException("a passing check holds at least one expectation");
      }
    }
  }

  record Failed(
      ProviderId provider, String operation, Provenance provenance, String field, FailureKind kind)
      implements SmokeVerdict {}

  record NotApplicable(ProviderId provider, String operation, Provenance provenance, String reason)
      implements SmokeVerdict {}
}
