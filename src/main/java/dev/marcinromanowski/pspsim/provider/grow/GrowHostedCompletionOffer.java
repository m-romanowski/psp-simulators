package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerSteps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class GrowHostedCompletionOffer {

  static final String ONLY_A_PROCESS_HAS_A_PAYER_STEP =
      "provider grow leaves a payer step on a payment process and on nothing else, and this test"
          + " run holds no grow process under that id. A transaction is what a payer's completion"
          + " created: the card, the hold and the approval it carries are what that completion"
          + " already decided, and grow publishes no second payer step over them.";

  static final String WHAT_TAKING_A_STEP_ON_A_GROW_PAGE_DOES =
      "it runs the payer's own step on grow's hosted page, charges the card it names, creates the"
          + " transaction grow creates for a page the payer paid, and sends whatever callback grow"
          + " sends for the step taken";

  static final PayerSteps THE_PAYER_STEPS =
      new PayerSteps(
          GrowLightApi.GROW,
          Map.of(
              PayerOutcome.PAID,
              "the payer paid on the page",
              PayerOutcome.DECLINED,
              "the payer met a decline on the page",
              PayerOutcome.ABANDONED,
              "the payer left the page without paying"),
          Map.of(),
          WHAT_TAKING_A_STEP_ON_A_GROW_PAGE_DOES);

  private GrowHostedCompletionOffer() {}

  static HostedCompletionOffer of(
      @Nullable GrowPaymentProcess process, List<GrowPayerCard> cardsThisRunOffers) {
    if (process == null) {
      return new HostedCompletionOffer.NotOffered(
          GrowLightApi.GROW, ONLY_A_PROCESS_HAS_A_PAYER_STEP);
    }
    if (process.outcome() != null) {
      return new HostedCompletionOffer.NotOffered(
          GrowLightApi.GROW, CompleteHostedProcess.ALREADY_COMPLETED);
    }
    return new HostedCompletionOffer.Offered(
        GrowLightApi.GROW, payerCardsOf(cardsThisRunOffers), THE_PAYER_STEPS);
  }

  private static List<PayerCard> payerCardsOf(List<GrowPayerCard> offered) {
    List<PayerCard> cards = new ArrayList<>();
    for (GrowPayerCard card : offered) {
      cards.add(new PayerCard(card.alias(), card.describedForAPayer()));
    }
    return List.copyOf(cards);
  }
}
