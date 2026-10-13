package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public record ObjectActionRequest(
    TestRunId testRunId,
    String providerObjectId,
    ObjectAction action,
    @Nullable Amount amount,
    ObjectOrigin askedBy) {

  public static final String AN_AMOUNT_IS_REQUIRED =
      "name the amount to move; this simulator does not guess one, because the amount a provider"
          + " would take by default is that provider's rule and not a number a screen may invent";

  public ObjectActionRequest {
    if (providerObjectId.isBlank()) {
      throw new IllegalArgumentException(
          "an action names the object it acts on by the id the provider gave it, and this one"
              + " names none");
    }
    if (askedBy == ObjectOrigin.PROVIDER_API) {
      throw new IllegalArgumentException(
          "a client's request reaches an operation through the provider's own route, which checks"
              + " the credential and the signature that route publishes, so a request that"
              + " arrived that way is never asked for through this port");
    }
    if (askedBy == ObjectOrigin.SCHEDULED_JOB) {
      throw new IllegalArgumentException(
          "the delivery queue moves callbacks and never money, so no scheduled job acts on an"
              + " object through this port");
    }
    if (amount == null && action.takesAnAmount()) {
      throw new IllegalArgumentException(AN_AMOUNT_IS_REQUIRED);
    }
    if (amount != null && !action.takesAnAmount()) {
      throw new IllegalArgumentException(
          "an action that asks the provider to "
              + action.asked()
              + " releases what the object still holds and takes no amount of its own, so a"
              + " caller naming one is naming a number nothing would read");
    }
  }

  public static ObjectActionRequest of(
      TestRunId testRunId,
      String providerObjectId,
      ObjectAction action,
      @Nullable String amount,
      ObjectOrigin askedBy) {
    return new ObjectActionRequest(
        testRunId,
        providerObjectId.strip(),
        action,
        amount == null || amount.isBlank() ? null : Amount.of(amount.strip()),
        askedBy);
  }

  public Amount theAmountAsked() {
    Amount asked = amount;
    if (asked == null) {
      throw new IllegalStateException(
          "an action that asks the provider to " + action.asked() + " carries no amount");
    }
    return asked;
  }
}
