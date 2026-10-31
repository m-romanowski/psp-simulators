package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import java.util.List;
import org.junit.jupiter.api.Test;

class StripeResponseVersionTest {

  @Test
  void allowed_types_start_at_the_july_dahlia_release() {
    StripeJson intent =
        StripeIntentView.of(
            StripeConformanceSamples.intent(
                StripeIntentStatus.REQUIRES_PAYMENT_METHOD,
                StripeCaptureMethod.AUTOMATIC,
                false,
                StripeSelection.allowedOnly(List.of("card"), List.of(StripePaymentMethodType.CARD)),
                Ledger.empty()),
            StripeConformanceSamples.NOW);
    assertThat(at(intent, "2026-06-24.dahlia", StripeOperations.CREATE).flatten())
        .doesNotContainKey("allowed_payment_method_types");
    assertThat(at(intent, "2026-07-29.dahlia", StripeOperations.CREATE).flatten())
        .containsEntry("allowed_payment_method_types", List.of("card"));
    StripeJson setup =
        StripeSetupIntentView.of(
            StripeConformanceSamples.setup(
                StripeSetupStatus.REQUIRES_PAYMENT_METHOD,
                StripeSetupUsage.OFF_SESSION,
                false,
                StripeSelection.allowedOnly(
                    List.of("card"), List.of(StripePaymentMethodType.CARD))));
    assertThat(at(setup, "2026-06-24.dahlia", StripeOperations.CREATE_SETUP_INTENT).flatten())
        .doesNotContainKey("allowed_payment_method_types");
    assertThat(at(setup, "2026-07-29.dahlia", StripeOperations.CREATE_SETUP_INTENT).flatten())
        .containsEntry("allowed_payment_method_types", List.of("card"));
  }

  @Test
  void excluded_types_start_at_different_releases_on_payment_and_setup_intents() {
    StripeSelection selection =
        StripeSelection.dynamic(
            null, List.of("paypal"), null, List.of(StripePaymentMethodType.CARD));
    StripeJson intent =
        StripeIntentView.of(
            StripeConformanceSamples.intent(
                StripeIntentStatus.REQUIRES_PAYMENT_METHOD,
                StripeCaptureMethod.AUTOMATIC,
                false,
                selection,
                Ledger.empty()),
            StripeConformanceSamples.NOW);
    assertThat(at(intent, "2025-07-30.basil", StripeOperations.RETRIEVE).flatten())
        .doesNotContainKey("excluded_payment_method_types");
    assertThat(at(intent, "2025-08-27.basil", StripeOperations.RETRIEVE).flatten())
        .containsEntry("excluded_payment_method_types", List.of("paypal"));
    StripeJson setup =
        StripeSetupIntentView.of(
            StripeConformanceSamples.setup(
                StripeSetupStatus.REQUIRES_PAYMENT_METHOD,
                StripeSetupUsage.OFF_SESSION,
                false,
                selection));
    assertThat(at(setup, "2025-09-30.clover", StripeOperations.RETRIEVE_SETUP_INTENT).flatten())
        .doesNotContainKey("excluded_payment_method_types");
    assertThat(at(setup, "2025-10-29.clover", StripeOperations.RETRIEVE_SETUP_INTENT).flatten())
        .containsEntry("excluded_payment_method_types", List.of("paypal"));
  }

  @Test
  void customer_tax_provider_starts_at_the_october_clover_release() {
    StripeJson customer = customer();
    assertThat(at(customer, "2025-09-30.clover", StripeOperations.RETRIEVE_CUSTOMER).flatten())
        .doesNotContainKey("tax.provider");
    assertThat(at(customer, "2025-10-29.clover", StripeOperations.RETRIEVE_CUSTOMER).flatten())
        .containsEntry("tax.provider", List.of("stripe"));
    StripeJson embedded =
        StripeJson.object(
            "error", StripeJson.object("payment_intent", StripeJson.object("customer", customer)));
    assertThat(at(embedded, "2025-09-30.clover", StripeOperations.CONFIRM).flatten())
        .doesNotContainKey("error.payment_intent.customer.tax.provider");
  }

  @Test
  void mobile_customers_omit_balances_and_tax_that_their_schemas_do_not_publish() {
    for (String version : List.of("2020-03-02", "2020-08-27")) {
      assertThat(
              StripeJsonWriter.toText(at(customer(), version, StripeOperations.RETRIEVE_CUSTOMER)))
          .isEqualTo("{\"id\":\"cus_sim_1\",\"object\":\"customer\"}");
    }
    assertThat(at(customer(), "2025-03-31.basil", StripeOperations.RETRIEVE_CUSTOMER).flatten())
        .containsKeys("cash_balance", "tax.automatic_tax");
  }

  @Test
  void webhook_payloads_are_transformed_at_the_version_the_event_names() {
    StripeJson intent =
        StripeIntentView.of(
            StripeConformanceSamples.intent(
                StripeIntentStatus.SUCCEEDED,
                StripeCaptureMethod.AUTOMATIC,
                true,
                StripeDoubles.DYNAMIC_SELECTION,
                Ledger.empty()),
            StripeConformanceSamples.NOW);
    StripeJson event =
        StripeEvent.of(
            "evt_sim_1",
            StripeEventType.SUCCEEDED,
            intent,
            StripeConformanceSamples.NOW,
            new StripeApiVersion("2025-03-31.basil"));
    assertThat(event.flatten())
        .containsEntry("api_version", List.of("2025-03-31.basil"))
        .doesNotContainKeys(
            "data.object.allowed_payment_method_types",
            "data.object.excluded_payment_method_types");
    StripeJson latest =
        StripeEvent.of(
            "evt_sim_1",
            StripeEventType.SUCCEEDED,
            intent,
            StripeConformanceSamples.NOW,
            new StripeApiVersion("2026-10-28.endive"));
    assertThat(latest.flatten())
        .containsKeys(
            "data.object.allowed_payment_method_types",
            "data.object.excluded_payment_method_types");
  }

  @Test
  void mobile_backports_preserve_the_selection_fields_recorded_in_real_answers() {
    StripeJson intent =
        StripeIntentView.of(
            StripeConformanceSamples.intent(
                StripeIntentStatus.REQUIRES_PAYMENT_METHOD,
                StripeCaptureMethod.AUTOMATIC,
                false,
                StripeDoubles.DYNAMIC_SELECTION,
                Ledger.empty()),
            StripeConformanceSamples.NOW);
    assertThat(at(intent, "2020-08-27", StripeOperations.RETRIEVE).flatten())
        .containsEntry("allowed_payment_method_types", List.of())
        .containsEntry("excluded_payment_method_types", List.of());
  }

  private static StripeJson customer() {
    return StripeJson.object(
        "id",
        StripeJson.text("cus_sim_1"),
        "object",
        StripeJson.text("customer"),
        "cash_balance",
        StripeJson.nothing(),
        "invoice_credit_balance",
        StripeJson.object(),
        "tax",
        StripeJson.object(
            "provider",
            StripeJson.text("stripe"),
            "automatic_tax",
            StripeJson.text("unrecognized_location")));
  }

  private static StripeJson at(StripeJson answer, String version, String operation) {
    return StripeVersionChanges.asAnsweredAt(answer, new StripeApiVersion(version), operation);
  }
}
