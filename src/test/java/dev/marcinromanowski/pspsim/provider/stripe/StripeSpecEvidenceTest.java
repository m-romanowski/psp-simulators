package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.member;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class StripeSpecEvidenceTest {

  @Test
  void a_full_recorded_mobile_payment_method_validates_without_drift_exclusions()
      throws IOException {
    StripeJson response =
        recorded(
            "/stripe-ios-recorded-traffic/STPPaymentMethodFunctionalTest/testCreateCardPaymentMethod/0000_post_v1_payment_methods.tail");
    assertThat(member(response, "object")).isEqualTo(StripeJson.text("payment_method"));
    assertThat(
            new StripeResponseSchema(StripeSpecEvidence.schemasAt("2020-08-27"))
                .problems("payment_method", response))
        .isEmpty();
  }

  @Test
  void recorded_mobile_additions_validate_on_the_surface_the_simulator_renders()
      throws IOException {
    StripeJson response =
        recorded(
            "/stripe-ios-recorded-traffic/PaymentSheetLPMConfirmFlowTests/testLinkCardConfirmFlows/0002_get_v1_payment_intents_pi_3UVdlcGoesj9fw9Q1dp9Xowy.tail");
    java.util.Map<String, StripeJson> rendered = new java.util.LinkedHashMap<>();
    for (String field :
        java.util.List.of(
            "id",
            "object",
            "allowed_payment_method_types",
            "automatic_payment_methods",
            "excluded_payment_method_types",
            "payment_method_configuration_details",
            "capture_method",
            "status")) {
      rendered.put(field, member(response, field));
    }
    assertThat(member(response, "allowed_payment_method_types")).isEqualTo(StripeJson.nothing());
    assertThat(
            new StripeResponseSchema(StripeSpecEvidence.schemasAt("2020-08-27"))
                .problems("payment_intent", new StripeJson.JsonObject(rendered)))
        .isEmpty();
  }

  private StripeJson recorded(String source) throws IOException {
    try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(source))) {
      String recording = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(recording).contains("stripe-version: 2020-08-27");
      return StripeJsonReader.read(recording.substring(recording.indexOf("\n{") + 1));
    }
  }

  @Test
  void allowed_payment_method_types_validates_additively_at_old_releases() throws IOException {
    for (String version : java.util.List.of("2026-04-22.dahlia", "2020-08-27")) {
      StripeResponseSchema schema = new StripeResponseSchema(StripeSpecEvidence.schemasAt(version));
      assertThat(
              schema.problems(
                  "payment_intent",
                  StripeJsonReader.read(
                      """
          {"object":"payment_intent","allowed_payment_method_types":["card"]}
          """)))
          .isEmpty();
    }
  }

  @Test
  void a_breaking_enum_replacement_is_not_backported_as_an_additive_value() throws IOException {
    StripeResponseSchema schema =
        new StripeResponseSchema(StripeSpecEvidence.schemasAt("2020-08-27"));
    assertThat(
            schema.problems(
                "payment_intent",
                StripeJsonReader.read(
                    """
        {"cancellation_reason":"expired"}
        """)))
        .containsExactly(
            new StripeResponseSchema.Problem(
                "payment_intent.cancellation_reason", "breaking-enum"));
    assertThat(
            schema.problems(
                "payment_intent",
                StripeJsonReader.read(
                    """
        {"cancellation_reason":"automatic"}
        """)))
        .isEmpty();
  }

  @Test
  void the_ephemeral_key_example_extension_checks_its_members_and_types() throws IOException {
    StripeResponseSchema schema =
        new StripeResponseSchema(StripeSpecEvidence.schemasAt("2026-10-28.endive"));
    assertThat(
            schema.problems(
                "ephemeral_key",
                StripeJsonReader.read(
                    """
        {"associated_objects":[{"type":"customer","id":"cus_1"}]}
        """)))
        .isEmpty();
    assertThat(
            schema.problems(
                "ephemeral_key",
                StripeJsonReader.read(
                    """
        {"associated_objects":[{"type":"invented","id":4,"unknown":false}]}
        """)))
        .containsExactlyInAnyOrder(
            new StripeResponseSchema.Problem("ephemeral_key.associated_objects[].type", "enum"),
            new StripeResponseSchema.Problem(
                "ephemeral_key.associated_objects[].id", "type:string"),
            new StripeResponseSchema.Problem(
                "ephemeral_key.associated_objects[].unknown", "unpublished"));
  }

  @Test
  void additive_fields_keep_their_earliest_published_types_and_refuse_unknown_fields()
      throws IOException {
    StripeResponseSchema schema =
        new StripeResponseSchema(StripeSpecEvidence.schemasAt("2020-08-27"));
    assertThat(
            schema.problems(
                "payment_intent",
                StripeJsonReader.read(
                    """
        {"allowed_payment_method_types":true,"automatic_payment_methods":{"enabled":"true"},"invented":null}
        """)))
        .containsExactlyInAnyOrder(
            new StripeResponseSchema.Problem(
                "payment_intent.allowed_payment_method_types", "type:array"),
            new StripeResponseSchema.Problem(
                "payment_intent.automatic_payment_methods.enabled", "type:boolean"),
            new StripeResponseSchema.Problem("payment_intent.invented", "unpublished"));
  }
}
