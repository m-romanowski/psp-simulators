package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.regex.Pattern;

public record FollowUp(String name, String asTheProviderNamesIt, String whatItMoves) {

  private static final Pattern A_PATH_SEGMENT = Pattern.compile("[a-z][a-z0-9-]*");

  public FollowUp {
    if (!A_PATH_SEGMENT.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "a follow-up is named in the path of the control route that performs it, so its name is"
              + " lowercase letters, digits and hyphens beginning with a letter, and \""
              + name
              + "\" is not");
    }
    if (asTheProviderNamesIt.isBlank()) {
      throw new IllegalArgumentException(
          "follow-up "
              + name
              + " must carry the words its provider uses for it, because the screen draws its"
              + " button from those words and knows no provider's vocabulary of its own");
    }
    if (whatItMoves.isBlank()) {
      throw new IllegalArgumentException(
          "follow-up "
              + name
              + " must say what it moves the object from and to, because a tester pressing it"
              + " changes provider state that no request of their own client asked for");
    }
  }
}
