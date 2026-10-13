package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.io.IOException;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ReproductionBundleApiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "repro-api-it";

  private static final String INTO = "repro-api-it-2";

  private static final String MERCHANT = "merchant-1";

  private static final String THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON = "sk_test_51ReproExportedKey";

  private static final String THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON = "sk_test_51ReproImportedKey";

  private static final String THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN =
      "whsec_theReproExportedSigningSecret";

  private static final String CAPTURE = "/repro-bundle";

  private static final String EXPORT = "/__simulator/stripe" + CAPTURE;

  private static final String IMPORT = EXPORT + "/import";

  private static final Instant PINNED = Instant.parse("2026-09-24T12:00:00Z");

  private static final String MERCHANT_HOOK = "https://merchant.example/hooks/stripe";

  private static final String FIXTURE_ALIAS = "visa-success";

  private static final String THE_CARD_THIS_FIXTURE_NAMES = "pm_card_visa";

  private static final String THE_CARD_A_REMAPPING_NAMES = "pm_card_remapped";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    clearBothRuns();
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    clearBothRuns();
  }

  @Test
  void a_capture_naming_no_payment_is_refused_with_the_property_it_wanted() {
    configure(RUN);

    ResponseEntity<String> answered = get(EXPORT + "?testRunId=" + RUN);

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(answered.getBody())).contains("key must name the provider object");
  }

  @Test
  void a_capture_naming_a_payment_this_run_never_held_is_not_found() {
    configure(RUN);

    ResponseEntity<String> answered = get(EXPORT + "?testRunId=" + RUN + "&key=pi_nothing");

    assertThat(answered.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void the_bundle_of_a_payment_carries_the_setup_the_evidence_and_the_two_content_lists() {
    String intent = aPaymentOf(RUN);

    Map<String, Object> bundle = captured(RUN, intent);

    assertThat(bundle.get("testRunId")).isEqualTo(RUN);
    assertThat(bundle.get("provider")).isEqualTo("stripe");
    assertThat(bundle.get("capturedAt")).isEqualTo(PINNED.toString());
    assertThat(asMap(bundle.get("setup")).get("testRunId")).isEqualTo(RUN);
    assertThat(theEvidenceOf(bundle).get("key")).isEqualTo(intent);
    assertThat(theEvidenceOf(bundle).get("effectiveClock")).isEqualTo(PINNED.toString());
    assertThat(theContentsAnImportWrites(bundle))
        .containsExactly(
            "accounts",
            "webhookTargets",
            "routeAliases",
            "settings",
            "clock",
            "fixtures",
            "scenarioRules");
    assertThat(theContentsNoImportWrites(bundle))
        .containsExactly(
            "stateSnapshot",
            "ledger",
            "trail",
            "providerExchanges",
            "callbacks",
            "callbackAttempts");
  }

  @Test
  void the_bundle_of_a_payment_carries_the_rows_of_that_payment_and_its_state_and_its_callbacks() {
    String intent = aPaymentOf(RUN);
    scheduleADeliveryFor(intent, RUN);

    Map<String, Object> evidence = theEvidenceOf(captured(RUN, intent));

    assertThat((List<?>) evidence.get("trail")).isNotEmpty();
    assertThat(asMap(evidence.get("object")).get("providerObjectId")).isEqualTo(intent);
    assertThat((List<?>) evidence.get("callbacks"))
        .singleElement()
        .satisfies(
            callback -> assertThat(asMap(callback).get("providerObjectId")).isEqualTo(intent));
  }

  @Test
  void the_bundle_of_a_payment_carries_no_value_this_provider_declares_secret() {
    String intent = aPaymentOf(RUN);
    scheduleADeliveryFor(intent, RUN);

    String body = capturedText(RUN, intent);

    assertThat(body)
        .doesNotContain(THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON)
        .doesNotContain(THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
  }

  @Test
  void a_dry_run_reports_what_it_would_write_and_the_target_run_holds_nothing_afterwards() {
    String intent = aPaymentOf(RUN);
    Map<String, Object> bundle = captured(RUN, intent);

    Map<String, Object> report = theReportOf(importing(bundle, INTO, true, theKeyOfTheNewRun()));

    assertThat(asMap(report.get("setup")).get("applied")).isEqualTo(false);
    assertThat(asMap(report.get("setup")).get("created")).isNotNull();
    assertThat(theFixturesOf(INTO)).isEmpty();
    assertThat(theRulesOf(INTO)).isEmpty();
    assertThat(theListOf("/__simulator/stripe/webhooks?testRunId=" + INTO)).isEmpty();
    assertThat(theObjectsOf(INTO)).isEmpty();
  }

  @Test
  void an_import_writes_the_setup_and_no_row_of_the_trail_no_callback_and_no_payment() {
    String intent = aPaymentOf(RUN);
    scheduleADeliveryFor(intent, RUN);
    Map<String, Object> bundle = captured(RUN, intent);

    Map<String, Object> report = theReportOf(importing(bundle, INTO, false, theKeyOfTheNewRun()));

    assertThat(asMap(report.get("setup")).get("applied")).isEqualTo(true);
    assertThat(theFixturesOf(INTO)).hasSize(1);
    assertThat(theListOf("/__simulator/stripe/webhooks?testRunId=" + INTO)).isEmpty();
    assertThat(
            get("/__simulator/stripe/state/" + intent + "?testRunId=" + INTO)
                .getStatusCode()
                .value())
        .isEqualTo(404);
    assertThat(auditText(INTO)).doesNotContain(intent);
  }

  @Test
  void an_import_preserves_the_provider_native_id_the_bundle_carried_when_none_is_remapped() {
    String intent = aPaymentOf(RUN);
    Map<String, Object> bundle = captured(RUN, intent);

    Map<String, Object> report = theReportOf(importing(bundle, INTO, false, theKeyOfTheNewRun()));

    assertThat(report.get("providerNativeIdsPreserved")).isEqualTo(true);
    assertThat(theProviderIdOfTheOnlyFixtureOf(INTO)).isEqualTo(THE_CARD_THIS_FIXTURE_NAMES);
  }

  @Test
  void an_import_asked_to_remap_writes_the_id_it_was_given_and_names_the_key_it_changed() {
    String intent = aPaymentOf(RUN);
    Map<String, Object> bundle = captured(RUN, intent);

    Map<String, Object> report =
        theReportOf(
            importing(
                bundle,
                INTO,
                false,
                theKeyOfTheNewRun(),
                Map.of(THE_CARD_THIS_FIXTURE_NAMES, THE_CARD_A_REMAPPING_NAMES)));

    assertThat(report.get("providerNativeIdsPreserved")).isEqualTo(false);
    assertThat(theStringsOf(report.get("theProviderNativeIdKeysItRemapped")))
        .containsExactly("paymentMethodId");
    assertThat(theProviderIdOfTheOnlyFixtureOf(INTO)).isEqualTo(THE_CARD_A_REMAPPING_NAMES);
  }

  @Test
  void an_id_the_target_run_already_gave_away_conflicts_and_the_run_keeps_the_one_instrument() {
    String intent = aPaymentOf(RUN);
    Map<String, Object> bundle = captured(RUN, intent);
    theReportOf(importing(bundle, INTO, false, theKeyOfTheNewRun()));

    ResponseEntity<String> again =
        importing(theBundleUnderAnotherAlias(bundle), INTO, false, theKeyOfTheNewRun());

    assertThat(again.getStatusCode().value()).isEqualTo(409);
    assertThat(thePropertiesOf(again)).contains("setup.fixtures[0].providerIds.paymentMethodId");
    assertThat(String.valueOf(again.getBody())).doesNotContain(THE_CARD_THIS_FIXTURE_NAMES);
    assertThat(theFixturesOf(INTO)).hasSize(1);
  }

  @Test
  void an_id_the_target_run_holds_is_taken_back_under_a_remapping_that_frees_it() {
    String intent = aPaymentOf(RUN);
    Map<String, Object> bundle = captured(RUN, intent);
    theReportOf(importing(bundle, INTO, false, theKeyOfTheNewRun()));

    theReportOf(
        importing(
            theBundleUnderAnotherAlias(bundle),
            INTO,
            false,
            theKeyOfTheNewRun(),
            Map.of(THE_CARD_THIS_FIXTURE_NAMES, THE_CARD_A_REMAPPING_NAMES)));

    assertThat(theFixturesOf(INTO)).hasSize(2);
  }

  @Test
  void a_remapping_of_an_id_no_instrument_of_the_bundle_carries_is_refused_and_writes_nothing() {
    String intent = aPaymentOf(RUN);
    Map<String, Object> bundle = captured(RUN, intent);

    ResponseEntity<String> refused =
        importing(bundle, INTO, false, theKeyOfTheNewRun(), Map.of("pm_nothing", "pm_other"));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(thePropertiesOf(refused)).containsExactly("remapProviderIds.pm_nothing");
    assertThat(theFixturesOf(INTO)).isEmpty();
  }

  @Test
  void an_import_naming_no_bundle_is_refused_and_the_trail_files_it_under_the_bundle() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("testRunId", INTO);
    body.put("dryRun", false);

    ResponseEntity<String> answered = post(IMPORT, body);

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(auditText(INTO)).contains("repro-bundle/import");
  }

  @Test
  void an_import_of_a_bundle_carrying_no_setup_is_refused_over_the_setup_it_named_none_of() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("testRunId", INTO);
    body.put("bundle", Map.of("testRunId", RUN, "provider", "stripe"));

    ResponseEntity<String> answered = post(IMPORT, body);

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(answered.getBody())).contains("setup must carry");
  }

  @Test
  void a_capture_of_an_unknown_provider_is_not_found_before_anything_else_is_read() {
    assertThat(get("/__simulator/nosuch/repro-bundle?key=pi_1").getStatusCode().value())
        .isEqualTo(404);
    assertThat(post("/__simulator/nosuch/repro-bundle/import", Map.of()).getStatusCode().value())
        .isEqualTo(404);
  }

  private Map<String, Object> theBundleUnderAnotherAlias(Map<String, Object> bundle) {
    Map<String, Object> copied = new LinkedHashMap<>(bundle);
    Map<String, Object> setup = new LinkedHashMap<>(asMap(copied.get("setup")));
    List<Object> fixtures = new ArrayList<>();
    for (Object fixture : (List<?>) setup.get("fixtures")) {
      Map<String, Object> renamed = new LinkedHashMap<>(asMap(fixture));
      renamed.put("alias", FIXTURE_ALIAS + "-again");
      fixtures.add(renamed);
    }
    setup.put("fixtures", fixtures);
    copied.put("setup", setup);
    return copied;
  }

  private Map<String, Map<String, String>> theKeyOfTheNewRun() {
    return Map.of(
        MERCHANT,
        Map.of(
            "secretKey",
            THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON,
            "webhookSecret",
            "whsec_theReproImportedSigningSecret"));
  }

  private void configure(String run) {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put("secretKey", THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON);
    configuration.put("webhookSecret", THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    configuration.put("captureWindow", "P3D");
    ResponseEntity<String> applied =
        put(
            "/__simulator/stripe/config",
            Map.of(
                "testRunId",
                run,
                "clock",
                Map.of("fixedNow", PINNED.toString()),
                "accounts",
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", MERCHANT_HOOK))));
    assertThat(applied.getStatusCode().value()).as(applied.getBody()).isEqualTo(200);
  }

  private String aPaymentOf(String run) {
    configure(run);
    seedAFixture(run);
    return anIntentOf(run, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON);
  }

  private void seedAFixture(String run) {
    Map<String, Object> fixture = new LinkedHashMap<>();
    fixture.put("testRunId", run);
    fixture.put("merchantId", MERCHANT);
    fixture.put("ownerId", "owner-1");
    fixture.put("type", "card");
    fixture.put("alias", FIXTURE_ALIAS);
    fixture.put(
        "card",
        Map.of(
            "brand",
            "visa",
            "last4",
            "4242",
            "expMonth",
            12,
            "expYear",
            2031,
            "funding",
            "credit"));
    fixture.put("providerIds", Map.of("paymentMethodId", THE_CARD_THIS_FIXTURE_NAMES));
    assertThat(
            post("/__simulator/stripe/fixtures/payment-methods", fixture).getStatusCode().value())
        .isEqualTo(201);
  }

  private String anIntentOf(String run, String secretKey) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", THE_CARD_THIS_FIXTURE_NAMES);
    form.put("confirm", "true");
    HttpResponse<byte[]> created =
        sent(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/payment_intents"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + secretKey)
                .header("X-Simulator-Test-Run-Id", run)
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
                .build());
    String body = new String(created.body(), UTF_8);
    assertThat(created.statusCode()).as(body).isEqualTo(200);
    return valueIn(body, "id");
  }

  private void scheduleADeliveryFor(String providerObjectId, String run) {
    Map<String, Object> call = new LinkedHashMap<>();
    call.put("testRunId", run);
    call.put("providerObjectId", providerObjectId);
    ResponseEntity<String> scheduled = post("/__simulator/stripe/webhooks", call);
    assertThat(scheduled.getStatusCode().value()).as(scheduled.getBody()).isEqualTo(201);
  }

  private Map<String, Object> captured(String run, String key) {
    ResponseEntity<String> answered = get(EXPORT + "?testRunId=" + run + "&key=" + key);
    assertThat(answered.getStatusCode().value()).as(answered.getBody()).isEqualTo(200);
    return asMap(read(answered));
  }

  private String capturedText(String run, String key) {
    ResponseEntity<String> answered = get(EXPORT + "?testRunId=" + run + "&key=" + key);
    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    return String.valueOf(answered.getBody());
  }

  private static Map<String, Object> theEvidenceOf(Map<String, Object> bundle) {
    return asMap(bundle.get("evidence"));
  }

  private static List<String> theContentsAnImportWrites(Map<String, Object> bundle) {
    return theContentNamesOf(bundle, "replayable");
  }

  private static List<String> theContentsNoImportWrites(Map<String, Object> bundle) {
    return theContentNamesOf(bundle, "readable");
  }

  private static List<String> theContentNamesOf(Map<String, Object> bundle, String half) {
    List<String> named = new ArrayList<>();
    for (Object content : (List<?>) asMap(bundle.get("contents")).get(half)) {
      named.add(String.valueOf(asMap(content).get("content")));
    }
    return named;
  }

  private String theProviderIdOfTheOnlyFixtureOf(String run) {
    List<?> seeded = theFixturesOf(run);
    assertThat(seeded).hasSize(1);
    return String.valueOf(
        asMap(asMap(seeded.getFirst()).get("providerObjects")).get("paymentMethodId"));
  }

  private List<?> theFixturesOf(String run) {
    return theListOf("/__simulator/stripe/fixtures/payment-methods?testRunId=" + run);
  }

  private List<?> theRulesOf(String run) {
    return theListOf("/__simulator/stripe/scenarios?testRunId=" + run);
  }

  private List<?> theObjectsOf(String run) {
    return theListOf("/__simulator/stripe/objects?testRunId=" + run);
  }

  private List<?> theListOf(String path) {
    ResponseEntity<String> answered = get(path);
    assertThat(answered.getStatusCode().value()).as(answered.getBody()).isEqualTo(200);
    return (List<?>) read(answered);
  }

  private String auditText(String run) {
    ResponseEntity<String> answered = get("/__simulator/stripe/audit?testRunId=" + run);
    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    return String.valueOf(answered.getBody());
  }

  private ResponseEntity<String> importing(
      Map<String, Object> bundle,
      String into,
      boolean dryRun,
      Map<String, Map<String, String>> secrets) {
    return importing(bundle, into, dryRun, secrets, Map.of());
  }

  private ResponseEntity<String> importing(
      Map<String, Object> bundle,
      String into,
      boolean dryRun,
      Map<String, Map<String, String>> secrets,
      Map<String, String> remapProviderIds) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("testRunId", into);
    body.put("dryRun", dryRun);
    body.put("bundle", bundle);
    body.put("secrets", secrets);
    body.put("remapProviderIds", remapProviderIds);
    return post(IMPORT, body);
  }

  private Map<String, Object> theReportOf(ResponseEntity<String> answered) {
    assertThat(answered.getStatusCode().value()).as(answered.getBody()).isIn(200, 201);
    return asMap(read(answered));
  }

  private List<String> thePropertiesOf(ResponseEntity<String> answered) {
    return theStringsOf(asMap(read(answered)).get("properties"));
  }

  private static List<String> theStringsOf(Object values) {
    List<String> named = new ArrayList<>();
    for (Object value : (List<?>) values) {
      named.add(String.valueOf(value));
    }
    return named;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  private Object read(ResponseEntity<String> answered) {
    return json.readValue(String.valueOf(answered.getBody()), Object.class);
  }

  private ResponseEntity<String> get(String path) {
    return http.get()
        .uri(path)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private ResponseEntity<String> post(String path, Object body) {
    return exchange(HttpMethod.POST, path, body);
  }

  private ResponseEntity<String> put(String path, Object body) {
    return exchange(HttpMethod.PUT, path, body);
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

  private void clearBothRuns() {
    forgetEverythingOf(RUN);
    forgetEverythingOf(INTO);
  }

  private void forgetEverythingOf(String run) {
    post("/__simulator/stripe/reset", Map.of("testRunId", run));
    Map<String, Object> released = new LinkedHashMap<>();
    released.put("testRunId", run);
    released.put("clock", Map.of());
    put("/__simulator/stripe/config", released);
    http.method(HttpMethod.DELETE)
        .uri("/__simulator/stripe/config/accounts/" + MERCHANT + "?testRunId=" + run)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toBodilessEntity();
  }

  private HttpResponse<byte[]> sent(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofByteArray());
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

  private static String valueIn(String body, String member) {
    String opening = "\"" + member + "\":\"";
    int start = body.indexOf(opening);
    int from = start + opening.length();
    return body.substring(from, body.indexOf('"', from));
  }
}
