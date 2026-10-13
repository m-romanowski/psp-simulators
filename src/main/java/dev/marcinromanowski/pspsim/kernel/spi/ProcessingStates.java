package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public sealed interface ProcessingStates {

  record NoneOfThem(String reason) implements ProcessingStates {

    public NoneOfThem {
      if (reason.isBlank()) {
        throw new IllegalArgumentException(
            "a provider that answers no operation with a processing state must say, in its own"
                + " words, what its contract publishes instead of one, because a tester told only"
                + " that the state is absent cannot tell a contract that forbids it from a build"
                + " that has not reached it");
      }
    }
  }

  record OnTheseOperations(Set<String> operations) implements ProcessingStates {

    public OnTheseOperations {
      if (operations.isEmpty()) {
        throw new IllegalArgumentException(
            "a provider that names no operation reaches no processing state, and must say so as "
                + NoneOfThem.class.getSimpleName()
                + " with the words its contract uses");
      }
      operations = Set.copyOf(new LinkedHashSet<>(operations));
    }
  }

  static ProcessingStates noneOfThem(String reason) {
    return new NoneOfThem(reason);
  }

  static ProcessingStates onTheseOperations(String... operations) {
    return new OnTheseOperations(new LinkedHashSet<>(List.of(operations)));
  }

  default boolean reachedBy(String operation) {
    return this instanceof OnTheseOperations named && named.operations().contains(operation);
  }
}
