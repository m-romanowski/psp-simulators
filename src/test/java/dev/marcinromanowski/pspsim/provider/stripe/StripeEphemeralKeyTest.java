package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class StripeEphemeralKeyTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final Instant AN_HOUR_ON = NOW.plus(Duration.ofHours(1));
  private static final Instant A_MINUTE_BEFORE_THE_HOUR = AN_HOUR_ON.minus(Duration.ofMinutes(1));

  private static final String ACCOUNT = "merchant-1";
  private static final String OTHER_ACCOUNT = "merchant-2";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String OTHER_SECRET_KEY = "sk_test_51Another";
  private static final String THREE_DAYS = "P3D";

  private static final String CUSTOMERS_PATH = "/v1/customers";
  private static final String EPHEMERAL_KEYS_PATH = "/v1/ephemeral_keys";
  private static final String PAYMENT_INTENTS_PATH = "/v1/payment_intents";

  private static final String FIRST_CUSTOMER = "cus_sim_0000000000000001";
  private static final String SECOND_CUSTOMER = "cus_sim_0000000000000003";
  private static final String NO_CUSTOMER_OF_THIS_RUN = "cus_sim_0000000000000099";

  private static final String AN_EMAIL = "payer@example.test";

  private static final String A_CARD = "pm_the_card";

  private static final String IDEMPOTENCY_KEY = "8f1a1e2c-0f1a-4f2b-9c3d-5a6b7c8d9e01";

  private static final String STRIPE_VERSION = "Stripe-Version";
  private static final String A_PUBLISHED_VERSION = "2025-08-27.basil";

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
  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final InMemoryPaymentMethodFixtures seededMethods = new InMemoryPaymentMethodFixtures();

  @Test
  void a_tester_who_mints_a_key_for_a_customer_is_answered_the_object_stripe_publishes_for_one() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response = api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.providerObjectId()).isEqualTo(theOnlyKey().ephemeralKeyId());
    assertThat(bodyOf(response))
        .contains("\"id\":\"" + theOnlyKey().ephemeralKeyId() + "\"")
        .contains("\"object\":\"ephemeral_key\"")
        .contains("\"associated_objects\":[{\"type\":\"customer\",\"id\":\"" + FIRST_CUSTOMER)
        .contains("\"created\":" + NOW.getEpochSecond())
        .contains("\"livemode\":false")
        .contains("\"secret\":\"" + theOnlyKey().secret() + "\"");
  }

  @Test
  void
      the_secret_is_a_key_of_its_own_and_not_the_keys_id_carrying_an_intents_client_secret_suffix() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response = api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(theOnlyKey().secret())
        .as("stripe publishes ek_test_ and an opaque body for a key's secret")
        .matches("ek_sim_[0-9a-f]{32}")
        .doesNotContain(theOnlyKey().ephemeralKeyId())
        .doesNotContain("_secret_");
    assertThat(theOnlyKey().ephemeralKeyId()).matches("ephkey_sim_[0-9a-f]{16}");
    assertThat(bodyOf(response)).contains("\"secret\":\"" + theOnlyKey().secret() + "\"");
  }

  @Test
  void
      the_keys_id_carries_the_stem_stripe_publishes_for_a_key_and_not_the_stem_it_publishes_for_a_secret() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response = api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(theOnlyKey().ephemeralKeyId())
        .as(
            "stripe's object sample publishes ephkey_1Mr6PhLkdIwHu7ix9Al0eucz for a key's id and"
                + " ek_test_ for its secret alone, and a client reads the stem to know which kind"
                + " of object it is holding, so this contract's sim marking replaces stripe's mode"
                + " token and never its stem")
        .matches("ephkey_sim_[0-9a-f]{16}")
        .doesNotStartWith(
            StripeEphemeralKeySecret.THE_KEY_FAMILY_PREFIX_WITH_SIM_WHERE_STRIPE_WRITES_TEST);
    assertThat(bodyOf(response))
        .contains("\"id\":\"" + theOnlyKey().ephemeralKeyId() + "\"")
        .contains("\"object\":\"ephemeral_key\"");
  }

  @Test
  void the_key_expires_one_hour_after_it_was_created_because_that_is_what_this_build_decided() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response = api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    assertThat(CreateEphemeralKey.THE_HOUR_READ_OFF_ONE_PUBLISHED_SAMPLE)
        .isEqualTo(Duration.ofHours(1));
    assertThat(bodyOf(response)).contains("\"expires\":" + AN_HOUR_ON.getEpochSecond());
    assertThat(theOnlyKey().expiresAt()).isEqualTo(AN_HOUR_ON);
  }

  @Test
  void minting_a_key_for_a_customer_no_call_of_this_run_created_is_the_published_refusal() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse response = api(NOW).createEphemeralKey(mintFor(NO_CUSTOMER_OF_THIS_RUN));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"customer\"")
        .doesNotContain(NO_CUSTOMER_OF_THIS_RUN);
    assertThat(ephemeralKeys.all()).isEmpty();
  }

  @Test
  void minting_a_key_for_a_customer_another_account_holds_is_refused_as_a_missing_resource() {
    accounts
        .with(stripeAccount(ACCOUNT, SECRET_KEY))
        .with(stripeAccount(OTHER_ACCOUNT, OTHER_SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response =
        api(NOW)
            .createEphemeralKey(
                new StripeApiCall(
                    "POST",
                    EPHEMERAL_KEYS_PATH,
                    bearer(OTHER_SECRET_KEY),
                    fieldsOf(Map.of("customer", FIRST_CUSTOMER))));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response)).contains("\"code\":\"resource_missing\"");
    assertThat(ephemeralKeys.all()).isEmpty();
  }

  @Test
  void minting_a_key_that_names_no_resource_at_all_is_refused_and_names_the_parameter() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse response =
        api(NOW)
            .createEphemeralKey(
                new StripeApiCall("POST", EPHEMERAL_KEYS_PATH, bearer(SECRET_KEY), Map.of()));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"parameter_missing\"")
        .contains("\"param\":\"customer\"")
        .contains("issuing_card, nonce and verification_session");
    assertThat(ephemeralKeys.all()).isEmpty();
  }

  @Test
  void a_key_is_minted_whether_or_not_stripe_version_is_sent_and_the_trail_shows_it_arrived() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse withoutVersion = api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));
    StripeApiResponse withVersion =
        api(NOW).createEphemeralKey(alsoSending(STRIPE_VERSION, A_PUBLISHED_VERSION));

    assertThat(withoutVersion.httpStatus()).isEqualTo(200);
    assertThat(withVersion.httpStatus()).isEqualTo(200);
    assertThat(ephemeralKeys.all()).hasSize(2);
    assertThat(theMembersOf(withVersion)).isEqualTo(theMembersOf(withoutVersion));
    assertThat(theInboundRowsOf(EPHEMERAL_KEYS_PATH))
        .anySatisfy(
            row ->
                assertThat(row.sanitizedHeaders())
                    .containsEntry(STRIPE_VERSION, List.of(A_PUBLISHED_VERSION)));
  }

  @Test
  void the_secret_is_answered_on_the_wire_and_withheld_from_every_audit_row_of_the_same_call() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response = api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    String secret = theOnlyKey().secret();
    assertThat(bodyOf(response)).contains(secret);
    assertThat(audit.all())
        .filteredOn(row -> row.path().equals(EPHEMERAL_KEYS_PATH))
        .isNotEmpty()
        .allSatisfy(row -> assertThat(everythingWrittenTo(row)).doesNotContain(secret));
    assertThat(theOutboundRowOf(EPHEMERAL_KEYS_PATH).sanitizedBody())
        .containsEntry("secret", List.of(Redaction.REDACTED))
        .containsEntry("associated_objects.id", List.of(FIRST_CUSTOMER));
  }

  @Test
  void the_secret_is_withheld_from_the_key_inspector_on_the_first_request_and_on_its_replay() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    StripeApiResponse first = api(NOW).createEphemeralKey(keyed(mintFor(FIRST_CUSTOMER)));

    StripeApiResponse replay = api(NOW).createEphemeralKey(keyed(mintFor(FIRST_CUSTOMER)));

    String secret = theOnlyKey().secret();
    assertThat(bodyOf(first)).contains(secret);
    assertThat(bodyOf(replay)).contains(secret);
    assertThat(theOnlyExchange().requests())
        .hasSize(2)
        .allSatisfy(
            request ->
                assertThat(request.body())
                    .doesNotContain(secret)
                    .contains("\"secret\":\"" + Redaction.REDACTED + "\""));
  }

  @Test
  void a_short_lived_key_reads_back_the_customer_it_was_minted_for() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    StripeApiResponse response =
        api(A_MINUTE_BEFORE_THE_HOUR)
            .retrieveCustomer(readingAs(theOnlyKey().secret(), FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response))
        .contains("\"id\":\"" + FIRST_CUSTOMER + "\"")
        .contains("\"email\":\"" + AN_EMAIL + "\"");
  }

  @Test
  void a_short_lived_key_lists_the_payment_methods_of_the_customer_it_was_minted_for() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    StripeApiResponse response =
        api(A_MINUTE_BEFORE_THE_HOUR)
            .listCustomerPaymentMethods(
                readingAs(theOnlyKey().secret(), FIRST_CUSTOMER + "/payment_methods"),
                FIRST_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response)).contains("\"object\":\"list\"");
  }

  @Test
  void a_short_lived_key_does_not_read_another_customer_of_the_same_account() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));
    StripeApiResponse second = api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    assertThat(second.providerObjectId()).isEqualTo(SECOND_CUSTOMER);

    StripeApiResponse response =
        api(NOW)
            .retrieveCustomer(readingAs(theOnlyKey().secret(), SECOND_CUSTOMER), SECOND_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(response))
        .contains("minted for another resource")
        .doesNotContain(theOnlyKey().secret());
  }

  @Test
  void a_short_lived_key_reaches_none_of_the_other_routes_this_build_serves() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                new StripeApiCall(
                    "POST",
                    PAYMENT_INTENTS_PATH,
                    bearer(theOnlyKey().secret()),
                    fieldsOf(Map.of("amount", "1099", "currency", "pln"))));

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(response)).contains("not one a short-lived key reaches in this build");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_short_lived_key_stops_reading_its_customer_once_its_expires_moment_has_passed() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    StripeApiResponse response =
        api(AN_HOUR_ON)
            .retrieveCustomer(readingAs(theOnlyKey().secret(), FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(response)).contains("expires moment has passed");
  }

  @Test
  void a_secret_no_key_of_this_simulator_was_minted_under_authenticates_nothing() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response =
        api(NOW)
            .retrieveCustomer(
                readingAs("ek_sim_never_minted_secret_anything", FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(response)).contains("No valid API key provided.");
  }

  @Test
  void a_key_whose_account_the_run_no_longer_configures_reads_nothing_back() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));
    accounts.remove(E2E_RUN, ACCOUNT);

    StripeApiResponse response =
        api(NOW).retrieveCustomer(readingAs(theOnlyKey().secret(), FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(response)).contains("No valid API key provided.");
  }

  @Test
  void a_customer_this_build_has_deleted_still_takes_a_key_because_its_retrieve_still_answers() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(NOW)
        .deleteCustomer(
            new StripeApiCall(
                "DELETE", CUSTOMERS_PATH + "/" + FIRST_CUSTOMER, bearer(SECRET_KEY), Map.of()),
            FIRST_CUSTOMER);

    StripeApiResponse minted = api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));
    StripeApiResponse read =
        api(NOW).retrieveCustomer(readingAs(theOnlyKey().secret(), FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(minted.httpStatus()).isEqualTo(200);
    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(read)).contains("\"deleted\":true");
  }

  @Test
  void a_key_of_one_test_run_authenticates_nothing_in_the_run_a_header_routes_the_call_to() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    accounts.with(StripeDoubles.account(DEFAULT_RUN, ACCOUNT, OTHER_SECRET_KEY, THREE_DAYS));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(NOW).createEphemeralKey(mintFor(FIRST_CUSTOMER));

    StripeApiResponse response =
        api(NOW)
            .retrieveCustomer(
                routedTo(DEFAULT_RUN.value(), readingAs(theOnlyKey().secret(), FIRST_CUSTOMER)),
                FIRST_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(response)).contains("No valid API key provided.");
  }

  @Test
  void a_mint_that_cannot_claim_an_id_gives_up_loudly_rather_than_looping() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    for (int drawn = 2; drawn < 2 + CreateEphemeralKey.ID_ATTEMPTS; drawn++) {
      ephemeralKeys.insertIfAbsent(
          new StripeEphemeralKey(
              E2E_RUN,
              CreateEphemeralKey.ID_PREFIX + String.format("%016x", drawn),
              ACCOUNT,
              FIRST_CUSTOMER,
              "a-secret-nothing-else-holds-" + drawn,
              NOW,
              AN_HOUR_ON));
    }

    StripeApi api = api(NOW);
    StripeApiCall minting = mintFor(FIRST_CUSTOMER);

    assertThatThrownBy(() -> api.createEphemeralKey(minting))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(CreateEphemeralKey.ID_ATTEMPTS));
  }

  private static StripeApiCall routedTo(String testRunId, StripeApiCall call) {
    Map<String, List<String>> headers = new LinkedHashMap<>(call.headers());
    headers.put("X-Simulator-Test-Run-Id", List.of(testRunId));
    return new StripeApiCall(call.method(), call.path(), headers, call.form());
  }

  private StripeEphemeralKey theOnlyKey() {
    assertThat(ephemeralKeys.all()).isNotEmpty();
    return ephemeralKeys.all().get(0);
  }

  private KeyedExchange theOnlyExchange() {
    List<KeyedExchange> found =
        keys.exchanges(new KeyedExchangeQuery(E2E_RUN, KeyedExchangeQuery.FROM_THE_NEWEST, 50))
            .exchanges();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private static List<String> theMembersOf(StripeApiResponse response) {
    List<String> names = new ArrayList<>();
    Matcher members = Pattern.compile("\"([a-z_]+)\":").matcher(bodyOf(response));
    while (members.find()) {
      names.add(members.group(1));
    }
    return List.copyOf(names);
  }

  private List<AuditRecord> theInboundRowsOf(String path) {
    return audit.all().stream()
        .filter(row -> row.path().equals(path) && row.direction() == AuditDirection.INBOUND_REQUEST)
        .toList();
  }

  private AuditRecord theOutboundRowOf(String path) {
    List<AuditRecord> found =
        audit.all().stream()
            .filter(
                row ->
                    row.path().equals(path) && row.direction() == AuditDirection.OUTBOUND_RESPONSE)
            .toList();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private static String everythingWrittenTo(AuditRecord row) {
    return row.sanitizedHeaders() + " " + row.sanitizedBody();
  }

  private StripeAccount stripeAccount(String accountId, String secretKey) {
    return StripeDoubles.account(E2E_RUN, accountId, secretKey, THREE_DAYS);
  }

  private static Map<String, String> withEmail(String email) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("email", email);
    return form;
  }

  private static StripeApiCall post(String path, Map<String, String> form) {
    return new StripeApiCall("POST", path, bearer(SECRET_KEY), fieldsOf(form));
  }

  private static StripeApiCall mintFor(String customerId) {
    return new StripeApiCall(
        "POST", EPHEMERAL_KEYS_PATH, bearer(SECRET_KEY), fieldsOf(Map.of("customer", customerId)));
  }

  private static StripeApiCall alsoSending(String header, String value) {
    Map<String, List<String>> headers = new LinkedHashMap<>(bearer(SECRET_KEY));
    headers.put(header, List.of(value));
    return new StripeApiCall(
        "POST", EPHEMERAL_KEYS_PATH, headers, fieldsOf(Map.of("customer", FIRST_CUSTOMER)));
  }

  private static StripeApiCall readingAs(String secret, String underCustomers) {
    return new StripeApiCall(
        "GET", CUSTOMERS_PATH + "/" + underCustomers, bearer(secret), Map.of());
  }

  private static StripeApiCall keyed(StripeApiCall call) {
    Map<String, List<String>> headers = new LinkedHashMap<>(call.headers());
    headers.put("Idempotency-Key", List.of(IDEMPOTENCY_KEY));
    return new StripeApiCall(call.method(), call.path(), headers, call.form());
  }

  @Test
  void a_payment_method_seeded_under_the_prefix_an_ephemeral_key_is_minted_with_is_refused() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    ProviderFixtureRejectedException refused =
        catchThrowableOfType(
            ProviderFixtureRejectedException.class,
            () ->
                provider()
                    .seed(
                        aFixtureOf(
                            Map.of(
                                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                                CreateEphemeralKey.ID_PREFIX + "stolen"))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.PAYMENT_METHOD_PROPERTY);
    assertThat(refused)
        .hasMessage(StripeFixtureCards.A_PAYMENT_METHOD_ID_IS_NOT_A_MINTED_OBJECT_ID);
  }

  @Test
  void a_customer_seeded_under_the_prefix_an_ephemeral_key_is_minted_with_is_refused() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    ProviderFixtureRejectedException refused =
        catchThrowableOfType(
            ProviderFixtureRejectedException.class,
            () ->
                provider()
                    .seed(
                        aFixtureOf(
                            Map.of(
                                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                                A_CARD,
                                ProviderPaymentMethodFields.CUSTOMER_ID,
                                CreateEphemeralKey.ID_PREFIX + "stolen"))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.CUSTOMER_PROPERTY);
    assertThat(refused).hasMessage(StripeFixtureCards.A_CUSTOMER_ID_IS_NOT_A_MINTED_OBJECT_ID);
  }

  private static NewPaymentMethodFixture aFixtureOf(Map<String, String> providerIds) {
    return new NewPaymentMethodFixture(
        StripeApi.STRIPE,
        E2E_RUN,
        ACCOUNT,
        "owner-1",
        PaymentMethodKind.CARD,
        "a-card",
        new FixtureCard("visa", "4242", 12, 2030, "credit"),
        new LinkedHashMap<>(providerIds),
        null,
        ObjectOrigin.UI);
  }

  private SimulatorProvider provider() {
    return new StripeProvider(
        accounts,
        intents,
        charges,
        refunds,
        customers,
        paymentMethods,
        setupIntents,
        ephemeralKeys,
        keys,
        audit,
        seededMethods,
        scenarioRules,
        StripeDoubles.heldResponses(),
        StripeDoubles.clockAt(NOW),
        StripeDoubles.noDeliveries(accounts),
        new StripeWebhooks(
            intents,
            refunds,
            customers,
            paymentMethods,
            setupIntents,
            accounts,
            StripeDoubles.noDeliveries(accounts),
            StripeDoubles.clockAt(NOW),
            ids),
        new CapturePaymentIntent(intents, scenarioEngine),
        new CancelPaymentIntent(intents, scenarioEngine),
        new CreateRefund(intents, refunds, charges, ids, scenarioEngine),
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            scenarioEngine,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine));
  }

  private StripeApi api(Instant now) {
    ScenarioEngine noScenarios = new ScenarioEngine(new InMemoryScenarioRules());
    return new StripeApi(
        accounts,
        keys,
        ephemeralKeys,
        testRuns,
        audit,
        StripeDoubles.clockAt(now),
        ids,
        DEFAULT_RUN,
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            noScenarios,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new ConfirmPaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            noScenarios,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CapturePaymentIntent(intents, noScenarios),
        new CancelPaymentIntent(intents, noScenarios),
        new RetrievePaymentIntent(intents),
        new RetrieveCharge(charges, intents),
        new CreateRefund(intents, refunds, charges, ids, noScenarios),
        customers,
        paymentMethods,
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, noScenarios));
  }
}
