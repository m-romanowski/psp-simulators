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

public final class TheTrailRowACompletedHostedPageLeaves {

  public static final String THE_METHOD_A_HOSTED_PAGE_IS_COMPLETED_ON = "POST";

  public static final String PROVIDER_OBJECT_ID = HostedCompletionOutcome.PROVIDER_OBJECT_ID;

  public static final String OUTCOME = "outcome";

  public static final String CARD = "card";

  public static final String CREATED_OBJECT_ID = "createdObjectId";

  public static final String WEBHOOK_JOB_IDS = "webhookJobIds";

  public static final String REFUSED_BECAUSE = ActionFacts.REFUSED_BECAUSE;

  public static final String THIS_RUN_HOLDS_NO_SUCH_HOSTED_PAGE =
      "this test run holds no object of this provider under the id this completion named, so"
          + " there was no hosted page for a payer to have finished";

  private TheTrailRowACompletedHostedPageLeaves() {}

  public static AuditedAction of(
      ProviderId provider,
      String path,
      HostedCompletionRequest request,
      HostedCompletionOutcome outcome,
      int theStatusTheSurfaceAnsweredACompletedPageWith) {
    ActionVerdict.Refused turnedAway = outcome.theRefusalItReports();
    return new AuditedAction(
        provider,
        request.testRunId(),
        request.askedBy(),
        THE_METHOD_A_HOSTED_PAGE_IS_COMPLETED_ON,
        path,
        turnedAway == null
            ? ActionVerdict.applied(theStatusTheSurfaceAnsweredACompletedPageWith)
            : turnedAway,
        theObjectItCompleted(outcome),
        theFactsOf(request, outcome),
        null,
        null);
  }

  public static AuditedAction ofACompletionTurnedAwayBeforeTheProviderWasReached(
      ProviderId provider,
      TestRunId testRunId,
      ObjectOrigin askedBy,
      String path,
      String providerObjectId,
      @Nullable String outcome,
      @Nullable String card,
      String inItsOwnWords) {
    return AuditedAction.refusing(
        provider,
        testRunId,
        askedBy,
        THE_METHOD_A_HOSTED_PAGE_IS_COMPLETED_ON,
        path,
        ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(
                HostedCompletionOutcome.THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM)),
        ActionFacts.none()
            .and(PROVIDER_OBJECT_ID, providerObjectId)
            .and(OUTCOME, outcome)
            .and(CARD, card)
            .and(REFUSED_BECAUSE, inItsOwnWords)
            .asRecorded());
  }

  private static @Nullable String theObjectItCompleted(HostedCompletionOutcome outcome) {
    return outcome instanceof HostedCompletionOutcome.Completed completed
        ? completed.providerObjectId()
        : null;
  }

  private static Map<String, List<String>> theFactsOf(
      HostedCompletionRequest request, HostedCompletionOutcome outcome) {
    ActionFacts facts =
        ActionFacts.none()
            .and(PROVIDER_OBJECT_ID, request.providerObjectId())
            .and(OUTCOME, request.outcome().name())
            .and(CARD, request.card());
    ActionFacts andWhatCameBack =
        switch (outcome) {
          case HostedCompletionOutcome.Completed completed ->
              facts
                  .and(CREATED_OBJECT_ID, completed.createdObjectId())
                  .andEach(WEBHOOK_JOB_IDS, completed.webhookJobIds());
          case HostedCompletionOutcome.RefusedByTheProvider refused ->
              facts.and(REFUSED_BECAUSE, refused.inItsOwnWords());
          case HostedCompletionOutcome.NotOffered notOffered ->
              facts.and(REFUSED_BECAUSE, notOffered.whatThisProviderHasInstead());
          case HostedCompletionOutcome.NoSuchObject _ ->
              facts.and(REFUSED_BECAUSE, THIS_RUN_HOLDS_NO_SUCH_HOSTED_PAGE);
        };
    return andWhatCameBack.asRecorded();
  }
}
