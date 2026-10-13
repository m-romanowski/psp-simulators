package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TheTrailRowAReadLeavesTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final TestRunId RUN = TestRunId.of("a-run");

  private static final String ACCOUNT = "merchant-1";

  private static final String A_RETRACE = "aRetrace";

  private static final String THE_ROUTE = "/__simulator/aprovider/reads/answer";

  private static final ReadRequest ASKED =
      ReadRequest.of(
          RUN, ACCOUNT, A_RETRACE, Map.of("cardToken", "tok_1"), ObjectOrigin.CONTROL_API);

  @Test
  void a_read_a_provider_answered_is_recorded_as_a_read_this_surface_carried_out() {
    AuditedAction row =
        TheTrailRowAReadLeaves.of(
            APROVIDER,
            THE_ROUTE,
            ASKED,
            new ReadOutcome.Answered(List.of(aRowAbout("txn_1"), aRowAbout("txn_2"))));

    assertThat(row.provider()).isEqualTo(APROVIDER);
    assertThat(row.testRunId()).isEqualTo(RUN);
    assertThat(row.origin())
        .as("the surface a read was asked on is the one the request itself names")
        .isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(row.method()).isEqualTo("GET");
    assertThat(row.path()).isEqualTo(THE_ROUTE);
    assertThat(row.httpStatus())
        .as(
            "the status a read that was answered carries, read as the number it is rather than"
                + " from the constant this row took it from")
        .isEqualTo(200);
    assertThat(row.accountId()).isEqualTo(ACCOUNT);
    assertThat(row.facts())
        .containsEntry(TheTrailRowAReadLeaves.READ_NAMED, List.of(A_RETRACE))
        .containsEntry(TheTrailRowAReadLeaves.ACCOUNT_ID, List.of(ACCOUNT))
        .containsEntry("cardToken", List.of("tok_1"))
        .containsEntry(TheTrailRowAReadLeaves.ROWS_ANSWERED, List.of("2"));
  }

  @Test
  void a_read_that_answered_one_object_names_it_and_one_that_answered_several_names_none() {
    assertThat(
            TheTrailRowAReadLeaves.of(
                    APROVIDER,
                    THE_ROUTE,
                    ASKED,
                    new ReadOutcome.Answered(List.of(aRowAbout("txn_1"))))
                .providerObjectId())
        .as(
            "a trail row carries one object a tester opens from it, so a read that answered"
                + " exactly one names that one")
        .isEqualTo("txn_1");
    assertThat(
            TheTrailRowAReadLeaves.of(
                    APROVIDER,
                    THE_ROUTE,
                    ASKED,
                    new ReadOutcome.Answered(List.of(aRowAbout("txn_1"), aRowAbout("txn_2"))))
                .providerObjectId())
        .as("a read that answered several objects names none of them in particular")
        .isNull();
    assertThat(
            TheTrailRowAReadLeaves.of(
                    APROVIDER, THE_ROUTE, ASKED, new ReadOutcome.Answered(List.of()))
                .providerObjectId())
        .isNull();
  }

  @Test
  void the_rule_of_this_run_that_decided_a_read_is_the_one_the_row_says_decided_it() {
    assertThat(
            TheTrailRowAReadLeaves.of(
                    APROVIDER,
                    THE_ROUTE,
                    ASKED,
                    new ReadOutcome.Answered(List.of(aRowAbout("txn_1")), "scn_1"))
                .scenarioId())
        .as(
            "a rule that answered a read moved the story it stands on, and the trail is where a"
                + " tester finds which rule moved it once the answer is off the screen")
        .isEqualTo("scn_1");
    assertThat(
            TheTrailRowAReadLeaves.of(
                    APROVIDER,
                    THE_ROUTE,
                    ASKED,
                    new ReadOutcome.AnsweredByAScenarioInstead("scn_2"))
                .scenarioId())
        .isEqualTo("scn_2");
    assertThat(
            TheTrailRowAReadLeaves.of(
                    APROVIDER,
                    THE_ROUTE,
                    ASKED,
                    new ReadOutcome.RefusedByTheProvider("err.id 140", "scn_3"))
                .scenarioId())
        .isEqualTo("scn_3");
  }

  @Test
  void a_read_a_provider_refused_is_recorded_with_the_words_it_refused_in() {
    AuditedAction row =
        TheTrailRowAReadLeaves.of(
            APROVIDER, THE_ROUTE, ASKED, new ReadOutcome.RefusedByTheProvider("err.id 140", null));

    assertThat(row.httpStatus()).isEqualTo(422);
    assertThat(row.refusal())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                TheTrailRowAReadLeaves.THE_ANSWER_IT_ASKED_THIS_PROVIDER_FOR));
    assertThat(row.facts())
        .containsEntry(TheTrailRowAReadLeaves.REFUSED_BECAUSE, List.of("err.id 140"));
  }

  @Test
  void a_read_a_scenario_answered_instead_is_recorded_as_one_this_surface_turned_away() {
    AuditedAction row =
        TheTrailRowAReadLeaves.of(
            APROVIDER, THE_ROUTE, ASKED, new ReadOutcome.AnsweredByAScenarioInstead("scn_2"));

    assertThat(row.httpStatus()).isEqualTo(422);
    assertThat(row.facts())
        .as(
            "the rule stands in the column every row of this trail names its rule in, and the"
                + " words say what came back instead of the provider's answer, because a row"
                + " that says only which rule fired leaves a tester to work out what it did")
        .containsEntry(
            TheTrailRowAReadLeaves.REFUSED_BECAUSE,
            List.of(TheTrailRowAReadLeaves.A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD + "scn_2"));
  }

  @Test
  void a_read_this_provider_does_not_offer_is_recorded_with_what_it_has_instead() {
    AuditedAction row =
        TheTrailRowAReadLeaves.of(
            APROVIDER,
            THE_ROUTE,
            ASKED,
            new ReadOutcome.NotOffered("this module answers no read of that kind"));

    assertThat(row.httpStatus()).isEqualTo(400);
    assertThat(row.facts())
        .containsEntry(
            TheTrailRowAReadLeaves.REFUSED_BECAUSE,
            List.of("this module answers no read of that kind"));
  }

  @Test
  void a_read_asked_on_an_account_this_run_no_longer_configures_is_recorded_as_not_found() {
    AuditedAction row =
        TheTrailRowAReadLeaves.of(APROVIDER, THE_ROUTE, ASKED, new ReadOutcome.NoSuchAccount());

    assertThat(row.httpStatus()).isEqualTo(404);
    assertThat(row.facts())
        .as(
            "a row a tester finds after a reset took the account away mid-run says why the"
                + " question was turned away, because a not-found status leaves them to guess"
                + " between an account that is gone and an object the provider holds none of")
        .containsEntry(
            TheTrailRowAReadLeaves.REFUSED_BECAUSE,
            List.of(TheTrailRowAReadLeaves.THIS_RUN_CONFIGURES_NO_SUCH_ACCOUNT));
  }

  @Test
  void a_question_turned_away_before_the_provider_is_recorded_with_the_refusal_it_was_sent() {
    AuditedAction row =
        TheTrailRowAReadLeaves.ofAQuestionTurnedAwayBeforeTheProviderWasReached(
            APROVIDER,
            TestRunId.UNKNOWN,
            ObjectOrigin.UI,
            "/ui/reads/ask",
            A_RETRACE,
            ACCOUNT,
            "nothing is sent for sum");

    assertThat(row.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(row.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(row.method()).isEqualTo("GET");
    assertThat(row.httpStatus()).isEqualTo(400);
    assertThat(row.accountId()).isEqualTo(ACCOUNT);
    assertThat(row.providerObjectId()).isNull();
    assertThat(row.verdict()).isInstanceOf(ActionVerdict.Refused.class);
    assertThat(row.facts())
        .containsEntry(TheTrailRowAReadLeaves.READ_NAMED, List.of(A_RETRACE))
        .containsEntry(TheTrailRowAReadLeaves.ACCOUNT_ID, List.of(ACCOUNT))
        .containsEntry(TheTrailRowAReadLeaves.REFUSED_BECAUSE, List.of("nothing is sent for sum"));
  }

  @Test
  void a_question_that_named_neither_a_read_nor_an_account_is_still_recorded() {
    AuditedAction row =
        TheTrailRowAReadLeaves.ofAQuestionTurnedAwayBeforeTheProviderWasReached(
            APROVIDER,
            RUN,
            ObjectOrigin.CONTROL_API,
            THE_ROUTE,
            null,
            null,
            "a read answers one of the questions the provider declares");

    assertThat(row.facts())
        .as(
            "a request that named nothing is the one a tester most needs to find on the trail,"
                + " and the row says what it was turned away with")
        .containsOnlyKeys(TheTrailRowAReadLeaves.REFUSED_BECAUSE);
  }

  private static AnsweredRow aRowAbout(String providerObjectId) {
    return new AnsweredRow(providerObjectId, List.of(new AnsweredRow.Fact("status", "APPROVED")));
  }
}
