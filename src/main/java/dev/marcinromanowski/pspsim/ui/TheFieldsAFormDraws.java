package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class TheFieldsAFormDraws {

  public static final String THIS_RUN_HAS_PRODUCED_NO_VALUE_THIS_FIELD_COULD_SUGGEST =
      "This test run has produced no value this field could suggest yet, so the box suggests"
          + " none. What it would suggest is what this run has already done rather than anything"
          + " this provider publishes, and the sentence beside this field says what has to happen"
          + " first. The box still takes any value at all, and the provider this value is read by"
          + " answers it in its own words.";

  public static final String THE_VALUES_THIS_PROVIDER_PUBLISHES_ARE_OFFERED_AND_NONE_IS_IMPOSED =
      "These are the values this provider publishes for this field, each under the sentence the"
          + " provider wrote for it, and the box takes any value at all. Nothing on this screen"
          + " judges what you type: the provider is asked with the value you typed, so the refusal"
          + " it publishes for a value it does not name is reachable here in the provider's own"
          + " words.";

  public static final String THE_VALUES_THIS_RUN_PRODUCED_ARE_SUGGESTED_AND_THE_PROVIDER_JUDGES =
      "These are the values this test run has already produced for this field, which is a fact"
          + " about this run and not a vocabulary this provider published. The box takes any"
          + " value at all, because this value is forwarded to the provider and the provider is"
          + " what reads it: the refusal it publishes for a value it never issued is reachable"
          + " here in the provider's own words, by typing one.";

  public static final String THE_SUGGESTIONS_OF_A_VOCABULARY_A_PROVIDER_PUBLISHES =
      "-the-values-this-provider-publishes";

  public static final String THE_SUGGESTIONS_OF_WHAT_THIS_TEST_RUN_HAS_PRODUCED =
      "-the-values-this-test-run-has-produced";

  public record Choice(String value, String label, boolean chosen) {}

  public record Field(
      String key,
      String label,
      boolean required,
      String whatItIsFor,
      String value,
      @Nullable String example,
      List<Choice> choices,
      boolean thisSimulatorIsTheOneThatConsumesTheValue,
      boolean theValuesItSuggestsAreTheOnesThisRunProduced) {

    public Field {
      choices = List.copyOf(choices);
    }

    public boolean isTypedIn() {
      return !thisSimulatorIsTheOneThatConsumesTheValue;
    }

    public boolean itSuggestsTheOffersBesideItWithoutImposingThem() {
      return isTypedIn() && !choices.isEmpty();
    }

    public String theSuggestionsOnIt() {
      return key
          + (theValuesItSuggestsAreTheOnesThisRunProduced
              ? THE_SUGGESTIONS_OF_WHAT_THIS_TEST_RUN_HAS_PRODUCED
              : THE_SUGGESTIONS_OF_A_VOCABULARY_A_PROVIDER_PUBLISHES);
    }

    public String whyTheseAreSuggestedAndTheBoxStillTakesAnother() {
      return theValuesItSuggestsAreTheOnesThisRunProduced
          ? THE_VALUES_THIS_RUN_PRODUCED_ARE_SUGGESTED_AND_THE_PROVIDER_JUDGES
          : THE_VALUES_THIS_PROVIDER_PUBLISHES_ARE_OFFERED_AND_NONE_IS_IMPOSED;
    }

    public boolean thisRunHasProducedNoneOfThese() {
      return theValuesItSuggestsAreTheOnesThisRunProduced && choices.isEmpty();
    }

    public String whatHasToHappenBeforeThisRunSuggestsOne() {
      return THIS_RUN_HAS_PRODUCED_NO_VALUE_THIS_FIELD_COULD_SUGGEST;
    }

    public String theParameterItArrivesUnder() {
      return TheParameterATestersValueArrivesUnder.onAFormControlFor(key);
    }
  }

  public record FilledIn(String key, String why) {}

  private TheFieldsAFormDraws() {}

  public static List<Field> drawnFrom(
      List<AFieldATesterFillsIn> theOnesItAsksFor,
      List<String> callbackTargets,
      Map<String, List<AValueAFieldOffers>> theValuesThisRunHasAlreadyProduced,
      Map<String, String> submitted) {
    List<Field> fields = new ArrayList<>();
    for (AFieldATesterFillsIn declared : theOnesItAsksFor) {
      String typed = submitted.getOrDefault(declared.key(), "");
      fields.add(
          new Field(
              declared.key(),
              declared.label(),
              declared.required(),
              declared.whatItIsFor(),
              typed,
              declared.example(),
              choicesOf(declared, callbackTargets, theValuesThisRunHasAlreadyProduced, typed),
              declared.thisSimulatorIsTheOneThatConsumesTheValue(),
              declared.namesAValueThisTestRunHasAlreadyProduced()));
    }
    return List.copyOf(fields);
  }

  private static List<Choice> choicesOf(
      AFieldATesterFillsIn declared,
      List<String> callbackTargets,
      Map<String, List<AValueAFieldOffers>> theValuesThisRunHasAlreadyProduced,
      String typed) {
    List<Choice> offered = new ArrayList<>();
    if (declared.thisSimulatorIsTheOneThatConsumesTheValue()) {
      for (String target : callbackTargets) {
        offered.add(new Choice(target, target, target.equals(typed)));
      }
      return List.copyOf(offered);
    }
    if (declared.namesAValueThisTestRunHasAlreadyProduced()) {
      for (AValueAFieldOffers produced :
          theValuesThisRunHasAlreadyProduced.getOrDefault(declared.key(), List.of())) {
        offered.add(new Choice(produced.value(), produced.label(), produced.value().equals(typed)));
      }
      return List.copyOf(offered);
    }
    for (AValueAFieldOffers choice : declared.choices()) {
      offered.add(new Choice(choice.value(), choice.label(), choice.value().equals(typed)));
    }
    return List.copyOf(offered);
  }
}
