package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlRefusal;
import dev.marcinromanowski.pspsim.control.ControlPayloads.TheWordsARefusalWasAnsweredIn;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ControlRefusalsTest {

  private static final TestRunId SEARCHED = TestRunId.of("a-named-run");

  private static final String THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER = "unknown provider";

  @Test
  void the_words_a_refusal_was_answered_in_stand_beside_the_facts_it_was_asked_with() {
    Map<String, List<String>> asked =
        ActionFacts.none().and("scope", "TEST_RUN").and("accountId", "merchant-1").asRecorded();

    Map<String, List<String>> recorded =
        TheWordsARefusalWasAnsweredIn.beside(asked, new ControlError("no such scope"));

    assertThat(recorded.keySet())
        .as(
            "the words a caller was answered with go on the row after what it was asked with,"
                + " because a tester reads the row left to right and the refusal is what they"
                + " came for")
        .containsExactly("scope", "accountId", ActionFacts.REFUSED_BECAUSE);
    assertThat(recorded).containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("no such scope"));
    assertThat(asked)
        .as("the facts a route handed over are left as they were")
        .doesNotContainKey(ActionFacts.REFUSED_BECAUSE);
  }

  @Test
  void a_refusal_naming_the_properties_it_would_not_accept_is_answered_in_those_words_as_well() {
    assertThat(
            TheWordsARefusalWasAnsweredIn.beside(
                ActionFacts.none().asRecorded(),
                new ControlRefusal(
                    "priority cannot be applied: not a number", List.of("priority"))))
        .containsEntry(
            ActionFacts.REFUSED_BECAUSE, List.of("priority cannot be applied: not a number"));
  }

  @Test
  void a_scenario_rule_that_was_never_written_is_what_the_refusal_names() {
    assertThat(ControlRefusals.noScenarioRule("grow", SEARCHED))
        .isEqualTo("no scenario rule with that scenarioId in test run a-named-run of provider grow")
        .doesNotContain(THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER);
  }

  @Test
  void a_scenario_rule_named_by_its_own_id_is_refused_by_that_id_and_not_by_the_property() {
    assertThat(ControlRefusals.noScenarioRuleCalled("grow", SEARCHED, "scn_gone"))
        .isEqualTo("no scenario rule scn_gone in test run a-named-run of provider grow")
        .doesNotContain(THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER);
    assertThat(ControlRefusals.noScenarioRuleCalled("grow", SEARCHED, "scn_gone"))
        .isNotEqualTo(ControlRefusals.noScenarioRule("grow", SEARCHED));
  }

  @Test
  void a_request_that_named_no_second_rule_is_refused_by_the_parameter_and_names_no_run() {
    assertThat(ControlRefusals.noSecondScenarioRuleWasNamed())
        .isEqualTo(
            "a comparison reads two rules and this request named one: put the other rule's"
                + " scenarioId in the against parameter")
        .contains("against")
        .doesNotContain(SEARCHED.value())
        .doesNotContain(THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER);
    assertThat(ControlRefusals.noSecondScenarioRuleWasNamed())
        .isNotEqualTo(ControlRefusals.noScenarioRuleCalled("grow", SEARCHED, ""));
  }

  @Test
  void a_hosted_page_no_process_backs_is_what_the_refusal_names() {
    assertThat(ControlRefusals.noHostedProcess("grow", SEARCHED))
        .isEqualTo(
            "no hosted payment process with that providerObjectId in test run a-named-run of"
                + " provider grow")
        .doesNotContain(THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER);
  }

  @Test
  void an_object_the_test_run_never_held_is_what_the_refusal_names() {
    assertThat(ControlRefusals.noProviderObject("grow", SEARCHED))
        .isEqualTo(
            "no provider object with that providerObjectId in test run a-named-run of provider"
                + " grow")
        .doesNotContain(THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER);
  }

  @Test
  void a_webhook_job_that_was_never_scheduled_is_what_the_refusal_names() {
    assertThat(ControlRefusals.noWebhookJob("grow", SEARCHED))
        .isEqualTo("no webhook job with that webhookJobId in test run a-named-run of provider grow")
        .doesNotContain(THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER);
  }

  @Test
  void a_send_that_scheduled_nothing_says_so_rather_than_naming_an_identifier_it_never_looked_up() {
    assertThat(ControlRefusals.nothingWasScheduled("grow", SEARCHED))
        .isEqualTo(
            "no webhook was scheduled for that providerObjectId in test run a-named-run of provider"
                + " grow")
        .doesNotContain(THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER);
  }

  @Test
  void an_account_this_test_run_does_not_configure_is_what_the_refusal_names() {
    assertThat(ControlRefusals.noAccountOfThisProvider("grow", SEARCHED))
        .isEqualTo("no account with that accountId in test run a-named-run of provider grow")
        .doesNotContain(THE_REFUSAL_OF_AN_UNKNOWN_PROVIDER);
    assertThat(ControlRefusals.noAccountOfThisProvider("grow", SEARCHED))
        .isNotEqualTo(ControlRefusals.noProviderObject("grow", SEARCHED));
  }

  @Test
  void every_refusal_names_the_test_run_that_was_searched_and_the_provider_that_was_found() {
    assertThat(everyRefusalOf("azericard", TestRunId.of("run-one")))
        .allSatisfy(
            refusal -> assertThat(refusal).endsWith("in test run run-one of provider azericard"));
  }

  @Test
  void each_missing_thing_is_refused_in_its_own_words_and_not_in_one_shared_sentence() {
    List<String> refusals = everyRefusalOf("grow", SEARCHED);

    assertThat(Set.copyOf(refusals)).hasSize(refusals.size());
  }

  @Test
  void a_refusal_names_the_identifier_it_searched_by_under_its_published_property_name() {
    assertThat(ControlRefusals.noScenarioRule("grow", SEARCHED)).contains("scenarioId");
    assertThat(ControlRefusals.noHostedProcess("grow", SEARCHED)).contains("providerObjectId");
    assertThat(ControlRefusals.noProviderObject("grow", SEARCHED)).contains("providerObjectId");
    assertThat(ControlRefusals.noWebhookJob("grow", SEARCHED)).contains("webhookJobId");
    assertThat(ControlRefusals.noAccountOfThisProvider("grow", SEARCHED)).contains("accountId");
  }

  private static List<String> everyRefusalOf(String provider, TestRunId searched) {
    return List.of(
        ControlRefusals.noScenarioRule(provider, searched),
        ControlRefusals.noScenarioRuleCalled(provider, searched, "scn_gone"),
        ControlRefusals.noHostedProcess(provider, searched),
        ControlRefusals.noProviderObject(provider, searched),
        ControlRefusals.noAccountOfThisProvider(provider, searched),
        ControlRefusals.noWebhookJob(provider, searched),
        ControlRefusals.nothingWasScheduled(provider, searched));
  }
}
