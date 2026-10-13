package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record ProviderOperation(
    String name, Set<MatchFact> carries, String storiesKeyedOn, StoryKeyLifetime storyKeyLifetime) {

  public ProviderOperation {
    if (name.isBlank()) {
      throw new IllegalArgumentException(
          "an operation of a provider vocabulary must be named as the provider spells it on the"
              + " wire");
    }
    if (storiesKeyedOn.isBlank()) {
      throw new IllegalArgumentException(
          "operation "
              + name
              + " must name what a story about it is keyed on, because a story with no key would"
              + " be shared by every call of the test run");
    }
    carries = Set.copyOf(carries);
  }

  public List<String> theFactsItCarries() {
    List<String> named = new ArrayList<>();
    for (MatchFact fact : MatchFact.values()) {
      if (carries.contains(fact)) {
        named.add(fact.property());
      }
    }
    return List.copyOf(named);
  }

  public boolean itsFlowKeyMayBeReplaced() {
    return storyKeyLifetime == StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE;
  }
}
