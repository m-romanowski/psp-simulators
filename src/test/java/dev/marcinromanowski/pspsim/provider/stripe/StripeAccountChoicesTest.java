package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StripeAccountChoicesTest {

  @Test
  void the_five_statuses_a_refund_reaches_say_which_of_them_leave_the_money_refundable() {
    assertThat(valuesOf(StripeRefundOutcome.SETTING))
        .containsExactly("PENDING", "REQUIRES_ACTION", "SUCCEEDED", "FAILED", "CANCELED");
    assertThat(field(StripeRefundOutcome.SETTING).acceptedValues())
        .filteredOn(offered -> offered.whatItMeans().contains("stays refundable"))
        .extracting(AcceptedValue::value)
        .as("failed and canceled are the two that move no money")
        .containsExactly("FAILED", "CANCELED");
  }

  @Test
  void the_two_signatures_an_event_carries_say_what_each_gives_a_merchant_check_to_do() {
    assertThat(StripeSignatureEmission.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "VALID", "a delivered event is signed so your own Stripe-Signature check passes"),
            new AcceptedValue(
                "CORRUPTED",
                "a delivered event carries a v1 element that cannot verify, so your check has"
                    + " something to refuse"));
  }

  @Test
  void the_values_offered_for_each_field_are_every_value_that_field_has() {
    assertThat(valuesOf(StripeRefundOutcome.SETTING))
        .containsExactlyElementsOf(namesOf(StripeRefundStatus.values()));
    assertThat(valuesOf(StripeWebhookSigning.EMISSION_SETTING))
        .containsExactlyElementsOf(namesOf(StripeSignatureEmission.values()));
  }

  @Test
  void every_value_a_field_offers_is_one_this_provider_takes() {
    for (String offered : valuesOf(StripeRefundOutcome.SETTING)) {
      assertThat(StripeRefundStatus.named(offered)).as(offered).isPresent();
    }
    for (String offered : valuesOf(StripeWebhookSigning.EMISSION_SETTING)) {
      assertThat(StripeSignatureEmission.of(offered)).as(offered).isPresent();
    }
  }

  @Test
  void
      the_window_this_provider_refuses_an_account_without_is_declared_required_and_shows_a_shape() {
    ConfigurableField window = field(StripeCaptureWindow.SETTING);

    assertThat(window.required())
        .as(
            "the field read \"Leaving it empty holds for Stripe's own default\" and carried no"
                + " required badge, while StripeCaptureWindow refuses a blank outright: the"
                + " screen told a tester that empty was allowed and the call then refused it")
        .isTrue();
    assertThat(window.whatItIsFor()).contains("refuses an account that leaves this empty");
    assertThat(window.example()).isEqualTo("P3D");
  }

  @Test
  void the_two_settings_a_signing_secret_carries_say_they_are_refused_without_one() {
    assertThat(field(StripeWebhookSigning.EMISSION_SETTING).whatItIsFor())
        .as("they read \"needs a webhook signing secret to mean anything\" and are refused")
        .contains("is refused");
    assertThat(field(StripeWebhookSigning.AGE_SETTING).whatItIsFor()).contains("is refused");
    assertThat(field(StripeWebhookSigning.AGE_SETTING).example()).isEqualTo("PT6M");
  }

  @ParameterizedTest
  @CsvSource({
    StripeAccount.SECRET_KEY_PROPERTY,
    StripeCaptureWindow.SETTING,
    StripeWebhookSigning.SECRET_SETTING,
    StripeWebhookSigning.AGE_SETTING
  })
  void a_field_whose_values_are_not_a_closed_set_offers_none_of_them(String key) {
    assertThat(field(key).acceptedValues())
        .as(
            "a secret key, a signing secret and two spans of time: none of them is a set this"
                + " provider could write out")
        .isEmpty();
  }

  @Test
  void the_two_spans_hold_one_and_the_credentials_beside_them_do_not() {
    assertThat(field(StripeCaptureWindow.SETTING).holdsADuration()).isTrue();
    assertThat(field(StripeWebhookSigning.AGE_SETTING).holdsADuration()).isTrue();
    assertThat(field(StripeAccount.SECRET_KEY_PROPERTY).holdsADuration()).isFalse();
    assertThat(field(StripeWebhookSigning.SECRET_SETTING).holdsADuration()).isFalse();
  }

  @Test
  void the_two_spans_say_how_each_reader_states_one() {
    assertThat(field(StripeCaptureWindow.SETTING).whatItIsFor())
        .as(
            "this sentence is read on the form and answered to a control call asking what this"
                + " provider takes, and the two readers state a span in different forms")
        .contains("A form states it as a number and a unit")
        .contains("an ISO-8601 duration such as P3D");
    assertThat(field(StripeWebhookSigning.AGE_SETTING).whatItIsFor())
        .contains("A form states it as a number and a unit")
        .contains("an ISO-8601 duration such as PT6M");
  }

  @Test
  void the_five_refund_statuses_are_declared_with_the_wire_word_and_the_money_each_one_leaves() {
    assertThat(StripeRefundStatus.acceptedValues())
        .as(
            "a form offers the sentence and never the enum name, so the word Stripe puts on the"
                + " wire and what each status does to the refundable amount are written out here")
        .containsExactly(
            new AcceptedValue(
                "PENDING",
                "a refund of this account comes back \"pending\", so your client reads a refund"
                    + " still being processed, and its amount is already off what the payment can"
                    + " refund"),
            new AcceptedValue(
                "REQUIRES_ACTION",
                "a refund comes back \"requires_action\", so your client reads a refund waiting on"
                    + " the payer, and its amount is already off what the payment can refund"),
            new AcceptedValue(
                "SUCCEEDED",
                "a refund comes back \"succeeded\", where Stripe's own card refund comes to rest,"
                    + " and its amount is off what the payment can refund"),
            new AcceptedValue(
                "FAILED",
                "a refund comes back \"failed\", and its amount stays refundable because no money"
                    + " moved"),
            new AcceptedValue(
                "CANCELED",
                "a refund comes back \"canceled\", and its amount stays refundable because no"
                    + " money moved"));
  }

  private static List<String> namesOf(Enum<?>[] values) {
    List<String> names = new ArrayList<>();
    for (Enum<?> value : values) {
      names.add(value.name());
    }
    return names;
  }

  private static List<String> valuesOf(String key) {
    List<String> values = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      values.add(accepted.value());
    }
    return values;
  }

  private static ConfigurableField field(String key) {
    return StripeAccount.ACCOUNT_FIELDS.field(key).orElseThrow();
  }
}
