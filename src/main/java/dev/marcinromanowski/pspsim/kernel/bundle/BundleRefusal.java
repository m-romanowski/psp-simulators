package dev.marcinromanowski.pspsim.kernel.bundle;

import dev.marcinromanowski.pspsim.kernel.ARefusedProperty;
import java.util.ArrayList;
import java.util.List;

public record BundleRefusal(String property, String reason) {

  public String inItsOwnWords() {
    return ARefusedProperty.theWordsOf(property, reason);
  }

  public static String theWordsOf(List<BundleRefusal> refusals) {
    List<String> said = new ArrayList<>();
    for (BundleRefusal refusal : refusals) {
      said.add(refusal.inItsOwnWords());
    }
    return ARefusedProperty.theWordsOfEachOf(said);
  }
}
