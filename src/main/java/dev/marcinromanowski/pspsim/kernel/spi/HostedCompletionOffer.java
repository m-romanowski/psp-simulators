package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public sealed interface HostedCompletionOffer {

  ProviderId provider();

  boolean namesACard();

  sealed interface WithAPayerStep extends HostedCompletionOffer {

    PayerSteps theStepsItOffers();
  }

  record Offered(
      ProviderId provider, List<PayerCard> cardsAPayerMayName, PayerSteps theStepsItOffers)
      implements WithAPayerStep {

    public Offered {
      if (cardsAPayerMayName.isEmpty()) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " offers this page for completion and names no card a payer may finish it with,"
                + " and a completion reporting that the payer paid carries the card they used, so"
                + " a screen drawn from this would offer a tester an empty list to pick from. A"
                + " page whose payer names no card at all is offered as OfferedWithoutACard"
                + " instead, which says what the payer pays with there.");
      }
      Set<String> aliases = new LinkedHashSet<>();
      for (PayerCard card : cardsAPayerMayName) {
        if (!aliases.add(card.alias())) {
          throw new IllegalArgumentException(
              "provider "
                  + provider.value()
                  + " offers the card "
                  + card.alias()
                  + " twice, so a screen would draw one card under two labels and a tester could"
                  + " not tell which of them they had picked");
        }
      }
      theStepsMustBeTheOnesThisProviderDeclared(provider, theStepsItOffers);
      cardsAPayerMayName = List.copyOf(cardsAPayerMayName);
    }

    @Override
    public boolean namesACard() {
      return true;
    }

    public Optional<PayerCard> card(String alias) {
      for (PayerCard offered : cardsAPayerMayName) {
        if (offered.alias().equals(alias)) {
          return Optional.of(offered);
        }
      }
      return Optional.empty();
    }

    public String theCardsItOffers() {
      List<String> offered = new ArrayList<>();
      for (PayerCard card : cardsAPayerMayName) {
        offered.add(card.label() + " (" + card.alias() + ")");
      }
      return String.join(", ", offered);
    }
  }

  record OfferedWithoutACard(
      ProviderId provider, String whatThePayerPaysWithInstead, PayerSteps theStepsItOffers)
      implements WithAPayerStep {

    public OfferedWithoutACard {
      if (whatThePayerPaysWithInstead.isBlank()) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " offers a payer step on this object that collects no card and must say in its"
                + " own words what the payer pays with there, because a screen that offered no card"
                + " and no reason would read as a card list this simulator failed to fill rather"
                + " than as a page that never had one");
      }
      theStepsMustBeTheOnesThisProviderDeclared(provider, theStepsItOffers);
    }

    @Override
    public boolean namesACard() {
      return false;
    }
  }

  record NotOffered(ProviderId provider, String whatThisProviderHasInstead)
      implements HostedCompletionOffer {

    public NotOffered {
      if (whatThisProviderHasInstead.isBlank()) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " offers no payer step on this object and must say in its own words what its"
                + " contract has in place of one, because a screen reporting that absence in this"
                + " simulator's words would be reporting a decision this simulator did not make");
      }
    }

    @Override
    public boolean namesACard() {
      return false;
    }
  }

  String THE_STEPS_OF_ANOTHER_PROVIDER =
      ", so a screen would draw one provider's object under another provider's words";

  private static void theStepsMustBeTheOnesThisProviderDeclared(
      ProviderId provider, PayerSteps steps) {
    if (!provider.equals(steps.provider())) {
      throw new IllegalArgumentException(
          "provider "
              + provider.value()
              + " offers a payer step on this object and names the steps provider "
              + steps.provider().value()
              + " declared"
              + THE_STEPS_OF_ANOTHER_PROVIDER);
    }
  }
}
