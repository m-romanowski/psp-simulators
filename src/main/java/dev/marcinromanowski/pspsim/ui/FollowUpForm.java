package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUp;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class FollowUpForm {

  public static final String FOLLOW_UP = "followUp";

  public static final String CONFIRM_BEFORE_A_FOLLOW_UP =
      "tick confirm before a follow-up: it is a step its provider names and performs on its own"
          + " side, what it changes or sends is what the provider's own description beside the"
          + " button says, and closing the page undoes none of it";

  public record Offered(String name, String label, String whatItMoves) {}

  public sealed interface Asking {

    record NotAsked(String why) implements Asking {}

    record Asked(FollowUpRequest request) implements Asking {}
  }

  private FollowUpForm() {}

  public static List<Offered> of(List<FollowUp> followUps) {
    List<Offered> offered = new ArrayList<>();
    for (FollowUp followUp : followUps) {
      offered.add(
          new Offered(followUp.name(), followUp.asTheProviderNamesIt(), followUp.whatItMoves()));
    }
    return List.copyOf(offered);
  }

  public static Asking asked(
      TestRunId testRunId,
      @Nullable String providerObjectId,
      @Nullable String followUp,
      @Nullable String confirm) {
    if (!ObjectScreen.A_CHANGE_IS_CONFIRMED.equals(confirm)) {
      return new Asking.NotAsked(CONFIRM_BEFORE_A_FOLLOW_UP);
    }
    try {
      return new Asking.Asked(
          FollowUpRequest.of(testRunId, providerObjectId, followUp, ObjectOrigin.UI));
    } catch (IllegalArgumentException unreadable) {
      return new Asking.NotAsked(String.valueOf(unreadable.getMessage()));
    }
  }

  public static ActionVerdict.Refused theRejectionOfAFollowUpNeverAsked() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            FollowUpOutcome.THE_FOLLOW_UP_IT_ASKED_THIS_PROVIDER_TO_PERFORM));
  }

  public static @Nullable String theObjectItNamed(@Nullable String providerObjectId) {
    if (providerObjectId == null || providerObjectId.isBlank()) {
      return null;
    }
    return providerObjectId.strip();
  }

  public static Map<String, List<String>> factsOf(
      @Nullable String providerObjectId, @Nullable String followUp, String refusal) {
    return ActionFacts.none()
        .and(ObjectScreen.PROVIDER_OBJECT_ID, providerObjectId)
        .and(FOLLOW_UP, followUp)
        .and(ActionFacts.REFUSED_BECAUSE, refusal)
        .asRecorded();
  }

  public static Map<String, List<String>> factsOf(FollowUpOutcome.Performed performed) {
    return ActionFacts.none()
        .and(FOLLOW_UP, performed.name())
        .andEach("webhookJobIds", performed.webhookJobIds())
        .asRecorded();
  }
}
