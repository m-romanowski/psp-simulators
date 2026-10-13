package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TheOrderPageTest {

  private static final String THE_SEND_BUTTON = "Send payment_intent.succeeded";

  private static final String THE_STATUS_REGION = "<div id=\"story-ending\" role=\"status\">";

  @TestConfiguration(proxyBeanMethods = false)
  static class TheSimulatorInMemory {

    @Bean
    @Primary
    AnInMemorySimulator anInMemorySimulator(StripeWebhooks endpoint) {
      AnInMemorySimulator simulator = new AnInMemorySimulator();
      simulator.deliversWebhooksTo(endpoint);
      return simulator;
    }
  }

  @LocalServerPort private int port;

  @Autowired private AnInMemorySimulator simulator;

  @Autowired private Stories stories;

  private final HttpClient http = HttpClient.newHttpClient();

  @AfterEach
  void tidyUp() {
    stories.tidyUp();
  }

  @Test
  @DisplayName("the live order is refused as too early before anything has been bought")
  void theLiveOrderIsTooEarlyBeforeBuying() throws Exception {
    String testRunId = aStoryIsPlayed();

    HttpResponse<String> live = get("/runs/" + testRunId + "/order/live");

    assertThat(live.statusCode()).isEqualTo(409);
    assertThat(live.body()).contains("has not reached the point where there is an order to show");
  }

  @Test
  @DisplayName(
      "the live order of a run the shop does not keep is not found, and answers with a page that"
          + " asks no more")
  void theLiveOrderOfAnUnknownRunIsNotFoundAndStopsThePolling() throws Exception {
    HttpResponse<String> gone = get("/runs/shop-gone/order/live");

    assertThat(gone.statusCode()).isEqualTo(404);
    assertThat(gone.body())
        .contains("<main id=\"order-live\"")
        .contains("data-testid=\"run-gone\"")
        .doesNotContain("hx-get")
        .doesNotContain("hx-trigger");
  }

  @Test
  @DisplayName("a poll of a run tidied up meanwhile is answered with a page that asks no more")
  void aPollOfARunTidiedUpMeanwhileAsksNoMore() throws Exception {
    String testRunId = aStoryIsPlayed();
    post("/runs/" + testRunId + "/checkout");
    post("/runs/" + testRunId + "/order/capture");
    post("/runs/" + testRunId + "/stripe-reports-the-finished-payment");
    assertThat(get("/runs/" + testRunId + "/order").body()).contains("hx-trigger=\"every 2s\"");

    post("/tidy-up");
    HttpResponse<String> gone = get("/runs/" + testRunId + "/order/live");

    assertThat(gone.statusCode()).isEqualTo(404);
    assertThat(gone.body())
        .contains("<main id=\"order-live\"")
        .contains("data-testid=\"run-gone\"")
        .doesNotContain("hx-trigger");
  }

  @Test
  @DisplayName("the order page of a kept run shows one order and no word of a tidied run")
  void theOrderPageOfAKeptRunShowsOneOrder() throws Exception {
    String testRunId = aStoryIsPlayed();
    post("/runs/" + testRunId + "/checkout");

    String page = get("/runs/" + testRunId + "/order").body();

    assertThat(Pattern.compile("id=\"order-live\"").matcher(page).results()).hasSize(1);
    assertThat(page).doesNotContain("run-gone");
  }

  @Test
  @DisplayName("the order page asks nothing before Stripe has been asked to report the payment")
  void theOrderPageDoesNotPollBeforeStripeIsAsked() throws Exception {
    String testRunId = aStoryIsPlayed();
    post("/runs/" + testRunId + "/checkout");

    assertThat(get("/runs/" + testRunId + "/order").body())
        .contains("data-testid=\"order-status\"")
        .doesNotContain("hx-trigger")
        .doesNotContain("hx-get");

    post("/runs/" + testRunId + "/order/capture");

    assertThat(get("/runs/" + testRunId + "/order").body())
        .contains(THE_SEND_BUTTON)
        .doesNotContain("hx-trigger")
        .doesNotContain("hx-get");
  }

  @Test
  @DisplayName(
      "the story ending appears inside a status region the page already held while the event was"
          + " awaited")
  void theStoryEndingAppearsInsideAStatusRegionHeldBeforeIt() throws Exception {
    String testRunId = aStoryIsPlayed();
    post("/runs/" + testRunId + "/checkout");
    post("/runs/" + testRunId + "/order/capture");
    post("/runs/" + testRunId + "/stripe-reports-the-finished-payment");

    String awaited = get("/runs/" + testRunId + "/order/live").body();
    simulator.deliverWhatIsDue();
    String arrived = get("/runs/" + testRunId + "/order/live").body();

    assertThat(awaited).containsPattern(THE_STATUS_REGION).doesNotContain("story-ended");
    assertThat(arrived).containsPattern(THE_STATUS_REGION + "\\s*<p data-testid=\"story-ended\">");
    assertThat(arrived).contains("<section id=\"order-news\" class=\"card\" aria-live=\"polite\">");
  }

  @Test
  @DisplayName(
      "every link and button the order page swaps carries an id, so a swap keeps its focus")
  void everyControlTheOrderPageSwapsCarriesAnId() throws Exception {
    String testRunId = aStoryIsPlayed("a-decline-the-test-asked-for");
    String before = get("/runs/" + testRunId + "/order").body();
    post("/runs/" + testRunId + "/checkout");
    String bought = get("/runs/" + testRunId + "/order/live").body();

    for (String page : List.of(theMainOf(before), bought)) {
      assertThat(Pattern.compile("<(a|button)\\b[^>]*>").matcher(page).results())
          .isNotEmpty()
          .allSatisfy(control -> assertThat(control.group()).containsPattern(" id=\"[^\"]+\""));
    }
  }

  @Test
  @DisplayName("the home page names a run tidy-up could not tidy, and why")
  void theHomePageNamesARunTidyUpCouldNotTidy() throws Exception {
    String testRunId = aStoryIsPlayed();
    simulator.refusesToRemoveTheAccountOf(testRunId);
    HttpClient keepingTheSession =
        HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

    HttpResponse<String> tidied =
        keepingTheSession.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/tidy-up"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    URI whereItSends =
        URI.create("http://127.0.0.1:" + port + "/")
            .resolve(tidied.headers().firstValue("Location").orElseThrow());
    HttpResponse<String> home =
        keepingTheSession.send(
            HttpRequest.newBuilder(whereItSends).build(), HttpResponse.BodyHandlers.ofString());
    simulator.agreesToRemoveEveryAccount();

    assertThat(tidied.statusCode()).isEqualTo(302);
    assertThat(home.statusCode()).isEqualTo(200);
    assertThat(home.body())
        .contains("data-testid=\"left-behind\"")
        .contains("<code>" + testRunId + "</code>")
        .contains("HTTP 404");
  }

  @Test
  @DisplayName(
      "the story panel refreshes with the order while Stripe's webhook is awaited, and stops once"
          + " it has arrived")
  void theStoryPanelRefreshesUntilTheWebhookArrives() throws Exception {
    String testRunId = aStoryIsPlayed();
    post("/runs/" + testRunId + "/checkout");
    post("/runs/" + testRunId + "/order/capture");

    String captured = get("/runs/" + testRunId + "/order/live").body();
    assertThat(captured).doesNotContain("hx-trigger").contains(THE_SEND_BUTTON);

    post("/runs/" + testRunId + "/stripe-reports-the-finished-payment");
    post("/runs/" + testRunId + "/stripe-reports-the-finished-payment");

    String awaited = get("/runs/" + testRunId + "/order/live").body();
    assertThat(awaited)
        .contains("hx-trigger=\"every 2s\"")
        .contains("data-testid=\"event-awaited\"")
        .doesNotContain(THE_SEND_BUTTON)
        .doesNotContain("data-testid=\"story-ended\"");
    assertThat(simulator.sent()).filteredOn(sent -> sent.testRunId().equals(testRunId)).hasSize(1);

    simulator.deliverWhatIsDue();

    String arrived = get("/runs/" + testRunId + "/order/live").body();
    assertThat(arrived)
        .contains("data-testid=\"story-ended\"")
        .contains("payment_intent.succeeded</td>")
        .doesNotContain("data-testid=\"event-awaited\"")
        .doesNotContain(THE_SEND_BUTTON)
        .doesNotContain("hx-trigger")
        .doesNotContain("hx-get");
  }

  @Test
  @DisplayName("the order page of an ended story does not poll")
  void theOrderPageOfAnEndedStoryDoesNotPoll() throws Exception {
    String testRunId = aStoryIsPlayed("a-decline-the-test-asked-for");
    post("/runs/" + testRunId + "/checkout");
    post("/runs/" + testRunId + "/order/capture");

    String page = get("/runs/" + testRunId + "/order").body();

    assertThat(page).contains("data-testid=\"story-ended\"").doesNotContain("hx-trigger");
  }

  private static String theMainOf(String page) {
    return page.substring(page.indexOf("<main"), page.indexOf("</main>"));
  }

  private String aStoryIsPlayed() throws IOException, InterruptedException {
    return aStoryIsPlayed("a-capture-that-goes-through");
  }

  private String aStoryIsPlayed(String slug) throws IOException, InterruptedException {
    HttpResponse<String> played = post("/stories/" + slug);
    assertThat(played.statusCode()).isEqualTo(302);
    return played
        .headers()
        .firstValue("Location")
        .orElseThrow()
        .replaceAll(".*/runs/([^/]+)/product$", "$1");
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path) throws IOException, InterruptedException {
    HttpResponse<String> answered =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(answered.statusCode()).as("POST " + path).isEqualTo(302);
    return answered;
  }
}
