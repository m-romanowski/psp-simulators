package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StripeRelatedObjects {

  private final StripePaymentIntents intents;
  private final StripeCharges charges;
  private final StripeRefunds refunds;
  private final StripeCustomers customers;
  private final StripePaymentMethods paymentMethods;

  StripeRelatedObjects(
      StripePaymentIntents intents,
      StripeCharges charges,
      StripeRefunds refunds,
      StripeCustomers customers,
      StripePaymentMethods paymentMethods) {
    this.intents = intents;
    this.charges = charges;
    this.refunds = refunds;
    this.customers = customers;
    this.paymentMethods = paymentMethods;
  }

  Reach reachedBy(TestRunId testRunId, StripeAccount account, Instant now) {
    return new Reach(this, testRunId, account, now, false);
  }

  Reach reachedByAPublishableKey(TestRunId testRunId, StripeAccount account, Instant now) {
    return new Reach(this, testRunId, account, now, true);
  }

  static final class Reach {

    private final StripeRelatedObjects held;
    private final TestRunId testRunId;
    private final StripeAccount account;
    private final Instant now;
    private final boolean onlyThePaymentMethod;
    private final Map<StripeRelation, Map<String, Optional<StripeJson>>> found = new HashMap<>();

    private Reach(
        StripeRelatedObjects held,
        TestRunId testRunId,
        StripeAccount account,
        Instant now,
        boolean onlyThePaymentMethod) {
      this.held = held;
      this.testRunId = testRunId;
      this.account = account;
      this.now = now;
      this.onlyThePaymentMethod = onlyThePaymentMethod;
    }

    Optional<StripeJson> find(StripeRelation relation, String id) {
      if (onlyThePaymentMethod && relation != StripeRelation.A_PAYMENT_METHOD) {
        return Optional.empty();
      }
      return found
          .computeIfAbsent(relation, unread -> new HashMap<>())
          .computeIfAbsent(id, unread -> lookedUp(relation, id));
    }

    Optional<StripeJson> included(StripeIncludable includable, String objectId) {
      return switch (includable) {
        case THE_REFUNDS_OF_THE_CHARGE -> Optional.of(theRefundsOf(objectId));
        case THE_CASH_BALANCE -> ofALiveCustomer(objectId, StripeJson.nothing());
        case THE_INVOICE_CREDIT_BALANCE -> ofALiveCustomer(objectId, StripeJson.object());
        case THE_SOURCES -> ofALiveCustomer(objectId, anEmptyList(objectId, "sources"));
        case THE_SUBSCRIPTIONS -> ofALiveCustomer(objectId, anEmptyList(objectId, "subscriptions"));
        case THE_TAX_DETAILS -> ofALiveCustomer(objectId, theTaxDetails());
        case THE_TAX_IDS -> ofALiveCustomer(objectId, anEmptyList(objectId, "tax_ids"));
      };
    }

    private Optional<StripeJson> lookedUp(StripeRelation relation, String id) {
      return switch (relation) {
        case A_CUSTOMER ->
            StripeCustomerLookup.of(held.customers, testRunId, account, id)
                .map(StripeCustomerView::of);
        case A_PAYMENT_METHOD ->
            StripePaymentMethodLookup.of(held.paymentMethods, testRunId, account, id)
                .map(StripePaymentMethodView::of);
        case A_CHARGE ->
            StripeChargeLookup.of(held.charges, testRunId, account, id)
                .flatMap(
                    charge ->
                        StripeLookup.of(held.intents, testRunId, account, charge.intentId())
                            .map(payment -> StripeChargeView.of(charge, payment, now)));
        case A_PAYMENT_INTENT ->
            StripeLookup.of(held.intents, testRunId, account, id)
                .map(intent -> StripeIntentView.of(intent, now));
      };
    }

    private StripeJson theRefundsOf(String chargeId) {
      List<StripeJson> listed = new ArrayList<>();
      for (StripeRefund refund :
          held.refunds.ofTheCharge(testRunId, account.accountId(), chargeId)) {
        listed.add(StripeRefundView.of(refund));
      }
      return aList(StripeApiController.CHARGES + "/" + chargeId + "/refunds", listed);
    }

    private Optional<StripeJson> ofALiveCustomer(String customerId, StripeJson answered) {
      return StripeCustomerLookup.of(held.customers, testRunId, account, customerId)
          .filter(customer -> !customer.deleted())
          .map(customer -> answered);
    }

    private static StripeJson anEmptyList(String customerId, String property) {
      return aList(StripeApiController.CUSTOMERS + "/" + customerId + "/" + property, List.of());
    }

    private static StripeJson aList(String url, List<StripeJson> data) {
      return StripeJson.object(
          "object",
          StripeJson.text(StripePaymentMethodView.LIST),
          "data",
          StripeJson.array(data),
          "has_more",
          StripeJson.bool(false),
          "url",
          StripeJson.text(url));
    }

    private static StripeJson theTaxDetails() {
      return StripeJson.object(
          "automatic_tax",
          StripeJson.text("unrecognized_location"),
          "ip_address",
          StripeJson.nothing(),
          "location",
          StripeJson.nothing(),
          "provider",
          StripeJson.text("stripe"));
    }
  }
}
