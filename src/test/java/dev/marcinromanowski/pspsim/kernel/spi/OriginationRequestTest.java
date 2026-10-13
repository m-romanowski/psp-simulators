package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class OriginationRequestTest {

  private static final TestRunId RUN = TestRunId.of("origination-request");

  @Test
  void a_payment_that_names_no_account_names_nothing_the_provider_could_sign_the_call_with() {
    assertThatThrownBy(() -> asked("   ", "aHold", ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("this request names no account");
  }

  @Test
  void a_payment_that_names_no_way_of_starting_is_refused() {
    assertThatThrownBy(() -> asked("merchant-1", "   ", ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("this request names none");
  }

  @Test
  void a_request_that_arrived_on_the_providers_own_route_is_never_asked_for_through_this_port() {
    assertThatThrownBy(() -> asked("merchant-1", "aHold", ObjectOrigin.PROVIDER_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("checks the credential and the signature that route publishes");
  }

  @Test
  void the_delivery_queue_moves_callbacks_a_payment_caused_and_never_starts_one() {
    assertThatThrownBy(() -> asked("merchant-1", "aHold", ObjectOrigin.SCHEDULED_JOB))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no scheduled job originates a payment through this port");
  }

  @Test
  void a_harness_asking_through_the_control_api_starts_a_payment_the_same_way_a_tester_does() {
    assertThat(asked("merchant-1", "aHold", ObjectOrigin.CONTROL_API).askedBy())
        .isEqualTo(ObjectOrigin.CONTROL_API);
  }

  @Test
  void a_value_a_tester_typed_with_spaces_around_it_reaches_the_provider_without_them() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("sum", "  10.00  ");

    assertThat(asked("  merchant-1  ", "  aHold  ", typed).required("sum")).isEqualTo("10.00");
  }

  @Test
  void a_field_a_tester_left_empty_is_not_sent_at_all_rather_than_sent_empty() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("sum", "10.00");
    typed.put("description", "   ");

    OriginationRequest request = asked("merchant-1", "aHold", typed);

    assertThat(request.values()).containsOnlyKeys("sum");
    assertThat(request.value("description")).isEmpty();
  }

  @Test
  void reading_a_field_this_payment_never_carried_fails_rather_than_inventing_a_value() {
    OriginationRequest request = asked("merchant-1", "aHold", ObjectOrigin.UI);

    assertThatThrownBy(() -> request.required("sum"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("before the core had checked");
  }

  @Test
  void the_values_a_payment_carries_keep_the_order_they_came_in_and_refuse_a_write_afterwards() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("sum", "10.00");
    typed.put("description", "one seat");
    typed.put("successUrl", "https://merchant.example/paid");
    typed.put("cancelUrl", "https://merchant.example/gaveup");
    typed.put("fullName", "Dana Levi");
    typed.put("phone", "0501234567");
    typed.put("email", "dana@example.com");
    typed.put("notifyUrl", "https://merchant.example/hook");

    OriginationRequest request = asked("merchant-1", "aHold", typed);

    assertThat(request.values())
        .as(
            "a provider writes these onto its own wire by iterating them, so the map a payment"
                + " carries says in its own type that it has an order, which no unordered copy"
                + " of it could satisfy in any JVM")
        .isInstanceOf(SequencedMap.class);
    assertThat(request.values().keySet())
        .as(
            "a payment holds its keys in one of the many orders %d of them could be held in, and"
                + " a list this wide is one no unordered copy lands on by coincidence",
            typed.size())
        .containsExactlyElementsOf(typed.keySet());
    assertThatCode(() -> request.values().put("sum", "20.00"))
        .as(
            "an order a caller may write over is an order a caller may break, so the map handed"
                + " out is wrapped and stays wrapped")
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static OriginationRequest asked(
      String accountId, String startedAs, ObjectOrigin askedBy) {
    return OriginationRequest.of(RUN, accountId, startedAs, Map.of(), askedBy);
  }

  private static OriginationRequest asked(
      String accountId, String startedAs, Map<String, String> values) {
    return OriginationRequest.of(RUN, accountId, startedAs, values, ObjectOrigin.UI);
  }
}
