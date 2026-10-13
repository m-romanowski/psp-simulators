package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerSteps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

public final class HostedCompletionForm {

  public static final String TICK_CONFIRM_BEFORE_TAKING_THIS_PAYER_STEP =
      "tick confirm before taking this payer step: ";

  public static final String AND_NONE_OF_IT_CAN_BE_UNDONE =
      ", and none of it can be undone by leaving this screen";

  public static final String THE_PAGE_IS_GONE =
      "this test run holds no object of this provider under that id any more; the run may have"
          + " been reset while this page was open";

  public static final String NO_STEP_TO_REPORT =
      "a payer step this simulator applied was asked of an offer that has none on it, so there is"
          + " no step of this provider's to say back to a tester; an offer with no payer step on it"
          + " is refused before anything is applied";

  public record Met(String outcome, String asATesterReadsIt, boolean namesACard, boolean chosen) {}

  public record Unoffered(String outcome, String whatThePayerDid, String whatItHasInstead) {}

  public record Card(String alias, String label, boolean chosen) {}

  public record View(
      String provider,
      String testRunId,
      String providerObjectId,
      List<Met> outcomes,
      List<Unoffered> unoffered,
      List<Card> cards,
      @Nullable String whatThisProviderHasInstead,
      @Nullable String whatThePayerPaysWithInstead,
      @Nullable String whatTakingOneDoes) {

    public View {
      outcomes = List.copyOf(outcomes);
      unoffered = List.copyOf(unoffered);
      cards = List.copyOf(cards);
    }

    public boolean offersAPayerStep() {
      return whatThisProviderHasInstead == null;
    }

    public boolean namesACard() {
      return whatThePayerPaysWithInstead == null;
    }

    public String theCautionBeforeTakingOne() {
      return theCautionOf(String.valueOf(whatTakingOneDoes));
    }
  }

  private HostedCompletionForm() {}

  public static View of(
      String provider,
      String testRunId,
      String providerObjectId,
      HostedCompletionOffer offer,
      @Nullable String outcome,
      @Nullable String card) {
    return switch (offer) {
      case HostedCompletionOffer.NotOffered notOffered ->
          new View(
              provider,
              testRunId,
              providerObjectId,
              List.of(),
              List.of(),
              List.of(),
              notOffered.whatThisProviderHasInstead(),
              null,
              null);
      case HostedCompletionOffer.OfferedWithoutACard without ->
          new View(
              provider,
              testRunId,
              providerObjectId,
              theStepsThisObjectAdmits(without.theStepsItOffers(), outcome),
              theStepsThisObjectDoesNotAdmit(without.theStepsItOffers()),
              List.of(),
              null,
              without.whatThePayerPaysWithInstead(),
              without.theStepsItOffers().whatTakingOneDoes());
      case HostedCompletionOffer.Offered offered ->
          new View(
              provider,
              testRunId,
              providerObjectId,
              theStepsThisObjectAdmits(offered.theStepsItOffers(), outcome),
              theStepsThisObjectDoesNotAdmit(offered.theStepsItOffers()),
              theCardsOffered(offered, card),
              null,
              null,
              offered.theStepsItOffers().whatTakingOneDoes());
    };
  }

  public static String theCautionBefore(HostedCompletionOffer offer) {
    return switch (offer) {
      case HostedCompletionOffer.NotOffered notOffered -> notOffered.whatThisProviderHasInstead();
      case HostedCompletionOffer.WithAPayerStep withAPayerStep ->
          theCautionOf(withAPayerStep.theStepsItOffers().whatTakingOneDoes());
    };
  }

  private static String theCautionOf(String whatTakingOneDoes) {
    return TICK_CONFIRM_BEFORE_TAKING_THIS_PAYER_STEP
        + whatTakingOneDoes
        + AND_NONE_OF_IT_CAN_BE_UNDONE;
  }

  public static String theStepItTook(HostedCompletionOffer offer, PayerOutcome outcome) {
    return switch (offer) {
      case HostedCompletionOffer.WithAPayerStep withAPayerStep ->
          withAPayerStep.theStepsItOffers().asATesterReadsIt(outcome);
      case HostedCompletionOffer.NotOffered _ -> throw new IllegalStateException(NO_STEP_TO_REPORT);
    };
  }

  public static String thePayerStepTaken(
      HostedCompletionOffer offer, HostedCompletionOutcome.Completed completed) {
    return Done.thePayerStepIsTaken(theStepItTook(offer, completed.outcome()));
  }

  private static List<Met> theStepsThisObjectAdmits(PayerSteps steps, @Nullable String chosen) {
    List<Met> admitted = new ArrayList<>();
    for (PayerOutcome step : steps.theStepsAPayerMayTake()) {
      admitted.add(
          new Met(
              step.name(),
              steps.asATesterReadsIt(step),
              step.namesACard(),
              step.name().equals(chosen)));
    }
    return List.copyOf(admitted);
  }

  private static List<Unoffered> theStepsThisObjectDoesNotAdmit(PayerSteps steps) {
    List<Unoffered> absent = new ArrayList<>();
    for (PayerOutcome step : steps.theStepsItAccountsForWithoutOffering()) {
      absent.add(
          new Unoffered(step.name(), step.whatThePayerDid(), steps.whatItHasInsteadOf(step)));
    }
    return List.copyOf(absent);
  }

  private static List<Card> theCardsOffered(
      HostedCompletionOffer.Offered offered, @Nullable String chosen) {
    List<Card> cards = new ArrayList<>();
    for (PayerCard card : offered.cardsAPayerMayName()) {
      cards.add(new Card(card.alias(), card.label(), card.alias().equals(chosen)));
    }
    return List.copyOf(cards);
  }

  public static @Nullable String refusalOf(HostedCompletionOutcome outcome) {
    return switch (outcome) {
      case HostedCompletionOutcome.Completed _ -> null;
      case HostedCompletionOutcome.RefusedByTheProvider refused -> refused.inItsOwnWords();
      case HostedCompletionOutcome.NotOffered notOffered -> notOffered.whatThisProviderHasInstead();
      case HostedCompletionOutcome.NoSuchObject _ -> THE_PAGE_IS_GONE;
    };
  }

  public static ActionVerdict.Refused theRefusalBeforeTakingOne(HostedCompletionOffer offer) {
    return switch (offer) {
      case HostedCompletionOffer.NotOffered notOffered ->
          new HostedCompletionOutcome.NotOffered(notOffered.whatThisProviderHasInstead())
              .theRefusalItReports();
      case HostedCompletionOffer.WithAPayerStep _ -> theRefusalOfAnUnconfirmedCompletion();
    };
  }

  public static ActionVerdict.Refused theRefusalOfAnUnconfirmedCompletion() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            HostedCompletionOutcome.THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM));
  }

  public static final int THE_STATUS_THIS_SCREEN_ANSWERS_A_COMPLETED_PAGE_WITH =
      HttpStatus.FOUND.value();

  public static Map<String, List<String>> theFactsOfACompletionThisScreenTurnedAway(
      @Nullable String outcome, @Nullable String card, String refusal) {
    return ActionFacts.none()
        .and("outcome", outcome)
        .and("card", card)
        .and(ActionFacts.REFUSED_BECAUSE, refusal)
        .asRecorded();
  }
}
