package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HostedCompletionTest {

  private static final TestRunId RUN = TestRunId.of("hosted-completion");

  private static final PayerSteps EVERY_STEP =
      new PayerSteps(
          ProviderId.of("aprovider"),
          Map.of(
              PayerOutcome.PAID,
              "the payer paid on the page",
              PayerOutcome.DECLINED,
              "the payer met a decline on the page",
              PayerOutcome.ABANDONED,
              "the payer left the page without paying"),
          Map.of(),
          "it runs the payer's own step on this page and charges the card it names");

  private static final HostedCompletionOffer A_PAGE_THAT_NAMES_A_CARD =
      new HostedCompletionOffer.Offered(
          ProviderId.of("aprovider"),
          List.of(new PayerCard("sandbox-4580", "Visa 4580")),
          EVERY_STEP);

  private static final HostedCompletionOffer A_PAGE_THE_PAYER_PAYS_AWAY_FROM =
      new HostedCompletionOffer.OfferedWithoutACard(
          ProviderId.of("aprovider"),
          "the payer pays this QR code in their own bank app",
          EVERY_STEP);

  @Test
  void names_the_three_things_a_payer_can_do_and_refuses_a_fourth() {
    assertThat(PayerOutcome.declared("PAID")).isEqualTo(PayerOutcome.PAID);
    assertThat(PayerOutcome.declared("DECLINED")).isEqualTo(PayerOutcome.DECLINED);
    assertThat(PayerOutcome.declared("ABANDONED")).isEqualTo(PayerOutcome.ABANDONED);
    assertThatThrownBy(() -> PayerOutcome.declared("EXPIRED"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PAID, DECLINED, ABANDONED");
  }

  @Test
  void a_page_that_collects_no_card_must_say_what_the_payer_pays_with_instead() {
    ProviderId aprovider = ProviderId.of("aprovider");

    assertThatThrownBy(
            () -> new HostedCompletionOffer.OfferedWithoutACard(aprovider, "   ", EVERY_STEP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("aprovider")
        .hasMessageContaining("must say in its own words what the payer pays with there");
  }

  @Test
  void only_a_payer_who_paid_names_a_card() {
    assertThat(PayerOutcome.PAID.namesACard()).isTrue();
    assertThat(PayerOutcome.DECLINED.namesACard()).isFalse();
    assertThat(PayerOutcome.ABANDONED.namesACard()).isFalse();
  }

  @Test
  void what_a_payer_did_is_said_in_words_a_core_refusal_can_quote_and_never_names_a_page() {
    assertThat(PayerOutcome.PAID.whatThePayerDid()).isEqualTo("paid");
    assertThat(PayerOutcome.DECLINED.whatThePayerDid()).isEqualTo("met a decline");
    assertThat(PayerOutcome.ABANDONED.whatThePayerDid()).isEqualTo("left without paying");
    for (PayerOutcome outcome : PayerOutcome.values()) {
      assertThat(outcome.whatThePayerDid()).doesNotContain("page");
    }
  }

  @Test
  void a_payment_on_a_page_that_names_a_card_is_refused_when_it_names_none() {
    assertThatThrownBy(
            () ->
                HostedCompletionRequest.of(
                    A_PAGE_THAT_NAMES_A_CARD,
                    RUN,
                    "734754",
                    PayerOutcome.PAID,
                    null,
                    ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(HostedCompletionRequest.A_CARD_IS_REQUIRED);
  }

  @Test
  void a_blank_card_is_the_same_as_naming_none() {
    assertThatThrownBy(
            () ->
                HostedCompletionRequest.of(
                    A_PAGE_THAT_NAMES_A_CARD,
                    RUN,
                    "734754",
                    PayerOutcome.PAID,
                    "   ",
                    ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(HostedCompletionRequest.A_CARD_IS_REQUIRED);
  }

  @Test
  void a_payment_on_a_page_that_collects_no_card_is_admitted_naming_none() {
    HostedCompletionRequest asked =
        HostedCompletionRequest.of(
            A_PAGE_THE_PAYER_PAYS_AWAY_FROM,
            RUN,
            "734754",
            PayerOutcome.PAID,
            null,
            ObjectOrigin.CONTROL_API);

    assertThat(asked.outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(asked.card()).isNull();
  }

  @Test
  void a_card_named_on_a_page_that_collects_none_is_refused_in_the_providers_own_words() {
    assertThatThrownBy(
            () ->
                HostedCompletionRequest.of(
                    A_PAGE_THE_PAYER_PAYS_AWAY_FROM,
                    RUN,
                    "734754",
                    PayerOutcome.PAID,
                    "sandbox-4580",
                    ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            HostedCompletionRequest.THIS_PAGE_COLLECTS_NO_CARD
                + "the payer pays this QR code in their own bank app");
  }

  @Test
  void a_card_page_names_a_card_and_a_page_whose_payer_pays_elsewhere_does_not() {
    assertThat(A_PAGE_THAT_NAMES_A_CARD.namesACard()).isTrue();
    assertThat(A_PAGE_THE_PAYER_PAYS_AWAY_FROM.namesACard()).isFalse();
  }

  @Test
  void a_page_this_provider_does_not_offer_names_no_card_and_leaves_the_refusal_to_the_provider() {
    HostedCompletionOffer notOffered =
        new HostedCompletionOffer.NotOffered(ProviderId.of("aprovider"), "no page here");

    assertThat(notOffered.namesACard()).isFalse();
    assertThat(
            HostedCompletionRequest.of(
                    notOffered, RUN, "734754", PayerOutcome.PAID, null, ObjectOrigin.CONTROL_API)
                .card())
        .isNull();
  }

  @Test
  void a_payer_who_did_not_pay_may_not_name_a_card() {
    assertThatThrownBy(
            () ->
                HostedCompletionRequest.of(
                    A_PAGE_THAT_NAMES_A_CARD,
                    RUN,
                    "734754",
                    PayerOutcome.ABANDONED,
                    "sandbox-4580",
                    ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("a payer who left without paying")
        .hasMessageContaining(HostedCompletionRequest.A_PAYER_WHO_DID_NOT_PAY_LEAVES_NO_CARD);
  }

  @Test
  void a_completion_names_the_process_it_completes() {
    assertThatThrownBy(
            () ->
                HostedCompletionRequest.of(
                    A_PAGE_THAT_NAMES_A_CARD,
                    RUN,
                    "   ",
                    PayerOutcome.ABANDONED,
                    null,
                    ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names none");
  }

  @Test
  void the_delivery_queue_never_completes_a_hosted_page() {
    assertThatThrownBy(
            () ->
                HostedCompletionRequest.of(
                    A_PAGE_THAT_NAMES_A_CARD,
                    RUN,
                    "734754",
                    PayerOutcome.ABANDONED,
                    null,
                    ObjectOrigin.SCHEDULED_JOB))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no scheduled job completes a hosted page");
  }

  @Test
  void the_payer_page_is_the_providers_own_surface_and_is_admitted_as_one() {
    HostedCompletionRequest asked =
        HostedCompletionRequest.of(
            A_PAGE_THAT_NAMES_A_CARD,
            RUN,
            " 734754 ",
            PayerOutcome.PAID,
            " sandbox-4580 ",
            ObjectOrigin.PROVIDER_API);

    assertThat(asked.providerObjectId()).isEqualTo("734754");
    assertThat(asked.card()).isEqualTo("sandbox-4580");
    assertThat(asked.askedBy()).isEqualTo(ObjectOrigin.PROVIDER_API);
  }

  @Test
  void a_completion_does_not_report_the_process_it_completed_as_an_object_it_created() {
    assertThatThrownBy(
            () ->
                new HostedCompletionOutcome.Completed(
                    "734754", "734754", PayerOutcome.PAID, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("an object the caller was already looking at");
  }

  @Test
  void a_completion_carries_the_webhook_jobs_it_caused() {
    HostedCompletionOutcome.Completed completed =
        new HostedCompletionOutcome.Completed(
            "734754", "236260", PayerOutcome.PAID, List.of("whjob_1"));

    assertThat(completed.webhookJobIds()).containsExactly("whjob_1");
    assertThat(completed.createdObjectId()).isEqualTo("236260");
  }

  @Test
  void a_refusal_and_an_absence_are_both_reported_in_the_providers_own_words() {
    assertThatThrownBy(() -> new HostedCompletionOutcome.RefusedByTheProvider(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tells a tester only that something failed");
    assertThatThrownBy(() -> new HostedCompletionOutcome.NotOffered(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a decision this simulator did not make");
    assertThat(new HostedCompletionOutcome.RefusedByTheProvider("already done").inItsOwnWords())
        .isEqualTo("already done");
    assertThat(new HostedCompletionOutcome.NotOffered("no page here").whatThisProviderHasInstead())
        .isEqualTo("no page here");
  }

  @Test
  void a_declared_completion_requires_the_outcome_the_payer_met() {
    DeclaredHostedCompletion namingNoOutcome = new DeclaredHostedCompletion(null, null, null);
    DeclaredHostedCompletion namingABlankOutcome = new DeclaredHostedCompletion(null, "  ", null);

    assertThatThrownBy(namingNoOutcome::payerOutcome)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outcome is required");
    assertThatThrownBy(namingABlankOutcome::payerOutcome)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outcome is required");
    assertThat(new DeclaredHostedCompletion("run", "PAID", "sandbox-4580").payerOutcome())
        .isEqualTo(PayerOutcome.PAID);
  }

  @Test
  void every_arm_of_a_hosted_completion_says_whether_it_refused_and_under_which_status() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            HostedCompletionOutcome.THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM);

    assertThat(new HostedCompletionOutcome.NoSuchObject().theRefusalItReports())
        .isEqualTo(
            ActionVerdict.notFound(
                RefusalSubject.noObjectAnsweredTo(HostedCompletionOutcome.PROVIDER_OBJECT_ID)));
    assertThat(new HostedCompletionOutcome.NotOffered("no hosted page").theRefusalItReports())
        .isEqualTo(ActionVerdict.refused(subject));
    assertThat(
            new HostedCompletionOutcome.RefusedByTheProvider("already paid").theRefusalItReports())
        .isEqualTo(ActionVerdict.refused(subject));
    assertThat(
            new HostedCompletionOutcome.Completed("pp_1", null, PayerOutcome.PAID, List.of())
                .theRefusalItReports())
        .isNull();
  }
}
