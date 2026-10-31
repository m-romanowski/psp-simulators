package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StripeVersionChangesTest {

  private static final StripeVersionChange AT_A_DAHLIA_MONTHLY = aChangeAt("2026-07-29.dahlia");

  private static final StripeVersionChange AT_ENDIVES_OPENING = aChangeAt("2026-09-30.endive");

  private static final StripeVersionChange AT_THE_NEWEST_RELEASE = aChangeAt("2026-10-28.endive");

  private static final StripeVersionChange AT_BASILS_OPENING = aChangeAt("2025-03-31.basil");

  private static final List<StripeVersionChange> DECLARED =
      List.of(AT_BASILS_OPENING, AT_A_DAHLIA_MONTHLY, AT_ENDIVES_OPENING, AT_THE_NEWEST_RELEASE);

  @Test
  void the_walk_from_the_newest_release_collects_every_change_newer_than_the_request() {
    assertThat(walkingBackTo("2026-04-22.dahlia"))
        .containsExactly(AT_THE_NEWEST_RELEASE, AT_ENDIVES_OPENING, AT_A_DAHLIA_MONTHLY);
  }

  @Test
  void a_change_does_not_apply_to_a_request_at_the_release_that_shipped_it() {
    assertThat(walkingBackTo("2026-09-30.endive")).containsExactly(AT_THE_NEWEST_RELEASE);
    assertThat(walkingBackTo("2026-07-29.dahlia"))
        .containsExactly(AT_THE_NEWEST_RELEASE, AT_ENDIVES_OPENING);
  }

  @Test
  void a_request_between_two_changes_takes_only_the_newer_one() {
    assertThat(walkingBackTo("2026-08-26.dahlia"))
        .containsExactly(AT_THE_NEWEST_RELEASE, AT_ENDIVES_OPENING);
  }

  @Test
  void a_request_at_the_newest_release_takes_no_change() {
    assertThat(walkingBackTo("2026-10-28.endive")).isEmpty();
  }

  @Test
  void a_request_at_the_oldest_release_answered_takes_every_change_but_the_one_it_shipped() {
    assertThat(walkingBackTo("2025-03-31.basil"))
        .containsExactly(AT_THE_NEWEST_RELEASE, AT_ENDIVES_OPENING, AT_A_DAHLIA_MONTHLY);
  }

  @Test
  void a_request_at_a_version_older_than_every_release_answered_takes_every_change() {
    assertThat(walkingBackTo("2020-08-27"))
        .containsExactly(
            AT_THE_NEWEST_RELEASE, AT_ENDIVES_OPENING, AT_A_DAHLIA_MONTHLY, AT_BASILS_OPENING);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2020-03-02", "2020-08-27"})
  void a_request_at_a_mobile_sdks_pinned_version_takes_every_change_this_simulator_models(
      String pinned) {
    assertThat(StripeVersionChanges.walkingBackTo(new StripeApiVersion(pinned), DECLARED))
        .containsExactly(
            AT_THE_NEWEST_RELEASE, AT_ENDIVES_OPENING, AT_A_DAHLIA_MONTHLY, AT_BASILS_OPENING);
    assertThat(
            StripeVersionChanges.walkingBackTo(
                new StripeApiVersion(pinned), StripeVersionChanges.DECLARED))
        .containsExactly(
            StripeVersionChanges.PAYMENT_METHOD_TYPES_IS_NO_LONGER_WRITABLE,
            StripeVersionChanges.AN_EXPIRED_AUTHORIZATION_IS_CANCELLED_AS_EXPIRED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2020-03-02", "2020-08-27"})
  void an_ephemeral_key_minted_at_a_mobile_sdks_version_reaches_the_core_as_sent(String pinned) {
    StripeForm sent =
        StripeForm.of(
            Map.of("customer", List.of("cus_sim_1"), "payment_method_types[]", List.of("card")));

    StripeCoreRequest atTheCore =
        StripeVersionChanges.atTheCore(
            sent, new StripeApiVersion(pinned), "POST /v1/ephemeral_keys");

    assertThat(atTheCore.form()).isSameAs(sent);
    assertThat(atTheCore.writtenPaymentMethodTypes()).isNull();
  }

  @Test
  void the_changes_this_simulator_models_are_the_basil_expiry_reason_and_the_endive_removal() {
    assertThat(StripeVersionChanges.DECLARED)
        .containsExactly(
            StripeVersionChanges.AN_EXPIRED_AUTHORIZATION_IS_CANCELLED_AS_EXPIRED,
            StripeVersionChanges.PAYMENT_METHOD_TYPES_IS_NO_LONGER_WRITABLE);
    StripeVersionChange basil =
        StripeVersionChanges.AN_EXPIRED_AUTHORIZATION_IS_CANCELLED_AS_EXPIRED;
    assertThat(basil.version()).isEqualTo(new StripeApiVersion("2025-03-31.basil"));
    assertThat(basil.changelog())
        .isEqualTo(
            "https://docs.stripe.com/changelog/basil/2025-03-31/"
                + "remove-refund-from-partial-capture-and-payment-cancellation-flow");
    assertThat(basil.operations())
        .containsExactlyInAnyOrder(
            "POST /v1/payment_intents",
            "POST /v1/payment_intents/{id}/confirm",
            "POST /v1/payment_intents/{id}/capture",
            "POST /v1/payment_intents/{id}/cancel",
            "GET /v1/payment_intents/{id}",
            "GET /v1/charges/{id}",
            "POST /v1/refunds");
    StripeForm sent = StripeForm.of(Map.of("payment_method_types[]", List.of("card")));
    StripeCoreRequest untouched = basil.upgrade().apply(StripeCoreRequest.asSent(sent));
    assertThat(untouched.form()).isSameAs(sent);
    assertThat(untouched.writtenPaymentMethodTypes()).isNull();
    StripeVersionChange change = StripeVersionChanges.PAYMENT_METHOD_TYPES_IS_NO_LONGER_WRITABLE;
    assertThat(change.version()).isEqualTo(new StripeApiVersion("2026-09-30.endive"));
    assertThat(change.changelog())
        .isEqualTo(
            "https://docs.stripe.com/changelog/endive/2026-09-30/"
                + "removes-the-payment-method-types-parameter-from-payment-intents-and-setup-intents");
    assertThat(change.operations())
        .containsExactlyInAnyOrder(
            "POST /v1/payment_intents",
            "POST /v1/payment_intents/{id}/confirm",
            "POST /v1/setup_intents");
  }

  @Test
  void a_dahlia_request_hands_the_core_its_payment_method_types_as_the_list_it_wrote() {
    StripeForm sent = StripeForm.of(Map.of("payment_method_types[]", List.of("card", "paypal")));

    StripeCoreRequest atTheCore =
        StripeVersionChanges.atTheCore(
            sent, new StripeApiVersion("2026-08-26.dahlia"), "POST /v1/payment_intents");

    assertThat(atTheCore.form().carries("payment_method_types")).isFalse();
    assertThat(atTheCore.writtenPaymentMethodTypes())
        .isEqualTo(new StripeParameter.Appended(List.of("card", "paypal")));
  }

  @Test
  void an_endive_request_reaches_the_core_with_payment_method_types_still_on_its_form() {
    StripeForm sent = StripeForm.of(Map.of("payment_method_types[]", List.of("card")));

    StripeCoreRequest atTheCore =
        StripeVersionChanges.atTheCore(
            sent, new StripeApiVersion("2026-09-30.endive"), "POST /v1/setup_intents");

    assertThat(atTheCore.form().carries("payment_method_types")).isTrue();
    assertThat(atTheCore.writtenPaymentMethodTypes()).isNull();
  }

  @Test
  void a_dahlia_request_to_an_operation_the_change_does_not_touch_is_handed_on_as_sent() {
    StripeForm sent = StripeForm.of(Map.of("payment_method_types[]", List.of("card")));

    StripeCoreRequest atTheCore =
        StripeVersionChanges.atTheCore(
            sent, new StripeApiVersion("2026-08-26.dahlia"), "POST /v1/setup_intents/{id}/confirm");

    assertThat(atTheCore.form().carries("payment_method_types")).isTrue();
    assertThat(atTheCore.writtenPaymentMethodTypes()).isNull();
  }

  @Test
  void a_request_passes_the_older_change_before_the_newer_one() {
    List<StripeVersionChange> newestFirst =
        List.of(
            aChangeRecording("2026-10-28.endive", "newer"),
            aChangeRecording("2026-09-30.endive", "older"));
    List<StripeVersionChange> oldestFirst = newestFirst.reversed();

    for (List<StripeVersionChange> declared : List.of(newestFirst, oldestFirst)) {
      StripeCoreRequest atTheCore =
          StripeVersionChanges.atTheCore(
              StripeForm.of(Map.of()),
              new StripeApiVersion("2026-08-26.dahlia"),
              "POST /v1/payment_intents",
              declared);

      assertThat(atTheCore.writtenPaymentMethodTypes())
          .isEqualTo(new StripeParameter.Appended(List.of("older", "newer")));
    }
  }

  @Test
  void a_request_passes_only_the_changes_that_touch_its_operation_and_are_newer_than_it() {
    List<StripeVersionChange> declared =
        List.of(
            aChangeRecording("2026-10-28.endive", "newer"),
            aChangeRecording("2026-09-30.endive", "older"),
            new StripeVersionChange(
                new StripeApiVersion("2026-10-28.endive"),
                "https://docs.stripe.com/changelog/elsewhere",
                Set.of("POST /v1/setup_intents"),
                recording("elsewhere")));

    StripeCoreRequest atEndivesOpening =
        StripeVersionChanges.atTheCore(
            StripeForm.of(Map.of()),
            new StripeApiVersion("2026-09-30.endive"),
            "POST /v1/payment_intents",
            declared);

    assertThat(atEndivesOpening.writtenPaymentMethodTypes())
        .isEqualTo(new StripeParameter.Appended(List.of("newer")));
  }

  @Test
  void an_older_answer_names_an_expired_authorization_cancelled_automatically_wherever_it_sits() {
    StripeJson answered =
        StripeJson.object(
            "error",
            StripeJson.object(
                "type",
                StripeJson.text("invalid_request_error"),
                "payment_intent",
                anIntentCancelledAs("expired")),
            "data",
            StripeJson.array(List.of(anIntentCancelledAs("expired"), StripeJson.text("expired"))),
            "object",
            StripeJson.text("payment_intent"),
            "cancellation_reason",
            StripeJson.text("expired"));

    StripeJson atIos =
        StripeVersionChanges.asAnsweredAt(
            answered, new StripeApiVersion("2020-08-27"), "GET /v1/payment_intents/{id}");

    assertThat(StripeJsonWriter.toText(atIos))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"payment_intent\":"
                + "{\"id\":\"pi_sim_1\",\"object\":\"payment_intent\","
                + "\"cancellation_reason\":\"automatic\"}},\"data\":[{\"id\":\"pi_sim_1\","
                + "\"object\":\"payment_intent\",\"cancellation_reason\":\"automatic\"},"
                + "\"expired\"],\"object\":\"payment_intent\","
                + "\"cancellation_reason\":\"automatic\"}");
  }

  @Test
  void the_expiry_reason_is_kept_at_basil_and_on_an_operation_the_change_does_not_touch() {
    StripeJson expired = anIntentCancelledAs("expired");

    assertThat(
            StripeVersionChanges.asAnsweredAt(
                expired, new StripeApiVersion("2025-03-31.basil"), "GET /v1/payment_intents/{id}"))
        .isEqualTo(expired);
    assertThat(
            StripeVersionChanges.asAnsweredAt(
                expired, new StripeApiVersion("2020-08-27"), "GET /v1/customers/{id}"))
        .isEqualTo(expired);
  }

  @Test
  void only_an_intent_cancelled_as_expired_changes_its_reason_before_basil() {
    StripeJson requested = anIntentCancelledAs("requested_by_customer");
    StripeJson aRefundNamingExpired =
        StripeJson.object(
            "object", StripeJson.text("refund"), "cancellation_reason", StripeJson.text("expired"));
    StripeJson notCancelled =
        StripeJson.object(
            "object",
            StripeJson.text("payment_intent"),
            "cancellation_reason",
            StripeJson.nothing());

    for (StripeJson kept : List.of(requested, aRefundNamingExpired, notCancelled)) {
      assertThat(
              StripeVersionChanges.asAnsweredAt(
                  kept,
                  new StripeApiVersion("2020-03-02"),
                  "POST /v1/payment_intents/{id}/confirm"))
          .isEqualTo(kept);
    }
  }

  @Test
  void a_change_of_the_answer_passes_newest_first_back_to_the_requests_release() {
    List<StripeVersionChange> declared =
        List.of(
            StripeVersionChange.ofTheAnswer(
                new StripeApiVersion("2026-09-30.endive"),
                "https://docs.stripe.com/changelog/older",
                Set.of("GET /v1/payment_intents/{id}"),
                answer -> appending(answer, "older")),
            StripeVersionChange.ofTheAnswer(
                new StripeApiVersion("2026-10-28.endive"),
                "https://docs.stripe.com/changelog/newer",
                Set.of("GET /v1/payment_intents/{id}"),
                answer -> appending(answer, "newer")));

    StripeJson atDahlia =
        StripeVersionChanges.asAnsweredAt(
            StripeJson.array(List.of()),
            new StripeApiVersion("2026-08-26.dahlia"),
            "GET /v1/payment_intents/{id}",
            declared);

    assertThat(atDahlia)
        .isEqualTo(StripeJson.array(List.of(StripeJson.text("newer"), StripeJson.text("older"))));
  }

  private static StripeJson appending(StripeJson answer, String label) {
    List<StripeJson> passed = new ArrayList<>(((StripeJson.JsonArray) answer).elements());
    passed.add(StripeJson.text(label));
    return StripeJson.array(passed);
  }

  private static StripeJson anIntentCancelledAs(String reason) {
    return StripeJson.object(
        "id",
        StripeJson.text("pi_sim_1"),
        "object",
        StripeJson.text("payment_intent"),
        "cancellation_reason",
        StripeJson.text(reason));
  }

  @Test
  void a_change_is_declared_only_at_a_release_this_simulator_answers() {
    assertThatThrownBy(() -> aChangeAt("2024-09-30.acacia"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(StripeVersionChange.A_CHANGE_SHIPS_IN_A_RELEASE_THIS_SIMULATOR_ANSWERS);
  }

  @Test
  void a_change_touches_at_least_one_operation() {
    assertThatThrownBy(
            () ->
                new StripeVersionChange(
                    new StripeApiVersion("2026-09-30.endive"),
                    "https://docs.stripe.com/changelog",
                    Set.of(),
                    UnaryOperator.identity()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(StripeVersionChange.A_CHANGE_TOUCHES_AN_OPERATION);
  }

  private static List<StripeVersionChange> walkingBackTo(String version) {
    return StripeVersionChanges.walkingBackTo(new StripeApiVersion(version), DECLARED);
  }

  private static StripeVersionChange aChangeRecording(String version, String label) {
    return new StripeVersionChange(
        new StripeApiVersion(version),
        "https://docs.stripe.com/changelog/" + version,
        Set.of("POST /v1/payment_intents"),
        recording(label));
  }

  private static UnaryOperator<StripeCoreRequest> recording(String label) {
    return request -> {
      List<String> passed = new ArrayList<>();
      if (request.writtenPaymentMethodTypes() instanceof StripeParameter.Appended earlier) {
        passed.addAll(earlier.values());
      }
      passed.add(label);
      return new StripeCoreRequest(request.form(), new StripeParameter.Appended(passed));
    };
  }

  private static StripeVersionChange aChangeAt(String version) {
    return new StripeVersionChange(
        new StripeApiVersion(version),
        "https://docs.stripe.com/changelog/" + version,
        Set.of("POST /v1/payment_intents"),
        UnaryOperator.identity());
  }
}
