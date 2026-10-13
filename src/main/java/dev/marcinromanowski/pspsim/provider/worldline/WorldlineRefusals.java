package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;

final class WorldlineRefusals {

  static final String THE_CREDENTIAL_WAS_REFUSED =
      "provider worldline refuses this call on the Authorization header it carries, before it"
          + " looks for anything the path or the body would have named, so this refusal is about"
          + " the caller and not about any object this test run holds. The vendor publishes this"
          + " case as 403 with errors.id ACCESS_TO_MERCHANT_NOT_ALLOWED and publishes no 401 at"
          + " all: \"The Direct API uses the following HTTP response codes\" lists 200, 201, 202,"
          + " 400, 402, 403, 404, 409, 410, 500, 502 and 503";

  static final String THE_BODY_WAS_REFUSED =
      "provider worldline refuses this call on the fields the body carries, before it looks for"
          + " the payment those fields would have named, so this refusal is about the request and"
          + " not about any object this test run holds";

  static final String A_DECLINED_PAYMENT_IS_NAMED_BY_THE_ENVELOPE_ITSELF =
      "provider worldline answers a declined CreatePayment 402 with a paymentErrorResponse, which"
          + " adds a third member to errorId and errors[]: paymentResult, \"a full"
          + " createPaymentResponse if one has been generated\". So unlike the bare errorResponse"
          + " every other route of this contract answers with, a decline here does name the"
          + " payment it declined, and this simulator puts the rejected payment in that member";

  static final String THE_KEY_IS_STILL_HELD_BY_A_REQUEST_THAT_HAS_NOT_FINISHED =
      "provider worldline publishes one 409 for a key a request has not finished under — \"For"
          + " requests in progress: A response with an HTTP status code of 409 (Conflict) will"
          + " indicate that the request is currently being processed\" — and that answer names no"
          + " object, because the request that claimed the key has not produced one yet";

  private WorldlineRefusals() {}

  static RefusalSubject theCredentialWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_CREDENTIAL_WAS_REFUSED);
  }

  static RefusalSubject theBodyWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_BODY_WAS_REFUSED);
  }

  static RefusalSubject aDeclinedPayment(String paymentId) {
    return RefusalSubject.theObject(paymentId);
  }

  static RefusalSubject theKeyIsStillHeld() {
    return RefusalSubject.anObjectItWillNotName(
        THE_KEY_IS_STILL_HELD_BY_A_REQUEST_THAT_HAS_NOT_FINISHED);
  }
}
