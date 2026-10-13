package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TheTrailRowAnImportOnAScreenLeavesTest {

  private static final TestRunId THE_RUN_THE_SCREEN_IS_ON = TestRunId.of("the-run-a-tester-is-on");

  private static final String THE_TARGET_A_TESTER_TYPED = "another-run";

  private static final String A_TARGET_NO_NAMESPACE_CAN_HOLD = "not a run!";

  private static final String THE_PATH = "/ui/run-bundle/import";

  private static final String A_SECRET_FIELD_IT_WAS_GIVEN = "merchant-1.secretKey";

  private static final Selection.Chosen TAKING =
      new Selection.Chosen(new ProviderModuleDouble("stripe"), THE_RUN_THE_SCREEN_IS_ON);

  @Test
  void the_run_an_import_writes_into_is_the_run_its_row_belongs_under() {
    TheTrailRowAnImportOnAScreenLeaves row = rowOf(THE_TARGET_A_TESTER_TYPED);

    assertThat(row.theRunItWritesInto()).isEqualTo(THE_TARGET_A_TESTER_TYPED);
    assertThat(row.theRunItBelongsUnder()).isEqualTo(TestRunId.of(THE_TARGET_A_TESTER_TYPED));
  }

  @Test
  void a_target_box_a_tester_left_empty_writes_into_the_run_the_screen_is_on() {
    for (String emptied : List.of("", "   ")) {
      assertThat(rowOf(emptied).theRunItWritesInto())
          .as("the box held %s", emptied.isEmpty() ? "nothing" : "spaces alone")
          .isEqualTo(THE_RUN_THE_SCREEN_IS_ON.value());
    }
    TheTrailRowAnImportOnAScreenLeaves unnamed = rowOf(null);

    assertThat(unnamed.theRunItWritesInto()).isEqualTo(THE_RUN_THE_SCREEN_IS_ON.value());
    assertThat(unnamed.theRunItBelongsUnder()).isEqualTo(THE_RUN_THE_SCREEN_IS_ON);
  }

  @Test
  void a_target_typed_with_spaces_around_it_writes_into_the_run_those_spaces_hold() {
    TheTrailRowAnImportOnAScreenLeaves row = rowOf("  " + THE_TARGET_A_TESTER_TYPED + "  ");

    assertThat(row.theRunItWritesInto()).isEqualTo(THE_TARGET_A_TESTER_TYPED);
    assertThat(row.theRunItBelongsUnder()).isEqualTo(TestRunId.of(THE_TARGET_A_TESTER_TYPED));
  }

  @Test
  void a_target_no_namespace_can_hold_belongs_under_the_reserved_run_and_is_still_named() {
    TheTrailRowAnImportOnAScreenLeaves row = rowOf(A_TARGET_NO_NAMESPACE_CAN_HOLD);

    assertThat(row.theRunItBelongsUnder())
        .as(
            "no row could be filed under a name no namespace holds, and the run the screen was"
                + " used on is not where an import aimed elsewhere belongs")
        .isEqualTo(TestRunId.UNKNOWN);
    assertThat(row.theRunItWritesInto()).isEqualTo(A_TARGET_NO_NAMESPACE_CAN_HOLD);
  }

  @Test
  void the_facts_of_an_import_say_the_target_the_dry_run_and_the_secret_fields_it_was_given() {
    assertThat(
            TheTrailRowAnImportOnAScreenLeaves.of(TAKING, THE_TARGET_A_TESTER_TYPED, true, THE_PATH)
                .theFactsOfWhatWasAsked(List.of(A_SECRET_FIELD_IT_WAS_GIVEN))
                .asRecorded())
        .isEqualTo(
            Map.of(
                ActionFacts.AN_IMPORT_WRITES_NOTHING,
                List.of("true"),
                ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO,
                List.of(THE_TARGET_A_TESTER_TYPED),
                ActionFacts.THE_SECRETS_AN_IMPORT_WAS_GIVEN,
                List.of(A_SECRET_FIELD_IT_WAS_GIVEN)));
  }

  @Test
  void an_import_given_no_secret_names_none_rather_than_naming_an_empty_list_of_them() {
    assertThat(rowOf(THE_TARGET_A_TESTER_TYPED).theFactsOfWhatWasAsked(List.of()).asRecorded())
        .isEqualTo(
            Map.of(
                ActionFacts.AN_IMPORT_WRITES_NOTHING,
                List.of("false"),
                ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO,
                List.of(THE_TARGET_A_TESTER_TYPED)));
  }

  @Test
  void a_row_an_import_was_turned_away_in_carries_the_words_it_was_turned_away_in() {
    TheTrailRowAnImportOnAScreenLeaves row = rowOf(THE_TARGET_A_TESTER_TYPED);

    AuditedAction turnedAway =
        row.turnedAway(
            RunBundleScreen.theRefusalOfABundleItDidNotTakeBack(),
            row.theFactsOfWhatWasAsked(List.of()),
            "the words a tester reads on the screen");

    assertThat(turnedAway.facts())
        .containsEntry(
            ActionFacts.REFUSED_BECAUSE, List.of("the words a tester reads on the screen"))
        .containsEntry(
            ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO, List.of(THE_TARGET_A_TESTER_TYPED));
    assertThat(turnedAway.testRunId()).isEqualTo(TestRunId.of(THE_TARGET_A_TESTER_TYPED));
    assertThat(turnedAway.provider().value()).isEqualTo("stripe");
    assertThat(turnedAway.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(turnedAway.method()).isEqualTo("POST");
    assertThat(turnedAway.path()).isEqualTo(THE_PATH);
    assertThat(turnedAway.httpStatus())
        .as("the status the admission's own refusal reports")
        .isEqualTo(RunBundleScreen.theRefusalOfABundleItDidNotTakeBack().status().httpStatus());
  }

  @Test
  void a_row_an_applied_import_leaves_carries_the_answer_the_screen_gave_the_tester() {
    TheTrailRowAnImportOnAScreenLeaves row = rowOf(THE_TARGET_A_TESTER_TYPED);

    AuditedAction applied =
        row.applied(row.theFactsOfWhatWasAsked(List.of()).andCount("created", 2));

    assertThat(applied.httpStatus())
        .as("a screen answers an import it applied by drawing the report, which is a 200")
        .isEqualTo(200);
    assertThat(applied.facts()).containsEntry("created", List.of("2"));
    assertThat(applied.testRunId()).isEqualTo(TestRunId.of(THE_TARGET_A_TESTER_TYPED));
    assertThat(applied.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(applied.method()).isEqualTo("POST");
    assertThat(applied.path()).isEqualTo(THE_PATH);
  }

  private static TheTrailRowAnImportOnAScreenLeaves rowOf(String into) {
    return TheTrailRowAnImportOnAScreenLeaves.of(TAKING, into, false, THE_PATH);
  }
}
