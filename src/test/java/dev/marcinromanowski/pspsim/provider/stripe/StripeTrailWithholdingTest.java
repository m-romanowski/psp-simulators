package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.List;
import org.junit.jupiter.api.Test;

class StripeTrailWithholdingTest {

  private static final String A_SECRET = "ek_sim_00000000000000000000000000000001";

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

    String withheld =
        StripeJsonWriter.toText(answer.withholding(StripeFields.SENSITIVE_RESPONSE_FIELDS));

    assertThat(withheld)
        .doesNotContain(A_SECRET)
        .contains("\"secret\":\"" + Redaction.REDACTED + "\"")
        .contains("\"type\":\"customer\"");
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

    String withheld =
        StripeJsonWriter.toText(answer.withholding(StripeFields.SENSITIVE_RESPONSE_FIELDS));

    assertThat(withheld)
        .isEqualTo(
            "{\"id\":\"ephkey_sim_0000000000000002\",\"created\":1788000000,\"livemode\":false,"
                + "\"customer\":null,\"secret\":\""
                + Redaction.REDACTED
                + "\"}");
  }
}
