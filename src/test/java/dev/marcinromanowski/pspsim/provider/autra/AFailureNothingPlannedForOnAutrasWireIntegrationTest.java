package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.TheCausesOfAFailure;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@Import(AFailureNothingPlannedForOnAutrasWireIntegrationTest.ASessionReadThatBreaks.class)
class AFailureNothingPlannedForOnAutrasWireIntegrationTest extends PostgresBackedTest {

  static final String THE_BEARER_THAT_BREAKS_THE_READ = "brk_autra_session_read";

  static final String THE_CARD_SESSION_THAT_BREAKS = "cs_brkautrapageread";

  static final String WHAT_BROKE_INSIDE =
      "the session column rejected the value it was handed [select * from autra_session where"
          + " token = 'brk']";

  private static final String WHAT_AUTRA_PUBLISHES_FOR_A_FAILURE_INSIDE_IT =
      "{\"errors\":[{\"code\":\"INTERNAL_ERROR\",\"msg\":\"Falha interna.\"}]}";

  private static final String THE_RUN = "ownfix-autra";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void anApplicationWrittenAgainstAutra() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void a_failure_nothing_planned_for_inside_the_operation_answers_the_envelope_autra_publishes() {
    ResponseEntity<String> answered =
        whatItAnswers("POST", AutraApiController.PAYMENTS + "?testRunId=" + THE_RUN);

    assertThat(answered.getStatusCode())
        .as(
            "the controller caught every RuntimeException and answered text/plain itself, so the"
                + " error dispatch was never reached and every database, state-machine and"
                + " mapping failure answered in a media type autra never sends")
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(answered.getHeaders().getContentType())
        .isNotNull()
        .matches(type -> type.isCompatibleWith(MediaType.APPLICATION_JSON));
    assertThat(answered.getBody()).isEqualTo(WHAT_AUTRA_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
  }

  @Test
  void one_namespace_answers_one_fault_one_way_whatever_layer_the_failure_arose_in() {
    ResponseEntity<String> inTheOperation =
        whatItAnswers("POST", AutraApiController.PAYMENTS + "?testRunId=" + THE_RUN);
    ResponseEntity<String> onAnotherAcquiringAddress =
        whatItAnswers("POST", AutraApiController.PIX_CHECKOUT + "?testRunId=" + THE_RUN);

    assertThat(onAnotherAcquiringAddress.getBody()).isEqualTo(inTheOperation.getBody());
    assertThat(onAnotherAcquiringAddress.getHeaders().getContentType())
        .isEqualTo(inTheOperation.getHeaders().getContentType());
    assertThat(inTheOperation.getBody())
        .as("a real acquirer hands a client none of this simulator's insides")
        .doesNotContain("Exception")
        .doesNotContain("dev.marcinromanowski.pspsim")
        .doesNotContain("org.springframework")
        .doesNotContain("select ")
        .doesNotContain("src/main")
        .doesNotContain("\"timestamp\"")
        .doesNotContain("Whitelabel");
  }

  @Test
  void a_failure_on_a_namespace_autra_publishes_no_5xx_for_carries_no_body_at_all() {
    ResponseEntity<String> answered =
        whatItAnswers(
            "GET",
            AutraCardPage.SESSIONS
                + THE_CARD_SESSION_THAT_BREAKS
                + "?"
                + AutraCardPage.TOKEN
                + "=anything&testRunId="
                + THE_RUN);

    assertThat(answered.getStatusCode().is5xxServerError()).isTrue();
    assertThat(answered.getBody())
        .as(
            "the hosted card page answers html and declares no 5xx of any kind, so this simulator"
                + " sends the one shape no client can read as autra's: none at all")
        .isNull();
    assertThat(answered.getHeaders().getContentType()).isNull();
  }

  @Test
  void the_trail_names_the_method_the_client_asked_with_and_not_the_error_dispatch_s_own() {
    String run = THE_RUN + "-post";

    whatItAnswers("POST", AutraApiController.PAYMENTS + "?testRunId=" + run);

    ResponseEntity<String> trail =
        whatItAnswers("GET", "/__simulator/autra/audit?testRunId=" + run);

    assertThat(trail.getBody())
        .as(
            "an error dispatch carries its own method, and GET %s is a published operation of"
                + " autra in its own right, so a row filed under GET blames one operation for"
                + " another's failure",
            AutraApiController.PAYMENTS)
        .contains("\"method\":\"POST\"")
        .doesNotContain("\"method\":\"GET\"");
  }

  @Test
  void the_reasons_the_wire_withholds_are_on_the_trail_before_the_client_could_read_it_back() {
    whatItAnswers("POST", AutraApiController.PAYMENTS + "?testRunId=" + THE_RUN);

    ResponseEntity<String> trail =
        whatItAnswers("GET", "/__simulator/autra/audit?testRunId=" + THE_RUN);

    assertThat(trail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(trail.getBody())
        .as(
            "the wire says nothing of this deliberately, so a tester or a CI suite meeting the"
                + " 500 and reading the trail on the same connection must find the row already"
                + " there")
        .contains(ObjectOrigin.PROVIDER_API.name())
        .contains(TheCausesOfAFailure.THE_CAUSES)
        .contains("the session column rejected the value it was handed")
        .contains(AutraApiController.PAYMENTS)
        .contains("\"status\":500");
    assertThat(trail.getBody())
        .as(
            "the trail travels in run bundles and bug-report bundles, so the statement a driver"
                + " quoted back and the package a class was named by are left behind on write")
        .doesNotContain("select * from autra_session")
        .doesNotContain("org.springframework.jdbc");
  }

  private ResponseEntity<String> whatItAnswers(String method, String route) {
    return http.method(HttpMethod.valueOf(method))
        .uri(route)
        .header("Authorization", "Bearer " + THE_BEARER_THAT_BREAKS_THE_READ)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ASessionReadThatBreaks {

    @Bean
    @Primary
    AutraSessions anAutraSessionReadThatBreaksOnOneBearer(
        @Qualifier("autraSessions") AutraSessions reading) {
      return new BreakingOnOneBearer(reading);
    }

    @Bean
    @Primary
    AutraCardSessions anAutraCardPageReadThatBreaksOnOneSession(
        @Qualifier("autraCardSessions") AutraCardSessions reading) {
      return new BreakingOnOneCardSession(reading);
    }
  }

  private record BreakingOnOneCardSession(AutraCardSessions reading) implements AutraCardSessions {

    @Override
    public boolean insertIfAbsent(AutraCardSession session) {
      return reading.insertIfAbsent(session);
    }

    @Override
    public Optional<AutraCardSession> findById(TestRunId testRunId, String cardSessionId) {
      return reading.findById(testRunId, cardSessionId);
    }

    @Override
    public List<AutraCardSession> allById(TestRunId testRunId, List<String> cardSessionIds) {
      return reading.allById(testRunId, cardSessionIds);
    }

    @Override
    public Optional<AutraCardSession> findByPage(String cardSessionId, String pageToken) {
      if (THE_CARD_SESSION_THAT_BREAKS.equals(cardSessionId)) {
        throw new IllegalStateException(WHAT_BROKE_INSIDE);
      }
      return reading.findByPage(cardSessionId, pageToken);
    }

    @Override
    public List<AutraStoredCard> tokenizedIn(TestRunId testRunId) {
      return reading.tokenizedIn(testRunId);
    }

    @Override
    public boolean moveFromPendingTo(AutraCardSession moved) {
      return reading.moveFromPendingTo(moved);
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String cardSessionId) {
      return reading.placeOf(testRunId, cardSessionId);
    }

    @Override
    public List<ArrivalOf<AutraCardSession>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      return reading.newestFirst(testRunId, after, limit);
    }

    @Override
    public int clear(TestRunId testRunId) {
      return reading.clear(testRunId);
    }

    @Override
    public int clearAll() {
      return reading.clearAll();
    }
  }

  private record BreakingOnOneBearer(AutraSessions reading) implements AutraSessions {

    @Override
    public boolean mint(AutraAccessToken token) {
      return reading.mint(token);
    }

    @Override
    public Optional<AutraAccessToken> accessToken(String value) {
      if (THE_BEARER_THAT_BREAKS_THE_READ.equals(value)) {
        throw new IllegalStateException(
            "org.springframework.jdbc.BadSqlGrammarException: " + WHAT_BROKE_INSIDE);
      }
      return reading.accessToken(value);
    }

    @Override
    public boolean open(AutraSession session) {
      return reading.open(session);
    }

    @Override
    public Optional<AutraSession> session(TestRunId testRunId, String transactionalToken) {
      return reading.session(testRunId, transactionalToken);
    }

    @Override
    public int clear(TestRunId testRunId) {
      return reading.clear(testRunId);
    }

    @Override
    public int clearAll() {
      return reading.clearAll();
    }
  }
}
