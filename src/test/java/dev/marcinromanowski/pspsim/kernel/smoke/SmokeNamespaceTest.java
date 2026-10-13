package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmokeNamespaceTest {

  private final CannedTransport wire =
      new CannedTransport()
          .answering("POST", "/", 200, "{}")
          .answering("GET", "/", 200, "{}")
          .answering("PUT", "/", 200, "{}");

  private final SmokeNamespace namespace = new SmokeNamespace(wire);

  @Test
  void the_run_the_checks_write_to_is_named_smoke_and_is_not_the_default_run() {
    assertThat(SmokeNamespace.RUN.value()).isEqualTo("smoke");
  }

  @Test
  void every_request_carries_the_test_run_header_that_routes_it_to_the_check_run() {
    namespace.send(
        CheckRequest.form("POST", "/api/light/server/1.0/createPaymentProcess", Map.of()));

    assertThat(sent().headers()).containsEntry(TestRunHeader.NAME, "smoke");
  }

  @Test
  void a_control_route_also_carries_the_run_in_its_query_because_the_control_api_reads_it_there() {
    namespace.send(CheckRequest.get("/__simulator/grow/audit"));

    assertThat(sent().path()).isEqualTo("/__simulator/grow/audit?testRunId=smoke");
  }

  @Test
  void a_control_route_that_already_carries_a_query_gains_the_run_beside_it() {
    namespace.send(CheckRequest.get("/__simulator/grow/audit?origin=PROVIDER_API"));

    assertThat(sent().path())
        .isEqualTo("/__simulator/grow/audit?origin=PROVIDER_API&testRunId=smoke");
  }

  @Test
  void a_provider_route_keeps_the_path_a_client_would_dial() {
    namespace.send(CheckRequest.form("POST", "/v1/payment_intents", Map.of("amount", "1099")));

    assertThat(sent().path()).isEqualTo("/v1/payment_intents");
  }

  @Test
  void a_json_control_body_gains_the_run_as_its_first_member() {
    namespace.send(
        CheckRequest.json("POST", "/__simulator/grow/reset", "{\"scope\":\"TEST_RUN\"}"));

    assertThat(sent().body()).isEqualTo("{\"testRunId\":\"smoke\",\"scope\":\"TEST_RUN\"}");
  }

  @Test
  void an_empty_json_body_is_stamped_into_an_object_naming_only_the_run() {
    namespace.send(CheckRequest.json("POST", "/__simulator/grow/reset", "{}"));

    assertThat(sent().body()).isEqualTo("{\"testRunId\":\"smoke\"}");
  }

  @Test
  void a_form_body_is_not_stamped_because_the_stamp_this_namespace_writes_is_a_json_member() {
    namespace.send(CheckRequest.form("POST", "/v1/payment_intents", Map.of("amount", "1099")));

    assertThat(sent().body()).isEqualTo("amount=1099");
  }

  @Test
  void
      a_json_provider_body_is_sent_as_the_check_wrote_it_because_no_vendor_publishes_that_member() {
    namespace.send(
        CheckRequest.json(
            "POST",
            "/v2/payments",
            "{\"amountOfMoney\":{\"amount\":1099,\"currencyCode\":\"EUR\"}}"));

    assertThat(sent().body())
        .as(
            "a json provider body goes to the wire as the check wrote it, so neither the field"
                + " this namespace adds to a form body, "
                + SmokeNamespace.FIELD
                + ", nor the run it scopes a check by reaches a provider inside the body, and"
                + " pinning the body whole is what says so rather than naming the two values"
                + " separately, which a pinned body can no longer fail over")
        .isEqualTo("{\"amountOfMoney\":{\"amount\":1099,\"currencyCode\":\"EUR\"}}");
    assertThat(sent().headers()).containsEntry(TestRunHeader.NAME, SmokeNamespace.RUN.value());
  }

  @Test
  void a_json_body_that_is_not_an_object_is_sent_as_the_check_wrote_it() {
    namespace.send(CheckRequest.json("POST", "/__simulator/grow/scenarios", "[]"));

    assertThat(sent().body()).isEqualTo("[]");
  }

  @Test
  void a_control_route_with_no_body_gains_none_because_the_stamp_needs_an_object_to_go_in() {
    namespace.send(CheckRequest.get("/__simulator/grow/audit"));

    assertThat(sent().body()).isNull();
    assertThat(sent().path()).isEqualTo("/__simulator/grow/audit?testRunId=smoke");
  }

  @Test
  void a_request_with_no_body_at_all_is_sent_with_none() {
    namespace.send(CheckRequest.get("/v1/payment_intents/pi_sim_1"));

    assertThat(sent().body()).isNull();
  }

  @Test
  void a_check_that_names_a_test_run_in_its_body_is_refused_before_anything_reaches_the_wire() {
    CheckRequest namingATestRunInItsBody =
        CheckRequest.json("POST", "/__simulator/grow/reset", "{\"testRunId\":\"default\"}");

    assertThatThrownBy(() -> namespace.send(namingATestRunInItsBody))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(SmokeNamespace.A_CHECK_NAMES_NO_RUN_OF_ITS_OWN);
    assertThat(wire.sent()).isEmpty();
  }

  @Test
  void a_check_that_names_a_test_run_in_its_path_is_refused_before_anything_reaches_the_wire() {
    CheckRequest namingATestRunInItsPath =
        CheckRequest.get("/__simulator/grow/audit?testRunId=default");

    assertThatThrownBy(() -> namespace.send(namingATestRunInItsPath))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(SmokeNamespace.A_CHECK_NAMES_NO_RUN_OF_ITS_OWN);
    assertThat(wire.sent()).isEmpty();
  }

  @Test
  void the_answer_the_wire_gave_is_the_one_the_check_reads() {
    CheckResponse answer = namespace.send(CheckRequest.get("/v1/payment_intents/pi_sim_1"));

    assertThat(answer.status()).isEqualTo(200);
    assertThat(answer.body()).isEqualTo("{}");
  }

  @Test
  void the_headers_a_check_set_itself_are_kept_beside_the_stamped_one() {
    namespace.send(
        CheckRequest.form("POST", "/v1/payment_intents", Map.of())
            .carrying("Authorization", "Bearer sk_test_x"));

    assertThat(sent().headers())
        .containsEntry("Authorization", "Bearer sk_test_x")
        .containsEntry(TestRunHeader.NAME, "smoke");
  }

  private CheckRequest sent() {
    List<CheckRequest> sent = wire.sent();
    return sent.get(sent.size() - 1);
  }
}
