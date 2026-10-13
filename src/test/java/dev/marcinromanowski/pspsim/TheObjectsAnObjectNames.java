package dev.marcinromanowski.pspsim;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

public final class TheObjectsAnObjectNames {

  public static final String A_NAME_AND_AN_IDENTIFIER_PER_OBJECT =
      "the objects one object names are a field name and the identifier it names, two arguments"
          + " per named object, so an odd number of arguments leaves one name without an"
          + " identifier";

  private TheObjectsAnObjectNames() {}

  public static SequencedMap<String, String> theObjectsNamed(String... fieldsAndTheIdsTheyName) {
    if (fieldsAndTheIdsTheyName.length % 2 != 0) {
      throw new IllegalArgumentException(A_NAME_AND_AN_IDENTIFIER_PER_OBJECT);
    }
    SequencedMap<String, String> named = new LinkedHashMap<>();
    for (int at = 0; at < fieldsAndTheIdsTheyName.length; at += 2) {
      named.put(fieldsAndTheIdsTheyName[at], fieldsAndTheIdsTheyName[at + 1]);
    }
    return named;
  }
}
