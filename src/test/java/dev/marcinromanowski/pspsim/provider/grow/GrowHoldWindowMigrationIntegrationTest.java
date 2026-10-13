package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class GrowHoldWindowMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_hold_window_had_to_be_a_span_a_hold_outlives";

  private static final String RUN = "before-a-hold-window-had-to-be-a-span";

  private static final String A_YEAR_IN_HOURS = "PT8760H";

  private static final String THE_DEFAULT_WINDOW = "PT168H";

  private static final String THE_DEFAULT_VOID = "PT240H";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverEveryShapeTheUnconstrainedColumnsCouldHold() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("76")).migrate();
    everyShapeTheOldStoreCouldHold().forEach((account, span) -> insert(account, span, span));
    flyway(MigrationVersion.LATEST).migrate();
  }

  private static Map<String, String> everyShapeTheOldStoreCouldHold() {
    Map<String, String> shapes = new LinkedHashMap<>();
    shapes.put("canonical", THE_DEFAULT_WINDOW);
    shapes.put("iso-days-form", "P7D");
    shapes.put("days-and-time", "P5DT6H30M");
    shapes.put("sub-second", "PT0.5S");
    shapes.put("one-microsecond", "PT0.000001S");
    shapes.put("exactly-a-year", A_YEAR_IN_HOURS);
    shapes.put("zero", "PT0S");
    shapes.put("zero-days", "P0D");
    shapes.put("negative", "PT-120H");
    shapes.put("negative-whole", "-P5D");
    shapes.put("a-year-and-a-second", "PT8760H1S");
    shapes.put("finer-than-the-store", "PT0.000000001S");
    shapes.put("finer-above-the-floor", "PT1.0000015S");
    shapes.put("enormous", "PT876000000H");
    shapes.put("the-longest-java-reads", "PT2562047788015215H30M7S");
    shapes.put("leading-plus", "+PT1H");
    shapes.put("lowercase", "pt1h");
    shapes.put("signed-component", "PT+1H");
    shapes.put("comma-decimal", "PT0,5S");
    shapes.put("time-with-no-component", "P7DT");
    shapes.put("weeks", "P1W");
    shapes.put("bare-number", "5");
    shapes.put("unreadable", "soon");
    shapes.put("blank", "   ");
    return shapes;
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "canonical",
        "iso-days-form",
        "days-and-time",
        "sub-second",
        "one-microsecond",
        "exactly-a-year"
      })
  void a_span_the_columns_predicate_admits_survives_in_the_very_text_it_was_stored_in(
      String account) {
    String stored = everyShapeTheOldStoreCouldHold().get(account);

    assertThat(storedWindowOf(account))
        .as(
            "P7D and PT168H are one span written two ways, and only the provider ever writes the"
                + " canonical one: V27 copied the raw configured string out of settings, so a"
                + " backfilled row can hold either, and neither is worth rewriting")
        .isEqualTo(stored);
    assertThat(storedVoidOf(account)).isEqualTo(stored);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "zero",
        "zero-days",
        "negative",
        "negative-whole",
        "a-year-and-a-second",
        "finer-than-the-store",
        "finer-above-the-floor",
        "enormous",
        "the-longest-java-reads",
        "leading-plus",
        "lowercase",
        "signed-component",
        "comma-decimal",
        "time-with-no-component",
        "weeks",
        "bare-number",
        "unreadable",
        "blank"
      })
  void a_span_the_columns_predicate_does_not_admit_is_reset_to_grows_published_default(
      String account) {
    assertThat(storedWindowOf(account))
        .as(
            "a constraint cannot refuse a row that is already there, and an ALTER that aborted"
                + " would leave the simulator unable to boot, so every value the predicate does"
                + " not admit takes the vendor default a blank value already falls back on. Four"
                + " of these -- +PT1H, pt1h, PT+1H and PT0,5S -- Duration.parse does read, and"
                + " only a pre-V27 backfill can hold them, because JdbcGrowAccounts writes"
                + " Duration.toString() and that is canonical")
        .isEqualTo(THE_DEFAULT_WINDOW);
    assertThat(storedVoidOf(account))
        .as(
            "the second update statement was unproved: every seeded row held a valid"
                + " automatic_void_after, so deleting that statement left this class green while"
                + " a store holding an out-of-floor void aborted the ALTER")
        .isEqualTo(THE_DEFAULT_VOID);
  }

  @Test
  void every_migrated_row_still_names_both_spans() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".grow_account"
                    + " where capture_window is null or automatic_void_after is null",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("select count(*) from " + SCHEMA + ".grow_account", Integer.class))
        .isEqualTo(everyShapeTheOldStoreCouldHold().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PT168H",
        "P7D",
        "PT0.5S",
        "PT0.000001S",
        "PT1.0015S",
        "PT8760H",
        "P365D",
        "P5DT6H30M",
        "PT8759H59M59.999999S"
      })
  void the_migrated_table_admits_a_span_a_hold_can_outlive_in_either_column(String admitted) {
    assertThatCode(() -> insert("takes-" + admitted, admitted, admitted))
        .doesNotThrowAnyException();

    assertThat(storedWindowOf("takes-" + admitted)).isEqualTo(admitted);
    assertThat(storedVoidOf("takes-" + admitted)).isEqualTo(admitted);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PT0S",
        "P0D",
        "PT-120H",
        "-P5D",
        "PT8761H",
        "P366D",
        "PT8760H1S",
        "PT8760H0.000001S",
        "PT8760H0.000000001S",
        "PT0.000000001S",
        "PT0.0000005S",
        "PT1.0000015S",
        "PT876000000H",
        "PT2562047788015215H30M7S",
        "+PT1H",
        "pt1h",
        "PT+1H",
        "PT0,5S",
        "P7DT",
        "P1W",
        "5",
        "soon",
        "   ",
        "",
        "PT9X",
        "PT"
      })
  void the_migrated_table_refuses_a_span_no_hold_could_outlive_in_the_capture_window(
      String refused) {
    assertThatCode(() -> insert("refuses-" + refused, refused, THE_DEFAULT_VOID))
        .as(
            "the column was text not null with no check at all, so a value the provider now"
                + " refuses could still be written straight into the store")
        .isInstanceOf(DataAccessException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "PT0S",
        "P0D",
        "PT-120H",
        "-P5D",
        "PT8761H",
        "P366D",
        "PT8760H1S",
        "PT8760H0.000001S",
        "PT8760H0.000000001S",
        "PT0.000000001S",
        "PT0.0000005S",
        "PT1.0000015S",
        "PT876000000H",
        "PT2562047788015215H30M7S",
        "+PT1H",
        "pt1h",
        "PT+1H",
        "PT0,5S",
        "P7DT",
        "P1W",
        "5",
        "soon",
        "   ",
        "",
        "PT9X",
        "PT"
      })
  void the_migrated_table_refuses_the_same_spans_in_the_automatic_void_column(String refused) {
    assertThatThrownBy(() -> insert("void-" + refused, THE_DEFAULT_WINDOW, refused))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void a_row_that_predates_the_migration_can_be_read_back_by_the_provider_it_once_crashed() {
    List<String> windows =
        jdbc.queryForList("select capture_window from " + SCHEMA + ".grow_account", String.class);

    assertThat(windows)
        .as(
            "P1W, 5 and soon all threw DateTimeParseException out of JdbcGrowAccounts at load"
                + " before this migration, because nothing had ever judged the column")
        .isNotEmpty()
        .allSatisfy(
            stored -> assertThatCode(() -> Duration.parse(stored)).doesNotThrowAnyException());
    assertThat(
            jdbc.queryForList(
                "select automatic_void_after from " + SCHEMA + ".grow_account", String.class))
        .isNotEmpty()
        .allSatisfy(
            stored -> assertThatCode(() -> Duration.parse(stored)).doesNotThrowAnyException());
  }

  private String storedWindowOf(String account) {
    return storedColumnOf("capture_window", account);
  }

  private String storedVoidOf(String account) {
    return storedColumnOf("automatic_void_after", account);
  }

  private String storedColumnOf(String column, String account) {
    return jdbc.queryForObject(
        "select " + column + " from " + SCHEMA + ".grow_account where account_id = ?",
        String.class,
        account);
  }

  private void insert(String accountId, String window, String voidsAfter) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_account (test_run_id, account_id, user_id, page_code,"
            + " callback_acknowledgement, settlement_ceiling, refund_allowance,"
            + " refund_excess_rejection, merchant_balance, process_lookup_rejection, wire_form,"
            + " capture_window, automatic_void_after)"
            + " values (?, ?, ?, ?, 'HTTP_2XX', 'FRAME_CAPTURE', 'ONE_REFUND', 'ORIGINAL_AMOUNT',"
            + " 'AVAILABLE', 'PUBLISHED_EXAMPLE', 'LIVE_WIRE', ?, ?)",
        RUN,
        accountId,
        "user-" + accountId,
        "page-" + accountId,
        window,
        voidsAfter);
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
