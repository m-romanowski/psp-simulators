package dev.marcinromanowski.pspsim.kernel.spi;

import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcKeyedExchangeRows implements KeyedExchangeRows {

  private final JdbcClient jdbc;
  private final KeyedExchangeTable table;

  public JdbcKeyedExchangeRows(JdbcClient jdbc, KeyedExchangeTable table) {
    this.jdbc = jdbc;
    this.table = table;
  }

  @Override
  public int recordedAtTheNextFreeSequence(KeyedExchangeRow row) {
    return jdbc.sql(table.insert()).params(table.parametersOf(row)).update();
  }
}
