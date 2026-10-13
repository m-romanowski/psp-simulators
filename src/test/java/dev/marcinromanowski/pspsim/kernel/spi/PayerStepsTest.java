package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayerStepsTest {

  private static final ProviderId AUTRA = ProviderId.of("autra");

  private static final String WHAT_TAKING_ONE_DOES =
      "it records the payer as having paid the payload from their own bank app; a Pix collects no"
          + " card, so none is named here and nothing is charged";

  private static final String NO_DECLINED_STATE =
      "provider autra publishes the Pix state machine in full and it has no declined state at all";

  private static final PayerSteps WHAT_A_PIX_ADMITS =
      new PayerSteps(
          AUTRA,
          Map.of(
              PayerOutcome.PAID,
              "the payer pasted the payload into their own bank app and paid it",
              PayerOutcome.ABANDONED,
              "the payer never paid the payload and let it expire"),
          Map.of(PayerOutcome.DECLINED, NO_DECLINED_STATE),
          WHAT_TAKING_ONE_DOES);

  @Test
  void a_provider_declares_the_steps_its_object_admits_in_the_order_a_screen_draws_them() {
    assertThat(WHAT_A_PIX_ADMITS.theStepsAPayerMayTake())
        .containsExactly(PayerOutcome.PAID, PayerOutcome.ABANDONED);
    assertThat(WHAT_A_PIX_ADMITS.offers(PayerOutcome.PAID)).isTrue();
    assertThat(WHAT_A_PIX_ADMITS.offers(PayerOutcome.ABANDONED)).isTrue();
    assertThat(WHAT_A_PIX_ADMITS.offers(PayerOutcome.DECLINED)).isFalse();
  }

  @Test
  void each_step_it_offers_carries_the_words_a_tester_reads_on_it() {
    assertThat(WHAT_A_PIX_ADMITS.asATesterReadsIt(PayerOutcome.PAID))
        .isEqualTo("the payer pasted the payload into their own bank app and paid it");
    assertThat(WHAT_A_PIX_ADMITS.asATesterReadsIt(PayerOutcome.ABANDONED))
        .isEqualTo("the payer never paid the payload and let it expire");
  }

  @Test
  void
      a_step_it_does_not_offer_has_no_words_a_tester_reads_and_says_so_rather_than_inventing_them() {
    assertThatThrownBy(() -> WHAT_A_PIX_ADMITS.asATesterReadsIt(PayerOutcome.DECLINED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider autra offers no DECLINED step on this object")
        .hasMessageContaining("publishes no words a tester reads on one");
  }

  @Test
  void a_step_it_does_not_offer_is_refused_in_this_providers_own_words() {
    assertThat(WHAT_A_PIX_ADMITS.whatItHasInsteadOf(PayerOutcome.DECLINED))
        .isEqualTo(NO_DECLINED_STATE);
  }

  @Test
  void a_step_it_does_offer_publishes_nothing_in_place_of_itself() {
    assertThatThrownBy(() -> WHAT_A_PIX_ADMITS.whatItHasInsteadOf(PayerOutcome.PAID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider autra offers the PAID step on this object")
        .hasMessageContaining("publishes nothing in place of it");
  }

  @Test
  void a_step_the_provider_accounts_for_neither_way_is_refused_rather_than_worded_by_the_core() {
    assertThatThrownBy(
            () ->
                new PayerSteps(
                    AUTRA,
                    Map.of(PayerOutcome.PAID, "the payer paid it in their own bank app"),
                    Map.of(PayerOutcome.DECLINED, NO_DECLINED_STATE),
                    WHAT_TAKING_ONE_DOES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider autra leaves the ABANDONED step")
        .hasMessageContaining(PayerSteps.IT_SAYS_NEITHER);
  }

  @Test
  void a_step_declared_as_offered_and_as_absent_at_once_is_refused_before_a_screen_draws_it() {
    assertThatThrownBy(
            () ->
                new PayerSteps(
                    AUTRA,
                    Map.of(
                        PayerOutcome.PAID,
                        "the payer paid it in their own bank app",
                        PayerOutcome.DECLINED,
                        "the payer met a decline",
                        PayerOutcome.ABANDONED,
                        "the payer let it expire"),
                    Map.of(PayerOutcome.DECLINED, NO_DECLINED_STATE),
                    WHAT_TAKING_ONE_DOES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider autra declares the DECLINED step")
        .hasMessageContaining(PayerSteps.IT_SAYS_BOTH);
  }

  @Test
  void a_step_offered_under_no_words_at_all_is_refused_rather_than_drawn_as_a_blank_choice() {
    assertThatThrownBy(
            () ->
                new PayerSteps(
                    AUTRA,
                    Map.of(
                        PayerOutcome.PAID,
                        "the payer paid it in their own bank app",
                        PayerOutcome.ABANDONED,
                        "   "),
                    Map.of(PayerOutcome.DECLINED, NO_DECLINED_STATE),
                    WHAT_TAKING_ONE_DOES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider autra offers the ABANDONED step and names it with nothing");
  }

  @Test
  void a_step_left_out_under_no_reason_at_all_is_refused_rather_than_worded_by_this_simulator() {
    assertThatThrownBy(
            () ->
                new PayerSteps(
                    AUTRA,
                    Map.of(PayerOutcome.PAID, "the payer paid it in their own bank app"),
                    Map.of(PayerOutcome.DECLINED, NO_DECLINED_STATE, PayerOutcome.ABANDONED, " "),
                    WHAT_TAKING_ONE_DOES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider autra offers no ABANDONED step")
        .hasMessageContaining("a decision this simulator did not make");
  }

  @Test
  void an_object_with_no_step_left_on_it_is_refused_because_a_form_would_have_nothing_to_pick() {
    assertThatThrownBy(
            () ->
                new PayerSteps(
                    AUTRA,
                    Map.of(),
                    Map.of(
                        PayerOutcome.PAID,
                        "this one is already paid",
                        PayerOutcome.DECLINED,
                        NO_DECLINED_STATE,
                        PayerOutcome.ABANDONED,
                        "this one has already expired"),
                    WHAT_TAKING_ONE_DOES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PayerSteps.IT_NAMES_NO_STEP_AT_ALL)
        .hasMessageContaining("provider autra offers a payer step on this object and names none a")
        .hasMessageContaining("screen would draw a form with nothing in it to pick")
        .hasMessageContaining("offered as NotOffered instead");
  }

  @Test
  void the_steps_a_provider_declared_are_held_against_a_caller_changing_the_maps_afterwards() {
    Map<PayerOutcome, String> offered = new HashMap<>();
    offered.put(PayerOutcome.PAID, "the payer paid it in their own bank app");
    Map<PayerOutcome, String> instead = new HashMap<>();
    instead.put(PayerOutcome.DECLINED, NO_DECLINED_STATE);
    instead.put(PayerOutcome.ABANDONED, "this one expires instead");
    PayerSteps declared = new PayerSteps(AUTRA, offered, instead, WHAT_TAKING_ONE_DOES);

    offered.put(PayerOutcome.ABANDONED, "a step nothing declared");
    instead.remove(PayerOutcome.DECLINED);

    assertThat(declared.theStepsAPayerMayTake()).containsExactly(PayerOutcome.PAID);
    assertThat(declared.whatItHasInsteadOf(PayerOutcome.DECLINED)).isEqualTo(NO_DECLINED_STATE);
  }

  @Test
  void the_steps_it_accounts_for_without_offering_are_the_ones_a_screen_explains_the_absence_of() {
    assertThat(WHAT_A_PIX_ADMITS.theStepsItAccountsForWithoutOffering())
        .containsExactly(PayerOutcome.DECLINED);
    assertThat(WHAT_A_PIX_ADMITS.theStepsItAccountsForWithoutOffering())
        .doesNotContain(PayerOutcome.PAID, PayerOutcome.ABANDONED);
  }

  @Test
  void a_vendor_page_says_each_offered_step_in_the_words_that_provider_put_on_its_own_page() {
    Map<PayerOutcome, String> onItsOwnPage =
        Map.of(PayerOutcome.PAID, "Já paguei", PayerOutcome.ABANDONED, "Sair sem pagar");

    assertThat(WHAT_A_PIX_ADMITS.asItsOwnPageSaysIt(onItsOwnPage, PayerOutcome.PAID))
        .isEqualTo("Já paguei");
    assertThat(WHAT_A_PIX_ADMITS.asItsOwnPageSaysIt(onItsOwnPage, PayerOutcome.ABANDONED))
        .isEqualTo("Sair sem pagar");
  }

  @Test
  void a_step_this_providers_own_page_has_no_words_for_is_a_fault_and_never_a_blank_or_a_null() {
    Map<PayerOutcome, String> onlyOneIsWorded = Map.of(PayerOutcome.PAID, "Já paguei");

    assertThatThrownBy(
            () -> WHAT_A_PIX_ADMITS.asItsOwnPageSaysIt(onlyOneIsWorded, PayerOutcome.ABANDONED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider autra offers the ABANDONED step")
        .hasMessageContaining(PayerSteps.ITS_OWN_PAGE_HAS_NO_WORDS_FOR_IT)
        .hasMessageContaining("its own payer page publishes no words of its own for it")
        .hasMessageContaining("the page a merchant's own payer is sent to");
    assertThatThrownBy(
            () ->
                WHAT_A_PIX_ADMITS.asItsOwnPageSaysIt(
                    Map.of(PayerOutcome.PAID, "   "), PayerOutcome.PAID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider autra offers the PAID step");
  }

  @Test
  void a_provider_that_will_not_say_what_taking_a_step_does_is_refused_rather_than_guessed_for() {
    assertThatThrownBy(
            () ->
                new PayerSteps(
                    AUTRA,
                    Map.of(PayerOutcome.PAID, "the payer paid it in their own bank app"),
                    Map.of(
                        PayerOutcome.DECLINED,
                        NO_DECLINED_STATE,
                        PayerOutcome.ABANDONED,
                        "this one expires instead"),
                    "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say in its own words what taking one does to it")
        .hasMessageContaining("a card would be charged on an object that collects none");
  }
}
