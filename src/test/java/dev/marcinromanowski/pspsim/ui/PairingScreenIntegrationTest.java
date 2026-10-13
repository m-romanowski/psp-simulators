package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class PairingScreenIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-pairing-it");

  private static final String MERCHANT = "merchant-1";

  private static final String PATH = "/ui/pairing";

  private static final String GROW_USER = "grow-user-must-never-be-read-off-a-monitor";
  private static final String GROW_PAGE = "grow-page-must-never-be-read-off-a-monitor";
  private static final String AUTRA_CLIENT_ID = "autra-client-id-a-tester-may-read";
  private static final String AUTRA_CLIENT_SECRET = "autra-secret-must-never-be-read";
  private static final String AUTRA_DOCUMENT_ID = "12345678000199";
  private static final String AUTRA_WEBHOOK_SECRET = "autra-webhook-secret-must-never-be-read";
  private static final String TERMINAL = "az-terminal-1";
  private static final String WORLDLINE_MERCHANT = "PSPID-4412";
  private static final String WORLDLINE_API_KEY = "worldline-api-key-a-tester-may-read";
  private static final String WORLDLINE_API_SECRET = "worldline-secret-must-never-be-read";
  private static final String STRIPE_SECRET_KEY = "sk_test_must_never_be_read_off_a_monitor";
  private static final String STRIPE_SIGNING_SECRET = "whsec_must_never_be_read_off_a_monitor";

  private static final KeyPair MERCHANT_KEYS = rsa();
  private static final KeyPair GATEWAY_KEYS = rsa();
  private static final String MERCHANT_PUBLIC_PEM =
      pem("PUBLIC KEY", MERCHANT_KEYS.getPublic().getEncoded());
  private static final String GATEWAY_PRIVATE_PEM =
      pem("PRIVATE KEY", GATEWAY_KEYS.getPrivate().getEncoded());

  private static final List<String> EVERY_DECLARED_SECRET =
      List.of(
          GROW_USER,
          GROW_PAGE,
          AUTRA_CLIENT_SECRET,
          AUTRA_WEBHOOK_SECRET,
          WORLDLINE_API_SECRET,
          STRIPE_SECRET_KEY,
          STRIPE_SIGNING_SECRET);

  private static final List<String> EVERY_PROVIDER =
      List.of("grow", "autra", "azericard", "worldline", "stripe");

  @LocalServerPort private int port;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void configureEveryProviderOfTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    configure(
        "grow",
        Map.of("userId", GROW_USER, "pageCode", GROW_PAGE),
        List.of(Map.of("accountId", MERCHANT, "url", "https://merchant.test/callbacks")));
    configure(
        "autra",
        Map.of(
            "clientId", AUTRA_CLIENT_ID,
            "clientSecret", AUTRA_CLIENT_SECRET,
            "documentId", AUTRA_DOCUMENT_ID,
            "webhookSecret", AUTRA_WEBHOOK_SECRET),
        List.of());
    configure(
        "azericard",
        Map.of(
            "terminalId", TERMINAL,
            "apiKey", MERCHANT_PUBLIC_PEM,
            "apiSecret", GATEWAY_PRIVATE_PEM),
        List.of());
    configure(
        "worldline",
        Map.of(
            "merchantId", WORLDLINE_MERCHANT,
            "apiKey", WORLDLINE_API_KEY,
            "apiSecret", WORLDLINE_API_SECRET),
        List.of());
    configure(
        "stripe",
        Map.of(
            "secretKey", STRIPE_SECRET_KEY,
            "captureWindow", "P3D",
            "webhookSecret", STRIPE_SIGNING_SECRET),
        List.of());
  }

  @AfterEach
  void unconfigureTheTestRun() {
    EVERY_PROVIDER.forEach(this::unconfigure);
  }

  @Test
  void no_value_any_provider_declares_secret_reaches_any_providers_pairing_screen() {
    for (String provider : EVERY_PROVIDER) {
      String page = page(screen(provider));
      for (String secret : EVERY_DECLARED_SECRET) {
        assertThat(page).doesNotContain(secret);
      }
      assertThat(page).doesNotContain(oneLineOf(GATEWAY_PRIVATE_PEM));
    }
  }

  @Test
  void a_secret_is_withheld_by_this_screen_rather_than_printed_as_the_marker_it_was_stored_as() {
    for (String provider : EVERY_PROVIDER) {
      assertThat(page(screen(provider))).doesNotContain(Redaction.REDACTED);
    }
    assertThat(page(screen("stripe"))).contains("set this yourself from");
  }

  @Test
  void every_field_a_provider_declares_secret_is_named_on_its_screen_although_no_value_is() {
    assertThat(page(screen("grow"))).contains("userId").contains("pageCode");
    assertThat(page(screen("autra"))).contains("clientSecret");
    assertThat(page(screen("azericard"))).contains("apiSecret");
    assertThat(page(screen("worldline"))).contains("apiSecret");
    assertThat(page(screen("stripe"))).contains("secretKey");
  }

  @Test
  void a_value_the_provider_declares_is_not_secret_is_the_pairing_data_the_screen_hands_over() {
    assertThat(page(screen("autra"))).contains(AUTRA_CLIENT_ID).contains(AUTRA_DOCUMENT_ID);
    assertThat(page(screen("azericard"))).contains(TERMINAL);
    assertThat(page(screen("worldline"))).contains(WORLDLINE_MERCHANT).contains(WORLDLINE_API_KEY);
  }

  @Test
  void the_screen_names_the_base_url_the_test_run_the_header_and_the_account_of_the_run() {
    String worldline = page(screen("worldline"));

    assertThat(worldline).contains("http://localhost:8080/v2");
    assertThat(worldline).contains(TestRunHeader.NAME);
    assertThat(worldline).contains(RUN.value());
    assertThat(worldline).contains(MERCHANT);
  }

  @Test
  void each_provider_is_paired_against_on_the_path_its_own_contract_answers_on() {
    assertThat(page(screen("grow"))).contains("http://localhost:8080/api/light/server/1.0");
    assertThat(page(screen("autra"))).contains("http://localhost:8080/v1");
    assertThat(page(screen("azericard"))).contains("http://localhost:8080/cgi-bin/cgi_link");
    assertThat(page(screen("worldline"))).contains("http://localhost:8080/v2");
    assertThat(page(screen("stripe"))).contains("http://localhost:8080/v1");
  }

  @Test
  void the_payload_a_camera_reads_off_this_screen_is_the_screens_own_url_and_nothing_else() {
    String stripe = page(screen("stripe"));

    assertThat(stripe)
        .contains("http://localhost:8080/ui/pairing?provider=stripe&amp;testRunId=ui-pairing-it");
    for (String secret : EVERY_DECLARED_SECRET) {
      assertThat(stripe).doesNotContain(secret);
    }
  }

  @Test
  void the_screen_sends_a_tester_to_the_trail_filtered_to_traffic_that_arrived_from_an_app() {
    assertThat(page(screen("grow")))
        .contains("/ui/audit?provider=grow&amp;testRunId=ui-pairing-it&amp;origin=PROVIDER_API");
  }

  @Test
  void the_callback_url_the_test_run_configured_is_on_the_screen_beside_the_account_it_belongs() {
    assertThat(page(screen("grow"))).contains("https://merchant.test/callbacks").contains(MERCHANT);
  }

  @Test
  void a_provider_this_simulator_does_not_know_is_refused_rather_than_paired_against() {
    assertThat(page(PATH + "?provider=nosuchprovider&testRunId=" + RUN.value()))
        .contains("unknown provider");
  }

  private static String screen(String provider) {
    return PATH + "?provider=" + provider + "&testRunId=" + RUN.value();
  }

  private String page(String uri) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + uri))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(response.statusCode()).isEqualTo(200);
    return new String(response.body(), UTF_8);
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

  private void configure(
      String provider, Map<String, String> configuration, List<Map<String, String>> webhooks) {
    try {
      put(provider, configuration, webhooks);
    } catch (RestClientResponseException refused) {
      throw new IllegalStateException(
          "provider "
              + provider
              + " refused this test's configuration: "
              + refused.getResponseBodyAsString());
    }
  }

  private void put(
      String provider, Map<String, String> configuration, List<Map<String, String>> webhooks) {
    http.put()
        .uri("/__simulator/" + provider + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "accounts",
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                webhooks))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure(String provider) {
    http.put()
        .uri("/__simulator/" + provider + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private static KeyPair rsa() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }

  private static String pem(String label, byte[] encoded) {
    return "-----BEGIN "
        + label
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
        + "\n-----END "
        + label
        + "-----\n";
  }

  private static String oneLineOf(String pem) {
    return pem.lines().skip(2).findFirst().orElseThrow();
  }
}
