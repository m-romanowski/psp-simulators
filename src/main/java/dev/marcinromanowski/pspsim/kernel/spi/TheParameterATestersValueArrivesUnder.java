package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;

public final class TheParameterATestersValueArrivesUnder {

  public static final String IS_NAMED_FIELD = "field";

  public static final String AND_A_FORM_CONTROL_NAMES_THE_KEY_AFTER_A_DOT = ".";

  public static final String ON_A_FORM_CONTROL =
      IS_NAMED_FIELD + AND_A_FORM_CONTROL_NAMES_THE_KEY_AFTER_A_DOT;

  private TheParameterATestersValueArrivesUnder() {}

  public static String onAFormControlFor(String key) {
    return ON_A_FORM_CONTROL + key;
  }

  public static SequencedMap<String, String> theValuesAFormPostedUnderIt(
      Map<String, String> parameters) {
    LinkedHashMap<String, String> values = new LinkedHashMap<>();
    for (Map.Entry<String, String> parameter : parameters.entrySet()) {
      String name = parameter.getKey();
      if (name.startsWith(ON_A_FORM_CONTROL)) {
        values.put(name.substring(ON_A_FORM_CONTROL.length()), parameter.getValue());
      }
    }
    return Collections.unmodifiableSequencedMap(values);
  }
}
