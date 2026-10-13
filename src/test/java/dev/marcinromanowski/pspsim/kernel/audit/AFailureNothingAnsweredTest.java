package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.marcinromanowski.pspsim.RecordedActions;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AFailureNothingAnsweredTest {

  private static final ProviderId GATEWAY = ProviderId.of("gateway");

  @Test
  void a_status_something_decided_is_not_a_failure_nothing_answered() {
    for (int decided : new int[] {200, 400, 404, 405, 422, 499}) {
      assertThatCode(() -> failureAnsweredWith(decided))
          .as(String.valueOf(decided))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("server error");
    }
  }

  @Test
  void the_lowest_server_error_and_every_status_above_it_is_one() {
    for (int nothingChose : new int[] {500, 502, 503, 504, 599}) {
      assertThat(failureAnsweredWith(nothingChose).httpStatus()).isEqualTo(nothingChose);
    }
  }

  @Test
  void the_causes_a_row_carries_cannot_be_changed_after_it_was_built() {
    List<String> stillBeingRead = new ArrayList<>(List.of("IllegalStateException: the first one"));
    AFailureNothingAnswered failure =
        new AFailureNothingAnswered(
            GATEWAY, TestRunId.UNKNOWN, ObjectOrigin.CONTROL_API, "PUT", "/x", 500, stillBeingRead);

    stillBeingRead.add("something read later");

    assertThat(failure.causes()).containsExactly("IllegalStateException: the first one");
  }

  @Test
  void the_trail_files_it_as_one_inbound_row_of_the_run_and_provider_it_names() {
    RecordedActions recorded = new RecordedActions();

    recorded
        .asTrail()
        .record(
            new AFailureNothingAnswered(
                GATEWAY,
                TestRunId.UNKNOWN,
                ObjectOrigin.CONTROL_API,
                "PUT",
                "/__simulator/gateway/config",
                500,
                List.of("IllegalStateException: a column rejected the value")));

    assertThat(recorded.written()).hasSize(1);
    AuditRecord row = recorded.written().get(0);
    assertThat(row.auditId()).startsWith("aud_");
    assertThat(row.provider()).isEqualTo(GATEWAY);
    assertThat(row.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(row.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(row.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(row.method()).isEqualTo("PUT");
    assertThat(row.theCalledPath()).isEqualTo("/__simulator/gateway/config");
    assertThat(row.httpStatus()).isEqualTo(500);
    assertThat(row.attempt()).isEqualTo(1);
    assertThat(row.createdAt()).isEqualTo(Instant.EPOCH);
    assertThat(row.outcome()).isNull();
    assertThat(row.refusal())
        .as(
            "a refusal is a decision this simulator published, and filing a failure nothing"
                + " decided as one would have a tester read it as a rule of the provider")
        .isNull();
    assertThat(row.sanitizedHeaders()).isEmpty();
    assertThat(row.sanitizedBody())
        .containsExactly(
            Assertions.entry(
                TheCausesOfAFailure.THE_CAUSES,
                List.of("IllegalStateException: a column rejected the value")));
  }

  @Test
  void a_failure_that_read_no_cause_at_all_is_filed_without_one_rather_than_with_an_empty_one() {
    RecordedActions recorded = new RecordedActions();

    recorded
        .asTrail()
        .record(
            new AFailureNothingAnswered(
                GATEWAY,
                TestRunId.UNKNOWN,
                ObjectOrigin.PROVIDER_API,
                "GET",
                "/x",
                500,
                List.of()));

    assertThat(recorded.written().get(0).sanitizedBody()).isEmpty();
  }

  private static AFailureNothingAnswered failureAnsweredWith(int httpStatus) {
    return new AFailureNothingAnswered(
        GATEWAY,
        TestRunId.UNKNOWN,
        ObjectOrigin.CONTROL_API,
        "PUT",
        "/__simulator/gateway/config",
        httpStatus,
        List.of());
  }
}
