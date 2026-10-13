package dev.marcinromanowski.pspsim.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum RailGroup {
  DAILY_WORK("daily-work", "Daily work"),
  SET_UP("set-up", "Set up"),
  EVIDENCE("evidence", "Evidence"),
  TAKE_IT_ELSEWHERE("take-it-elsewhere", "Take it elsewhere");

  private final String token;

  private final String label;

  RailGroup(String token, String label) {
    this.token = token;
    this.label = label;
  }

  public String token() {
    return token;
  }

  public String label() {
    return label;
  }

  public static Optional<RailGroup> named(String token) {
    for (RailGroup group : values()) {
      if (group.token.equals(token)) {
        return Optional.of(group);
      }
    }
    return Optional.empty();
  }

  public static String theOnesThisRailHas() {
    List<String> tokens = new ArrayList<>();
    for (RailGroup group : values()) {
      tokens.add(group.token);
    }
    return String.join(", ", tokens);
  }
}
