package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StripeApiVersionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";

  private static final String CREATE_PATH = "/v1/payment_intents";
  private static final String CUSTOMERS_PATH = "/v1/customers";
  private static final String EPHEMERAL_KEYS_PATH = "/v1/ephemeral_keys";

  private static final String VERSION_HEADER = "Stripe-Version";

  private static final String KEY = "6c2d9f0e-3a4b-4c5d-8e9f-0a1b2c3d4e5f";

  private static final String THE_NEWEST_RELEASE = "2026-10-28.endive";
  private static final String THE_JAVA_SDKS_RELEASE = "2026-08-26.dahlia";
  private static final String THE_FIRST_BASIL_RELEASE = "2025-03-31.basil";
  private static final String AN_ACACIA_RELEASE = "2024-09-30.acacia";
  private static final String A_DATE_ONLY_RELEASE = "2024-06-20";
  private static final String THE_IOS_SDKS_RELEASE = "2020-08-27";

  private static final String THE_RANGE =
      THE_FIRST_BASIL_RELEASE + " through " + THE_NEWEST_RELEASE;

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final StripeDoubles.Accounts accounts = new StripeDoubles.Accounts();
  private final StripeDoubles.Runs testRuns = new StripeDoubles.Runs(DEFAULT_RUN);
  private final StripeDoubles.Audit audit = new StripeDoubles.Audit();
  private final StripeDoubles.Intents intents = new StripeDoubles.Intents();
  private final StripeDoubles.Charges charges = new StripeDoubles.Charges(intents);
  private final StripeDoubles.Refunds refunds = new StripeDoubles.Refunds(intents);
  private final StripeDoubles.Customers customers = new StripeDoubles.Customers();
  private final StripeDoubles.PaymentMethods paymentMethods = new StripeDoubles.PaymentMethods();
  private final StripeDoubles.SetupIntents setupIntents =
      new StripeDoubles.SetupIntents(paymentMethods);
  private final StripeDoubles.Keys keys = new StripeDoubles.Keys();
  private final StripeDoubles.EphemeralKeys ephemeralKeys = new StripeDoubles.EphemeralKeys();
  private final StripeDoubles.CountingIds ids = new StripeDoubles.CountingIds();

  @Test
  void a_request_naming_a_version_this_simulator_answers_is_answered_at_that_version() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    StripeApiResponse created =
        api().createPaymentIntent(sending(VERSION_HEADER, THE_JAVA_SDKS_RELEASE));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(created.headers().get(VERSION_HEADER)).containsExactly(THE_JAVA_SDKS_RELEASE);
    assertThat(theOutboundRow().sanitizedHeaders())
        .containsEntry(VERSION_HEADER, List.of(THE_JAVA_SDKS_RELEASE));
  }

  @Test
  void a_request_naming_no_version_is_answered_at_the_default_its_account_names() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    StripeApiResponse created = api().createPaymentIntent(sendingNoVersion());

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(created.headers().get(VERSION_HEADER)).containsExactly(THE_FIRST_BASIL_RELEASE);
  }

  @Test
  void a_request_naming_no_version_of_an_account_naming_no_default_is_answered_at_the_newest() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));

    StripeApiResponse created = api().createPaymentIntent(sendingNoVersion());

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(created.headers().get(VERSION_HEADER)).containsExactly(THE_NEWEST_RELEASE);
  }

  @Test
  void a_blank_stripe_version_is_read_as_one_that_was_never_sent() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    StripeApiResponse created = api().createPaymentIntent(sending(VERSION_HEADER, "  "));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(created.headers().get(VERSION_HEADER)).containsExactly(THE_FIRST_BASIL_RELEASE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"stripe-version", "STRIPE-VERSION", "sTrIpE-vErSiOn"})
  void the_header_is_read_whatever_case_its_name_arrives_in(String name) {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    StripeApiResponse created = api().createPaymentIntent(sending(name, THE_JAVA_SDKS_RELEASE));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(created.headers().get(VERSION_HEADER)).containsExactly(THE_JAVA_SDKS_RELEASE);
  }

  @ParameterizedTest
  @ValueSource(strings = {AN_ACACIA_RELEASE, A_DATE_ONLY_RELEASE, "2026-10-28.preview"})
  void a_published_version_outside_the_range_is_refused_with_an_answer_naming_the_range(
      String outside) {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    StripeApiResponse refused = api().createPaymentIntent(sending(VERSION_HEADER, outside));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"type\":\"invalid_request_error\"")
        .contains("Stripe-Version " + outside + " is a published Stripe API version")
        .contains(THE_RANGE)
        .contains("basil, clover, dahlia and endive")
        .doesNotContain("\"code\"")
        .doesNotContain("\"param\"");
    assertThat(refused.headers()).doesNotHave(aHeaderOfThisNameUnderAnySpelling(VERSION_HEADER));
    assertThat(intents.all()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2025-01-01.zinnia",
        "2026-13-01.endive",
        "2026-11-31.endive",
        "latest",
        "2026-10-28",
        "2026-08-26.DAHLIA"
      })
  void a_value_that_names_no_published_version_is_refused_as_an_invalid_version(String invented) {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    StripeApiResponse refused = api().createPaymentIntent(sending(VERSION_HEADER, invented));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Invalid Stripe API"
                + " version: "
                + invented
                + "\"}}");
    assertThat(refused.headers()).doesNotHave(aHeaderOfThisNameUnderAnySpelling(VERSION_HEADER));
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_published_version_asking_for_a_beta_is_refused_as_one_this_simulator_does_not_serve() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    String withABeta = THE_JAVA_SDKS_RELEASE + "; vipps_preview=v1";

    StripeApiResponse refused = api().createPaymentIntent(sending(VERSION_HEADER, withABeta));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("Stripe-Version " + withABeta + " asks for a beta")
        .contains(THE_RANGE);
  }

  @Test
  void a_refused_version_claims_no_idempotency_key_so_the_corrected_retry_runs() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    StripeApi api = api();

    StripeApiResponse refused =
        api.createPaymentIntent(keyed(sending(VERSION_HEADER, AN_ACACIA_RELEASE)));
    StripeApiResponse corrected =
        api.createPaymentIntent(keyed(sending(VERSION_HEADER, THE_JAVA_SDKS_RELEASE)));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(corrected.httpStatus()).isEqualTo(200);
    assertThat(corrected.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void a_replay_carries_the_version_the_replaying_request_was_answered_at() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    StripeApi api = api();
    api.createPaymentIntent(keyed(sending(VERSION_HEADER, THE_JAVA_SDKS_RELEASE)));

    StripeApiResponse replay = api.createPaymentIntent(keyed(sendingNoVersion()));

    assertThat(replay.headers().get("Idempotent-Replayed")).containsExactly("true");
    assertThat(replay.headers().get(VERSION_HEADER)).containsExactly(THE_FIRST_BASIL_RELEASE);
  }

  @Test
  void a_request_no_account_answers_carries_no_version() {
    StripeApiResponse refused =
        api().createPaymentIntent(sending(VERSION_HEADER, THE_JAVA_SDKS_RELEASE));

    assertThat(refused.httpStatus()).isEqualTo(401);
    assertThat(refused.headers()).doesNotHave(aHeaderOfThisNameUnderAnySpelling(VERSION_HEADER));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-11-25.endive", "2027-03-31.fig", "2026-12-16.preview"})
  void a_dated_release_later_than_the_catalogue_is_refused_as_one_this_build_may_not_know(
      String later) {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    StripeApiResponse refused = api().createPaymentIntent(sending(VERSION_HEADER, later));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(
            "Stripe-Version "
                + later
                + " is dated after 2026-10-28.endive, the newest Stripe API version this"
                + " simulator was built with")
        .contains(THE_RANGE)
        .doesNotContain("Invalid Stripe API version");
  }

  @Test
  void a_later_release_asking_for_a_beta_is_refused_as_asking_for_a_beta() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    String withABeta = "2026-11-25.endive; a_beta=v2";

    StripeApiResponse refused = api().createPaymentIntent(sending(VERSION_HEADER, withABeta));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains(withABeta + " asks for a beta");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2020-03-02", "2020-08-27"})
  void the_secret_key_mints_an_ephemeral_key_at_the_version_a_mobile_sdk_pins(String pinned) {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    String customerId = aCustomer();

    StripeApiResponse minted = mintingAt(customerId, pinned);

    assertThat(minted.httpStatus()).isEqualTo(200);
    assertThat(minted.headers().get(VERSION_HEADER)).containsExactly(pinned);
    assertThat(ephemeralKeys.all()).hasSize(1);
  }

  @Test
  void the_secret_key_minting_at_a_date_only_version_no_mobile_sdk_pins_is_refused() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    String customerId = aCustomer();

    StripeApiResponse refused = mintingAt(customerId, A_DATE_ONLY_RELEASE);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains(THE_RANGE);
    assertThat(ephemeralKeys.all()).isEmpty();
  }

  @Test
  void the_secret_key_reading_a_customer_at_the_version_the_ios_sdk_pins_is_refused() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    String customerId = aCustomer();

    StripeApiResponse read =
        api()
            .retrieveCustomer(
                new StripeApiCall(
                    "GET",
                    CUSTOMERS_PATH + "/" + customerId,
                    headers(SECRET_KEY, VERSION_HEADER, THE_IOS_SDKS_RELEASE),
                    Map.of()),
                customerId);

    assertThat(read.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(read)).contains(THE_RANGE);
  }

  @Test
  void a_form_this_api_cannot_read_is_refused_at_the_version_the_request_was_answered_at() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    Map<String, List<String>> unreadable = new LinkedHashMap<>(aHold());
    unreadable.put("[amount", List.of("1"));

    StripeApiResponse refused =
        api()
            .createPaymentIntent(
                new StripeApiCall(
                    "POST",
                    CREATE_PATH,
                    headers(SECRET_KEY, VERSION_HEADER, THE_JAVA_SDKS_RELEASE),
                    unreadable));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"type\":\"invalid_request_error\"");
    assertThat(refused.headers().get(VERSION_HEADER)).containsExactly(THE_JAVA_SDKS_RELEASE);
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_version_refusal_is_answered_ahead_of_a_form_this_api_cannot_read() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    Map<String, List<String>> unreadable = new LinkedHashMap<>(aHold());
    unreadable.put("[amount", List.of("1"));

    StripeApiResponse refused =
        api()
            .createPaymentIntent(
                new StripeApiCall(
                    "POST",
                    CREATE_PATH,
                    headers(SECRET_KEY, VERSION_HEADER, AN_ACACIA_RELEASE),
                    unreadable));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains(THE_RANGE);
    assertThat(refused.headers()).doesNotHave(aHeaderOfThisNameUnderAnySpelling(VERSION_HEADER));
  }

  @Test
  void a_short_lived_key_is_answered_at_the_version_the_ios_sdk_pins() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    String customerId = aCustomer();
    String shortLived = aShortLivedKeyFor(customerId);

    StripeApiResponse read =
        api()
            .retrieveCustomer(
                new StripeApiCall(
                    "GET",
                    CUSTOMERS_PATH + "/" + customerId,
                    headers(shortLived, VERSION_HEADER, THE_IOS_SDKS_RELEASE),
                    Map.of()),
                customerId);

    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(read.headers().get(VERSION_HEADER)).containsExactly(THE_IOS_SDKS_RELEASE);
  }

  @Test
  void a_short_lived_key_sending_no_version_is_answered_at_its_accounts_default() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    String customerId = aCustomer();
    String shortLived = aShortLivedKeyFor(customerId);

    StripeApiResponse read =
        api()
            .retrieveCustomer(
                new StripeApiCall(
                    "GET", CUSTOMERS_PATH + "/" + customerId, bearer(shortLived), Map.of()),
                customerId);

    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(read.headers().get(VERSION_HEADER)).containsExactly(THE_FIRST_BASIL_RELEASE);
  }

  @Test
  void a_short_lived_key_naming_a_version_no_mobile_sdk_pins_is_refused_like_any_other_request() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);
    String customerId = aCustomer();
    String shortLived = aShortLivedKeyFor(customerId);

    StripeApiResponse read =
        api()
            .retrieveCustomer(
                new StripeApiCall(
                    "GET",
                    CUSTOMERS_PATH + "/" + customerId,
                    headers(shortLived, VERSION_HEADER, A_DATE_ONLY_RELEASE),
                    Map.of()),
                customerId);

    assertThat(read.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(read)).contains(THE_RANGE);
  }

  @Test
  void the_secret_key_naming_the_version_the_ios_sdk_pins_is_refused() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    StripeApiResponse refused =
        api().createPaymentIntent(sending(VERSION_HEADER, THE_IOS_SDKS_RELEASE));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains(THE_RANGE);
  }

  @Test
  void the_trail_records_the_version_a_request_sent_and_the_one_it_was_answered_at() {
    anAccountDefaultingTo(THE_FIRST_BASIL_RELEASE);

    api().createPaymentIntent(sending("stripe-version", THE_JAVA_SDKS_RELEASE));

    AuditRecord inbound = audit.of(StripeApi.STRIPE, E2E_RUN).get(0);
    assertThat(inbound.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(inbound.sanitizedHeaders())
        .containsEntry("stripe-version", List.of(THE_JAVA_SDKS_RELEASE));
    assertThat(theOutboundRow().sanitizedHeaders())
        .containsEntry(VERSION_HEADER, List.of(THE_JAVA_SDKS_RELEASE));
  }

  private AuditRecord theOutboundRow() {
    List<AuditRecord> rows = audit.of(StripeApi.STRIPE, E2E_RUN);
    AuditRecord last = rows.get(rows.size() - 1);
    assertThat(last.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    return last;
  }

  private void anAccountDefaultingTo(String apiVersion) {
    accounts.with(
        StripeDoubles.account(
            E2E_RUN, ACCOUNT, SECRET_KEY, "P3D", Map.of(StripeApiVersion.SETTING, apiVersion)));
  }

  private String aCustomer() {
    StripeApiResponse created =
        api()
            .createCustomer(
                new StripeApiCall("POST", CUSTOMERS_PATH, bearer(SECRET_KEY), Map.of()));
    assertThat(created.httpStatus()).isEqualTo(200);
    return Objects.requireNonNull(created.providerObjectId());
  }

  private String aShortLivedKeyFor(String customerId) {
    StripeApiResponse minted =
        api()
            .createEphemeralKey(
                new StripeApiCall(
                    "POST",
                    EPHEMERAL_KEYS_PATH,
                    headers(SECRET_KEY, VERSION_HEADER, THE_NEWEST_RELEASE),
                    Map.of(StripeFields.CUSTOMER, List.of(customerId))));
    assertThat(minted.httpStatus()).isEqualTo(200);
    return ephemeralKeys.all().get(0).secret();
  }

  private StripeApiResponse mintingAt(String customerId, String version) {
    return api()
        .createEphemeralKey(
            new StripeApiCall(
                "POST",
                EPHEMERAL_KEYS_PATH,
                headers(SECRET_KEY, VERSION_HEADER, version),
                Map.of(StripeFields.CUSTOMER, List.of(customerId))));
  }

  private static StripeApiCall sending(String name, String value) {
    return new StripeApiCall("POST", CREATE_PATH, headers(SECRET_KEY, name, value), aHold());
  }

  private static StripeApiCall sendingNoVersion() {
    return new StripeApiCall("POST", CREATE_PATH, bearer(SECRET_KEY), aHold());
  }

  private static StripeApiCall keyed(StripeApiCall call) {
    Map<String, List<String>> headers = new LinkedHashMap<>(call.headers());
    headers.put("Idempotency-Key", List.of(KEY));
    return new StripeApiCall(call.method(), call.path(), headers, call.form());
  }

  private static Map<String, List<String>> headers(String key, String name, String value) {
    Map<String, List<String>> headers = bearer(key);
    headers.put(name, List.of(value));
    return headers;
  }

  private static Map<String, List<String>> bearer(String key) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Authorization", List.of("Bearer " + key));
    return headers;
  }

  private static Map<String, List<String>> aHold() {
    Map<String, List<String>> form = new LinkedHashMap<>();
    form.put("amount", List.of("1099"));
    form.put("currency", List.of("pln"));
    form.put("capture_method", List.of("manual"));
    form.put("payment_method", List.of("pm_card_visa"));
    form.put("confirm", List.of("true"));
    return form;
  }

  private StripeApi api() {
    return new StripeApi(
        accounts,
        keys,
        ephemeralKeys,
        testRuns,
        audit,
        StripeDoubles.clockAt(NOW),
        ids,
        DEFAULT_RUN,
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            scenarioEngine,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new ConfirmPaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            scenarioEngine,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CapturePaymentIntent(intents, scenarioEngine),
        new CancelPaymentIntent(intents, scenarioEngine),
        new RetrievePaymentIntent(intents),
        new RetrieveCharge(charges, intents),
        new CreateRefund(intents, refunds, charges, ids, scenarioEngine),
        customers,
        paymentMethods,
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(
            setupIntents, paymentMethods, customers, StripeDoubles.noScenarios()));
  }
}
