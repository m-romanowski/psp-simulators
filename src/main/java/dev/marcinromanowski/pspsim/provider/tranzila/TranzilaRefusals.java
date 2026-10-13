package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;

final class TranzilaRefusals {

  static final String THE_CREDENTIAL_WAS_REFUSED =
      "provider tranzila refuses this call on the four authentication headers it carries, before"
          + " it reads anything the body would have named, so this refusal is about the caller and"
          + " not about any object this test run holds";

  static final String THE_BODY_WAS_REFUSED =
      "provider tranzila refuses this call on the fields the body carries, before it looks for"
          + " any transaction those fields would have named, so this refusal is about the request"
          + " and not about any object this test run holds";

  private TranzilaRefusals() {}

  static RefusalSubject theCredentialWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_CREDENTIAL_WAS_REFUSED);
  }

  static RefusalSubject theBodyWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_BODY_WAS_REFUSED);
  }

  static RefusalSubject noOriginalAnswered() {
    return RefusalSubject.noObjectAnsweredTo(TranzilaFields.REFERENCE_TXN_ID);
  }

  static RefusalSubject theTransaction(long transactionId) {
    return RefusalSubject.theObject(Long.toString(transactionId));
  }
}
