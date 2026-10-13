package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedAttempt;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedCallback;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedExchange;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedObject;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedOperation;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionEvidence;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class RecordedEvidence {

  public static final String ADDRESSED_BY_ITS_PROVIDER_OBJECT_ID = "providerObjectId";

  public static final String ADDRESSED_BY_AN_IDENTIFIER_IT_ONCE_ANSWERED_TO =
      "anIdentifierItOnceAnsweredTo";

  private RecordedEvidence() {}

  public static ReproductionEvidence of(
      String key,
      AddressedProviderObject addressed,
      Instant effectiveClock,
      List<AuditRecord> trail,
      boolean trailTruncated,
      List<WebhookDelivery> callbacks,
      Map<String, List<WebhookAttempt>> attempts) {
    List<String> answersTo = addressed.answersTo();
    return new ReproductionEvidence(
        key,
        addressed.addressedByItsCanonicalMember()
            ? ADDRESSED_BY_ITS_PROVIDER_OBJECT_ID
            : ADDRESSED_BY_AN_IDENTIFIER_IT_ONCE_ANSWERED_TO,
        effectiveClock.toString(),
        theObjectOf(addressed),
        theTrailOf(answersTo, trail),
        theCallbacksOf(answersTo, callbacks, attempts),
        trailTruncated);
  }

  public static List<RecordedExchange> theTrailOf(List<String> answersTo, List<AuditRecord> trail) {
    List<RecordedExchange> carried = new ArrayList<>();
    for (AuditRecord record : trail) {
      if (!namesOneOf(answersTo, record.providerObjectId())) {
        continue;
      }
      RefusalSubject refusal = record.refusal();
      carried.add(
          new RecordedExchange(
              carried.size(),
              record.auditId(),
              record.origin().name(),
              record.direction().name(),
              record.method(),
              record.path(),
              record.httpStatus(),
              record.outcome() == null ? null : record.outcome().name(),
              refusal == null ? null : refusal.getClass().getSimpleName(),
              refusal == null ? null : theSubjectOf(refusal),
              record.scenarioId(),
              record.attempt(),
              record.createdAt().toString(),
              record.sanitizedHeaders(),
              record.sanitizedBody()));
    }
    return List.copyOf(carried);
  }

  public static List<RecordedCallback> theCallbacksOf(
      List<String> answersTo,
      List<WebhookDelivery> callbacks,
      Map<String, List<WebhookAttempt>> attempts) {
    List<RecordedCallback> carried = new ArrayList<>();
    for (WebhookDelivery delivery : callbacks) {
      if (!namesOneOf(answersTo, delivery.providerObjectId())) {
        continue;
      }
      Instant next = delivery.nextAttemptAt();
      carried.add(
          new RecordedCallback(
              delivery.deliveryId(),
              delivery.eventId(),
              delivery.eventType(),
              delivery.providerObjectId(),
              delivery.state().name(),
              delivery.targetUrl(),
              delivery.attemptsMade(),
              delivery.retryPlan().attemptCap(),
              next == null ? null : next.toString(),
              delivery.shapedBy(),
              delivery.notSentBecause(),
              delivery.payload().contentType(),
              delivery.payload().sanitizedBody(),
              theAttemptsOf(attempts.getOrDefault(delivery.deliveryId(), List.of()))));
    }
    return List.copyOf(carried);
  }

  public static List<RecordedAttempt> theAttemptsOf(List<WebhookAttempt> made) {
    List<RecordedAttempt> carried = new ArrayList<>();
    for (WebhookAttempt attempt : made) {
      carried.add(
          new RecordedAttempt(
              attempt.number(),
              attempt.attemptedAt().toString(),
              attempt.targetUrl(),
              attempt.verdict().name(),
              attempt.httpStatus(),
              attempt.failure() == null ? null : String.valueOf(attempt.failure()),
              attempt.responsePreview(),
              attempt.sentHeaders()));
    }
    return List.copyOf(carried);
  }

  public static RecordedObject theObjectOf(AddressedProviderObject addressed) {
    ProviderObjectState state = addressed.object();
    List<RecordedOperation> ledger = new ArrayList<>();
    for (MonetaryOperation operation : state.ledger().operations()) {
      ledger.add(
          new RecordedOperation(
              operation.sequence(),
              operation.kind().name(),
              operation.amount().value(),
              operation.closesAuthorization(),
              operation.createdObjectId(),
              operation.nativeStatus(),
              operation.normalizedStatus().name(),
              operation.at().toString()));
    }
    return new RecordedObject(
        state.providerObjectId(),
        addressed.answersTo(),
        state.nativeStatus(),
        state.normalizedStatus().name(),
        state.amount() == null ? null : state.amount().value(),
        state.currency(),
        state.authorizationOpen(),
        state.capturesAccepted(),
        state.refundsAccepted(),
        state.origin().name(),
        state.updatedAt().toString(),
        state.relatedObjects(),
        List.copyOf(ledger));
  }

  public static String theSubjectOf(RefusalSubject refusal) {
    return switch (refusal) {
      case RefusalSubject.TheObject named -> named.providerObjectId();
      case RefusalSubject.NoObjectAnsweredTo named -> named.theFieldThatNamedIt();
      case RefusalSubject.SeveralObjectsAnsweredTo named -> named.theFieldThatNamedThem();
      case RefusalSubject.AnObjectItWillNotName named -> named.whyItsContractNamesNone();
      case RefusalSubject.NoObjectWasNamed named -> named.whatItRefusedInstead();
    };
  }

  public static boolean namesOneOf(List<String> answersTo, @Nullable String carried) {
    return carried != null && answersTo.contains(carried);
  }
}
