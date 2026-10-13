package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class PersonaScreenIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-personas-it");

  private static final TestRunId OTHER_RUN = TestRunId.of("ui-personas-it-2");

  private static final String SCREEN = "/ui/personas";

  private static final String WORLDLINE = "worldline";

  private static final String GROW = "grow";

  private static final String PERSONA = "worldline-customer-with-tokenized-card";

  private static final String MERCHANT = "merchant-1";

  private static final int TWO_CELLS_END_ON_FEWER_CHARACTERS_THAN_THIS = 64;

  private static final int A_CELL_CARRIES_FEWER_CHARACTERS_THAN_THIS = 160;

  private static final int THE_WORDS_AT_EITHER_END_OF_A_SAYING = 40;

  @LocalServerPort private int port;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    for (TestRunId run : List.of(RUN, OTHER_RUN)) {
      reset(WORLDLINE, run);
      configure(run);
    }
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    for (TestRunId run : List.of(RUN, OTHER_RUN)) {
      reset(WORLDLINE, run);
    }
  }

  @Test
  void the_screen_lists_every_persona_this_provider_publishes_with_the_presets_it_writes() {
    Answer page = open(WORLDLINE, RUN);

    assertThat(page.status()).isEqualTo(200);
    assertThat(theCellText(page.body(), "persona-" + PERSONA)).isEqualTo(PERSONA);
    assertThat(page.body()).contains("preauth-success-capture-success");
    assertThat(page.body()).contains("Everything a persona creates is an ordinary object");
  }

  @Test
  void pressing_a_persona_without_confirming_shows_what_it_would_create_and_creates_nothing() {
    Answer shown = seed(WORLDLINE, RUN, PERSONA, null);

    assertThat(shown.status()).isEqualTo(200);
    assertThat(shown.body()).contains("not yet created");
    assertThat(theCellText(shown.body(), "proposal-persona")).isEqualTo(PERSONA);
    assertThat(theRulesOf(WORLDLINE, RUN)).isEmpty();
    assertThat(theFixturesOf(WORLDLINE, RUN)).isEmpty();
  }

  @Test
  void the_preview_panel_states_the_one_refusal_it_cannot_foresee_before_anything_is_created() {
    Answer shown = seed(WORLDLINE, RUN, PERSONA, null);

    assertThat(theCellText(shown.body(), "proposal-unforeseeable-refusal"))
        .contains("the provider mints them at the moment the persona is created")
        .contains("cannot foresee that refusal");
    assertThat(theFixturesOf(WORLDLINE, RUN)).isEmpty();
  }

  @Test
  void confirming_a_persona_creates_the_rules_and_the_instrument_and_names_each_of_them() {
    Answer created = seed(WORLDLINE, RUN, PERSONA, "yes");

    assertThat(created.status()).isEqualTo(200);
    assertThat(theCellText(created.body(), "created-persona")).isEqualTo(PERSONA);
    assertThat(theRulesOf(WORLDLINE, RUN)).isNotEmpty();
    assertThat(theFixturesOf(WORLDLINE, RUN)).hasSize(1);
    String fixtureId = String.valueOf(theFixturesOf(WORLDLINE, RUN).get(0).get("fixtureId"));
    assertThat(theCellText(created.body(), "created-fixture-" + fixtureId)).isEqualTo(fixtureId);
  }

  @Test
  void every_object_a_persona_created_is_reachable_and_deletable_on_its_own_screen() {
    seed(WORLDLINE, RUN, PERSONA, "yes");
    String fixtureId = String.valueOf(theFixturesOf(WORLDLINE, RUN).get(0).get("fixtureId"));
    String scenarioId = String.valueOf(theRulesOf(WORLDLINE, RUN).get(0).get("scenarioId"));

    Answer methods = open("/ui/payment-methods", WORLDLINE, RUN);
    Answer scenarios = open("/ui/scenarios", WORLDLINE, RUN);

    assertThat(methods.body()).contains("id=\"confirm-" + fixtureId + "\"");
    assertThat(methods.body()).contains(PERSONA);
    assertThat(scenarios.body()).contains("<td class=\"id\">" + scenarioId + "</td>");
    Answer opened =
        submitGet(
            "/ui/scenarios?provider="
                + WORLDLINE
                + "&testRunId="
                + RUN.value()
                + "&open="
                + scenarioId);
    assertThat(opened.body()).contains("value=\"" + scenarioId + "\"");

    for (Map<String, Object> rule : theRulesOf(WORLDLINE, RUN)) {
      assertThat(deleteTheRule(WORLDLINE, RUN, String.valueOf(rule.get("scenarioId"))).status())
          .isEqualTo(302);
    }
    assertThat(removeTheFixture(WORLDLINE, RUN, fixtureId).status()).isEqualTo(302);

    assertThat(theRulesOf(WORLDLINE, RUN)).isEmpty();
    assertThat(theFixturesOf(WORLDLINE, RUN)).isEmpty();
  }

  @Test
  void a_persona_seeded_on_one_run_is_invisible_on_the_same_screen_switched_to_another_run() {
    seed(WORLDLINE, RUN, PERSONA, "yes");

    assertThat(theFixturesOf(WORLDLINE, OTHER_RUN)).isEmpty();
    assertThat(theRulesOf(WORLDLINE, OTHER_RUN)).isEmpty();
    assertThat(open(WORLDLINE, OTHER_RUN).body()).doesNotContain("this run already holds it");
    assertThat(open(WORLDLINE, RUN).body()).contains("this run already holds it");
  }

  @Test
  void pressing_a_persona_the_run_already_holds_says_which_and_why_and_creates_nothing_more() {
    seed(WORLDLINE, RUN, PERSONA, "yes");
    int rules = theRulesOf(WORLDLINE, RUN).size();

    Answer again = seed(WORLDLINE, RUN, PERSONA, "yes");

    assertThat(again.status()).isEqualTo(200);
    assertThat(theRefusalOn(again.body())).contains(PERSONA).contains("already holds");
    assertThat(theRulesOf(WORLDLINE, RUN)).hasSize(rules);
    assertThat(theFixturesOf(WORLDLINE, RUN)).hasSize(1);
  }

  @Test
  void pressing_a_persona_with_no_account_says_so_and_creates_nothing() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", WORLDLINE);
    form.put("testRunId", RUN.value());
    form.put("persona", PERSONA);
    form.put("merchantId", "");
    form.put("confirm", "yes");

    Answer refused = submit(SCREEN + "/seed", form);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theRefusalOn(refused.body())).contains("merchantId must name the account");
    assertThat(theRulesOf(WORLDLINE, RUN)).isEmpty();
    assertThat(theFixturesOf(WORLDLINE, RUN)).isEmpty();
  }

  @Test
  void a_name_this_provider_does_not_publish_is_refused_on_the_screen_naming_the_ones_it_does() {
    Answer refused = seed(WORLDLINE, RUN, "stripe-customer-with-visa-success", "yes");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theRefusalOn(refused.body())).contains(PERSONA);
    assertThat(theFixturesOf(WORLDLINE, RUN)).isEmpty();
  }

  @Test
  void a_provider_that_publishes_no_persona_says_on_the_screen_what_it_has_instead() {
    Answer page = open(GROW, RUN);

    assertThat(page.status()).isEqualTo(200);
    assertThat(page.body()).contains("This provider publishes no persona.");
    assertThat(page.body())
        .contains("provider grow mints a cardToken only where a hosted process saved a card");
  }

  @Test
  void no_two_rows_of_the_persona_grid_end_on_the_same_words() {
    List<String> cells = theCellsOf(open(WORLDLINE, RUN).body());

    assertThat(cells).isNotEmpty();
    for (int one = 0; one < cells.size(); one++) {
      for (int other = one + 1; other < cells.size(); other++) {
        assertThat(theCharactersTwoCellsEndOn(cells.get(one), cells.get(other)))
            .as(
                "a fact two rows both end on is the provider's fact and not either row's, and it"
                    + " belongs under the disclosure once, not in every row: <"
                    + cells.get(one)
                    + "> and <"
                    + cells.get(other)
                    + ">")
            .isLessThan(TWO_CELLS_END_ON_FEWER_CHARACTERS_THAN_THIS);
      }
    }
  }

  @Test
  void no_cell_of_the_page_a_preview_opens_carries_a_paragraph_and_the_minting_is_under_a_read() {
    Answer shown = seed(WORLDLINE, RUN, PERSONA, null);
    String minted = theMintingThisProviderPublishes();

    assertThat(theCellsOf(shown.body()))
        .allSatisfy(
            cell ->
                assertThat(cell.length()).isLessThan(A_CELL_CARRIES_FEWER_CHARACTERS_THAN_THIS));
    assertThat(theDisclosuresOf(shown.body()))
        .anySatisfy(read -> assertThat(read).contains(minted));
  }

  @Test
  void the_first_and_the_last_words_of_every_published_persona_are_both_still_on_the_screen() {
    String page = theTextOf(open(WORLDLINE, RUN).body());

    assertThat(whatEachPersonaIsPublishedAsGiving()).isNotEmpty();
    for (String published : whatEachPersonaIsPublishedAsGiving()) {
      assertThat(page)
          .as("the words this provider opens %s with are on the screen", published)
          .contains(theFirstWordsOf(published));
      assertThat(page)
          .as("the words this provider ends %s on are on the screen", published)
          .contains(theLastWordsOf(published));
    }
  }

  @Test
  void the_screen_that_names_no_provider_offers_the_ones_this_simulator_knows() {
    Answer page = submitGet(SCREEN);

    assertThat(page.status()).isEqualTo(200);
    assertThat(page.body()).contains("Choose a provider");
  }

  private Answer open(String provider, TestRunId testRunId) {
    return open(SCREEN, provider, testRunId);
  }

  private Answer open(String screen, String provider, TestRunId testRunId) {
    return submitGet(screen + "?provider=" + provider + "&testRunId=" + testRunId.value());
  }

  private Answer seed(
      String provider, TestRunId testRunId, String persona, @Nullable String confirm) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", provider);
    form.put("testRunId", testRunId.value());
    form.put("persona", persona);
    form.put("merchantId", MERCHANT);
    if (confirm != null) {
      form.put("confirm", confirm);
    }
    return submit(SCREEN + "/seed", form);
  }

  private Answer deleteTheRule(String provider, TestRunId testRunId, String scenarioId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", provider);
    form.put("testRunId", testRunId.value());
    form.put("scenarioId", scenarioId);
    form.put("confirm", "yes");
    return submit("/ui/scenarios/delete", form);
  }

  private Answer removeTheFixture(String provider, TestRunId testRunId, String fixtureId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", provider);
    form.put("testRunId", testRunId.value());
    form.put("fixtureId", fixtureId);
    form.put("confirm", "yes");
    return submit("/ui/payment-methods/remove", form);
  }

  private static List<String> theCellsOf(String page) {
    return theTextOfEvery("<td[^>]*>(.*?)</td>", page);
  }

  private static List<String> theDisclosuresOf(String page) {
    return theTextOfEvery("<details[^>]*>(.*?)</details>", page);
  }

  private static List<String> theTextOfEvery(String shape, String page) {
    List<String> found = new ArrayList<>();
    Matcher parts = Pattern.compile(shape, Pattern.DOTALL).matcher(page);
    while (parts.find()) {
      found.add(theTextOf(parts.group(1)));
    }
    return found;
  }

  private static String theTextOf(String markup) {
    return markup
        .replaceAll("<[^>]*>", " ")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replaceAll("\\s+", " ")
        .strip();
  }

  private static String theFirstWordsOf(String published) {
    return published.strip().substring(0, THE_WORDS_AT_EITHER_END_OF_A_SAYING);
  }

  private static String theLastWordsOf(String published) {
    String said = published.strip();
    return said.substring(said.length() - THE_WORDS_AT_EITHER_END_OF_A_SAYING);
  }

  private List<String> whatEachPersonaIsPublishedAsGiving() {
    List<String> published = new ArrayList<>();
    for (Object persona : thePersonasThisProviderPublishes()) {
      published.add(String.valueOf(((Map<?, ?>) persona).get("whatItGives")));
    }
    return published;
  }

  private static int theCharactersTwoCellsEndOn(String one, String other) {
    int shared = 0;
    while (shared < one.length()
        && shared < other.length()
        && one.charAt(one.length() - 1 - shared) == other.charAt(other.length() - 1 - shared)) {
      shared++;
    }
    return shared;
  }

  private List<?> thePersonasThisProviderPublishes() {
    Map<String, Object> published =
        http.get()
            .uri("/__simulator/" + WORLDLINE + "/personas")
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return (List<?>) Objects.requireNonNull(published).get("personas");
  }

  private String theMintingThisProviderPublishes() {
    for (Object persona : thePersonasThisProviderPublishes()) {
      Map<?, ?> named = (Map<?, ?>) persona;
      if (PERSONA.equals(named.get("name"))) {
        Map<?, ?> instrument = (Map<?, ?>) ((List<?>) named.get("instruments")).get(0);
        return String.valueOf(instrument.get("howThoseAreMinted"));
      }
    }
    throw new IllegalStateException("provider " + WORLDLINE + " published no persona " + PERSONA);
  }

  private static @Nullable String theCellText(String page, String id) {
    Matcher cell = Pattern.compile("id=\"" + Pattern.quote(id) + "\"[^>]*>([^<]*)<").matcher(page);
    return cell.find() ? cell.group(1).strip() : null;
  }

  private static String theRefusalOn(String page) {
    Matcher refusal = Pattern.compile("<div class=\"refusal\">([^<]*)</div>").matcher(page);
    assertThat(refusal.find()).isTrue();
    return refusal.group(1).strip();
  }

  private List<Map<String, Object>> theRulesOf(String provider, TestRunId testRunId) {
    return read("/__simulator/" + provider + "/scenarios?testRunId=" + testRunId.value());
  }

  private List<Map<String, Object>> theFixturesOf(String provider, TestRunId testRunId) {
    return read(
        "/__simulator/" + provider + "/fixtures/payment-methods?testRunId=" + testRunId.value());
  }

  private List<Map<String, Object>> read(String uri) {
    return http.get()
        .uri(uri)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private void reset(String provider, TestRunId testRunId) {
    http.post()
        .uri("/__simulator/" + provider + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", testRunId.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private void configure(TestRunId testRunId) {
    http.put()
        .uri("/__simulator/worldline/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                testRunId.value(),
                "clock",
                Map.of(),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of(
                            "merchantId", MERCHANT, "apiKey", "key-1", "apiSecret", "secret-1"))),
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private Answer submitGet(String path) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET());
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  private Answer submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private record Answer(int status, String body, String location) {}
}
