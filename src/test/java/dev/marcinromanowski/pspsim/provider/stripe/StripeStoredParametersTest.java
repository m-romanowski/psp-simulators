package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeStoredParametersTest {
  @Test
  void both_identifier_bounds_refuse_the_first_unstoreable_character_without_echoing_the_value() {
    for (String parameter : List.of("customer", "payment_method")) {
      assertThat(StripeStoredParameters.refusalOf(parameter, "x".repeat(128))).isNull();
      StripeError refused = StripeStoredParameters.refusalOf(parameter, "x".repeat(129));
      assertThat(refused).isNotNull();
      assertThat(StripeJsonWriter.toText(refused.body()))
          .isEqualTo(
              "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"A parameter carries a value this API does not accept. It accepts an identifier of at most 128 characters.\",\"param\":\""
                  + parameter
                  + "\"}}");
    }
    assertThat(StripeStoredParameters.refusalOf("receipt_email", "x".repeat(5001))).isNull();
  }

  @Test
  void a_nul_is_refused_in_every_text_parameter_before_a_database_write() {
    StripeError refused =
        StripeStoredParameters.refusalOf(
            "receipt_email", Character.toString(0) + "payer@example.com");
    assertThat(refused).isNotNull();
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(refused.param()).isEqualTo("receipt_email");
    assertThat(refused.message())
        .isEqualTo(
            "A parameter carries a value this API does not accept. It accepts text without a NUL character.");
  }

  @Test
  void form_validation_skips_absent_values_and_stops_at_the_first_refusal() {
    StripeForm form =
        StripeForm.of(
            Map.of(
                "customer", List.of("c".repeat(129)), "payment_method", List.of("p".repeat(129))));
    StripeError refused =
        StripeStoredParameters.refusalOf(form, "receipt_email", "customer", "payment_method");
    assertThat(refused).isNotNull();
    assertThat(refused.param()).isEqualTo("customer");
    assertThat(
            StripeStoredParameters.refusalOf(
                StripeForm.of(Map.of("customer", List.of("cus_1"))), "receipt_email", "customer"))
        .isNull();
  }
}
