package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class OriginationControl {

  public static final String THIS_SIMULATOR_IS_THE_PARTY_THAT_POSTS_A_CALLBACK =
      " This simulator is the party that posts a callback, so this simulator is the party that"
          + " refuses an address it was never given: this is the one value of this form the"
          + " provider never reads, and every value the provider does read is judged by the"
          + " provider alone.";

  public static final String AN_ACCOUNT_IS_REQUIRED =
      "choose the account this payment is started on. The provider signs the call with that"
          + " account's own credentials and this simulator never asks a tester to type one, so a"
          + " payment with no account named is a payment nothing could sign.";

  private OriginationControl() {}

  public static Map<String, List<AValueAFieldOffers>> theValuesThisTestRunHasAlreadyProduced(
      ProviderOriginationFields declared,
      @Nullable String accountId,
      @Nullable String startedAs,
      TheValuesARunHasAlreadyProduced theRunHolds) {
    if (accountId == null || accountId.isBlank() || startedAs == null) {
      return Map.of();
    }
    DeclaredOrigination way = declared.wayNamed(startedAs.strip()).orElse(null);
    if (way == null) {
      return Map.of();
    }
    LinkedHashMap<String, List<AValueAFieldOffers>> produced = new LinkedHashMap<>();
    for (AFieldATesterFillsIn field : way.fields()) {
      if (field.namesAValueThisTestRunHasAlreadyProduced()) {
        produced.put(field.key(), theRunHolds.ofTheField(accountId.strip(), field.key()));
      }
    }
    return Collections.unmodifiableSequencedMap(produced);
  }

  public static OriginationRequest asked(
      ProviderOriginationFields declared,
      TestRunId testRunId,
      String accountId,
      String startedAs,
      Map<String, String> submitted,
      List<String> callbackTargetsTheRunConfigures,
      ObjectOrigin askedBy) {
    if (accountId.isBlank()) {
      throw new IllegalArgumentException(AN_ACCOUNT_IS_REQUIRED);
    }
    DeclaredOrigination way =
        declared
            .wayNamed(startedAs.strip())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "provider "
                            + declared.provider().value()
                            + " starts a payment one of these ways and no other: "
                            + declared.theWaysItOffers()
                            + ". Go back a step and pick one of them."));
    Map<String, String> submittedForAFieldItDeclares = theValuesItReads(way, submitted);
    Map<String, String> values = new LinkedHashMap<>();
    for (AFieldATesterFillsIn field : way.fields()) {
      String typed = submittedForAFieldItDeclares.get(field.key());
      if (typed == null) {
        refuseAFieldThisWayCannotStartWithout(way, field);
        continue;
      }
      refuseACallbackTargetThisRunDoesNotConfigure(field, typed, callbackTargetsTheRunConfigures);
      values.put(field.key(), typed);
    }
    return OriginationRequest.of(testRunId, accountId, way.name(), values, askedBy);
  }

  private static Map<String, String> theValuesItReads(
      DeclaredOrigination way, Map<String, String> submitted) {
    Map<String, String> values = new LinkedHashMap<>();
    for (Map.Entry<String, String> answer : submitted.entrySet()) {
      if (way.field(answer.getKey()).isEmpty()) {
        throw new IllegalArgumentException(
            "nothing is sent for "
                + answer.getKey()
                + ", because "
                + way.label()
                + " reads "
                + String.join(", ", way.keys())
                + " and nothing else. A value typed into a field that is not there would be"
                + " dropped without a word, which is why it is refused here instead.");
      }
      if (!answer.getValue().isBlank()) {
        values.put(answer.getKey(), answer.getValue().strip());
      }
    }
    return values;
  }

  private static void refuseAFieldThisWayCannotStartWithout(
      DeclaredOrigination way, AFieldATesterFillsIn field) {
    if (!field.required()) {
      return;
    }
    throw new IllegalArgumentException(
        way.label()
            + " cannot start without "
            + field.label()
            + ": "
            + field.whatItIsFor()
            + " Fill it in and start the payment again.");
  }

  private static void refuseACallbackTargetThisRunDoesNotConfigure(
      AFieldATesterFillsIn field, String typed, List<String> configured) {
    if (!field.thisSimulatorIsTheOneThatConsumesTheValue() || configured.contains(typed)) {
      return;
    }
    throw new IllegalArgumentException(
        field.label()
            + " must be one of the callback targets this test run configures, and this test run"
            + (configured.isEmpty()
                ? " configures none. Configure one on the Configuration screen, under the account"
                    + " this payment is started on, and start the payment again."
                : " configures "
                    + String.join(", ", configured)
                    + ". Pick one of them, or add this one on the Configuration screen under the"
                    + " account this payment is started on.")
            + THIS_SIMULATOR_IS_THE_PARTY_THAT_POSTS_A_CALLBACK);
  }
}
