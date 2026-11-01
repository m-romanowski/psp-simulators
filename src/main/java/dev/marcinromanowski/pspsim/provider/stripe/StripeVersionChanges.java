package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  static final StripeVersionChange AN_EXPIRED_AUTHORIZATION_IS_CANCELLED_AS_EXPIRED =
      StripeVersionChange.ofTheAnswer(
          new StripeApiVersion("2025-03-31.basil"),
          "https://docs.stripe.com/changelog/basil/2025-03-31/"
              + "remove-refund-from-partial-capture-and-payment-cancellation-flow",
          Set.of(
              StripeOperations.CREATE,
              StripeOperations.CONFIRM,
              StripeOperations.CAPTURE,
              StripeOperations.CANCEL,
              StripeOperations.RETRIEVE,
              StripeOperations.RETRIEVE_CHARGE,
              StripeOperations.REFUND,
              "webhook"),
          StripeVersionChanges::anExpiredAuthorizationWasCancelledAutomatically);

  static final List<StripeVersionChange> DECLARED =
      List.of(
          AN_EXPIRED_AUTHORIZATION_IS_CANCELLED_AS_EXPIRED,
          PAYMENT_METHOD_TYPES_IS_NO_LONGER_WRITABLE);

  static final String EXPIRED = "expired";

  static final String AUTOMATIC = "automatic";

  static final String WEBHOOK = "webhook";

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

  static StripeJson asAnsweredAt(StripeJson answer, StripeApiVersion version, String operation) {
    return asAnsweredAt(answer, version, operation, DECLARED);
  }

  static StripeJson asAnsweredAt(
      StripeJson answer,
      StripeApiVersion version,
      String operation,
      List<StripeVersionChange> declared) {
    StripeJson answered = answer;
    for (StripeVersionChange change : walkingBackTo(version, declared)) {
      if (change.touches(operation)) {
        answered = change.downgrade().apply(answered);
      }
    }
    return answered;
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

  private static StripeJson anExpiredAuthorizationWasCancelledAutomatically(StripeJson answer) {
    return switch (answer) {
      case StripeJson.JsonObject object -> {
        Map<String, StripeJson> members = new LinkedHashMap<>();
        object
            .members()
            .forEach(
                (name, member) ->
                    members.put(name, anExpiredAuthorizationWasCancelledAutomatically(member)));
        if (StripeJson.text(StripeIntentView.OBJECT).equals(members.get("object"))
            && StripeJson.text(EXPIRED).equals(members.get(StripeFields.CANCELLATION_REASON))) {
          members.put(StripeFields.CANCELLATION_REASON, StripeJson.text(AUTOMATIC));
        }
        yield new StripeJson.JsonObject(members);
      }
      case StripeJson.JsonArray array -> {
        List<StripeJson> elements = new ArrayList<>();
        for (StripeJson element : array.elements()) {
          elements.add(anExpiredAuthorizationWasCancelledAutomatically(element));
        }
        yield StripeJson.array(elements);
      }
      default -> answer;
    };
  }
}
