package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.ArrayList;
import java.util.List;

public enum EvidenceClass {
  PUBLISHED_RULE("published rule"),
  PUBLISHED_EXAMPLE("published example"),
  LIVE_PROBE("live probe"),
  SIMULATOR_CONTRACT("simulator contract");

  private final String spelling;

  EvidenceClass(String spelling) {
    this.spelling = spelling;
  }

  public String spelling() {
    return spelling;
  }

  static EvidenceClass spelled(String sample, String spelling) {
    List<String> known = new ArrayList<>();
    for (EvidenceClass evidence : values()) {
      if (evidence.spelling.equals(spelling)) {
        return evidence;
      }
      known.add(evidence.spelling);
    }
    throw new MalformedVendorSampleException(
        sample + " names evidence class " + spelling + ", which is none of " + known);
  }
}
