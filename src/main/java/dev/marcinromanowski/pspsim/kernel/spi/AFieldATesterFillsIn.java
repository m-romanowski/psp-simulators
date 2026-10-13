package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record AFieldATesterFillsIn(
    String key,
    String label,
    boolean required,
    String whatItIsFor,
    WhereAFieldsValuesComeFrom offering,
    List<AValueAFieldOffers> choices,
    @Nullable String example) {

  public AFieldATesterFillsIn {
    if (key.isBlank()) {
      throw new IllegalArgumentException(
          "a field with no key names nothing the provider reads, so neither the Start a payment"
              + " screen, nor the Look a payment up screen, nor a suite calling the route this"
              + " provider publishes the declaration on could send what a tester filled in"
              + " anywhere");
    }
    if (label.isBlank()) {
      throw new IllegalArgumentException(
          "field "
              + key
              + " must carry a label, because the Start a payment screen prints the label and"
              + " never the key, the Look a payment up screen prints it the same way, and the"
              + " route this provider publishes the declaration on answers it beside the key");
    }
    if (whatItIsFor.isBlank()) {
      throw new IllegalArgumentException(
          "field "
              + key
              + " must say, in this provider's own words, what the provider does with the value:"
              + " the Start a payment screen and the Look a payment up screen both lead a tester"
              + " who has read no provider contract, the route this provider publishes the"
              + " declaration on answers that sentence to a suite that has read none either, and a"
              + " field with no sentence beside it leaves both of them guessing");
    }
    Set<String> values = new LinkedHashSet<>();
    for (AValueAFieldOffers choice : choices) {
      if (!values.add(choice.value())) {
        throw new IllegalArgumentException(
            "field "
                + key
                + " offers the value "
                + choice.value()
                + " twice, so a tester would be offered one value under two labels and could not"
                + " tell which of them they had picked");
      }
    }
    if (offering == WhereAFieldsValuesComeFrom.THE_VALUES_THE_PROVIDER_DECLARES
        && choices.isEmpty()) {
      throw new IllegalArgumentException(
          "field "
              + key
              + " says the provider declares the values it accepts and then declares none, so the"
              + " Start a payment screen and the Look a payment up screen would offer a tester an"
              + " empty list to pick from, and the route publishing the declaration would answer"
              + " one");
    }
    if (offering != WhereAFieldsValuesComeFrom.THE_VALUES_THE_PROVIDER_DECLARES
        && !choices.isEmpty()) {
      throw new IllegalArgumentException(
          "field "
              + key
              + " declares values to pick from and at the same time says they come from somewhere"
              + " other than this provider, and the Start a payment screen, the Look a payment up"
              + " screen and the route publishing the declaration would not know which list to"
              + " offer");
    }
    if (example != null && !choices.isEmpty() && !values.contains(example)) {
      throw new IllegalArgumentException(
          "field "
              + key
              + " suggests "
              + example
              + ", which is not among the values it accepts, so the Start a payment screen and the"
              + " Look a payment up screen would prefill, and the route publishing the declaration"
              + " would suggest, an answer the provider refuses");
    }
    choices = List.copyOf(choices);
  }

  public static AFieldATesterFillsIn typed(
      String key, String label, boolean required, String whatItIsFor, @Nullable String example) {
    return new AFieldATesterFillsIn(
        key,
        label,
        required,
        whatItIsFor,
        WhereAFieldsValuesComeFrom.WHATEVER_THE_TESTER_TYPES,
        List.of(),
        example);
  }

  public static AFieldATesterFillsIn chosenFrom(
      String key,
      String label,
      boolean required,
      String whatItIsFor,
      List<AValueAFieldOffers> choices) {
    return new AFieldATesterFillsIn(
        key,
        label,
        required,
        whatItIsFor,
        WhereAFieldsValuesComeFrom.THE_VALUES_THE_PROVIDER_DECLARES,
        choices,
        null);
  }

  public static AFieldATesterFillsIn theCallbackTarget(
      String key, String label, String whatItIsFor) {
    return new AFieldATesterFillsIn(
        key,
        label,
        false,
        whatItIsFor,
        WhereAFieldsValuesComeFrom.THE_CALLBACK_TARGETS_THIS_RUN_CONFIGURES,
        List.of(),
        null);
  }

  public static AFieldATesterFillsIn theValuesThisTestRunHasAlreadyProduced(
      String key, String label, boolean required, String whatItIsFor) {
    return new AFieldATesterFillsIn(
        key,
        label,
        required,
        whatItIsFor,
        WhereAFieldsValuesComeFrom.THE_VALUES_THIS_TEST_RUN_HAS_ALREADY_PRODUCED,
        List.of(),
        null);
  }

  public boolean thisSimulatorIsTheOneThatConsumesTheValue() {
    return offering == WhereAFieldsValuesComeFrom.THE_CALLBACK_TARGETS_THIS_RUN_CONFIGURES;
  }

  public boolean namesTheCallbackTarget() {
    return thisSimulatorIsTheOneThatConsumesTheValue();
  }

  public boolean namesAValueThisTestRunHasAlreadyProduced() {
    return offering == WhereAFieldsValuesComeFrom.THE_VALUES_THIS_TEST_RUN_HAS_ALREADY_PRODUCED;
  }

  public String theValuesItAccepts() {
    List<String> offered = new ArrayList<>();
    for (AValueAFieldOffers choice : choices) {
      offered.add(choice.label() + " (" + choice.value() + ")");
    }
    return String.join(", ", offered);
  }
}
