package dev.marcinromanowski.pspsim.kernel.http;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

@Import(AnAddressNobodyClaimsIntegrationTest.ARouteLookupThatBreaks.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnAddressNobodyClaimsIntegrationTest extends PostgresBackedTest {

  static final String THE_ADDRESS_WHOSE_ROUTE_LOOKUP_BREAKS = "/nobody-claims-this";

  static final String WHAT_BROKE_INSIDE = "the route alias table rejected the read";

  private static final List<String> ADDRESSES_NOBODY_CLAIMS =
      List.of("/zzz", "/farm", "/tokens", "/v2x", "/favicon.ico", "/zzz/deeper/still");

  private static final String A_METHOD_NO_ADDRESS_OF_THIS_BUILD_ANSWERS = "TRACE";

  private static final String WHAT_A_BROWSER_ASKS_FOR =
      "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

  @LocalServerPort private int port;

  @Autowired private JdbcClient rows;

  private RestClient http;

  private WhatTheLogRemembers serverLog;

  @BeforeEach
  void anApplicationPointedAtAnAddressNobodyClaims() {
    http = RestClient.create("http://localhost:" + port);
    serverLog = WhatTheLogRemembers.attachedTo(Logger.ROOT_LOGGER_NAME);
  }

  @AfterEach
  void stopReadingTheServerLog() {
    serverLog.stopReading();
  }

  @Test
  void an_address_nobody_claims_answers_in_nobodys_words_whatever_the_client_asked_for() {
    for (String address : ADDRESSES_NOBODY_CLAIMS) {
      for (ResponseEntity<String> answered :
          List.of(whatItAnswers(address), whatABrowserIsAnswered(address))) {
        assertThat(answered.getStatusCode()).as(address).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(answered.getBody())
            .as(
                "%s is claimed by no provider and owned by no part of this simulator, so there is"
                    + " nobody whose words it could be answered in, and the framework's own"
                    + " envelope is a shape nothing in this build publishes",
                address)
            .isNull();
        assertThat(answered.getHeaders().getContentType())
            .as(
                "a body nothing sent is read as nothing, and a media type on an empty answer"
                    + " offers a client a contract to read it against",
                address)
            .isNull();
      }
    }
  }

  @Test
  void a_method_no_address_of_this_build_answers_is_answered_the_same_way_at_such_an_address() {
    ResponseEntity<String> answered =
        askedWith(A_METHOD_NO_ADDRESS_OF_THIS_BUILD_ANSWERS, ADDRESSES_NOBODY_CLAIMS.get(0));

    assertThat(answered.getStatusCode())
        .as(
            "a method fault at such an address is reachable, and the shape must not depend on"
                + " which of the three faults the client met")
        .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(answered.getBody()).isNull();
    assertThat(answered.getHeaders().getContentType()).isNull();
  }

  @Test
  void the_server_log_names_the_request_at_every_fault_answered_at_such_an_address() {
    whatItAnswers("/a-tester-typo-here");

    assertThat(theLineAbout("/a-tester-typo-here"))
        .as(
            "the wire says nothing and no row can be filed, so a line of the server log is all a"
                + " tester has: without it a mistyped address answers nothing and leaves nothing,"
                + " which the framework's envelope at least did not do - it echoed the path")
        .anySatisfy(said -> assertThat(said).contains("GET").contains("404"));
  }

  @Test
  void a_method_the_connector_refused_is_on_the_log_by_its_address_and_status() {
    askedWith(A_METHOD_NO_ADDRESS_OF_THIS_BUILD_ANSWERS, "/a-refused-method-here");

    assertThat(theLineAbout("/a-refused-method-here"))
        .as(
            "the connector answers this method before any filter of this application runs, so"
                + " nothing remembered what the client asked with and the dispatch reports its"
                + " own method; the address and the status are what identify the request, and"
                + " claiming a method here would be the misattribution the trail already refuses")
        .anySatisfy(said -> assertThat(said).contains("405"));
  }

  @Test
  void the_front_door_a_tester_types_is_this_simulators_own_and_keeps_the_answer_it_had() {
    ResponseEntity<String> aMethodItDoesNotAnswer =
        http.post()
            .uri("/")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);

    assertThat(aMethodItDoesNotAnswer.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(aMethodItDoesNotAnswer.getBody())
        .as(
            "/ is the address a tester types first and the only address outside the three"
                + " reserved namespaces that this simulator answers itself, so it is nobody's"
                + " only if the mechanism forgets that this simulator serves it")
        .isNotNull();
  }

  @Test
  void a_failure_nothing_answered_at_an_address_nobody_claims_answers_the_same_way() {
    ResponseEntity<String> asAnApi = whatItAnswers(THE_ADDRESS_WHOSE_ROUTE_LOOKUP_BREAKS);
    ResponseEntity<String> asABrowser =
        whatABrowserIsAnswered(THE_ADDRESS_WHOSE_ROUTE_LOOKUP_BREAKS);

    for (ResponseEntity<String> answered : List.of(asAnApi, asABrowser)) {
      assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(answered.getBody())
          .as(
              "one address answers one shape: a 404 here carries no body, and a 500 that carried"
                  + " the framework's envelope would make the shape depend on which failure the"
                  + " client met")
          .isNull();
      assertThat(answered.getHeaders().getContentType()).isNull();
    }
  }

  @Test
  void no_row_of_the_trail_names_that_address() {
    whatItAnswers(THE_ADDRESS_WHOSE_ROUTE_LOOKUP_BREAKS);

    assertThat(
            rows.sql("select count(*) from simulator_audit_record where path = ?")
                .param(THE_ADDRESS_WHOSE_ROUTE_LOOKUP_BREAKS)
                .query(Integer.class)
                .single())
        .as(
            "the trail is keyed by provider and this address named none, and every control route"
                + " and every screen resolves the provider before it reads, so a row filed under"
                + " an id no module answers to is a row nothing can read back")
        .isZero();
  }

  @Test
  void the_server_log_names_that_request_too_and_carries_the_cause_the_wire_withholds() {
    whatItAnswers(THE_ADDRESS_WHOSE_ROUTE_LOOKUP_BREAKS);

    assertThat(theLineAbout(THE_ADDRESS_WHOSE_ROUTE_LOOKUP_BREAKS))
        .as(
            "the entry the container writes for a raised failure carries the cause and names"
                + " neither the method nor the address, so on its own it does not tell a tester"
                + " which request met it")
        .anyMatch(said -> said.contains("GET") && said.contains("500"));
    assertThat(serverLog.theCausesItReported())
        .as(
            "no row can be filed under a provider nobody named, so the server log is the only"
                + " place left for the reason, and a tester reads it with docker logs")
        .anyMatch(cause -> cause.contains(WHAT_BROKE_INSIDE));
  }

  private ResponseEntity<String> askedWith(String method, String address) {
    return http.method(HttpMethod.valueOf(method))
        .uri(address)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private List<String> theLineAbout(String address) {
    return serverLog.saidAt(Level.INFO).stream().filter(said -> said.contains(address)).toList();
  }

  private ResponseEntity<String> whatItAnswers(String address) {
    return http.get()
        .uri(address)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private ResponseEntity<String> whatABrowserIsAnswered(String address) {
    return http.get()
        .uri(address)
        .header("Accept", WHAT_A_BROWSER_ASKS_FOR)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ARouteLookupThatBreaks {

    @Bean
    @Primary
    RouteAliases aRouteLookupThatBreaksOnOneAddress(
        @Qualifier("routeAliases") RouteAliases reading) {
      return new BreakingOnOneAddress(reading);
    }
  }

  private record BreakingOnOneAddress(RouteAliases reading) implements RouteAliases {

    @Override
    public void merge(ProviderId provider, TestRunId testRunId, List<RouteAlias> aliases) {
      reading.merge(provider, testRunId, aliases);
    }

    @Override
    public List<RouteAlias> of(ProviderId provider, TestRunId testRunId) {
      return reading.of(provider, testRunId);
    }

    @Override
    public boolean release(ProviderId provider, TestRunId testRunId, RouteAliasKey key) {
      return reading.release(provider, testRunId, key);
    }

    @Override
    public Optional<RouteAlias> boundTo(RouteAliasKey key) {
      if (THE_ADDRESS_WHOSE_ROUTE_LOOKUP_BREAKS.equals(key.path())) {
        throw new IllegalStateException(WHAT_BROKE_INSIDE);
      }
      return reading.boundTo(key);
    }
  }
}
