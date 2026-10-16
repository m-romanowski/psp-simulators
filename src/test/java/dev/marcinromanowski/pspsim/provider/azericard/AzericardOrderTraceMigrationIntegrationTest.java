package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.idempotency.InMemoryKeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.spi.JdbcKeyedExchangeRows;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeLog;
import dev.marcinromanowski.pspsim.kernel.spi.SequencedRows;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class AzericardOrderTraceMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_page_held_an_order_trace";

  private static final MigrationVersion THE_MIGRATION = MigrationVersion.fromVersion("73");

  private static final TestRunId RUN = TestRunId.of("before-the-page-run");

  private static final String ANOTHER_RUN = "before-the-page-another-run";

  private static final String TERMINAL = "17205353";

  private static final String ANOTHER_TERMINAL = "17205354";

  private static final LocalDate DAY = LocalDate.parse("2026-09-28");

  private static final LocalDate THE_NEXT_DAY = LocalDate.parse("2026-09-29");

  private static final String TRACE = "120001";

  private static final List<List<String>> THE_OLD_ROWS =
      List.of(
          List.of(RUN.value(), TERMINAL, DAY.toString(), TRACE),
          List.of(RUN.value(), TERMINAL, THE_NEXT_DAY.toString(), TRACE),
          List.of(RUN.value(), TERMINAL, THE_NEXT_DAY.toString(), "120002"),
          List.of(RUN.value(), ANOTHER_TERMINAL, THE_NEXT_DAY.toString(), TRACE),
          List.of(ANOTHER_RUN, TERMINAL, THE_NEXT_DAY.toString(), TRACE));

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JdbcConnectionDetails database;
  @Autowired private ObjectMapper json;

  private SingleConnectionDataSource migrated;
  private JdbcAzericardPayments payments;
  private JdbcAzericardPayerSessions sessions;

  @BeforeEach
  void migrateOverTracesClaimedBeforeAPageCouldHoldOne() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(theVersionBefore(THE_MIGRATION)).migrate();
    for (List<String> row : THE_OLD_ROWS) {
      jdbc.update(
          "insert into "
              + SCHEMA
              + ".azericard_order_trace (test_run_id, terminal_id, trace_day, trace)"
              + " values (?, ?, ?::date, ?)",
          row.get(0),
          row.get(1),
          row.get(2),
          row.get(3));
    }
    flyway(MigrationVersion.LATEST).migrate();
    migrated =
        new SingleConnectionDataSource(
            database.getJdbcUrl()
                + (database.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema="
                + SCHEMA,
            database.getUsername(),
            database.getPassword(),
            true);
    JdbcClient client = JdbcClient.create(migrated);
    payments =
        new JdbcAzericardPayments(
            client,
            new TransactionTemplate(new DataSourceTransactionManager(migrated)),
            new KeyedExchangeLog(
                AzericardKeyMechanisms.MECHANISMS,
                testRunId -> InMemoryKeyedExchanges.THE_KEY_OF_EVERY_TEST_RUN,
                new JdbcKeyedExchangeRows(client, JdbcAzericardPayments.EXCHANGES),
                new SequencedRows(),
                json));
    sessions = new JdbcAzericardPayerSessions(client, json);
    assertThat(client.sql("select current_schema()").query(String.class).single())
        .isEqualTo(SCHEMA);
  }

  @AfterEach
  void closeTheConnectionToTheMigratedSchema() {
    migrated.destroy();
  }

  @Test
  void every_trace_claimed_before_the_migration_survives_it_as_claimed_by_a_payment() {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select test_run_id, terminal_id, trace_day::text as trace_day, trace,"
                + " held_by_session from "
                + SCHEMA
                + ".azericard_order_trace order by test_run_id, terminal_id, trace_day, trace");

    assertThat(rows)
        .extracting(
            row ->
                String.join(
                    " ",
                    String.valueOf(row.get("test_run_id")),
                    String.valueOf(row.get("terminal_id")),
                    String.valueOf(row.get("trace_day")),
                    String.valueOf(row.get("trace"))))
        .containsExactlyInAnyOrderElementsOf(
            THE_OLD_ROWS.stream().map(row -> String.join(" ", row)).toList());
    assertThat(rows).allSatisfy(row -> assertThat(row.get("held_by_session")).isNull());
    for (List<String> row : THE_OLD_ROWS) {
      assertThat(
              payments.theSessionHoldingOrderTrace(
                  TestRunId.of(row.get(0)), row.get(1), LocalDate.parse(row.get(2)), row.get(3)))
          .isEmpty();
    }
  }

  @Test
  void a_page_opens_for_an_order_no_trace_claimed_before_the_migration_names() {
    AzericardPayerSession page = aPage("s".repeat(32), "20260929120009");

    assertThat(sessions.open(page)).isTrue();
    assertThat(payments.holdOrderTrace(RUN, TERMINAL, THE_NEXT_DAY, "120009", page.sessionId()))
        .isTrue();
    assertThat(payments.theSessionHoldingOrderTrace(RUN, TERMINAL, THE_NEXT_DAY, "120009"))
        .contains(page.sessionId());
    assertThat(payments.claimOrderTrace(RUN, TERMINAL, THE_NEXT_DAY, "120009")).isFalse();
    assertThat(payments.takeOverOrderTrace(RUN, page.sessionId())).isTrue();
  }

  @Test
  void a_trace_claimed_before_the_migration_still_refuses_a_page_for_its_order() {
    assertThat(payments.holdOrderTrace(RUN, TERMINAL, DAY, TRACE, "t".repeat(32))).isFalse();
    assertThat(payments.holdOrderTrace(RUN, TERMINAL, THE_NEXT_DAY, TRACE, "u".repeat(32)))
        .isFalse();
    assertThat(payments.takeOverOrderTrace(RUN, "t".repeat(32))).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".azericard_order_trace", Integer.class))
        .isEqualTo(THE_OLD_ROWS.size());
  }

  @Test
  void
      clearing_every_page_takes_every_trace_a_page_holds_and_leaves_every_trace_a_payment_claimed() {
    AzericardPayerSession page = aPage("v".repeat(32), "20260929120010");
    assertThat(sessions.open(page)).isTrue();
    assertThat(payments.holdOrderTrace(RUN, TERMINAL, THE_NEXT_DAY, "120010", page.sessionId()))
        .isTrue();

    assertThat(sessions.clearAll()).isEqualTo(1);

    assertThat(payments.theSessionHoldingOrderTrace(RUN, TERMINAL, THE_NEXT_DAY, "120010"))
        .isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".azericard_order_trace", Integer.class))
        .isEqualTo(THE_OLD_ROWS.size());
  }

  private static AzericardPayerSession aPage(String sessionId, String order) {
    return new AzericardPayerSession(
        sessionId,
        RUN,
        AzericardRoute.TOKEN_CGI_LINK,
        TERMINAL,
        "merchant-1",
        Map.of(AzericardFields.ORDER, List.of(order)),
        Map.of(),
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        Instant.parse("2026-09-29T12:00:00Z"),
        null);
  }

  private MigrationVersion theVersionBefore(MigrationVersion migration) {
    return Arrays.stream(flyway(MigrationVersion.LATEST).info().all())
        .map(MigrationInfo::getVersion)
        .filter(version -> version.isNewerThan("0") && migration.isNewerThan(version.getVersion()))
        .max(MigrationVersion::compareTo)
        .orElseThrow();
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
