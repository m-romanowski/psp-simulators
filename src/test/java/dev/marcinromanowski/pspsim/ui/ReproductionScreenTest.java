package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import dev.marcinromanowski.pspsim.kernel.spi.ReproductionAdmission;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReproductionScreenTest {

  private static final String A_CARRIED_ID = "tok_carried";

  @Test
  void an_empty_remapping_box_asks_for_no_remapping_at_all() {
    assertThat(ReproductionScreen.theRemappingTypedInto(null))
        .isInstanceOfSatisfying(
            ReproductionScreen.Remapping.Asked.class,
            asked -> assertThat(asked.byCarriedId()).isEmpty());
    assertThat(ReproductionScreen.theRemappingTypedInto("  \n \n"))
        .isInstanceOfSatisfying(
            ReproductionScreen.Remapping.Asked.class,
            asked -> assertThat(asked.byCarriedId()).isEmpty());
  }

  @Test
  void a_line_names_the_id_the_bundle_carries_and_the_id_to_use_here() {
    assertThat(ReproductionScreen.theRemappingTypedInto("tok_carried = tok_free \n tok_b=tok_c"))
        .isInstanceOfSatisfying(
            ReproductionScreen.Remapping.Asked.class,
            asked ->
                assertThat(asked.byCarriedId())
                    .containsExactlyInAnyOrderEntriesOf(
                        Map.of(A_CARRIED_ID, "tok_free", "tok_b", "tok_c")));
  }

  @Test
  void a_line_with_no_equals_sign_is_refused_by_its_number_and_never_by_what_was_typed_on_it() {
    assertThat(ReproductionScreen.theRemappingTypedInto("tok_carried=tok_free\ntok_secret_value"))
        .isInstanceOfSatisfying(
            ReproductionScreen.Remapping.Unreadable.class,
            unreadable -> {
              assertThat(unreadable.message())
                  .contains(ReproductionScreen.THE_BOX_A_REMAPPING_IS_TYPED_INTO + " line 2");
              assertThat(unreadable.message())
                  .contains(ReproductionScreen.A_LINE_NAMES_THE_ID_IT_REPLACES);
              assertThat(unreadable.message()).doesNotContain("tok_secret_value");
            });
  }

  @Test
  void a_line_naming_one_side_and_not_the_other_is_refused_by_its_number() {
    assertThat(ReproductionScreen.theRemappingTypedInto("=tok_free"))
        .isInstanceOfSatisfying(
            ReproductionScreen.Remapping.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message())
                    .contains(ReproductionScreen.THE_BOX_A_REMAPPING_IS_TYPED_INTO + " line 1"));
    assertThat(ReproductionScreen.theRemappingTypedInto("tok_carried="))
        .isInstanceOfSatisfying(
            ReproductionScreen.Remapping.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message())
                    .contains(ReproductionScreen.THE_BOX_A_REMAPPING_IS_TYPED_INTO + " line 1"));
  }

  @Test
  void a_blank_line_between_two_remappings_is_skipped_and_does_not_shift_the_numbers_after_it() {
    assertThat(ReproductionScreen.theRemappingTypedInto("a=b\n\nc"))
        .isInstanceOfSatisfying(
            ReproductionScreen.Remapping.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message())
                    .contains(ReproductionScreen.THE_BOX_A_REMAPPING_IS_TYPED_INTO + " line 3"));
  }

  @Test
  void an_empty_box_is_not_a_bundle_and_the_refusal_says_what_the_box_takes() {
    assertThat(ReproductionScreen.pastedInto(null, document -> aBundle()))
        .isInstanceOfSatisfying(
            ReproductionScreen.Pasted.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message())
                    .isEqualTo(ReproductionScreen.A_BUNDLE_IS_WHAT_THIS_BOX_TAKES));
    assertThat(ReproductionScreen.pastedInto("  ", document -> aBundle()))
        .isInstanceOf(ReproductionScreen.Pasted.Unreadable.class);
  }

  @Test
  void a_document_the_reader_throws_on_is_refused_without_the_document_being_quoted_back() {
    assertThat(
            ReproductionScreen.pastedInto(
                "{not json",
                document -> {
                  throw new IllegalStateException(document);
                }))
        .isInstanceOfSatisfying(
            ReproductionScreen.Pasted.Unreadable.class,
            unreadable -> {
              assertThat(unreadable.message())
                  .isEqualTo(ReproductionScreen.A_BUNDLE_IS_WHAT_THIS_BOX_TAKES);
              assertThat(unreadable.message()).doesNotContain("{not json");
            });
  }

  @Test
  void a_document_the_reader_reads_is_handed_on_as_the_bundle_it_read() {
    assertThat(ReproductionScreen.pastedInto("{}", document -> aBundle()))
        .isInstanceOfSatisfying(
            ReproductionScreen.Pasted.Bundle.class,
            read -> assertThat(read.bundle().testRunId()).isEqualTo("run-a"));
  }

  @Test
  void the_screen_files_a_box_it_could_not_read_as_a_bundle_it_did_not_take_back() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            ReproductionAdmission.THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK);

    assertThat(ReproductionScreen.theRefusalOfABundleItDidNotTakeBack())
        .as(
            "what a tester typed into a box is unprocessable rather than rejected, and the"
                + " verdicts of the admission are the admission's own to report")
        .isEqualTo(ActionVerdict.refused(subject));
    assertThat(ReproductionScreen.theRefusalOfABundleItDidNotTakeBack().httpStatus())
        .isEqualTo(422);
  }

  private static ReproductionBundle aBundle() {
    return new ReproductionBundle("run-a", "twokeys", null, null, null, null);
  }
}
