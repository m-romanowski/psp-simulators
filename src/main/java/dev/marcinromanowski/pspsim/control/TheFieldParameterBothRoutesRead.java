package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

final class TheFieldParameterBothRoutesRead {

  static final String IS_NAMED = TheParameterATestersValueArrivesUnder.IS_NAMED_FIELD;

  static final String BETWEEN_THE_KEY_AND_THE_VALUE = "=";

  static final String WHERE_THE_KEY_GOES = "<key>";

  static final String WHERE_THE_VALUE_GOES = "<value>";

  static final String A_FIELD_NAMES_THE_KEY_IT_IS_SENT_FOR =
      "a value sent with nothing before the "
          + BETWEEN_THE_KEY_AND_THE_VALUE
          + " names no key the provider reads it under, so nothing could be sent anywhere: ";

  static final String AND_ONE_OF_THEM_WOULD_BE_DROPPED =
      ", so one of the two would be dropped without a word, which is why it is refused here"
          + " instead. Send the field once.";

  static final String A_ROUTE_READING_THIS_PARAMETER_SAYS_WHAT_IT_TAKES_AND_WHAT_ANSWERS_ITS_KEYS =
      "a route taking a tester's values under this parameter refuses what it cannot read in its"
          + " own words, so it must say what every value it takes is, what asks for the keys those"
          + " values are sent under, and which route of this provider answers them";

  private TheFieldParameterBothRoutesRead() {}

  record TheWordsARouteTakesItsValuesIn(
      String whatEveryValueItTakesIs, String whatAsksForItsKeys, String theRouteThatAnswersThem) {

    TheWordsARouteTakesItsValuesIn {
      if (whatEveryValueItTakesIs.isBlank()
          || whatAsksForItsKeys.isBlank()
          || theRouteThatAnswersThem.isBlank()) {
        throw new IllegalArgumentException(
            A_ROUTE_READING_THIS_PARAMETER_SAYS_WHAT_IT_TAKES_AND_WHAT_ANSWERS_ITS_KEYS);
      }
    }
  }

  static String theSpellingOfOnePair() {
    return IS_NAMED
        + "="
        + WHERE_THE_KEY_GOES
        + BETWEEN_THE_KEY_AND_THE_VALUE
        + WHERE_THE_VALUE_GOES;
  }

  static String theQueryParameterAValueIsSentIn(String key) {
    return IS_NAMED + "=" + key + BETWEEN_THE_KEY_AND_THE_VALUE + WHERE_THE_VALUE_GOES;
  }

  static String aFieldCarriesTheKeyItIsSentForAndTheValueAfterIt(
      TheWordsARouteTakesItsValuesIn words) {
    return words.whatEveryValueItTakesIs()
        + " is sent as one "
        + IS_NAMED
        + " parameter spelling the key the provider reads it under, then "
        + BETWEEN_THE_KEY_AND_THE_VALUE
        + ", then the value, as "
        + theSpellingOfOnePair()
        + ". The keys "
        + words.whatAsksForItsKeys()
        + " asks for are answered by the "
        + words.theRouteThatAnswersThem()
        + " route of this provider, each with the sentence its own contract describes it by, and"
        + " this one carries no "
        + BETWEEN_THE_KEY_AND_THE_VALUE
        + " at all: ";
  }

  static String itAsksEachOfItsFieldsOnce(TheWordsARouteTakesItsValuesIn words) {
    return words.whatAsksForItsKeys()
        + " asks each of its fields once and this request sends two values for ";
  }

  static SequencedMap<String, String> theValuesItWasSent(
      @Nullable List<String> field, TheWordsARouteTakesItsValuesIn words) {
    LinkedHashMap<String, String> sent = new LinkedHashMap<>();
    for (String pair : field == null ? List.<String>of() : field) {
      int between = pair.indexOf(BETWEEN_THE_KEY_AND_THE_VALUE);
      if (between < 0) {
        throw new IllegalArgumentException(
            aFieldCarriesTheKeyItIsSentForAndTheValueAfterIt(words) + pair);
      }
      String key = pair.substring(0, between).strip();
      if (key.isEmpty()) {
        throw new IllegalArgumentException(A_FIELD_NAMES_THE_KEY_IT_IS_SENT_FOR + pair);
      }
      if (sent.put(key, pair.substring(between + BETWEEN_THE_KEY_AND_THE_VALUE.length())) != null) {
        throw new IllegalArgumentException(
            itAsksEachOfItsFieldsOnce(words) + key + AND_ONE_OF_THEM_WOULD_BE_DROPPED);
      }
    }
    return Collections.unmodifiableSequencedMap(sent);
  }
}
