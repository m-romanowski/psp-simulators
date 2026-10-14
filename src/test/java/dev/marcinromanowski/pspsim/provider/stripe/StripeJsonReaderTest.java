package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StripeJsonReaderTest {

  @Test
  void an_answer_read_back_from_what_was_written_flattens_to_the_fields_it_was_written_from() {
    StripeJson written =
        StripeJson.object(
            "id",
            StripeJson.text("pi_1"),
            "amount",
            StripeJson.number("1099"),
            "livemode",
            StripeJson.bool(false),
            "customer",
            StripeJson.nothing(),
            "payment_method_types",
            StripeJson.array(List.of(StripeJson.text("card"), StripeJson.text("blik"))),
            "metadata",
            StripeJson.object("order", StripeJson.text("o-1")));

    StripeJson read = StripeJsonReader.read(StripeJsonWriter.toText(written));

    assertThat(read).isEqualTo(written);
    assertThat(read.flatten()).isEqualTo(written.flatten());
  }

  @Test
  void a_number_is_read_back_in_the_lexical_form_it_was_written_in() {
    assertThat(StripeJsonReader.read("{\"amount\":10.50}"))
        .isEqualTo(StripeJson.object("amount", StripeJson.number("10.50")));
  }
}
