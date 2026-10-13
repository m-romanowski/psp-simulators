package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;

final class AutraRefusals {

  static final String THE_CREDENTIAL_WAS_REFUSED =
      "provider autra refuses this call on the credential it carries, before it looks for anything"
          + " the body would have named, so this refusal is about the caller and not about any"
          + " object this test run holds";

  static final String THE_BODY_WAS_REFUSED =
      "provider autra refuses this call on the fields the body carries, before it looks for the"
          + " payment those fields would have named, so this refusal is about the request and not"
          + " about any object this test run holds";

  static final String THE_ORDER_IS_ALREADY_HELD =
      "provider autra holds a pre-authorization under the orderId this call repeats and answers"
          + " the repeat 409 ORDER_ALREADY_PREAUTHORIZED, and its published conflict body carries"
          + " no payment identity at all, so the answer names neither the payment that holds the"
          + " order nor any other";

  static final String A_DECLINED_PRE_AUTHORIZATION_IS_NAMED_BY_NOTHING_ON_THE_WIRE =
      "provider autra answers a declined pre-authorization 422 with errors[], and its error"
          + " envelope publishes errors as its only top-level property, so the answer carries no"
          + " paymentID. This simulator keeps the attempt as an object so a tester can see it, and"
          + " the wire says nothing about it";

  static final String A_DECLINED_PIX_IS_NAMED_BY_NOTHING =
      "provider autra answers a refused Pix checkout with its error envelope, whose only top-level"
          + " property is errors, so the answer carries no pixId and no QR code. Nothing was"
          + " created for a tester to look at and nothing on the wire names one";

  static final String THE_ORDER_IS_ALREADY_APPROVED =
      "provider autra holds an approved payment under the orderId this call repeats and answers"
          + " the repeat 409 ORDER_ALREADY_APPROVED — \"Se um pagamento com o mesmo orderId ja"
          + " estiver ACCEPTED ou CAPTURED, retorna 409 sem criar um novo debito\" — and its"
          + " published conflict body is the bare errors[] envelope, whose only top-level property"
          + " is errors, so the answer names neither the payment that was approved nor any other."
          + " Autra publishes no lookup by orderId either, so nothing on this contract leads a"
          + " caller from the conflict back to the payment. The original payment is named on this"
          + " simulator's own keyed-exchange record of the refusal, which is a facility of this"
          + " simulator and not an answer provider autra gives";

  static final String A_DECLINED_SALE_IS_NAMED_BY_NOTHING_AT_422 =
      "provider autra answers a declined card sale 422 with errors[] under the reading this"
          + " account is configured for, and its error envelope publishes errors as its only"
          + " top-level property, so that answer carries no paymentID. The declined 200 the same"
          + " page declares does carry one. This simulator keeps the attempt as an object either"
          + " way, and under this reading the wire says nothing about it";

  private AutraRefusals() {}

  static RefusalSubject theCredentialWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_CREDENTIAL_WAS_REFUSED);
  }

  static RefusalSubject theBodyWasRefused() {
    return RefusalSubject.noObjectWasNamed(THE_BODY_WAS_REFUSED);
  }

  static RefusalSubject theOrderIsAlreadyApproved() {
    return RefusalSubject.anObjectItWillNotName(THE_ORDER_IS_ALREADY_APPROVED);
  }

  static RefusalSubject aDeclinedSaleAt422() {
    return RefusalSubject.anObjectItWillNotName(A_DECLINED_SALE_IS_NAMED_BY_NOTHING_AT_422);
  }

  static RefusalSubject theOrderIsAlreadyHeld() {
    return RefusalSubject.anObjectItWillNotName(THE_ORDER_IS_ALREADY_HELD);
  }

  static RefusalSubject aDeclinedPreAuthorization() {
    return RefusalSubject.anObjectItWillNotName(
        A_DECLINED_PRE_AUTHORIZATION_IS_NAMED_BY_NOTHING_ON_THE_WIRE);
  }
}
