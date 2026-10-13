package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public record HostedCompletionRequest(
    TestRunId testRunId,
    String providerObjectId,
    PayerOutcome outcome,
    @Nullable String card,
    ObjectOrigin askedBy) {

  public static final String A_CARD_IS_REQUIRED =
      "name the card the payer used, by the alias a provider publishes for one of its test cards or"
          + " by the alias of a payment-method fixture seeded into this test run; this simulator"
          + " does not choose one, because the card a hosted page collects is the payer's and not a"
          + " value a completion may invent";

  public static final String A_PAYER_WHO_DID_NOT_PAY_LEAVES_NO_CARD =
      " leaves no card behind for anything to report, so a caller naming one is naming a value"
          + " neither the response nor the callback of this completion carries";

  public static final String THIS_PAGE_COLLECTS_NO_CARD =
      "this page collects no card from the payer, so a completion of it names none: ";

  public HostedCompletionRequest {
    if (providerObjectId.isBlank()) {
      throw new IllegalArgumentException(
          "a completion names the hosted process it completes by the id the provider gave it, and"
              + " this one names none");
    }
    if (askedBy == ObjectOrigin.SCHEDULED_JOB) {
      throw new IllegalArgumentException(
          "the delivery queue moves the callbacks a completion has already caused and never causes"
              + " one, so no scheduled job completes a hosted page");
    }
    if (card != null && !outcome.namesACard()) {
      throw new IllegalArgumentException(
          "a payer who " + outcome.whatThePayerDid() + A_PAYER_WHO_DID_NOT_PAY_LEAVES_NO_CARD);
    }
  }

  public static HostedCompletionRequest of(
      HostedCompletionOffer offer,
      TestRunId testRunId,
      String providerObjectId,
      PayerOutcome outcome,
      @Nullable String card,
      ObjectOrigin askedBy) {
    String named = card == null || card.isBlank() ? null : card.strip();
    if (offer instanceof HostedCompletionOffer.WithAPayerStep withAPayerStep
        && !withAPayerStep.theStepsItOffers().offers(outcome)) {
      throw new IllegalArgumentException(
          withAPayerStep.theStepsItOffers().whatItHasInsteadOf(outcome));
    }
    if (named == null && outcome.namesACard() && offer.namesACard()) {
      throw new IllegalArgumentException(A_CARD_IS_REQUIRED);
    }
    if (named != null && offer instanceof HostedCompletionOffer.OfferedWithoutACard without) {
      throw new IllegalArgumentException(
          THIS_PAGE_COLLECTS_NO_CARD + without.whatThePayerPaysWithInstead());
    }
    return new HostedCompletionRequest(
        testRunId, providerObjectId.strip(), outcome, named, askedBy);
  }
}
