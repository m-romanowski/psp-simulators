package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import java.util.List;
import org.junit.jupiter.api.Test;

class StripeResponseVersionTest {

  @Test
  void additive_selection_fields_are_emitted_before_the_snapshot_that_introduced_them() {
    StripeJson intent =
        StripeIntentView.of(
            StripeConformanceSamples.intent(
                StripeIntentStatus.REQUIRES_PAYMENT_METHOD,
                StripeCaptureMethod.AUTOMATIC,
                false,
                StripeSelection.allowedOnly(List.of("card"), List.of(StripePaymentMethodType.CARD)),
                Ledger.empty()),
            StripeConformanceSamples.NOW);
    for (String version : List.of("2020-08-27", "2026-04-22.dahlia", "2026-07-29.dahlia")) {
      assertThat(at(intent, version, StripeOperations.CREATE).flatten())
          .containsEntry("allowed_payment_method_types", List.of("card"));
    }
    StripeSelection selection =
        StripeSelection.dynamic(
            null, List.of("paypal"), null, List.of(StripePaymentMethodType.CARD));
    StripeJson excluded =
        StripeIntentView.of(
            StripeConformanceSamples.intent(
                StripeIntentStatus.REQUIRES_PAYMENT_METHOD,
                StripeCaptureMethod.AUTOMATIC,
                false,
                selection,
                Ledger.empty()),
            StripeConformanceSamples.NOW);
    assertThat(at(excluded, "2025-07-30.basil", StripeOperations.RETRIEVE).flatten())
        .containsEntry("excluded_payment_method_types", List.of("paypal"));
    StripeJson setup =
        StripeSetupIntentView.of(
            StripeConformanceSamples.setup(
                StripeSetupStatus.REQUIRES_PAYMENT_METHOD,
                StripeSetupUsage.OFF_SESSION,
                false,
                selection));
    assertThat(at(setup, "2025-09-30.clover", StripeOperations.RETRIEVE_SETUP_INTENT).flatten())
        .containsEntry("excluded_payment_method_types", List.of("paypal"));
  }

  @Test
  void customer_balances_and_tax_are_additive_at_the_mobile_pins_too() {
    StripeJson customer =
        StripeJsonReader.read(
            """
        {"id":"cus_sim_1","object":"customer","cash_balance":null,"invoice_credit_balance":{},"tax":{"provider":"stripe","automatic_tax":"unrecognized_location"}}
        """);
    for (String version : List.of("2020-03-02", "2020-08-27", "2025-09-30.clover")) {
      assertThat(at(customer, version, StripeOperations.RETRIEVE_CUSTOMER))
          .isEqualTo(
              StripeJsonReader.read(
                  """
          {"id":"cus_sim_1","object":"customer","cash_balance":null,"invoice_credit_balance":{},"tax":{"provider":"stripe","automatic_tax":"unrecognized_location"}}
          """));
    }
  }

  @Test
  void webhook_payloads_use_the_breaking_change_pipeline() {
    StripeJson expired =
        StripeJsonReader.read(
            """
        {"object":"payment_intent","id":"pi_sim_1","cancellation_reason":"expired"}
        """);
    StripeJson event =
        StripeEvent.of(
            "evt_sim_1",
            StripeEventType.CANCELED,
            expired,
            StripeConformanceSamples.NOW,
            new StripeApiVersion("2020-08-27"));
    assertThat(event.flatten())
        .containsEntry("api_version", List.of("2020-08-27"))
        .containsEntry("data.object.cancellation_reason", List.of("automatic"));
    StripeJson latest =
        StripeEvent.of(
            "evt_sim_1",
            StripeEventType.CANCELED,
            expired,
            StripeConformanceSamples.NOW,
            new StripeApiVersion("2026-10-28.endive"));
    assertThat(latest.flatten())
        .containsEntry("data.object.cancellation_reason", List.of("expired"));
  }

  private static StripeJson at(StripeJson answer, String version, String operation) {
    return StripeVersionChanges.asAnsweredAt(answer, new StripeApiVersion(version), operation);
  }
}
