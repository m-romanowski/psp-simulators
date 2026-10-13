package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class AKeyPastedAsItComesOutOfAFileIsSuppliedIntegrationTest extends PostgresBackedTest {

  private static final String SCREEN = "/ui/run-bundle";

  private static final String RUN = "a-key-out-of-a-file-it";

  private static final String INTO = "a-key-out-of-a-file-it-2";

  private static final String TERMINAL = "70000011";

  private static final String MERCHANT = "merchant-1";

  private static final KeyPair MERCHANT_KEYS = rsa();

  private static final KeyPair GATEWAY_KEYS = rsa();

  private static final Pattern THE_DOCUMENT_BOX =
      Pattern.compile(
          "<textarea class=\"document\" readonly[^>]*>(.*?)</textarea>", Pattern.DOTALL);

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
    clearBothRuns();
    configureTheTerminalOf(RUN);
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    clearBothRuns();
  }

  @Test
  void a_gateway_key_pasted_as_it_comes_out_of_a_file_is_the_secret_the_import_was_given() {
    String document = theDocumentOn(get(SCREEN + "?provider=azericard&testRunId=" + RUN));
    assertThat(document)
        .as("the bundle names the secret it does not carry, which is the one a tester must retype")
        .contains("apiSecret");

    String reported = pasteInto(INTO, document, MERCHANT + ".apiSecret=" + aPrivateKeyInPem());

    assertThat(reported)
        .as(
            "a private key is a block of many lines and nothing on this screen asks a tester to"
                + " flatten it, so the lines under the first one are the rest of that one value."
                + " The account was created, which no value arriving in pieces could have"
                + " managed: this provider reads that field as a key before it writes anything,"
                + " and refuses the import when it cannot")
        .contains("account")
        .contains(MERCHANT)
        .contains("CREATED")
        .doesNotContain(RunBundleScreen.A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE);
    assertThat(reported)
        .as("the import names the field it was given and never the key itself")
        .contains(MERCHANT + ".apiSecret")
        .doesNotContain("MIIEvQ");
  }

  @Test
  void only_a_provider_that_draws_a_block_offers_a_tester_a_value_that_runs_on() {
    String whatTheClauseSays = "may run on over the";

    assertThat(get(SCREEN + "?provider=azericard&testRunId=" + RUN))
        .as("azericard draws two keys as blocks of many lines, so its box takes one")
        .contains(whatTheClauseSays);
    assertThat(get(SCREEN + "?provider=grow&testRunId=" + RUN))
        .as(
            "provider grow draws no block at all, so a box promising a value may run on would"
                + " offer a tester something no field of this provider accepts")
        .doesNotContain(whatTheClauseSays);
    assertThat(get("/ui/repro-bundle?provider=grow&testRunId=" + RUN))
        .as("the other import screen draws the same box under the same rule")
        .doesNotContain(whatTheClauseSays);
    assertThat(get("/ui/repro-bundle?provider=azericard&testRunId=" + RUN))
        .contains(whatTheClauseSays);
  }

  @Test
  void a_second_secret_mistyped_under_a_whole_key_is_refused_and_never_stored_inside_that_key() {
    String document = theDocumentOn(get(SCREEN + "?provider=azericard&testRunId=" + RUN));

    String reported =
        pasteInto(
            INTO,
            document,
            MERCHANT
                + ".apiSecret="
                + aPrivateKeyInPem()
                + "\n"
                + MERCHANT
                + "_apiKey=-----BEGIN PUBLIC KEY-----");

    assertThat(reported)
        .as(
            "the key ended on the line azericard names it ends with, so the mistyped address"
                + " under it is a line this box cannot read: welding it on would store one"
                + " secret inside another and report the import as applied")
        .contains(RunBundleScreen.A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE)
        .doesNotContain("CREATED");
  }

  @Test
  void the_same_key_with_a_line_the_box_cannot_read_under_it_is_refused_by_that_line() {
    String document = theDocumentOn(get(SCREEN + "?provider=azericard&testRunId=" + RUN));

    String reported =
        pasteInto(
            INTO,
            document,
            MERCHANT + ".secretKeyNoProviderDeclares=sk_one\nsk_two_on_a_line_of_its_own");

    assertThat(reported)
        .as(
            "only a key its provider draws as a block runs on, so a line under a key no provider"
                + " declares is the typo it looks like")
        .contains(RunBundleScreen.A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE);
    assertThat(reported)
        .as("nothing was written into the run this import named")
        .doesNotContain("CREATED");
  }

  private String pasteInto(String into, String document, String secrets) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "azericard");
    form.put("testRunId", RUN);
    form.put("into", into);
    form.put("document", document);
    form.put("secrets", secrets);
    return submit(SCREEN + "/import", form);
  }

  private void configureTheTerminalOf(String run) {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put("terminalId", TERMINAL);
    configuration.put("apiKey", pem("PUBLIC KEY", MERCHANT_KEYS.getPublic().getEncoded()));
    configuration.put("apiSecret", pem("PRIVATE KEY", GATEWAY_KEYS.getPrivate().getEncoded()));
    ResponseEntity<String> applied =
        exchange(
            HttpMethod.PUT,
            "/__simulator/azericard/config",
            Map.of(
                "testRunId",
                run,
                "clock",
                Map.of(),
                "accounts",
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                List.of()));
    assertThat(applied.getStatusCode().value()).as(applied.getBody()).isEqualTo(200);
  }

  private String aPrivateKeyInPem() {
    return pem("PRIVATE KEY", GATEWAY_KEYS.getPrivate().getEncoded()).strip();
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

  private static KeyPair rsa() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }

  private void clearBothRuns() {
    forgetEverythingOf(RUN);
    forgetEverythingOf(INTO);
  }

  private void forgetEverythingOf(String run) {
    exchange(HttpMethod.POST, "/__simulator/azericard/reset", Map.of("testRunId", run));
  }

  private static String theDocumentOn(String page) {
    Matcher box = THE_DOCUMENT_BOX.matcher(page);
    assertThat(box.find()).isTrue();
    return unescaped(box.group(1));
  }

  private static String unescaped(String html) {
    return html.replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
    return http.method(method)
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private String get(String path) {
    HttpResponse<byte[]> answered =
        send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET());
    assertThat(answered.statusCode()).isEqualTo(200);
    return new String(answered.body(), UTF_8);
  }

  private String submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> answered =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return new String(answered.body(), UTF_8);
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
    StringBuilder body = new StringBuilder();
    form.forEach(
        (name, value) -> {
          if (!body.isEmpty()) {
            body.append('&');
          }
          body.append(URLEncoder.encode(name, UTF_8))
              .append('=')
              .append(URLEncoder.encode(value, UTF_8));
        });
    return body.toString();
  }
}
