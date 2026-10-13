package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class AScreenThisBuildDoesNotServeIntegrationTest extends PostgresBackedTest {

  private static final String ONE_NOBODY_CONTRIBUTED = "/ui/x/grow/nope";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void aClientOfThisSimulator() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void a_tester_who_asks_for_a_screen_nobody_contributed_reads_why_on_the_page() {
    ResponseEntity<String> answered = whatAskingFor(ONE_NOBODY_CONTRIBUTED, MediaType.TEXT_HTML);

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(answered.getBody())
        .as(
            "the eleven lines of guidance on this refusal were asserted in a unit test and"
                + " delivered to nobody: a tester reading a generic JSON error body cannot tell a"
                + " screen that was never contributed from one this build failed to serve")
        .contains("no page of this build is contributed at")
        .contains("src/main/resources/templates/extensions/grow/nope.html")
        .contains("The pages this build serves are")
        .contains("/ui/x/autra/pix");
    assertThat(answered.getBody())
        .as("a refusal a tester reads is a refusal inside the shell they were already in")
        .contains("Skip the navigation")
        .contains("Payments")
        .contains(ONE_NOBODY_CONTRIBUTED);
    assertThat(answered.getBody())
        .as(
            "this refusal is a thing this simulator decided, and it is written out in full above:"
                + " what this build was doing when it stopped is for an answer nothing decided")
        .doesNotContain("What this simulator was doing when it stopped");
  }

  @Test
  void a_client_that_asked_for_json_is_told_the_same_thing_in_json() {
    ResponseEntity<String> answered =
        whatAskingFor(ONE_NOBODY_CONTRIBUTED, MediaType.APPLICATION_JSON);

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(answered.getBody())
        .as("what a tester reads on a screen is what CI reads on the wire")
        .contains("no page of this build is contributed at")
        .contains("/ui/x/autra/pix");
  }

  @Test
  void the_screen_this_build_does_serve_is_drawn_with_the_navigation_it_was_handed() {
    ResponseEntity<String> answered = whatAskingFor("/ui/x/autra/pix", MediaType.TEXT_HTML);

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(answered.getBody())
        .contains("Hand out a Pix payload")
        .contains("aria-current=\"page\"")
        .contains("Payments");
  }

  private ResponseEntity<String> whatAskingFor(String screen, MediaType asRead) {
    return http.get()
        .uri(screen)
        .accept(asRead)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }
}
