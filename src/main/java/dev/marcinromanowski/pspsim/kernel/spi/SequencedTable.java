package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SequencedTable(String name, List<String> keyColumns) {

  public static final String SEQUENCE = "seq";

  public static final String VERDICT = "verdict";

  private static final String BETWEEN = ", ";

  public SequencedTable {
    if (keyColumns.isEmpty()) {
      throw new IllegalArgumentException(
          "a sequenced table numbers its rows under a key, and table "
              + name
              + " names no column that key is kept in");
    }
    keyColumns = List.copyOf(keyColumns);
  }

  public String appendingAtTheNextFreeSequence(List<String> columns) {
    return "insert into "
        + name
        + " ("
        + String.join(BETWEEN, keyColumns)
        + BETWEEN
        + SEQUENCE
        + BETWEEN
        + String.join(BETWEEN, columns)
        + ") select "
        + placeholders(keyColumns.size())
        + BETWEEN
        + theNextFreeSequence()
        + BETWEEN
        + placeholders(columns.size())
        + " from "
        + name
        + whereTheKeyMatches()
        + onTheSequenceAnotherRowTook();
  }

  public String recordingUnderAKey(List<String> columns) {
    return "insert into "
        + name
        + " ("
        + String.join(BETWEEN, keyColumns)
        + BETWEEN
        + SEQUENCE
        + BETWEEN
        + VERDICT
        + BETWEEN
        + String.join(BETWEEN, columns)
        + ") select "
        + placeholders(keyColumns.size())
        + BETWEEN
        + theNextFreeSequence()
        + ", case when coalesce(bool_or("
        + VERDICT
        + " in ("
        + placeholders(ExchangeVerdict.THAT_RAN_THE_OPERATION.size())
        + ")), false) then ? else ? end"
        + BETWEEN
        + placeholders(columns.size())
        + " from "
        + name
        + whereTheKeyMatches()
        + onTheSequenceAnotherRowTook();
  }

  private static String theNextFreeSequence() {
    return "coalesce(max(" + SEQUENCE + ") + 1, 0)";
  }

  private String whereTheKeyMatches() {
    return " where " + String.join(" = ? and ", keyColumns) + " = ?";
  }

  private String onTheSequenceAnotherRowTook() {
    List<String> primaryKey = new ArrayList<>(keyColumns);
    primaryKey.add(SEQUENCE);
    return " on conflict (" + String.join(BETWEEN, primaryKey) + ") do nothing";
  }

  private static String placeholders(int count) {
    return String.join(BETWEEN, Collections.nCopies(count, "?"));
  }
}
