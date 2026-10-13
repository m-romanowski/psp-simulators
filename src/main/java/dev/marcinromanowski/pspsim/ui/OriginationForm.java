package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAStartedPaymentLeaves;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

public final class OriginationForm {

  public static final String THE_RUN_CONFIGURES_NO_ACCOUNT =
      "This test run configures no account for this provider, and a payment is started on one:"
          + " the provider signs the call with that account's own credentials. Configure an"
          + " account on the Configuration screen, under Configure an account, and come back to"
          + " this wizard.";

  public static final String NO_CALLBACK_TARGET_IS_CONFIGURED =
      "This test run configures no callback target for this provider, so the callback this"
          + " payment causes will have nowhere to go. Add one on the Configuration screen, under"
          + " the account this payment is started on, before you start the payment. Starting it"
          + " without one still works and leaves the callback unaddressable.";

  public static final int THE_STATUS_THIS_SCREEN_ANSWERS_A_STARTED_PAYMENT_WITH =
      HttpStatus.FOUND.value();

  public static final String THE_ACCOUNT_IS_GONE =
      "the account this payment named is not one this test run configures any more; the run may"
          + " have been reset while this wizard was open";

  public enum Panel {
    THE_PAYMENT_EXISTS_NOW,
    NO_ACCOUNT_IS_CONFIGURED,
    WHICH_KIND_OF_PAYMENT,
    WHAT_THIS_PAYMENT_SAYS
  }

  public record Way(
      String name,
      String label,
      String whatItLeavesBehind,
      String whereThePayerFinishesIt,
      boolean chosen) {}

  public record View(
      String provider,
      String testRunId,
      String accountId,
      String startedAs,
      List<String> accountsThisRunConfigures,
      List<String> callbackTargetsThisRunConfigures,
      List<Way> ways,
      @Nullable Way chosenWay,
      List<TheFieldsAFormDraws.Field> fields,
      List<TheFieldsAFormDraws.FilledIn> filledIn,
      @Nullable String startedObjectId) {

    public View {
      accountsThisRunConfigures = List.copyOf(accountsThisRunConfigures);
      callbackTargetsThisRunConfigures = List.copyOf(callbackTargetsThisRunConfigures);
      ways = List.copyOf(ways);
      fields = List.copyOf(fields);
      filledIn = List.copyOf(filledIn);
    }

    public Panel panel() {
      if (startedObjectId != null) {
        return Panel.THE_PAYMENT_EXISTS_NOW;
      }
      if (accountsThisRunConfigures.isEmpty()) {
        return Panel.NO_ACCOUNT_IS_CONFIGURED;
      }
      if (chosenWay == null) {
        return Panel.WHICH_KIND_OF_PAYMENT;
      }
      return Panel.WHAT_THIS_PAYMENT_SAYS;
    }

    public boolean noCallbackTargetIsConfigured() {
      return callbackTargetsThisRunConfigures.isEmpty();
    }

    public String whatToDoWithNoAccount() {
      return THE_RUN_CONFIGURES_NO_ACCOUNT;
    }

    public String whatToDoWithNoCallbackTarget() {
      return NO_CALLBACK_TARGET_IS_CONFIGURED;
    }
  }

  private OriginationForm() {}

  public static View of(
      String provider,
      String testRunId,
      @Nullable String accountId,
      @Nullable String startedAs,
      ProviderOriginationFields declared,
      List<String> accountsThisRunConfigures,
      List<String> callbackTargetsThisRunConfigures,
      Map<String, List<AValueAFieldOffers>> theValuesThisRunHasAlreadyProduced,
      Map<String, String> submitted,
      @Nullable String startedObjectId) {
    String named = startedAs == null ? "" : startedAs.strip();
    DeclaredOrigination chosen = declared.wayNamed(named).orElse(null);
    return new View(
        provider,
        testRunId,
        accountId == null ? "" : accountId.strip(),
        named,
        accountsThisRunConfigures,
        callbackTargetsThisRunConfigures,
        waysOf(declared, chosen),
        chosen == null ? null : wayOf(chosen, true),
        fieldsOf(
            chosen,
            callbackTargetsThisRunConfigures,
            theValuesThisRunHasAlreadyProduced,
            submitted),
        filledInBy(declared),
        startedObjectId);
  }

  private static List<Way> waysOf(
      ProviderOriginationFields declared, @Nullable DeclaredOrigination chosen) {
    List<Way> ways = new ArrayList<>();
    for (DeclaredOrigination way : declared.waysToStartAPayment()) {
      ways.add(wayOf(way, chosen != null && chosen.name().equals(way.name())));
    }
    return List.copyOf(ways);
  }

  private static Way wayOf(DeclaredOrigination way, boolean chosen) {
    return new Way(
        way.name(),
        way.label(),
        way.whatItLeavesBehind(),
        way.thePayerStep().asATesterReadsIt(),
        chosen);
  }

  private static List<TheFieldsAFormDraws.Field> fieldsOf(
      @Nullable DeclaredOrigination chosen,
      List<String> callbackTargets,
      Map<String, List<AValueAFieldOffers>> theValuesThisRunHasAlreadyProduced,
      Map<String, String> submitted) {
    if (chosen == null) {
      return List.of();
    }
    return TheFieldsAFormDraws.drawnFrom(
        chosen.fields(), callbackTargets, theValuesThisRunHasAlreadyProduced, submitted);
  }

  private static List<TheFieldsAFormDraws.FilledIn> filledInBy(ProviderOriginationFields declared) {
    List<TheFieldsAFormDraws.FilledIn> filled = new ArrayList<>();
    for (Map.Entry<String, String> supplied : declared.fieldsItFillsInItself().entrySet()) {
      filled.add(new TheFieldsAFormDraws.FilledIn(supplied.getKey(), supplied.getValue()));
    }
    return List.copyOf(filled);
  }

  public static ActionVerdict.Refused theRefusalOfARunThatConfiguresNoAccount() {
    return ActionVerdict.refused(
        RefusalSubject.noObjectWasNamed(
            TheTrailRowAStartedPaymentLeaves.THE_PAYMENT_IT_ASKED_THIS_PROVIDER_TO_START));
  }

  public static ActionVerdict.Refused theRefusalOfAnOriginationThisSimulatorCannotRead() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            TheTrailRowAStartedPaymentLeaves.THE_PAYMENT_IT_ASKED_THIS_PROVIDER_TO_START));
  }

  public static ActionVerdict.@Nullable Refused theRefusalItReports(OriginationOutcome outcome) {
    ActionVerdict verdict =
        TheTrailRowAStartedPaymentLeaves.theVerdictOf(
            outcome, THE_STATUS_THIS_SCREEN_ANSWERS_A_STARTED_PAYMENT_WITH);
    return verdict instanceof ActionVerdict.Refused refused ? refused : null;
  }

  public static Map<String, List<String>> theFactsOfARequestTurnedAway(
      @Nullable String accountId, @Nullable String startedAs, String refusal) {
    return ActionFacts.none()
        .and(TheTrailRowAStartedPaymentLeaves.STARTED_AS, startedAs)
        .and(TheTrailRowAStartedPaymentLeaves.ACCOUNT_ID, accountId)
        .and(TheTrailRowAStartedPaymentLeaves.REFUSED_BECAUSE, refusal)
        .asRecorded();
  }

  public static @Nullable String refusalOf(OriginationOutcome outcome) {
    return switch (outcome) {
      case OriginationOutcome.Originated _ -> null;
      case OriginationOutcome.RefusedByTheProvider refused -> refused.inItsOwnWords();
      case OriginationOutcome.AnsweredByAScenarioInstead answered ->
          "a scenario answered this call instead of the provider, so no payment was started;"
              + " the rule that decided it is "
              + answered.scenarioId();
      case OriginationOutcome.NotOffered notOffered -> notOffered.whatThisProviderHasInstead();
      case OriginationOutcome.NoSuchAccount _ -> THE_ACCOUNT_IS_GONE;
    };
  }
}
