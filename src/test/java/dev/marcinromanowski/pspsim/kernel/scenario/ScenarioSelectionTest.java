package dev.marcinromanowski.pspsim.kernel.scenario;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ScenarioSelectionTest {

  private static final ProviderId GROW = ProviderId.of("grow");
  private static final ProviderId STRIPE = ProviderId.of("stripe");
  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("another-run");

  private static final String OF_A_PROCESS = "process";

  private static final String ONE_FLOW = StoryKey.inSpace(OF_A_PROCESS, "one-flow");

  private static final int SPACE_AND_SEPARATOR = StoryKey.inSpace(OF_A_PROCESS, "").length();

  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine engine = new ScenarioEngine(rules);

  @Test
  void a_request_no_rule_matches_is_left_to_the_provider() {
    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void a_rule_with_no_predicates_matches_every_request_of_its_test_run() {
    ScenarioRule rule = register(RUN, 0, ScenarioMatch.anyRequest(), decline("701"));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(rule.scenarioId(), "701"));
  }

  @Test
  void a_failure_the_operation_does_not_answer_is_no_match_and_the_next_rule_decides() {
    ScenarioRule declining = register(RUN, 5, ScenarioMatch.anyRequest(), decline("701"));
    ScenarioRule proceeding =
        register(RUN, 0, ScenarioMatch.anyRequest(), new ScenarioAction.FixedSuccess());
    List<String> asked = new ArrayList<>();
    ScenarioEngine judging =
        new ScenarioEngine(
            rules,
            (provider, operation, code) -> {
              asked.add(provider.value() + " " + operation + " " + code);
              return !"refund".equals(operation);
            });

    assertThat(judging.decide(about("refund")))
        .isEqualTo(new ScenarioDecision.Proceed(proceeding.scenarioId()));
    assertThat(judging.decide(about("capture")))
        .isEqualTo(new ScenarioDecision.Fail(declining.scenarioId(), "701"));
    assertThat(asked).containsExactly("grow refund 701", "grow capture 701");
    assertThat(engine.decide(about("refund")))
        .as("an engine told nothing lets every operation answer every code")
        .isEqualTo(new ScenarioDecision.Fail(declining.scenarioId(), "701"));
  }

  @Test
  void a_step_whose_failure_the_operation_does_not_answer_moves_no_story() {
    step("declines", null, "declined", decline("701"));
    ScenarioEngine answeringNothing =
        new ScenarioEngine(rules, (provider, operation, code) -> false);

    assertThat(answeringNothing.decide(request())).isEqualTo(new ScenarioDecision.NoScenario());
    assertThat(rules.storyStatesOf(GROW, RUN, new StoryKey(StoryKeying.FLOW, ONE_FLOW)))
        .doesNotContainKey("declines");
    assertThat(engine.decide(request())).isInstanceOf(ScenarioDecision.Fail.class);
    assertThat(rules.storyStatesOf(GROW, RUN, new StoryKey(StoryKeying.FLOW, ONE_FLOW)))
        .containsEntry("declines", "declined");
  }

  @Test
  void an_action_naming_no_code_is_answered_by_every_operation() {
    assertThat(
            ScenarioEngine.itsCodeIsOneTheOperationAnswers(
                new ScenarioAction.FixedSuccess(), code -> false))
        .isTrue();
    assertThat(ScenarioEngine.itsCodeIsOneTheOperationAnswers(decline("7"), code -> false))
        .isFalse();
    assertThat(ScenarioEngine.itsCodeIsOneTheOperationAnswers(decline("7"), code -> true)).isTrue();
    assertThat(OperationCodes.EVERY_CODE.answers(GROW, null, "7")).isTrue();
  }

  @Test
  void a_rule_of_another_test_run_never_decides_this_request() {
    register(ANOTHER_RUN, 0, ScenarioMatch.anyRequest(), decline("701"));

    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void a_rule_of_another_provider_never_decides_this_request() {
    rules.add(
        new NewScenarioRule(STRIPE, RUN, 0, ScenarioMatch.anyRequest(), decline("701"), null));

    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.NoScenario());
  }

  @ParameterizedTest(name = "a {0} predicate matches only the request that carries its value")
  @MethodSource("theNamedPredicates")
  void every_named_predicate_matches_only_the_request_that_carries_its_value(
      String name, ScenarioMatch match, ScenarioRequest carrying, ScenarioRequest carryingAnother) {
    register(RUN, 0, match, decline("701"));

    assertThat(engine.decide(carrying)).isInstanceOf(ScenarioDecision.Fail.class);
    assertThat(engine.decide(carryingAnother)).isEqualTo(new ScenarioDecision.NoScenario());
  }

  @ParameterizedTest(name = "a {0} predicate matches no request that carries no such fact")
  @MethodSource("theNamedPredicates")
  void every_named_predicate_matches_no_request_that_carries_no_such_fact(
      String name, ScenarioMatch match) {
    register(RUN, 0, match, decline("701"));

    assertThat(engine.decide(carryingNothing())).isEqualTo(new ScenarioDecision.NoScenario());
  }

  @ParameterizedTest(name = "a {0} predicate wins a tie against a rule that names nothing")
  @MethodSource("theNamedPredicates")
  void every_named_predicate_decides_over_a_rule_of_one_priority_that_names_nothing(
      String name, ScenarioMatch match, ScenarioRequest carrying) {
    ScenarioRule specific = register(RUN, 100, match, decline("803"));
    register(RUN, 100, ScenarioMatch.anyRequest(), decline("701"));

    assertThat(engine.decide(carrying))
        .isEqualTo(new ScenarioDecision.Fail(specific.scenarioId(), "803"));
  }

  @Test
  void specificity_counts_every_match_field_that_names_something() {
    assertThat(ScenarioMatch.anyRequest().specificity()).isZero();
    assertThat(
            new ScenarioMatch(
                    "merchant-1",
                    "order-123",
                    "pi_sim_123",
                    "cus_sim_123",
                    "pm_success",
                    Amount.of("1000"),
                    "PLN",
                    "settleSuspendedTransaction",
                    null,
                    Map.of("X-Tenant", "acme"))
                .specificity())
        .isEqualTo(9);
    assertThat(ScenarioMatch.anyRequest().reporting("payment_process.completed").specificity())
        .isEqualTo(1);
  }

  @Test
  void the_delivery_rule_of_the_highest_priority_is_the_one_that_shapes_the_delivery() {
    DeliveryShaping shaping = new DeliveryShaping(rules);
    rules.add(
        new NewScenarioRule(
            GROW, RUN, 0, ScenarioMatch.anyRequest(), new ScenarioAction.DeliveryDropped(), null));
    ScenarioRule louder =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                100,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.DeliveryDuplicated(),
                null));

    assertThat(shaping.shapeOf(aDelivery(RUN, "merchant-1", "100001", null)))
        .isEqualTo(new DeliveryShape.Repeated(louder.scenarioId()));
  }

  @Test
  void a_delivery_rule_narrowed_on_the_merchant_shapes_no_delivery_of_another_one() {
    DeliveryShaping shaping = new DeliveryShaping(rules);
    rules.add(
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            new ScenarioMatch(
                "merchant-1", null, null, null, null, null, null, null, null, Map.of()),
            new ScenarioAction.DeliveryDropped(),
            null));

    assertThat(shaping.shapeOf(aDelivery(RUN, "merchant-2", "100001", null)))
        .isEqualTo(new DeliveryShape.AsScheduled());
    assertThat(shaping.shapeOf(aDelivery(RUN, "merchant-1", "100001", null)))
        .isInstanceOf(DeliveryShape.Dropped.class);
  }

  @Test
  void a_delivery_rule_of_another_test_run_never_shapes_this_delivery() {
    DeliveryShaping shaping = new DeliveryShaping(rules);
    rules.add(
        new NewScenarioRule(
            GROW,
            ANOTHER_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.DeliveryDropped(),
            null));

    assertThat(shaping.shapeOf(aDelivery(RUN, "merchant-1", "100001", null)))
        .isEqualTo(new DeliveryShape.AsScheduled());
  }

  @Test
  void a_held_delivery_carries_the_length_of_the_hold_to_the_dispatcher() {
    DeliveryShaping shaping = new DeliveryShaping(rules);
    ScenarioRule held =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(7)),
                null));

    assertThat(shaping.shapeOf(aDelivery(RUN, "merchant-1", "100001", null)))
        .isEqualTo(new DeliveryShape.HeldBack(held.scenarioId(), Duration.ofSeconds(7)));
  }

  private static DeliveryFacts aDelivery(
      TestRunId testRunId,
      String merchantId,
      @Nullable String providerObjectId,
      @Nullable String eventType) {
    return new DeliveryFacts(GROW, testRunId, merchantId, providerObjectId, eventType);
  }

  private static List<Arguments> theNamedPredicates() {
    return List.of(
        Arguments.of(
            "merchantId",
            new ScenarioMatch(
                "merchant-1", null, null, null, null, null, null, null, null, Map.of()),
            fact("merchant-1", null, null, null, null, null, null),
            fact("merchant-2", null, null, null, null, null, null)),
        Arguments.of(
            "orderId",
            new ScenarioMatch(
                null, "order-123", null, null, null, null, null, null, null, Map.of()),
            fact(null, "order-123", null, null, null, null, null),
            fact(null, "order-456", null, null, null, null, null)),
        Arguments.of(
            "providerObjectId",
            new ScenarioMatch(
                null, null, "pi_sim_123", null, null, null, null, null, null, Map.of()),
            fact(null, null, "pi_sim_123", null, null, null, null),
            fact(null, null, "pi_sim_456", null, null, null, null)),
        Arguments.of(
            "customerId",
            new ScenarioMatch(
                null, null, null, "cus_sim_123", null, null, null, null, null, Map.of()),
            fact(null, null, null, "cus_sim_123", null, null, null),
            fact(null, null, null, "cus_sim_456", null, null, null)),
        Arguments.of(
            "paymentMethodId",
            new ScenarioMatch(
                null, null, null, null, "pm_success", null, null, null, null, Map.of()),
            fact(null, null, null, null, "pm_success", null, null),
            fact(null, null, null, null, "pm_decline", null, null)),
        Arguments.of(
            "amount",
            new ScenarioMatch(
                null, null, null, null, null, Amount.of("1000"), null, null, null, Map.of()),
            fact(null, null, null, null, null, Amount.of("1000"), null),
            fact(null, null, null, null, null, Amount.of("1001"), null)),
        Arguments.of(
            "currency",
            new ScenarioMatch(null, null, null, null, null, null, "PLN", null, null, Map.of()),
            fact(null, null, null, null, null, null, "PLN"),
            fact(null, null, null, null, null, null, "ILS")),
        Arguments.of(
            "operation",
            ScenarioMatch.anyRequest().naming("settleSuspendedTransaction"),
            about("settleSuspendedTransaction"),
            about("refundTransaction")),
        Arguments.of(
            "header",
            headerMatch(Map.of("X-Simulator-Scenario", "capture-decline")),
            requestOf(
                "merchant-1", null, Map.of("X-Simulator-Scenario", List.of("capture-decline"))),
            requestOf("merchant-1", null, Map.of("X-Simulator-Scenario", List.of("something")))));
  }

  private static ScenarioRequest fact(
      String merchantId,
      String orderId,
      String providerObjectId,
      String customerId,
      String paymentMethodId,
      Amount amount,
      String currency) {
    return new ScenarioRequest(
        GROW,
        RUN,
        ONE_FLOW,
        merchantId,
        orderId,
        providerObjectId,
        customerId,
        paymentMethodId,
        amount,
        currency,
        null,
        Map.of());
  }

  private static ScenarioRequest about(String operation) {
    return new ScenarioRequest(
        GROW, RUN, ONE_FLOW, "merchant-1", null, null, null, null, null, null, operation, Map.of());
  }

  private static ScenarioRequest carryingNothing() {
    return fact(null, null, null, null, null, null, null);
  }

  @ParameterizedTest(name = "an amount predicate {0} against a request carrying {1} matches: {2}")
  @CsvSource({"1000,1000,true", "10.00,10.0,false", "10.0,10.00,false", "1000,1001,false"})
  void an_amount_predicate_is_matched_on_the_exact_lexical_string(
      String required, String sent, boolean matches) {
    register(
        RUN,
        0,
        new ScenarioMatch(
            null, null, null, null, null, Amount.of(required), null, null, null, Map.of()),
        decline("701"));

    boolean declined =
        engine.decide(requestOf("merchant-1", Amount.of(sent), Map.of()))
            instanceof ScenarioDecision.Fail;

    assertThat(declined).isEqualTo(matches);
  }

  @Test
  void an_amount_predicate_matches_no_request_that_carries_no_amount() {
    register(
        RUN,
        0,
        new ScenarioMatch(
            null, null, null, null, null, Amount.of("1000"), null, null, null, Map.of()),
        decline("701"));

    assertThat(engine.decide(requestOf("merchant-1", null, Map.of())))
        .isEqualTo(new ScenarioDecision.NoScenario());
  }

  @ParameterizedTest(name = "a header predicate matches the header sent as {0}")
  @ValueSource(strings = {"X-Simulator-Scenario", "x-simulator-scenario", "X-SIMULATOR-SCENARIO"})
  void a_header_predicate_ignores_the_case_the_client_chose(String sentAs) {
    register(
        RUN, 0, headerMatch(Map.of("X-Simulator-Scenario", "capture-decline")), decline("701"));

    assertThat(
            engine.decide(
                requestOf("merchant-1", null, Map.of(sentAs, List.of("capture-decline")))))
        .isInstanceOf(ScenarioDecision.Fail.class);
  }

  @Test
  void a_header_predicate_matches_one_of_several_values_sent_under_one_name() {
    register(
        RUN, 0, headerMatch(Map.of("X-Simulator-Scenario", "capture-decline")), decline("701"));

    assertThat(
            engine.decide(
                requestOf(
                    "merchant-1",
                    null,
                    Map.of("X-Simulator-Scenario", List.of("other", "capture-decline")))))
        .isInstanceOf(ScenarioDecision.Fail.class);
  }

  @Test
  void a_header_predicate_the_request_answers_with_another_value_matches_nothing() {
    register(
        RUN, 0, headerMatch(Map.of("X-Simulator-Scenario", "capture-decline")), decline("701"));

    assertThat(
            engine.decide(
                requestOf(
                    "merchant-1", null, Map.of("X-Simulator-Scenario", List.of("something")))))
        .isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void every_named_header_must_be_sent_before_the_rule_matches() {
    register(
        RUN,
        0,
        headerMatch(Map.of("X-Simulator-Scenario", "decline", "X-Tenant", "acme")),
        decline("701"));

    assertThat(
            engine.decide(
                requestOf("merchant-1", null, Map.of("X-Simulator-Scenario", List.of("decline")))))
        .isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void the_highest_priority_rule_decides() {
    register(RUN, 10, ScenarioMatch.anyRequest(), decline("701"));
    ScenarioRule higher = register(RUN, 20, ScenarioMatch.anyRequest(), decline("803"));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(higher.scenarioId(), "803"));
  }

  @Test
  void the_answer_does_not_depend_on_the_order_the_store_hands_the_rules_back() {
    ScenarioRule higher = register(RUN, 20, ScenarioMatch.anyRequest(), decline("803"));
    register(RUN, 10, ScenarioMatch.anyRequest(), decline("701"));

    ScenarioDecision newestFirst = engine.decide(request());
    rules.handingBackTheOldestFirst();

    assertThat(engine.decide(request()))
        .isEqualTo(newestFirst)
        .isEqualTo(new ScenarioDecision.Fail(higher.scenarioId(), "803"));
  }

  @Test
  void the_more_specific_rule_decides_when_two_rules_carry_one_priority() {
    ScenarioRule specific = register(RUN, 100, matchingMerchant("merchant-1"), decline("803"));
    register(RUN, 100, ScenarioMatch.anyRequest(), decline("701"));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(specific.scenarioId(), "803"));
  }

  @Test
  void a_header_block_counts_once_however_many_headers_it_names() {
    ScenarioRule merchantAndHeader =
        register(
            RUN,
            100,
            new ScenarioMatch(
                "merchant-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("X-Tenant", "acme")),
            decline("804"));
    ScenarioRule twoHeaders =
        register(
            RUN,
            100,
            headerMatch(Map.of("X-Simulator-Scenario", "decline", "X-Tenant", "acme")),
            decline("803"));

    assertThat(twoHeaders.match().specificity()).isEqualTo(1);
    assertThat(merchantAndHeader.match().specificity()).isEqualTo(2);
    assertThat(
            engine.decide(
                requestOf(
                    "merchant-1",
                    null,
                    Map.of(
                        "X-Simulator-Scenario", List.of("decline"), "X-Tenant", List.of("acme")))))
        .isEqualTo(new ScenarioDecision.Fail(merchantAndHeader.scenarioId(), "804"));
  }

  @Test
  void the_rule_registered_later_decides_when_priority_and_specificity_both_tie() {
    register(RUN, 100, matchingMerchant("merchant-1"), decline("803"));
    ScenarioRule later = register(RUN, 100, matchingMerchant("merchant-1"), decline("804"));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(later.scenarioId(), "804"));
    rules.handingBackTheOldestFirst();
    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(later.scenarioId(), "804"));
  }

  @Test
  void a_terminal_failure_declines_every_request_it_matches() {
    ScenarioRule rule = register(RUN, 0, ScenarioMatch.anyRequest(), decline("701"));

    for (int call = 0; call < 3; call++) {
      assertThat(engine.decide(request()))
          .isEqualTo(new ScenarioDecision.Fail(rule.scenarioId(), "701"));
    }
  }

  @Test
  void an_action_that_names_no_provider_code_is_refused_rather_than_stored() {
    assertThatThrownBy(() -> new ScenarioAction.TerminalFailure(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("providerCode");
    assertThatThrownBy(() -> new ScenarioAction.TerminalFailure(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("providerCode");
  }

  @Test
  void a_transport_fault_rule_disrupts_every_request_it_matches_without_declining_any() {
    TransportFault fault = new TransportFault.TruncatedBody();
    ScenarioRule rule =
        register(
            RUN, 0, ScenarioMatch.anyRequest(), new ScenarioAction.TransportFaultAlways(fault));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Disrupt(rule.scenarioId(), fault));
    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Disrupt(rule.scenarioId(), fault));
  }

  @Test
  void a_fault_answered_instead_of_the_operation_hands_the_provider_a_finished_answer() {
    ScenarioRule rule =
        register(
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)));

    ScenarioDecision decided = engine.decide(request());

    assertThat(decided).isInstanceOf(ScenarioDecision.Preempt.class);
    ScenarioDecision.Preempt preempted = (ScenarioDecision.Preempt) decided;
    assertThat(preempted.scenarioId()).isEqualTo(rule.scenarioId());
    assertThat(preempted.answer().httpStatus()).isEqualTo(502);
    assertThat(preempted.answer().contentType()).isEqualTo("text/plain; charset=utf-8");
    assertThat(new String(preempted.answer().body(), UTF_8))
        .startsWith("502 was answered in front of the provider.");
    assertThat(preempted.answer().fault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.SERVER_ERROR, FaultPlacement.INSTEAD_OF_THE_OPERATION));
  }

  @ParameterizedTest(name = "a rule cannot answer {0} in place of the operation that renders bytes")
  @MethodSource("theFaultsThatCannotReplaceAnOperation")
  void a_rule_naming_a_fault_that_needs_rendered_bytes_is_told_what_that_fault_does_with_them(
      String name, TransportFault fault, String whatItDoesWithThem) {
    assertThatThrownBy(() -> new ScenarioAction.TransportFaultInsteadOfOperation(fault))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "action.fault "
                + name
                + " "
                + whatItDoesWithThem
                + ", and an action applied instead of the operation renders none; only"
                + " SERVER_ERROR answers without them");
  }

  private static List<Arguments> theFaultsThatCannotReplaceAnOperation() {
    return List.of(
        Arguments.of(
            "TRUNCATED_BODY",
            new TransportFault.TruncatedBody(),
            "sends a genuine prefix of the bytes an operation rendered"),
        Arguments.of(
            "CORRUPTED_BODY",
            new TransportFault.CorruptedBody(),
            "inverts one of the bytes an operation rendered"),
        Arguments.of(
            "DROPPED_CONNECTION",
            new TransportFault.DroppedConnection(),
            "writes part of the bytes an operation rendered under their full declared length"),
        Arguments.of(
            "DELAYED_RESPONSE",
            new TransportFault.DelayedResponse(Duration.ofMillis(250)),
            "delivers the bytes an operation rendered late"));
  }

  @Test
  void a_step_that_answers_instead_of_the_operation_moves_the_story_on_like_any_other_step() {
    rules.addAll(
        List.of(
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502)),
                new ScenarioStory(
                    "capture-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "unreached")),
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.FixedSuccess(),
                new ScenarioStory("capture-retries", StoryKeying.FLOW, "unreached", null))));

    assertThat(engine.decide(requestAbout("9000001"))).isInstanceOf(ScenarioDecision.Preempt.class);
    assertThat(engine.decide(requestAbout("9000001"))).isInstanceOf(ScenarioDecision.Proceed.class);
  }

  @Test
  void a_transport_fault_of_another_test_run_disrupts_nothing_here() {
    register(
        ANOTHER_RUN,
        0,
        ScenarioMatch.anyRequest(),
        new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()));

    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void a_fixed_success_rule_stands_aside_and_names_itself_to_every_request_it_matches() {
    ScenarioRule rule =
        register(RUN, 0, ScenarioMatch.anyRequest(), new ScenarioAction.FixedSuccess());

    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.Proceed(rule.scenarioId()));
    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.Proceed(rule.scenarioId()));
  }

  @Test
  void a_fixed_success_rule_naming_one_operation_shields_it_from_a_decline_naming_none() {
    ScenarioRule shielded =
        register(
            RUN,
            100,
            ScenarioMatch.anyRequest().naming("settleSuspendedTransaction"),
            new ScenarioAction.FixedSuccess());
    ScenarioRule declined = register(RUN, 100, ScenarioMatch.anyRequest(), decline("813"));

    assertThat(engine.decide(about("settleSuspendedTransaction")))
        .isEqualTo(new ScenarioDecision.Proceed(shielded.scenarioId()));
    assertThat(engine.decide(about("refundTransaction")))
        .isEqualTo(new ScenarioDecision.Fail(declined.scenarioId(), "813"));
  }

  @Test
  void a_rule_that_shapes_a_delivery_is_never_the_decision_a_request_is_owed() {
    rules.add(
        new NewScenarioRule(
            GROW,
            RUN,
            100,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.DeliveryDropped(),
            null));

    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void a_rule_that_shapes_a_delivery_stands_aside_for_the_rule_that_answers_the_request() {
    rules.add(
        new NewScenarioRule(
            GROW,
            RUN,
            100,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.DeliveryDuplicated(),
            null));
    ScenarioRule answering = register(RUN, 0, ScenarioMatch.anyRequest(), decline("813"));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(answering.scenarioId(), "813"));
  }

  @Test
  void the_preset_vocabulary_is_the_seventeen_names_the_contract_publishes_and_no_other() {
    assertThat(ScenarioPreset.thePublishedNames())
        .isEqualTo(
            "card-add-success, card-add-failed, card-add-callback-delayed,"
                + " preauth-success-capture-success, preauth-success-capture-decline,"
                + " preauth-success-capture-timeout-then-success, capture-accepted-response-lost,"
                + " cancel-hold-success, refund-full-success, refund-partial-then-full,"
                + " psp-5xx-then-success, webhook-delayed, webhook-duplicate,"
                + " webhook-out-of-order, webhook-invalid-signature,"
                + " status-lookup-processing-then-success, status-lookup-processing-then-failed");
    assertThat(ScenarioPreset.values()).hasSize(17);
  }

  @ParameterizedTest(name = "the preset {0} is found by the name it is published under")
  @EnumSource(ScenarioPreset.class)
  void a_preset_is_found_by_its_published_name_and_never_by_the_name_the_code_calls_it(
      ScenarioPreset preset) {
    assertThat(ScenarioPreset.of(preset.publishedName())).contains(preset);
    assertThat(ScenarioPreset.of(preset.name())).isEmpty();
    assertThat(preset.publishedName()).isLowerCase().doesNotContain("_");
  }

  @Test
  void a_name_no_preset_is_published_under_names_no_preset() {
    assertThat(ScenarioPreset.of("cancel-hold-succes")).isEmpty();
    assertThat(ScenarioPreset.of("")).isEmpty();
  }

  @Test
  void a_step_that_names_no_state_decides_a_story_no_request_has_moved_yet() {
    ScenarioRule step = step("capture-retries", null, "asked", decline("813"));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(step.scenarioId(), "813"));
  }

  @Test
  void a_step_moves_the_story_on_and_the_next_request_is_decided_by_the_step_after_it() {
    ScenarioRule declines =
        step("capture-retries", ScenarioStory.INITIAL_STATE, "asked", decline("813"));
    ScenarioRule lets = step("capture-retries", "asked", null, new ScenarioAction.FixedSuccess());

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(declines.scenarioId(), "813"));
    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.Proceed(lets.scenarioId()));
    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.Proceed(lets.scenarioId()));
  }

  @Test
  void a_step_that_names_no_thenState_answers_every_request_the_same_way() {
    ScenarioRule step = step("capture-retries", null, null, decline("813"));

    for (int call = 0; call < 3; call++) {
      assertThat(engine.decide(request()))
          .isEqualTo(new ScenarioDecision.Fail(step.scenarioId(), "813"));
    }
  }

  @Test
  void
      a_story_keyed_on_the_test_run_declines_the_first_call_of_the_run_and_lets_the_next_through() {
    ScenarioRule declines =
        stepKeyedOn(
            StoryKeying.TEST_RUN,
            "hold-retries",
            ScenarioStory.INITIAL_STATE,
            "asked",
            decline("813"));
    ScenarioRule lets =
        stepKeyedOn(
            StoryKeying.TEST_RUN, "hold-retries", "asked", null, new ScenarioAction.FixedSuccess());

    assertThat(engine.decide(requestAbout("pi_sim_123")))
        .isEqualTo(new ScenarioDecision.Fail(declines.scenarioId(), "813"));
    assertThat(engine.decide(requestAbout("pi_sim_456")))
        .isEqualTo(new ScenarioDecision.Proceed(lets.scenarioId()));
    assertThat(engine.decide(requestAbout("pi_sim_789")))
        .isEqualTo(new ScenarioDecision.Proceed(lets.scenarioId()));
  }

  @Test
  void a_story_of_a_payment_and_a_story_of_the_whole_run_each_move_only_their_own_instance() {
    ScenarioRule perRun =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                100,
                ScenarioMatch.anyRequest(),
                decline("701"),
                new ScenarioStory(
                    "run-retries", StoryKeying.TEST_RUN, ScenarioStory.INITIAL_STATE, "asked")));
    ScenarioRule perPayment =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                200,
                ScenarioMatch.anyRequest(),
                decline("813"),
                new ScenarioStory(
                    "payment-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked")));

    assertThat(engine.decide(requestAbout("pi_sim_123")))
        .isEqualTo(new ScenarioDecision.Fail(perPayment.scenarioId(), "813"));
    assertThat(engine.decide(requestAbout("pi_sim_123")))
        .isEqualTo(new ScenarioDecision.Fail(perRun.scenarioId(), "701"));
    assertThat(engine.decide(requestAbout("pi_sim_456")))
        .isEqualTo(new ScenarioDecision.Fail(perPayment.scenarioId(), "813"));
    assertThat(engine.decide(requestAbout("pi_sim_456")))
        .isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void a_key_a_step_may_declare_reads_back_by_the_name_the_wire_carries_and_no_other() {
    assertThat(StoryKeying.of("FLOW")).contains(StoryKeying.FLOW);
    assertThat(StoryKeying.of("TEST_RUN")).contains(StoryKeying.TEST_RUN);
    assertThat(StoryKeying.of("flow")).isEmpty();
    assertThat(StoryKeying.theDeclarableNames()).isEqualTo("FLOW, TEST_RUN");
  }

  @Test
  void one_payment_never_takes_the_step_another_payment_of_the_same_test_run_is_owed() {
    ScenarioRule declines =
        step("capture-retries", ScenarioStory.INITIAL_STATE, "asked", decline("813"));
    ScenarioRule lets = step("capture-retries", "asked", null, new ScenarioAction.FixedSuccess());

    assertThat(engine.decide(requestAbout("pi_sim_123")))
        .isEqualTo(new ScenarioDecision.Fail(declines.scenarioId(), "813"));
    assertThat(engine.decide(requestAbout("pi_sim_456")))
        .isEqualTo(new ScenarioDecision.Fail(declines.scenarioId(), "813"));
    assertThat(engine.decide(requestAbout("pi_sim_123")))
        .isEqualTo(new ScenarioDecision.Proceed(lets.scenarioId()));
    assertThat(engine.decide(requestAbout("pi_sim_456")))
        .isEqualTo(new ScenarioDecision.Proceed(lets.scenarioId()));
  }

  @Test
  void two_calls_of_one_flow_meet_one_story_and_a_call_of_another_flow_meets_its_own() {
    ScenarioRule declines =
        step("hold-retries", ScenarioStory.INITIAL_STATE, "asked", decline("813"));
    ScenarioRule lets = step("hold-retries", "asked", null, new ScenarioAction.FixedSuccess());

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(declines.scenarioId(), "813"));
    assertThat(engine.decide(requestOf("merchant-1", Amount.of("10"), Map.of())))
        .isEqualTo(new ScenarioDecision.Proceed(lets.scenarioId()));
    assertThat(engine.decide(requestAbout("pi_sim_123")))
        .isEqualTo(new ScenarioDecision.Fail(declines.scenarioId(), "813"));
  }

  @Test
  void a_story_of_another_test_run_never_moves_this_one() {
    rules.add(
        new NewScenarioRule(
            GROW,
            ANOTHER_RUN,
            0,
            ScenarioMatch.anyRequest(),
            decline("813"),
            new ScenarioStory(
                "capture-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked")));
    ScenarioRule here =
        step("capture-retries", ScenarioStory.INITIAL_STATE, "asked", decline("701"));

    engine.decide(
        new ScenarioRequest(
            GROW,
            ANOTHER_RUN,
            ONE_FLOW,
            "merchant-1",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of()));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(here.scenarioId(), "701"));
  }

  @Test
  void a_step_that_names_the_state_it_wants_outranks_a_rule_of_one_priority_that_names_none() {
    ScenarioRule inAnyState = register(RUN, 100, ScenarioMatch.anyRequest(), decline("701"));
    ScenarioRule atTheStart =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                100,
                ScenarioMatch.anyRequest(),
                decline("813"),
                new ScenarioStory(
                    "capture-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked")));

    assertThat(atTheStart.specificity()).isEqualTo(inAnyState.specificity() + 1);
    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(atTheStart.scenarioId(), "813"));
    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(inAnyState.scenarioId(), "701"));
  }

  @Test
  void a_step_that_names_a_state_decides_over_a_step_of_its_own_story_that_names_none() {
    ScenarioRule inTheStateItNames =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                100,
                ScenarioMatch.anyRequest(),
                decline("813"),
                new ScenarioStory(
                    "capture-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked")));
    ScenarioRule inAnyState =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                100,
                ScenarioMatch.anyRequest(),
                decline("701"),
                new ScenarioStory("capture-retries", StoryKeying.FLOW, null, "settled")));

    assertThat(inTheStateItNames.specificity()).isEqualTo(inAnyState.specificity() + 1);
    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(inTheStateItNames.scenarioId(), "813"));
    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(inAnyState.scenarioId(), "701"));
  }

  @Test
  void a_step_naming_the_initial_state_fires_while_the_story_is_there_and_at_no_other_state() {
    ScenarioRule onlyAtTheStart =
        step("capture-retries", ScenarioStory.INITIAL_STATE, "asked", decline("813"));

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Fail(onlyAtTheStart.scenarioId(), "813"));
    assertThat(engine.decide(request())).isEqualTo(new ScenarioDecision.NoScenario());
  }

  @Test
  void a_step_may_send_the_story_back_to_its_start_so_the_flow_can_be_replayed() {
    ScenarioRule declines =
        step("capture-retries", ScenarioStory.INITIAL_STATE, "asked", decline("813"));
    ScenarioRule lets =
        step(
            "capture-retries",
            "asked",
            ScenarioStory.INITIAL_STATE,
            new ScenarioAction.FixedSuccess());

    List<ScenarioDecision> answers = new ArrayList<>();
    for (int call = 0; call < 6; call++) {
      answers.add(engine.decide(request()));
    }

    assertThat(answers)
        .containsExactly(
            new ScenarioDecision.Fail(declines.scenarioId(), "813"),
            new ScenarioDecision.Proceed(lets.scenarioId()),
            new ScenarioDecision.Fail(declines.scenarioId(), "813"),
            new ScenarioDecision.Proceed(lets.scenarioId()),
            new ScenarioDecision.Fail(declines.scenarioId(), "813"),
            new ScenarioDecision.Proceed(lets.scenarioId()));
  }

  @Test
  void a_step_naming_no_state_takes_itself_again_once_the_story_stands_where_it_put_it() {
    ScenarioRule everywhere =
        step("capture-retries", null, "settled", new ScenarioAction.FixedSuccess());

    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Proceed(everywhere.scenarioId()));
    assertThat(engine.decide(request()))
        .isEqualTo(new ScenarioDecision.Proceed(everywhere.scenarioId()));
    assertThat(rules.storyStatesOf(GROW, RUN, new StoryKey(StoryKeying.FLOW, ONE_FLOW)))
        .containsEntry("capture-retries", "settled");
  }

  @Test
  void a_store_that_refuses_a_step_this_decision_alone_may_take_is_told_so_rather_than_retried() {
    step("capture-retries", ScenarioStory.INITIAL_STATE, "asked", decline("813"));
    rules.losingEveryMoveWithoutTheStoryMoving();

    ScenarioRequest asked = request();

    assertThatThrownBy(() -> engine.decide(asked))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("story capture-retries");
  }

  @Test
  void a_state_that_names_nothing_is_refused_rather_than_stored() {
    assertThatThrownBy(() -> new ScenarioStory(" ", StoryKeying.FLOW, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("story");
    assertThatThrownBy(() -> new ScenarioStory("capture-retries", StoryKeying.FLOW, "", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("whenState");
    assertThatThrownBy(() -> new ScenarioStory("capture-retries", StoryKeying.FLOW, null, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("thenState");
  }

  @Test
  void a_step_that_moves_the_story_to_the_state_it_already_requires_is_refused() {
    assertThatThrownBy(
            () -> new ScenarioStory("capture-retries", StoryKeying.FLOW, "asked", "asked"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("thenState");
  }

  @Test
  void a_label_longer_than_the_column_holds_is_refused_rather_than_stored() {
    String tooLong = "s".repeat(ScenarioStory.LONGEST_NAME + 1);

    assertThatThrownBy(() -> new ScenarioStory(tooLong, StoryKeying.FLOW, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("story");
    assertThat(
            new ScenarioStory("s".repeat(ScenarioStory.LONGEST_NAME), StoryKeying.FLOW, null, null)
                .name())
        .hasSize(ScenarioStory.LONGEST_NAME);
  }

  @Test
  void a_label_is_counted_in_the_characters_the_column_counts_and_not_in_java_char_values() {
    String astral = "\uD83D\uDCB3".repeat(ScenarioStory.LONGEST_NAME);

    assertThat(new ScenarioStory(astral, StoryKeying.FLOW, null, null).name())
        .hasSize(ScenarioStory.LONGEST_NAME * 2);
    assertThatThrownBy(
            () -> new ScenarioStory(astral + "\uD83D\uDCB3", StoryKeying.FLOW, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("story");
  }

  @ParameterizedTest(name = "a label of only {0} names nothing and is refused")
  @MethodSource("theBlanksNoLabelMayBeMadeOf")
  void a_label_made_only_of_whitespace_the_reader_calls_blank_is_refused(
      String name, String blank) {
    assertThatThrownBy(() -> new ScenarioStory(blank, StoryKeying.FLOW, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("story");
  }

  @ParameterizedTest(name = "a story key of only {0} names no flow and is refused")
  @MethodSource("theBlanksNoLabelMayBeMadeOf")
  void a_request_that_names_no_flow_is_refused_before_it_can_be_decided(String name, String blank) {
    assertThatThrownBy(
            () ->
                new ScenarioRequest(
                    GROW,
                    RUN,
                    blank,
                    "merchant-1",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("storyKey");
  }

  @ParameterizedTest(name = "a story key holding {0} is refused where the provider hands it over")
  @MethodSource("theKeysNoStoryTableCanHold")
  void a_story_key_the_simulator_could_not_store_is_refused_at_the_handover_and_not_four_layers_on(
      String name, String unstorable) {
    assertThatThrownBy(() -> requestKeyedOn(unstorable))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("storyKey")
        .hasMessageContaining("store");
  }

  @ParameterizedTest(name = "a story key holding {0} never reaches the story table")
  @MethodSource("theKeysNoStoryTableCanHold")
  void a_story_key_the_simulator_could_not_store_cannot_be_made_at_all(
      String name, String unstorable) {
    assertThatThrownBy(() -> new StoryKey(StoryKeying.FLOW, unstorable))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("storyKey");
  }

  static List<Arguments> theKeysNoStoryTableCanHold() {
    return List.of(
        Arguments.of("a NUL separator", "payment:8\u0000777777772022"),
        Arguments.of("a NUL at the end", "process:900001\u0000"),
        Arguments.of("the lowest lone surrogate", "process:\uD800"),
        Arguments.of("the highest lone surrogate", "process:\uDFFF"),
        Arguments.of("a low surrogate before a high one", "process:\uDC00\uD800"),
        Arguments.of(
            "one character too many",
            StoryKey.inSpace(
                OF_A_PROCESS, "p".repeat(StoryKey.LONGEST_KEY - SPACE_AND_SEPARATOR + 1))));
  }

  @Test
  void a_story_key_of_nothing_but_whitespace_names_no_flow_and_never_reaches_the_story_table() {
    assertThatThrownBy(() -> new StoryKey(StoryKeying.FLOW, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("storyKey");
  }

  @ParameterizedTest(name = "a story key of {0} is storable and is handed over unchanged")
  @MethodSource("theKeysAStoryTableHolds")
  void a_story_key_the_simulator_can_store_is_taken_exactly_as_the_provider_spelled_it(
      String name, String storable) {
    assertThat(requestKeyedOn(storable).storyKey()).isEqualTo(storable);
    assertThat(new StoryKey(StoryKeying.FLOW, storable).value()).isEqualTo(storable);
  }

  static List<Arguments> theKeysAStoryTableHolds() {
    return List.of(
        Arguments.of("a colon separator", "payment:8:77777777202206280848"),
        Arguments.of("a counted field", "payment:8-77777777202206280848"),
        Arguments.of("a whole surrogate pair", "process:\uD83D\uDCB3"),
        Arguments.of("the character just past the surrogates", "process:\uE000"),
        Arguments.of(
            "the longest key the index takes",
            StoryKey.inSpace(
                OF_A_PROCESS, "p".repeat(StoryKey.LONGEST_KEY - SPACE_AND_SEPARATOR))));
  }

  @ParameterizedTest(name = "a story key that {0} is refused where the provider hands it over")
  @MethodSource("theKeysThatNameNoIdentifierSpace")
  void a_story_key_that_names_no_identifier_space_is_refused_at_the_handover(
      String name, String unnamed) {
    assertThatThrownBy(() -> requestKeyedOn(unnamed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("storyKey")
        .hasMessageContaining("identifier space");
  }

  @ParameterizedTest(name = "a story key that {0} never reaches the story table")
  @MethodSource("theKeysThatNameNoIdentifierSpace")
  void a_story_key_that_names_no_identifier_space_cannot_be_made_at_all(
      String name, String unnamed) {
    assertThatThrownBy(() -> new StoryKey(StoryKeying.FLOW, unnamed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("storyKey")
        .hasMessageContaining("identifier space");
  }

  static List<Arguments> theKeysThatNameNoIdentifierSpace() {
    return List.of(
        Arguments.of("hands over a bare identifier", "900001"),
        Arguments.of("counts a field where the space is named", "8-7777777720220628084800"),
        Arguments.of("opens with the separator and no name", ":900001"),
        Arguments.of("spells the space in a case no provider names one in", "Process:900001"),
        Arguments.of("names the space and nothing in it", "process:"),
        Arguments.of("names the space and nothing but whitespace in it", "process:\u2003"));
  }

  @ParameterizedTest(
      name = "a configured value spelling a key with {0} is refused where configured")
  @MethodSource("theKeysNoStoryTableCanHold")
  void a_configured_value_spelling_a_key_no_story_table_holds_is_refused_where_it_is_configured(
      String name, String unstorable) {
    assertThatThrownBy(
            () -> StoryKey.rejectWhatNoStoryKeyCanCarry("accounts[0].terminalId", unstorable))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accounts[0].terminalId")
        .hasMessageContaining("story key")
        .hasMessageContaining("store");
  }

  @ParameterizedTest(
      name = "a configured value spelling the key {1} is taken where it is configured")
  @MethodSource("theKeysAStoryTableHolds")
  void a_configured_value_spelling_a_key_the_story_table_holds_is_taken_where_it_is_configured(
      String name, String storable) {
    assertThatCode(() -> StoryKey.rejectWhatNoStoryKeyCanCarry("accounts[0].terminalId", storable))
        .doesNotThrowAnyException();
  }

  @Test
  void the_key_the_kernel_makes_for_the_whole_test_run_names_a_space_of_its_own() {
    assertThat(StoryKey.ofTestRun(RUN))
        .isEqualTo(new StoryKey(StoryKeying.TEST_RUN, "test-run:" + RUN.value()));
  }

  private static ScenarioRequest requestKeyedOn(String storyKey) {
    return new ScenarioRequest(
        GROW, RUN, storyKey, "merchant-1", null, null, null, null, null, null, null, Map.of());
  }

  static List<Arguments> theBlanksNoLabelMayBeMadeOf() {
    return List.of(
        Arguments.of("a space", " "),
        Arguments.of("a tab", "\t"),
        Arguments.of("a newline", "\n"),
        Arguments.of("a form feed", "\f"),
        Arguments.of("a vertical tab", "\u000B"),
        Arguments.of("a file separator", "\u001C"),
        Arguments.of("an ogham space mark", "\u1680"),
        Arguments.of("an em space", "\u2003"),
        Arguments.of("a line separator", "\u2028"),
        Arguments.of("a medium mathematical space", "\u205F"),
        Arguments.of("an ideographic space", "\u3000"),
        Arguments.of("a tab beside a space", "\t "));
  }

  private ScenarioRule step(
      String story, @Nullable String whenState, @Nullable String thenState, ScenarioAction action) {
    return stepKeyedOn(StoryKeying.FLOW, story, whenState, thenState, action);
  }

  private ScenarioRule stepKeyedOn(
      StoryKeying keyedOn,
      String story,
      @Nullable String whenState,
      @Nullable String thenState,
      ScenarioAction action) {
    return rules.add(
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            action,
            new ScenarioStory(story, keyedOn, whenState, thenState)));
  }

  private static ScenarioAction decline(String providerCode) {
    return new ScenarioAction.TerminalFailure(providerCode);
  }

  private static ScenarioMatch matchingMerchant(String merchantId) {
    return new ScenarioMatch(merchantId, null, null, null, null, null, null, null, null, Map.of());
  }

  private static ScenarioMatch headerMatch(Map<String, String> header) {
    return new ScenarioMatch(null, null, null, null, null, null, null, null, null, header);
  }

  private ScenarioRule register(
      TestRunId testRunId, int priority, ScenarioMatch match, ScenarioAction action) {
    return rules.add(new NewScenarioRule(GROW, testRunId, priority, match, action, null));
  }

  private static ScenarioRequest request() {
    return requestOf("merchant-1", Amount.of("1000"), Map.of());
  }

  private static ScenarioRequest requestAbout(String flow) {
    return new ScenarioRequest(
        GROW,
        RUN,
        StoryKey.inSpace(OF_A_PROCESS, flow),
        "merchant-1",
        null,
        flow,
        null,
        null,
        null,
        null,
        null,
        Map.of());
  }

  private static ScenarioRequest requestOf(
      String merchantId, Amount amount, Map<String, List<String>> headers) {
    return new ScenarioRequest(
        GROW, RUN, ONE_FLOW, merchantId, null, null, null, null, amount, null, null, headers);
  }
}
