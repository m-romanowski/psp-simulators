package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StripePublishableViewTest {

  @Test
  void a_payment_keeps_only_the_members_a_publishable_key_is_shown() {
    StripeJson whole =
        StripeJson.object(
            "id", StripeJson.text("pi_sim_1"),
            "object", StripeJson.text("payment_intent"),
            "amount", StripeJson.number("1099"),
            "amount_capturable", StripeJson.number("1099"),
            "amount_received", StripeJson.number("0"),
            "customer", StripeJson.text("cus_sim_1"),
            "latest_charge", StripeJson.text("ch_sim_1"),
            "metadata", StripeJson.object(),
            "status", StripeJson.text("requires_capture"));

    assertThat(StripeJsonWriter.toText(StripePublishableView.of(whole)))
        .isEqualTo(
            "{\"id\":\"pi_sim_1\",\"object\":\"payment_intent\",\"amount\":1099,"
                + "\"status\":\"requires_capture\"}");
  }

  @Test
  void a_setup_keeps_only_the_members_a_publishable_key_is_shown() {
    StripeJson whole =
        StripeJson.object(
            "id", StripeJson.text("seti_sim_1"),
            "object", StripeJson.text("setup_intent"),
            "customer", StripeJson.text("cus_sim_1"),
            "latest_attempt", StripeJson.text("setatt_1"),
            "mandate", StripeJson.nothing(),
            "usage", StripeJson.text("off_session"));

    assertThat(StripeJsonWriter.toText(StripePublishableView.of(whole)))
        .isEqualTo("{\"id\":\"seti_sim_1\",\"object\":\"setup_intent\",\"usage\":\"off_session\"}");
  }

  @Test
  void an_error_keeps_its_own_members_and_reduces_the_intent_it_embeds() {
    StripeJson refused =
        StripeJson.object(
            "error",
            StripeJson.object(
                "type", StripeJson.text("card_error"),
                "code", StripeJson.text("card_declined"),
                "setup_intent",
                    StripeJson.object(
                        "id", StripeJson.text("seti_sim_1"),
                        "object", StripeJson.text("setup_intent"),
                        "customer", StripeJson.text("cus_sim_1"))));

    assertThat(StripeJsonWriter.toText(StripePublishableView.of(refused)))
        .isEqualTo(
            "{\"error\":{\"type\":\"card_error\",\"code\":\"card_declined\",\"setup_intent\":"
                + "{\"id\":\"seti_sim_1\",\"object\":\"setup_intent\"}}}");
  }

  @Test
  void an_expanded_payment_method_inside_a_payment_is_kept_but_for_its_metadata() {
    StripeJson method =
        StripeJson.object(
            "id", StripeJson.text("pm_1"),
            "object", StripeJson.text("payment_method"),
            "customer", StripeJson.text("cus_sim_1"),
            "metadata", StripeJson.object("order", StripeJson.text("o-1")));
    StripeJson whole =
        StripeJson.object(
            "id", StripeJson.text("pi_sim_1"),
            "object", StripeJson.text("payment_intent"),
            "payment_method", method);

    assertThat(StripeJsonWriter.toText(StripePublishableView.of(whole)))
        .isEqualTo(
            "{\"id\":\"pi_sim_1\",\"object\":\"payment_intent\",\"payment_method\":"
                + "{\"id\":\"pm_1\",\"object\":\"payment_method\",\"customer\":\"cus_sim_1\"}}");
  }

  @Test
  void a_payment_method_answered_to_a_publishable_key_carries_no_metadata() {
    StripeJson method =
        StripeJson.object(
            "id", StripeJson.text("pm_1"),
            "object", StripeJson.text("payment_method"),
            "metadata", StripeJson.object("order", StripeJson.text("o-1")),
            "type", StripeJson.text("card"));

    assertThat(StripeJsonWriter.toText(StripePublishableView.of(method)))
        .isEqualTo("{\"id\":\"pm_1\",\"object\":\"payment_method\",\"type\":\"card\"}");
  }

  @Test
  void an_answer_that_is_no_intent_is_handed_on_unchanged() {
    StripeJson aMethod =
        StripeJson.object(
            "id", StripeJson.text("cus_sim_1"),
            "object", StripeJson.text("customer"),
            "email", StripeJson.text("payer@example.com"));
    StripeJson aList = StripeJson.array(List.of(StripeJson.text("x")));
    StripeJson anErrorThatIsNoObject = StripeJson.object("error", StripeJson.text("x"));

    assertThat(StripePublishableView.of(aMethod)).isSameAs(aMethod);
    assertThat(StripePublishableView.of(aList)).isSameAs(aList);
    assertThat(StripeJsonWriter.toText(StripePublishableView.of(anErrorThatIsNoObject)))
        .isEqualTo("{\"error\":\"x\"}");
  }

  @Test
  void the_members_shown_are_those_the_recorded_publishable_key_answers_carry() {
    assertThat(StripePublishableView.WHAT_A_PAYMENT_INTENT_SHOWS)
        .containsExactlyInAnyOrder(
            "allowed_payment_method_types",
            "amount",
            "amount_details",
            "amount_subtotal",
            "automatic_payment_methods",
            "canceled_at",
            "cancellation_reason",
            "capture_method",
            "client_secret",
            "confirmation_method",
            "created",
            "currency",
            "description",
            "excluded_payment_method_types",
            "id",
            "last_payment_error",
            "livemode",
            "next_action",
            "object",
            "payment_method",
            "payment_method_configuration_details",
            "payment_method_options",
            "payment_method_types",
            "processing",
            "receipt_email",
            "setup_future_usage",
            "shipping",
            "source",
            "status",
            "total_details");
    assertThat(StripePublishableView.WHAT_A_SETUP_INTENT_SHOWS)
        .containsExactlyInAnyOrder(
            "allowed_payment_method_types",
            "automatic_payment_methods",
            "cancellation_reason",
            "client_secret",
            "created",
            "description",
            "excluded_payment_method_types",
            "id",
            "last_setup_error",
            "livemode",
            "next_action",
            "object",
            "payment_method",
            "payment_method_configuration_details",
            "payment_method_options",
            "payment_method_types",
            "status",
            "usage");
  }

  @Test
  void a_secret_key_is_shown_the_answer_whole_and_a_publishable_key_its_reduced_view() {
    StripeJson whole =
        StripeJson.object(
            "object", StripeJson.text("payment_intent"), "customer", StripeJson.text("cus_sim_1"));

    assertThat(StripeKeyKind.SECRET_KEY.asShownTo(whole)).isSameAs(whole);
    assertThat(StripeKeyKind.SHORT_LIVED_KEY.asShownTo(whole)).isSameAs(whole);
    assertThat(StripeKeyKind.PUBLISHABLE_KEY.asShownTo(whole))
        .isEqualTo(StripeJson.object("object", StripeJson.text("payment_intent")));
  }
}
