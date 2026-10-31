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
  void every_mobile_override_is_present_in_the_cited_stripe_recording() throws IOException {
    StripeJson evidence = StripeResponseConformanceTest.resource("evidence-overrides.json");
    for (StripeJson patch : ((StripeJson.JsonArray) member(evidence, "fields")).elements()) {
      String source = ((StripeJson.JsonText) member(patch, "source")).value();
      assertThat(((StripeJson.JsonText) member(patch, "reason")).value()).isNotBlank();
      if (!source.startsWith("stripe-ios-recorded-traffic/")) {
        continue;
      }
      try (InputStream input =
          Objects.requireNonNull(getClass().getResourceAsStream("/" + source))) {
        String recording = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(recording).contains("stripe-version: 2020-08-27");
        StripeJson response =
            StripeJsonReader.read(recording.substring(recording.indexOf("\n{") + 1));
        String field = ((StripeJson.JsonText) member(patch, "field")).value();
        assertThat(member(response, field))
            .as(source + " " + field)
            .isEqualTo(
                field.equals("capture_method")
                    ? StripeJson.text("automatic_async")
                    : StripeJson.nothing());
      }
    }
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
  void mobile_backport_overrides_check_types_and_do_not_permit_unrelated_fields()
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
