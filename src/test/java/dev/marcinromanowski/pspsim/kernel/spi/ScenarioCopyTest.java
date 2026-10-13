package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredAction;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ScenarioCopyTest {

  private static final Instant AT = Instant.parse("2026-09-25T09:00:00Z");
  private static final TestRunId RUN = TestRunId.of("copy-scenarios");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("copy-scenarios-2");
  private static final ProviderId GROW = ProviderId.of("grow");
  private static final String SETTLE = "settleSuspendedTransaction";
  private static final String LOOKUP = "getTransactionInfo";
  private static final String SIGNATURE_HEADER = "X-Signature";

  @Test
  void a_written_rule_is_read_back_as_the_declaration_that_would_write_it_again() {
    DeclaredScenario declared = ScenarioCopy.asDeclared(aRuleThatNarrowsOnEverythingItCan());

    assertThat(declared.testRunId()).isEqualTo(RUN.value());
    assertThat(declared.priority()).isEqualTo(5);
    assertThat(declared.story()).isEqualTo("a settlement that declines once");
    assertThat(declared.keyedOn()).isEqualTo(StoryKeying.FLOW.name());
    assertThat(declared.whenState()).isEqualTo("Started");
    assertThat(declared.thenState()).isEqualTo("Tried");
    assertThat(theMatchOf(declared).merchantId()).isEqualTo("merchant-1");
    assertThat(theMatchOf(declared).orderId()).isEqualTo("order-1");
    assertThat(theMatchOf(declared).providerObjectId()).isEqualTo("trx_1");
    assertThat(theMatchOf(declared).customerId()).isEqualTo("customer-1");
    assertThat(theMatchOf(declared).paymentMethodId()).isEqualTo("pm_1");
    assertThat(theMatchOf(declared).amount()).isEqualTo("10.00");
    assertThat(theMatchOf(declared).currency()).isEqualTo("ILS");
    assertThat(theMatchOf(declared).operation()).isEqualTo(SETTLE);
    assertThat(theMatchOf(declared).header())
        .containsExactly(Map.entry(SIGNATURE_HEADER, "signed"));
    assertThat(theActionOf(declared).type()).isEqualTo(ScenarioGrammar.TERMINAL_FAILURE);
    assertThat(theActionOf(declared).providerCode()).isEqualTo("101");
    assertThat(theActionOf(declared).terminalStatus()).isNull();
    assertThat(theActionOf(declared).failuresBeforeSuccess()).isNull();
  }

  @Test
  void
      a_transport_fault_and_the_delay_it_holds_a_response_for_are_read_back_as_they_were_written() {
    DeclaredScenario declared =
        ScenarioCopy.asDeclared(
            rule(
                ScenarioMatch.anyRequest().naming(SETTLE),
                new ScenarioAction.TransportFaultAlways(
                    new TransportFault.DelayedResponse(Duration.ofSeconds(3))),
                null));

    assertThat(theActionOf(declared).type()).isEqualTo(ScenarioGrammar.TRANSPORT_FAULT);
    assertThat(theActionOf(declared).fault()).isEqualTo("DELAYED_RESPONSE");
    assertThat(theActionOf(declared).delayMs()).isEqualTo(3000L);
    assertThat(theActionOf(declared).httpStatus()).isNull();
  }

  @Test
  void a_server_error_is_read_back_with_the_status_it_answers_with() {
    DeclaredScenario declared =
        ScenarioCopy.asDeclared(
            rule(
                ScenarioMatch.anyRequest().naming(SETTLE),
                new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(502)),
                null));

    assertThat(theActionOf(declared).httpStatus()).isEqualTo(502);
    assertThat(theActionOf(declared).delayMs()).isNull();
  }

  @Test
  void a_scheduled_outcome_is_read_back_with_the_wait_it_settles_after() {
    DeclaredScenario declared =
        ScenarioCopy.asDeclared(
            rule(
                ScenarioMatch.anyRequest().naming(LOOKUP),
                new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), "101"),
                null));

    assertThat(theActionOf(declared).settlesAfterMs()).isEqualTo(5000L);
    assertThat(theActionOf(declared).providerCode()).isEqualTo("101");
  }

  @Test
  void a_copy_asked_for_as_it_stands_declares_every_field_the_rule_it_copies_was_written_with() {
    DeclaredScenario copy =
        theCopyOf(aRuleThatNarrowsOnEverythingItCan(), DeclaredScenarioCopy.asItStands());

    assertThat(copy).isEqualTo(ScenarioCopy.asDeclared(aRuleThatNarrowsOnEverythingItCan()));
  }

  @Test
  void a_copy_that_edits_one_field_carries_that_field_and_leaves_every_other_one_as_it_was() {
    DeclaredScenario copy =
        theCopyOf(
            aRuleThatNarrowsOnEverythingItCan(),
            new DeclaredScenarioCopy(anEditNaming(withAmount("25.50")), null));

    assertThat(theMatchOf(copy).amount()).isEqualTo("25.50");
    assertThat(theMatchOf(copy).merchantId()).isEqualTo("merchant-1");
    assertThat(theMatchOf(copy).operation()).isEqualTo(SETTLE);
    assertThat(theActionOf(copy).providerCode()).isEqualTo("101");
    assertThat(copy.story()).isEqualTo("a settlement that declines once");
    assertThat(copy.priority()).isEqualTo(5);
  }

  @Test
  void a_copy_that_clears_one_property_drops_it_and_keeps_the_rest() {
    DeclaredScenario copy =
        theCopyOf(
            aRuleThatNarrowsOnEverythingItCan(),
            new DeclaredScenarioCopy(null, List.of("match.amount")));

    assertThat(theMatchOf(copy).amount()).isNull();
    assertThat(theMatchOf(copy).merchantId()).isEqualTo("merchant-1");
    assertThat(theMatchOf(copy).header()).containsKey(SIGNATURE_HEADER);
  }

  @Test
  void a_copy_that_clears_the_header_narrowing_drops_every_header_the_rule_named() {
    DeclaredScenario copy =
        theCopyOf(
            aRuleThatNarrowsOnEverythingItCan(),
            new DeclaredScenarioCopy(null, List.of(ScenarioCopy.MATCH_HEADER)));

    assertThat(theMatchOf(copy).header()).isEmpty();
    assertThat(theMatchOf(copy).merchantId()).isEqualTo("merchant-1");
  }

  @Test
  void a_copy_that_names_another_test_run_is_declared_for_that_run_and_not_for_the_one_it_copied() {
    DeclaredScenario copy =
        theCopyOf(
            aRuleThatNarrowsOnEverythingItCan(),
            new DeclaredScenarioCopy(
                new DeclaredScenario(ANOTHER_RUN.value(), null, null, null, null, null, null, null),
                null));

    assertThat(copy.testRunId()).isEqualTo(ANOTHER_RUN.value());
    assertThat(theMatchOf(copy).merchantId()).isEqualTo("merchant-1");
  }

  @Test
  void a_property_this_copy_both_clears_and_gives_a_value_is_refused_under_its_own_name() {
    List<ScenarioRefusal> refusals =
        theRefusalsOf(
            aRuleThatNarrowsOnEverythingItCan(),
            new DeclaredScenarioCopy(anEditNaming(withAmount("25.50")), List.of("match.amount")));

    assertThat(refusals).extracting(ScenarioRefusal::property).containsExactly("match.amount");
    assertThat(refusals.getFirst().reason()).contains("cleared and given a value");
  }

  @Test
  void a_header_both_cleared_and_given_a_value_is_refused_rather_than_written_either_way() {
    List<ScenarioRefusal> refusals =
        theRefusalsOf(
            aRuleThatNarrowsOnEverythingItCan(),
            new DeclaredScenarioCopy(
                anEditNaming(
                    new DeclaredMatch(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(SIGNATURE_HEADER, "another-secret"))),
                List.of(ScenarioCopy.MATCH_HEADER)));

    assertThat(refusals)
        .extracting(ScenarioRefusal::property)
        .containsExactly(ScenarioCopy.MATCH_HEADER);
  }

  @Test
  void a_name_no_copy_clears_is_refused_under_clear_and_the_refusal_lists_what_may_be_cleared() {
    List<ScenarioRefusal> refusals =
        theRefusalsOf(
            aRuleThatNarrowsOnEverythingItCan(),
            new DeclaredScenarioCopy(null, List.of("action.type")));

    assertThat(refusals).extracting(ScenarioRefusal::property).containsExactly("clear");
    assertThat(refusals.getFirst().reason())
        .contains("action.type is not one of them")
        .contains("match.amount")
        .contains("thenState");
    assertThat(ScenarioCopy.thePropertiesACopyMayClear())
        .contains("match.amount", ScenarioCopy.MATCH_HEADER, "story", "action.settlesAfterMs")
        .doesNotContain("action.type", "priority");
  }

  @Test
  void a_copy_that_edits_the_action_it_answers_with_carries_the_action_the_edit_names() {
    DeclaredScenario copy =
        theCopyOf(
            aRuleThatNarrowsOnEverythingItCan(),
            new DeclaredScenarioCopy(
                new DeclaredScenario(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new DeclaredAction(
                        ScenarioGrammar.FIXED_SUCCESS, null, null, null, null, null, null, null)),
                List.of("action.providerCode")));

    assertThat(theActionOf(copy).type()).isEqualTo(ScenarioGrammar.FIXED_SUCCESS);
    assertThat(theActionOf(copy).providerCode()).isNull();
  }

  private static DeclaredScenario theCopyOf(ScenarioRule source, DeclaredScenarioCopy asked) {
    ScenarioCopy.Copy copy = ScenarioCopy.of(source, asked);
    assertThat(copy).isInstanceOf(ScenarioCopy.Copy.Declared.class);
    return ((ScenarioCopy.Copy.Declared) copy).scenario();
  }

  private static List<ScenarioRefusal> theRefusalsOf(
      ScenarioRule source, DeclaredScenarioCopy asked) {
    ScenarioCopy.Copy copy = ScenarioCopy.of(source, asked);
    assertThat(copy).isInstanceOf(ScenarioCopy.Copy.Refused.class);
    return ((ScenarioCopy.Copy.Refused) copy).refusals();
  }

  private static DeclaredScenario anEditNaming(DeclaredMatch match) {
    return new DeclaredScenario(null, null, null, null, null, null, match, null);
  }

  private static DeclaredMatch withAmount(String amount) {
    return new DeclaredMatch(null, null, null, null, null, amount, null, null, null, null);
  }

  @Test
  void a_rule_read_back_as_a_declaration_names_its_headers_in_the_order_the_rule_named_them() {
    Map<String, String> named = new LinkedHashMap<>();
    for (int at = 1; at <= 12; at++) {
      named.put("X-Header-" + at, "value-" + at);
    }

    Map<String, String> read =
        theMatchOf(
                ScenarioCopy.asDeclared(
                    rule(
                        aMatchNarrowingOn(named), new ScenarioAction.TerminalFailure("101"), null)))
            .header();

    assertThat(read)
        .as(
            "a declaration is what a tester copies, diffs and reads a run back as, so the headers"
                + " it names come out in the order the rule named them; twelve names fall into one"
                + " declared order by chance once in 479001600")
        .containsExactlyEntriesOf(named);
    assertThat(read)
        .as(
            "an unordered copy would answer that sequence from the keys' hashes and a per-JVM"
                + " salt, and the type says which of the two this is in every run")
        .isInstanceOf(SequencedMap.class);
  }

  private static ScenarioMatch aMatchNarrowingOn(Map<String, String> header) {
    return new ScenarioMatch(
        "merchant-1", null, null, null, null, null, null, SETTLE, null, header);
  }

  private static DeclaredMatch theMatchOf(DeclaredScenario declared) {
    DeclaredMatch match = declared.match();
    assertThat(match).isNotNull();
    return match;
  }

  private static DeclaredAction theActionOf(DeclaredScenario declared) {
    DeclaredAction action = declared.action();
    assertThat(action).isNotNull();
    return action;
  }

  private static ScenarioRule aRuleThatNarrowsOnEverythingItCan() {
    return new ScenarioRule(
        "scn_source",
        GROW,
        RUN,
        5,
        3L,
        new ScenarioMatch(
            "merchant-1",
            "order-1",
            "trx_1",
            "customer-1",
            "pm_1",
            Amount.of("10.00"),
            "ILS",
            SETTLE,
            null,
            Map.of(SIGNATURE_HEADER, "signed")),
        new ScenarioAction.TerminalFailure("101"),
        new ScenarioStory("a settlement that declines once", StoryKeying.FLOW, "Started", "Tried"),
        AT);
  }

  private static ScenarioRule rule(
      ScenarioMatch match, ScenarioAction action, @Nullable ScenarioStory story) {
    return new ScenarioRule("scn_source", GROW, RUN, 0, 1L, match, action, story, AT);
  }
}
