package dev.marcinromanowski.pspsim.kernel.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum StoryKeying {
  FLOW,
  TEST_RUN;

  public static Optional<StoryKeying> of(String declared) {
    for (StoryKeying keying : values()) {
      if (keying.name().equals(declared)) {
        return Optional.of(keying);
      }
    }
    return Optional.empty();
  }

  public static String theDeclarableNames() {
    List<String> names = new ArrayList<>();
    for (StoryKeying keying : values()) {
      names.add(keying.name());
    }
    return String.join(", ", names);
  }
}
