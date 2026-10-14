package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKeys;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

public final class KeyedExchangeLogs {

  private final JdbcClient jdbc;
  private final FingerprintKeys keys;
  private final SequencedRows sequenced;
  private final ObjectMapper json;

  public KeyedExchangeLogs(
      JdbcClient jdbc, FingerprintKeys keys, SequencedRows sequenced, ObjectMapper json) {
    this.jdbc = jdbc;
    this.keys = keys;
    this.sequenced = sequenced;
    this.json = json;
  }

  public KeyedExchangeLog of(ProviderKeyMechanisms mechanisms, KeyedExchangeTable table) {
    return new KeyedExchangeLog(
        mechanisms, keys, new JdbcKeyedExchangeRows(jdbc, table), sequenced, json);
  }

  public SequencedRows sequenced() {
    return sequenced;
  }
}
