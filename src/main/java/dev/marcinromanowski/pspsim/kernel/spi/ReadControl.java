package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ReadControl {

  public static final String AN_ACCOUNT_IS_REQUIRED =
      "choose the account this question is asked on. The provider answers a read only for the"
          + " business whose credentials signed it and this simulator never asks a tester to type"
          + " one, so a read with no account named is a read nothing could sign.";

  private ReadControl() {}

  public static Map<String, List<AValueAFieldOffers>> theValuesThisTestRunHasAlreadyProduced(
      ProviderReads declared,
      @Nullable String accountId,
      @Nullable String readNamed,
      TheValuesARunHasAlreadyProduced theRunHolds) {
    if (accountId == null || accountId.isBlank() || readNamed == null) {
      return Map.of();
    }
    DeclaredRead read = declared.readNamed(readNamed.strip()).orElse(null);
    if (read == null) {
      return Map.of();
    }
    LinkedHashMap<String, List<AValueAFieldOffers>> produced = new LinkedHashMap<>();
    for (AFieldATesterFillsIn field : read.fields()) {
      if (field.namesAValueThisTestRunHasAlreadyProduced()) {
        produced.put(field.key(), theRunHolds.ofTheField(accountId.strip(), field.key()));
      }
    }
    return Collections.unmodifiableSequencedMap(produced);
  }

  public static ReadRequest asked(
      ProviderReads declared,
      TestRunId testRunId,
      String accountId,
      String readNamed,
      Map<String, String> submitted,
      ObjectOrigin askedBy) {
    if (accountId.isBlank()) {
      throw new IllegalArgumentException(AN_ACCOUNT_IS_REQUIRED);
    }
    DeclaredRead read =
        declared
            .readNamed(readNamed.strip())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        declared.theRefusalOfAQuestionThisProviderDoesNotAnswer(
                            readNamed.strip())));
    Map<String, String> submittedForAFieldItDeclares = theValuesItReads(read, submitted);
    Map<String, String> values = new LinkedHashMap<>();
    for (AFieldATesterFillsIn field : read.fields()) {
      String typed = submittedForAFieldItDeclares.get(field.key());
      if (typed == null) {
        refuseAFieldThisReadCannotAnswerWithout(read, field);
        continue;
      }
      values.put(field.key(), typed);
    }
    return ReadRequest.of(testRunId, accountId, read.name(), values, askedBy);
  }

  private static Map<String, String> theValuesItReads(
      DeclaredRead read, Map<String, String> submitted) {
    Map<String, String> values = new LinkedHashMap<>();
    for (Map.Entry<String, String> answer : submitted.entrySet()) {
      if (read.field(answer.getKey()).isEmpty()) {
        throw new IllegalArgumentException(
            "nothing is sent for "
                + answer.getKey()
                + ", because "
                + read.label()
                + " reads "
                + String.join(", ", read.keys())
                + " and nothing else. A value typed into a field that is not there would be"
                + " dropped without a word, which is why it is refused here instead.");
      }
      if (!answer.getValue().isBlank()) {
        values.put(answer.getKey(), answer.getValue().strip());
      }
    }
    return values;
  }

  private static void refuseAFieldThisReadCannotAnswerWithout(
      DeclaredRead read, AFieldATesterFillsIn field) {
    if (!field.required()) {
      return;
    }
    throw new IllegalArgumentException(
        read.label()
            + " cannot be answered without "
            + field.label()
            + ": "
            + field.whatItIsFor()
            + " Fill it in and ask again.");
  }
}
