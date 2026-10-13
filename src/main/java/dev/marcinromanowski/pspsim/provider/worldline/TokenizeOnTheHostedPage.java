package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerSteps;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TokenizeOnTheHostedPage {

  static final String NO_CARD_OF_THIS_RUN_ANSWERS_TO_THAT_ALIAS =
      "no card this hosted tokenization session offers answers to that alias. "
          + WorldlinePayerCards.WORLDLINE_PUBLISHES_NO_TEST_CARD_LIST_ON_THESE_PAGES
          + ". The aliases this run offers are: ";

  static final String NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE =
      "provider worldline publishes the hosted tokenization page as a page that stores a card and"
          + " charges nothing: its answer is a token and a tokenStatus of UNCHANGED, CREATED or"
          + " UPDATED, and none of the three is a decline. There is no acquirer on that page to"
          + " decline anything, and the vendor publishes the decline of a card on CreatePayment"
          + " instead, as 402 with a paymentErrorResponse. A payer who does not finish leaves the"
          + " session to time out, which is the only other outcome worldline publishes for one";

  static final String WHAT_TAKING_A_STEP_ON_A_TOKENIZATION_PAGE_DOES =
      "it runs the payer's own step on worldline's hosted tokenization page, which stores the card"
          + " it names as a token and charges nothing at all, and leaves the session in the state"
          + " worldline publishes for the step taken";

  static final PayerSteps THE_PAYER_STEPS =
      new PayerSteps(
          WorldlineApi.WORLDLINE,
          Map.of(
              PayerOutcome.PAID,
              "the payer typed a card on the page and worldline stored it as a token",
              PayerOutcome.ABANDONED,
              "the payer left the page without storing a card, and the session times out"),
          Map.of(PayerOutcome.DECLINED, NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE),
          WHAT_TAKING_A_STEP_ON_A_TOKENIZATION_PAGE_DOES);

  private final WorldlineTokenizations sessions;
  private final WorldlinePayerCards cards;
  private final IdGenerator ids;

  TokenizeOnTheHostedPage(
      WorldlineTokenizations sessions, WorldlinePayerCards cards, IdGenerator ids) {
    this.sessions = sessions;
    this.cards = cards;
    this.ids = ids;
  }

  HostedCompletionOffer offerOn(WorldlineTokenization session, Instant now) {
    if (!session.isAwaitingThePayerAt(now)) {
      return new HostedCompletionOffer.NotOffered(
          WorldlineApi.WORLDLINE, session.whyItIsNoLongerWaitingAt(now));
    }
    return new HostedCompletionOffer.Offered(
        WorldlineApi.WORLDLINE, theCardsAPayerMayName(session.testRunId()), THE_PAYER_STEPS);
  }

  HostedCompletionOutcome complete(
      HostedCompletionRequest request, WorldlineTokenization session, Instant now) {
    if (!THE_PAYER_STEPS.offers(request.outcome())) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          THE_PAYER_STEPS.whatItHasInsteadOf(request.outcome()));
    }
    if (!session.isAwaitingThePayerAt(now)) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          session.whyItIsNoLongerWaitingAt(now));
    }
    if (request.outcome() == PayerOutcome.ABANDONED) {
      return expired(session, now);
    }
    Optional<WorldlinePayerCard> named =
        cards.resolve(session.testRunId(), String.valueOf(request.card()));
    if (named.isEmpty()) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          NO_CARD_OF_THIS_RUN_ANSWERS_TO_THAT_ALIAS + cards.aliasesOffered(session.testRunId()));
    }
    WorldlinePayerCard card = named.get();
    WorldlineTokenization tokenized =
        session.tokenizedAs(
            card.namesATokenOfItsOwn()
                ? String.valueOf(card.seededToken())
                : WorldlineIdentifiers.aToken(ids),
            card.theCardNumberAsWorldlineWritesIt(),
            card.theExpiryDateAsWorldlineWritesIt(),
            now);
    if (!sessions.moveFromAwaitingThePayerTo(tokenized)) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          sessions
              .findById(session.testRunId(), session.hostedTokenizationId())
              .orElse(session)
              .whyItIsNoLongerWaitingAt(now));
    }
    return new HostedCompletionOutcome.Completed(
        tokenized.hostedTokenizationId(), null, request.outcome(), List.of());
  }

  private HostedCompletionOutcome expired(WorldlineTokenization session, Instant now) {
    if (!sessions.moveFromAwaitingThePayerTo(session.expiredAt(now))) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          sessions
              .findById(session.testRunId(), session.hostedTokenizationId())
              .orElse(session)
              .whyItIsNoLongerWaitingAt(now));
    }
    return new HostedCompletionOutcome.Completed(
        session.hostedTokenizationId(), null, PayerOutcome.ABANDONED, List.of());
  }

  private List<PayerCard> theCardsAPayerMayName(TestRunId testRunId) {
    List<PayerCard> offered = new ArrayList<>();
    for (WorldlinePayerCard card : cards.offeredBy(testRunId)) {
      offered.add(new PayerCard(card.alias(), card.describedForAPayer()));
    }
    return List.copyOf(offered);
  }
}
