package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.RefusedActionStatus;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerSteps;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class HostedCompletionFormTest {

  private static final TestRunId RUN = TestRunId.of("ui-hosted-completion");

  private static final String A_PROCESS = "734754";

  private static final String WHAT_TAKING_A_STEP_ON_GROWS_PAGE_DOES =
      "it runs the payer's own step on grow's hosted page and charges the card it names";

  private static final String WHAT_TAKING_A_STEP_ON_A_PIX_DOES =
      "it records the payer of this Pix as having paid the payload from their own bank app; a Pix"
          + " collects no card, so none is named here and nothing is charged";

  private static final PayerSteps WHAT_GROWS_PAGE_ADMITS =
      new PayerSteps(
          ProviderId.of("grow"),
          Map.of(
              PayerOutcome.PAID,
              "the payer paid on the page",
              PayerOutcome.DECLINED,
              "the payer met a decline on the page",
              PayerOutcome.ABANDONED,
              "the payer left the page without paying"),
          Map.of(),
          WHAT_TAKING_A_STEP_ON_GROWS_PAGE_DOES);

  private static final PayerSteps WHAT_A_PIX_ADMITS =
      new PayerSteps(
          ProviderId.of("autra"),
          Map.of(
              PayerOutcome.PAID,
              "the payer pasted the payload into their own bank app and paid it",
              PayerOutcome.ABANDONED,
              "the payer never paid the payload and let it expire"),
          Map.of(
              PayerOutcome.DECLINED, "provider autra publishes no declined state for a Pix at all"),
          WHAT_TAKING_A_STEP_ON_A_PIX_DOES);

  private static final HostedCompletionOffer A_PAGE_WITH_TWO_CARDS =
      new HostedCompletionOffer.Offered(
          ProviderId.of("grow"),
          List.of(
              new PayerCard("sandbox-4580", "Visa •••• 4580, 0424"),
              new PayerCard("seeded-visa", "Visa •••• 1111, 1230")),
          WHAT_GROWS_PAGE_ADMITS);

  private static final String WHAT_AZERICARD_HAS_INSTEAD =
      "provider azericard publishes no server-to-server call that hands back a hosted page URL";

  private static final HostedCompletionOffer NO_PAGE_AT_ALL =
      new HostedCompletionOffer.NotOffered(ProviderId.of("azericard"), WHAT_AZERICARD_HAS_INSTEAD);

  @Test
  void a_page_awaiting_its_payer_offers_the_three_things_a_payer_can_do_to_it() {
    HostedCompletionForm.View view = form(A_PAGE_WITH_TWO_CARDS, null, null);

    assertThat(view.offersAPayerStep()).isTrue();
    assertThat(view.outcomes())
        .extracting(HostedCompletionForm.Met::outcome)
        .containsExactly(
            PayerOutcome.PAID.name(), PayerOutcome.DECLINED.name(), PayerOutcome.ABANDONED.name());
  }

  @Test
  void the_screen_says_which_of_the_three_leaves_a_card_behind() {
    HostedCompletionForm.View view = form(A_PAGE_WITH_TWO_CARDS, null, null);

    assertThat(view.outcomes())
        .filteredOn(HostedCompletionForm.Met::namesACard)
        .extracting(HostedCompletionForm.Met::outcome)
        .containsExactly(PayerOutcome.PAID.name());
  }

  @Test
  void every_step_is_offered_in_the_words_the_provider_declared_for_it_and_not_in_core_words() {
    HostedCompletionForm.View view = form(A_PAGE_WITH_TWO_CARDS, null, null);

    assertThat(view.outcomes())
        .extracting(HostedCompletionForm.Met::asATesterReadsIt)
        .containsExactly(
            "the payer paid on the page",
            "the payer met a decline on the page",
            "the payer left the page without paying");
  }

  @Test
  void
      an_object_whose_provider_admits_two_steps_draws_those_two_and_never_a_third_that_must_fail() {
    HostedCompletionForm.View view =
        HostedCompletionForm.of(
            "autra",
            RUN.value(),
            A_PROCESS,
            new HostedCompletionOffer.OfferedWithoutACard(
                ProviderId.of("autra"),
                "the payer pays this payload in their own bank app",
                WHAT_A_PIX_ADMITS),
            null,
            null);

    assertThat(view.outcomes())
        .extracting(HostedCompletionForm.Met::outcome, HostedCompletionForm.Met::asATesterReadsIt)
        .containsExactly(
            Assertions.tuple(
                "PAID", "the payer pasted the payload into their own bank app and paid it"),
            Assertions.tuple("ABANDONED", "the payer never paid the payload and let it expire"));
    assertThat(view.outcomes())
        .extracting(HostedCompletionForm.Met::asATesterReadsIt)
        .noneMatch(read -> read.contains("on the page"));
  }

  @Test
  void the_step_a_tester_took_is_said_back_to_them_in_the_words_they_read_on_it() {
    assertThat(HostedCompletionForm.theStepItTook(A_PAGE_WITH_TWO_CARDS, PayerOutcome.DECLINED))
        .isEqualTo("the payer met a decline on the page");
    assertThat(
            Done.thePayerStepIsTaken(
                HostedCompletionForm.theStepItTook(
                    new HostedCompletionOffer.OfferedWithoutACard(
                        ProviderId.of("autra"),
                        "the payer pays this payload in their own bank app",
                        WHAT_A_PIX_ADMITS),
                    PayerOutcome.PAID)))
        .isEqualTo(
            "This payer step is taken: the payer pasted the payload into their own bank app and"
                + " paid it.");
  }

  @Test
  void the_banner_says_the_step_that_was_taken_and_never_another_step_the_object_admits() {
    assertThat(
            HostedCompletionForm.thePayerStepTaken(A_PAGE_WITH_TWO_CARDS, took(PayerOutcome.PAID)))
        .isEqualTo("This payer step is taken: the payer paid on the page.");
    assertThat(
            HostedCompletionForm.thePayerStepTaken(
                A_PAGE_WITH_TWO_CARDS, took(PayerOutcome.DECLINED)))
        .as("a banner naming a step the tester did not pick reports the opposite of what they did")
        .isEqualTo("This payer step is taken: the payer met a decline on the page.")
        .doesNotContain("the payer paid on the page");
    assertThat(
            HostedCompletionForm.thePayerStepTaken(
                A_PAGE_WITH_TWO_CARDS, took(PayerOutcome.ABANDONED)))
        .isEqualTo("This payer step is taken: the payer left the page without paying.")
        .doesNotContain("the payer paid on the page")
        .doesNotContain("met a decline");
  }

  @Test
  void the_banner_of_an_object_that_collects_no_card_is_that_providers_own_words_for_its_step() {
    HostedCompletionOffer aPix =
        new HostedCompletionOffer.OfferedWithoutACard(
            ProviderId.of("autra"),
            "the payer pays this payload in their own bank app",
            WHAT_A_PIX_ADMITS);

    assertThat(HostedCompletionForm.thePayerStepTaken(aPix, took(PayerOutcome.ABANDONED)))
        .isEqualTo("This payer step is taken: the payer never paid the payload and let it expire.")
        .doesNotContain("paid it");
    assertThat(HostedCompletionForm.thePayerStepTaken(aPix, took(PayerOutcome.PAID)))
        .isEqualTo(
            "This payer step is taken: the payer pasted the payload into their own bank app and"
                + " paid it.");
  }

  @Test
  void
      a_step_the_provider_accounts_for_without_offering_is_carried_to_the_screen_with_its_reason() {
    HostedCompletionForm.View view =
        HostedCompletionForm.of(
            "autra",
            RUN.value(),
            A_PROCESS,
            new HostedCompletionOffer.OfferedWithoutACard(
                ProviderId.of("autra"),
                "the payer pays this payload in their own bank app",
                WHAT_A_PIX_ADMITS),
            null,
            null);

    assertThat(view.unoffered())
        .extracting(
            HostedCompletionForm.Unoffered::outcome,
            HostedCompletionForm.Unoffered::whatThePayerDid,
            HostedCompletionForm.Unoffered::whatItHasInstead)
        .containsExactly(
            Assertions.tuple(
                "DECLINED",
                "met a decline",
                "provider autra publishes no declined state for a Pix at all"));
    assertThat(view.outcomes())
        .extracting(HostedCompletionForm.Met::outcome)
        .doesNotContain("DECLINED");
  }

  @Test
  void an_object_that_admits_every_step_has_no_absence_for_a_screen_to_explain() {
    assertThat(form(A_PAGE_WITH_TWO_CARDS, null, null).unoffered()).isEmpty();
    assertThat(form(NO_PAGE_AT_ALL, null, null).unoffered()).isEmpty();
  }

  @Test
  void the_screen_carries_the_caution_a_tester_reads_before_ticking_confirm_and_not_only_after() {
    assertThat(
            HostedCompletionForm.of(
                    "autra",
                    RUN.value(),
                    A_PROCESS,
                    new HostedCompletionOffer.OfferedWithoutACard(
                        ProviderId.of("autra"),
                        "the payer pays this payload in their own bank app",
                        WHAT_A_PIX_ADMITS),
                    null,
                    null)
                .theCautionBeforeTakingOne())
        .isEqualTo(
            "tick confirm before taking this payer step: it records the payer of this Pix as having"
                + " paid the payload from their own bank app; a Pix collects no card, so none is"
                + " named here and nothing is charged, and none of it can be undone by leaving"
                + " this screen");
    assertThat(form(A_PAGE_WITH_TWO_CARDS, null, null).theCautionBeforeTakingOne())
        .as("the caution a screen draws is the one an unconfirmed submit is refused with")
        .isEqualTo(HostedCompletionForm.theCautionBefore(A_PAGE_WITH_TWO_CARDS));
  }

  @Test
  void an_unconfirmed_step_on_an_object_that_has_none_is_recorded_as_the_providers_own_refusal() {
    assertThat(HostedCompletionForm.theRefusalBeforeTakingOne(NO_PAGE_AT_ALL).status())
        .as(
            "a step this provider never offers was not stopped by a tester leaving confirm unticked")
        .isEqualTo(RefusedActionStatus.REFUSED);
    assertThat(HostedCompletionForm.theRefusalBeforeTakingOne(A_PAGE_WITH_TWO_CARDS).status())
        .isEqualTo(RefusedActionStatus.REJECTED);
    assertThat(HostedCompletionForm.theRefusalBeforeTakingOne(A_PAGE_WITH_TWO_CARDS))
        .isEqualTo(HostedCompletionForm.theRefusalOfAnUnconfirmedCompletion());
  }

  @Test
  void
      the_caution_a_tester_confirms_says_what_this_object_does_and_names_no_card_when_it_has_none() {
    HostedCompletionOffer aPix =
        new HostedCompletionOffer.OfferedWithoutACard(
            ProviderId.of("autra"),
            "the payer pays this payload in their own bank app",
            WHAT_A_PIX_ADMITS);

    assertThat(HostedCompletionForm.theCautionBefore(aPix))
        .isEqualTo(
            "tick confirm before taking this payer step: it records the payer of this Pix as having"
                + " paid the payload from their own bank app; a Pix collects no card, so none is"
                + " named here and nothing is charged, and none of it can be undone by leaving"
                + " this screen");
    assertThat(HostedCompletionForm.theCautionBefore(aPix))
        .as("a caution that warns of a card being charged is false of an object that collects none")
        .doesNotContain("charges the card")
        .doesNotContain("finishing this page")
        .doesNotContain("closing the page");
  }

  @Test
  void the_caution_on_a_hosted_card_page_still_says_the_card_it_names_is_charged() {
    assertThat(HostedCompletionForm.theCautionBefore(A_PAGE_WITH_TWO_CARDS))
        .isEqualTo(
            "tick confirm before taking this payer step: it runs the payer's own step on grow's"
                + " hosted page and charges the card it names, and none of it can be undone by"
                + " leaving this screen");
  }

  @Test
  void the_screen_says_what_this_object_does_with_a_payer_step_in_the_words_of_its_own_provider() {
    assertThat(form(A_PAGE_WITH_TWO_CARDS, null, null).whatTakingOneDoes())
        .isEqualTo(
            "it runs the payer's own step on grow's hosted page and charges the card it"
                + " names");
    assertThat(form(NO_PAGE_AT_ALL, null, null).whatTakingOneDoes()).isNull();
  }

  @Test
  void an_object_with_no_payer_step_answers_an_unconfirmed_one_in_the_providers_own_words() {
    assertThat(HostedCompletionForm.theCautionBefore(NO_PAGE_AT_ALL))
        .isEqualTo(WHAT_AZERICARD_HAS_INSTEAD);
  }

  @Test
  void a_step_reported_back_from_an_offer_that_has_none_is_a_fault_and_not_a_sentence() {
    assertThatThrownBy(() -> HostedCompletionForm.theStepItTook(NO_PAGE_AT_ALL, PayerOutcome.PAID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(HostedCompletionForm.NO_STEP_TO_REPORT);
  }

  @Test
  void the_cards_the_screen_offers_are_the_ones_this_provider_declared_for_this_test_run() {
    HostedCompletionForm.View view = form(A_PAGE_WITH_TWO_CARDS, null, null);

    assertThat(view.cards())
        .extracting(HostedCompletionForm.Card::alias, HostedCompletionForm.Card::label)
        .containsExactly(
            Assertions.tuple("sandbox-4580", "Visa •••• 4580, 0424"),
            Assertions.tuple("seeded-visa", "Visa •••• 1111, 1230"));
  }

  @Test
  void what_a_tester_already_picked_comes_back_marked_as_theirs() {
    HostedCompletionForm.View view = form(A_PAGE_WITH_TWO_CARDS, "DECLINED", "seeded-visa");

    assertThat(view.outcomes())
        .filteredOn(HostedCompletionForm.Met::chosen)
        .extracting(HostedCompletionForm.Met::outcome)
        .containsExactly(PayerOutcome.DECLINED.name());
    assertThat(view.cards())
        .filteredOn(HostedCompletionForm.Card::chosen)
        .extracting(HostedCompletionForm.Card::alias)
        .containsExactly("seeded-visa");
  }

  @Test
  void a_provider_with_no_payer_step_gets_its_own_sentence_on_the_screen_and_no_form_at_all() {
    HostedCompletionForm.View view = form(NO_PAGE_AT_ALL, null, null);

    assertThat(view.offersAPayerStep()).isFalse();
    assertThat(view.whatThisProviderHasInstead()).isEqualTo(WHAT_AZERICARD_HAS_INSTEAD);
    assertThat(view.outcomes()).isEmpty();
    assertThat(view.cards()).isEmpty();
  }

  @Test
  void a_completion_the_provider_took_leaves_the_screen_with_nothing_to_report_as_a_refusal() {
    assertThat(
            HostedCompletionForm.refusalOf(
                new HostedCompletionOutcome.Completed(
                    A_PROCESS, "236260", PayerOutcome.PAID, List.of("whjob_1"))))
        .isNull();
  }

  @Test
  void a_completion_the_provider_refused_is_reported_in_that_providers_own_words() {
    assertThat(
            HostedCompletionForm.refusalOf(
                new HostedCompletionOutcome.RefusedByTheProvider(
                    "this hosted process has already reached an outcome")))
        .isEqualTo("this hosted process has already reached an outcome");
    assertThat(
            HostedCompletionForm.refusalOf(
                new HostedCompletionOutcome.NotOffered(WHAT_AZERICARD_HAS_INSTEAD)))
        .isEqualTo(WHAT_AZERICARD_HAS_INSTEAD);
  }

  @Test
  void a_page_the_run_no_longer_holds_is_reported_as_gone_rather_than_as_a_failure() {
    assertThat(HostedCompletionForm.refusalOf(new HostedCompletionOutcome.NoSuchObject()))
        .isEqualTo(HostedCompletionForm.THE_PAGE_IS_GONE);
  }

  @Test
  void a_page_whose_payer_names_no_card_draws_the_outcomes_and_no_card_list_at_all() {
    HostedCompletionForm.View view =
        HostedCompletionForm.of(
            "autra",
            RUN.value(),
            A_PROCESS,
            new HostedCompletionOffer.OfferedWithoutACard(
                ProviderId.of("autra"),
                "the payer pays this QR in their own bank app",
                WHAT_A_PIX_ADMITS),
            null,
            null);

    assertThat(view.offersAPayerStep()).isTrue();
    assertThat(view.namesACard()).isFalse();
    assertThat(view.cards()).isEmpty();
    assertThat(view.outcomes()).hasSize(2);
    assertThat(view.whatThePayerPaysWithInstead())
        .isEqualTo("the payer pays this QR in their own bank app");
  }

  @Test
  void a_page_whose_payer_names_a_card_draws_the_cards_the_run_offers() {
    HostedCompletionForm.View view =
        HostedCompletionForm.of(
            "grow",
            RUN.value(),
            A_PROCESS,
            new HostedCompletionOffer.Offered(
                ProviderId.of("grow"),
                List.of(new PayerCard("seeded-visa", "Visa 1111")),
                WHAT_GROWS_PAGE_ADMITS),
            null,
            null);

    assertThat(view.namesACard()).isTrue();
    assertThat(view.whatThePayerPaysWithInstead()).isNull();
    assertThat(view.cards()).hasSize(1);
  }

  private static HostedCompletionOutcome.Completed took(PayerOutcome outcome) {
    return new HostedCompletionOutcome.Completed(A_PROCESS, null, outcome, List.of());
  }

  private static HostedCompletionForm.View form(
      HostedCompletionOffer offer, String outcome, String card) {
    return HostedCompletionForm.of("grow", RUN.value(), A_PROCESS, offer, outcome, card);
  }
}
