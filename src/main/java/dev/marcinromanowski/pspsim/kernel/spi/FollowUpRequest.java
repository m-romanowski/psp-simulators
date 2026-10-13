package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public record FollowUpRequest(
    TestRunId testRunId, String providerObjectId, String name, ObjectOrigin askedBy) {

  public static final String A_FOLLOW_UP_IS_NAMED =
      "name the follow-up to perform, by the name the object offers it under; this simulator does"
          + " not pick one, because a follow-up moves provider state and which one a tester meant"
          + " is the whole of what the request decides";

  public FollowUpRequest {
    if (providerObjectId.isBlank()) {
      throw new IllegalArgumentException(
          "a follow-up names the object it moves by the id the provider gave it, and this one"
              + " names none");
    }
    if (name.isBlank()) {
      throw new IllegalArgumentException(A_FOLLOW_UP_IS_NAMED);
    }
    if (askedBy == ObjectOrigin.PROVIDER_API) {
      throw new IllegalArgumentException(
          "a follow-up is a step the provider takes on its own side and no client request of its"
              + " contract asks for, so a request that arrived over the provider's own route never"
              + " performs one");
    }
    if (askedBy == ObjectOrigin.SCHEDULED_JOB) {
      throw new IllegalArgumentException(
          "the delivery queue moves the callbacks a follow-up has already caused and never causes"
              + " one, so no scheduled job performs a follow-up");
    }
  }

  public static FollowUpRequest of(
      TestRunId testRunId,
      @Nullable String providerObjectId,
      @Nullable String name,
      ObjectOrigin askedBy) {
    return new FollowUpRequest(
        testRunId,
        providerObjectId == null ? "" : providerObjectId.strip(),
        name == null ? "" : name.strip(),
        askedBy);
  }
}
