package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HostedCompletionOfferTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final PayerCard A_PUBLISHED_CARD =
      new PayerCard("sandbox-4580", "Visa •••• 4580, 0424");

  private static final PayerCard A_SEEDED_CARD =
      new PayerCard("the-declining-one", "Mastercard •••• 0002, 1229");

  private static final PayerSteps EVERY_STEP_ON_A_PAGE =
      new PayerSteps(
          GROW,
          Map.of(
              PayerOutcome.PAID,
              "the payer paid on the page",
              PayerOutcome.DECLINED,
              "the payer met a decline on the page",
              PayerOutcome.ABANDONED,
              "the payer left the page without paying"),
          Map.of(),
          "it runs the payer's own step on this page and charges the card it names");

  @Test
  void a_provider_that_offers_a_payer_step_names_the_cards_that_payer_may_finish_it_with() {
    HostedCompletionOffer.Offered offered =
        new HostedCompletionOffer.Offered(
            GROW, List.of(A_PUBLISHED_CARD, A_SEEDED_CARD), EVERY_STEP_ON_A_PAGE);

    assertThat(offered.card("the-declining-one")).contains(A_SEEDED_CARD);
    assertThat(offered.card("no-such-card")).isEmpty();
  }

  @Test
  void a_provider_offering_a_payer_step_and_no_card_is_refused_before_a_screen_draws_it() {
    assertThatThrownBy(
            () -> new HostedCompletionOffer.Offered(GROW, List.of(), EVERY_STEP_ON_A_PAGE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("an empty list to pick from");
  }

  @Test
  void one_card_offered_twice_is_refused_because_a_tester_could_not_tell_which_they_picked() {
    List<PayerCard> oneAliasTwice =
        List.of(A_PUBLISHED_CARD, new PayerCard("sandbox-4580", "a second label"));

    assertThatThrownBy(
            () -> new HostedCompletionOffer.Offered(GROW, oneAliasTwice, EVERY_STEP_ON_A_PAGE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("offers the card sandbox-4580 twice");
  }

  @Test
  void the_cards_it_offers_are_named_for_a_tester_by_label_and_by_the_alias_they_send_back() {
    HostedCompletionOffer.Offered offered =
        new HostedCompletionOffer.Offered(
            GROW, List.of(A_PUBLISHED_CARD, A_SEEDED_CARD), EVERY_STEP_ON_A_PAGE);

    assertThat(offered.theCardsItOffers())
        .isEqualTo(
            "Visa •••• 4580, 0424 (sandbox-4580),"
                + " Mastercard •••• 0002, 1229 (the-declining-one)");
  }

  @Test
  void a_provider_with_no_payer_step_says_in_its_own_words_what_it_has_in_place_of_one() {
    HostedCompletionOffer.NotOffered notOffered =
        new HostedCompletionOffer.NotOffered(
            ProviderId.of("azericard"), "its payer-facing surface is the gateway form itself");

    assertThat(notOffered.whatThisProviderHasInstead())
        .isEqualTo("its payer-facing surface is the gateway form itself");
  }

  @Test
  void a_provider_with_no_payer_step_and_nothing_to_say_is_refused_rather_than_worded_for() {
    assertThatThrownBy(() -> new HostedCompletionOffer.NotOffered(GROW, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a decision this simulator did not make");
  }

  @Test
  void a_card_with_no_alias_or_no_label_is_refused_because_a_screen_renders_one_and_sends_other() {
    assertThatThrownBy(() -> new PayerCard(" ", "Visa •••• 4580, 0424"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the alias is what a completion sends back");
    assertThatThrownBy(() -> new PayerCard("sandbox-4580", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("card sandbox-4580 must carry a label");
  }

  @Test
  void the_cards_a_provider_declared_are_held_against_a_caller_changing_the_list_afterwards() {
    List<PayerCard> declared = new ArrayList<>(List.of(A_PUBLISHED_CARD));
    HostedCompletionOffer.Offered offered =
        new HostedCompletionOffer.Offered(GROW, declared, EVERY_STEP_ON_A_PAGE);

    declared.add(A_SEEDED_CARD);

    assertThat(offered.cardsAPayerMayName()).containsExactly(A_PUBLISHED_CARD);
  }

  @Test
  void an_offer_carrying_another_providers_steps_is_refused_before_a_screen_draws_them() {
    PayerSteps whatAutraDeclared =
        new PayerSteps(
            ProviderId.of("autra"),
            Map.of(PayerOutcome.PAID, "the payer paid it in their own bank app"),
            Map.of(
                PayerOutcome.DECLINED,
                "autra publishes no declined state for one",
                PayerOutcome.ABANDONED,
                "autra lets one expire instead"),
            "it records the payer as having paid it from their own bank app");

    assertThatThrownBy(
            () ->
                new HostedCompletionOffer.Offered(
                    GROW, List.of(A_PUBLISHED_CARD), whatAutraDeclared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider grow")
        .hasMessageContaining("provider autra declared")
        .hasMessageContaining(HostedCompletionOffer.THE_STEPS_OF_ANOTHER_PROVIDER)
        .hasMessageContaining("a screen would draw one provider's object under another provider's")
        .hasMessageEndingWith("words");
    assertThatThrownBy(
            () ->
                new HostedCompletionOffer.OfferedWithoutACard(
                    GROW, "the payer pays it elsewhere", whatAutraDeclared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(HostedCompletionOffer.THE_STEPS_OF_ANOTHER_PROVIDER);
  }

  @Test
  void an_offer_draws_only_the_steps_the_provider_declared_for_the_object_it_was_made_on() {
    PayerSteps whatAPixAdmits =
        new PayerSteps(
            GROW,
            Map.of(
                PayerOutcome.PAID,
                "the payer paid it in their own bank app",
                PayerOutcome.ABANDONED,
                "the payer let it expire"),
            Map.of(PayerOutcome.DECLINED, "there is no declined state on one"),
            "it records the payer as having paid it from their own bank app");

    HostedCompletionOffer.OfferedWithoutACard offered =
        new HostedCompletionOffer.OfferedWithoutACard(
            GROW, "the payer pays it in their own bank app", whatAPixAdmits);

    assertThat(offered.theStepsItOffers().theStepsAPayerMayTake())
        .containsExactly(PayerOutcome.PAID, PayerOutcome.ABANDONED);
    assertThat(offered.theStepsItOffers().offers(PayerOutcome.DECLINED)).isFalse();
  }
}
