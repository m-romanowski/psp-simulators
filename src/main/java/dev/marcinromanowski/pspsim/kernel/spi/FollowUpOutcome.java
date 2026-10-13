package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import java.util.List;

public sealed interface FollowUpOutcome {

  String THE_FOLLOW_UP_IT_ASKED_THIS_PROVIDER_TO_PERFORM =
      "the follow-up on a provider object this request asked this provider to perform";

  record Performed(String name, AddressedProviderObject after, List<String> webhookJobIds)
      implements FollowUpOutcome {

    public Performed {
      webhookJobIds = List.copyOf(webhookJobIds);
    }
  }

  record Refused(ActionVerdict.Refused verdict, String inItsOwnWords) implements FollowUpOutcome {

    public Refused {
      if (inItsOwnWords.isBlank()) {
        throw new IllegalArgumentException(
            "a refused follow-up answers with the reason it was refused, and an empty refusal"
                + " tells a tester only that something failed");
      }
    }
  }
}
