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

public final class TheTrailRowAStartedPaymentLeaves {

  public static final String THE_METHOD_A_PAYMENT_IS_STARTED_ON = "POST";

  public static final String THE_PAYMENT_IT_ASKED_THIS_PROVIDER_TO_START =
      "the payment it asked this provider to start";

  public static final String STARTED_AS = "startedAs";

  public static final String ACCOUNT_ID = "accountId";

  public static final String PROVIDER_OBJECT_ID = "providerObjectId";

  public static final String REFUSED_BECAUSE = ActionFacts.REFUSED_BECAUSE;

  public static final String THIS_RUN_CONFIGURES_NO_SUCH_ACCOUNT =
      "this test run configures no account under the identifier this payment was asked to be"
          + " started on, so nothing could sign the call this provider answers only a signed"
          + " call of";

  public static final String A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD =
      "a rule of this test run answered this call instead of the provider, so no payment was"
          + " started and what came back is the rule's answer and not this provider's. The rule"
          + " that decided it is ";

  private TheTrailRowAStartedPaymentLeaves() {}

  public static AuditedAction of(
      ProviderId provider,
      String path,
      OriginationRequest request,
      OriginationOutcome outcome,
      int theStatusTheSurfaceAnsweredAStartedPaymentWith) {
    return new AuditedAction(
            provider,
            request.testRunId(),
            request.askedBy(),
            THE_METHOD_A_PAYMENT_IS_STARTED_ON,
            path,
            theVerdictOf(outcome, theStatusTheSurfaceAnsweredAStartedPaymentWith),
            theObjectItStarted(outcome),
            theFactsOf(request, outcome),
            null,
            request.accountId())
        .decidedBy(outcome.theRuleThatDecidedIt());
  }

  public static AuditedAction ofARequestTurnedAwayBeforeTheProviderWasReached(
      ProviderId provider,
      TestRunId testRunId,
      ObjectOrigin askedBy,
      String path,
      @Nullable String startedAs,
      @Nullable String accountId,
      String inItsOwnWords) {
    return AuditedAction.refusing(
            provider,
            testRunId,
            askedBy,
            THE_METHOD_A_PAYMENT_IS_STARTED_ON,
            path,
            ActionVerdict.rejected(theSubjectOfARefusal()),
            ActionFacts.none()
                .and(STARTED_AS, startedAs)
                .and(ACCOUNT_ID, accountId)
                .and(REFUSED_BECAUSE, inItsOwnWords)
                .asRecorded())
        .theAccountItActedFor(accountId);
  }

  public static ActionVerdict theVerdictOf(
      OriginationOutcome outcome, int theStatusTheSurfaceAnsweredAStartedPaymentWith) {
    return switch (outcome) {
      case OriginationOutcome.Originated _ ->
          ActionVerdict.applied(theStatusTheSurfaceAnsweredAStartedPaymentWith);
      case OriginationOutcome.RefusedByTheProvider _ ->
          ActionVerdict.refused(theSubjectOfARefusal());
      case OriginationOutcome.AnsweredByAScenarioInstead _ ->
          ActionVerdict.refused(theSubjectOfARefusal());
      case OriginationOutcome.NotOffered _ -> ActionVerdict.rejected(theSubjectOfARefusal());
      case OriginationOutcome.NoSuchAccount _ -> ActionVerdict.notFound(theSubjectOfARefusal());
    };
  }

  private static RefusalSubject theSubjectOfARefusal() {
    return RefusalSubject.noObjectWasNamed(THE_PAYMENT_IT_ASKED_THIS_PROVIDER_TO_START);
  }

  private static @Nullable String theObjectItStarted(OriginationOutcome outcome) {
    return outcome instanceof OriginationOutcome.Originated started
        ? started.providerObjectId()
        : null;
  }

  private static Map<String, List<String>> theFactsOf(
      OriginationRequest request, OriginationOutcome outcome) {
    ActionFacts facts =
        ActionFacts.none()
            .and(STARTED_AS, request.startedAs())
            .and(ACCOUNT_ID, request.accountId());
    for (Map.Entry<String, String> value : request.values().entrySet()) {
      facts = facts.and(value.getKey(), value.getValue());
    }
    ActionFacts andWhatCameBack =
        switch (outcome) {
          case OriginationOutcome.Originated started ->
              facts.and(PROVIDER_OBJECT_ID, started.providerObjectId());
          case OriginationOutcome.RefusedByTheProvider refused ->
              facts.and(REFUSED_BECAUSE, refused.inItsOwnWords());
          case OriginationOutcome.AnsweredByAScenarioInstead answered ->
              facts.and(
                  REFUSED_BECAUSE, A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD + answered.scenarioId());
          case OriginationOutcome.NotOffered notOffered ->
              facts.and(REFUSED_BECAUSE, notOffered.whatThisProviderHasInstead());
          case OriginationOutcome.NoSuchAccount _ ->
              facts.and(REFUSED_BECAUSE, THIS_RUN_CONFIGURES_NO_SUCH_ACCOUNT);
        };
    return andWhatCameBack.asRecorded();
  }
}
