package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FollowUpControl {

  public static final String PROVIDER_OBJECT_ID = "providerObjectId";

  private FollowUpControl() {}

  public static String theRunHoldsNoSuchObject(ProviderId provider) {
    return "this test run holds no object of provider "
        + provider.value()
        + " under that providerObjectId, so there is nothing for a follow-up to move";
  }

  public static String theObjectDoesNotOfferIt(ProviderId provider, String name) {
    return "object of provider "
        + provider.value()
        + " offers no follow-up named "
        + name
        + " in the state it is in now; the Payments screen lists the follow-ups it offers";
  }

  public static FollowUpOutcome perform(
      SimulatorProvider module, FollowUpRequest request, Instant now) {
    Optional<AddressedProviderObject> before =
        AddressedProviderObject.read(module, request.testRunId(), request.providerObjectId());
    if (before.isEmpty()) {
      return noSuchObject(module);
    }
    String canonical = before.get().canonical();
    if (!offers(module.followUps(request.testRunId(), canonical, now), request.name())) {
      return new FollowUpOutcome.Refused(
          ActionVerdict.refused(RefusalSubject.theObject(canonical)),
          theObjectDoesNotOfferIt(module.id(), request.name()));
    }
    List<ScheduledWebhook> scheduled;
    try {
      scheduled =
          module.followUp(
              new FollowUpRequest(
                  request.testRunId(), canonical, request.name(), request.askedBy()),
              now);
    } catch (FollowUpNotOfferedException notOfferedNow) {
      return new FollowUpOutcome.Refused(
          ActionVerdict.refused(RefusalSubject.theObject(canonical)),
          String.valueOf(notOfferedNow.getMessage()));
    } catch (UnknownProviderObjectException goneBeforeItMoved) {
      return noSuchObject(module);
    }
    return AddressedProviderObject.read(module, request.testRunId(), request.providerObjectId())
        .<FollowUpOutcome>map(
            after -> new FollowUpOutcome.Performed(request.name(), after, jobIdsOf(scheduled)))
        .orElseGet(() -> noSuchObject(module));
  }

  private static boolean offers(List<FollowUp> offered, String name) {
    for (FollowUp followUp : offered) {
      if (followUp.name().equals(name)) {
        return true;
      }
    }
    return false;
  }

  private static FollowUpOutcome noSuchObject(SimulatorProvider module) {
    return new FollowUpOutcome.Refused(
        ActionVerdict.notFound(RefusalSubject.noObjectAnsweredTo(PROVIDER_OBJECT_ID)),
        theRunHoldsNoSuchObject(module.id()));
  }

  private static List<String> jobIdsOf(List<ScheduledWebhook> scheduled) {
    List<String> jobIds = new ArrayList<>();
    for (ScheduledWebhook job : scheduled) {
      jobIds.add(job.webhookJobId());
    }
    return List.copyOf(jobIds);
  }
}
