package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ARefusedProperty;
import java.util.ArrayList;
import java.util.List;

public record ScenarioRefusal(String property, String reason) {

  public String inItsOwnWords() {
    return ARefusedProperty.theWordsOf(property, reason);
  }

  public static String theWordsOf(List<ScenarioRefusal> refusals) {
    List<String> said = new ArrayList<>();
    for (ScenarioRefusal refusal : refusals) {
      said.add(refusal.inItsOwnWords());
    }
    return ARefusedProperty.theWordsOfEachOf(said);
  }
}
