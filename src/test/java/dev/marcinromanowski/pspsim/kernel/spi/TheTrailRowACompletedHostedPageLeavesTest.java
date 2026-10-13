package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TheTrailRowACompletedHostedPageLeavesTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final TestRunId RUN = TestRunId.of("a-run");

  private static final String A_PROCESS = "prc_1";

  private static final String THE_ROUTE_A_SUITE_COMPLETES_ONE_ON =
      "/__simulator/aprovider/hosted/prc_1/complete";

  private static final String THE_ROUTE_A_SCREEN_COMPLETES_ONE_ON = "/ui/objects/complete";

  private static final int THE_STATUS_A_SUITE_IS_ANSWERED_WITH = 200;

  private static final int THE_STATUS_A_SCREEN_IS_ANSWERED_WITH = 302;

  @Test
  void a_completed_page_is_recorded_with_what_the_payer_met_the_card_they_named_and_what_it_made() {
    AuditedAction row =
        TheTrailRowACompletedHostedPageLeaves.of(
            APROVIDER,
            THE_ROUTE_A_SUITE_COMPLETES_ONE_ON,
            asked(PayerOutcome.PAID, "seeded-visa", ObjectOrigin.CONTROL_API),
            new HostedCompletionOutcome.Completed(
                A_PROCESS, "236260", PayerOutcome.PAID, List.of("whjob_1")),
            THE_STATUS_A_SUITE_IS_ANSWERED_WITH);

    assertThat(row.provider()).isEqualTo(APROVIDER);
    assertThat(row.testRunId()).isEqualTo(RUN);
    assertThat(row.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(row.method()).isEqualTo("POST");
    assertThat(row.path()).isEqualTo(THE_ROUTE_A_SUITE_COMPLETES_ONE_ON);
    assertThat(row.httpStatus()).isEqualTo(200);
    assertThat(row.providerObjectId()).isEqualTo(A_PROCESS);
    assertThat(row.facts())
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.PROVIDER_OBJECT_ID, List.of(A_PROCESS))
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.OUTCOME, List.of("PAID"))
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.CARD, List.of("seeded-visa"))
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.CREATED_OBJECT_ID, List.of("236260"))
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.WEBHOOK_JOB_IDS, List.of("whjob_1"));
  }

  @Test
  void one_screen_and_one_suite_completing_the_same_page_are_recorded_the_same_way_throughout() {
    HostedCompletionOutcome.Completed completed =
        new HostedCompletionOutcome.Completed(
            A_PROCESS, "236260", PayerOutcome.PAID, List.of("whjob_1"));

    AuditedAction clicked =
        TheTrailRowACompletedHostedPageLeaves.of(
            APROVIDER,
            THE_ROUTE_A_SCREEN_COMPLETES_ONE_ON,
            asked(PayerOutcome.PAID, "seeded-visa", ObjectOrigin.UI),
            completed,
            THE_STATUS_A_SCREEN_IS_ANSWERED_WITH);
    AuditedAction called =
        TheTrailRowACompletedHostedPageLeaves.of(
            APROVIDER,
            THE_ROUTE_A_SUITE_COMPLETES_ONE_ON,
            asked(PayerOutcome.PAID, "seeded-visa", ObjectOrigin.CONTROL_API),
            completed,
            THE_STATUS_A_SUITE_IS_ANSWERED_WITH);

    assertThat(clicked.facts())
        .as(
            "a tester compares the completion they clicked against the same completion their"
                + " suite made, and one composer writes both, so the facts of one row are the"
                + " facts of the other whatever either surface knows")
        .isEqualTo(called.facts())
        .containsKeys(
            TheTrailRowACompletedHostedPageLeaves.CARD,
            TheTrailRowACompletedHostedPageLeaves.CREATED_OBJECT_ID);
    assertThat(clicked.providerObjectId()).isEqualTo(called.providerObjectId());
    assertThat(clicked.httpStatus())
        .as(
            "the status a surface answered with is the status its row carries, which is the one"
                + " this composer was handed and not one of its own choosing")
        .isEqualTo(THE_STATUS_A_SCREEN_IS_ANSWERED_WITH)
        .isNotEqualTo(called.httpStatus());
  }

  @Test
  void a_completion_this_provider_refused_is_recorded_with_the_words_it_refused_in() {
    AuditedAction row =
        rowOf(new HostedCompletionOutcome.RefusedByTheProvider("this page is already completed"));

    assertThat(row.httpStatus()).isEqualTo(422);
    assertThat(row.refusal())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                HostedCompletionOutcome.THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM));
    assertThat(row.facts())
        .containsEntry(
            TheTrailRowACompletedHostedPageLeaves.REFUSED_BECAUSE,
            List.of("this page is already completed"));
  }

  @Test
  void a_completion_this_provider_offers_none_of_is_recorded_with_what_it_has_instead() {
    AuditedAction row =
        rowOf(new HostedCompletionOutcome.NotOffered("this provider hosts no page for a payer"));

    assertThat(row.httpStatus()).isEqualTo(422);
    assertThat(row.facts())
        .containsEntry(
            TheTrailRowACompletedHostedPageLeaves.REFUSED_BECAUSE,
            List.of("this provider hosts no page for a payer"));
  }

  @Test
  void a_completion_of_a_page_no_object_of_this_run_backs_is_recorded_as_not_found() {
    AuditedAction row = rowOf(new HostedCompletionOutcome.NoSuchObject());

    assertThat(row.httpStatus()).isEqualTo(404);
    assertThat(row.refusal())
        .isEqualTo(RefusalSubject.noObjectAnsweredTo(HostedCompletionOutcome.PROVIDER_OBJECT_ID));
    assertThat(row.facts())
        .as(
            "the page a completion named is on the row as a fact whether anything answered to it"
                + " or not, because the column a trail row names its object in is emptied for"
                + " every refusal naming no object, so the fact is the only place the page a"
                + " tester asked about survives; and the row says why it was turned away in"
                + " words both surfaces file alike, though each says its own thing to its own"
                + " reader")
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.PROVIDER_OBJECT_ID, List.of(A_PROCESS))
        .containsEntry(
            TheTrailRowACompletedHostedPageLeaves.REFUSED_BECAUSE,
            List.of(TheTrailRowACompletedHostedPageLeaves.THIS_RUN_HOLDS_NO_SUCH_HOSTED_PAGE));
  }

  @Test
  void a_payer_who_named_no_card_leaves_no_card_on_the_row_rather_than_an_empty_one() {
    AuditedAction row =
        TheTrailRowACompletedHostedPageLeaves.of(
            APROVIDER,
            THE_ROUTE_A_SUITE_COMPLETES_ONE_ON,
            asked(PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API),
            new HostedCompletionOutcome.Completed(
                A_PROCESS, null, PayerOutcome.ABANDONED, List.of()),
            THE_STATUS_A_SUITE_IS_ANSWERED_WITH);

    assertThat(row.facts())
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.OUTCOME, List.of("ABANDONED"))
        .doesNotContainKey(TheTrailRowACompletedHostedPageLeaves.CARD)
        .doesNotContainKey(TheTrailRowACompletedHostedPageLeaves.CREATED_OBJECT_ID)
        .doesNotContainKey(TheTrailRowACompletedHostedPageLeaves.WEBHOOK_JOB_IDS);
  }

  @Test
  void
      a_completion_turned_away_before_the_provider_is_recorded_with_what_it_was_turned_away_with() {
    AuditedAction row =
        TheTrailRowACompletedHostedPageLeaves.ofACompletionTurnedAwayBeforeTheProviderWasReached(
            APROVIDER,
            TestRunId.UNKNOWN,
            ObjectOrigin.UI,
            THE_ROUTE_A_SCREEN_COMPLETES_ONE_ON,
            A_PROCESS,
            "PAID",
            "seeded-visa",
            HostedCompletionRequest.A_CARD_IS_REQUIRED);

    assertThat(row.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(row.httpStatus()).isEqualTo(400);
    assertThat(row.verdict()).isInstanceOf(ActionVerdict.Refused.class);
    assertThat(row.facts())
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.PROVIDER_OBJECT_ID, List.of(A_PROCESS))
        .containsEntry(TheTrailRowACompletedHostedPageLeaves.OUTCOME, List.of("PAID"))
        .containsEntry(
            TheTrailRowACompletedHostedPageLeaves.REFUSED_BECAUSE,
            List.of(HostedCompletionRequest.A_CARD_IS_REQUIRED));
  }

  private static AuditedAction rowOf(HostedCompletionOutcome outcome) {
    return TheTrailRowACompletedHostedPageLeaves.of(
        APROVIDER,
        THE_ROUTE_A_SUITE_COMPLETES_ONE_ON,
        asked(PayerOutcome.PAID, "seeded-visa", ObjectOrigin.CONTROL_API),
        outcome,
        THE_STATUS_A_SUITE_IS_ANSWERED_WITH);
  }

  private static HostedCompletionRequest asked(
      PayerOutcome outcome, @Nullable String card, ObjectOrigin askedBy) {
    return new HostedCompletionRequest(RUN, A_PROCESS, outcome, card, askedBy);
  }
}
