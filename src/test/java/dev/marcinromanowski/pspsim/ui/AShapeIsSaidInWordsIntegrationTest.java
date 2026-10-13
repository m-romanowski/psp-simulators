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
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class AShapeIsSaidInWordsIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "ui-shape-in-words-it";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";

  private static final String A_RUN_NAME_CARRYING_A_SPACE = "a run";

  private static final String A_REFUSAL_IS_PRINTED_IN = "<div class=\"refusal\">";

  private static final String NOTHING_WAS_REFUSED = "";

  private static final String THE_SHAPE_A_RUN_IS_NAMED_IN =
      "testRunId must match [A-Za-z0-9][A-Za-z0-9._-]{0,63}, which is at most 64 characters, the"
          + " first a letter or a digit and the rest a letter, a digit, a dot, a hyphen or an"
          + " underscore, for example checkout-smoke";

  @LocalServerPort private int port;

  @Autowired private ContributedScreens contributed;

  private HttpClient wire;

  @BeforeEach
  void connect() {
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Test
  void every_screen_whose_bar_judges_a_run_name_refuses_it_in_the_characters_a_name_admits() {
    Map<String, String> answeredOnTheScreen = new LinkedHashMap<>();
    for (String path : TheRail.everyScreenAFormMayComeBackTo(contributed)) {
      answeredOnTheScreen.put(
          path,
          theRefusalOrNothingOn(
              page(
                  path
                      + "?provider=grow&testRunId="
                      + URLEncoder.encode(A_RUN_NAME_CARRYING_A_SPACE, UTF_8))));
    }

    assertThat(answeredOnTheScreen)
        .as(
            "a floor on the sweep, because a sweep that found no screen at all would otherwise"
                + " pass having measured nothing")
        .hasSizeGreaterThanOrEqualTo(20)
        .allSatisfy(
            (path, answered) ->
                assertThat(answered)
                    .as(
                        "a tester typing a space into the bar of %s reads which characters a run"
                            + " name is made of, and a space is named by none of them. A screen"
                            + " that stopped judging answers nothing here and fails, where it used"
                            + " to drop out of the sweep and leave one screen fewer measured",
                        path)
                    .isNotEqualTo(NOTHING_WAS_REFUSED)
                    .isEqualTo(THE_SHAPE_A_RUN_IS_NAMED_IN)
                    .contains("a letter, a digit, a dot, a hyphen or an underscore")
                    .contains("for example checkout-smoke"));
  }

  @Test
  void a_provider_shape_a_tester_types_into_is_refused_in_the_characters_that_shape_admits() {
    Map<String, String> autra = new LinkedHashMap<>();
    autra.put("provider", "autra");
    autra.put("testRunId", RUN);
    autra.put("accountId", "shape-in-words");
    autra.put("field.clientId", "shape-in-words-client");
    autra.put("field.clientSecret", "shape-in-words-secret");
    autra.put("field.documentId", "123.456.789-09");

    assertThat(theRefusalOn(submit(autra)))
        .as(
            "the box is drawn on the account screen, the refusal comes back on it under the"
                + " label that box is drawn under, and the shape is said in characters rather"
                + " than left as a vendor phrase in Portuguese")
        .contains(
            "document id must match [0-9]{1,32}, which is at most 32 characters, each a digit,"
                + " for example 12345678000190")
        .contains("CPF ou CNPJ, sem mascara");
  }

  @Test
  void a_provider_grammar_the_core_will_not_say_is_still_said_in_that_providers_own_words() {
    Map<String, String> worldline = new LinkedHashMap<>();
    worldline.put("provider", "worldline");
    worldline.put("testRunId", RUN);
    worldline.put("accountId", "shape-in-words");
    worldline.put("field.merchantId", "merchant/1");
    worldline.put("field.apiKey", "shape-in-words-key");
    worldline.put("field.apiSecret", "shape-in-words-secret");

    assertThat(theRefusalOn(submit(worldline)))
        .as(
            "worldline's merchant id admits every character but three, which the core has no"
                + " vocabulary for, so worldline names the three it refuses itself")
        .contains("merchant id (PSPID) must match [^/?#]+")
        .contains("a slash, a question mark or a hash");
  }

  private static String theRefusalOn(String page) {
    String refused = theRefusalOrNothingOn(page);
    assertThat(refused)
        .as("the page came back carrying a refusal")
        .isNotEqualTo(NOTHING_WAS_REFUSED);
    return refused;
  }

  private static String theRefusalOrNothingOn(String page) {
    int opened = page.indexOf(A_REFUSAL_IS_PRINTED_IN);
    if (opened < 0) {
      return NOTHING_WAS_REFUSED;
    }
    return page.substring(
        opened + A_REFUSAL_IS_PRINTED_IN.length(), page.indexOf("</div>", opened));
  }

  private String submit(Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + ACCOUNT_FORM))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    assertThat(response.statusCode())
        .as("a refused post comes back on the form rather than redirecting away from it")
        .isEqualTo(200);
    return new String(response.body(), UTF_8);
  }

  private String page(String uri) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + uri))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(response.statusCode()).as("the screen %s was drawn", uri).isEqualTo(200);
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

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }
}
