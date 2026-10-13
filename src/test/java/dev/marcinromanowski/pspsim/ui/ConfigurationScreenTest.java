package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.ClockChange;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRouteAlias;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ConfigurationScreenTest {

  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

  private static final String RUN = "configuration-screen-test";
  private static final ProviderId GROW = ProviderId.of("grow");
  private static final TestRunId THE_RUN = TestRunId.of(RUN);
  private static final String TERMINAL = "az-terminal-1";

  private static final ProviderAccountFields DECLARED =
      new ProviderAccountFields(
          GROW,
          List.of(
              new ConfigurableField(
                  "userId",
                  "user id",
                  "a sentence",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "pageCode",
                  "page code",
                  "a sentence",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "captureWindow",
                  "how long a hold lasts",
                  "a sentence",
                  false,
                  false,
                  List.of(),
                  "a value a test does not read"),
              ConfigurableField.aDuration(
                  "automaticVoidAfter", "when a hold voids", "a sentence", false, "P10D"),
              new ConfigurableField(
                  "wireForm",
                  "which wire form answers carry",
                  "a sentence",
                  false,
                  false,
                  List.of(
                      new AcceptedValue("LIVE_WIRE", "answer text/html, as the live wire does"),
                      new AcceptedValue(
                          "PUBLISHED_EXAMPLE", "answer application/json, as the example does")))));

  private static final ConfigurationScreen.Deployment DEPLOYMENT =
      ConfigurationScreen.deploymentOf(
          "https://simulator.internal",
          "default",
          new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(5))),
          GROW,
          THE_RUN);

  @Test
  void only_the_account_a_reader_opened_puts_its_configured_properties_on_the_page() {
    ConfigurationScreen.View view = view("m1", growLike(), "m2", stripeLike());

    assertThat(propertiesOf(view, "m1")).isNotEmpty();
    assertThat(propertiesOf(view, "m2")).isEmpty();
  }

  @Test
  void the_screen_prints_the_property_names_the_provider_answered_and_adds_none_of_its_own() {
    ConfigurationScreen.View grow = view("m1", growLike(), "m2", stripeLike());
    ConfigurationScreen.View azericard = view("m1", azericardLike(), "m2", stripeLike());

    assertThat(propertiesOf(grow, "m1")).containsExactly("userId", "pageCode", "captureWindow");
    assertThat(propertiesOf(azericard, "m1")).containsExactly("terminalId", "apiKey", "apiSecret");
  }

  @Test
  void
      a_credential_the_provider_answers_with_a_marker_is_named_without_its_value_even_when_asked() {
    ConfigurationScreen.View asked = opened("m1", azericardLike(), true);

    ConfigurationScreen.Field secret = fieldOf(asked, "m1", "apiSecret");
    assertThat(secret.visibility()).isEqualTo(Disclosure.Visibility.REDACTED_ON_WRITE);
    assertThat(secret.values()).isEmpty();
    assertThat(secret.occurrences()).isEqualTo(1);
    assertThat(valuesOn(asked)).doesNotContain(Redaction.REDACTED);
  }

  @Test
  void a_configured_value_the_provider_did_hand_over_stays_off_the_page_until_it_is_asked_for() {
    ConfigurationScreen.View unasked = opened("m1", azericardLike(), false);
    ConfigurationScreen.View asked = opened("m1", azericardLike(), true);

    assertThat(fieldOf(unasked, "m1", "terminalId").visibility())
        .isEqualTo(Disclosure.Visibility.WITHHELD);
    assertThat(valuesOn(unasked)).doesNotContain(TERMINAL);
    assertThat(valuesOn(asked)).contains(TERMINAL);
    assertThat(unasked.valuesShown()).isFalse();
    assertThat(asked.valuesShown()).isTrue();
  }

  @Test
  void a_property_this_account_configured_nothing_for_is_named_with_nothing_configured() {
    ConfigurationScreen.View view = opened("m1", stripeLike(), true);

    ConfigurationScreen.Field absent = fieldOf(view, "m1", "webhookSecret");
    assertThat(absent.occurrences()).isZero();
    assertThat(absent.values()).isEmpty();
  }

  @Test
  void a_value_the_provider_declared_reads_back_under_the_sentence_it_is_offered_under() {
    ConfigurationScreen.View asked = opened("m1", chosenGrowLike("PUBLISHED_EXAMPLE"), true);

    assertThat(fieldOf(asked, "m1", "wireForm").values())
        .as(
            "the form offers this value as a sentence and the screen reporting it printed the"
                + " wire word alone, so a tester could pick correctly and not read back what"
                + " they had picked")
        .containsExactly(
            new ConfigurationScreen.Value(
                "PUBLISHED_EXAMPLE", "answer application/json, as the example does"));
  }

  @Test
  void a_value_no_option_carries_still_prints_rather_than_disappearing() {
    ConfigurationScreen.View asked = opened("m1", chosenGrowLike("A_FORM_RETIRED_SINCE"), true);

    assertThat(fieldOf(asked, "m1", "wireForm").values())
        .as(
            "a value stored before this provider published its list, or set by a call that no"
                + " option carries, has no sentence and must still be readable")
        .containsExactly(new ConfigurationScreen.Value("A_FORM_RETIRED_SINCE", null));
  }

  @Test
  void a_property_this_provider_declares_no_field_for_prints_with_no_sentence_beside_it() {
    ConfigurationScreen.View asked = opened("m1", azericardLike(), true);

    assertThat(fieldOf(asked, "m1", "terminalId").values())
        .as("the provider answered a property these declared fields do not name")
        .containsExactly(new ConfigurationScreen.Value(TERMINAL, null));
  }

  @Test
  void a_value_of_a_field_that_takes_a_shape_rather_than_a_list_prints_with_no_sentence() {
    ConfigurationScreen.View asked = opened("m1", growLike(), true);

    assertThat(fieldOf(asked, "m1", "captureWindow").values())
        .containsExactly(new ConfigurationScreen.Value("P7D", null));
  }

  @Test
  void an_identity_the_provider_holds_no_configuration_for_is_listed_rather_than_dropped() {
    ConfigurationScreen.View view =
        ConfigurationScreen.of(
            "grow",
            "run-1",
            List.of("m1"),
            DECLARED,
            accountId -> Optional.empty(),
            "m1",
            true,
            List.of(),
            List.of(),
            null,
            NOW,
            DEPLOYMENT);

    ConfigurationScreen.Account account = accountOf(view, "m1");
    assertThat(account.heldByTheProvider()).isFalse();
    assertThat(account.open()).isTrue();
    assertThat(account.configuration()).isEmpty();
  }

  @Test
  void a_callback_target_is_shown_against_the_account_identity_it_belongs_to() {
    ConfigurationScreen.View view =
        ConfigurationScreen.of(
            "grow",
            "run-1",
            List.of("m1"),
            DECLARED,
            growLike(),
            null,
            false,
            List.of(new WebhookTarget("m1", "https://merchant.test/callbacks")),
            List.of(),
            null,
            NOW,
            DEPLOYMENT);

    assertThat(view.webhookTargets())
        .containsExactly(new ConfigurationScreen.Target("m1", "https://merchant.test/callbacks"));
  }

  @Test
  void a_test_run_reading_the_host_clock_says_so_rather_than_naming_a_pinned_time() {
    ConfigurationScreen.View onTheHost = view("m1", growLike(), "m2", stripeLike());
    ConfigurationScreen.View pinned =
        ConfigurationScreen.of(
            "grow",
            "run-1",
            List.of("m1"),
            DECLARED,
            growLike(),
            null,
            false,
            List.of(),
            List.of(),
            NOW,
            NOW,
            DEPLOYMENT);

    assertThat(onTheHost.clock().pinnedTo()).isNull();
    assertThat(onTheHost.clock().now()).isEqualTo(NOW.toString());
    assertThat(pinned.clock().pinnedTo()).isEqualTo(NOW.toString());
  }

  @Test
  void the_callback_attempt_plan_names_one_delay_per_attempt_and_counts_its_own_length() {
    ConfigurationScreen.Deployment deployment =
        ConfigurationScreen.deploymentOf(
            "https://simulator.internal",
            "default",
            new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5))),
            GROW,
            THE_RUN);

    assertThat(deployment.webhookRetryDelays())
        .as(
            "this row is read by a tester and by nobody else, so it names the delays in the words"
                + " a person would say them in rather than in the form the plan is written in")
        .containsExactly("0 seconds", "1 second", "5 seconds");
    assertThat(deployment.webhookAttemptCap()).isEqualTo(3);
    assertThat(deployment.publicBaseUrl()).isEqualTo("https://simulator.internal");
    assertThat(deployment.defaultTestRunId()).isEqualTo("default");
    assertThat(deployment.testRunHeader()).isEqualTo(TestRunHeader.NAME);
  }

  @Test
  void a_delay_no_whole_number_of_any_unit_counts_is_named_as_the_plan_itself_writes_it() {
    ConfigurationScreen.Deployment deployment =
        ConfigurationScreen.deploymentOf(
            "https://simulator.internal",
            "default",
            new RetryPlan(List.of(Duration.ZERO, Duration.ofMillis(1500))),
            GROW,
            THE_RUN);

    assertThat(deployment.webhookRetryDelays())
        .as(
            "a plan a control call set to a fraction of a second has a delay no number of whole"
                + " seconds names, and the row prints what the plan holds rather than a rounded"
                + " reading of it")
        .containsExactly("0 seconds", "PT1.5S");
  }

  @Test
  void a_span_of_time_reads_back_in_the_words_a_tester_would_state_it_in() {
    ConfigurationScreen.View asked = opened("m1", aHoldThatVoidsAfter("PT240H"), true);

    assertThat(fieldOf(asked, "m1", "automaticVoidAfter").values())
        .as(
            "the column holds the span normalised into hours, and the screen leads with the"
                + " words a tester would state it in and keeps the stored value beside them,"
                + " that being this screen's only bridge to the control API")
        .containsExactly(new ConfigurationScreen.Value("PT240H", "10 days"));
  }

  @Test
  void the_clock_form_states_an_advance_as_a_number_and_a_unit_and_opens_on_an_hour() {
    ConfigurationScreen.Clock clock = opened("m1", growLike(), true).clock();

    assertThat(clock.theNumberAnAdvanceHints())
        .as("the box hinted PT1H and a tester had to know what that was")
        .isEqualTo("1");
    assertThat(clock.units())
        .extracting(ADurationInWords.Offered::whatItMeans)
        .containsExactly("seconds", "minutes", "hours", "days");
    assertThat(clock.units())
        .filteredOn(ADurationInWords.Offered::chosen)
        .extracting(ADurationInWords.Offered::value)
        .containsExactly("HOURS");
  }

  @Test
  void the_parameter_a_picked_unit_travels_under_is_the_one_every_form_of_this_ui_sends_it_under() {
    assertThat(ConfigurationScreen.THE_UNIT_AN_ADVANCE_IS_STATED_IN).isEqualTo("unitOf.advanceBy");
  }

  @Test
  void the_span_a_clock_form_hints_is_one_hour_whichever_screen_asks_for_it() {
    assertThat(ConfigurationScreen.anAdvanceAFormHints())
        .as(
            "the hint is read here directly, because a value kept in a static final is built"
                + " once when the class is first touched and PIT cannot re-run that")
        .isEqualTo(new ADurationInWords(1, ADurationInWords.Unit.HOURS));
  }

  @Test
  void a_number_and_a_unit_a_tester_states_an_advance_as_name_the_span_the_clock_moves_by() {
    assertThat(ConfigurationScreen.theSpanAnAdvanceNames("90", "MINUTES")).isEqualTo("PT1H30M");
    assertThat(ConfigurationScreen.theSpanAnAdvanceNames("PT1H", null))
        .as("a call that writes the ISO form itself is handed on as it wrote it")
        .isEqualTo("PT1H");
    assertThat(ConfigurationScreen.theSpanAnAdvanceNames("", "HOURS"))
        .as("an empty box names no span, and the clock refuses it by name")
        .isEmpty();
    assertThat(ConfigurationScreen.theSpanAnAdvanceNames(null, null))
        .as("a post that names no advance at all is not a post that emptied the box")
        .isNull();
  }

  @Test
  void a_span_no_whole_number_of_any_unit_counts_reads_back_as_the_value_the_provider_holds() {
    ConfigurationScreen.View asked = opened("m1", aHoldThatVoidsAfter("PT0.5S"), true);

    assertThat(fieldOf(asked, "m1", "automaticVoidAfter").values())
        .containsExactly(new ConfigurationScreen.Value("PT0.5S", null));
  }

  @Test
  void an_instant_typed_into_the_form_pins_the_test_run_to_it() {
    assertThat(theClockAsked("  " + NOW + "  ")).isEqualTo(new ClockChange.PinnedTo(NOW));
  }

  @Test
  void an_empty_form_field_releases_the_test_run_back_to_the_host_clock() {
    assertThat(theClockAsked("   ")).isEqualTo(new ClockChange.ReleasedToHostClock());
    assertThat(theClockAsked(null)).isEqualTo(new ClockChange.ReleasedToHostClock());
  }

  @Test
  void the_clock_form_names_its_test_run_and_no_account_at_all() {
    DeclaredConfiguration asked = ConfigurationScreen.clockChangeOf(RUN, NOW.toString());

    assertThat(asked.testRunId()).isEqualTo(RUN);
    assertThat(asked.accounts()).isNull();
    assertThat(asked.webhooks()).isNull();
  }

  @Test
  void a_form_field_that_is_not_an_instant_is_refused_by_naming_the_property_and_not_the_value() {
    assertThatThrownBy(() -> theClockAsked("yesterday"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fixedNow")
        .hasMessageNotContaining("yesterday");
  }

  @Test
  void the_urls_a_test_run_answers_on_are_listed_beside_the_routes_that_answer_them() {
    ConfigurationScreen.View view =
        ConfigurationScreen.of(
            "grow",
            "run-1",
            List.of("m1"),
            DECLARED,
            growLike(),
            null,
            false,
            List.of(),
            List.of(
                new RouteAlias(
                    ProviderId.of("grow"),
                    TestRunId.of("run-1"),
                    new RouteAliasKey("api.provider.example", "/v2/merchant-1/payments"),
                    "https://api.provider.example/v2/merchant-1/payments",
                    "/api/light/server/1.0/createPaymentProcess")),
            null,
            NOW,
            DEPLOYMENT);

    assertThat(view.routeAliases())
        .containsExactly(
            new ConfigurationScreen.Alias(
                "https://api.provider.example/v2/merchant-1/payments",
                "/api/light/server/1.0/createPaymentProcess"));
  }

  @Test
  void the_route_alias_form_names_its_test_run_and_touches_no_account_and_no_clock() {
    DeclaredConfiguration asked =
        ConfigurationScreen.routeAliasOf(
            RUN, "https://api.provider.example/pay", "/api/light/server/1.0/refundTransaction");

    assertThat(asked.testRunId()).isEqualTo(RUN);
    assertThat(asked.clock()).isNull();
    assertThat(asked.accounts()).isNull();
    assertThat(asked.webhooks()).isNull();
    assertThat(asked.routeAliases())
        .containsExactly(
            new DeclaredRouteAlias(
                "https://api.provider.example/pay", "/api/light/server/1.0/refundTransaction"));
  }

  @Test
  void a_route_alias_form_submitted_empty_is_handed_on_as_the_empty_sides_it_was_given() {
    DeclaredConfiguration asked = ConfigurationScreen.routeAliasOf(RUN, null, null);

    assertThat(asked.routeAliases()).containsExactly(new DeclaredRouteAlias(null, null));
  }

  private static ClockChange theClockAsked(@Nullable String fixedNow) {
    return ClockChange.of(ConfigurationScreen.clockChangeOf(RUN, fixedNow).clock());
  }

  private static ConfigurationScreen.View view(
      String opened,
      Function<String, Optional<DisclosedAccount>> first,
      String other,
      Function<String, Optional<DisclosedAccount>> second) {
    return ConfigurationScreen.of(
        "grow",
        "run-1",
        List.of(opened, other),
        DECLARED,
        accountId -> accountId.equals(opened) ? first.apply(accountId) : second.apply(accountId),
        opened,
        true,
        List.of(),
        List.of(),
        null,
        NOW,
        DEPLOYMENT);
  }

  private static ConfigurationScreen.View opened(
      String accountId,
      Function<String, Optional<DisclosedAccount>> disclosure,
      boolean valuesAsked) {
    return ConfigurationScreen.of(
        "grow",
        "run-1",
        List.of(accountId),
        DECLARED,
        disclosure,
        accountId,
        valuesAsked,
        List.of(),
        List.of(),
        null,
        NOW,
        DEPLOYMENT);
  }

  private static Function<String, Optional<DisclosedAccount>> growLike() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put("userId", List.of(Redaction.REDACTED));
    disclosed.put("pageCode", List.of(Redaction.REDACTED));
    disclosed.put("captureWindow", List.of("P7D"));
    return accountId -> Optional.of(new DisclosedAccount(accountId, disclosed));
  }

  private static Function<String, Optional<DisclosedAccount>> aHoldThatVoidsAfter(String stored) {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put("automaticVoidAfter", List.of(stored));
    return accountId -> Optional.of(new DisclosedAccount(accountId, disclosed));
  }

  private static Function<String, Optional<DisclosedAccount>> chosenGrowLike(String wireForm) {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put("userId", List.of(Redaction.REDACTED));
    disclosed.put("wireForm", List.of(wireForm));
    return accountId -> Optional.of(new DisclosedAccount(accountId, disclosed));
  }

  private static Function<String, Optional<DisclosedAccount>> azericardLike() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put("terminalId", List.of(TERMINAL));
    disclosed.put("apiKey", List.of("-----BEGIN PUBLIC KEY-----"));
    disclosed.put("apiSecret", List.of(Redaction.REDACTED));
    return accountId -> Optional.of(new DisclosedAccount(accountId, disclosed));
  }

  private static Function<String, Optional<DisclosedAccount>> stripeLike() {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    disclosed.put("secretKey", List.of(Redaction.REDACTED));
    disclosed.put("webhookSecret", List.of());
    return accountId -> Optional.of(new DisclosedAccount(accountId, disclosed));
  }

  private static List<String> propertiesOf(ConfigurationScreen.View view, String accountId) {
    return accountOf(view, accountId).configuration().stream()
        .map(ConfigurationScreen.Field::name)
        .toList();
  }

  private static ConfigurationScreen.Field fieldOf(
      ConfigurationScreen.View view, String accountId, String property) {
    return accountOf(view, accountId).configuration().stream()
        .filter(field -> field.name().equals(property))
        .findFirst()
        .orElseThrow();
  }

  private static ConfigurationScreen.Account accountOf(
      ConfigurationScreen.View view, String accountId) {
    return view.accounts().stream()
        .filter(account -> account.accountId().equals(accountId))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> valuesOn(ConfigurationScreen.View view) {
    return view.accounts().stream()
        .flatMap(account -> account.configuration().stream())
        .flatMap(field -> field.values().stream())
        .map(ConfigurationScreen.Value::value)
        .toList();
  }
}
