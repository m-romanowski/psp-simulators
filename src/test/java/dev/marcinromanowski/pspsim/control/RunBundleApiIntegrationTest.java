package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class RunBundleApiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "run-bundle-it";

  private static final String INTO = "run-bundle-it-2";

  private static final String UNTOUCHED = "run-bundle-it-untouched";

  private static final String MERCHANT = "merchant-1";

  private static final String THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON = "sk_test_51ExportedRunKey";

  private static final String THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON = "sk_test_51ImportedRunKey";

  private static final String THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN =
      "whsec_theExportedRunSigningSecret";

  private static final String THE_SIGNING_SECRET_OF_THE_IMPORTED_RUN =
      "whsec_theImportedRunSigningSecret";

  private static final String AN_UNDECLARED_KEY = "smuggledKey";

  private static final String EXPORT = "/__simulator/stripe/run-bundle";

  private static final String IMPORT = "/__simulator/stripe/run-bundle/import";

  private static final Instant PINNED = Instant.parse("2026-09-24T12:00:00Z");

  private static final long HELD_BACK_BY = 900_000L;

  private static final String MERCHANT_HOOK = "https://merchant.example/hooks/stripe";

  private static final String FIXTURE_ALIAS = "visa-success";

  private static final String THE_CARD_THIS_FIXTURE_NAMES = "pm_card_visa";

  private static final String THE_CUSTOMER_BOTH_INSTRUMENTS_NAME = "cus_shared_by_both";

  private static final String
      ONE_UNORDERED_COPY_HANDS_BOTH_INSTRUMENTS_ONE_ORDER_AND_ONE_OF_THEM_DECLARED_THE_OTHER =
          "a tester exports a run, reads the ids an instrument carries and diffs that document"
              + " against the next export to reproduce a failing run, so each instrument names"
              + " its ids in the order it was seeded with them. Map.of and Map.copyOf draw one"
              + " order per key set from the keys' hashes and the salt"
              + " java.util.ImmutableCollections fixes once per JVM, so an unordered copy hands"
              + " both of these instruments the same order although they were declared in"
              + " opposite ones, and one of these two assertions fails on every JVM rather than"
              + " on the unlucky ones";

  @LocalServerPort private int port;

  @Autowired private TestRuns testRuns;
  @Autowired private FixedTimes fixedTimes;
  @Autowired private JdbcClient jdbc;

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
  void the_bundle_a_configured_run_exports_carries_no_value_this_provider_declares_secret() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);

    String body = exportedText(RUN);

    assertThat(body)
        .doesNotContain(THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON)
        .doesNotContain(THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN)
        .doesNotContain("[redacted]");
  }

  @Test
  void an_exported_account_names_the_secret_fields_it_left_out_and_carries_the_plain_ones() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);

    Map<String, Object> account = theOnlyAccountOf(exported(RUN));

    assertThat(account.get("accountId")).isEqualTo(MERCHANT);
    assertThat(theStringsOf(account.get("secretsItDoesNotCarry")))
        .contains("secretKey", "webhookSecret");
    assertThat(asMap(account.get("configuration"))).containsKey("captureWindow");
    assertThat(asMap(account.get("configuration")))
        .doesNotContainKeys("secretKey", "webhookSecret");
  }

  @Test
  void a_bundle_hand_edited_to_carry_a_secret_is_refused_by_the_property_and_not_by_the_value() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    Map<String, Object> bundle = exported(RUN);
    theOnlyAccountOf(bundle)
        .put(
            "configuration",
            Map.of(
                "captureWindow",
                "P3D",
                "secretKey",
                THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON,
                "webhookSecret",
                THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN,
                AN_UNDECLARED_KEY,
                THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON));

    ResponseEntity<String> refused = importing(bundle, INTO, false, Map.of());

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(thePropertiesOf(refused))
        .containsExactlyInAnyOrder(
            "accounts[0].configuration.secretKey",
            "accounts[0].configuration.webhookSecret",
            "accounts[0].configuration." + AN_UNDECLARED_KEY);
    assertThat(refused.getBody())
        .doesNotContain(THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON)
        .doesNotContain(THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN)
        .doesNotContain(THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON);
    assertThat(theAccountsOf(exported(INTO))).isEmpty();
  }

  @Test
  void the_trail_of_a_refused_import_names_the_refused_properties_and_holds_no_secret_of_them() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    Map<String, Object> bundle = exported(RUN);
    theOnlyAccountOf(bundle)
        .put("configuration", Map.of("secretKey", THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON));

    importing(bundle, INTO, false, Map.of());

    String trail = auditText(INTO);
    assertThat(trail).contains("accounts[0].configuration.secretKey");
    assertThat(trail).doesNotContain(THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON);
  }

  @Test
  void a_secret_supplied_for_a_field_this_provider_declares_plainly_is_refused_by_that_field() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);

    ResponseEntity<String> refused =
        importing(exported(RUN), INTO, true, Map.of(MERCHANT, Map.of("captureWindow", "P1D")));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(thePropertiesOf(refused)).containsExactly("secrets." + MERCHANT + ".captureWindow");
  }

  @Test
  void a_secret_supplied_for_an_account_the_bundle_does_not_configure_is_refused_by_that_label() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);

    ResponseEntity<String> refused =
        importing(
            exported(RUN),
            INTO,
            true,
            Map.of("merchant-9", Map.of("secretKey", THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON)));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(thePropertiesOf(refused)).containsExactly("secrets.merchant-9");
    assertThat(refused.getBody()).doesNotContain(THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON);
  }

  @Test
  void a_bundle_written_against_an_account_field_this_build_no_longer_declares_is_refused_by_it() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    Map<String, Object> bundle = exported(RUN);
    List<String> fields =
        new ArrayList<>(theStringsOf(theCompatibilityOf(bundle).get("accountFields")));
    fields.add("aFieldThisBuildNeverHad");
    theCompatibilityOf(bundle).put("accountFields", fields);

    ResponseEntity<String> refused = importing(bundle, INTO, true, Map.of());

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(thePropertiesOf(refused))
        .containsExactly("compatibility.accountFields.aFieldThisBuildNeverHad");
  }

  @Test
  void a_bundle_written_before_this_build_declared_a_term_it_never_used_is_taken_back() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    Map<String, Object> bundle = exported(RUN);
    theCompatibilityOf(bundle).put("accountFields", List.of("secretKey"));

    ResponseEntity<String> taken = importing(bundle, INTO, true, theKeyOfTheNewRun());

    assertThat(taken.getStatusCode().value()).as(taken.getBody()).isEqualTo(200);
  }

  @Test
  void the_bundle_a_run_an_import_created_exports_is_the_bundle_that_import_was_given() {
    aRunWithConfigurationAFixtureARuleAndAScheduledDelivery();
    Map<String, Object> bundle = exported(RUN);
    forgetEverythingOf(RUN);
    importing(bundle, INTO, false, theKeyOfTheNewRun());

    Map<String, Object> again = exported(INTO);

    assertThat(again.get("compatibility")).isEqualTo(bundle.get("compatibility"));
    assertThat(again.get("clock")).isEqualTo(bundle.get("clock"));
    assertThat(again.get("accounts")).isEqualTo(bundle.get("accounts"));
    assertThat(again.get("webhooks")).isEqualTo(bundle.get("webhooks"));
    assertThat(again.get("routeAliases")).isEqualTo(bundle.get("routeAliases"));
    assertThat(again.get("settings")).isEqualTo(bundle.get("settings"));
    assertThat(again.get("fixtures")).isEqualTo(bundle.get("fixtures"));
    assertThat(asMap(again.get("scenarios")).get("rules"))
        .isEqualTo(asMap(bundle.get("scenarios")).get("rules"));
    assertThat(again.get("testRunId")).isEqualTo(INTO);
    assertThat(bundle.get("testRunId")).isEqualTo(RUN);
  }

  @Test
  void a_dry_run_reports_what_it_would_do_and_the_target_run_holds_none_of_it_afterwards() {
    aRunWithConfigurationAFixtureARuleAndAScheduledDelivery();

    Map<String, Object> report =
        theReportOf(importing(exported(RUN), INTO, true, theKeyOfTheNewRun()));

    assertThat(report.get("applied")).isEqualTo(false);
    assertThat(theKindsOf(report))
        .contains("clock", "account", "webhookTarget", "scenarioRule", "fixture");
    assertThat(theAccountsOf(exported(INTO))).isEmpty();
    assertThat(theRulesOf(INTO)).isEmpty();
    assertThat(theFixturesOf(INTO)).isEmpty();
  }

  @Test
  void a_dry_run_and_the_import_that_follows_it_report_the_same_entries() {
    aRunWithConfigurationAFixtureARuleAndAScheduledDelivery();
    Map<String, Object> bundle = exported(RUN);

    Map<String, Object> dry = theReportOf(importing(bundle, INTO, true, theKeyOfTheNewRun()));
    Map<String, Object> applied = theReportOf(importing(bundle, INTO, false, theKeyOfTheNewRun()));

    assertThat(applied.get("applied")).isEqualTo(true);
    assertThat(applied.get("entries")).isEqualTo(dry.get("entries"));
    assertThat(applied.get("created")).isEqualTo(dry.get("created"));
    assertThat(applied.get("updated")).isEqualTo(dry.get("updated"));
    assertThat(applied.get("skipped")).isEqualTo(dry.get("skipped"));
  }

  @Test
  void the_report_of_an_import_names_the_secret_field_it_was_given_and_never_its_value() {
    aRunWithConfigurationAFixtureARuleAndAScheduledDelivery();

    ResponseEntity<String> taken = importing(exported(RUN), INTO, false, theKeyOfTheNewRun());

    assertThat(theStringsOf(theReportOf(taken).get("secretsSupplied")))
        .containsExactlyInAnyOrder(MERCHANT + ".secretKey", MERCHANT + ".webhookSecret");
    assertThat(taken.getBody())
        .doesNotContain(THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON)
        .doesNotContain(THE_SIGNING_SECRET_OF_THE_IMPORTED_RUN);
    assertThat(auditText(INTO))
        .contains(MERCHANT + ".secretKey")
        .doesNotContain(THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON)
        .doesNotContain(THE_SIGNING_SECRET_OF_THE_IMPORTED_RUN);
    assertThat(exportedText(INTO))
        .doesNotContain(THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON)
        .doesNotContain(THE_SIGNING_SECRET_OF_THE_IMPORTED_RUN);
  }

  @Test
  void the_run_a_bundle_is_taken_back_into_shapes_its_deliveries_the_way_the_exported_run_did() {
    aRunWithConfigurationAFixtureARuleAndAScheduledDelivery();
    Map<String, Object> heldInTheExportedRun = theOnlyDeliveryOf(RUN);
    Map<String, Object> bundle = exported(RUN);
    forgetEverythingOf(RUN);

    assertThat(theReportOf(importing(bundle, INTO, false, theKeyOfTheNewRun())).get("applied"))
        .isEqualTo(true);
    scheduleADeliveryFor(anIntentOf(INTO, THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON), INTO);

    Map<String, Object> heldInTheImportedRun = theOnlyDeliveryOf(INTO);
    assertThat(heldInTheImportedRun.get("status")).isEqualTo(heldInTheExportedRun.get("status"));
    assertThat(heldInTheImportedRun.get("eventType"))
        .isEqualTo(heldInTheExportedRun.get("eventType"));
    assertThat(heldInTheImportedRun.get("targetUrl"))
        .isEqualTo(heldInTheExportedRun.get("targetUrl"));
    assertThat(heldInTheImportedRun.get("nextAttemptAt"))
        .isEqualTo(heldInTheExportedRun.get("nextAttemptAt"))
        .isEqualTo(PINNED.plusMillis(HELD_BACK_BY).toString());
    assertThat(heldInTheImportedRun.get("shapedBy")).isNotNull();
    assertThat(heldInTheImportedRun.get("shapedBy"))
        .isNotEqualTo(heldInTheExportedRun.get("shapedBy"));
  }

  @Test
  void the_fixture_a_bundle_carries_triggers_the_rule_this_import_wrote_and_not_the_one_it_left() {
    aRunWithConfigurationAFixtureARuleAndAScheduledDelivery();
    String theRuleTheExportedRunHeld = theOnlyRuleOf(RUN).get("scenarioId").toString();
    Map<String, Object> bundle = exported(RUN);
    forgetEverythingOf(RUN);

    importing(bundle, INTO, false, theKeyOfTheNewRun());

    String theRuleTheImportedRunHolds = theOnlyRuleOf(INTO).get("scenarioId").toString();
    assertThat(theRuleTheImportedRunHolds).isNotEqualTo(theRuleTheExportedRunHeld);
    assertThat(theFixturesOf(INTO))
        .singleElement()
        .satisfies(
            fixture -> {
              assertThat(asMap(fixture).get("alias")).isEqualTo(FIXTURE_ALIAS);
              assertThat(asMap(fixture).get("defaultScenario"))
                  .isEqualTo(theRuleTheImportedRunHolds);
            });
  }

  @Test
  void the_second_import_of_one_bundle_skips_what_the_run_holds_and_writes_the_rules_again() {
    aRunWithConfigurationAFixtureARuleAndAScheduledDelivery();
    Map<String, Object> bundle = exported(RUN);
    forgetEverythingOf(RUN);
    importing(bundle, INTO, false, theKeyOfTheNewRun());

    Map<String, Object> again = theReportOf(importing(bundle, INTO, false, theKeyOfTheNewRun()));

    assertThat(theVerdictsOf(again, "account")).containsExactly("UPDATED");
    assertThat(theVerdictsOf(again, "webhookTarget")).containsExactly("SKIPPED");
    assertThat(theVerdictsOf(again, "clock")).containsExactly("SKIPPED");
    assertThat(theVerdictsOf(again, "fixture")).containsExactly("SKIPPED");
    assertThat(theVerdictsOf(again, "scenarioRule")).containsExactly("CREATED");
    assertThat(theRulesOf(INTO)).hasSize(2);
    assertThat(theFixturesOf(INTO)).hasSize(1);
  }

  @Test
  void an_import_whose_fixture_the_store_finds_claimed_conflicts_and_leaves_nothing_behind() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    seedAFixture(RUN, writeADelayingRule(RUN));
    Map<String, Object> bundle = exported(RUN);
    List<Object> fixtures = new ArrayList<>((List<?>) bundle.get("fixtures"));
    Map<String, Object> twin = new LinkedHashMap<>(asMap(fixtures.getFirst()));
    twin.put("alias", "visa-twin-of-the-first");
    fixtures.add(twin);
    bundle.put("fixtures", fixtures);

    ResponseEntity<String> refused = importing(bundle, UNTOUCHED, false, theKeyOfTheNewRun());

    assertThat(refused.getStatusCode().value()).as(refused.getBody()).isEqualTo(409);
    assertThat(thePropertiesOf(refused)).containsExactly("fixtures[1].providerIds.paymentMethodId");
    assertThat(testRuns.exists(TestRunId.of(UNTOUCHED))).isFalse();
    assertThat(fixedTimes.of(TestRunId.of(UNTOUCHED))).isEmpty();
    assertThat(theRowsOf("simulator_provider_account", UNTOUCHED)).isZero();
    assertThat(theRowsOf("stripe_account", UNTOUCHED)).isZero();
    assertThat(theRowsOf("simulator_webhook_target", UNTOUCHED)).isZero();
    assertThat(theRulesOf(UNTOUCHED)).isEmpty();
    assertThat(theFixturesOf(UNTOUCHED)).isEmpty();
  }

  @Test
  void a_run_whose_rule_narrows_on_a_request_header_cannot_be_exported_and_says_which_header() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("testRunId", RUN);
    rule.put(
        "match",
        Map.of("header", Map.of("Authorization", "Bearer " + THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON)));
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    assertThat(post("/__simulator/stripe/scenarios", rule).getStatusCode().value()).isEqualTo(201);

    ResponseEntity<String> refused = get(EXPORT + "?testRunId=" + RUN);

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(thePropertiesOf(refused)).containsExactly("scenarios.match.header.Authorization");
    assertThat(refused.getBody()).doesNotContain(THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON);
  }

  @Test
  void
      an_import_naming_no_bundle_at_all_is_rejected_rather_than_reported_as_an_import_of_nothing() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("testRunId", INTO);

    ResponseEntity<String> rejected = post(IMPORT, body);
    assertThat(rejected.getStatusCode().value()).isEqualTo(400);
    assertThat(rejected.getBody()).contains("bundle must carry");
  }

  @Test
  void a_provider_this_simulator_does_not_know_answers_not_found_on_both_halves_of_the_bundle() {
    assertThat(get("/__simulator/nosuch/run-bundle?testRunId=" + RUN).getStatusCode().value())
        .isEqualTo(404);
    assertThat(post("/__simulator/nosuch/run-bundle/import", Map.of()).getStatusCode().value())
        .isEqualTo(404);
  }

  private void aRunWithConfigurationAFixtureARuleAndAScheduledDelivery() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    String scenarioId = writeADelayingRule(RUN);
    seedAFixture(RUN, scenarioId);
    scheduleADeliveryFor(anIntentOf(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON), RUN);
  }

  private Map<String, Map<String, String>> theKeyOfTheNewRun() {
    return Map.of(
        MERCHANT,
        Map.of(
            "secretKey",
            THE_KEY_THE_IMPORTED_RUN_ANSWERS_ON,
            "webhookSecret",
            THE_SIGNING_SECRET_OF_THE_IMPORTED_RUN));
  }

  private void configure(String run, String secretKey, String signingSecret) {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put("secretKey", secretKey);
    configuration.put("webhookSecret", signingSecret);
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
    assertThat(applied.getStatusCode().value()).isEqualTo(200);
  }

  private String writeADelayingRule(String run) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("testRunId", run);
    rule.put("action", Map.of("type", "DELIVERY_DELAYED", "delayMs", HELD_BACK_BY));
    ResponseEntity<String> written = post("/__simulator/stripe/scenarios", rule);
    assertThat(written.getStatusCode().value()).isEqualTo(201);
    return asMap(read(written)).get("scenarioId").toString();
  }

  @Test
  void
      two_instruments_seeded_with_the_same_two_ids_in_opposite_orders_are_each_exported_in_the_order_it_was_seeded_with() {
    configure(RUN, THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON, THE_SIGNING_SECRET_OF_THE_EXPORTED_RUN);
    Map<String, String> theCustomerFirst = new LinkedHashMap<>();
    theCustomerFirst.put("customerId", THE_CUSTOMER_BOTH_INSTRUMENTS_NAME);
    theCustomerFirst.put("paymentMethodId", "pm_card_the_customer_first");
    Map<String, String> theMethodFirst = new LinkedHashMap<>();
    theMethodFirst.put("paymentMethodId", "pm_card_the_method_first");
    theMethodFirst.put("customerId", THE_CUSTOMER_BOTH_INSTRUMENTS_NAME);
    seedAFixtureCarrying(RUN, "visa-the-customer-first", theCustomerFirst);
    seedAFixtureCarrying(RUN, "visa-the-method-first", theMethodFirst);

    Map<String, Object> bundle = exported(RUN);

    assertThat(theProviderIdKeysOf(bundle, "visa-the-customer-first"))
        .as(ONE_UNORDERED_COPY_HANDS_BOTH_INSTRUMENTS_ONE_ORDER_AND_ONE_OF_THEM_DECLARED_THE_OTHER)
        .isEqualTo(List.copyOf(theCustomerFirst.keySet()));
    assertThat(theProviderIdKeysOf(bundle, "visa-the-method-first"))
        .as(ONE_UNORDERED_COPY_HANDS_BOTH_INSTRUMENTS_ONE_ORDER_AND_ONE_OF_THEM_DECLARED_THE_OTHER)
        .isEqualTo(List.copyOf(theMethodFirst.keySet()));
  }

  private List<String> theProviderIdKeysOf(Map<String, Object> bundle, String alias) {
    List<?> carried = (List<?>) bundle.get("fixtures");
    assertThat(carried).isNotNull();
    for (Object fixture : carried) {
      Map<String, Object> named = asMap(fixture);
      if (alias.equals(named.get("alias"))) {
        return List.copyOf(asMap(named.get("providerIds")).keySet());
      }
    }
    throw new AssertionError("the exported bundle carries no instrument filed under " + alias);
  }

  private void seedAFixtureCarrying(String run, String alias, Map<String, String> providerIds) {
    Map<String, Object> fixture = new LinkedHashMap<>();
    fixture.put("testRunId", run);
    fixture.put("merchantId", MERCHANT);
    fixture.put("ownerId", "owner-1");
    fixture.put("type", "card");
    fixture.put("alias", alias);
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
    fixture.put("providerIds", providerIds);
    ResponseEntity<String> seeded = post("/__simulator/stripe/fixtures/payment-methods", fixture);
    assertThat(seeded.getStatusCode().value()).as(seeded.getBody()).isEqualTo(201);
  }

  private void seedAFixture(String run, String scenarioId) {
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
    fixture.put("defaultScenario", scenarioId);
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

  private Map<String, Object> theOnlyDeliveryOf(String run) {
    List<?> deliveries = theListOf("/__simulator/stripe/webhooks?testRunId=" + run);
    assertThat(deliveries).hasSize(1);
    return asMap(deliveries.getFirst());
  }

  private Map<String, Object> theOnlyRuleOf(String run) {
    List<?> rules = theRulesOf(run);
    assertThat(rules).hasSize(1);
    return asMap(rules.getFirst());
  }

  private List<?> theRulesOf(String run) {
    return theListOf("/__simulator/stripe/scenarios?testRunId=" + run);
  }

  private List<?> theFixturesOf(String run) {
    return theListOf("/__simulator/stripe/fixtures/payment-methods?testRunId=" + run);
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

  private Map<String, Object> exported(String run) {
    ResponseEntity<String> answered = get(EXPORT + "?testRunId=" + run);
    assertThat(answered.getStatusCode().value()).as(answered.getBody()).isEqualTo(200);
    return asMap(read(answered));
  }

  private String exportedText(String run) {
    ResponseEntity<String> answered = get(EXPORT + "?testRunId=" + run);
    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    return String.valueOf(answered.getBody());
  }

  private ResponseEntity<String> importing(
      Map<String, Object> bundle,
      String into,
      boolean dryRun,
      Map<String, Map<String, String>> secrets) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("testRunId", into);
    body.put("dryRun", dryRun);
    body.put("bundle", bundle);
    body.put("secrets", secrets);
    return post(IMPORT, body);
  }

  private Map<String, Object> theReportOf(ResponseEntity<String> answered) {
    assertThat(answered.getStatusCode().value()).as(answered.getBody()).isIn(200, 201);
    return asMap(read(answered));
  }

  private static List<String> theKindsOf(Map<String, Object> report) {
    List<String> kinds = new ArrayList<>();
    for (Object entry : (List<?>) report.get("entries")) {
      kinds.add(String.valueOf(asMap(entry).get("kind")));
    }
    return kinds;
  }

  private static List<String> theVerdictsOf(Map<String, Object> report, String kind) {
    List<String> verdicts = new ArrayList<>();
    for (Object entry : (List<?>) report.get("entries")) {
      Map<String, Object> named = asMap(entry);
      if (kind.equals(named.get("kind"))) {
        verdicts.add(String.valueOf(named.get("verdict")));
      }
    }
    return verdicts;
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

  private static Map<String, Object> theOnlyAccountOf(Map<String, Object> bundle) {
    List<?> accounts = theAccountsOf(bundle);
    assertThat(accounts).hasSize(1);
    return asMap(accounts.getFirst());
  }

  private static List<?> theAccountsOf(Map<String, Object> bundle) {
    return (List<?>) bundle.get("accounts");
  }

  private static Map<String, Object> theCompatibilityOf(Map<String, Object> bundle) {
    return asMap(bundle.get("compatibility"));
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
    forgetEverythingOf(UNTOUCHED);
    jdbc.sql("delete from simulator_test_run where test_run_id = ?").param(UNTOUCHED).update();
  }

  private int theRowsOf(String table, String run) {
    return jdbc.sql("select count(*) from " + table + " where test_run_id = ?")
        .param(run)
        .query(Integer.class)
        .single();
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
