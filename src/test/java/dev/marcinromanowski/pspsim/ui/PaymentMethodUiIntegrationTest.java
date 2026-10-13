package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.RunBundles;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class PaymentMethodUiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "payment-method-ui-it";

  private static final String SCREEN = "/ui/payment-methods";

  private static final String PAYMENT_METHOD_ID = "pm_seeded_from_a_screen";

  private static final Pattern A_FIXTURE_ID = Pattern.compile("fx_[0-9a-f]+");

  @LocalServerPort private int port;

  @Autowired private ProviderRegistry providers;

  private HttpClient wire;
  private RestClient http;

  @BeforeEach
  void connect() {
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    http = RestClient.create("http://localhost:" + port);
    configureTheAccountAScreenSeedsAgainst();
  }

  private void configureTheAccountAScreenSeedsAgainst() {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN,
                "clock",
                Map.of(),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        "merchant-1",
                        "configuration",
                        Map.of(
                            "secretKey", "sk_test_51SeededFromAScreen", "captureWindow", "P3D"))),
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  @AfterEach
  void clearWhatThisTestSeeded() {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity();
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN,
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity();
  }

  @Test
  void a_test_run_that_has_seeded_nothing_says_so_rather_than_showing_an_empty_table() {
    assertThat(screen()).contains("This test run has seeded no payment method");
  }

  @Test
  void a_tester_seeds_a_card_from_the_screen_and_sees_it_listed_with_its_masked_card() {
    HttpResponse<byte[]> seeded = post(SCREEN + "/seed", aVisaCard("visa-success"));

    assertThat(seeded.statusCode()).isEqualTo(302);
    assertThat(screen())
        .contains("visa-success")
        .contains("**** 1111")
        .contains("12/2030")
        .contains("customer-123")
        .contains(PAYMENT_METHOD_ID);
  }

  @Test
  void the_screen_draws_the_provider_native_ids_this_provider_declares_and_says_why_for_the_rest() {
    String page = screen();

    assertThat(page)
        .contains(">payment_method</span>")
        .contains("The id a PaymentIntent of this account is created or confirmed with.")
        .contains(">customer</span>")
        .contains("The id a PaymentIntent is created with.")
        .contains("no Token object");
  }

  @Test
  void no_box_of_this_screen_hints_the_key_its_provider_reads_the_value_under() {
    List<String> swept = new ArrayList<>();
    for (String provider : providers.names()) {
      String page = page(SCREEN + "?provider=" + provider + "&testRunId=" + RUN);
      for (ConfigurableField declared :
          providers.find(provider).orElseThrow().paymentMethodFields().fields()) {
        swept.add(provider + "." + declared.key());
        assertThat(page)
            .as(
                "the box provider %s draws for %s hints the example that provider declares",
                provider, declared.key())
            .contains("placeholder=\"" + declared.example() + "\"");
        assertThat(page)
            .as(
                "and never the key %s reads the value under, which is a developer's name for the"
                    + " field rather than a value a tester could type",
                provider)
            .doesNotContain("placeholder=\"" + declared.key() + "\"");
      }
    }

    assertThat(swept)
        .as("every provider-native id box this build draws on this screen, across every provider")
        .hasSize(7)
        .contains("stripe.paymentMethodId", "azericard.token", "tranzila.token", "worldline.token");
  }

  @Test
  void the_two_boxes_stripe_draws_hint_values_this_provider_seeds_a_card_under() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN);
    form.put("merchantId", "merchant-1");
    form.put("ownerId", "customer-123");
    form.put("type", "card");
    form.put("alias", "visa-from-the-hints");
    form.put("brand", "visa");
    form.put("last4", "4242");
    form.put("expMonth", "12");
    form.put("expYear", "2030");
    form.put("funding", "credit");
    for (ConfigurableField declared :
        providers.find("stripe").orElseThrow().paymentMethodFields().fields()) {
      form.put("providerId." + declared.key(), String.valueOf(declared.example()));
    }

    assertThat(post(SCREEN + "/seed", form).statusCode()).isIn(200, 302);
    assertThat(page(SCREEN + "?provider=stripe&testRunId=" + RUN))
        .as(
            "a tester who types what both boxes hint has seeded a card, so neither hint is a"
                + " value this provider would turn them away for")
        .contains("visa-from-the-hints");
  }

  @Test
  void the_form_marks_every_field_the_seed_refuses_it_without_and_marks_nothing_on_the_rest() {
    String form = screen();

    assertThat(form)
        .as(
            "the grammar refuses merchantId, ownerId, type and alias always, the five card"
                + " properties while the type describes a card, and stripe requires"
                + " providerIds.paymentMethodId: ten of the twelve boxes this form draws")
        .containsOnlyOnce("10 of the 12 fields below are required and say so");
    assertThat(theRequiredBadgesOf(form)).hasSize(10);
    assertThat(form)
        .as(
            "a box with no badge is one nothing refuses; before this the ten boxes this form"
                + " hardcodes carried no badge at all while the seed refused nine of them")
        .doesNotContain(">optional<");
  }

  @Test
  void a_field_the_form_marks_required_is_one_the_seed_actually_refuses_it_without() {
    Map<String, String> missingTheAccount = aVisaCard("visa-success");
    missingTheAccount.put("merchantId", "");

    HttpResponse<byte[]> refused = post(SCREEN + "/seed", missingTheAccount);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(new String(refused.body(), UTF_8)).contains("merchantId is required");
  }

  @Test
  void a_form_refused_for_a_wallet_comes_back_asking_for_no_card_at_all() {
    Map<String, String> aWalletWithNoOwner = aVisaCard("a-wallet");
    aWalletWithNoOwner.put("type", "wallet");
    aWalletWithNoOwner.put("ownerId", "");

    HttpResponse<byte[]> refused = post(SCREEN + "/seed", aWalletWithNoOwner);
    String form = new String(refused.body(), UTF_8);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(theRequiredBadgesOf(form))
        .as("a wallet carries no card, and a type that describes none is refused for carrying one")
        .hasSize(5);
    assertThat(form).containsOnlyOnce("5 of the 12 fields below are required and say so");
  }

  private static List<String> theRequiredBadgesOf(String form) {
    List<String> badges = new ArrayList<>();
    Matcher marked = Pattern.compile("class=\"tag fact\">([a-z]+)<").matcher(form);
    while (marked.find()) {
      badges.add(marked.group(1));
    }
    return badges;
  }

  @Test
  void the_screen_asks_for_no_card_number_and_no_verification_code() {
    String page = screen();

    assertThat(page).contains("no card number and no verification code");
    assertThat(page).doesNotContain("name=\"pan\"").doesNotContain("name=\"cvc\"");
  }

  @Test
  void a_seed_the_provider_refuses_comes_back_on_the_screen_in_the_providers_own_words() {
    Map<String, String> asking = aVisaCard("visa-success");
    asking.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "token", "tok_seeded_from_a_screen");

    HttpResponse<byte[]> refused = post(SCREEN + "/seed", asking);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(new String(refused.body(), UTF_8)).contains("no Token object");
    assertThat(screen()).contains("This test run has seeded no payment method");
  }

  @Test
  void seeding_one_alias_twice_comes_back_as_the_conflict_the_control_api_answers() {
    post(SCREEN + "/seed", aVisaCard("visa-success"));

    Map<String, String> again = aVisaCard("visa-success");
    again.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", "pm_another");

    HttpResponse<byte[]> conflicting = post(SCREEN + "/seed", again);

    assertThat(conflicting.statusCode()).isEqualTo(200);
    assertThat(new String(conflicting.body(), UTF_8)).contains("already claimed");
  }

  @Test
  void removing_a_seeded_method_without_confirming_it_leaves_the_method_where_it_is() {
    post(SCREEN + "/seed", aVisaCard("visa-success"));
    String fixtureId = theFixtureOnTheScreen();

    Map<String, String> unconfirmed = new LinkedHashMap<>();
    unconfirmed.put("provider", "stripe");
    unconfirmed.put("testRunId", RUN);
    unconfirmed.put("fixtureId", fixtureId);

    HttpResponse<byte[]> answer = post(SCREEN + "/remove", unconfirmed);

    assertThat(answer.statusCode()).isEqualTo(200);
    assertThat(new String(answer.body(), UTF_8)).contains("tick confirm");
    assertThat(screen()).contains("visa-success");
  }

  @Test
  void a_tester_confirms_the_removal_and_the_method_leaves_the_screen() {
    post(SCREEN + "/seed", aVisaCard("visa-success"));
    Map<String, String> confirmed = new LinkedHashMap<>();
    confirmed.put("provider", "stripe");
    confirmed.put("testRunId", RUN);
    confirmed.put("fixtureId", theFixtureOnTheScreen());
    confirmed.put("confirm", "yes");

    HttpResponse<byte[]> answer = post(SCREEN + "/remove", confirmed);

    assertThat(answer.statusCode()).isEqualTo(302);
    assertThat(screen()).contains("This test run has seeded no payment method");
  }

  @Test
  void a_fixture_whose_rule_this_run_still_holds_is_listed_without_the_deleted_rule_marker() {
    String scenarioId = aRuleThisRunHolds();
    post(SCREEN + "/seed", aVisaCardNaming("visa-with-a-rule", scenarioId));

    assertThat(screen())
        .contains(scenarioId)
        .doesNotContain(RunBundles.A_RULE_THIS_RUN_NO_LONGER_HOLDS);
  }

  @Test
  void deleting_the_rule_a_fixture_names_marks_the_row_instead_of_printing_the_id_as_live() {
    String scenarioId = aRuleThisRunHolds();
    post(SCREEN + "/seed", aVisaCardNaming("visa-with-a-rule", scenarioId));

    forgetTheRule(scenarioId);

    assertThat(screen()).contains(scenarioId).contains(RunBundles.A_RULE_THIS_RUN_NO_LONGER_HOLDS);
  }

  private String aRuleThisRunHolds() {
    Map<String, Object> written =
        http.post()
            .uri("/__simulator/stripe/scenarios")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "testRunId",
                    RUN,
                    "priority",
                    50,
                    "match",
                    Map.of("operation", "POST /v1/payment_intents"),
                    "action",
                    Map.of("type", "FIXED_SUCCESS")))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return String.valueOf(written.get("scenarioId"));
  }

  private void forgetTheRule(String scenarioId) {
    http.delete()
        .uri("/__simulator/stripe/scenarios/" + scenarioId + "?testRunId=" + RUN)
        .retrieve()
        .toBodilessEntity();
  }

  private static Map<String, String> aVisaCardNaming(String alias, String scenarioId) {
    Map<String, String> form = aVisaCard(alias);
    form.put("defaultScenario", scenarioId);
    return form;
  }

  @Test
  void the_screen_is_reachable_from_the_navigation_of_every_other_screen() {
    assertThat(screen()).contains(">Payment methods</a");
  }

  private String theFixtureOnTheScreen() {
    Matcher named = A_FIXTURE_ID.matcher(screen());
    assertThat(named.find()).isTrue();
    return named.group();
  }

  private String screen() {
    return page(SCREEN + "?provider=stripe&testRunId=" + RUN);
  }

  private String page(String path) {
    HttpResponse<byte[]> answer =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(answer.statusCode()).isEqualTo(200);
    return new String(answer.body(), UTF_8);
  }

  private static Map<String, String> aVisaCard(String alias) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN);
    form.put("merchantId", "merchant-1");
    form.put("ownerId", "customer-123");
    form.put("type", "card");
    form.put("alias", alias);
    form.put("brand", "visa");
    form.put("last4", "1111");
    form.put("expMonth", "12");
    form.put("expYear", "2030");
    form.put("funding", "credit");
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", PAYMENT_METHOD_ID);
    return form;
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
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
