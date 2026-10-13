package dev.marcinromanowski.pspsim.kernel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public enum ACharacterAValueIsMadeOf {
  A_LOWERCASE_LETTER("abcdefghijklmnopqrstuvwxyz", "a lowercase letter"),
  AN_UPPERCASE_LETTER("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "an uppercase letter"),
  A_DIGIT("0123456789", "a digit"),
  A_DOT(".", "a dot"),
  A_HYPHEN("-", "a hyphen"),
  AN_UNDERSCORE("_", "an underscore"),
  A_SPACE(" ", "a space");

  private static final String A_LETTER = "a letter";

  private final String everyOneOfItsOwn;

  private final String inWords;

  ACharacterAValueIsMadeOf(String everyOneOfItsOwn, String inWords) {
    this.everyOneOfItsOwn = everyOneOfItsOwn;
    this.inWords = inWords;
  }

  public char oneOfItsOwn() {
    return everyOneOfItsOwn.charAt(0);
  }

  public String everyOneOfItsOwn() {
    return everyOneOfItsOwn;
  }

  public String inWords() {
    return inWords;
  }

  public static Optional<ACharacterAValueIsMadeOf> of(char character) {
    for (ACharacterAValueIsMadeOf kind : values()) {
      if (kind.everyOneOfItsOwn.indexOf(character) >= 0) {
        return Optional.of(kind);
      }
    }
    return Optional.empty();
  }

  public static String theWordsOfEachOf(Set<ACharacterAValueIsMadeOf> kinds) {
    if (kinds.isEmpty()) {
      throw new IllegalArgumentException(
          "no character at all was named, and a shape made of no character accepts nothing, so"
              + " there would be no value a tester could type to satisfy it");
    }
    List<String> said = new ArrayList<>();
    boolean lowercase = kinds.contains(A_LOWERCASE_LETTER);
    boolean uppercase = kinds.contains(AN_UPPERCASE_LETTER);
    if (lowercase && uppercase) {
      said.add(A_LETTER);
    } else if (lowercase) {
      said.add(A_LOWERCASE_LETTER.inWords);
    } else if (uppercase) {
      said.add(AN_UPPERCASE_LETTER.inWords);
    }
    Set<ACharacterAValueIsMadeOf> beyondLetters = EnumSet.copyOf(kinds);
    beyondLetters.remove(A_LOWERCASE_LETTER);
    beyondLetters.remove(AN_UPPERCASE_LETTER);
    for (ACharacterAValueIsMadeOf kind : beyondLetters) {
      said.add(kind.inWords);
    }
    if (said.size() == 1) {
      return said.get(0);
    }
    return String.join(", ", said.subList(0, said.size() - 1)) + " or " + said.get(said.size() - 1);
  }
}
