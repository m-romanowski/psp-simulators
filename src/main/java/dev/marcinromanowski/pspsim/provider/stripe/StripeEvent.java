package dev.marcinromanowski.pspsim.provider.stripe;

import java.time.Instant;

final class StripeEvent {

  static final String OBJECT = "event";

  static final String CONTENT_TYPE = "application/json";

  static final String API_VERSION = "2026-01-01";

  static final String PENDING_WEBHOOKS = "0";

  private StripeEvent() {}

  static StripeJson of(
      String eventId, StripeEventType type, StripeJson dataObject, Instant createdAt) {
    return StripeJson.object(
        "id",
        StripeJson.text(eventId),
        "object",
        StripeJson.text(OBJECT),
        "api_version",
        StripeJson.text(API_VERSION),
        "type",
        StripeJson.text(type.wireValue()),
        "created",
        StripeJson.number(Long.toString(createdAt.getEpochSecond())),
        "livemode",
        StripeJson.bool(false),
        "pending_webhooks",
        StripeJson.number(PENDING_WEBHOOKS),
        "request",
        StripeJson.object("id", StripeJson.nothing(), "idempotency_key", StripeJson.nothing()),
        "data",
        StripeJson.object("object", dataObject));
  }
}
