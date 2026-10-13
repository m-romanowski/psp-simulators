package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadRequestTest {

  private static final TestRunId RUN = TestRunId.of("read-request");

  private static final String ACCOUNT = "merchant-1";

  private static final String A_RETRACE = "aRetrace";

  @Test
  void a_read_request_strips_what_was_typed_and_drops_what_was_left_empty() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("myOwnIdentifier", " 1001 ");
    typed.put("myOwnGroup", "   ");

    ReadRequest request =
        ReadRequest.of(RUN, " " + ACCOUNT + " ", " " + A_RETRACE, typed, ObjectOrigin.UI);

    assertThat(request.accountId()).isEqualTo(ACCOUNT);
    assertThat(request.readNamed()).isEqualTo(A_RETRACE);
    assertThat(request.values()).containsExactly(Map.entry("myOwnIdentifier", "1001"));
  }

  @Test
  void a_read_request_naming_no_account_is_refused() {
    assertThatThrownBy(() -> ReadRequest.of(RUN, " ", A_RETRACE, Map.of(), ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a read is asked on one of the accounts this test run configures");
  }

  @Test
  void a_read_request_naming_no_question_is_refused() {
    assertThatThrownBy(() -> ReadRequest.of(RUN, ACCOUNT, " ", Map.of(), ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("answers one of the questions the provider declares");
  }

  @Test
  void a_request_that_arrived_on_the_providers_own_route_never_reaches_this_port() {
    assertThatThrownBy(
            () -> ReadRequest.of(RUN, ACCOUNT, A_RETRACE, Map.of(), ObjectOrigin.PROVIDER_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a client's request reaches a read through the provider's own route");
  }

  @Test
  void a_scheduled_job_asks_this_provider_nothing() {
    assertThatThrownBy(
            () -> ReadRequest.of(RUN, ACCOUNT, A_RETRACE, Map.of(), ObjectOrigin.SCHEDULED_JOB))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no scheduled job performs a read through this port");
  }

  @Test
  void a_value_read_before_the_core_checked_it_was_required_is_a_defect_of_the_module() {
    ReadRequest request = ReadRequest.of(RUN, ACCOUNT, A_RETRACE, Map.of(), ObjectOrigin.UI);

    assertThatThrownBy(() -> request.required("myOwnIdentifier"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("before the core had checked");
  }

  @Test
  void a_row_a_read_answered_carries_the_facts_the_provider_answered_about_it() {
    AnsweredRow row = new AnsweredRow("100003", List.of(new AnsweredRow.Fact("status", "paid")));

    assertThat(row.providerObjectId()).isEqualTo("100003");
    assertThat(row.facts()).containsExactly(new AnsweredRow.Fact("status", "paid"));
  }

  @Test
  void a_row_naming_an_object_and_no_fact_is_the_object_the_provider_answered_and_stands() {
    assertThat(new AnsweredRow("100003", List.of()).providerObjectId())
        .as(
            "a row that names the object it is about says that much, and a read that found"
                + " nothing answers no row at all rather than an empty one")
        .isEqualTo("100003");
  }

  @Test
  void a_row_naming_no_object_and_carrying_no_fact_is_refused_because_it_prints_as_a_blank_block() {
    assertThatThrownBy(() -> new AnsweredRow(null, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prints as a blank block on a screen");
  }

  @Test
  void a_fact_carries_the_one_value_a_provider_answered_under_the_name_it_answered_it_under() {
    assertThat(new AnsweredRow.Fact("status", "paid").value())
        .isEqualTo(new AnsweredRow.AnsweredValue.TheValueItAnswered("paid"));
  }

  @Test
  void a_member_a_provider_answered_with_no_value_a_list_or_an_object_is_each_its_own_answer() {
    AnsweredRow.Fact nested =
        new AnsweredRow.Fact(
            "events",
            new AnsweredRow.AnsweredValue.TheElementsUnderIt(
                List.of(
                    new AnsweredRow.AnsweredValue.TheMembersUnderIt(
                        List.of(new AnsweredRow.Fact("type", "SALE"))))));

    assertThat(nested.value())
        .as(
            "a provider that answers a list of objects is read as that list of objects, because a"
                + " name this port invented for a member of one is a name the provider answers"
                + " nothing under")
        .isEqualTo(
            new AnsweredRow.AnsweredValue.TheElementsUnderIt(
                List.of(
                    new AnsweredRow.AnsweredValue.TheMembersUnderIt(
                        List.of(new AnsweredRow.Fact("type", "SALE"))))));
    assertThat(new AnsweredRow.AnsweredValue.NoValueAtAll())
        .isEqualTo(new AnsweredRow.AnsweredValue.NoValueAtAll());
    assertThat(new AnsweredRow.AnsweredValue.TheElementsUnderIt(List.of()).elements()).isEmpty();
    assertThat(new AnsweredRow.AnsweredValue.TheMembersUnderIt(List.of()).members()).isEmpty();
  }

  @Test
  void a_fact_with_no_name_is_refused_because_a_screen_prints_the_providers_own_name_for_it() {
    assertThatThrownBy(() -> new AnsweredRow.Fact(" ", "paid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must carry the name its provider answered it under");
  }

  @Test
  void the_rule_that_decided_a_read_is_the_one_each_outcome_carries() {
    assertThat(new ReadOutcome.Answered(List.of(), "rule-1").theRuleThatDecidedIt())
        .isEqualTo("rule-1");
    assertThat(
            new ReadOutcome.RefusedByTheProvider("140 unknown token", "rule-2")
                .theRuleThatDecidedIt())
        .isEqualTo("rule-2");
    assertThat(new ReadOutcome.AnsweredByAScenarioInstead("rule-3").theRuleThatDecidedIt())
        .isEqualTo("rule-3");
    assertThat(new ReadOutcome.NotOffered("it publishes none").theRuleThatDecidedIt()).isNull();
    assertThat(new ReadOutcome.NoSuchAccount().theRuleThatDecidedIt()).isNull();
  }

  @Test
  void an_answer_with_no_row_is_the_one_that_answered_no_row_at_all() {
    assertThat(new ReadOutcome.Answered(List.of()).itAnsweredNoRowAtAll()).isTrue();
    assertThat(
            new ReadOutcome.Answered(
                    List.of(new AnsweredRow(null, List.of(new AnsweredRow.Fact("status", "paid")))))
                .itAnsweredNoRowAtAll())
        .isFalse();
  }

  @Test
  void what_a_provider_answered_about_the_query_is_no_row_and_never_counts_as_one_found() {
    ReadOutcome.Answered answered =
        new ReadOutcome.Answered(
            List.of(),
            List.of(new AnsweredRow.Fact("hasMore", "false"), new AnsweredRow.Fact("page", "1")),
            "rule-5");

    assertThat(answered.itAnsweredNoRowAtAll())
        .as(
            "a provider that echoes the window and the page it applied answers that beside the"
                + " payments, and a screen reading it as a payment would leave the sentence that"
                + " says what an empty answer means unreachable")
        .isTrue();
    assertThat(answered.whatItAnsweredAboutTheQuery())
        .extracting(AnsweredRow.Fact::asTheProviderNamesIt)
        .containsExactly("hasMore", "page");
    assertThat(new ReadOutcome.Answered(List.of()).whatItAnsweredAboutTheQuery())
        .as("a provider that answers nothing about the query answers no fact about it")
        .isEmpty();
    assertThat(new ReadOutcome.Answered(List.of(), "rule-6").whatItAnsweredAboutTheQuery())
        .isEmpty();
  }

  @Test
  void what_a_provider_answered_is_held_as_the_answer_it_gave_and_not_as_a_list_it_still_owns() {
    List<AnsweredRow.Fact> members = new ArrayList<>(List.of(new AnsweredRow.Fact("type", "SALE")));
    List<AnsweredRow.AnsweredValue> elements =
        new ArrayList<>(List.of(new AnsweredRow.AnsweredValue.NoValueAtAll()));
    List<AnsweredRow.Fact> aboutTheQuery =
        new ArrayList<>(List.of(new AnsweredRow.Fact("page", "1")));
    AnsweredRow.AnsweredValue.TheMembersUnderIt nested =
        new AnsweredRow.AnsweredValue.TheMembersUnderIt(members);
    AnsweredRow.AnsweredValue.TheElementsUnderIt listed =
        new AnsweredRow.AnsweredValue.TheElementsUnderIt(elements);
    ReadOutcome.Answered answered = new ReadOutcome.Answered(List.of(), aboutTheQuery, null);

    members.clear();
    elements.clear();
    aboutTheQuery.clear();

    assertThat(nested.members())
        .as("what a provider answered under a name is read back as it answered it")
        .hasSize(1);
    assertThat(listed.elements()).hasSize(1);
    assertThat(answered.whatItAnsweredAboutTheQuery()).hasSize(1);
  }

  @Test
  void a_refusal_with_no_words_in_it_is_refused() {
    assertThatThrownBy(() -> new ReadOutcome.RefusedByTheProvider(" ", "rule-4"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("an empty refusal tells a tester only that something failed");
  }

  @Test
  void an_absence_with_nothing_said_in_its_place_is_refused() {
    assertThatThrownBy(() -> new ReadOutcome.NotOffered(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say in its own words what its contract has in place of one");
  }
}
