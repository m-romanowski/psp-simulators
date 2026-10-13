package dev.marcinromanowski.pspsim.kernel.scenario;

import org.jspecify.annotations.Nullable;

public record ScenarioStory(
    String name, StoryKeying keyedOn, @Nullable String whenState, @Nullable String thenState) {

  public static final String INITIAL_STATE = "Started";

  public static final int LONGEST_NAME = 64;

  public ScenarioStory {
    requireALabel("story", name);
    requireALabelOrNothing("whenState", whenState);
    requireALabelOrNothing("thenState", thenState);
    if (thenState != null && thenState.equals(whenState)) {
      throw new IllegalArgumentException(
          "thenState must name a state other than whenState, or the step moves the story nowhere");
    }
  }

  public boolean matches(@Nullable String currentState) {
    return whenState == null || whenState.equals(stateOrInitial(currentState));
  }

  public boolean narrowsByState() {
    return whenState != null;
  }

  public static String stateOrInitial(@Nullable String currentState) {
    return currentState == null ? INITIAL_STATE : currentState;
  }

  private static void requireALabelOrNothing(String property, @Nullable String label) {
    if (label != null) {
      requireALabel(property, label);
    }
  }

  private static void requireALabel(String property, String label) {
    if (label.isBlank()) {
      throw new IllegalArgumentException(property + " must name something, or be absent");
    }
    if (label.codePointCount(0, label.length()) > LONGEST_NAME) {
      throw new IllegalArgumentException(
          property + " must be at most " + LONGEST_NAME + " characters");
    }
  }
}
