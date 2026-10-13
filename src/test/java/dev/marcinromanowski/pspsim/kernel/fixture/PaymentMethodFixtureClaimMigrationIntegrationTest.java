package dev.marcinromanowski.pspsim.kernel.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentMethodFixtureClaimMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_provider_id_could_name_an_owner";

  private static final String RUN = "before-an-owner-key";

  private static final String PROVIDER = "stripe";

  private static final String OLDER_THAN_OWNER_KEYS = "fx_older_than_owner_keys";

  private static final String SEEDED_AFTER = "fx_seeded_after";

  private static final MigrationVersion BEFORE_AN_OWNER_KEY = MigrationVersion.fromVersion("52");

  private static final MigrationVersion WHEN_AN_OWNER_KEY_ARRIVED =
      MigrationVersion.fromVersion("53");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseClaimsWereAllExclusive() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_AN_OWNER_KEY).migrate();
    fixtureOfTheOldShape(OLDER_THAN_OWNER_KEYS, "a-card-seeded-before");
    claimOfTheOldShape(OLDER_THAN_OWNER_KEYS, "customerId", "cus_claimed_before");
    flyway(WHEN_AN_OWNER_KEY_ARRIVED).migrate();
  }

  @Test
  void
      a_claim_made_before_an_owner_key_existed_still_holds_its_value_against_every_other_fixture() {
    Map<String, Object> kept = theClaim(OLDER_THAN_OWNER_KEYS);

    assertThat(kept.get("claims_the_value_alone")).isEqualTo(true);
    assertThat(kept.get("id_value")).isEqualTo("cus_claimed_before");
  }

  @Test
  void a_second_exclusive_claim_on_a_value_an_older_row_holds_is_refused_by_the_migrated_index() {
    fixtureOfTheOldShape(SEEDED_AFTER, "a-card-seeded-after");

    assertThatThrownBy(() -> claim(SEEDED_AFTER, "customerId", "cus_claimed_before", true))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void
      a_second_shared_claim_on_a_value_an_older_row_holds_is_taken_because_the_key_names_an_owner() {
    fixtureOfTheOldShape(SEEDED_AFTER, "a-card-seeded-after");
    jdbc.update(
        "update "
            + SCHEMA
            + ".simulator_payment_method_fixture_claim set claims_the_value_alone = false"
            + " where fixture_id = ?",
        OLDER_THAN_OWNER_KEYS);

    assertThatCode(() -> claim(SEEDED_AFTER, "customerId", "cus_claimed_before", false))
        .doesNotThrowAnyException();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".simulator_payment_method_fixture_claim where id_value = ?",
                Integer.class,
                "cus_claimed_before"))
        .isEqualTo(2);
  }

  @Test
  void one_fixture_still_cannot_claim_one_value_twice_under_one_key_however_it_is_shared() {
    assertThatThrownBy(
            () -> claim(OLDER_THAN_OWNER_KEYS, "customerId", "cus_claimed_before", false))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void claim(String fixtureId, String key, String value, boolean alone) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_payment_method_fixture_claim"
            + " (provider, test_run_id, id_key, id_value, fixture_id, claims_the_value_alone)"
            + " values (?, ?, ?, ?, ?, ?)",
        PROVIDER,
        RUN,
        key,
        value,
        fixtureId,
        alone);
  }

  private void claimOfTheOldShape(String fixtureId, String key, String value) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_payment_method_fixture_claim"
            + " (provider, test_run_id, id_key, id_value, fixture_id)"
            + " values (?, ?, ?, ?, ?)",
        PROVIDER,
        RUN,
        key,
        value,
        fixtureId);
  }

  private void fixtureOfTheOldShape(String fixtureId, String alias) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_payment_method_fixture"
            + " (fixture_id, provider, test_run_id, merchant_id, owner_id, method_type,"
            + " fixture_alias, card_brand, card_last4, card_exp_month, card_exp_year,"
            + " card_funding, provider_ids, default_scenario, created_at)"
            + " values (?, ?, ?, 'merchant-1', 'owner-1', 'CARD', ?, 'visa', '4242', 12, 2030,"
            + " 'credit', '{}', null, now())",
        fixtureId,
        PROVIDER,
        RUN,
        alias);
  }

  private Map<String, Object> theClaim(String fixtureId) {
    return jdbc.queryForMap(
        "select * from " + SCHEMA + ".simulator_payment_method_fixture_claim where fixture_id = ?",
        fixtureId);
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
