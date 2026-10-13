package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.RefusedActionStatus;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.AKindOfObjectATestNames;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUp;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FollowUpFormTest {

  private static final TestRunId RUN = TestRunId.of("run-follow-up-form");

  @Test
  void every_follow_up_the_object_offers_is_drawn_in_its_providers_words_and_in_its_order() {
    List<FollowUpForm.Offered> drawn =
        FollowUpForm.of(
            List.of(
                new FollowUp("settle", "settle (payout)", "AUTHORIZED to SETTLED"),
                new FollowUp("expire", "let it expire", "PENDING to EXPIRED")));

    assertThat(drawn)
        .containsExactly(
            new FollowUpForm.Offered("settle", "settle (payout)", "AUTHORIZED to SETTLED"),
            new FollowUpForm.Offered("expire", "let it expire", "PENDING to EXPIRED"));
  }

  @Test
  void an_object_that_offers_no_follow_up_draws_no_button() {
    assertThat(FollowUpForm.of(List.of())).isEmpty();
  }

  @Test
  void a_follow_up_nobody_confirmed_is_not_asked_and_says_what_confirming_would_do() {
    assertThat(FollowUpForm.asked(RUN, "pix-1", "settle", null))
        .isEqualTo(new FollowUpForm.Asking.NotAsked(FollowUpForm.CONFIRM_BEFORE_A_FOLLOW_UP));
    assertThat(FollowUpForm.asked(RUN, "pix-1", "settle", "on"))
        .isEqualTo(new FollowUpForm.Asking.NotAsked(FollowUpForm.CONFIRM_BEFORE_A_FOLLOW_UP));
  }

  @Test
  void a_confirmed_follow_up_naming_none_is_not_asked_and_says_what_is_missing() {
    assertThat(FollowUpForm.asked(RUN, "pix-1", " ", ObjectScreen.A_CHANGE_IS_CONFIRMED))
        .isEqualTo(new FollowUpForm.Asking.NotAsked(FollowUpRequest.A_FOLLOW_UP_IS_NAMED));
  }

  @Test
  void a_confirmed_follow_up_is_asked_as_the_testers_own_from_the_screen() {
    assertThat(FollowUpForm.asked(RUN, " pix-1 ", "settle", ObjectScreen.A_CHANGE_IS_CONFIRMED))
        .isEqualTo(
            new FollowUpForm.Asking.Asked(
                new FollowUpRequest(RUN, "pix-1", "settle", ObjectOrigin.UI)));
  }

  @Test
  void a_follow_up_never_asked_is_rejected_without_naming_an_object() {
    ActionVerdict.Refused rejected = FollowUpForm.theRejectionOfAFollowUpNeverAsked();

    assertThat(rejected.status()).isEqualTo(RefusedActionStatus.REJECTED);
    assertThat(rejected.subject())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                FollowUpOutcome.THE_FOLLOW_UP_IT_ASKED_THIS_PROVIDER_TO_PERFORM));
  }

  @Test
  void a_follow_up_that_named_no_object_names_nothing_on_its_row_rather_than_a_blank_id() {
    assertThat(FollowUpForm.theObjectItNamed(null)).isNull();
    assertThat(FollowUpForm.theObjectItNamed(""))
        .as(
            "a browser posts an empty text field as an empty value, and a row naming an object"
                + " by a blank id names nothing while looking as though it names something")
        .isNull();
    assertThat(FollowUpForm.theObjectItNamed("   ")).isNull();
    assertThat(FollowUpForm.theObjectItNamed("  trx_1 "))
        .as("the id a tester typed is the id the row names, without what they typed around it")
        .isEqualTo("trx_1");
  }

  @Test
  void a_refused_follow_up_records_the_object_what_was_asked_and_why_it_was_refused() {
    assertThat(FollowUpForm.factsOf("trx_1", "settle", "already settled"))
        .as(
            "the control api names the object a refused follow-up was asked about and this"
                + " screen did not, so a tester comparing the follow-up they clicked with the"
                + " one their suite made could not tell the two rows named the same object")
        .containsEntry(ObjectScreen.PROVIDER_OBJECT_ID, List.of("trx_1"))
        .containsEntry(FollowUpForm.FOLLOW_UP, List.of("settle"))
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("already settled"));
  }

  @Test
  void a_performed_follow_up_records_its_name_and_every_callback_it_queued() {
    FollowUpOutcome.Performed performed =
        new FollowUpOutcome.Performed(
            "settle", AddressedProviderObject.canonically(anObject()), List.of("job-1", "job-2"));

    Map<String, List<String>> facts = FollowUpForm.factsOf(performed);

    assertThat(facts)
        .containsEntry(FollowUpForm.FOLLOW_UP, List.of("settle"))
        .containsEntry("webhookJobIds", List.of("job-1", "job-2"));
  }

  private static ProviderObjectState anObject() {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("pix-1"),
        AKindOfObjectATestNames.ONE,
        "SETTLED",
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        Instant.EPOCH);
  }
}
