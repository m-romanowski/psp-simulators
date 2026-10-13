package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;

final class CancelBitTransaction {

  private final GrowScalarType scalarType;

  CancelBitTransaction(GrowScalarType scalarType) {
    this.scalarType = scalarType;
  }

  GrowOperationResult handle(GrowForm form) {
    for (String required : GrowFields.REQUIRED_ON_CANCEL_BIT_TRANSACTION) {
      if (!form.has(required)) {
        return failure(GrowRefusals.theFormWasRefused(), GrowErrors.missingRequiredField(required));
      }
    }
    return failure(
        RefusalSubject.noObjectWasNamed(GrowRefusals.NO_PROCESS_IS_EVER_ADDRESSED),
        GrowErrors.invalidProcessLookupCredentials());
  }

  private GrowOperationResult failure(RefusalSubject refusal, GrowError error) {
    return GrowOperationResult.failed(refusal, GrowEnvelope.failure(error, scalarType));
  }
}
