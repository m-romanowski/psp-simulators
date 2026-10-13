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

class ARefusedFormOnTheScenariosScreenKeepsWhatWasTypedIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-refused-form-it");

  private static final String SCREEN = "/ui/scenarios";

  private static final String AZERICARD = "azericard";

  private static final String GROW = "grow";

  private static final String A_FLOW_AZERICARD_EXPANDS = "card-add-success";

  private static final String A_FLOW_AZERICARD_CANNOT_EXPRESS = "webhook-out-of-order";

  private static final String NO_WHOLE_NUMBER = "high";

  private static final Pattern THE_FLOW_CHOOSER =
      Pattern.compile("<select id=\"preset\".*?</select>", Pattern.DOTALL);

  private static final Pattern AN_OPTION_OF_THE_CHOOSER =
      Pattern.compile("<option\\s+value=\"([^\"]*)\"\\s*(selected=\"selected\")?", Pattern.DOTALL);

  private static final Pattern THE_FIELDS_OF_ONE_RULE =
      Pattern.compile("<details([^>]*)>\\s*<summary>Show the fields of one rule</summary>");

  private static final Pattern THE_DOCUMENT_BOX =
      Pattern.compile("<textarea[^>]*id=\"document\".*?>(.*?)</textarea>", Pattern.DOTALL);

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
    reset(AZERICARD);
    reset(GROW);
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    reset(AZERICARD);
    reset(GROW);
  }

  @Test
  void a_flow_refused_over_what_was_typed_comes_back_with_that_flow_still_chosen() {
    Answer refused = askForAFlow(A_FLOW_AZERICARD_EXPANDS, NO_WHOLE_NUMBER, null);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theFlowsChosenOn(refused.body()))
        .as(
            "the chooser came back with no option chosen at all, so it showed the first flow of"
                + " the list and a tester who submitted it again asked for a flow they never"
                + " chose")
        .containsExactly(A_FLOW_AZERICARD_EXPANDS);
    assertThat(theValueOfTheBox(refused.body(), "presetPriority"))
        .as("the box the refusal names came back empty, so what was typed into it was lost")
        .isEqualTo(NO_WHOLE_NUMBER);
    assertThat(theValueOfTheBox(refused.body(), "priority"))
        .as(
            "what was typed into the flow form came back in the hand-written rule form, where"
                + " correcting it writes one rule by hand rather than asking for the flow again")
        .isEmpty();
    assertThat(theFieldsOfOneRuleAreOpenOn(refused.body()))
        .as("a flow this screen did not write says nothing about writing a rule by hand")
        .isFalse();
    assertThat(refused.body())
        .contains(WhatTheScenariosScreenCameBackWith.A_READY_MADE_FLOW_WAS_NOT_WRITTEN)
        .contains(WhatTheScenariosScreenCameBackWith.A_BOX_ON_THE_FLOW_FORM_HOLDS_WHAT_WAS_NAMED);
    assertThat(theRulesOf(AZERICARD)).isEmpty();
  }

  @Test
  void a_flow_this_provider_cannot_express_comes_back_beside_the_chooser_that_says_which_can() {
    Answer refused =
        askForAFlow(A_FLOW_AZERICARD_CANNOT_EXPRESS, "", ScenarioScreen.AN_EXPANSION_IS_CONFIRMED);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theFlowsChosenOn(refused.body())).containsExactly(A_FLOW_AZERICARD_CANNOT_EXPRESS);
    assertThat(refused.body())
        .as(
            "the sentence beside it told a tester to find the property named here in the form"
                + " below, and no box of that form is the preset: it is a chooser, and the only"
                + " place that says which flows this provider cannot express")
        .contains(
            WhatTheScenariosScreenCameBackWith
                .THIS_PROVIDER_DOES_NOT_EXPRESS_THE_FLOW_THAT_WAS_CHOSEN);
    assertThat(theChooserOn(refused.body()))
        .as("the chooser beside that sentence says of this very flow that it cannot be expressed")
        .contains(
            A_FLOW_AZERICARD_CANNOT_EXPRESS
                + APresetInWords.THE_CHOOSER_READS
                + APresetInWords.NO_PROVIDER_HERE_EXPRESSES_IT);
    assertThat(theFieldsOfOneRuleAreOpenOn(refused.body())).isFalse();
    assertThat(theRulesOf(AZERICARD)).isEmpty();
  }

  @Test
  void a_rule_refused_by_hand_comes_back_in_the_form_it_was_written_in_and_opens_it() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("priority", NO_WHOLE_NUMBER);
    typed.put("actionType", "FIXED_SUCCESS");

    Answer refused = submit(SCREEN + "/write", AZERICARD, typed);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theValueOfTheBox(refused.body(), "priority")).isEqualTo(NO_WHOLE_NUMBER);
    assertThat(theValueOfTheBox(refused.body(), "presetPriority"))
        .as(
            "a rule refused by hand left what was typed in the ready-made flow form as well, and"
                + " a tester asking for a flow then asked for it under a priority they had typed"
                + " somewhere else")
        .isEmpty();
    assertThat(theFieldsOfOneRuleAreOpenOn(refused.body())).isTrue();
    assertThat(refused.body())
        .contains(WhatTheScenariosScreenCameBackWith.ONE_RULE_WAS_NOT_WRITTEN)
        .contains(
            WhatTheScenariosScreenCameBackWith.A_BOX_ON_THE_HAND_WRITTEN_FORM_NAMES_WHAT_WAS_NAMED);
    assertThat(theFlowsChosenOn(refused.body()))
        .as("no flow was chosen, so the chooser claims none was")
        .isEmpty();
    assertThat(theRulesOf(AZERICARD)).isEmpty();
  }

  @Test
  void a_document_refused_comes_back_in_the_box_it_was_pasted_into() {
    String ofAnotherProvider = "{\"provider\":\"" + GROW + "\",\"rules\":[]}";

    Answer refused = submit(SCREEN + "/import", AZERICARD, Map.of("document", ofAnotherProvider));

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theDocumentBoxOn(refused.body()))
        .as(
            "the box came back empty and a tester had to find and paste the whole document again"
                + " to correct one property of it")
        .isEqualTo(ofAnotherProvider);
    assertThat(refused.body())
        .contains(WhatTheScenariosScreenCameBackWith.THE_RULES_OF_A_DOCUMENT_WERE_NOT_WRITTEN)
        .contains(WhatTheScenariosScreenCameBackWith.THE_PROPERTY_NAMED_IS_A_FIELD_OF_THE_DOCUMENT);
    assertThat(theFieldsOfOneRuleAreOpenOn(refused.body()))
        .as("a document this screen did not take back says nothing about writing a rule by hand")
        .isFalse();
    assertThat(theRulesOf(AZERICARD)).isEmpty();
  }

  @Test
  void
      a_flow_this_provider_expands_is_previewed_with_the_flow_and_the_priority_it_was_asked_with() {
    Answer previewed = askForAFlow(A_FLOW_AZERICARD_EXPANDS, "7", null);

    assertThat(previewed.body())
        .doesNotContain(WhatTheScenariosScreenCameBackWith.A_READY_MADE_FLOW_WAS_NOT_WRITTEN);
    assertThat(theFlowsChosenOn(previewed.body())).containsExactly(A_FLOW_AZERICARD_EXPANDS);
    assertThat(theValueOfTheBox(previewed.body(), "presetPriority")).isEqualTo("7");
    assertThat(theRulesOf(AZERICARD)).isEmpty();
  }

  private Answer askForAFlow(String flow, String priority, @Nullable String confirm) {
    Map<String, String> asked = new LinkedHashMap<>();
    asked.put("preset", flow);
    asked.put("priority", priority);
    if (confirm != null) {
      asked.put("confirm", confirm);
    }
    return submit(SCREEN + "/preset", AZERICARD, asked);
  }

  private static List<String> theFlowsChosenOn(String page) {
    List<String> chosen = new ArrayList<>();
    Matcher option = AN_OPTION_OF_THE_CHOOSER.matcher(theChooserOn(page));
    while (option.find()) {
      if (option.group(2) != null) {
        chosen.add(option.group(1));
      }
    }
    return List.copyOf(chosen);
  }

  private static String theChooserOn(String page) {
    Matcher chooser = THE_FLOW_CHOOSER.matcher(page);
    assertThat(chooser.find()).isTrue();
    return chooser.group();
  }

  private static String theValueOfTheBox(String page, String id) {
    Matcher box =
        Pattern.compile("<input[^>]*\\bid=\"" + id + "\"[^>]*>", Pattern.DOTALL).matcher(page);
    assertThat(box.find()).as("the screen draws a box with the id %s", id).isTrue();
    Matcher value = Pattern.compile("\\bvalue=\"([^\"]*)\"").matcher(box.group());
    return value.find() ? value.group(1) : "";
  }

  private static boolean theFieldsOfOneRuleAreOpenOn(String page) {
    Matcher fields = THE_FIELDS_OF_ONE_RULE.matcher(page);
    assertThat(fields.find()).as("the screen draws the fields of one rule").isTrue();
    return fields.group(1).contains("open");
  }

  private static String theDocumentBoxOn(String page) {
    Matcher box = THE_DOCUMENT_BOX.matcher(page);
    assertThat(box.find()).isTrue();
    return unescaped(box.group(1));
  }

  private static String unescaped(String markup) {
    return markup
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private List<Map<String, Object>> theRulesOf(String provider) {
    return http.get()
        .uri("/__simulator/" + provider + "/scenarios?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private void reset(String provider) {
    http.post()
        .uri("/__simulator/" + provider + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private Answer submit(String path, String provider, Map<String, String> fields) {
    List<String> pairs = new ArrayList<>();
    fields.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + port
                        + path
                        + "?provider="
                        + provider
                        + "&testRunId="
                        + RUN.value()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs)))
            .build();
    HttpResponse<byte[]> response = send(request);
    return new Answer(response.statusCode(), new String(response.body(), UTF_8));
  }

  private HttpResponse<byte[]> send(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private record Answer(int status, String body) {}
}
