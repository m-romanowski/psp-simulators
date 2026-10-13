package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionFactsTest {

  @Test
  void a_reset_says_what_it_destroyed_so_a_tester_reading_one_row_knows_what_was_there() {
    Map<String, List<String>> facts =
        ActionFacts.none()
            .and("scope", "TEST_RUN")
            .andCount("clearedObjects", 2)
            .andCount("clearedAuditRecords", 6)
            .asRecorded();

    assertThat(facts)
        .containsEntry("scope", List.of("TEST_RUN"))
        .containsEntry("clearedObjects", List.of("2"))
        .containsEntry("clearedAuditRecords", List.of("6"));
  }

  @Test
  void a_fact_an_action_did_not_carry_is_left_out_rather_than_recorded_as_nothing() {
    Map<String, List<String>> facts =
        ActionFacts.none().and("preset", null).and("operation", "SEND").asRecorded();

    assertThat(facts).containsExactly(Map.entry("operation", List.of("SEND")));
  }

  @Test
  void a_count_of_nothing_is_still_recorded_because_zero_is_an_answer_and_absence_is_not() {
    assertThat(ActionFacts.none().andCount("attempted", 0).asRecorded())
        .containsEntry("attempted", List.of("0"));
  }

  @Test
  void an_action_that_wrote_several_rules_lists_every_one_of_them_under_one_name() {
    Map<String, List<String>> facts =
        ActionFacts.none().andEach("scenarioIds", List.of("scn_1", "scn_2")).asRecorded();

    assertThat(facts).containsEntry("scenarioIds", List.of("scn_1", "scn_2"));
  }

  @Test
  void an_action_that_wrote_no_rule_leaves_the_list_out_rather_than_recording_an_empty_one() {
    assertThat(ActionFacts.none().andEach("scenarioIds", List.of()).asRecorded()).isEmpty();
  }

  @Test
  void an_action_that_did_nothing_worth_saying_records_no_facts_at_all() {
    assertThat(ActionFacts.none().asRecorded()).isEmpty();
  }

  @Test
  void the_facts_are_read_back_in_the_order_the_action_stated_them() {
    Map<String, List<String>> facts =
        ActionFacts.none()
            .and("operation", "SEND")
            .and("eventType", "payment_process.completed")
            .andEach("webhookJobIds", List.of("whjob_1"))
            .asRecorded();

    assertThat(facts.keySet()).containsExactly("operation", "eventType", "webhookJobIds");
  }
}
