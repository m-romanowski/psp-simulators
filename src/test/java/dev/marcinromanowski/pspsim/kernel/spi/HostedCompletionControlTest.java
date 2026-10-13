package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HostedCompletionControlTest {

  private static final TestRunId RUN = TestRunId.of("hosted-completion-control");

  private static final String A_PROCESS = "734754";

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
          "it runs the payer's own step on this page and charges the card it names");

  private static final HostedCompletionOffer.Offered GROW_OFFERS_TWO_CARDS =
      new HostedCompletionOffer.Offered(
          ProviderId.of("grow"),
          List.of(
              new PayerCard("sandbox-4580", "Visa •••• 4580, 0424"),
              new PayerCard("seeded-visa", "Visa •••• 1111, 1230")),
          WHAT_GROWS_PAGE_ADMITS);

  private static final String NO_DECLINED_STATE_ON_A_PIX =
      "provider autra publishes the Pix state machine in full and it has no declined state at all,"
          + " so a payer who does not pay one leaves it to expire";

  private static final PayerSteps WHAT_A_PIX_ADMITS =
      new PayerSteps(
          ProviderId.of("autra"),
          Map.of(
              PayerOutcome.PAID,
              "the payer pasted the payload into their own bank app and paid it",
              PayerOutcome.ABANDONED,
              "the payer never paid the payload and let it expire"),
          Map.of(PayerOutcome.DECLINED, NO_DECLINED_STATE_ON_A_PIX),
          "it records the payer of this Pix as having paid it or as having let it expire");

  private static final HostedCompletionOffer A_PIX =
      new HostedCompletionOffer.OfferedWithoutACard(
          ProviderId.of("autra"),
          "the payer pays this payload in their own bank app",
          WHAT_A_PIX_ADMITS);

  private static final HostedCompletionOffer AZERICARD_OFFERS_NO_PAGE =
      new HostedCompletionOffer.NotOffered(
          ProviderId.of("azericard"),
          "provider azericard publishes no server-to-server call that hands back a hosted page URL");

  @Test
  void a_payer_who_paid_on_a_card_this_run_offers_reaches_the_provider_as_that_completion() {
    HostedCompletionRequest asked =
        HostedCompletionControl.asked(
            GROW_OFFERS_TWO_CARDS, RUN, A_PROCESS, "PAID", "seeded-visa", ObjectOrigin.UI);

    assertThat(asked.outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(asked.card()).isEqualTo("seeded-visa");
    assertThat(asked.providerObjectId()).isEqualTo(A_PROCESS);
    assertThat(asked.askedBy()).isEqualTo(ObjectOrigin.UI);
  }

  @Test
  void a_payer_who_left_the_page_reaches_the_provider_naming_no_card() {
    HostedCompletionRequest asked =
        HostedCompletionControl.asked(
            GROW_OFFERS_TWO_CARDS, RUN, A_PROCESS, "ABANDONED", "", ObjectOrigin.UI);

    assertThat(asked.outcome()).isEqualTo(PayerOutcome.ABANDONED);
    assertThat(asked.card()).isNull();
  }

  @Test
  void a_provider_with_no_payer_step_refuses_the_completion_in_its_own_words() {
    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    AZERICARD_OFFERS_NO_PAGE, RUN, A_PROCESS, "PAID", "x", ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "provider azericard publishes no server-to-server call that hands back a hosted page"
                + " URL");
  }

  @Test
  void a_card_this_test_run_does_not_offer_is_refused_naming_the_cards_it_does() {
    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    GROW_OFFERS_TWO_CARDS, RUN, A_PROCESS, "PAID", "no-such-card", ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such-card")
        .hasMessageContaining("Visa •••• 4580, 0424 (sandbox-4580)")
        .hasMessageContaining("Visa •••• 1111, 1230 (seeded-visa)");
  }

  @Test
  void a_completion_that_names_no_outcome_is_refused_rather_than_given_one() {
    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    GROW_OFFERS_TWO_CARDS, RUN, A_PROCESS, null, null, ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(HostedCompletionControl.AN_OUTCOME_IS_REQUIRED)
        .hasMessageStartingWith("choose what the payer did")
        .hasMessageContaining("this simulator does not pick one for them")
        .hasMessageContaining("which of them this object admits is its provider's to say");
    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    GROW_OFFERS_TWO_CARDS, RUN, A_PROCESS, "  ", null, ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(HostedCompletionControl.AN_OUTCOME_IS_REQUIRED)
        .hasMessageStartingWith("choose what the payer did")
        .hasMessageNotContaining("on the page");
  }

  @Test
  void a_step_this_object_accounts_for_without_offering_is_refused_in_that_providers_own_words() {
    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    A_PIX, RUN, A_PROCESS, "DECLINED", null, ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(NO_DECLINED_STATE_ON_A_PIX)
        .hasMessageContaining("no declined state at all");
  }

  @Test
  void the_steps_this_object_does_offer_are_asked_of_the_provider_untouched() {
    assertThat(
            HostedCompletionControl.asked(A_PIX, RUN, A_PROCESS, "PAID", null, ObjectOrigin.UI)
                .outcome())
        .isEqualTo(PayerOutcome.PAID);
    assertThat(
            HostedCompletionControl.asked(A_PIX, RUN, A_PROCESS, "ABANDONED", null, ObjectOrigin.UI)
                .outcome())
        .isEqualTo(PayerOutcome.ABANDONED);
  }

  @Test
  void an_outcome_no_payer_can_meet_is_refused_with_the_three_that_can() {
    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    GROW_OFFERS_TWO_CARDS, RUN, A_PROCESS, "EXPIRED", null, ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PAID, DECLINED, ABANDONED");
  }

  @Test
  void a_payer_who_paid_and_named_no_card_is_refused_before_the_provider_is_called() {
    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    GROW_OFFERS_TWO_CARDS, RUN, A_PROCESS, "PAID", null, ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(HostedCompletionRequest.A_CARD_IS_REQUIRED);
  }

  @Test
  void a_card_picked_beside_an_outcome_that_leaves_none_is_refused_rather_than_dropped() {
    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    GROW_OFFERS_TWO_CARDS,
                    RUN,
                    A_PROCESS,
                    "DECLINED",
                    "sandbox-4580",
                    ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(HostedCompletionRequest.A_PAYER_WHO_DID_NOT_PAY_LEAVES_NO_CARD);
  }

  @Test
  void an_outcome_and_a_card_a_screen_sent_with_spaces_around_them_are_read_as_named() {
    HostedCompletionRequest asked =
        HostedCompletionControl.asked(
            GROW_OFFERS_TWO_CARDS, RUN, A_PROCESS, " PAID ", " sandbox-4580 ", ObjectOrigin.UI);

    assertThat(asked.outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(asked.card()).isEqualTo("sandbox-4580");
  }
}
