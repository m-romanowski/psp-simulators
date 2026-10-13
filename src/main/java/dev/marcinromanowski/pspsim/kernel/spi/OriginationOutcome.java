package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public sealed interface OriginationOutcome {

  record Originated(String providerObjectId, String whatExistsNow, @Nullable String scenarioId)
      implements OriginationOutcome {

    public Originated {
      if (providerObjectId.isBlank()) {
        throw new IllegalArgumentException(
            "a payment that started carries the id its provider gave it, and a blank one leaves a"
                + " tester with nothing to look the payment up by on any other screen");
      }
      if (whatExistsNow.isBlank()) {
        throw new IllegalArgumentException(
            "a provider that started a payment must say, in its own words, what now exists and"
                + " what moves it forward, because what one provider leaves behind is a hosted"
                + " page waiting for a payer and what another leaves behind is an authorization"
                + " already taken");
      }
    }

    public Originated(String providerObjectId, String whatExistsNow) {
      this(providerObjectId, whatExistsNow, null);
    }
  }

  record RefusedByTheProvider(String inItsOwnWords, @Nullable String scenarioId)
      implements OriginationOutcome {

    public RefusedByTheProvider {
      if (inItsOwnWords.isBlank()) {
        throw new IllegalArgumentException(
            "a provider that refused to start a payment answers with the refusal its own contract"
                + " publishes, and an empty refusal tells a tester only that something failed");
      }
    }

    public RefusedByTheProvider(String inItsOwnWords) {
      this(inItsOwnWords, null);
    }
  }

  record AnsweredByAScenarioInstead(String scenarioId) implements OriginationOutcome {}

  record NotOffered(String whatThisProviderHasInstead) implements OriginationOutcome {

    public NotOffered {
      if (whatThisProviderHasInstead.isBlank()) {
        throw new IllegalArgumentException(
            "a provider whose module starts no payment must say in its own words what its contract"
                + " has in place of one, because a screen reporting that absence in this"
                + " simulator's words would be reporting a decision this simulator did not make");
      }
    }
  }

  record NoSuchAccount() implements OriginationOutcome {}

  default @Nullable String theRuleThatDecidedIt() {
    return switch (this) {
      case Originated originated -> originated.scenarioId();
      case RefusedByTheProvider refused -> refused.scenarioId();
      case AnsweredByAScenarioInstead answered -> answered.scenarioId();
      case NotOffered _ -> null;
      case NoSuchAccount _ -> null;
    };
  }
}
