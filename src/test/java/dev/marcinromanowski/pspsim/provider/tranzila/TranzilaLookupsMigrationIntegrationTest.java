package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class TranzilaLookupsMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_tranzila_kept_user_fields";

  private static final String RUN = "before-lookups";

  private static final MigrationVersion BEFORE_THE_LOOKUPS = MigrationVersion.fromVersion("87");

  private static final MigrationVersion WHEN_THE_LOOKUPS_LANDED =
      MigrationVersion.fromVersion("88");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverTransactionsThatKeptOnlyTheirDcDisable() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_LOOKUPS).migrate();
    jdbc.update(aTransaction(10000001, "'order-7'"));
    jdbc.update(aTransaction(10000002, "null"));
    jdbc.update(aTransaction(10000003, "'" + "d".repeat(254) + "'"));
    flyway(WHEN_THE_LOOKUPS_LANDED).migrate();
  }

  @Test
  void every_dc_disable_a_transaction_kept_becomes_its_first_user_defined_field() {
    List<Map<String, Object>> fields =
        jdbc.queryForList(
            "select test_run_id, transaction_id, position, name, value from "
                + SCHEMA
                + ".tranzila_transaction_user_field order by transaction_id");

    assertThat(fields)
        .containsExactly(
            Map.of(
                "test_run_id",
                RUN,
                "transaction_id",
                10000001L,
                "position",
                0,
                "name",
                "DCdisable",
                "value",
                "order-7"),
            Map.of(
                "test_run_id",
                RUN,
                "transaction_id",
                10000003L,
                "position",
                0,
                "name",
                "DCdisable",
                "value",
                "d".repeat(254)));
    assertThat(
            jdbc.queryForList(
                "select test_run_id, account_id, slot, name from "
                    + SCHEMA
                    + ".tranzila_user_slot"))
        .containsExactly(
            Map.of("test_run_id", RUN, "account_id", "a-1", "slot", 1, "name", "DCdisable"));
    assertThat(count("tranzila_transaction")).isEqualTo(3);
    assertThat(count("tranzila_transaction_item")).isZero();
  }

  @Test
  void the_fields_and_items_of_a_transaction_go_with_it() {
    jdbc.update(anItem(10000002, 0, 1, "10.53"));

    jdbc.update("delete from " + SCHEMA + ".tranzila_transaction where transaction_id = 10000001");
    jdbc.update("delete from " + SCHEMA + ".tranzila_transaction where transaction_id = 10000002");

    assertThat(count("tranzila_transaction_user_field")).isEqualTo(1);
    assertThat(count("tranzila_transaction_item")).isZero();
  }

  @Test
  void a_field_or_an_item_belongs_to_a_transaction_the_store_holds() {
    assertThatThrownBy(() -> jdbc.update(aField(99999999, 0)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(anItem(99999999, 0, 1, "1")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void every_check_refuses_the_value_it_was_written_against() {
    assertThatCode(() -> jdbc.update(aField(10000002, 0))).doesNotThrowAnyException();
    assertThatThrownBy(() -> jdbc.update(aField(10000002, -1)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> jdbc.update(anItem(10000002, 0, 1, "0.5"))).doesNotThrowAnyException();
    assertThatThrownBy(() -> jdbc.update(anItem(10000002, -1, 1, "1")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(anItem(10000002, 1, 0, "1")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(anItem(10000002, 2, 1, "1E2")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(anItem(10000002, 0, 1, "1")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_terminal_holds_twenty_five_slots_each_naming_one_name_once() {
    assertThatCode(() -> jdbc.update(aSlot("a-1", 25, "last"))).doesNotThrowAnyException();
    assertThatCode(() -> jdbc.update(aSlot("a-2", 1, "DCdisable"))).doesNotThrowAnyException();
    assertThatThrownBy(() -> jdbc.update(aSlot("a-1", 26, "beyond")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(aSlot("a-1", 0, "before")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(aSlot("a-1", 2, "DCdisable")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(aSlot("a-1", 1, "another")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String aSlot(String account, int slot, String name) {
    return "insert into "
        + SCHEMA
        + ".tranzila_user_slot (test_run_id, account_id, slot, name, first_recorded_at) values ('"
        + RUN
        + "', '"
        + account
        + "', "
        + slot
        + ", '"
        + name
        + "', timestamptz '2026-10-13T12:00:00Z')";
  }

  private String aTransaction(long transactionId, String dcDisable) {
    return "insert into "
        + SCHEMA
        + ".tranzila_transaction (test_run_id, transaction_id, root_txn_id, account_id,"
        + " terminal_name, txn_type, processor_response_code, amount, currency_code, auth_number,"
        + " card_mask, card_type, expiry_month, expiry_year, response_cvv, response_id,"
        + " dc_disable, origin, created_at) values ('"
        + RUN
        + "', "
        + transactionId
        + ", "
        + transactionId
        + ", 'a-1', 'myterminal', 'debit', '000', '10.53', 'ILS', '1000001',"
        + " '458021xxxxxx4245', 2, 7, 30, '0', '0', "
        + dcDisable
        + ", 'PROVIDER_API', timestamptz '2026-10-13T12:00:00Z')";
  }

  private String aField(long transactionId, int position) {
    return "insert into "
        + SCHEMA
        + ".tranzila_transaction_user_field (test_run_id, transaction_id, position, name, value)"
        + " values ('"
        + RUN
        + "', "
        + transactionId
        + ", "
        + position
        + ", 'orderid', 'ORD-1')";
  }

  private String anItem(long transactionId, int position, long units, String unitPrice) {
    return "insert into "
        + SCHEMA
        + ".tranzila_transaction_item (test_run_id, transaction_id, position, name, units_number,"
        + " unit_price, item_type) values ('"
        + RUN
        + "', "
        + transactionId
        + ", "
        + position
        + ", 'a', "
        + units
        + ", '"
        + unitPrice
        + "', 'I')";
  }

  private int count(String table) {
    Integer counted =
        jdbc.queryForObject("select count(*) from " + SCHEMA + "." + table, Integer.class);
    return counted == null ? -1 : counted;
  }

  private Flyway flyway(MigrationVersion target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .schemas(SCHEMA)
        .target(target)
        .load();
  }
}
