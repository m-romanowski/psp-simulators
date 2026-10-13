package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class AzericardSeededCardIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-seeded-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");

  private static final String TERMINAL_ID = "70000011";

  private static final String MERCHANT = "merchant-1";

  private static final String TOKEN_LINK = "/token/cgi_link";

  private static final String SEEDED_TOKEN = "SEEDEDOVERTHEWIRE00000000001";

  private static final String A_SECOND_SEEDED_TOKEN = "SEEDEDFROMASCREEN00000000001";

  private static final String FIXTURES = "/__simulator/azericard/fixtures/payment-methods";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheTerminal() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTerminal() {
    reset();
    unconfigure();
  }

  @Test
  void a_harness_seeds_a_card_over_the_wire_and_then_pays_with_the_token_it_chose_itself() {
    ResponseEntity<Map<String, Object>> seeded = seed(SEEDED_TOKEN, "visa-success");

    assertThat(seeded.getStatusCode().value()).isEqualTo(201);
    assertThat(seeded.getBody()).containsEntry("providerObjects", Map.of("token", SEEDED_TOKEN));

    String answered =
        new String(post(signed(storedCardPayment("20260910140011", SEEDED_TOKEN))).body(), UTF_8);

    assertThat(answered).contains("<action>0</action>").contains("<rc>00</rc>");
  }

  @Test
  void the_seeded_card_is_an_object_of_its_own_that_names_the_fixture_and_no_payment() {
    String fixtureId = fixtureIdOf(seed(SEEDED_TOKEN, "visa-success"));

    Map<String, Object> state = stateOf(SEEDED_TOKEN);

    assertThat(relatedOf(state))
        .containsEntry("terminal", TERMINAL_ID)
        .containsEntry("merchantId", MERCHANT)
        .containsEntry("fixtureId", fixtureId)
        .doesNotContainKey("order")
        .doesNotContainKey("storedBy");
    assertThat(state).containsEntry("origin", "CONTROL_API");
  }

  @Test
  void a_card_a_tester_seeded_from_the_screen_reports_the_screen_on_every_surface_that_reads_it() {
    HttpResponse<byte[]> seeded = seedFromTheScreen(SEEDED_TOKEN, "visa-from-a-screen");

    assertThat(seeded.statusCode()).isEqualTo(302);
    assertThat(stateOf(SEEDED_TOKEN)).containsEntry("origin", "UI");
    assertThat(theActionThatSeededIt()).containsEntry("origin", "UI");
    assertThat(thePaymentListScreen()).contains(SEEDED_TOKEN).contains(">UI<");
  }

  @Test
  void a_card_the_control_api_seeded_still_reports_the_control_api_beside_one_a_screen_seeded() {
    seed(SEEDED_TOKEN, "visa-success");
    seedFromTheScreen(A_SECOND_SEEDED_TOKEN, "visa-from-a-screen");

    assertThat(stateOf(SEEDED_TOKEN)).containsEntry("origin", "CONTROL_API");
    assertThat(stateOf(A_SECOND_SEEDED_TOKEN)).containsEntry("origin", "UI");
  }

  @Test
  void the_token_this_box_hints_is_one_this_gateway_seeds_a_card_under() {
    HttpResponse<byte[]> seeded =
        seedFromTheScreen(
            AzericardFixtureCards.A_TOKEN_OF_THE_WIDTH_THIS_GATEWAY_CARRIES, "visa-from-the-hint");

    assertThat(seeded.statusCode()).isIn(200, 302);
    assertThat(stateOf(AzericardFixtureCards.A_TOKEN_OF_THE_WIDTH_THIS_GATEWAY_CARRIES))
        .as(
            "this gateway sizes a TOKEN at exactly %s characters, so a hint of any other width"
                + " would teach a tester a value their first seed is refused for",
            AzericardStoredCard.TOKEN_LENGTH)
        .containsEntry("origin", "UI");
  }

  private HttpResponse<byte[]> seedFromTheScreen(String token, String alias) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "azericard");
    form.put("testRunId", RUN.value());
    form.put("merchantId", MERCHANT);
    form.put("ownerId", "customer-123");
    form.put("type", "card");
    form.put("alias", alias);
    form.put("brand", "visa");
    form.put("last4", "4580");
    form.put("expMonth", "4");
    form.put("expYear", "2024");
    form.put("funding", "credit");
    form.put("providerId.token", token);
    return submit("/ui/payment-methods/seed", form);
  }

  private Map<String, Object> theActionThatSeededIt() {
    List<Map<String, Object>> rows =
        http.get()
            .uri("/__simulator/azericard/audit?testRunId=" + RUN.value() + "&origin=UI&method=POST")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(rows).isNotNull();
    for (Map<String, Object> row : rows) {
      if ("/ui/payment-methods/seed".equals(row.get("path"))) {
        return row;
      }
    }
    throw new AssertionError("no action of the trail names the payment-method seed screen");
  }

  private String thePaymentListScreen() {
    HttpResponse<byte[]> answer = get("/ui/objects?provider=azericard&testRunId=" + RUN.value());
    assertThat(answer.statusCode()).isEqualTo(200);
    return new String(answer.body(), UTF_8);
  }

  private HttpResponse<byte[]> submit(String path, Map<String, String> form) {
    try {
      return wire.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private HttpResponse<byte[]> get(String path) {
    try {
      return wire.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
          HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  @Test
  void removing_the_fixture_takes_the_card_the_gateway_would_have_charged_with_it() {
    String fixtureId = fixtureIdOf(seed(SEEDED_TOKEN, "visa-success"));

    ResponseEntity<Map<String, Object>> removed =
        exchange(HttpMethod.DELETE, FIXTURES + "/" + fixtureId + "?testRunId=" + RUN.value(), null);

    assertThat(removed.getStatusCode().value()).isEqualTo(200);
    assertThat(missingStateOf(SEEDED_TOKEN)).isEqualTo(404);
    assertThat(
            new String(
                post(signed(storedCardPayment("20260910140012", SEEDED_TOKEN))).body(), UTF_8))
        .contains("<rc>" + AzericardOutcomes.RC_NO_SUCH_TOKEN + "</rc>");
  }

  @Test
  void a_reset_takes_the_seeded_card_with_the_fixture_that_seeded_it() {
    seed(SEEDED_TOKEN, "visa-success");

    reset();

    assertThat(missingStateOf(SEEDED_TOKEN)).isEqualTo(404);
    assertThat(list()).isEmpty();
  }

  @Test
  void a_card_seeded_for_an_account_this_run_never_configured_is_refused_by_the_gateway() {
    Map<String, Object> body = aCard(SEEDED_TOKEN, "visa-success");
    body.put("merchantId", "merchant-nobody-configured");

    ResponseEntity<Map<String, Object>> refused = exchange(HttpMethod.POST, FIXTURES, body);

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(refused.getBody().get("message"))).contains("TERMINAL");
    assertThat(missingStateOf(SEEDED_TOKEN)).isEqualTo(404);
  }

  @Test
  void a_token_a_payment_of_this_run_already_stored_a_card_under_cannot_be_seeded_over() {
    String stored = aStoredCardFor("20260910140013");

    ResponseEntity<Map<String, Object>> refused = seed(stored, "visa-again");

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(refused.getBody().get("message")))
        .contains("already holds a stored card");
  }

  private String aStoredCardFor(String order) {
    String body = new String(post(signed(cardStorage(order))).body(), UTF_8);
    return (String) relatedOf(stateOf(valueIn(body, "intref"))).get("token");
  }

  private static String fixtureIdOf(ResponseEntity<Map<String, Object>> seeded) {
    return String.valueOf(seeded.getBody().get("fixtureId"));
  }

  private ResponseEntity<Map<String, Object>> seed(String token, String alias) {
    return exchange(HttpMethod.POST, FIXTURES, aCard(token, alias));
  }

  private static Map<String, Object> aCard(String token, String alias) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("testRunId", RUN.value());
    body.put("merchantId", MERCHANT);
    body.put("ownerId", "customer-123");
    body.put("type", "card");
    body.put("alias", alias);
    body.put(
        "card",
        Map.of(
            "brand", "visa", "last4", "4580", "expMonth", 4, "expYear", 2024, "funding", "credit"));
    body.put("providerIds", Map.of("token", token));
    return body;
  }

  private List<Map<String, Object>> list() {
    return http.get()
        .uri(FIXTURES + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
        .getBody();
  }

  private ResponseEntity<Map<String, Object>> exchange(
      HttpMethod method, String uri, Map<String, Object> body) {
    RestClient.RequestBodySpec sending = http.method(method).uri(uri);
    return (body == null ? sending : sending.contentType(MediaType.APPLICATION_JSON).body(body))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private HttpResponse<byte[]> post(Map<String, String> form) {
    try {
      return wire.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + TOKEN_LINK))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private static String valueIn(String xml, String element) {
    int opening = xml.indexOf("<" + element + ">");
    return xml.substring(
        opening + element.length() + 2, xml.indexOf("</" + element + ">", opening));
  }

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/azericard/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private int missingStateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/azericard/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> relatedOf(Map<String, Object> state) {
    return (Map<String, Object>) state.get("relatedObjects");
  }

  private static Map<String, String> authorization(String order) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "1");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.AMOUNT, "0.01");
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    form.put(AzericardFields.NONCE, "F2B2DD7E603A7ADA");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> cardStorage(String order) {
    Map<String, String> form = authorization(order);
    form.put(AzericardFields.TOKEN_ACTION, AzericardTokenFlow.REGISTER);
    form.put(AzericardFields.MERCH_TRAN_STATE, AzericardTokenFlow.STORING_A_CARD);
    return form;
  }

  private static Map<String, String> storedCardPayment(String order, String token) {
    Map<String, String> form = authorization(order);
    form.put(AzericardFields.TOKEN, token);
    form.keySet().removeAll(AzericardFields.CARD_DATA);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    Map<String, List<String>> fields = new LinkedHashMap<>();
    signed.forEach((name, value) -> fields.put(name, List.of(value)));
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.TOKEN_CGI_LINK, fields).macSource()));
    return signed;
  }

  private void configure() {
    http.put()
        .uri("/__simulator/azericard/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of(
                            AzericardTerminal.TERMINAL_PROPERTY,
                            TERMINAL_ID,
                            AzericardTerminal.MERCHANT_KEY_PROPERTY,
                            AzericardKeyFixture.MERCHANT_PUBLIC,
                            AzericardTerminal.GATEWAY_KEY_PROPERTY,
                            AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8)))))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure() {
    http.put()
        .uri("/__simulator/azericard/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/azericard/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }
}
