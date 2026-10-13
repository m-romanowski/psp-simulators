package dev.marcinromanowski.pspsim.kernel;

public enum NormalizedStatus {
  NOT_INITIALIZED,
  READY_FOR_CAPTURE,
  PARTIALLY_CAPTURED,
  PROCESSING,
  SUCCEEDED,
  PARTIALLY_REFUNDED,
  REFUNDED,
  FAILED,
  CANCELLED,
  UNKNOWN
}
