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
              StripeOperations.REFUND),
          StripeVersionChanges::anExpiredAuthorizationWasCancelledAutomatically);

  static final String WEBHOOK = "webhook";

  private static final Set<String> RESPONSE_OPERATIONS =
      Set.of(
          StripeOperations.CREATE,
          StripeOperations.CONFIRM,
          StripeOperations.CAPTURE,
          StripeOperations.CANCEL,
          StripeOperations.REFUND,
          StripeOperations.RETRIEVE,
          StripeOperations.RETRIEVE_CHARGE,
          StripeOperations.CREATE_CUSTOMER,
          StripeOperations.RETRIEVE_CUSTOMER,
          StripeOperations.UPDATE_CUSTOMER,
          StripeOperations.DELETE_CUSTOMER,
          StripeOperations.LIST_PAYMENT_METHODS,
          StripeOperations.LIST_CUSTOMER_PAYMENT_METHODS,
          StripeOperations.RETRIEVE_PAYMENT_METHOD,
          StripeOperations.DETACH_PAYMENT_METHOD,
          StripeOperations.CREATE_SETUP_INTENT,
          StripeOperations.CONFIRM_SETUP_INTENT,
          StripeOperations.RETRIEVE_SETUP_INTENT,
          StripeOperations.CREATE_EPHEMERAL_KEY,
          WEBHOOK);

  static final StripeVersionChange ALLOWED_PAYMENT_METHOD_TYPES_WAS_ADDED =
      addedField(
          "2026-07-29.dahlia",
          "19be337d65af244fed355eaeb144de6dbeea82f1/latest/openapi.spec3.json",
          Set.of("payment_intent", "setup_intent"),
          "allowed_payment_method_types");

  static final StripeVersionChange PAYMENT_INTENT_EXCLUDED_TYPES_WAS_ADDED =
      addedField(
          "2025-08-27.basil",
          "62fbe0713198e910b8843f70fdcd351be3ec979c/openapi/spec3.json",
          Set.of("payment_intent"),
          "excluded_payment_method_types");

  static final StripeVersionChange SETUP_INTENT_EXCLUDED_TYPES_WAS_ADDED =
      addedField(
          "2025-10-29.clover",
          "260f91c74e44bcd2a819bac9428f6c9e57360105/openapi/spec3.json",
          Set.of("setup_intent"),
          "excluded_payment_method_types");

  static final StripeVersionChange CUSTOMER_TAX_PROVIDER_WAS_ADDED =
      StripeVersionChange.ofTheAnswer(
          new StripeApiVersion("2025-10-29.clover"),
          "https://github.com/stripe/openapi/blob/260f91c74e44bcd2a819bac9428f6c9e57360105/openapi/spec3.json",
          RESPONSE_OPERATIONS,
          answer -> withoutTaxProvider(answer));

  static final StripeVersionChange CUSTOMER_BALANCES_AND_TAX_POSTDATE_THE_MOBILE_PINS =
      StripeVersionChange.ofTheAnswer(
          new StripeApiVersion("2025-03-31.basil"),
          "https://github.com/stripe/openapi/blob/adae467392d46f04ac1afca7b96e7d471c647e1c/openapi/spec3.json",
          RESPONSE_OPERATIONS,
          answer ->
              withoutFields(
                  answer,
                  Set.of("customer"),
                  Set.of("cash_balance", "invoice_credit_balance", "tax")));

  private static final Set<StripeVersionChange> RESPONSE_ADDITIONS_RECORDED_AT_MOBILE_PINS =
      Set.of(
          ALLOWED_PAYMENT_METHOD_TYPES_WAS_ADDED,
          PAYMENT_INTENT_EXCLUDED_TYPES_WAS_ADDED,
          SETUP_INTENT_EXCLUDED_TYPES_WAS_ADDED);

  static final List<StripeVersionChange> DECLARED =
      List.of(
          CUSTOMER_BALANCES_AND_TAX_POSTDATE_THE_MOBILE_PINS,
          AN_EXPIRED_AUTHORIZATION_IS_CANCELLED_AS_EXPIRED,
          PAYMENT_INTENT_EXCLUDED_TYPES_WAS_ADDED,
          SETUP_INTENT_EXCLUDED_TYPES_WAS_ADDED,
          CUSTOMER_TAX_PROVIDER_WAS_ADDED,
          ALLOWED_PAYMENT_METHOD_TYPES_WAS_ADDED,
          PAYMENT_METHOD_TYPES_IS_NO_LONGER_WRITABLE);

  static final String EXPIRED = "expired";

  static final String AUTOMATIC = "automatic";

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
      if (change.touches(operation)
          && !(StripeApiVersions.PINNED_BY_THE_MOBILE_SDKS.contains(version)
              && RESPONSE_ADDITIONS_RECORDED_AT_MOBILE_PINS.contains(change))) {
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

  private static StripeVersionChange addedField(
      String version, String source, Set<String> kinds, String field) {
    return StripeVersionChange.ofTheAnswer(
        new StripeApiVersion(version),
        "https://github.com/stripe/openapi/blob/" + source,
        RESPONSE_OPERATIONS,
        answer -> withoutFields(answer, kinds, Set.of(field)));
  }

  private static StripeJson withoutFields(
      StripeJson answer, Set<String> kinds, Set<String> fields) {
    return transformObjects(
        answer,
        object -> {
          Map<String, StripeJson> members = new LinkedHashMap<>(object.members());
          if (kinds.stream()
              .anyMatch(kind -> StripeJson.text(kind).equals(members.get("object")))) {
            fields.forEach(members::remove);
          }
          return new StripeJson.JsonObject(members);
        });
  }

  private static StripeJson withoutTaxProvider(StripeJson answer) {
    return transformObjects(
        answer,
        object -> {
          Map<String, StripeJson> members = new LinkedHashMap<>(object.members());
          if (StripeJson.text("customer").equals(members.get("object"))
              && members.get("tax") instanceof StripeJson.JsonObject tax) {
            Map<String, StripeJson> fields = new LinkedHashMap<>(tax.members());
            fields.remove("provider");
            members.put("tax", new StripeJson.JsonObject(fields));
          }
          return new StripeJson.JsonObject(members);
        });
  }

  private static StripeJson transformObjects(
      StripeJson answer, java.util.function.UnaryOperator<StripeJson.JsonObject> transform) {
    return switch (answer) {
      case StripeJson.JsonObject object -> {
        Map<String, StripeJson> members = new LinkedHashMap<>();
        object
            .members()
            .forEach((name, member) -> members.put(name, transformObjects(member, transform)));
        yield transform.apply(new StripeJson.JsonObject(members));
      }
      case StripeJson.JsonArray array ->
          StripeJson.array(
              array.elements().stream()
                  .map(element -> transformObjects(element, transform))
                  .toList());
      default -> answer;
    };
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
