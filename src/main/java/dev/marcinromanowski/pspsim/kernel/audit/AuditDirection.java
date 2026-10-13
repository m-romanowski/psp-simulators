package dev.marcinromanowski.pspsim.kernel.audit;

public enum AuditDirection {
  INBOUND_REQUEST,
  OUTBOUND_RESPONSE,
  OUTBOUND_WEBHOOK_ATTEMPT,
  OUTBOUND_WEBHOOK_DROPPED,
  OUTBOUND_WEBHOOK_NOT_SENT
}
