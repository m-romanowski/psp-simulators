package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredWebhookCall;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class DeliveryForm {

  public static final String THE_TARGET_IS_NOT_CONFIGURED_FOR_THIS_RUN =
      "targetUrl must name one of the callback targets this test run configures. A delivery is"
          + " posted to an address a test declared, so this screen offers the run's targets and"
          + " accepts nothing else";

  public static final String THIS_RUN_CONFIGURES_NO_TARGET =
      "this test run configures no callback target, so there is no other address a delivery could"
          + " be posted to. Targets are configured on the Configuration screen";

  public static final String A_DELAY_IS_A_WHOLE_NUMBER_OF_MILLISECONDS =
      "delayMs must be a whole number of milliseconds on this test run's clock, or empty to"
          + " attempt the callback before this page comes back";

  public static final String A_DELAY_IS_NOT_NEGATIVE =
      "delayMs must not be negative: a callback cannot be scheduled before the moment it is asked"
          + " for";

  public static final String NO_DELIVERY_OF_THIS_RUN_ANSWERS_TO_THAT_ID =
      "this test run holds no callback delivery of this provider under that id; the run may have"
          + " been reset while this page was open";

  public static final String THE_EVENT_TYPE_A_SEND_NAMES = "webhook.eventType";

  public static final String AN_OBJECT_IS_REQUIRED =
      "providerObjectId names the object whose callback is to be sent, and this provider draws the"
          + " message from that object rather than from anything typed here";

  public record Target(String accountId, String url) {}

  public static WhatWasTyped theEventTypeTypedIn(@Nullable String eventType) {
    return WhatWasTyped.theFieldTyped(THE_EVENT_TYPE_A_SEND_NAMES, eventType);
  }

  public record Replayable(String eventId, boolean chosen) {}

  public record Composer(
      List<Replayable> eventIds,
      @Nullable String providerObjectId,
      @Nullable String eventType,
      @Nullable String delayMs,
      @Nullable String reuseEventId) {

    public Composer {
      eventIds = List.copyOf(eventIds);
    }
  }

  public sealed interface Posting {

    record ToTheTargetItWasScheduledFor() implements Posting {}

    record ToAConfiguredTarget(String url) implements Posting {}

    record Refused(String message) implements Posting {

      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(
                DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE));
      }
    }
  }

  public sealed interface Composed {

    record Send(DeclaredWebhookCall call) implements Composed {}

    record Refused(String message) implements Composed {

      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(
                DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE));
      }
    }
  }

  private DeliveryForm() {}

  public static List<Target> targetsOffered(List<WebhookTarget> configured) {
    List<Target> offered = new ArrayList<>();
    for (WebhookTarget target : configured) {
      offered.add(new Target(target.accountId(), target.url()));
    }
    return List.copyOf(offered);
  }

  public static Posting postingTo(List<WebhookTarget> configured, @Nullable String chosen) {
    if (chosen == null || chosen.isBlank()) {
      return new Posting.ToTheTargetItWasScheduledFor();
    }
    for (WebhookTarget target : configured) {
      if (target.url().equals(chosen)) {
        return new Posting.ToAConfiguredTarget(target.url());
      }
    }
    return new Posting.Refused(
        configured.isEmpty()
            ? THIS_RUN_CONFIGURES_NO_TARGET
            : THE_TARGET_IS_NOT_CONFIGURED_FOR_THIS_RUN);
  }

  public static Composer noneOffered() {
    return new Composer(List.of(), null, null, null, null);
  }

  public static Composer composerOf(
      List<WebhookDelivery> queued,
      @Nullable String providerObjectId,
      @Nullable String eventType,
      @Nullable String delayMs,
      @Nullable String reuseEventId) {
    Set<String> seen = new LinkedHashSet<>();
    for (WebhookDelivery delivery : queued) {
      seen.add(delivery.eventId());
    }
    List<Replayable> eventIds = new ArrayList<>();
    for (String eventId : seen) {
      eventIds.add(new Replayable(eventId, eventId.equals(reuseEventId)));
    }
    return new Composer(eventIds, providerObjectId, eventType, delayMs, reuseEventId);
  }

  public static Composed composed(
      String testRunId,
      @Nullable String providerObjectId,
      @Nullable String eventType,
      @Nullable String delayMs,
      @Nullable String reuseEventId) {
    if (providerObjectId == null || providerObjectId.isBlank()) {
      return new Composed.Refused(AN_OBJECT_IS_REQUIRED);
    }
    Long delay;
    try {
      delay = delayOf(delayMs);
    } catch (IllegalArgumentException rejected) {
      return new Composed.Refused(String.valueOf(rejected.getMessage()));
    }
    return new Composed.Send(
        new DeclaredWebhookCall(
            testRunId,
            null,
            providerObjectId.strip(),
            blankAsNothing(eventType),
            null,
            delay,
            blankAsNothing(reuseEventId),
            null,
            null));
  }

  private static @Nullable Long delayOf(@Nullable String delayMs) {
    if (delayMs == null || delayMs.isBlank()) {
      return null;
    }
    long milliseconds;
    try {
      milliseconds = Long.parseLong(delayMs.strip());
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException(A_DELAY_IS_A_WHOLE_NUMBER_OF_MILLISECONDS);
    }
    if (milliseconds < 0) {
      throw new IllegalArgumentException(A_DELAY_IS_NOT_NEGATIVE);
    }
    return milliseconds;
  }

  public static ActionVerdict.Refused theRefusalOfAnUnconfirmedCallback() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE));
  }

  public static ActionVerdict.Refused theRefusalOfAScheduling(Exception refused) {
    if (refused instanceof UnknownProviderObjectException) {
      return ActionVerdict.notFound(
          RefusalSubject.noObjectAnsweredTo(ObjectScreen.PROVIDER_OBJECT_ID));
    }
    if (refused instanceof WebhookNotSchedulableException) {
      return ActionVerdict.refused(
          RefusalSubject.noObjectWasNamed(
              DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE));
    }
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE));
  }

  public static ActionVerdict.Refused theRefusalOfASendThatScheduledNothing() {
    return ActionVerdict.notFound(
        RefusalSubject.noObjectWasNamed(
            DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE));
  }

  public static ActionVerdict.Refused theRefusalOfARetryOfNoDeliveryOfThisRun() {
    return ActionVerdict.notFound(
        RefusalSubject.noObjectAnsweredTo(DeclaredWebhookCall.WEBHOOK_JOB_ID));
  }

  public static Map<String, List<String>> factsOfARefusedSend(
      @Nullable String providerObjectId, @Nullable String eventType, String refusal) {
    return ActionFacts.none()
        .and(ObjectScreen.PROVIDER_OBJECT_ID, providerObjectId)
        .and("eventType", eventType)
        .and(ActionFacts.REFUSED_BECAUSE, refusal)
        .asRecorded();
  }

  public static Map<String, List<String>> factsOfARefusedRetry(
      String webhookJobId, @Nullable String targetUrl, String refusal) {
    return ActionFacts.none()
        .and(DeclaredWebhookCall.WEBHOOK_JOB_ID, webhookJobId)
        .and("targetUrl", targetUrl)
        .and(ActionFacts.REFUSED_BECAUSE, refusal)
        .asRecorded();
  }

  public static Map<String, List<String>> factsOf(DeclaredWebhookCall call, List<String> jobIds) {
    Long delayMs = call.delayMs();
    return ActionFacts.none()
        .and("eventType", call.eventType())
        .and("reuseEventId", call.reuseEventId())
        .and("delayMs", delayMs == null ? null : String.valueOf(delayMs))
        .andEach("webhookJobIds", jobIds)
        .asRecorded();
  }

  public static Map<String, List<String>> factsOf(String webhookJobId, @Nullable String targetUrl) {
    return ActionFacts.none()
        .and("webhookJobId", webhookJobId)
        .and("targetUrl", targetUrl)
        .asRecorded();
  }

  private static @Nullable String blankAsNothing(@Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return null;
    }
    return declared.strip();
  }
}
