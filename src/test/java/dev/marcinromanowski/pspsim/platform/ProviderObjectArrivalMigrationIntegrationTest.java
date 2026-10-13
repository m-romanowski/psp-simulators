package dev.marcinromanowski.pspsim.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ProviderObjectArrivalMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_provider_object_had_an_arrival";

  private static final String RUN = "before-arrival";

  private static final MigrationVersion BEFORE_ARRIVAL = MigrationVersion.fromVersion("38");

  private static final MigrationVersion WITH_ARRIVAL = MigrationVersion.fromVersion("39");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseObjectsHadNoPlaceInAnyOrder() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_ARRIVAL).migrate();
    aTransactionOfTheOldShape("100001");
    aTransactionOfTheOldShape("100002");
    aTransactionOfTheOldShape("100003");
    flyway(WITH_ARRIVAL).migrate();
  }

  @Test
  void every_payment_a_run_already_held_keeps_a_place_of_its_own_in_the_list() {
    List<Long> arrivals = arrivalsOf();

    assertThat(arrivals).hasSize(3);
    assertThat(arrivals).doesNotHaveDuplicates();
  }

  @Test
  void a_payment_made_after_the_upgrade_lists_above_every_payment_that_was_there_before_it() {
    long highestBefore = arrivalsOf().stream().mapToLong(Long::longValue).max().orElseThrow();

    aTransactionOfTheOldShape("100004");

    assertThat(arrivalOf("100004")).isGreaterThan(highestBefore);
  }

  private List<Long> arrivalsOf() {
    return jdbc.queryForList(
        "select arrival from " + SCHEMA + ".grow_transaction where test_run_id = ?",
        Long.class,
        RUN);
  }

  private long arrivalOf(String transactionId) {
    Long arrival =
        jdbc.queryForObject(
            "select arrival from "
                + SCHEMA
                + ".grow_transaction where test_run_id = ? and transaction_id = ?",
            Long.class,
            RUN,
            transactionId);
    assertThat(arrival).isNotNull();
    return arrival;
  }

  private void aTransactionOfTheOldShape(String transactionId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction (test_run_id, kind, transaction_id, transaction_token,"
            + " merchant_id, process_id, asmachta, payment_date, description, payer_full_name,"
            + " payer_phone, payer_email, amount, origin, created_at, updated_at)"
            + " values (?, 'PAYMENT', ?, 'token', 'merchant-1', '900001', '1', '01/01/26',"
            + " 'A payment', 'Israel Israeli', '0505503144', null, '89.90', 'PROVIDER_API',"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        transactionId);
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
