package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface HostedCompletionOutcome {

  String PROVIDER_OBJECT_ID = "providerObjectId";

  String THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM =
      "the completion of a hosted page this request asked this provider to perform";

  ActionVerdict.@Nullable Refused theRefusalItReports();

  record Completed(
      String providerObjectId,
      @Nullable String createdObjectId,
      PayerOutcome outcome,
      List<String> webhookJobIds)
      implements HostedCompletionOutcome {

    public Completed {
      if (providerObjectId.equals(createdObjectId)) {
        throw new IllegalArgumentException(
            "createdObjectId names the object the payer's completion brought into existence, and"
                + " this one names the hosted process that was completed, which reports as new an"
                + " object the caller was already looking at");
      }
      webhookJobIds = List.copyOf(webhookJobIds);
    }

    @Override
    public ActionVerdict.@Nullable Refused theRefusalItReports() {
      return null;
    }
  }

  record RefusedByTheProvider(String inItsOwnWords) implements HostedCompletionOutcome {

    public RefusedByTheProvider {
      if (inItsOwnWords.isBlank()) {
        throw new IllegalArgumentException(
            "a provider that refused a completion answers with the refusal its own contract"
                + " publishes, and an empty refusal tells a tester only that something failed");
      }
    }

    @Override
    public ActionVerdict.Refused theRefusalItReports() {
      return ActionVerdict.refused(
          RefusalSubject.noObjectWasNamed(THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM));
    }
  }

  record NotOffered(String whatThisProviderHasInstead) implements HostedCompletionOutcome {

    public NotOffered {
      if (whatThisProviderHasInstead.isBlank()) {
        throw new IllegalArgumentException(
            "a provider whose module completes no hosted page must say in its own words what its"
                + " contract has in place of one, because a screen reporting that absence in this"
                + " simulator's words would be reporting a decision this simulator did not make");
      }
    }

    @Override
    public ActionVerdict.Refused theRefusalItReports() {
      return ActionVerdict.refused(
          RefusalSubject.noObjectWasNamed(THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM));
    }
  }

  record NoSuchObject() implements HostedCompletionOutcome {

    @Override
    public ActionVerdict.Refused theRefusalItReports() {
      return ActionVerdict.notFound(RefusalSubject.noObjectAnsweredTo(PROVIDER_OBJECT_ID));
    }
  }
}
