package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeTrailWithholdingTest {

  private static final String A_SECRET = "ek_sim_00000000000000000000000000000001";

  @Test
  void the_structured_delivery_floor_preserves_json_shape_and_safe_values() {
    StripeJson event =
        StripeJson.object(
            "data",
            StripeJson.object(
                "object",
                StripeJson.object(
                    "card",
                        StripeJson.object(
                            "number",
                            StripeJson.text("not-even-a-number"),
                            "cvc",
                            StripeJson.number("123"),
                            "last4",
                            StripeJson.text("4242")),
                    "metadata",
                        StripeJson.object(
                            "order_4242424242424242", StripeJson.text("4000-0000-0000-9995")),
                    "list",
                        StripeJson.array(
                            List.of(
                                StripeJson.text("4242 4242 4242 4242"),
                                StripeJson.number("4242424242424242"),
                                StripeJson.number("1000"),
                                StripeJson.bool(false),
                                StripeJson.nothing())))));
    StripeJson withheld = StripeFields.withholdingTheCard(event);
    assertThat(StripeJsonWriter.toText(withheld))
        .isEqualTo(
            "{\"data\":{\"object\":{\"card\":{\"number\":\"[redacted]\",\"cvc\":\"[redacted]\",\"last4\":\"4242\"},"
                + "\"metadata\":{\"order_[redacted]\":\"[redacted]\"},\"list\":[\"[redacted]\",\"[redacted]\",1000,false,null]}}}");
    assertThat(StripeFields.withholdingTheCard(withheld)).isEqualTo(withheld);
    assertThat(StripeJsonWriter.toText(event)).contains("not-even-a-number", "4242424242424242");
  }

  @Test
  void withholds_a_secret_that_sits_inside_an_array_rather_than_at_the_top_of_the_object() {
    StripeJson answer =
        StripeJson.object(
            "id",
            StripeJson.text("ephkey_sim_0000000000000002"),
            StripeFields.ASSOCIATED_OBJECTS,
            StripeJson.array(
                List.of(
                    StripeJson.object(
                        StripeFields.TYPE,
                        StripeJson.text("customer"),
                        StripeFields.SECRET,
                        StripeJson.text(A_SECRET)))));

    Map<String, List<String>> withheld =
        StripeKeyMechanisms.MECHANISMS.withholding().withheld(answer.flatten());

    assertThat(withheld)
        .containsEntry("associated_objects.secret", List.of(Redaction.REDACTED))
        .containsEntry("associated_objects.type", List.of("customer"));
    assertThat(withheld.toString()).doesNotContain(A_SECRET);
  }

  @Test
  void leaves_every_member_this_provider_has_not_declared_sensitive_exactly_where_it_was() {
    StripeJson answer =
        StripeJson.object(
            "id",
            StripeJson.text("ephkey_sim_0000000000000002"),
            "created",
            StripeJson.number("1788000000"),
            "livemode",
            StripeJson.bool(false),
            "customer",
            StripeJson.textOrNull(null),
            StripeFields.SECRET,
            StripeJson.text(A_SECRET));

    assertThat(StripeKeyMechanisms.MECHANISMS.withholding().withheld(answer.flatten()))
        .containsExactly(
            Map.entry("id", List.of("ephkey_sim_0000000000000002")),
            Map.entry("created", List.of("1788000000")),
            Map.entry("livemode", List.of("false")),
            Map.entry("customer", List.of()),
            Map.entry("secret", List.of(Redaction.REDACTED)));
  }
}
