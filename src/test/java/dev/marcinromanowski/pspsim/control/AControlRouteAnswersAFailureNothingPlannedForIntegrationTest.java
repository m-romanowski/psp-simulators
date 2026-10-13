package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.TestRunActivity;
import dev.marcinromanowski.pspsim.kernel.audit.TheCausesOfAFailure;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@Import(AControlRouteAnswersAFailureNothingPlannedForIntegrationTest.AReadThatBreaks.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AControlRouteAnswersAFailureNothingPlannedForIntegrationTest extends PostgresBackedTest {

  static final String THE_AUDIT_ID_THAT_BREAKS_THE_READ = "aud_breakthisread";

  static final String WHAT_BROKE_INSIDE = "a column of the trail rejected the value it was handed";

  private static final String THE_RUN = "ownfail-control";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void anApplicationDrivingTheControlApi() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void a_failure_nothing_planned_for_on_a_control_route_answers_the_shape_the_control_api_uses() {
    ResponseEntity<String> answered = whatItAnswers(theReadThatBreaks());

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(answered.getHeaders().getContentType())
        .isNotNull()
        .matches(type -> type.isCompatibleWith(MediaType.APPLICATION_JSON));
    assertThat(answered.getBody())
        .as(
            "every refusal this control api publishes is one message field, and spring's own"
                + " envelope here is a second shape on the same route")
        .startsWith("{\"message\":")
        .doesNotContain("\"timestamp\"")
        .doesNotContain("\"error\":")
        .doesNotContain("\"path\":")
        .doesNotContain("\"trace\"")
        .doesNotContain("Internal Server Error")
        .doesNotContain("Whitelabel");
    assertThat(answered.getBody())
        .as(
            "a refusal or an error body must never carry the simulator's insides, and the"
                + " sentence the failure itself said is exactly such an inside")
        .doesNotContain("Exception")
        .doesNotContain("dev.marcinromanowski.pspsim")
        .doesNotContain("org.springframework")
        .doesNotContain(WHAT_BROKE_INSIDE)
        .doesNotContain("src/main")
        .doesNotContain("select ");
  }

  @Test
  void a_browser_landing_on_the_same_control_route_is_answered_the_same_bytes() {
    ResponseEntity<String> asAnApi = whatItAnswers(theReadThatBreaks());
    ResponseEntity<String> asABrowser =
        http.get()
            .uri(theReadThatBreaks())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);

    assertThat(asABrowser.getBody())
        .as(
            "a fidelity claim proved over one representation proves a fraction of itself, and the"
                + " screens' own page must not follow a tester onto the control api")
        .isEqualTo(asAnApi.getBody());
    assertThat(asABrowser.getBody()).doesNotContain("This request was not served");
  }

  @Test
  void the_reasons_the_wire_withholds_are_on_the_trail_the_control_api_publishes() {
    whatItAnswers(theReadThatBreaks());

    ResponseEntity<String> trail = whatItAnswers("/__simulator/grow/audit?testRunId=" + THE_RUN);

    assertThat(trail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(trail.getBody())
        .as(
            "a simulator whose own defect is invisible is worse than one that admits it: the"
                + " tester reads on the trail what the wire withheld")
        .contains(ObjectOrigin.CONTROL_API.name())
        .contains(TheCausesOfAFailure.THE_CAUSES)
        .contains(WHAT_BROKE_INSIDE)
        .contains("/__simulator/grow/audit")
        .contains("\"status\":500");
  }

  private static String theReadThatBreaks() {
    return "/__simulator/grow/audit?testRunId="
        + THE_RUN
        + "&auditId="
        + THE_AUDIT_ID_THAT_BREAKS_THE_READ;
  }

  private ResponseEntity<String> whatItAnswers(String route) {
    return http.get()
        .uri(route)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AReadThatBreaks {

    @Bean
    @Primary
    AuditReads anAuditReadThatBreaksOnOneId(@Qualifier("auditTrail") AuditReads reading) {
      return new BreakingOnOneAuditId(reading);
    }
  }

  private record BreakingOnOneAuditId(AuditReads reading) implements AuditReads {

    @Override
    public AuditPage pageOf(ProviderId provider, AuditQuery query) {
      if (THE_AUDIT_ID_THAT_BREAKS_THE_READ.equals(query.auditId())) {
        throw new IllegalStateException(WHAT_BROKE_INSIDE);
      }
      return reading.pageOf(provider, query);
    }

    @Override
    public List<AuditRecord> latestOf(
        ProviderId provider, TestRunId testRunId, @Nullable ObjectOrigin origin, int limit) {
      return reading.latestOf(provider, testRunId, origin, limit);
    }

    @Override
    public List<TestRunActivity> theRunsNewestTouchFirst() {
      return reading.theRunsNewestTouchFirst();
    }
  }
}
