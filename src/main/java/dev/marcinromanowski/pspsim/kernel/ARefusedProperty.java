package dev.marcinromanowski.pspsim.kernel;

import java.util.List;

public final class ARefusedProperty {

  public static final String CANNOT_BE_APPLIED = " cannot be applied: ";

  public static final String BESIDE_THE_NEXT_ONE = "; ";

  private ARefusedProperty() {}

  public static String theWordsOf(String property, String reason) {
    return property + CANNOT_BE_APPLIED + reason;
  }

  public static String theWordsOfEachOf(List<String> said) {
    return String.join(BESIDE_THE_NEXT_ONE, said);
  }
}
