package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record PayerSteps(
    ProviderId provider,
    Map<PayerOutcome, String> asATesterReadsThem,
    Map<PayerOutcome, String> whatItHasInsteadOfTheRest,
    String whatTakingOneDoes) {

  public static final String IT_SAYS_NEITHER =
      " and says neither what a tester reads on it nor what its contract has in place of it. A"
          + " payer step this simulator worded on a provider's behalf would read as a sentence"
          + " about a hosted page on an object that has none, and a step nothing accounts for"
          + " would be offered to a tester as a choice that must fail";

  public static final String IT_SAYS_BOTH =
      " both as a step a payer may take and as a step its contract does not have, so a screen"
          + " would offer a tester the very choice this provider refuses";

  public static final String ITS_OWN_PAGE_HAS_NO_WORDS_FOR_IT =
      " step on this object and its own payer page publishes no words of its own for it. A button"
          + " carrying nothing, or carrying the words a tester reads in this simulator's wizard,"
          + " puts a vocabulary of this simulator on the page a merchant's own payer is sent to";

  public static final String IT_NAMES_NO_STEP_AT_ALL =
      " offers a payer step on this object and names none a payer may take, so a screen would draw"
          + " a form with nothing in it to pick. An object with no step left on it is offered as"
          + " NotOffered instead, which says in this provider's words what stands in its place";

  public PayerSteps {
    if (whatTakingOneDoes.isBlank()) {
      throw new IllegalArgumentException(
          "provider "
              + provider.value()
              + " offers a payer step on this object and must say in its own words what taking one"
              + " does to it, because the caution a tester ticks confirm against is drawn from"
              + " those words, and a caution this simulator worded for every provider at once"
              + " warned a tester that a card would be charged on an object that collects none");
    }
    for (PayerOutcome step : PayerOutcome.values()) {
      String read = asATesterReadsThem.get(step);
      String instead = whatItHasInsteadOfTheRest.get(step);
      if (read == null && instead == null) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " leaves the "
                + step.name()
                + " step"
                + IT_SAYS_NEITHER);
      }
      if (read != null && instead != null) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " declares the "
                + step.name()
                + " step"
                + IT_SAYS_BOTH);
      }
      if (read != null && read.isBlank()) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " offers the "
                + step.name()
                + " step and names it with nothing, so a screen would draw a choice with no words"
                + " on it and a tester would pick between blanks");
      }
      if (instead != null && instead.isBlank()) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " offers no "
                + step.name()
                + " step and must say in its own words what its contract has in place of one,"
                + " because a screen reporting that absence in this simulator's words would be"
                + " reporting a decision this simulator did not make");
      }
    }
    if (asATesterReadsThem.isEmpty()) {
      throw new IllegalArgumentException("provider " + provider.value() + IT_NAMES_NO_STEP_AT_ALL);
    }
    asATesterReadsThem = Map.copyOf(asATesterReadsThem);
    whatItHasInsteadOfTheRest = Map.copyOf(whatItHasInsteadOfTheRest);
  }

  public boolean offers(PayerOutcome step) {
    return asATesterReadsThem.containsKey(step);
  }

  public String asATesterReadsIt(PayerOutcome step) {
    String read = asATesterReadsThem.get(step);
    if (read == null) {
      throw new IllegalStateException(
          "provider "
              + provider.value()
              + " offers no "
              + step.name()
              + " step on this object, so it publishes no words a tester reads on one");
    }
    return read;
  }

  public String whatItHasInsteadOf(PayerOutcome step) {
    String instead = whatItHasInsteadOfTheRest.get(step);
    if (instead == null) {
      throw new IllegalStateException(
          "provider "
              + provider.value()
              + " offers the "
              + step.name()
              + " step on this object, so it publishes nothing in place of it");
    }
    return instead;
  }

  public String asItsOwnPageSaysIt(Map<PayerOutcome, String> onItsOwnPage, PayerOutcome step) {
    String said = onItsOwnPage.get(step);
    if (said == null || said.isBlank()) {
      throw new IllegalStateException(
          "provider "
              + provider.value()
              + " offers the "
              + step.name()
              + ITS_OWN_PAGE_HAS_NO_WORDS_FOR_IT);
    }
    return said;
  }

  public List<PayerOutcome> theStepsAPayerMayTake() {
    List<PayerOutcome> offered = new ArrayList<>();
    for (PayerOutcome step : PayerOutcome.values()) {
      if (offers(step)) {
        offered.add(step);
      }
    }
    return List.copyOf(offered);
  }

  public List<PayerOutcome> theStepsItAccountsForWithoutOffering() {
    List<PayerOutcome> absent = new ArrayList<>();
    for (PayerOutcome step : PayerOutcome.values()) {
      if (!offers(step)) {
        absent.add(step);
      }
    }
    return List.copyOf(absent);
  }
}
