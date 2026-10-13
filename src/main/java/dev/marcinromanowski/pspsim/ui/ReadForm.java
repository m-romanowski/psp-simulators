package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.AnsweredValue;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.Fact;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead.EmptyAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ReadForm {

  public static final String THE_RUN_CONFIGURES_NO_ACCOUNT =
      "This test run configures no account for this provider, and a read is asked on one: the"
          + " provider answers only for the business whose credentials signed the call."
          + " Configure an account on the Configuration screen, under Configure an account, and"
          + " come back to this form.";

  public static final String A_READ_MOVES_NOTHING =
      "Nothing on this screen moves money or changes an object. Every question here is one the"
          + " provider answers and nothing else, and a payment you see named in an answer is the"
          + " one the provider already held.";

  public static final String NO_RULE_OF_THIS_RUN_IS_MATCHED_ON_THIS_QUESTION =
      "No scenario rule of this test run can be matched on this question, so asking it twice"
          + " leaves this test run exactly as it was.";

  public static final String A_RULE_OF_THIS_RUN_MAY_DECIDE_THIS_QUESTION =
      "A scenario rule of this test run may decide this question instead of the provider, and a"
          + " rule carrying a story moves that story on when it decides one, so asking this"
          + " question twice can meet two steps of that story and be answered twice differently."
          + " The trail of this test run records every question asked here and the rule that"
          + " answered it.";

  public static final String THE_ACCOUNT_IS_GONE =
      "the account this read named is not one this test run configures any more; the run may have"
          + " been reset while this form was open";

  public static final String A_SCENARIO_ANSWERED_IT_INSTEAD =
      "a scenario answered this read instead of the provider, so what came back is the rule's"
          + " answer and not this provider's; the rule that decided it is ";

  public static final String WHAT_IT_MEANS_WHEN_THIS_ONE_ANSWERS_NOTHING =
      "What it means when this question is answered with nothing at all:";

  public static final String THIS_ONE_NEVER_ANSWERS_NOTHING =
      "This question is never answered with nothing at all, and this is what it answers instead:";

  public static final String ANSWERED_THIS_WITH_NO_VALUE_AT_ALL =
      "answered this with no value at all, which is its answer and not a value this screen"
          + " dropped";

  public static final String ANSWERED_THIS_LIST_EMPTY =
      "answered this list empty, which is its answer and not a member this screen dropped";

  public static final String ANSWERED_THIS_WITH_NO_MEMBER_OF_ITS_OWN =
      "answered this with no member of its own, which is its answer and not a member this screen"
          + " dropped";

  public static final String THE_PLACE_A_LIST_ANSWERED_IT_IN = "number ";

  public static final String A_FACT_IS_ONE_VALUE_OR_WHAT_CAME_IN_PLACE_OF_ONE =
      "a fact a screen prints is one value the provider answered, or the words saying what it"
          + " answered in place of a value, or the facts it answered under that name, and never"
          + " two of the three at once: a row drawing two of them beside one name would print a"
          + " value the provider never answered under it";

  private static final boolean THE_PROVIDER_NAMED_IT = true;

  private static final boolean THE_PLACE_IN_THE_LIST_NAMED_IT = false;

  public enum Panel {
    THIS_PROVIDER_PUBLISHES_NO_READ,
    NO_ACCOUNT_IS_CONFIGURED,
    WHICH_QUESTION,
    WHAT_THIS_READ_ASKS_FOR
  }

  public record Question(
      String name,
      String label,
      String theQuestionItAnswers,
      String whatAnEmptyAnswerWouldMean,
      String theLeadInToWhatAnEmptyAnswerWouldMean,
      boolean chosen) {}

  public record PrintedFact(
      String asTheProviderNamesIt,
      boolean theProviderNamedIt,
      @Nullable String value,
      @Nullable String whatItAnsweredInsteadOfAValue,
      List<PrintedFact> under) {

    public PrintedFact {
      under = List.copyOf(under);
      int drawn =
          (value == null ? 0 : 1)
              + (whatItAnsweredInsteadOfAValue == null ? 0 : 1)
              + (under.isEmpty() ? 0 : 1);
      if (drawn != 1) {
        throw new IllegalArgumentException(A_FACT_IS_ONE_VALUE_OR_WHAT_CAME_IN_PLACE_OF_ONE);
      }
    }
  }

  public record PrintedRow(@Nullable String providerObjectId, List<PrintedFact> facts) {

    public PrintedRow {
      facts = List.copyOf(facts);
    }
  }

  public record Answer(
      List<PrintedRow> rows,
      List<PrintedFact> whatItAnsweredAboutTheQuery,
      @Nullable String refusal,
      @Nullable String whatItMeansThatNothingWasFound,
      @Nullable String decidedBy) {

    public Answer {
      rows = List.copyOf(rows);
      whatItAnsweredAboutTheQuery = List.copyOf(whatItAnsweredAboutTheQuery);
    }

    public boolean itFoundNothing() {
      return rows.isEmpty() && refusal == null;
    }
  }

  public record View(
      String provider,
      String testRunId,
      String accountId,
      String readNamed,
      List<String> accountsThisRunConfigures,
      List<Question> questions,
      @Nullable Question chosenQuestion,
      List<TheFieldsAFormDraws.Field> fields,
      List<TheFieldsAFormDraws.FilledIn> filledIn,
      @Nullable Answer answer,
      @Nullable String whatThisProviderPublishesInstead,
      boolean aRuleOfThisRunCouldDecideIt) {

    public View {
      accountsThisRunConfigures = List.copyOf(accountsThisRunConfigures);
      filledIn = List.copyOf(filledIn);
      questions = List.copyOf(questions);
      fields = List.copyOf(fields);
    }

    public Panel panel() {
      if (whatThisProviderPublishesInstead != null) {
        return Panel.THIS_PROVIDER_PUBLISHES_NO_READ;
      }
      if (accountsThisRunConfigures.isEmpty()) {
        return Panel.NO_ACCOUNT_IS_CONFIGURED;
      }
      if (chosenQuestion == null) {
        return Panel.WHICH_QUESTION;
      }
      return Panel.WHAT_THIS_READ_ASKS_FOR;
    }

    public String whatToDoWithNoAccount() {
      return THE_RUN_CONFIGURES_NO_ACCOUNT;
    }

    public String whatAReadMoves() {
      return A_READ_MOVES_NOTHING;
    }

    public String whatAskingThisQuestionAgainMoves() {
      return aRuleOfThisRunCouldDecideIt
          ? A_RULE_OF_THIS_RUN_MAY_DECIDE_THIS_QUESTION
          : NO_RULE_OF_THIS_RUN_IS_MATCHED_ON_THIS_QUESTION;
    }
  }

  private ReadForm() {}

  public static View of(
      String provider,
      String testRunId,
      @Nullable String accountId,
      @Nullable String readNamed,
      ProviderReads declared,
      List<String> accountsThisRunConfigures,
      Map<String, List<AValueAFieldOffers>> theValuesThisRunHasAlreadyProduced,
      Map<String, String> submitted,
      @Nullable Answer answer,
      boolean aRuleOfThisRunCouldDecideIt) {
    String named = readNamed == null ? "" : readNamed.strip();
    DeclaredRead chosen = declared.readNamed(named).orElse(null);
    return new View(
        provider,
        testRunId,
        accountId == null ? "" : accountId.strip(),
        named,
        accountsThisRunConfigures,
        questionsOf(declared, chosen),
        chosen == null ? null : questionOf(chosen, true),
        chosen == null
            ? List.of()
            : TheFieldsAFormDraws.drawnFrom(
                chosen.fields(), List.of(), theValuesThisRunHasAlreadyProduced, submitted),
        theFieldsThisSimulatorFillsInFor(chosen),
        answer,
        declared.publishesAny() ? null : declared.whatItPublishesInstead(),
        aRuleOfThisRunCouldDecideIt);
  }

  private static List<TheFieldsAFormDraws.FilledIn> theFieldsThisSimulatorFillsInFor(
      @Nullable DeclaredRead chosen) {
    if (chosen == null) {
      return List.of();
    }
    List<TheFieldsAFormDraws.FilledIn> filled = new ArrayList<>();
    chosen
        .fieldsItFillsInItself()
        .forEach(
            (key, whereItComesFrom) ->
                filled.add(new TheFieldsAFormDraws.FilledIn(key, whereItComesFrom)));
    return List.copyOf(filled);
  }

  public static Answer answerOf(DeclaredRead asked, ReadOutcome outcome) {
    return switch (outcome) {
      case ReadOutcome.Answered answered ->
          new Answer(
              thePrintedRowsOf(answered.rows()),
              thePrintedFactsOf(answered.whatItAnsweredAboutTheQuery()),
              null,
              answered.itAnsweredNoRowAtAll()
                  ? asked.whatItMeansThatThisOneAnsweredNothing()
                  : null,
              answered.scenarioId());
      case ReadOutcome.RefusedByTheProvider refused ->
          theRefusal(refused.inItsOwnWords(), refused.scenarioId());
      case ReadOutcome.AnsweredByAScenarioInstead instead ->
          theRefusal(A_SCENARIO_ANSWERED_IT_INSTEAD + instead.scenarioId(), instead.scenarioId());
      case ReadOutcome.NotOffered notOffered ->
          theRefusal(notOffered.whatThisProviderHasInstead(), null);
      case ReadOutcome.NoSuchAccount _ -> theRefusal(THE_ACCOUNT_IS_GONE, null);
    };
  }

  public static Answer theRefusal(String inItsOwnWords, @Nullable String decidedBy) {
    return new Answer(List.of(), List.of(), inItsOwnWords, null, decidedBy);
  }

  private static List<PrintedRow> thePrintedRowsOf(List<AnsweredRow> rows) {
    List<PrintedRow> printed = new ArrayList<>();
    for (AnsweredRow row : rows) {
      printed.add(new PrintedRow(row.providerObjectId(), thePrintedFactsOf(row.facts())));
    }
    return List.copyOf(printed);
  }

  private static List<PrintedFact> thePrintedFactsOf(List<Fact> facts) {
    List<PrintedFact> printed = new ArrayList<>();
    for (Fact fact : facts) {
      printed.add(
          thePrintedFactOf(fact.asTheProviderNamesIt(), THE_PROVIDER_NAMED_IT, fact.value()));
    }
    return List.copyOf(printed);
  }

  private static PrintedFact thePrintedFactOf(
      String name, boolean theProviderNamedIt, AnsweredValue answered) {
    return switch (answered) {
      case AnsweredValue.TheValueItAnswered one ->
          new PrintedFact(name, theProviderNamedIt, one.asTheProviderAnsweredIt(), null, List.of());
      case AnsweredValue.NoValueAtAll _ ->
          new PrintedFact(
              name, theProviderNamedIt, null, ANSWERED_THIS_WITH_NO_VALUE_AT_ALL, List.of());
      case AnsweredValue.TheMembersUnderIt members ->
          members.members().isEmpty()
              ? new PrintedFact(
                  name,
                  theProviderNamedIt,
                  null,
                  ANSWERED_THIS_WITH_NO_MEMBER_OF_ITS_OWN,
                  List.of())
              : new PrintedFact(
                  name, theProviderNamedIt, null, null, thePrintedFactsOf(members.members()));
      case AnsweredValue.TheElementsUnderIt elements ->
          elements.elements().isEmpty()
              ? new PrintedFact(name, theProviderNamedIt, null, ANSWERED_THIS_LIST_EMPTY, List.of())
              : new PrintedFact(
                  name, theProviderNamedIt, null, null, thePrintedElementsOf(elements.elements()));
    };
  }

  private static List<PrintedFact> thePrintedElementsOf(List<AnsweredValue> elements) {
    List<PrintedFact> printed = new ArrayList<>();
    int place = 0;
    for (AnsweredValue element : elements) {
      place++;
      printed.add(
          thePrintedFactOf(
              THE_PLACE_A_LIST_ANSWERED_IT_IN + place, THE_PLACE_IN_THE_LIST_NAMED_IT, element));
    }
    return List.copyOf(printed);
  }

  private static List<Question> questionsOf(ProviderReads declared, @Nullable DeclaredRead chosen) {
    List<Question> questions = new ArrayList<>();
    for (DeclaredRead read : declared.reads()) {
      questions.add(questionOf(read, chosen != null && chosen.name().equals(read.name())));
    }
    return List.copyOf(questions);
  }

  private static Question questionOf(DeclaredRead read, boolean chosen) {
    return new Question(
        read.name(),
        read.label(),
        read.theQuestionItAnswers(),
        read.whatAnEmptyAnswerWouldMean().inTheProvidersOwnWords(),
        theLeadInTo(read.whatAnEmptyAnswerWouldMean()),
        chosen);
  }

  private static String theLeadInTo(EmptyAnswer whatAnEmptyAnswerWouldMean) {
    return switch (whatAnEmptyAnswerWouldMean) {
      case EmptyAnswer.ItMeansThis _ -> WHAT_IT_MEANS_WHEN_THIS_ONE_ANSWERS_NOTHING;
      case EmptyAnswer.ItNeverAnswersNothing _ -> THIS_ONE_NEVER_ANSWERS_NOTHING;
    };
  }
}
