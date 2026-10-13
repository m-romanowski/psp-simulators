package dev.marcinromanowski.pspsim.kernel.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ActionFacts {

  public static final String REFUSED_BECAUSE = "refusedBecause";

  public static final String REFUSED_PROPERTIES = "refusedProperties";

  public static final String THE_RUN_AN_IMPORT_WRITES_INTO = "into";

  public static final String AN_IMPORT_WRITES_NOTHING = "dryRun";

  public static final String THE_SECRETS_AN_IMPORT_WAS_GIVEN = "secretsSupplied";

  private final Map<String, List<String>> named = new LinkedHashMap<>();

  private ActionFacts() {}

  public static ActionFacts none() {
    return new ActionFacts();
  }

  public ActionFacts and(String name, @Nullable String value) {
    if (value != null) {
      named.put(name, List.of(value));
    }
    return this;
  }

  public ActionFacts andCount(String name, int count) {
    named.put(name, List.of(String.valueOf(count)));
    return this;
  }

  public ActionFacts andEach(String name, List<String> values) {
    if (!values.isEmpty()) {
      named.put(name, List.copyOf(values));
    }
    return this;
  }

  public static Map<String, List<String>> beside(
      Map<String, List<String>> facts, String name, String value) {
    LinkedHashMap<String, List<String>> recorded = new LinkedHashMap<>(facts);
    recorded.put(name, List.of(value));
    return recorded;
  }

  public Map<String, List<String>> asRecorded() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(named));
  }
}
