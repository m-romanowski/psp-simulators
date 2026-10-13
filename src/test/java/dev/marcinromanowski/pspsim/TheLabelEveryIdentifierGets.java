package dev.marcinromanowski.pspsim;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

public final class TheLabelEveryIdentifierGets {

  public static final String THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL =
      "both callers of states take the first state in the answer whose identity answers to the"
          + " identifier they asked for, so when two of a provider's stores hold one identifier"
          + " the label a tester reads is decided by the order the provider concatenated its"
          + " stores in. The single read decides the same thing by the order of its or-chain."
          + " Nothing ties the two orders together, so each provider is asserted to answer, for"
          + " every identifier asked, the state its single read answers";

  private TheLabelEveryIdentifierGets() {}

  public static SequencedMap<String, Optional<ProviderObjectState>> inOneAnswer(
      List<ProviderObjectState> batched, List<String> asked) {
    SequencedMap<String, Optional<ProviderObjectState>> labelled = new LinkedHashMap<>();
    for (String identifier : asked) {
      labelled.put(identifier, theFirstStateAnsweringTo(batched, identifier));
    }
    return labelled;
  }

  public static SequencedMap<String, Optional<ProviderObjectState>> askingOneAtATime(
      SimulatorProvider module, TestRunId testRunId, List<String> asked) {
    SequencedMap<String, Optional<ProviderObjectState>> labelled = new LinkedHashMap<>();
    for (String identifier : asked) {
      labelled.put(identifier, module.state(testRunId, identifier));
    }
    return labelled;
  }

  private static Optional<ProviderObjectState> theFirstStateAnsweringTo(
      List<ProviderObjectState> batched, String identifier) {
    return batched.stream().filter(state -> state.identity().answersTo(identifier)).findFirst();
  }
}
