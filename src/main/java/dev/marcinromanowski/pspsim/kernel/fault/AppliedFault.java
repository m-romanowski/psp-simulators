package dev.marcinromanowski.pspsim.kernel.fault;

public record AppliedFault(TransportFaultKind kind, FaultPlacement placement) {}
