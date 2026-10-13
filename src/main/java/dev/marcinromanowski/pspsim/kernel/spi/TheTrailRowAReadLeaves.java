package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class TheTrailRowAReadLeaves {

  public static final String THE_METHOD_A_READ_IS_ASKED_ON = "GET";

  public static final String THE_ANSWER_IT_ASKED_THIS_PROVIDER_FOR =
      "the answer it asked this provider for";

  public static final String READ_NAMED = "readNamed";

  public static final String ACCOUNT_ID = "accountId";

  public static final String ROWS_ANSWERED = "rowsAnswered";

  public static final String REFUSED_BECAUSE = ActionFacts.REFUSED_BECAUSE;

  public static final String THIS_RUN_CONFIGURES_NO_SUCH_ACCOUNT =
      "this test run configures no account under the identifier this question was asked on, so"
          + " nothing could sign the call this provider answers only a signed call of";

  public static final int THE_STATUS_AN_ANSWERED_READ_IS_SENT_BACK_WITH = 200;

  public static final String A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD =
      "a rule of this test run answered this read instead of the provider, so what came back is"
          + " the rule's answer and not this provider's. The rule that decided it is ";

  private TheTrailRowAReadLeaves() {}

  public static AuditedAction of(
      ProviderId provider, String path, ReadRequest request, ReadOutcome outcome) {
    return new AuditedAction(
            provider,
            request.testRunId(),
            request.askedBy(),
            THE_METHOD_A_READ_IS_ASKED_ON,
            path,
            theVerdictOf(outcome),
            theObjectOfTheOneRowItAnsweredAndNoneWhenSeveralDid(outcome),
            theFactsOf(request, outcome),
            null,
            request.accountId())
        .decidedBy(outcome.theRuleThatDecidedIt());
  }

  public static AuditedAction ofAQuestionTurnedAwayBeforeTheProviderWasReached(
      ProviderId provider,
      TestRunId testRunId,
      ObjectOrigin askedBy,
      String path,
      @Nullable String readNamed,
      @Nullable String accountId,
      String inItsOwnWords) {
    return AuditedAction.refusing(
            provider,
            testRunId,
            askedBy,
            THE_METHOD_A_READ_IS_ASKED_ON,
            path,
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(THE_ANSWER_IT_ASKED_THIS_PROVIDER_FOR)),
            ActionFacts.none()
                .and(READ_NAMED, readNamed)
                .and(ACCOUNT_ID, accountId)
                .and(REFUSED_BECAUSE, inItsOwnWords)
                .asRecorded())
        .theAccountItActedFor(accountId);
  }

  private static ActionVerdict theVerdictOf(ReadOutcome outcome) {
    return switch (outcome) {
      case ReadOutcome.Answered _ ->
          ActionVerdict.applied(THE_STATUS_AN_ANSWERED_READ_IS_SENT_BACK_WITH);
      case ReadOutcome.RefusedByTheProvider _ -> ActionVerdict.refused(theSubjectOfARefusal());
      case ReadOutcome.AnsweredByAScenarioInstead _ ->
          ActionVerdict.refused(theSubjectOfARefusal());
      case ReadOutcome.NotOffered _ -> ActionVerdict.rejected(theSubjectOfARefusal());
      case ReadOutcome.NoSuchAccount _ -> ActionVerdict.notFound(theSubjectOfARefusal());
    };
  }

  private static RefusalSubject theSubjectOfARefusal() {
    return RefusalSubject.noObjectWasNamed(THE_ANSWER_IT_ASKED_THIS_PROVIDER_FOR);
  }

  private static @Nullable String theObjectOfTheOneRowItAnsweredAndNoneWhenSeveralDid(
      ReadOutcome outcome) {
    if (!(outcome instanceof ReadOutcome.Answered answered) || answered.rows().size() != 1) {
      return null;
    }
    return answered.rows().getFirst().providerObjectId();
  }

  private static Map<String, List<String>> theFactsOf(ReadRequest request, ReadOutcome outcome) {
    ActionFacts facts =
        ActionFacts.none()
            .and(READ_NAMED, request.readNamed())
            .and(ACCOUNT_ID, request.accountId());
    for (Map.Entry<String, String> value : request.values().entrySet()) {
      facts = facts.and(value.getKey(), value.getValue());
    }
    ActionFacts andWhatCameBack =
        switch (outcome) {
          case ReadOutcome.Answered answered ->
              facts.andCount(ROWS_ANSWERED, answered.rows().size());
          case ReadOutcome.RefusedByTheProvider refused ->
              facts.and(REFUSED_BECAUSE, refused.inItsOwnWords());
          case ReadOutcome.AnsweredByAScenarioInstead answered ->
              facts.and(
                  REFUSED_BECAUSE, A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD + answered.scenarioId());
          case ReadOutcome.NotOffered notOffered ->
              facts.and(REFUSED_BECAUSE, notOffered.whatThisProviderHasInstead());
          case ReadOutcome.NoSuchAccount _ ->
              facts.and(REFUSED_BECAUSE, THIS_RUN_CONFIGURES_NO_SUCH_ACCOUNT);
        };
    return andWhatCameBack.asRecorded();
  }
}
