package dev.marcinromanowski.pspsim;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;

public final class TheSequenceOneAnswerIsIn {

  private TheSequenceOneAnswerIsIn() {}

  public static List<ProviderObjectState> askingOneAtATimeInTheOrderAsked(
      SimulatorProvider module, TestRunId testRunId, List<String> asked) {
    List<ProviderObjectState> expected = new ArrayList<>();
    for (String identifier : asked) {
      module
          .state(testRunId, identifier)
          .filter(state -> !expected.contains(state))
          .ifPresent(expected::add);
    }
    return List.copyOf(expected);
  }
}
