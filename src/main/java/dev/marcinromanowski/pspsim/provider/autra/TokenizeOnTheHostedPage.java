package dev.marcinromanowski.pspsim.provider.autra;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class TokenizeOnTheHostedPage {

  static final int SLUG_HEX_DIGITS = 16;

  static final String NO_CARD_OF_THIS_RUN_ANSWERS_TO_THAT_ALIAS =
      "no card this hosted card session offers answers to that alias. "
          + AutraPayerCards.AUTRA_PUBLISHES_NO_TEST_CARDS
          + ". The aliases this run offers are: ";

  static final String NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE =
      "provider autra publishes the failures of its hosted card page as a closed set of SDK error"
          + " codes the page hands the merchant application — configuration_error, token_expired,"
          + " message_origin_rejected, message_invalid, validation_error and tokenization_failed,"
          + " and \"The table below is the complete set of code values\" — and none of them is a"
          + " state of the session. A card page charges nothing, so there is no acquirer on it to"
          + " decline: a payer who does not finish leaves the session to expire, which is the"
          + " only outcome autra publishes for one";

  static final String WHAT_TAKING_A_STEP_ON_A_CARD_PAGE_DOES =
      "it runs the payer's own step on autra's hosted card page, which stores the card it names as"
          + " a token and charges nothing at all, and leaves the card session in the state autra"
          + " publishes for the step taken";

  static final PayerSteps THE_PAYER_STEPS =
      new PayerSteps(
          AutraApi.AUTRA,
          Map.of(
              PayerOutcome.PAID,
              "the payer typed a card on the page and autra tokenized it",
              PayerOutcome.ABANDONED,
              "the payer left the page without typing a card, and the session expires"),
          Map.of(PayerOutcome.DECLINED, NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE),
          WHAT_TAKING_A_STEP_ON_A_CARD_PAGE_DOES);

  private final AutraCardSessions sessions;
  private final AutraPayerCards cards;
  private final IdGenerator ids;

  TokenizeOnTheHostedPage(AutraCardSessions sessions, AutraPayerCards cards, IdGenerator ids) {
    this.sessions = sessions;
    this.cards = cards;
    this.ids = ids;
  }

  HostedCompletionOffer offerOn(AutraCardSession session, Instant now) {
    if (!session.isAwaitingThePayerAt(now)) {
      return new HostedCompletionOffer.NotOffered(
          AutraApi.AUTRA, session.whyItIsNoLongerWaitingAt(now));
    }
    return new HostedCompletionOffer.Offered(
        AutraApi.AUTRA, theCardsAPayerMayName(session.testRunId()), THE_PAYER_STEPS);
  }

  HostedCompletionOutcome complete(
      HostedCompletionRequest request, AutraCardSession session, Instant now) {
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
    Optional<AutraPayerCard> named =
        cards.resolve(session.testRunId(), String.valueOf(request.card()));
    if (named.isEmpty()) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          NO_CARD_OF_THIS_RUN_ANSWERS_TO_THAT_ALIAS + cards.aliasesOffered(session.testRunId()));
    }
    AutraPayerCard card = named.get();
    AutraCardSession tokenized = session.tokenizedAs(theResultOf(card), now);
    if (!sessions.moveFromPendingTo(tokenized)) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          sessions
              .findById(session.testRunId(), session.cardSessionId())
              .orElse(session)
              .whyItIsNoLongerWaitingAt(now));
    }
    return new HostedCompletionOutcome.Completed(
        tokenized.cardSessionId(), null, request.outcome(), List.of());
  }

  private HostedCompletionOutcome expired(AutraCardSession session, Instant now) {
    if (!sessions.moveFromPendingTo(session.expiredAt(now))) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          sessions
              .findById(session.testRunId(), session.cardSessionId())
              .orElse(session)
              .whyItIsNoLongerWaitingAt(now));
    }
    return new HostedCompletionOutcome.Completed(
        session.cardSessionId(), null, PayerOutcome.ABANDONED, List.of());
  }

  private AutraStoredCard theResultOf(AutraPayerCard card) {
    return new AutraStoredCard(
        card.namesASlugOfItsOwn() ? card.slugToken() : aSlug(),
        card.namesASlugOfItsOwn() ? card.slugStoredCard() : aSlug(),
        card.theExpiryAsAutraWritesIt(),
        card.brand(),
        card.first4(),
        card.last4(),
        Integer.valueOf(card.expirationMonth()),
        Integer.valueOf(card.expirationYear()));
  }

  private String aSlug() {
    return aSlug(ids);
  }

  static String aSlug(IdGenerator ids) {
    return ids.hex(SLUG_HEX_DIGITS).toUpperCase(Locale.ROOT);
  }

  private List<PayerCard> theCardsAPayerMayName(TestRunId testRunId) {
    List<PayerCard> offered = new ArrayList<>();
    for (AutraPayerCard card : cards.offeredBy(testRunId)) {
      offered.add(new PayerCard(card.alias(), card.describedForAPayer()));
    }
    return List.copyOf(offered);
  }
}
