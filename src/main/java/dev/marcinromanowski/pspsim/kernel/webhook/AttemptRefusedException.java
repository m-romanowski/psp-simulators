package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import org.jspecify.annotations.Nullable;

public abstract sealed class AttemptRefusedException extends RuntimeException
    permits DeliveryHasNoTargetException, AttemptWithheldException {

  public static final String THE_JOB_IT_ASKED_TO_ATTEMPT =
      "the webhook job it asked to attempt, which reports no provider object";

  private static final long serialVersionUID = 1L;

  private final @Nullable String providerObjectId;

  protected AttemptRefusedException(@Nullable String providerObjectId, String message) {
    super(message);
    this.providerObjectId = providerObjectId;
  }

  public ActionVerdict.Refused theRefusalItReports() {
    return ActionVerdict.conflicting(
        providerObjectId == null
            ? RefusalSubject.noObjectWasNamed(THE_JOB_IT_ASKED_TO_ATTEMPT)
            : RefusalSubject.theObject(providerObjectId));
  }
}
