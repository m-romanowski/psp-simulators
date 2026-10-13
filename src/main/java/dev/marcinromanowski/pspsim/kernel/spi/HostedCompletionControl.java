package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public final class HostedCompletionControl {

  public static final String AN_OUTCOME_IS_REQUIRED =
      "choose what the payer did. A payer step is the payer's own and this simulator does not pick"
          + " one for them, because what the payer did is the whole of what a payer step decides,"
          + " and which of them this object admits is its provider's to say.";

  private HostedCompletionControl() {}

  public static HostedCompletionRequest asked(
      HostedCompletionOffer offer,
      TestRunId testRunId,
      String providerObjectId,
      @Nullable String outcome,
      @Nullable String card,
      ObjectOrigin askedBy) {
    return switch (offer) {
      case HostedCompletionOffer.NotOffered notOffered ->
          throw new IllegalArgumentException(notOffered.whatThisProviderHasInstead());
      case HostedCompletionOffer.OfferedWithoutACard without ->
          theCompletionItOffers(without, testRunId, providerObjectId, outcome, card, askedBy);
      case HostedCompletionOffer.Offered offered ->
          theCompletionItOffers(
              offered,
              testRunId,
              providerObjectId,
              outcome,
              theCardItOffers(offered, card),
              askedBy);
    };
  }

  private static @Nullable String theCardItOffers(
      HostedCompletionOffer.Offered offered, @Nullable String card) {
    String named = card == null || card.isBlank() ? null : card.strip();
    if (named != null && offered.card(named).isEmpty()) {
      throw new IllegalArgumentException(
          "the payer of this page may name one of these cards and no other: "
              + offered.theCardsItOffers()
              + ". This completion named "
              + named
              + ", which provider "
              + offered.provider().value()
              + " does not offer in this test run. Seed it as a payment-method fixture of this run"
              + " if the payer is to have it.");
    }
    return named;
  }

  private static HostedCompletionRequest theCompletionItOffers(
      HostedCompletionOffer offer,
      TestRunId testRunId,
      String providerObjectId,
      @Nullable String outcome,
      @Nullable String card,
      ObjectOrigin askedBy) {
    if (outcome == null || outcome.isBlank()) {
      throw new IllegalArgumentException(AN_OUTCOME_IS_REQUIRED);
    }
    return HostedCompletionRequest.of(
        offer, testRunId, providerObjectId, PayerOutcome.declared(outcome.strip()), card, askedBy);
  }
}
