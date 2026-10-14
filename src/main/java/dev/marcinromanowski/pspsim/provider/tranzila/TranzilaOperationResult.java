package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import org.jspecify.annotations.Nullable;

record TranzilaOperationResult(
    ProviderOutcome outcome,
    @Nullable String providerObjectId,
    @Nullable RefusalSubject refusal,
    @Nullable String scenarioId,
    @Nullable TransportFault fault,
    @Nullable FaultedDelivery insteadOfTheOperation,
    TranzilaBody body) {

  static TranzilaOperationResult succeeded(@Nullable String providerObjectId, TranzilaBody body) {
    return new TranzilaOperationResult(
        ProviderOutcome.SUCCESS, providerObjectId, null, null, null, null, body);
  }

  static TranzilaOperationResult failed(RefusalSubject refusal, TranzilaBody body) {
    return new TranzilaOperationResult(
        ProviderOutcome.FAILURE,
        refusal.theObjectItNames().orElse(null),
        refusal,
        null,
        null,
        null,
        body);
  }

  static TranzilaOperationResult answeredInsteadOf(String scenarioId, FaultedDelivery answer) {
    return new TranzilaOperationResult(
        ProviderOutcome.UNKNOWN,
        null,
        null,
        scenarioId,
        null,
        answer,
        TranzilaBody.nothingRendered());
  }

  TranzilaOperationResult declinedBy(String scenarioId) {
    return new TranzilaOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  TranzilaOperationResult proceededUnder(String scenarioId) {
    if (outcome == ProviderOutcome.FAILURE) {
      return this;
    }
    return new TranzilaOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  TranzilaOperationResult faultedBy(String scenarioId, TransportFault fault) {
    return new TranzilaOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }
}
