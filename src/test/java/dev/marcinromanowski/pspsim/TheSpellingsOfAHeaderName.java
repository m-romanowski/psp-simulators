package dev.marcinromanowski.pspsim;

import java.util.Map;
import org.assertj.core.api.Condition;

public final class TheSpellingsOfAHeaderName {

  public static final String ONE_HEADER_ANSWERS_TO_EVERY_SPELLING_OF_ITS_NAME =
      "HTTP header names are case-insensitive, so Idempotent-Replayed, idempotent-replayed and"
          + " IDEMPOTENT-REPLAYED name one header and a client that sends or reads any of the"
          + " three is sending or reading the same one. An absence asserted against a single"
          + " spelling of the name is a weaker claim than the sentence it is written under, and"
          + " it reads as proof: it passes over an answer that carries the header the client"
          + " would read, under a spelling nobody wrote the assertion for. Every absence of a"
          + " header this suite claims is therefore claimed against every spelling of the name at"
          + " once.";

  private TheSpellingsOfAHeaderName() {}

  public static Condition<Map<String, ?>> aHeaderOfThisNameUnderAnySpelling(String name) {
    return new Condition<>(
        headers -> oneSpellingOfItIsCarriedBy(headers, name),
        "a header named %s under any spelling of that name, because header names are"
            + " case-insensitive and %s carried in another case is that same header",
        name,
        name);
  }

  private static boolean oneSpellingOfItIsCarriedBy(Map<String, ?> headers, String name) {
    for (String carried : headers.keySet()) {
      if (name.equalsIgnoreCase(carried)) {
        return true;
      }
    }
    return false;
  }
}
