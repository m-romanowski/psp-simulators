package dev.marcinromanowski.pspsim.kernel.fault;

public enum TransportFaultKind {
  SERVER_ERROR,
  TRUNCATED_BODY,
  CORRUPTED_BODY,
  DROPPED_CONNECTION,
  DELAYED_RESPONSE
}
