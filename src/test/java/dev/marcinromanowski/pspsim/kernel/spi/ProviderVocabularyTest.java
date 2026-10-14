package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.groups.Tuple;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProviderVocabularyTest {

  private static final ProviderId PROVIDER = ProviderId.of("twokeys");
  private static final TestRunId RUN = TestRunId.of("e2e-run-123");

  private static final String OF_THE_SESSION = "the checkout session";
  private static final String OF_THE_PAYMENT = "the payment";

  private final ProviderVocabulary vocabulary =
      new ProviderVocabulary(
          PROVIDER,
          List.of(
              new ProviderOperation(
                  "openSession",
                  Set.of(MatchFact.MERCHANT_ID),
                  OF_THE_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  "readSession",
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID),
                  OF_THE_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  "capture",
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID, MatchFact.AMOUNT),
                  OF_THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  "ping",
                  Set.of(),
                  OF_THE_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
          Map.of(MatchFact.CURRENCY, "this provider names no currency anywhere"),
          new FailureCodes("[A-Z_]+", "this provider declines with an upper-case word"),
          ProcessingStates.noneOfThem("this provider names no processing state"));

  @Test
  void every_operation_answers_the_provider_wide_codes_unless_the_vocabulary_names_its_own() {
    ProviderVocabulary split =
        withTheCodesOf(Map.of("capture", new FailureCodes("[A-Z]", "a letter")));

    assertThat(vocabulary.theCodesOf("capture")).isSameAs(vocabulary.failureCodes());
    assertThat(vocabulary.theCodesOf(null)).isSameAs(vocabulary.failureCodes());
    assertThat(vocabulary.theCodesOf("capture").admits("DECLINED")).isTrue();
    assertThat(vocabulary.theCodesOf("capture").admits("declined")).isFalse();
    assertThat(split.theCodesOf("capture").describedAs()).isEqualTo("a letter");
    assertThat(split.theCodesOf("ping").describedAs())
        .isEqualTo("this provider declines with an upper-case word");
    assertThat(split.theCodesOf("capture").admits("X")).isTrue();
    assertThat(split.theCodesOf("capture").admits("XY")).isFalse();
    assertThat(split.theCodesOf(null).admits("XY")).isTrue();
  }

  @Test
  void a_rule_naming_an_operation_and_a_code_it_does_not_answer_is_refused_naming_both() {
    ProviderVocabulary split =
        withTheCodesOf(Map.of("capture", new FailureCodes("[A-Z]", "a letter")));

    assertThat(split.refusals(aFailing("capture", "DECLINED")))
        .containsExactly(
            new ScenarioRefusal(
                "action.providerCode",
                "operation capture of provider twokeys answers no failure code DECLINED: it"
                    + " answers a letter"));
    assertThat(split.refusals(aFailing("capture", "X"))).isEmpty();
    assertThat(split.refusals(aFailing("ping", "DECLINED"))).isEmpty();
    assertThat(split.refusals(aFailing(null, "DECLINED"))).isEmpty();
    assertThat(vocabulary.refusals(aFailing("capture", "DECLINED"))).isEmpty();
    assertThat(split.refusals(aFailing("capture", "lower")))
        .extracting(ScenarioRefusal::reason)
        .containsExactly("this provider declines with an upper-case word");
  }

  @Test
  void codes_of_an_operation_the_vocabulary_does_not_expose_are_refused_when_it_is_built() {
    assertThatThrownBy(
            () -> withTheCodesOf(Map.of("refund", new FailureCodes("[A-Z]", "a letter"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "operation refund is declared to answer failure codes of its own, and the same"
                + " vocabulary exposes no operation of that name");
  }

  @Test
  void a_vocabulary_declaring_codes_for_some_operations_declares_them_for_every_one() {
    assertThatThrownBy(
            () ->
                new ProviderVocabulary(
                    vocabulary.provider(),
                    vocabulary.operations(),
                    vocabulary.factsItNeverCarries(),
                    vocabulary.failureCodes(),
                    vocabulary.processingStates(),
                    Map.of("capture", new FailureCodes("[A-Z]", "a letter"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith("it names none for openSession, readSession, ping");
  }

  private ProviderVocabulary withTheCodesOf(Map<String, FailureCodes> codes) {
    Map<String, FailureCodes> every = new java.util.LinkedHashMap<>();
    for (String operation : vocabulary.operationNames()) {
      every.put(operation, vocabulary.failureCodes());
    }
    every.putAll(codes);
    return new ProviderVocabulary(
        vocabulary.provider(),
        vocabulary.operations(),
        vocabulary.factsItNeverCarries(),
        vocabulary.failureCodes(),
        vocabulary.processingStates(),
        every);
  }

  private static NewScenarioRule aFailing(@Nullable String operation, String code) {
    return new NewScenarioRule(
        PROVIDER,
        RUN,
        0,
        operation == null
            ? ScenarioMatch.anyRequest()
            : ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.TerminalFailure(code),
        null);
  }

  @Test
  void publishes_the_operations_a_scenario_may_name_without_a_candidate_rule_being_built() {
    assertThat(vocabulary.operationNames())
        .containsExactly("openSession", "readSession", "capture", "ping");
    assertThat(vocabulary.operation("capture").orElseThrow().storiesKeyedOn())
        .isEqualTo(OF_THE_PAYMENT);
    assertThat(vocabulary.operation("refund")).isEmpty();
  }

  @Test
  void names_each_object_an_identifier_addresses_once_in_the_order_this_provider_declares_them() {
    assertThat(vocabulary.theObjectsAnIdentifierAddresses())
        .containsExactly(OF_THE_SESSION, OF_THE_PAYMENT);
  }

  @Test
  void names_one_object_where_every_operation_of_the_provider_addresses_the_same_one() {
    ProviderVocabulary ofOneObject =
        new ProviderVocabulary(
            PROVIDER,
            List.of(
                new ProviderOperation(
                    "charge",
                    Set.of(MatchFact.MERCHANT_ID),
                    OF_THE_PAYMENT,
                    StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
                new ProviderOperation(
                    "refund",
                    Set.of(MatchFact.MERCHANT_ID, MatchFact.AMOUNT),
                    OF_THE_PAYMENT,
                    StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
            Map.of(),
            new FailureCodes("[A-Z_]+", "this provider declines with an upper-case word"),
            ProcessingStates.noneOfThem("this provider names no processing state"));

    assertThat(ofOneObject.theObjectsAnIdentifierAddresses()).containsExactly(OF_THE_PAYMENT);
  }

  @Test
  void refuses_a_decline_code_the_provider_could_never_answer_with() {
    assertThat(vocabulary.refusals(declining("payment_declined")))
        .extracting(ScenarioRefusal::property)
        .containsExactly("action.providerCode");
    assertThat(vocabulary.refusals(declining("PAYMENT_DECLINED"))).isEmpty();
  }

  @Test
  void refuses_a_fact_the_provider_carries_on_no_endpoint_at_all() {
    assertThat(vocabulary.refusals(matching(fact(null, null, null, "PLN"))))
        .extracting(ScenarioRefusal::property, ScenarioRefusal::reason)
        .containsExactly(Tuple.tuple("match.currency", "this provider names no currency anywhere"));
  }

  @Test
  void refuses_an_operation_the_provider_does_not_expose_and_names_no_other_property() {
    List<ScenarioRefusal> refused =
        vocabulary.refusals(matching(fact("void", "pi_1", Amount.of("10"), null)));

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("match.operation");
    assertThat(refused.get(0).reason())
        .contains("openSession", "readSession", "capture", "ping")
        .doesNotContain("void");
  }

  @Test
  void refuses_a_fact_the_named_operation_never_carries_because_that_rule_can_never_fire() {
    List<ScenarioRefusal> refused =
        vocabulary.refusals(matching(fact("openSession", "pi_1", null, null)));

    assertThat(refused)
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.providerObjectId");
    assertThat(refused.get(0).reason()).contains("openSession", "can never fire");
  }

  @Test
  void refuses_a_merchant_only_on_the_operation_that_carries_no_merchant_at_all() {
    assertThat(vocabulary.refusals(matching(ScenarioMatch.anyRequest().naming("ping")))).isEmpty();
    assertThat(
            vocabulary.refusals(
                matching(
                    new ScenarioMatch(
                        "merchant-1", null, null, null, null, null, null, "ping", null, Map.of()))))
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.merchantId");
  }

  @Test
  void accepts_a_fact_no_operation_carries_yet_while_the_rule_names_no_operation() {
    assertThat(vocabulary.refusals(matching(fact(null, "pi_1", Amount.of("10"), null)))).isEmpty();
    assertThat(vocabulary.refusals(matching(fact("capture", "pi_1", Amount.of("10"), null))))
        .isEmpty();
  }

  @Test
  void answers_one_rule_with_every_property_it_cannot_apply() {
    assertThat(
            vocabulary.refusals(
                new NewScenarioRule(
                    PROVIDER,
                    RUN,
                    0,
                    new ScenarioMatch(
                        null, null, "pi_1", null, null, null, "PLN", "openSession", null, Map.of()),
                    new ScenarioAction.TerminalFailure("declined"),
                    null)))
        .extracting(ScenarioRefusal::property)
        .containsExactly("action.providerCode", "match.currency", "match.providerObjectId");
  }

  @Test
  void refuses_a_story_whose_steps_name_operations_keyed_on_different_things() {
    List<ScenarioRefusal> refused =
        vocabulary.refusals(
            List.of(
                step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"),
                step("checkout", "capture", "opened", null)),
            List.of());

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("story");
    assertThat(refused.get(0).reason())
        .contains("checkout", OF_THE_SESSION, OF_THE_PAYMENT, "could never see");
  }

  @Test
  void refuses_a_story_whose_flow_key_the_object_it_addresses_may_replace_mid_story() {
    List<ScenarioRefusal> refused =
        aProviderWhoseCaptureRenamesThePayment()
            .refusals(
                List.of(
                    step("checkout", "capture", ScenarioStory.INITIAL_STATE, "captured"),
                    step("checkout", "refund", "captured", null)),
                List.of());

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("story");
    assertThat(refused.get(0).reason())
        .contains("capture", "may replace", "checkout", "no later step can read it");
  }

  @Test
  void refuses_a_story_step_that_names_no_operation_where_one_operation_may_be_renamed() {
    List<ScenarioRefusal> refused =
        aProviderWhoseCaptureRenamesThePayment()
            .refusals(
                List.of(
                    new NewScenarioRule(
                        PROVIDER,
                        RUN,
                        0,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.FixedSuccess(),
                        new ScenarioStory(
                            "checkout", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, null))),
                List.of());

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("story");
  }

  @Test
  void admits_a_story_whose_flow_key_every_step_draws_from_an_identifier_kept_for_life() {
    assertThat(
            vocabulary.refusals(
                List.of(
                    step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"),
                    step("checkout", "readSession", "opened", null)),
                List.of()))
        .isEmpty();
  }

  @Test
  void a_story_keyed_on_a_whole_test_run_is_not_narrowed_by_what_an_object_may_rename() {
    assertThat(
            aProviderWhoseCaptureRenamesThePayment()
                .refusals(
                    List.of(
                        stepKeyedOn(
                            StoryKeying.TEST_RUN,
                            "checkout",
                            "capture",
                            ScenarioStory.INITIAL_STATE,
                            null)),
                    List.of()))
        .isEmpty();
  }

  private static ProviderVocabulary aProviderWhoseCaptureRenamesThePayment() {
    return new ProviderVocabulary(
        PROVIDER,
        List.of(
            new ProviderOperation(
                "capture",
                Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID),
                OF_THE_PAYMENT,
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE),
            new ProviderOperation(
                "refund",
                Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID),
                OF_THE_PAYMENT,
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
        Map.of(MatchFact.CURRENCY, "this provider names no currency anywhere"),
        new FailureCodes("[A-Z_]+", "this provider declines with an upper-case word"),
        ProcessingStates.noneOfThem("this provider names no processing state"));
  }

  @Test
  void refuses_a_story_whose_steps_declare_different_keys_because_they_stand_apart() {
    List<ScenarioRefusal> refused =
        vocabulary.refusals(
            List.of(
                step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"),
                stepKeyedOn(StoryKeying.TEST_RUN, "checkout", "capture", "opened", null)),
            List.of());

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("keyedOn");
    assertThat(refused.get(0).reason()).contains("checkout", "FLOW", "TEST_RUN", "could never see");
  }

  @Test
  void refuses_a_step_added_later_to_a_story_whose_stored_step_declares_another_key() {
    List<ScenarioRefusal> refused =
        vocabulary.refusals(
            stepKeyedOn(StoryKeying.TEST_RUN, "checkout", "readSession", "opened", null),
            List.of(
                written(step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"))));

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("keyedOn");
  }

  @Test
  void accepts_a_story_keyed_on_the_test_run_whose_steps_name_operations_of_two_key_spaces() {
    assertThat(
            vocabulary.refusals(
                List.of(
                    stepKeyedOn(
                        StoryKeying.TEST_RUN,
                        "checkout",
                        "openSession",
                        ScenarioStory.INITIAL_STATE,
                        "opened"),
                    stepKeyedOn(StoryKeying.TEST_RUN, "checkout", "capture", "opened", null)),
                List.of()))
        .isEmpty();
  }

  @Test
  void accepts_a_story_whose_steps_name_operations_keyed_on_one_thing() {
    assertThat(
            vocabulary.refusals(
                List.of(
                    step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"),
                    step("checkout", "readSession", "opened", null)),
                List.of()))
        .isEmpty();
  }

  @Test
  void refuses_a_step_added_later_to_a_story_whose_stored_step_is_keyed_on_something_else() {
    List<ScenarioRefusal> refused =
        vocabulary.refusals(
            step("checkout", "capture", "opened", null),
            List.of(
                written(step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"))));

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("story");
    assertThat(refused.get(0).reason()).contains("checkout", OF_THE_SESSION, OF_THE_PAYMENT);
  }

  @Test
  void accepts_a_step_added_later_to_a_story_whose_stored_step_is_keyed_on_the_same_thing() {
    assertThat(
            vocabulary.refusals(
                step("checkout", "readSession", "opened", null),
                List.of(
                    written(
                        step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened")))))
        .isEmpty();
  }

  @Test
  void reads_a_stored_step_of_another_story_or_another_test_run_as_no_step_of_this_one() {
    assertThat(
            vocabulary.refusals(
                step("checkout", "capture", ScenarioStory.INITIAL_STATE, "captured"),
                List.of(
                    written(
                        step("refunding", "openSession", ScenarioStory.INITIAL_STATE, "opened")),
                    writtenIn(
                        TestRunId.of("another-run"),
                        step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened")),
                    writtenWithoutAStory())))
        .isEmpty();
  }

  @Test
  void reads_a_candidate_that_is_no_step_of_any_story_as_nothing_to_check_across_steps() {
    assertThat(
            vocabulary.refusals(
                matching(ScenarioMatch.anyRequest().naming("capture")),
                List.of(
                    written(
                        step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened")))))
        .isEmpty();
  }

  @Test
  void refuses_a_story_one_of_whose_steps_names_no_operation_and_so_could_meet_any_key() {
    List<ScenarioRefusal> refused =
        vocabulary.refusals(
            List.of(
                step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"),
                new NewScenarioRule(
                    PROVIDER,
                    RUN,
                    0,
                    ScenarioMatch.anyRequest(),
                    new ScenarioAction.FixedSuccess(),
                    new ScenarioStory("checkout", StoryKeying.FLOW, "opened", null))),
            List.of());

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("story");
    assertThat(refused.get(0).reason())
        .contains("a step naming no operation on any of", OF_THE_SESSION, OF_THE_PAYMENT);
  }

  @Test
  void reads_two_stories_of_one_name_in_two_test_runs_as_two_stories() {
    assertThat(
            vocabulary.refusals(
                List.of(
                    step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"),
                    inRun(
                        TestRunId.of("another-run"),
                        step("checkout", "capture", ScenarioStory.INITIAL_STATE, "captured"))),
                List.of()))
        .isEmpty();
  }

  @Test
  void reads_a_step_naming_an_operation_it_does_not_expose_as_keyed_on_nothing_it_declares() {
    assertThat(
            vocabulary.refusals(
                List.of(
                    step("checkout", "openSession", ScenarioStory.INITIAL_STATE, "opened"),
                    step("checkout", "void", "opened", null)),
                List.of()))
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.operation");
  }

  @Test
  void reports_a_property_two_rules_of_one_expansion_share_only_once() {
    assertThat(
            vocabulary.refusals(
                List.of(declining("declined"), declining("also_declined")), List.of()))
        .extracting(ScenarioRefusal::property)
        .containsExactly("action.providerCode");
  }

  @Test
  void refuses_to_declare_an_operation_that_carries_a_fact_the_same_provider_never_carries() {
    List<ProviderOperation> carryingACurrency =
        List.of(
            new ProviderOperation(
                "capture",
                Set.of(MatchFact.CURRENCY),
                OF_THE_PAYMENT,
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE));
    Map<MatchFact, String> denyingTheCurrency =
        Map.of(MatchFact.CURRENCY, "this provider names no currency anywhere");
    FailureCodes failureCodes = new FailureCodes("[0-9]+", "a number");
    ProcessingStates processingStates =
        ProcessingStates.noneOfThem("this provider names no processing state");

    assertThatThrownBy(
            () ->
                new ProviderVocabulary(
                    PROVIDER,
                    carryingACurrency,
                    denyingTheCurrency,
                    failureCodes,
                    processingStates))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("match.currency");
  }

  @Test
  void refuses_to_declare_one_operation_twice() {
    List<ProviderOperation> oneOperationTwice =
        List.of(
            new ProviderOperation(
                "capture",
                Set.of(),
                OF_THE_PAYMENT,
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
            new ProviderOperation(
                "capture",
                Set.of(),
                OF_THE_PAYMENT,
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE));
    Map<MatchFact, String> carryingEveryFact = Map.of();
    FailureCodes failureCodes = new FailureCodes("[0-9]+", "a number");
    ProcessingStates processingStates =
        ProcessingStates.noneOfThem("this provider names no processing state");

    assertThatThrownBy(
            () ->
                new ProviderVocabulary(
                    PROVIDER, oneOperationTwice, carryingEveryFact, failureCodes, processingStates))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("capture");
  }

  @Test
  void refuses_a_vocabulary_that_exposes_no_operation_at_all() {
    List<ProviderOperation> noOperation = List.of();
    Map<MatchFact, String> carryingEveryFact = Map.of();
    FailureCodes failureCodes = new FailureCodes("[0-9]+", "a number");
    ProcessingStates processingStates =
        ProcessingStates.noneOfThem("this provider names no processing state");

    assertThatThrownBy(
            () ->
                new ProviderVocabulary(
                    PROVIDER, noOperation, carryingEveryFact, failureCodes, processingStates))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no operation");
  }

  @Test
  void refuses_a_refused_fact_that_says_nothing_about_what_the_contract_has_instead() {
    List<ProviderOperation> oneCapture =
        List.of(
            new ProviderOperation(
                "capture",
                Set.of(),
                OF_THE_PAYMENT,
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE));
    Map<MatchFact, String> sayingNothingInItsOwnWords = Map.of(MatchFact.CURRENCY, " ");
    FailureCodes failureCodes = new FailureCodes("[0-9]+", "a number");
    ProcessingStates processingStates =
        ProcessingStates.noneOfThem("this provider names no processing state");

    assertThatThrownBy(
            () ->
                new ProviderVocabulary(
                    PROVIDER,
                    oneCapture,
                    sayingNothingInItsOwnWords,
                    failureCodes,
                    processingStates))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("match.currency");
  }

  @Test
  void refuses_an_operation_that_is_not_named_or_says_nothing_about_what_keys_its_stories() {
    assertThatThrownBy(
            () ->
                new ProviderOperation(
                    " ",
                    Set.of(),
                    OF_THE_PAYMENT,
                    StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("named");
    assertThatThrownBy(
            () ->
                new ProviderOperation(
                    "capture",
                    Set.of(),
                    " ",
                    StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("capture");
  }

  @Test
  void refuses_a_failure_vocabulary_that_says_nothing_about_the_shape_it_admits() {
    assertThatThrownBy(() -> new FailureCodes("[0-9]+", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shape");
  }

  @Test
  void publishes_the_shape_of_a_decline_code_so_a_reader_need_not_try_one() {
    assertThat(vocabulary.failureCodes().shape()).isEqualTo("[A-Z_]+");
    assertThat(vocabulary.failureCodes().describedAs())
        .isEqualTo("this provider declines with an upper-case word");
  }

  private static final ProviderVocabulary SOME_CALLS_REST_IN_FLIGHT =
      new ProviderVocabulary(
          PROVIDER,
          List.of(
              new ProviderOperation(
                  "openSession",
                  Set.of(MatchFact.MERCHANT_ID),
                  OF_THE_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  "capture",
                  Set.of(MatchFact.MERCHANT_ID),
                  OF_THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
          Map.of(),
          new FailureCodes("[A-Z_]+", "an upper-case word"),
          ProcessingStates.onTheseOperations("openSession"));

  @Test
  void refuses_a_pending_outcome_a_provider_that_publishes_no_such_state_could_never_report() {
    assertThat(vocabulary.refusals(pending("capture")))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("action.type");
              assertThat(refusal.reason()).isEqualTo("this provider names no processing state");
            });
  }

  @Test
  void refuses_a_pending_outcome_on_an_operation_of_this_provider_that_never_reports_one() {
    assertThat(SOME_CALLS_REST_IN_FLIGHT.refusals(pending("capture")))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("match.operation");
              assertThat(refusal.reason()).contains("openSession");
            });
  }

  @Test
  void refuses_a_pending_outcome_owed_a_decline_code_the_provider_could_never_answer_with() {
    assertThat(SOME_CALLS_REST_IN_FLIGHT.refusals(pendingUntil("openSession", "not_a_code")))
        .extracting(ScenarioRefusal::property)
        .containsExactly("action.providerCode");
    assertThat(SOME_CALLS_REST_IN_FLIGHT.refusals(pendingUntil("openSession", "DECLINED")))
        .isEmpty();
  }

  @Test
  void admits_a_pending_outcome_on_the_operation_the_provider_says_reports_one() {
    assertThat(SOME_CALLS_REST_IN_FLIGHT.refusals(pending("openSession"))).isEmpty();
    assertThat(SOME_CALLS_REST_IN_FLIGHT.theOperationsThatReportAProcessingState())
        .containsExactly("openSession");
  }

  @Test
  void answers_no_operation_at_all_where_the_provider_reports_no_processing_state() {
    assertThat(vocabulary.theOperationsThatReportAProcessingState()).isEmpty();
  }

  @Test
  void refuses_to_declare_a_processing_state_on_an_operation_this_vocabulary_never_exposes() {
    List<ProviderOperation> oneCapture =
        List.of(
            new ProviderOperation(
                "capture",
                Set.of(),
                OF_THE_PAYMENT,
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE));
    Map<MatchFact, String> carryingEveryFact = Map.of();
    FailureCodes failureCodes = new FailureCodes("[0-9]+", "a number");
    ProcessingStates reachedBySettle = ProcessingStates.onTheseOperations("settle");

    assertThatThrownBy(
            () ->
                new ProviderVocabulary(
                    PROVIDER, oneCapture, carryingEveryFact, failureCodes, reachedBySettle))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("settle");
  }

  @Test
  void refuses_a_provider_that_reports_no_processing_state_and_will_not_say_what_it_has_instead() {
    assertThatThrownBy(() -> ProcessingStates.noneOfThem(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("its own words");
  }

  @Test
  void refuses_a_provider_that_names_no_operation_rather_than_saying_it_reports_none() {
    assertThatThrownBy(ProcessingStates::onTheseOperations)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(ProcessingStates.NoneOfThem.class.getSimpleName());
  }

  private static NewScenarioRule pending(String operation) {
    return pendingUntil(operation, null);
  }

  private static NewScenarioRule pendingUntil(String operation, @Nullable String comingToRestWith) {
    return new NewScenarioRule(
        PROVIDER,
        RUN,
        0,
        ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), comingToRestWith),
        null);
  }

  private static NewScenarioRule declining(String providerCode) {
    return new NewScenarioRule(
        PROVIDER,
        RUN,
        0,
        ScenarioMatch.anyRequest(),
        new ScenarioAction.TerminalFailure(providerCode),
        null);
  }

  private static NewScenarioRule matching(ScenarioMatch match) {
    return new NewScenarioRule(PROVIDER, RUN, 0, match, new ScenarioAction.FixedSuccess(), null);
  }

  private static NewScenarioRule step(
      String story, String operation, String whenState, @Nullable String thenState) {
    return stepKeyedOn(StoryKeying.FLOW, story, operation, whenState, thenState);
  }

  private static NewScenarioRule stepKeyedOn(
      StoryKeying keyedOn,
      String story,
      String operation,
      String whenState,
      @Nullable String thenState) {
    return new NewScenarioRule(
        PROVIDER,
        RUN,
        0,
        ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.FixedSuccess(),
        new ScenarioStory(story, keyedOn, whenState, thenState));
  }

  private static NewScenarioRule inRun(TestRunId testRunId, NewScenarioRule rule) {
    return new NewScenarioRule(
        rule.provider(), testRunId, rule.priority(), rule.match(), rule.action(), rule.story());
  }

  private static ScenarioRule written(NewScenarioRule rule) {
    return writtenIn(rule.testRunId(), rule);
  }

  private static ScenarioRule writtenIn(TestRunId testRunId, NewScenarioRule rule) {
    return new ScenarioRule(
        "scn_1",
        rule.provider(),
        testRunId,
        rule.priority(),
        1,
        rule.match(),
        rule.action(),
        rule.story(),
        Instant.parse("2026-09-07T12:00:00Z"));
  }

  private static ScenarioRule writtenWithoutAStory() {
    return written(matching(ScenarioMatch.anyRequest().naming("capture")));
  }

  private static ScenarioMatch fact(
      @Nullable String operation,
      @Nullable String providerObjectId,
      @Nullable Amount amount,
      @Nullable String currency) {
    return new ScenarioMatch(
        null, null, providerObjectId, null, null, amount, currency, operation, null, Map.of());
  }
}
