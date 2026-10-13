package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowUpControlTest {

  private static final TestRunId RUN = TestRunId.of("run-follow-up");
  private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");
  private static final FollowUp SETTLE =
      new FollowUp("settle", "settle (payout)", "AUTHORIZED to SETTLED");

  @Test
  void a_follow_up_the_provider_performs_answers_the_object_as_it_now_reads_and_its_jobs() {
    ProviderDouble module =
        new ProviderDouble()
            .offeringAFollowUp("pix-1", SETTLE, List.of(new ScheduledWebhook("job-1", "event-1")));

    FollowUpOutcome outcome = FollowUpControl.perform(module, asked("pix-1", "settle"), NOW);

    assertThat(outcome).isInstanceOf(FollowUpOutcome.Performed.class);
    FollowUpOutcome.Performed performed = (FollowUpOutcome.Performed) outcome;
    assertThat(performed.name()).isEqualTo("settle");
    assertThat(performed.after().canonical()).isEqualTo("pix-1");
    assertThat(performed.webhookJobIds()).containsExactly("job-1");
    assertThat(module.followedUp()).containsExactly(asked("pix-1", "settle"));
  }

  @Test
  void an_object_the_run_does_not_hold_is_not_found_and_the_provider_is_never_asked() {
    ProviderDouble module = new ProviderDouble().offeringAFollowUp("pix-1", SETTLE, List.of());

    FollowUpOutcome outcome = FollowUpControl.perform(module, asked("pix-2", "settle"), NOW);

    assertThat(outcome)
        .isEqualTo(
            new FollowUpOutcome.Refused(
                ActionVerdict.notFound(
                    RefusalSubject.noObjectAnsweredTo(FollowUpControl.PROVIDER_OBJECT_ID)),
                FollowUpControl.theRunHoldsNoSuchObject(module.id())));
    assertThat(((FollowUpOutcome.Refused) outcome).verdict().httpStatus()).isEqualTo(404);
    assertThat(((FollowUpOutcome.Refused) outcome).inItsOwnWords())
        .contains("provider " + module.id().value());
    assertThat(module.followedUp()).isEmpty();
  }

  @Test
  void a_follow_up_the_provider_does_not_offer_now_is_refused_in_the_providers_own_words() {
    ProviderDouble module =
        new ProviderDouble()
            .offeringAFollowUp("pix-1", SETTLE, List.of())
            .refusingEveryFollowUpWith(new FollowUpNotOfferedException("it has already settled"));

    FollowUpOutcome outcome = FollowUpControl.perform(module, asked("pix-1", "settle"), NOW);

    assertThat(outcome)
        .isEqualTo(
            new FollowUpOutcome.Refused(
                ActionVerdict.refused(RefusalSubject.theObject("pix-1")),
                "it has already settled"));
    assertThat(((FollowUpOutcome.Refused) outcome).verdict().httpStatus()).isEqualTo(422);
  }

  @Test
  void a_module_that_declares_no_follow_up_is_refused_in_the_cores_words_and_never_asked() {
    ProviderDouble module = new ProviderDouble().holding("pay-1");

    FollowUpOutcome outcome = FollowUpControl.perform(module, asked("pay-1", "settle"), NOW);

    assertThat(outcome)
        .isEqualTo(
            new FollowUpOutcome.Refused(
                ActionVerdict.refused(RefusalSubject.theObject("pay-1")),
                FollowUpControl.theObjectDoesNotOfferIt(module.id(), "settle")));
    assertThat(module.followedUp()).isEmpty();
  }

  @Test
  void a_name_the_object_does_not_offer_now_is_refused_422_before_the_provider_is_asked() {
    ProviderDouble module = new ProviderDouble().offeringAFollowUp("pix-1", SETTLE, List.of());

    FollowUpOutcome outcome = FollowUpControl.perform(module, asked("pix-1", "refund"), NOW);

    assertThat(outcome)
        .isEqualTo(
            new FollowUpOutcome.Refused(
                ActionVerdict.refused(RefusalSubject.theObject("pix-1")),
                FollowUpControl.theObjectDoesNotOfferIt(module.id(), "refund")));
    assertThat(((FollowUpOutcome.Refused) outcome).verdict().httpStatus()).isEqualTo(422);
    assertThat(((FollowUpOutcome.Refused) outcome).inItsOwnWords())
        .contains("refund")
        .contains("provider " + module.id().value())
        .contains("the Payments screen lists the follow-ups it offers")
        .doesNotContain("the object screen");
    assertThat(module.followedUp()).isEmpty();
  }

  @Test
  void an_object_that_vanished_before_the_provider_moved_it_is_not_found() {
    ProviderDouble module =
        new ProviderDouble()
            .offeringAFollowUp("pix-1", SETTLE, List.of())
            .refusingEveryFollowUpWith(new UnknownProviderObjectException("gone"));

    FollowUpOutcome outcome = FollowUpControl.perform(module, asked("pix-1", "settle"), NOW);

    assertThat(outcome).isInstanceOf(FollowUpOutcome.Refused.class);
    assertThat(((FollowUpOutcome.Refused) outcome).verdict().httpStatus()).isEqualTo(404);
  }

  @Test
  void an_object_addressed_by_a_later_identifier_is_followed_up_and_read_back_by_that_identifier() {
    ProviderDouble module =
        new ProviderDouble()
            .offeringAFollowUp("pix-1", SETTLE, List.of())
            .mintingAFurtherIdentifierOn("pix-1", "pix-1-later");

    FollowUpOutcome.Performed performed =
        (FollowUpOutcome.Performed)
            FollowUpControl.perform(module, asked("pix-1-later", "settle"), NOW);

    assertThat(performed.after().addressedBy()).isEqualTo("pix-1-later");
    assertThat(performed.after().canonical()).isEqualTo("pix-1");
  }

  @Test
  void the_provider_is_handed_the_canonical_id_of_an_object_addressed_by_a_later_identifier() {
    ProviderDouble module =
        new ProviderDouble()
            .offeringAFollowUp("pix-1", SETTLE, List.of())
            .mintingAFurtherIdentifierOn("pix-1", "pix-1-later");

    FollowUpControl.perform(module, asked("pix-1-later", "settle"), NOW);

    assertThat(module.followedUp()).containsExactly(asked("pix-1", "settle"));
  }

  @Test
  void a_refusal_of_an_object_addressed_by_a_later_identifier_names_it_by_its_canonical_id() {
    ProviderDouble module =
        new ProviderDouble()
            .offeringAFollowUp("pix-1", SETTLE, List.of())
            .mintingAFurtherIdentifierOn("pix-1", "pix-1-later")
            .refusingEveryFollowUpWith(new FollowUpNotOfferedException("not now"));

    FollowUpOutcome outcome = FollowUpControl.perform(module, asked("pix-1-later", "settle"), NOW);

    assertThat(((FollowUpOutcome.Refused) outcome).verdict().subject())
        .isEqualTo(RefusalSubject.theObject("pix-1"));
  }

  @Test
  void an_object_gone_once_the_provider_moved_it_is_not_found_as_before_the_move() {
    ProviderDouble module =
        new ProviderDouble()
            .offeringAFollowUp("pix-1", SETTLE, List.of(new ScheduledWebhook("job-1", "event-1")))
            .forgettingTheObjectItFollowsUp();

    FollowUpOutcome outcome = FollowUpControl.perform(module, asked("pix-1", "settle"), NOW);

    assertThat(outcome)
        .isEqualTo(
            new FollowUpOutcome.Refused(
                ActionVerdict.notFound(
                    RefusalSubject.noObjectAnsweredTo(FollowUpControl.PROVIDER_OBJECT_ID)),
                FollowUpControl.theRunHoldsNoSuchObject(module.id())));
    assertThat(module.followedUp()).containsExactly(asked("pix-1", "settle"));
  }

  private static FollowUpRequest asked(String providerObjectId, String name) {
    return FollowUpRequest.of(RUN, providerObjectId, name, ObjectOrigin.CONTROL_API);
  }
}
