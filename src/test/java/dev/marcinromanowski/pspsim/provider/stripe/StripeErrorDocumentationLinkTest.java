package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeErrorDocumentationLinkTest {

  @Test
  void sends_a_caller_reading_a_declined_card_to_the_entry_published_for_that_code() {
    assertThat(linkIn(StripeErrors.declined(StripeDecline.INSUFFICIENT_FUNDS)))
        .isEqualTo("https://docs.stripe.com/error-codes#card-declined");
  }

  @Test
  void spells_a_code_of_four_words_the_way_the_published_table_anchors_it() {
    assertThat(linkIn(StripeErrors.unexpectedState("A PaymentIntent cannot be captured.")))
        .isEqualTo("https://docs.stripe.com/error-codes#payment-intent-unexpected-state");
  }

  @Test
  void sends_a_caller_nowhere_when_the_refusal_names_no_code_to_look_up() {
    assertThat(bodyOf(StripeErrors.noApiKey())).doesNotContain("doc_url");
    assertThat(bodyOf(StripeErrors.noValidApiKey())).doesNotContain("doc_url");
    assertThat(bodyOf(StripeErrors.idempotencyKeyReused())).doesNotContain("doc_url");
  }

  @Test
  void links_every_code_this_build_can_answer_with_to_the_entry_published_for_it() {
    Map<String, String> linked = new LinkedHashMap<>();

    for (StripeError refusal : everyRefusalThisBuildCanAnswerWith()) {
      String code = refusal.code();
      if (code == null) {
        assertThat(bodyOf(refusal)).doesNotContain("doc_url");
        continue;
      }
      linked.put(code, linkIn(refusal));
    }

    assertThat(linked)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                Map.entry("card_declined", "https://docs.stripe.com/error-codes#card-declined"),
                Map.entry(
                    "resource_missing", "https://docs.stripe.com/error-codes#resource-missing"),
                Map.entry(
                    "parameter_missing", "https://docs.stripe.com/error-codes#parameter-missing"),
                Map.entry(
                    "parameter_unknown", "https://docs.stripe.com/error-codes#parameter-unknown"),
                Map.entry(
                    "payment_intent_unexpected_state",
                    "https://docs.stripe.com/error-codes#payment-intent-unexpected-state"),
                Map.entry(
                    "setup_intent_unexpected_state",
                    "https://docs.stripe.com/error-codes#setup-intent-unexpected-state"),
                Map.entry(
                    "charge_expired_for_capture",
                    "https://docs.stripe.com/error-codes#charge-expired-for-capture"),
                Map.entry(
                    "charge_already_refunded",
                    "https://docs.stripe.com/error-codes#charge-already-refunded"),
                Map.entry(
                    "invalid_charge_amount",
                    "https://docs.stripe.com/error-codes#invalid-charge-amount"),
                Map.entry(
                    "amount_too_large", "https://docs.stripe.com/error-codes#amount-too-large"),
                Map.entry(
                    "idempotency_key_in_use",
                    "https://docs.stripe.com/error-codes#idempotency-key-in-use")));
  }

  private static List<StripeError> everyRefusalThisBuildCanAnswerWith() {
    List<StripeError> refusals = new ArrayList<>();
    for (Method factory : StripeErrors.class.getDeclaredMethods()) {
      if (!Modifier.isStatic(factory.getModifiers())
          || Modifier.isPrivate(factory.getModifiers())
          || !factory.getReturnType().equals(StripeError.class)) {
        continue;
      }
      refusals.add(refusalFrom(factory));
    }
    assertThat(refusals).hasSize(26);
    return refusals;
  }

  private static StripeError refusalFrom(Method factory) {
    Object[] arguments = new Object[factory.getParameterCount()];
    for (int at = 0; at < arguments.length; at++) {
      arguments[at] =
          factory.getParameterTypes()[at].equals(StripeDecline.class)
              ? StripeDecline.GENERIC_DECLINE
              : "amount";
    }
    try {
      return (StripeError) factory.invoke(null, arguments);
    } catch (ReflectiveOperationException unreachable) {
      throw new IllegalStateException(factory.getName(), unreachable);
    }
  }

  private static String linkIn(StripeError refusal) {
    List<String> linked = refusal.body().flatten().get("error.doc_url");
    assertThat(linked).hasSize(1);
    return linked.get(0);
  }

  private static String bodyOf(StripeError refusal) {
    return StripeJsonWriter.toText(refusal.body());
  }
}
