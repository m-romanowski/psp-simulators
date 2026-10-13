package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.util.List;

public record ContributedObject(
    ProviderObjectState object,
    List<FollowUpForm.Offered> followUps,
    HostedCompletionForm.View payerStep) {

  public ContributedObject {
    followUps = List.copyOf(followUps);
  }

  public String providerObjectId() {
    return object.providerObjectId();
  }

  public StatusBadge normalizedStatus() {
    return StatusBadge.of(object.normalizedStatus());
  }
}
