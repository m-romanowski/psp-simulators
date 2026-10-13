package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

final class GrowReads {

  static final String A_CHARGE_ON_A_SAVED_CARD =
      GrowOperations.GET_TOKEN_TRANSACTIONS_BY_EXTERNAL_IDENTIFIERS;

  static final String THE_ANSWER_OF_THIS_READ_IS_A_LIST_OF_TRANSACTIONS =
      "getTokenTransactionsByExternalIdentifiers answers data.transactions and this envelope"
          + " carries no such list, so the rows a screen would print are rows nothing answered."
          + " Grow publishes one shape for this operation and this simulator builds it in one"
          + " place: an envelope of any other shape reaching here is a change to that place and"
          + " not an answer to read rows out of";

  static final String THE_MERCHANT_IS_THE_ACCOUNTS_OWN =
      "Grow names the merchant by the userId configured for the account you pick, which this"
          + " simulator holds as a secret, sends with the call and never reads back onto a"
          + " screen, so you are never asked to type it and never retrace the charges of another"
          + " business by accident.";

  static final String EVERY_MEMBER_OF_A_RETRACED_CHARGE_IS_A_SINGLE_VALUE =
      "grow answers every member of a retraced charge as one value a merchant's own application"
          + " reads straight out of the row, and this answer carries a member holding an object"
          + " or a list instead. A screen that printed the members of that member would be"
          + " printing a shape grow does not publish for this operation";

  private static final String DATA = "data";

  private static final AFieldATesterFillsIn CARD_TOKEN =
      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
          GrowFields.CARD_TOKEN,
          "the saved card the charge was taken on",
          true,
          "Grow resolves the cardToken against the process that saved it, on the same business,"
              + " and answers err.id 140 for a token that business never saved. The box suggests"
              + " the cards the finished processes of this test run saved on the account above"
              + " and takes any token you type instead, because grow is what reads it and grow"
              + " is what refuses it. It is the card you charged, not the charge: the charge is"
              + " named by your own identifier below.");

  private static final AFieldATesterFillsIn TRANSACTION_UNIQUE_IDENTIFIER =
      AFieldATesterFillsIn.typed(
          GrowFields.TRANSACTION_UNIQUE_IDENTIFIER,
          "your own identifier for the charge you are asking about",
          true,
          "The identifier you sent with the charge, which is how grow finds it again when your"
              + " own application never read the answer. Grow holds one charge per identifier per"
              + " business, so this names at most one charge. It is a positive whole number, no"
              + " wider than 32 bits and with no leading zeros: grow answers err.id 1013 for"
              + " anything else, and err.id 104 when none is sent at all, which is the code it"
              + " publishes for a user it does not know.",
          "1001");

  private static final AFieldATesterFillsIn TRANSACTION_GROUP_IDENTIFIER =
      AFieldATesterFillsIn.typed(
          GrowFields.TRANSACTION_GROUP_IDENTIFIER,
          "your own identifier for the group that charge belonged to",
          false,
          "Grow reads the group identifier as a second identifier of yours on the same charge:"
              + " a charge whose group is not the one you name here is not answered, even when"
              + " the identifier above names it. Leaving it empty asks about the charge whatever"
              + " group it carried.",
          null);

  static final ProviderReads DECLARED =
      ProviderReads.theOnesItPublishes(
          GrowLightApi.GROW,
          List.of(
              new DeclaredRead(
                  A_CHARGE_ON_A_SAVED_CARD,
                  "Find out whether a charge on a saved card went through",
                  "A merchant that charged a saved card and never read the answer asks grow this"
                      + " one, by the identifiers it sent itself rather than by any id grow gave"
                      + " it: grow has no transactionId to give a call that timed out."
                      + " getTokenTransactionsByExternalIdentifiers answers the charge grow holds"
                      + " under those identifiers and changes nothing about it. Grow names this"
                      + " operation among the ones a scenario rule may be declared on, so a rule"
                      + " of your test run may answer it instead, and a rule carrying a story"
                      + " moves that story on when it does.",
                  DeclaredRead.EmptyAnswer.whatItMeans(
                      "Grow answers an empty transactions list, which is its answer and not a"
                          + " failed read: no charge of this business carries the identifiers you"
                          + " named. Either nothing ever reached grow under them, or the charge"
                          + " it took under them moved no money and left no transaction behind,"
                          + " which is what a declined stored-card charge leaves. The charge is"
                          + " yours to retry under a new identifier; grow rejects a repeat of one"
                          + " it has already seen whatever became of the first charge."),
                  List.of(CARD_TOKEN, TRANSACTION_UNIQUE_IDENTIFIER, TRANSACTION_GROUP_IDENTIFIER),
                  theFieldsTheSimulatorFillsIn())));

  private static SequencedMap<String, String> theFieldsTheSimulatorFillsIn() {
    LinkedHashMap<String, String> filled = new LinkedHashMap<>();
    filled.put(GrowFields.USER_ID, THE_MERCHANT_IS_THE_ACCOUNTS_OWN);
    return filled;
  }

  private GrowReads() {}

  static GrowForm theRetraceFormOf(ReadRequest request, GrowAccount account) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put(GrowFields.USER_ID, List.of(account.userId()));
    for (Map.Entry<String, String> value : request.values().entrySet()) {
      fields.put(value.getKey(), List.of(value.getValue()));
    }
    return GrowForm.of(fields);
  }

  static ReadOutcome outcomeOf(GrowOperationResult result) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new ReadOutcome.AnsweredByAScenarioInstead(String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new ReadOutcome.RefusedByTheProvider(
          GrowOrigination.theErrorInItsOwnWords(result), result.scenarioId());
    }
    return new ReadOutcome.Answered(theRowsOf(result.body()), result.scenarioId());
  }

  static List<AnsweredRow> theRowsOf(GrowJson body) {
    if (!(body instanceof GrowJson.JsonObject envelope
        && envelope.members().get(DATA) instanceof GrowJson.JsonObject data
        && data.members().get(GrowResponseFields.TRANSACTIONS)
            instanceof GrowJson.JsonArray answered)) {
      throw new IllegalStateException(THE_ANSWER_OF_THIS_READ_IS_A_LIST_OF_TRANSACTIONS);
    }
    List<AnsweredRow> rows = new ArrayList<>();
    for (GrowJson row : answered.elements()) {
      rows.add(theRowOf(row));
    }
    return List.copyOf(rows);
  }

  private static AnsweredRow theRowOf(GrowJson row) {
    if (!(row instanceof GrowJson.JsonObject members)) {
      throw new IllegalStateException(THE_ANSWER_OF_THIS_READ_IS_A_LIST_OF_TRANSACTIONS);
    }
    List<AnsweredRow.Fact> facts = new ArrayList<>();
    for (Map.Entry<String, GrowJson> member : members.members().entrySet()) {
      facts.add(new AnsweredRow.Fact(member.getKey(), theOneValueOf(member.getValue())));
    }
    return new AnsweredRow(theChargeItNames(members), facts);
  }

  private static @Nullable String theChargeItNames(GrowJson.JsonObject members) {
    GrowJson named = members.members().get(GrowResponseFields.TRANSACTION_ID);
    return named == null ? null : theOneValueOf(named);
  }

  private static String theOneValueOf(GrowJson member) {
    return switch (member) {
      case GrowJson.JsonText text -> text.value();
      case GrowJson.JsonNumber number -> Long.toString(number.value());
      case GrowJson.JsonObject _, GrowJson.JsonArray _ ->
          throw new IllegalStateException(EVERY_MEMBER_OF_A_RETRACED_CHARGE_IS_A_SINGLE_VALUE);
    };
  }
}
