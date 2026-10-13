package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.TheCausesOfAFailure;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@Import(AFailureNothingPlannedForOnGrowsWireIntegrationTest.AnAccountReadThatBreaks.class)
class AFailureNothingPlannedForOnGrowsWireIntegrationTest extends PostgresBackedTest {

  static final String THE_USER_ID_THAT_BREAKS_THE_READ = "brkgrowaccountread";

  static final String WHAT_BROKE_INSIDE =
      "the account column rejected the value it was handed [select * from grow_account where"
          + " user_id = 'brk']";

  private static final String A_ROUTE_GROW_PUBLISHES = "/api/light/server/1.0/createPaymentProcess";

  private static final String THE_RUN = "ownfix-grow";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void anApplicationWrittenAgainstGrow() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void a_failure_nothing_planned_for_carries_no_body_because_grow_publishes_no_5xx_at_all() {
    ResponseEntity<String> answered = whatItAnswers(THE_RUN);

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(answered.getBody())
        .as(
            "every documented answer of grow is HTTP 200 with the outcome in the body and no 5xx"
                + " appears anywhere, so this simulator's english prose in text/plain is a"
                + " content type grow's platform never sends: a generated client dispatching on"
                + " content type behaves differently here than against the real thing")
        .isNull();
    assertThat(answered.getHeaders().getContentType()).isNull();
    assertThat(answered.getHeaders().getContentLength()).isZero();
  }

  @Test
  void the_reasons_the_wire_withholds_are_on_grows_trail_under_the_method_the_client_asked_with() {
    whatItAnswers(THE_RUN);

    ResponseEntity<String> trail =
        http.get()
            .uri("/__simulator/grow/audit?testRunId=" + THE_RUN)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);

    assertThat(trail.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(trail.getBody())
        .as(
            "the wire deliberately says nothing at all here, so the trail is the whole diagnosis"
                + " and must already hold the row when the client reads it back")
        .contains(ObjectOrigin.PROVIDER_API.name())
        .contains(TheCausesOfAFailure.THE_CAUSES)
        .contains("the account column rejected the value it was handed")
        .contains(A_ROUTE_GROW_PUBLISHES)
        .contains("\"method\":\"POST\"")
        .contains("\"status\":500");
    assertThat(trail.getBody())
        .as("a statement a driver quoted back is left behind before the row is stored")
        .doesNotContain("select * from grow_account");
  }

  private ResponseEntity<String> whatItAnswers(String run) {
    return http.post()
        .uri(A_ROUTE_GROW_PUBLISHES + "?testRunId=" + run)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(
            "userId="
                + THE_USER_ID_THAT_BREAKS_THE_READ
                + "&pageCode=anything&chargeType=1&description=x")
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class AnAccountReadThatBreaks {

    @Bean
    @Primary
    GrowAccounts aGrowAccountReadThatBreaksOnOneUserId(
        @Qualifier("growAccounts") GrowAccounts reading) {
      return new BreakingOnOneUserId(reading);
    }
  }

  private record BreakingOnOneUserId(GrowAccounts reading) implements GrowAccounts {

    @Override
    public void merge(TestRunId testRunId, List<GrowAccount> accounts) {
      reading.merge(testRunId, accounts);
    }

    @Override
    public void remove(TestRunId testRunId, String accountId) {
      reading.remove(testRunId, accountId);
    }

    @Override
    public List<GrowAccount> ofTheRunHoldingEither(
        TestRunId testRunId, String userId, String pageCode) {
      return reading.ofTheRunHoldingEither(testRunId, userId, pageCode);
    }

    @Override
    public List<GrowAccount> byUserIdAndPageCode(String userId, String pageCode) {
      if (THE_USER_ID_THAT_BREAKS_THE_READ.equals(userId)) {
        throw new IllegalStateException(WHAT_BROKE_INSIDE);
      }
      return reading.byUserIdAndPageCode(userId, pageCode);
    }

    @Override
    public List<GrowAccount> byUserId(String userId) {
      return reading.byUserId(userId);
    }

    @Override
    public List<GrowAccount> byPageCode(String pageCode) {
      return reading.byPageCode(pageCode);
    }

    @Override
    public List<GrowAccount> byEitherCredential(
        @Nullable String userId, @Nullable String pageCode) {
      return reading.byEitherCredential(userId, pageCode);
    }

    @Override
    public Optional<GrowAccount> find(TestRunId testRunId, String accountId) {
      return reading.find(testRunId, accountId);
    }

    @Override
    public List<GrowAccount> allById(TestRunId testRunId, List<String> accountIds) {
      return reading.allById(testRunId, accountIds);
    }
  }
}
