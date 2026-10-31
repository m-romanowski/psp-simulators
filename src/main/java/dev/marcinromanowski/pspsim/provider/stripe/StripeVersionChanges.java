package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class StripeVersionChanges {

  static final StripeVersionChange PAYMENT_METHOD_TYPES_IS_NO_LONGER_WRITABLE =
      new StripeVersionChange(
          new StripeApiVersion("2026-09-30.endive"),
          "https://docs.stripe.com/changelog/endive/2026-09-30/"
              + "removes-the-payment-method-types-parameter-from-payment-intents-and-setup-intents",
          Set.of(
              StripeOperations.CREATE,
              StripeOperations.CONFIRM,
              StripeOperations.CREATE_SETUP_INTENT),
          StripeVersionChanges::paymentMethodTypesWasWritable);

  static final List<StripeVersionChange> DECLARED =
      List.of(PAYMENT_METHOD_TYPES_IS_NO_LONGER_WRITABLE);

  private StripeVersionChanges() {}

  static StripeCoreRequest atTheCore(StripeForm form, StripeApiVersion version, String operation) {
    return atTheCore(form, version, operation, DECLARED);
  }

  static StripeCoreRequest atTheCore(
      StripeForm form,
      StripeApiVersion version,
      String operation,
      List<StripeVersionChange> declared) {
    StripeCoreRequest request = StripeCoreRequest.asSent(form);
    for (StripeVersionChange change : walkingBackTo(version, declared).reversed()) {
      if (change.touches(operation)) {
        request = change.upgrade().apply(request);
      }
    }
    return request;
  }

  static List<StripeVersionChange> walkingBackTo(
      StripeApiVersion version, List<StripeVersionChange> declared) {
    List<StripeVersionChange> newer = new ArrayList<>();
    for (StripeApiVersion release : StripeApiVersions.answered().reversed()) {
      if (release.equals(version)) {
        break;
      }
      for (StripeVersionChange change : declared) {
        if (change.version().equals(release)) {
          newer.add(change);
        }
      }
    }
    return List.copyOf(newer);
  }

  private static StripeCoreRequest paymentMethodTypesWasWritable(StripeCoreRequest request) {
    StripeParameter written = request.form().parameters().get(StripeFields.PAYMENT_METHOD_TYPES);
    if (written == null) {
      return request;
    }
    return new StripeCoreRequest(
        request.form().withoutTheParameter(StripeFields.PAYMENT_METHOD_TYPES), written);
  }
}
