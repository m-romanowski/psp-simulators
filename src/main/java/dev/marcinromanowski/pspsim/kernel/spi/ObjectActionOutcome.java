package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public sealed interface ObjectActionOutcome {

  record Applied(
      String providerObjectId, @Nullable String createdObjectId, @Nullable String scenarioId)
      implements ObjectActionOutcome {

    public Applied {
      if (providerObjectId.equals(createdObjectId)) {
        throw new IllegalArgumentException(
            "createdObjectId names a second object the action brought into existence, and this"
                + " one names the object that was acted on, which reports as new an object the"
                + " caller was already looking at");
      }
    }

    public Applied(String providerObjectId, @Nullable String createdObjectId) {
      this(providerObjectId, createdObjectId, null);
    }
  }

  record RefusedByTheProvider(String inItsOwnWords, @Nullable String scenarioId)
      implements ObjectActionOutcome {

    public RefusedByTheProvider {
      if (inItsOwnWords.isBlank()) {
        throw new IllegalArgumentException(
            "a provider that refused an action answers with the refusal its own contract"
                + " publishes, and an empty refusal tells a tester only that something failed");
      }
    }

    public RefusedByTheProvider(String inItsOwnWords) {
      this(inItsOwnWords, null);
    }
  }

  record AnsweredByAScenarioInstead(String scenarioId) implements ObjectActionOutcome {}

  record NotOffered(String whatThisProviderHasInstead) implements ObjectActionOutcome {}

  record NoSuchObject() implements ObjectActionOutcome {}

  static ObjectActionOutcome notOffered(ObjectActions actions, ObjectAction action) {
    return new NotOffered(actions.whatItHasInsteadOf(action));
  }

  default @Nullable String theRuleThatDecidedIt() {
    return switch (this) {
      case Applied applied -> applied.scenarioId();
      case RefusedByTheProvider refused -> refused.scenarioId();
      case AnsweredByAScenarioInstead answered -> answered.scenarioId();
      case NotOffered _ -> null;
      case NoSuchObject _ -> null;
    };
  }
}
