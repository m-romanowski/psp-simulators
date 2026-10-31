package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class StripeConformanceSamples {

  static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
  static final TestRunId RUN = TestRunId.of("conformance");
  static final List<String> OPERATIONS =
      List.of(
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
          StripeOperations.CREATE_EPHEMERAL_KEY);

  record Sample(String schema, String variant, StripeJson body) {
    List<String> operations() {
      return switch (schema) {
        case "payment_intent", "charge", "refund" ->
            List.of(
                StripeOperations.CREATE,
                StripeOperations.CONFIRM,
                StripeOperations.CAPTURE,
                StripeOperations.CANCEL,
                StripeOperations.REFUND,
                StripeOperations.RETRIEVE,
                StripeOperations.RETRIEVE_CHARGE);
        case "setup_intent" ->
            List.of(
                StripeOperations.CREATE_SETUP_INTENT,
                StripeOperations.CONFIRM_SETUP_INTENT,
                StripeOperations.RETRIEVE_SETUP_INTENT);
        case "error" ->
            variant.contains("payment-error")
                ? List.of(StripeOperations.CONFIRM)
                : variant.contains("setup-error")
                    ? List.of(StripeOperations.CONFIRM_SETUP_INTENT)
                    : OPERATIONS;
        case "event" -> List.of(StripeVersionChanges.WEBHOOK);
        default -> OPERATIONS;
      };
    }

    StripeJson at(String version, String operation) {
      if (schema.equals("event")) {
        StripeEventType type =
            StripeEventType.of(
                    ((StripeJson.JsonText) StripeSpecSnapshot.member(body, "type")).value())
                .orElseThrow();
        return StripeEvent.of(
            "evt_sim_1",
            type,
            StripeSpecSnapshot.member(StripeSpecSnapshot.member(body, "data"), "object"),
            NOW,
            new StripeApiVersion(version));
      }
      return StripeVersionChanges.asAnsweredAt(body, new StripeApiVersion(version), operation);
    }
  }

  private StripeConformanceSamples() {}

  static List<Sample> all(boolean mobile) {
    List<Sample> samples = new ArrayList<>();
    List<StripeSelection> selections =
        List.of(
            StripeDoubles.DYNAMIC_SELECTION,
            StripeSelection.dynamic(
                StripeRedirects.ALWAYS,
                List.of("paypal"),
                "pmc_sim_1",
                List.of(StripePaymentMethodType.CARD)),
            StripeSelection.dynamic(
                StripeRedirects.NEVER, List.of(), null, List.of(StripePaymentMethodType.CARD)),
            StripeSelection.allowedOnly(
                List.of("card", "paypal"),
                List.of(StripePaymentMethodType.CARD, StripePaymentMethodType.PAYPAL)),
            StripeSelection.listed(false, null, List.of(StripePaymentMethodType.CARD)),
            StripeSelection.listed(null, null, List.of(StripePaymentMethodType.PAYPAL)));
    for (StripeIntentStatus status : StripeIntentStatus.values()) {
      for (StripeCaptureMethod capture : StripeCaptureMethod.values()) {
        for (boolean related : List.of(false, true)) {
          for (StripeSelection selection : selections) {
            StripePaymentIntent intent =
                intent(status, capture, related, selection, Ledger.empty());
            samples.add(
                new Sample(
                    "payment_intent",
                    "intent:" + status + ":" + capture + ":" + related + ":" + selection,
                    StripeIntentView.of(intent, NOW)));
          }
        }
      }
    }
    StripePaymentIntent payment =
        intent(
            StripeIntentStatus.SUCCEEDED,
            StripeCaptureMethod.MANUAL,
            true,
            StripeDoubles.DYNAMIC_SELECTION,
            ledger());
    for (StripeCancellationReason reason : StripeCancellationReason.values()) {
      samples.add(
          new Sample(
              "payment_intent",
              "cancellation:" + reason,
              StripeIntentView.of(payment.cancelledFor(reason, NOW), NOW)));
    }
    StripePaymentIntent expiring =
        intent(
            StripeIntentStatus.REQUIRES_CAPTURE,
            StripeCaptureMethod.MANUAL,
            true,
            StripeDoubles.DYNAMIC_SELECTION,
            ledger());
    samples.add(
        new Sample(
            "payment_intent", "expired", StripeIntentView.of(expiring, NOW.plusSeconds(7200))));
    for (StripeSetupStatus status : StripeSetupStatus.values()) {
      for (StripeSetupUsage usage : StripeSetupUsage.values()) {
        for (boolean related : List.of(false, true)) {
          for (StripeSelection selection : selections) {
            StripeSetupIntent setup = setup(status, usage, related, selection);
            samples.add(
                new Sample(
                    "setup_intent",
                    "setup:" + status + ":" + usage + ":" + related + ":" + selection,
                    StripeSetupIntentView.of(setup)));
            samples.add(
                new Sample(
                    "setup_intent",
                    "truncated:" + status,
                    StripeSetupIntentView.theTruncatedFormAnErrorCarries(setup)));
          }
        }
      }
    }
    for (StripeDecline decline : StripeDecline.values()) {
      StripePaymentIntent declined = payment.declinedWith(decline, "pm_sim_1", "ch_sim_1", NOW);
      StripeSetupIntent failed =
          setup(
                  StripeSetupStatus.REQUIRES_PAYMENT_METHOD,
                  StripeSetupUsage.OFF_SESSION,
                  true,
                  StripeDoubles.DYNAMIC_SELECTION)
              .failedWith(decline, "pm_sim_1", NOW);
      samples.add(new Sample("api_errors", "decline:" + decline, StripeDeclineView.of(decline)));
      samples.add(
          new Sample("payment_intent", "declined:" + decline, StripeIntentView.of(declined, NOW)));
      samples.add(
          new Sample("setup_intent", "failed:" + decline, StripeSetupIntentView.of(failed)));
      samples.add(
          new Sample(
              "error",
              "payment-error:" + decline,
              StripeErrors.declined(decline).about(StripeIntentView.of(declined, NOW)).body()));
      samples.add(
          new Sample(
              "error",
              "setup-error:" + decline,
              StripeErrors.declined(decline)
                  .aboutTheSetup(StripeSetupIntentView.theTruncatedFormAnErrorCarries(failed))
                  .body()));
    }
    for (StripeErrorType type : StripeErrorType.values()) {
      samples.add(
          new Sample(
              "error",
              "error:" + type,
              new StripeError(
                      400, type, "resource_missing", null, "No such object", "id", null, null)
                  .body()));
    }
    samples.add(new Sample("error", "authentication", StripeErrors.noApiKey().body()));
    for (boolean related : List.of(false, true)) {
      for (StripeChargeStatus status : StripeChargeStatus.values()) {
        StripeCharge charge = charge(status, related);
        samples.add(
            new Sample(
                "charge",
                "charge:" + status + ":" + related,
                StripeChargeView.of(charge, payment, NOW)));
        samples.add(
            new Sample("charge", "held:" + status, StripeChargeView.of(charge, expiring, NOW)));
      }
      for (StripeRefundStatus status : StripeRefundStatus.values()) {
        samples.add(
            new Sample(
                "refund",
                "refund:" + status + ":" + related,
                StripeRefundView.of(refund(status, null, related))));
        for (StripeRefundReason reason : StripeRefundReason.values()) {
          samples.add(
              new Sample(
                  "refund",
                  "refund:" + status + ":" + reason,
                  StripeRefundView.of(refund(status, reason, related))));
        }
      }
      samples.add(
          new Sample(
              "customer", "customer:" + related, StripeCustomerView.of(customer(related, false))));
      samples.add(
          new Sample(
              "deleted_customer", "deleted", StripeCustomerView.of(customer(related, true))));
      for (StripePaymentMethodType type : StripePaymentMethodType.values()) {
        StripePaymentMethod method = method(type, related);
        samples.add(
            new Sample(
                "payment_method",
                "method:" + type + ":" + related,
                StripePaymentMethodView.of(method)));
        samples.add(
            new Sample(
                "payment_method_list",
                "methods:" + type,
                StripePaymentMethodView.listOf(List.of(method), "/v1/payment_methods")));
      }
    }
    samples.add(
        new Sample(
            "payment_method_list",
            "empty-list",
            StripePaymentMethodView.listOf(List.of(), "/v1/payment_methods")));
    samples.add(
        new Sample(
            "ephemeral_key",
            "ephemeral",
            StripeEphemeralKeyView.of(
                new StripeEphemeralKey(
                    RUN,
                    "ephkey_sim_1",
                    "merchant",
                    "cus_sim_1",
                    "ek_test_secret",
                    NOW,
                    NOW.plusSeconds(3600)))));
    expanded(samples, payment);
    for (StripeEventType type : StripeEventType.values()) {
      StripeJson data =
          type == StripeEventType.REFUND_CREATED
              ? StripeRefundView.of(refund(StripeRefundStatus.SUCCEEDED, null, true))
              : StripeIntentView.of(payment, NOW);
      samples.add(
          new Sample(
              "event",
              "event:" + type,
              StripeEvent.of("evt_sim_1", type, data, NOW, StripeApiVersions.newest())));
    }
    List<Sample> publishable = new ArrayList<>();
    for (Sample sample : samples) {
      if (List.of("payment_intent", "setup_intent", "error").contains(sample.schema())) {
        publishable.add(
            new Sample(
                sample.schema(),
                "publishable:" + sample.variant(),
                StripePublishableView.of(sample.body())));
      }
    }
    if (mobile) {
      List<Sample> admitted = new ArrayList<>(publishable);
      samples.stream()
          .filter(
              sample ->
                  List.of(
                          "ephemeral_key",
                          "customer",
                          "deleted_customer",
                          "payment_method_list",
                          "payment_method")
                      .contains(sample.schema()))
          .forEach(admitted::add);
      return admitted;
    }
    samples.addAll(publishable);
    return samples;
  }

  static StripePaymentIntent intent(
      StripeIntentStatus status,
      StripeCaptureMethod capture,
      boolean related,
      StripeSelection selection,
      Ledger ledger) {
    return new StripePaymentIntent(
        RUN,
        "pi_sim_1",
        "merchant",
        Amount.of("1000"),
        "pln",
        capture,
        status,
        related ? "pm_sim_1" : null,
        related ? "cus_sim_1" : null,
        related ? "buyer@example.com" : null,
        selection,
        "pi_sim_1_secret_test",
        related ? "ch_sim_1" : null,
        null,
        null,
        NOW.plusSeconds(3600),
        null,
        null,
        ledger,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  static StripeSetupIntent setup(
      StripeSetupStatus status,
      StripeSetupUsage usage,
      boolean related,
      StripeSelection selection) {
    return new StripeSetupIntent(
        RUN,
        "seti_sim_1",
        "merchant",
        related ? "cus_sim_1" : null,
        related || status == StripeSetupStatus.SUCCEEDED ? "pm_sim_1" : null,
        usage,
        selection,
        status,
        "seti_sim_1_secret_test",
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static StripeCustomer customer(boolean email, boolean deleted) {
    return new StripeCustomer(
        RUN,
        "cus_sim_1",
        "merchant",
        email ? "buyer@example.com" : null,
        deleted ? NOW : null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static StripePaymentMethod method(StripePaymentMethodType type, boolean related) {
    return new StripePaymentMethod(
        RUN,
        "pm_sim_1",
        "merchant",
        related ? "cus_sim_1" : null,
        type,
        type.describesACard() ? new FixtureCard("visa", "4242", 12, 2030, "credit") : null,
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static StripeCharge charge(StripeChargeStatus status, boolean related) {
    return new StripeCharge(
        RUN,
        "ch_sim_1",
        "pi_sim_1",
        "merchant",
        Amount.of("1000"),
        "pln",
        related ? "pm_sim_1" : null,
        status,
        NOW);
  }

  private static StripeRefund refund(
      StripeRefundStatus status, @Nullable StripeRefundReason reason, boolean related) {
    return new StripeRefund(
        RUN,
        "re_sim_1",
        "pi_sim_1",
        related ? "ch_sim_1" : null,
        "merchant",
        Amount.of("100"),
        "pln",
        reason,
        status,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static Ledger ledger() {
    return new Ledger(
        List.of(
            new MonetaryOperation(
                1,
                OperationKind.AUTHORIZATION,
                Amount.of("1000"),
                null,
                null,
                "succeeded",
                NormalizedStatus.SUCCEEDED,
                NOW),
            new MonetaryOperation(
                2,
                OperationKind.CAPTURE,
                Amount.of("500"),
                false,
                null,
                "succeeded",
                NormalizedStatus.SUCCEEDED,
                NOW),
            new MonetaryOperation(
                3,
                OperationKind.REFUND,
                Amount.of("100"),
                null,
                "re_sim_1",
                "succeeded",
                NormalizedStatus.SUCCEEDED,
                NOW)));
  }

  private static void expanded(List<Sample> samples, StripePaymentIntent payment) {
    StripeDoubles.Intents intents = new StripeDoubles.Intents();
    StripeDoubles.Charges charges = new StripeDoubles.Charges(intents);
    StripeDoubles.Refunds refunds = new StripeDoubles.Refunds(intents);
    StripeDoubles.Customers customers = new StripeDoubles.Customers();
    StripeDoubles.PaymentMethods methods = new StripeDoubles.PaymentMethods();
    intents.insertIfAbsent(payment, charge(StripeChargeStatus.SUCCEEDED, true));
    customers.insertIfAbsent(customer(true, false));
    methods.insertIfAbsent(method(StripePaymentMethodType.CARD, true));
    refunds.refund(payment, null, refund(StripeRefundStatus.SUCCEEDED, null, true), NOW);
    StripeRelatedObjects.Reach reach =
        new StripeRelatedObjects(intents, charges, refunds, customers, methods)
            .reachedBy(
                RUN, StripeDoubles.account(RUN, "merchant", "sk_test_conformance", "P3D"), NOW);
    for (StripeExpandable kind : StripeExpandable.values()) {
      StripeJson body =
          switch (kind) {
            case PAYMENT_INTENT -> StripeIntentView.of(payment, NOW);
            case SETUP_INTENT ->
                StripeSetupIntentView.of(
                    setup(
                        StripeSetupStatus.SUCCEEDED,
                        StripeSetupUsage.OFF_SESSION,
                        true,
                        StripeDoubles.DYNAMIC_SELECTION));
            case CHARGE ->
                StripeChargeView.of(charge(StripeChargeStatus.SUCCEEDED, true), payment, NOW);
            case REFUND -> StripeRefundView.of(refund(StripeRefundStatus.SUCCEEDED, null, true));
            case CUSTOMER -> StripeCustomerView.of(customer(true, false));
            case PAYMENT_METHOD ->
                StripePaymentMethodView.of(method(StripePaymentMethodType.CARD, true));
          };
      List<String> paths =
          switch (kind) {
            case PAYMENT_INTENT ->
                List.of("customer", "payment_method.customer", "latest_charge.refunds.data.charge");
            case SETUP_INTENT -> List.of("customer", "payment_method.customer");
            case CHARGE ->
                List.of("customer", "payment_intent.payment_method", "refunds.data.payment_intent");
            case REFUND -> List.of("charge", "payment_intent.customer");
            case CUSTOMER ->
                List.of(
                    "cash_balance",
                    "invoice_credit_balance",
                    "sources",
                    "subscriptions",
                    "tax",
                    "tax_ids");
            case PAYMENT_METHOD -> List.of("customer");
          };
      StripeJson expanded =
          StripeExpansion.ofAnObject(kind)
              .answered(
                  StripeForm.of(Map.of("expand[]", paths)),
                  reach,
                  () -> StripeOperationResult.answered(body))
              .body();
      String schema = ((StripeJson.JsonText) StripeSpecSnapshot.member(body, "object")).value();
      samples.add(new Sample(schema, "expanded:" + kind, expanded));
    }
    StripeJson list =
        StripePaymentMethodView.listOf(
            List.of(method(StripePaymentMethodType.CARD, true)), "/v1/payment_methods");
    StripeJson expandedList =
        StripeExpansion.ofAList(StripeExpandable.PAYMENT_METHOD)
            .answered(
                StripeForm.of(Map.of("expand[]", List.of("data.customer"))),
                reach,
                () -> StripeOperationResult.answered(list))
            .body();
    samples.add(new Sample("payment_method_list", "expanded-list", expandedList));
  }
}
