package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeAccountSpanMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_stripe_span_was_one_the_provider_reads";

  private static final String RUN = "before-a-stripe-span-was-readable";

  private static final MigrationVersion BEFORE_THE_COLUMNS_AGREED =
      MigrationVersion.fromVersion("78");

  private static final String THE_VENDORS_PUBLISHED_WEEK = "PT168H";

  private static final String NO_AGE_AT_ALL = "PT0S";

  private static final Duration LONGEST_THE_PROVIDER_TAKES = Duration.ofDays(365);

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverEveryShapeTheCheckedColumnsCouldHold() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_COLUMNS_AGREED).migrate();
    everyShapeTheOldColumnsCouldHold().forEach((account, span) -> insert(account, span, span));
    flyway(MigrationVersion.LATEST).migrate();
  }

  private static Map<String, String> everyShapeTheOldColumnsCouldHold() {
    Map<String, String> shapes = new LinkedHashMap<>();
    shapes.put("canonical-week", THE_VENDORS_PUBLISHED_WEEK);
    shapes.put("three-days", "PT72H");
    shapes.put("the-published-age-example", "PT6M");
    shapes.put("half-a-second", "PT0.5S");
    shapes.put("one-microsecond", "PT0.000001S");
    shapes.put("one-nanosecond", "PT0.000000001S");
    shapes.put("half-a-microsecond", "PT0.0000005S");
    shapes.put("finer-than-a-microsecond", "PT1.0000015S");
    shapes.put("exactly-a-year", "PT8760H");
    shapes.put("a-nanosecond-under-a-year", "PT8759H59M59.999999999S");
    shapes.put("hours-minutes-seconds", "PT1H2M3S");
    shapes.put("hours-and-a-fraction", "PT1H0.5S");
    shapes.put("ninety-minutes", "PT90M");
    shapes.put("a-nanosecond-over-a-year", "PT8760H0.0000004S");
    shapes.put("zero", NO_AGE_AT_ALL);
    shapes.put("zero-hours", "PT00H");
    shapes.put("over-a-year", "PT8761H");
    shapes.put("a-second-over-a-year", "PT8760H1S");
    shapes.put("enormous", "PT876000H");
    shapes.put("unreadable-tail", "PT9X");
    shapes.put("letter-after-an-hour", "PT1Hx");
    shapes.put("ten-fraction-digits", "PT0.1234567890S");
    shapes.put("seconds-as-a-week", "PT604800S");
    shapes.put("comma-decimal", "PT0,5S");
    shapes.put("the-longest-java-reads", "PT2562047788015215H30M7S");
    shapes.put("trailing-space", "PT12H ");
    shapes.put("digit-after-the-last-unit", "PT1H2M3S4");
    return shapes;
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "canonical-week",
        "three-days",
        "the-published-age-example",
        "half-a-second",
        "one-microsecond",
        "finer-than-a-microsecond",
        "exactly-a-year",
        "a-nanosecond-under-a-year",
        "hours-minutes-seconds",
        "hours-and-a-fraction",
        "ninety-minutes",
        "a-nanosecond-over-a-year"
      })
  void a_span_the_new_predicate_admits_survives_in_the_very_text_it_was_stored_in(String account) {
    String stored = everyShapeTheOldColumnsCouldHold().get(account);

    assertThat(storedWindowOf(account))
        .as(
            "a window of one microsecond and a window with a finer tail than that are both"
                + " spans StripeCaptureWindow.of takes today, so a predicate that rewrote them"
                + " would change a fixture nobody asked it to change, and one that refused them"
                + " would answer the next write of a value the provider accepted with a"
                + " violation")
        .isEqualTo(stored);
    assertThat(storedAgeOf(account)).isEqualTo(stored);
  }

  @ParameterizedTest
  @ValueSource(strings = {"one-nanosecond", "half-a-microsecond"})
  void a_window_finer_than_its_own_deadline_takes_the_week_while_the_age_beside_it_keeps_it(
      String account) {
    assertThat(storedWindowOf(account))
        .as(
            "capture_expires_at keeps a microsecond at best, so a window finer than that fixed"
                + " a deadline equal to the moment the intent was created and every hold of the"
                + " account was born past it, and StripeCaptureWindow.stored now refuses such a"
                + " row rather than reading it back as a window nothing can capture within")
        .isEqualTo(THE_VENDORS_PUBLISHED_WEEK);
    assertThat(storedAgeOf(account))
        .as(
            "the age fixes no deadline at all: StripeWebhookSigning dates the t a delivery"
                + " carries by subtracting it, so a span finer than a microsecond costs nothing"
                + " there and the statement that normalises the window leaves this column"
                + " untouched")
        .isEqualTo(everyShapeTheOldColumnsCouldHold().get(account));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "zero",
        "zero-hours",
        "over-a-year",
        "a-second-over-a-year",
        "enormous",
        "unreadable-tail",
        "letter-after-an-hour",
        "ten-fraction-digits",
        "seconds-as-a-week",
        "comma-decimal",
        "the-longest-java-reads",
        "trailing-space",
        "digit-after-the-last-unit"
      })
  void a_window_the_new_predicate_does_not_admit_takes_the_week_stripe_publishes(String account) {
    assertThat(storedWindowOf(account))
        .as(
            "a constraint cannot refuse a row that is already there, and an ALTER that aborted"
                + " would leave the simulator unable to boot, so a window the predicate does not"
                + " admit takes the seven days Stripe publishes as its own default")
        .isEqualTo(THE_VENDORS_PUBLISHED_WEEK);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "over-a-year",
        "a-second-over-a-year",
        "enormous",
        "unreadable-tail",
        "letter-after-an-hour",
        "ten-fraction-digits",
        "seconds-as-a-week",
        "comma-decimal",
        "the-longest-java-reads",
        "trailing-space",
        "digit-after-the-last-unit"
      })
  void an_age_the_new_predicate_does_not_admit_dates_a_delivery_no_further_back(String account) {
    assertThat(storedAgeOf(account))
        .as(
            "the second update statement judges a column of its own: deleting it leaves every"
                + " assertion about capture_window green while an unreadable age aborts the"
                + " ALTER and stops the simulator booting")
        .isEqualTo(NO_AGE_AT_ALL);
  }

  @ParameterizedTest
  @ValueSource(strings = {"zero", "zero-hours"})
  void an_age_of_nothing_at_all_is_kept_where_a_window_of_nothing_at_all_is_not(String account) {
    assertThat(storedAgeOf(account))
        .as(
            "webhookTimestampAge is how far in the past the t a delivery carries is dated, so"
                + " nothing at all is the value every account that signs nothing must hold, and"
                + " V30 wrote exactly PT0S as the default of the column itself")
        .isEqualTo(everyShapeTheOldColumnsCouldHold().get(account));
    assertThat(storedWindowOf(account))
        .as(
            "a capture window of nothing at all leaves every PaymentIntent of the account"
                + " expired at the instant it was created")
        .isEqualTo(THE_VENDORS_PUBLISHED_WEEK);
  }

  @Test
  void every_migrated_row_still_names_both_spans() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".stripe_account"
                    + " where capture_window is null or webhook_timestamp_age is null",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".stripe_account", Integer.class))
        .isEqualTo(everyShapeTheOldColumnsCouldHold().size());
  }

  @Test
  void every_window_the_migrated_store_holds_is_one_the_provider_reads_as_a_positive_span() {
    assertThat(jdbc.queryForList(theColumn("capture_window"), String.class))
        .isNotEmpty()
        .allSatisfy(
            stored -> {
              assertThatCode(() -> Duration.parse(stored)).doesNotThrowAnyException();
              assertThat(Duration.parse(stored)).isGreaterThan(Duration.ZERO);
              assertThatCode(() -> StripeCaptureWindow.stored(stored))
                  .as(
                      "JdbcStripeAccounts reads every one of these rows straight through"
                          + " StripeCaptureWindow.stored, which now refuses a window a hold"
                          + " cannot outlive, so a row the normalisation missed would stop the"
                          + " account loading at all")
                  .doesNotThrowAnyException();
              assertThat(Duration.parse(stored)).isGreaterThanOrEqualTo(Duration.ofNanos(1000));
            });
  }

  @Test
  void every_age_the_migrated_store_holds_is_one_the_provider_reads_as_no_future_timestamp() {
    assertThat(jdbc.queryForList(theColumn("webhook_timestamp_age"), String.class))
        .isNotEmpty()
        .allSatisfy(
            stored -> {
              assertThatCode(() -> Duration.parse(stored)).doesNotThrowAnyException();
              assertThat(Duration.parse(stored)).isGreaterThanOrEqualTo(Duration.ZERO);
            });
  }

  @Test
  void the_migrated_table_admits_every_text_the_provider_writes_a_window_as() {
    List<String> written = everyTextAStoredWindowCanBeWrittenAs();

    assertThat(written).hasSizeGreaterThan(400);
    assertThat(written)
        .as(
            "StripeAccount hands the store Duration.toString() of a span of() has already"
                + " taken, so a predicate refusing any of these would answer a control call the"
                + " provider itself accepted with a constraint violation and no reason")
        .allSatisfy(
            text -> {
              assertThatCode(() -> insert("window-takes-" + text, text, NO_AGE_AT_ALL))
                  .doesNotThrowAnyException();
              assertThat(storedWindowOf("window-takes-" + text)).isEqualTo(text);
            });
  }

  @Test
  void the_migrated_table_admits_every_text_the_provider_writes_an_age_as() {
    List<String> written = everyTextAStoredSpanCanBeWrittenAs();

    assertThat(written).hasSizeGreaterThan(400);
    assertThat(written)
        .as(
            "the window carries the floor and the age does not: a nanosecond is a window whose"
                + " deadline the store cannot keep apart from the creation moment, and an age of"
                + " a nanosecond merely dates a signed timestamp a nanosecond back")
        .allSatisfy(
            text -> {
              assertThatCode(() -> insert("age-takes-" + text, THE_VENDORS_PUBLISHED_WEEK, text))
                  .doesNotThrowAnyException();
              assertThat(storedAgeOf("age-takes-" + text)).isEqualTo(text);
            });
  }

  @ParameterizedTest
  @MethodSource("aWindowFinerThanTheDeadlineItFixes")
  void the_migrated_table_refuses_a_window_whose_deadline_is_the_moment_it_was_taken(
      String refused) {
    assertThatCode(() -> insert("window-finer-" + refused, refused, NO_AGE_AT_ALL))
        .as(
            "a window the provider refuses must not reach the column either, or the simulator"
                + " would hold a window StripeCaptureWindow.stored throws on reading back")
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> insert("age-finer-" + refused, THE_VENDORS_PUBLISHED_WEEK, refused))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @MethodSource("aWindowTheProviderCannotRead")
  void the_migrated_table_refuses_either_span_in_a_text_the_provider_throws_on_reading(
      String refused) {
    assertThatCode(() -> insert("window-unreadable-" + refused, refused, NO_AGE_AT_ALL))
        .as(
            "both columns carried ~ '^PT[0-9]' from the migration that created them, which"
                + " admitted PT9X and every other text beginning with a digit after PT, and"
                + " Duration.parse throws on each of them when JdbcStripeAccounts reads the row")
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> insert("age-unreadable-" + refused, THE_VENDORS_PUBLISHED_WEEK, refused))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @MethodSource({
    "aSpanNothingCouldBeCapturedWithin",
    "aNegativeSpan",
    "aSpanLongerThanTheYearADeadlineStaysInside"
  })
  void the_migrated_table_refuses_a_window_an_authorization_could_not_be_captured_within(
      String refused) {
    assertThatThrownBy(() -> insert("window-refuses-" + refused, refused, NO_AGE_AT_ALL))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @MethodSource({"aNegativeSpan", "aSpanLongerThanTheYearADeadlineStaysInside"})
  void the_migrated_table_refuses_an_age_no_account_of_this_simulator_emits(String refused) {
    assertThatCode(() -> insert("age-refuses-" + refused, THE_VENDORS_PUBLISHED_WEEK, refused))
        .as(
            "a negative age would date a delivery ahead of the run's own clock, and an age"
                + " beyond a year is one the store is not asked to keep")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @MethodSource("aSpanNoWriterOfThisSimulatorWrites")
  void the_migrated_table_refuses_either_span_in_a_text_no_writer_of_this_simulator_produces(
      String refused) {
    assertThatCode(() -> insert("window-unwritten-" + refused, refused, NO_AGE_AT_ALL))
        .as(
            "Duration.parse reads every one of these and of() would take it as a setting, but"
                + " JdbcStripeAccounts writes Duration.toString(), which is canonical and names"
                + " no day, no week, no sign and no comma, so refusing them costs no caller an"
                + " answer the provider had already given")
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> insert("age-unwritten-" + refused, THE_VENDORS_PUBLISHED_WEEK, refused))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_age_column_takes_the_nothing_at_all_the_window_column_refuses() {
    assertThatCode(() -> insert("age-of-nothing", THE_VENDORS_PUBLISHED_WEEK, NO_AGE_AT_ALL))
        .as(
            "the two columns carried one predicate and have two lower bounds: an account with"
                + " no signing secret at all must hold exactly PT0S here, which V30 enforces,"
                + " and a capture window of PT0S is a window nothing can be captured within")
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> insert("window-of-nothing", NO_AGE_AT_ALL, NO_AGE_AT_ALL))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> insert("age-of-no-days", THE_VENDORS_PUBLISHED_WEEK, "P0D"))
        .as(
            "nothing at all is an age the provider takes, and P0D is one of the texts"
                + " Duration.parse reads it as, but storedAge writes Duration.toString(), which"
                + " writes it as PT0S and never as a count of days")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static List<String> aWindowTheProviderCannotRead() {
    return List.of(
        "PT9X",
        "PT1Hx",
        "PT0.1234567890S",
        "PT12H ",
        "PT1H2M3S4",
        "PT",
        "P1W",
        "P7DT",
        "5",
        "soon",
        "   ",
        "");
  }

  private static List<String> aSpanNothingCouldBeCapturedWithin() {
    return List.of(NO_AGE_AT_ALL, "PT00H", "P0D");
  }

  private static List<String> aNegativeSpan() {
    return List.of("PT-120H", "-P5D");
  }

  private static List<String> aSpanLongerThanTheYearADeadlineStaysInside() {
    return List.of("PT8761H", "PT8760H1S", "PT876000H", "PT2562047788015215H30M7S");
  }

  private static List<String> aSpanNoWriterOfThisSimulatorWrites() {
    return List.of("P7D", "P365D", "PT604800S", "PT0,5S", "+PT1H", "PT+1H", "pt1h");
  }

  private static List<String> aWindowFinerThanTheDeadlineItFixes() {
    return List.of(
        "PT0.000000001S",
        "PT0.0000005S",
        "PT0.000000999S",
        "PT0H0M0.0000001S",
        "PT00H00M00.000000999S");
  }

  private static List<String> everyTextAStoredWindowCanBeWrittenAs() {
    List<String> written = new ArrayList<>();
    for (String text : everyTextAStoredSpanCanBeWrittenAs()) {
      if (Duration.parse(text).compareTo(Duration.ofNanos(1000)) >= 0) {
        written.add(text);
      }
    }
    return List.copyOf(written);
  }

  private static List<String> everyTextAStoredSpanCanBeWrittenAs() {
    Set<Duration> spans = new LinkedHashSet<>();
    spans.add(Duration.ofNanos(1));
    spans.add(Duration.ofNanos(999));
    spans.add(Duration.ofNanos(1000));
    spans.add(Duration.ofNanos(1500));
    spans.add(Duration.ofMillis(1));
    spans.add(Duration.ofMillis(500));
    spans.add(Duration.ofSeconds(1));
    spans.add(Duration.ofSeconds(59).plusNanos(999999999));
    spans.add(Duration.ofMinutes(1));
    spans.add(Duration.ofMinutes(59).plusSeconds(59).plusNanos(999999999));
    spans.add(Duration.ofHours(1));
    spans.add(Duration.ofHours(1).plusNanos(1));
    spans.add(LONGEST_THE_PROVIDER_TAKES);
    spans.add(LONGEST_THE_PROVIDER_TAKES.minusNanos(1));
    spans.add(LONGEST_THE_PROVIDER_TAKES.minusSeconds(1));
    for (long days = 1; days <= 365; days++) {
      spans.add(Duration.ofDays(days));
    }
    for (long hours = 1; hours <= 60; hours++) {
      spans.add(Duration.ofHours(hours).plusMinutes(hours).plusSeconds(hours).plusNanos(hours * 7));
    }
    for (int digits = 1; digits <= 9; digits++) {
      spans.add(Duration.ofSeconds(1).plusNanos((long) Math.pow(10, digits - 1)));
    }
    List<String> written = new ArrayList<>();
    for (Duration span : spans) {
      if (span.isZero() || span.isNegative() || span.compareTo(LONGEST_THE_PROVIDER_TAKES) > 0) {
        throw new IllegalStateException("the sweep built a span no column here holds: " + span);
      }
      written.add(span.toString());
    }
    return List.copyOf(written);
  }

  private String theColumn(String column) {
    return "select " + column + " from " + SCHEMA + ".stripe_account";
  }

  private String storedWindowOf(String account) {
    return storedColumnOf("capture_window", account);
  }

  private String storedAgeOf(String account) {
    return storedColumnOf("webhook_timestamp_age", account);
  }

  private String storedColumnOf(String column, String account) {
    return jdbc.queryForObject(
        "select " + column + " from " + SCHEMA + ".stripe_account where account_id = ?",
        String.class,
        account);
  }

  private void insert(String accountId, String window, String age) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
            + " webhook_secret, webhook_signature, webhook_timestamp_age, refund_status)"
            + " values (?, ?, ?, ?, 'whsec_theEndpointSecret', 'VALID', ?, 'SUCCEEDED')",
        RUN,
        accountId,
        "sk_test_" + accountId,
        window,
        age);
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
