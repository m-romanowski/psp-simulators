package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.AKindOfObjectATestNames;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.PlacedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class WorkbenchScreenTest {

  private static final Instant AT = Instant.parse("2026-09-07T12:00:00Z");
  private static final TestRunId RUN = TestRunId.of("ui-workbench");
  private static final ProviderId GROW = ProviderId.of("grow");

  private static final String CHARGE = "charge";
  private static final String LOOK_UP = "lookUp";
  private static final String REFUND = "refund";

  private static final String PLAIN_FIELD = "wireForm";
  private static final String SECRET_FIELD = "userId";
  private static final String UNDECLARED_FIELD = "somethingNoProviderDeclares";

  private static final String THE_SECRET = "grow-user-must-not-be-read-off-a-monitor";
  private static final String THE_UNDECLARED_VALUE = "undeclared-must-not-be-read-off-a-monitor";

  private static final String ACCOUNT = "merchant-1";
  private static final String BASE_URL = "https://simulator.example";
  private static final String API_BASE_PATH = "/api/light/server/1.0";

  private static final DeliveryScreen.Queue EMPTY_QUEUE =
      DeliveryScreen.queueOf(
          List.of(),
          AT,
          null,
          false,
          deliveryId -> List.of(),
          List.of(),
          DeliveryForm.noneOffered());

  private static final HostedPages.Split THE_FIVE_THIS_BUILD_SERVES =
      new HostedPages.Split(
          List.of("autra", "grow", "worldline"),
          List.of("azericard", "stripe"),
          4,
          "autra a card session and a Pix; grow a payment process;"
              + " worldline a hosted tokenization session");

  @Test
  void
      the_value_of_a_field_the_provider_declares_secret_is_not_among_the_values_this_screen_shows() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of(SECRET_FIELD, THE_SECRET)));

    assertThat(theValuesShownOn(view)).doesNotContain(THE_SECRET);
  }

  @Test
  void a_field_the_provider_declares_secret_is_named_and_carries_no_value_of_its_own() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of(SECRET_FIELD, THE_SECRET)));

    Disclosure.Field field = theFieldNamed(SECRET_FIELD, view);
    assertThat(field.visibility()).isNotEqualTo(Disclosure.Visibility.SHOWN);
    assertThat(field.values()).isEmpty();
  }

  @Test
  void a_field_the_provider_declares_shown_in_plain_carries_the_value_it_holds() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of(PLAIN_FIELD, "JSON")));

    Disclosure.Field field = theFieldNamed(PLAIN_FIELD, view);
    assertThat(field.visibility()).isEqualTo(Disclosure.Visibility.SHOWN);
    assertThat(field.values()).containsExactly("JSON");
  }

  @Test
  void the_value_of_a_field_this_provider_declares_nowhere_is_not_among_the_values_it_shows() {
    WorkbenchScreen.View view =
        viewOf(anAccountHolding(Map.of(UNDECLARED_FIELD, THE_UNDECLARED_VALUE)));

    assertThat(theFieldNamed(UNDECLARED_FIELD, view).visibility())
        .isNotEqualTo(Disclosure.Visibility.SHOWN);
    assertThat(theValuesShownOn(view)).doesNotContain(THE_UNDECLARED_VALUE);
  }

  @Test
  void an_account_this_run_names_and_the_provider_holds_nothing_under_is_listed_with_no_field() {
    WorkbenchScreen.View view = viewOf(accountId -> Optional.empty());

    WorkbenchScreen.ConfiguredAccount account = view.accounts().get(0);
    assertThat(account.accountId()).isEqualTo(ACCOUNT);
    assertThat(account.heldByTheProvider()).isFalse();
    assertThat(account.configuration()).isEmpty();
  }

  @Test
  void the_provider_base_url_is_the_public_base_url_joined_to_the_path_the_provider_publishes_on() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of()));

    assertThat(view.addresses().publicBaseUrl()).isEqualTo(BASE_URL);
    assertThat(view.addresses().providerBaseUrl()).isEqualTo(BASE_URL + API_BASE_PATH);
    assertThat(view.addresses().testRunHeader()).isEqualTo(TestRunHeader.NAME);
  }

  @Test
  void a_route_alias_of_this_run_is_listed_as_the_url_called_and_the_route_it_reaches() {
    WorkbenchScreen.Addresses addresses =
        WorkbenchScreen.addressesOf(
            BASE_URL,
            pairing(),
            List.of(
                new RouteAlias(
                    GROW,
                    RUN,
                    new RouteAliasKey("grow.example", "/pay"),
                    "https://grow.example/pay",
                    API_BASE_PATH + "/createPaymentProcess")));

    assertThat(addresses.routeAliases())
        .containsExactly(
            new PairingScreen.Alias(
                "https://grow.example/pay", API_BASE_PATH + "/createPaymentProcess"));
  }

  @Test
  void every_operation_the_provider_exposes_is_listed_in_the_order_the_vocabulary_publishes_them() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of()));

    assertThat(view.operations().stream().map(WorkbenchScreen.Operation::name).toList())
        .containsExactly(CHARGE, LOOK_UP, REFUND);
  }

  @Test
  void an_operation_no_rule_of_this_run_names_is_counted_as_named_by_none() {
    List<WorkbenchScreen.Operation> operations =
        WorkbenchScreen.operationsOf(vocabulary(), List.of(aRuleNaming(CHARGE, "scn_1")));

    WorkbenchScreen.Operation refund = theOperationNamed(REFUND, operations);
    assertThat(refund.rulesOfThisRunNamingIt()).isZero();
    assertThat(refund.anyRuleOfThisRunNamesIt()).isFalse();
  }

  @Test
  void an_operation_two_rules_of_this_run_name_is_counted_as_named_by_both() {
    List<WorkbenchScreen.Operation> operations =
        WorkbenchScreen.operationsOf(
            vocabulary(), List.of(aRuleNaming(CHARGE, "scn_1"), aRuleNaming(CHARGE, "scn_2")));

    WorkbenchScreen.Operation charge = theOperationNamed(CHARGE, operations);
    assertThat(charge.rulesOfThisRunNamingIt()).isEqualTo(2);
    assertThat(charge.anyRuleOfThisRunNamesIt()).isTrue();
  }

  @Test
  void a_rule_naming_no_operation_is_counted_apart_and_against_no_operation_of_its_own() {
    List<ScenarioRule> rules = List.of(aRuleNaming(null, "scn_1"));

    assertThat(WorkbenchScreen.theRulesNamingNoOperation(rules)).isEqualTo(1);
    for (WorkbenchScreen.Operation operation : WorkbenchScreen.operationsOf(vocabulary(), rules)) {
      assertThat(operation.rulesOfThisRunNamingIt()).isZero();
    }
  }

  @Test
  void a_rule_naming_an_operation_is_not_counted_among_the_rules_that_name_none() {
    assertThat(WorkbenchScreen.theRulesNamingNoOperation(List.of(aRuleNaming(CHARGE, "scn_1"))))
        .isZero();
  }

  @Test
  void
      only_the_operation_the_provider_answers_with_a_processing_state_is_marked_as_reporting_one() {
    List<WorkbenchScreen.Operation> operations =
        WorkbenchScreen.operationsOf(vocabulary(), List.of());

    assertThat(theOperationNamed(CHARGE, operations).reportsAProcessingState()).isTrue();
    assertThat(theOperationNamed(REFUND, operations).reportsAProcessingState()).isFalse();
  }

  @Test
  void only_the_operation_whose_flow_key_the_object_may_replace_is_marked_as_replaceable() {
    List<WorkbenchScreen.Operation> operations =
        WorkbenchScreen.operationsOf(vocabulary(), List.of());

    assertThat(theOperationNamed(LOOK_UP, operations).itsFlowKeyMayBeReplaced()).isTrue();
    assertThat(theOperationNamed(CHARGE, operations).itsFlowKeyMayBeReplaced()).isFalse();
  }

  @Test
  void the_facts_an_operation_carries_are_named_as_the_properties_a_rule_writes_them_under() {
    List<WorkbenchScreen.Operation> operations =
        WorkbenchScreen.operationsOf(vocabulary(), List.of());

    assertThat(theOperationNamed(CHARGE, operations).carries())
        .containsExactly(MatchFact.MERCHANT_ID.property(), MatchFact.AMOUNT.property());
  }

  @Test
  void current_means_the_newest_objects_this_workbench_shows_and_no_object_older_than_those() {
    WorkbenchScreen.CurrentObjects current =
        WorkbenchScreen.theNewestObjectsOf(
            aPageOf(WorkbenchScreen.THE_NEWEST_OBJECTS_IT_SHOWS), AT);

    assertThat(current.newest()).hasSize(WorkbenchScreen.THE_NEWEST_OBJECTS_IT_SHOWS);
    assertThat(current.newest().stream().map(ObjectScreen.Row::providerObjectId).toList())
        .containsExactly("trx_5", "trx_4", "trx_3", "trx_2", "trx_1");
  }

  @Test
  void a_page_as_long_as_the_limit_it_asks_for_shows_older_objects_exist_and_lists_none_of_them() {
    WorkbenchScreen.CurrentObjects current =
        WorkbenchScreen.theNewestObjectsOf(
            aPageOf(WorkbenchScreen.theLimitItAsksTheObjectReadFor()), AT);

    assertThat(current.moreThanTheseExist()).isTrue();
    assertThat(current.newest()).hasSize(WorkbenchScreen.THE_NEWEST_OBJECTS_IT_SHOWS);
  }

  @Test
  void a_page_exactly_as_long_as_it_shows_claims_no_object_older_than_the_ones_it_lists() {
    WorkbenchScreen.CurrentObjects current =
        WorkbenchScreen.theNewestObjectsOf(
            aPageOf(WorkbenchScreen.THE_NEWEST_OBJECTS_IT_SHOWS), AT);

    assertThat(current.moreThanTheseExist()).isFalse();
  }

  @Test
  void a_run_holding_no_object_lists_none_and_names_no_reason_it_could_not_read_them() {
    WorkbenchScreen.CurrentObjects current = WorkbenchScreen.theNewestObjectsOf(aPageOf(0), AT);

    assertThat(current.newest()).isEmpty();
    assertThat(current.moreThanTheseExist()).isFalse();
    assertThat(current.whyItListsNone()).isNull();
  }

  @Test
  void a_read_that_refuses_the_cursor_says_the_run_may_hold_objects_this_panel_does_not_list() {
    WorkbenchScreen.CurrentObjects current =
        WorkbenchScreen.theNewestObjectsOf(new ProviderObjectPage.NoSuchCursor(), AT);

    assertThat(current.newest()).isEmpty();
    assertThat(current.whyItListsNone())
        .isEqualTo(WorkbenchScreen.THE_OBJECT_READ_REFUSED_THE_CURSOR)
        .contains("This screen sends no cursor at all")
        .contains("read them on the Payments screen rather than as none")
        .doesNotContain("on Objects");
  }

  @Test
  void the_limit_it_asks_the_object_read_for_leaves_room_for_one_object_it_does_not_list() {
    assertThat(WorkbenchScreen.theLimitItAsksTheObjectReadFor())
        .isGreaterThan(WorkbenchScreen.THE_NEWEST_OBJECTS_IT_SHOWS);
  }

  @Test
  void the_hosted_session_sentence_says_those_objects_are_among_the_ones_this_panel_lists() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of()));

    assertThat(view.whereTheHostedSessionsAre())
        .contains("is an ordinary object of this test run")
        .contains("The providers here keep 4 objects a payer finishes")
        .contains(
            "autra a card session and a Pix; grow a payment process; worldline a hosted"
                + " tokenization session")
        .contains("the payer's own step; the Payments screen does both")
        .doesNotContain("Objects does both")
        .doesNotContain("does not read that list")
        .doesNotContain("not built rather than unbuildable");
  }

  @Test
  void the_absent_payment_method_panel_says_the_fixtures_exist_and_which_screen_lists_them() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of()));

    assertThat(view.whyItListsNoPaymentMethod())
        .contains("A payment-method fixture is an ordinary object of this test run")
        .contains(
            "the Payment methods screen lists what this provider holds, seeds one and"
                + " removes one")
        .contains("a persona seeds one in one press")
        .contains("not built rather than unbuildable")
        .doesNotContain("No payment-method fixture exists");
  }

  @Test
  void the_five_sentences_this_screen_prints_about_itself_name_it_a_screen_and_never_a_workbench() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of()));

    assertThat(view.whereTheHostedSessionsAre())
        .contains("What this screen does not do is narrow that list")
        .doesNotContainIgnoringCase("workbench");
    assertThat(view.whyItListsNoPaymentMethod())
        .contains("This screen does not read them")
        .doesNotContainIgnoringCase("workbench");
    assertThat(WorkbenchScreen.THE_OBJECT_READ_REFUSED_THE_CURSOR)
        .contains("This screen sends no cursor at all")
        .doesNotContainIgnoringCase("workbench");
    assertThat(WorkbenchScreen.A_SECRET_IS_NEVER_A_SUMMARY)
        .contains("this screen has no way to ask for one")
        .doesNotContainIgnoringCase("workbench");
    assertThat(WorkbenchScreen.NO_CALL_RECORDS_THE_OPERATION_IT_ASKED_FOR)
        .doesNotContainIgnoringCase("workbench");
  }

  @Test
  void the_note_on_an_absent_exercised_tick_says_what_a_recorded_call_names_instead() {
    assertThat(WorkbenchScreen.NO_CALL_RECORDS_THE_OPERATION_IT_ASKED_FOR)
        .contains("the method and the path a call arrived on and never the operation it asked for")
        .contains("counts the scenario rules of this run that name each operation")
        .contains("counts nothing about traffic");
  }

  @Test
  void a_rule_that_is_a_step_of_a_story_shows_the_state_it_waits_for_and_the_state_it_leaves() {
    List<WorkbenchScreen.Scenario> scenarios =
        WorkbenchScreen.scenariosOf(
            List.of(aStep("scn_1", "settle-then-refund", "Started", "Settled")));

    WorkbenchScreen.Scenario scenario = scenarios.get(0);
    assertThat(scenario.story()).isEqualTo("settle-then-refund");
    assertThat(scenario.whenState()).isEqualTo("Started");
    assertThat(scenario.thenState()).isEqualTo("Settled");
    assertThat(scenario.keyedOn()).isEqualTo(StoryKeying.FLOW.name());
  }

  @Test
  void a_rule_that_is_no_part_of_a_story_reports_no_story_rather_than_an_invented_one() {
    List<WorkbenchScreen.Scenario> scenarios =
        WorkbenchScreen.scenariosOf(List.of(aStep("scn_1", null, null, null)));

    WorkbenchScreen.Scenario scenario = scenarios.get(0);
    assertThat(scenario.story()).isNull();
    assertThat(scenario.keyedOn()).isNull();
    assertThat(scenario.whenState()).isNull();
  }

  @Test
  void a_rule_narrowed_by_a_state_counts_as_more_specific_than_the_same_rule_without_one() {
    List<WorkbenchScreen.Scenario> narrowed =
        WorkbenchScreen.scenariosOf(
            List.of(aStep("scn_1", "settle-then-refund", "Settled", "Refunded")));
    List<WorkbenchScreen.Scenario> open =
        WorkbenchScreen.scenariosOf(
            List.of(aStep("scn_1", "settle-then-refund", null, "Refunded")));

    assertThat(narrowed.get(0).specificity()).isGreaterThan(open.get(0).specificity());
  }

  @Test
  void the_workbench_names_the_test_run_and_the_providers_this_simulator_serves() {
    WorkbenchScreen.View view = viewOf(anAccountHolding(Map.of()));

    assertThat(view.testRunId()).isEqualTo(RUN.value());
    assertThat(view.providers()).containsExactly("grow", "stripe");
    assertThat(view.provider()).isEqualTo("grow");
  }

  private static WorkbenchScreen.View viewOf(
      Function<String, Optional<DisclosedAccount>> disclosureOf) {
    return WorkbenchScreen.of(
        "grow",
        List.of("grow", "stripe"),
        RUN.value(),
        accountFields(),
        List.of(ACCOUNT),
        disclosureOf,
        BASE_URL,
        pairing(),
        List.of(),
        vocabulary(),
        WorkbenchScreen.theNewestObjectsOf(aPageOf(0), AT),
        List.of(),
        EMPTY_QUEUE,
        THE_FIVE_THIS_BUILD_SERVES);
  }

  private static Function<String, Optional<DisclosedAccount>> anAccountHolding(
      Map<String, String> held) {
    Map<String, List<String>> disclosed = new LinkedHashMap<>();
    held.forEach((field, value) -> disclosed.put(field, List.of(value)));
    return accountId -> Optional.of(new DisclosedAccount(accountId, Map.copyOf(disclosed)));
  }

  private static List<String> theValuesShownOn(WorkbenchScreen.View view) {
    List<String> shown = new ArrayList<>();
    for (WorkbenchScreen.ConfiguredAccount account : view.accounts()) {
      for (Disclosure.Field field : account.configuration()) {
        shown.addAll(field.values());
      }
    }
    return List.copyOf(shown);
  }

  private static Disclosure.Field theFieldNamed(String name, WorkbenchScreen.View view) {
    for (WorkbenchScreen.ConfiguredAccount account : view.accounts()) {
      for (Disclosure.Field field : account.configuration()) {
        if (field.name().equals(name)) {
          return field;
        }
      }
    }
    throw new AssertionError("no field named " + name + " is on this workbench");
  }

  private static WorkbenchScreen.Operation theOperationNamed(
      String name, List<WorkbenchScreen.Operation> operations) {
    for (WorkbenchScreen.Operation operation : operations) {
      if (operation.name().equals(name)) {
        return operation;
      }
    }
    throw new AssertionError("no operation named " + name + " is on this workbench");
  }

  private static ProviderObjectPage aPageOf(int objects) {
    List<PlacedProviderObject> placed = new ArrayList<>();
    for (int arrival = objects; arrival > 0; arrival--) {
      String providerObjectId = "trx_" + arrival;
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrival, providerObjectId), anObject(providerObjectId)));
    }
    return new ProviderObjectPage.Listed(List.copyOf(placed));
  }

  private static ProviderObjectState anObject(String providerObjectId) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(providerObjectId),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of("120.50"),
        "ILS",
        Ledger.empty(),
        true,
        true,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderAccountFields accountFields() {
    return new ProviderAccountFields(
        GROW,
        List.of(
            new ConfigurableField(
                SECRET_FIELD,
                "The user id",
                "a sentence a test does not read",
                true,
                true,
                List.of(),
                "a value a test does not read"),
            new ConfigurableField(
                PLAIN_FIELD,
                "The wire form",
                "a sentence a test does not read",
                false,
                false,
                List.of(),
                "a value a test does not read")));
  }

  private static ProviderPairing pairing() {
    return new ProviderPairing(
        GROW,
        API_BASE_PATH,
        "a request of this contract names its account in the body it posts",
        List.of(new PairingSetting("GROW_BASE_URL", new PairingValue.TheProviderBaseUrl())));
  }

  private static ProviderVocabulary vocabulary() {
    return new ProviderVocabulary(
        GROW,
        List.of(
            new ProviderOperation(
                CHARGE,
                Set.of(MatchFact.MERCHANT_ID, MatchFact.AMOUNT),
                "the charge",
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
            new ProviderOperation(
                LOOK_UP,
                Set.of(MatchFact.PROVIDER_OBJECT_ID),
                "the charge",
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE),
            new ProviderOperation(
                REFUND,
                Set.of(MatchFact.PROVIDER_OBJECT_ID, MatchFact.AMOUNT),
                "the charge",
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
        Map.of(MatchFact.CUSTOMER_ID, "this provider names no customer anywhere"),
        new FailureCodes("[0-9]{2}", "a two-digit code"),
        ProcessingStates.onTheseOperations(CHARGE));
  }

  private static ScenarioRule aRuleNaming(@Nullable String operation, String scenarioId) {
    ScenarioMatch match =
        operation == null
            ? ScenarioMatch.anyRequest()
            : ScenarioMatch.anyRequest().naming(operation);
    return new ScenarioRule(
        scenarioId, GROW, RUN, 0, 1L, match, new ScenarioAction.FixedSuccess(), null, AT);
  }

  private static ScenarioRule aStep(
      String scenarioId,
      @Nullable String story,
      @Nullable String whenState,
      @Nullable String thenState) {
    return new ScenarioRule(
        scenarioId,
        GROW,
        RUN,
        0,
        1L,
        ScenarioMatch.anyRequest().naming("settleSuspendedTransaction"),
        new ScenarioAction.FixedSuccess(),
        story == null ? null : new ScenarioStory(story, StoryKeying.FLOW, whenState, thenState),
        AT);
  }
}
