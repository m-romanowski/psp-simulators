package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredWebhookCall;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAStartedPaymentLeaves;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class RefusedScreenActionsIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "refused-screen-actions-it";

  private static final TestRunId TEST_RUN = TestRunId.of(RUN);

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final String ACT = "/ui/objects/act";

  private static final String COMPLETE = "/ui/objects/complete";

  private static final String WEBHOOK = "/ui/objects/webhook";

  private static final String START = "/ui/objects/start";

  private static final String SEND = "/ui/webhooks/send";

  private static final String RETRY = "/ui/webhooks/retry";

  private static final String DELIVER_DUE = "/ui/webhooks/deliver-due";

  private static final String WRITE = "/ui/scenarios/write";

  private static final String PRESET = "/ui/scenarios/preset";

  private static final String IMPORT = "/ui/scenarios/import";

  private static final String DELETE = "/ui/scenarios/delete";

  private static final String CLOCK = "/ui/configuration/clock";

  private static final String ROUTE_ALIAS = "/ui/configuration/route-alias";

  private static final String SETTINGS = "/ui/configuration/settings";

  private static final String RELEASE = "/ui/configuration/route-alias/release";

  private static final String FORGET = "/ui/configuration/account/remove";

  private static final String ACCOUNT = "/ui/configuration/account";

  private static final String SEED = "/ui/payment-methods/seed";

  private static final String REMOVE = "/ui/payment-methods/remove";

  private static final String THE_ACTION =
      ObjectScreen.THE_ACTION_IT_ASKED_THIS_PROVIDER_TO_PERFORM;

  private static final String THE_COMPLETION =
      HostedCompletionOutcome.THE_COMPLETION_IT_ASKED_THIS_PROVIDER_TO_PERFORM;

  private static final String THE_CALLBACK =
      DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE;

  private static final String THE_PAYMENT =
      TheTrailRowAStartedPaymentLeaves.THE_PAYMENT_IT_ASKED_THIS_PROVIDER_TO_START;

  private static final String THE_RULE_TO_HOLD =
      ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD;

  private static final String THE_RULE_TO_FORGET =
      ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_FORGET;

  private static final String THE_CONFIGURATION =
      ConfigurationAdmission.THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD;

  private static final String THE_ACCOUNT =
      ConfigurationAdmission.THE_ACCOUNT_IT_ASKED_THIS_PROVIDER_TO_FORGET;

  private static final String THE_ROUTE_ALIAS =
      ConfigurationAdmission.THE_ROUTE_ALIAS_IT_ASKED_THIS_PROVIDER_TO_RELEASE;

  private static final String THE_FIXTURE_TO_SEED =
      PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED;

  private static final String THE_FIXTURE_TO_FORGET =
      PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_FORGET;

  private static final String A_PUBLISHED_CARD = "sandbox-4580";

  private static final Pattern A_GROW_PROCESS_ID = Pattern.compile("started=([0-9]{6})");

  @LocalServerPort private int port;

  @Autowired private AuditTrail trail;

  @Autowired private ProviderRegistry providers;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void anEmptyRunWithOneAccount() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    reset();
    configureMerchantOne();
  }

  static Stream<Arguments> theRefusingPathsOfTheScreens() {
    return Stream.of(
        Arguments.of(
            "an action on a payment the tester did not confirm",
            ACT,
            Map.of("action", "CAPTURE", "providerObjectId", "trx_1"),
            400,
            RefusalSubject.noObjectWasNamed(THE_ACTION)),
        Arguments.of(
            "an action this simulator has no name for",
            ACT,
            Map.of("action", "", "providerObjectId", "trx_1", "confirm", "yes"),
            400,
            RefusalSubject.noObjectWasNamed(THE_ACTION)),
        Arguments.of(
            "an action over an id this test run holds no object under",
            ACT,
            Map.of(
                "action",
                "CAPTURE",
                "providerObjectId",
                "trx_nothing",
                "amount",
                "10.00",
                "confirm",
                "yes"),
            404,
            RefusalSubject.noObjectAnsweredTo(ObjectScreen.PROVIDER_OBJECT_ID)),
        Arguments.of(
            "a payer step asked of an object this provider leaves none on",
            COMPLETE,
            Map.of("providerObjectId", "pp_1", "outcome", "PAID"),
            422,
            RefusalSubject.noObjectWasNamed(THE_COMPLETION)),
        Arguments.of(
            "a callback the tester did not confirm sending",
            WEBHOOK,
            Map.of("providerObjectId", "pp_1", "eventType", "PAID"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CALLBACK)),
        Arguments.of(
            "a callback naming no object at all",
            WEBHOOK,
            Map.of("providerObjectId", "", "confirm", "yes"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CALLBACK)),
        Arguments.of(
            "a composed callback naming no object at all",
            SEND,
            Map.of("providerObjectId", ""),
            400,
            RefusalSubject.noObjectWasNamed(THE_CALLBACK)),
        Arguments.of(
            "a composed callback whose delay is not a whole number of milliseconds",
            SEND,
            Map.of("providerObjectId", "pp_1", "delayMs", "as soon as possible"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CALLBACK)),
        Arguments.of(
            "a retry posted to an address this test run does not configure",
            RETRY,
            Map.of("deliveryId", "whd_1", "targetUrl", "https://never.configured.example/hook"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CALLBACK)),
        Arguments.of(
            "a retry of a delivery this test run never scheduled",
            RETRY,
            Map.of("deliveryId", "whd_never_scheduled"),
            404,
            RefusalSubject.noObjectAnsweredTo(DeclaredWebhookCall.WEBHOOK_JOB_ID)),
        Arguments.of(
            "a scenario rule whose delay this simulator cannot read",
            WRITE,
            Map.of("actionType", "TERMINAL_FAILURE", "delayMs", "as soon as possible"),
            422,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "a scenario rule naming an action this simulator cannot perform",
            WRITE,
            Map.of("actionType", "SOMETHING_ELSE"),
            422,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "a preset this simulator does not publish",
            PRESET,
            Map.of("preset", "cancel-hold-succes"),
            400,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "a preset the tester previewed and did not confirm writing",
            PRESET,
            Map.of("preset", "cancel-hold-success"),
            400,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "a preset this simulator does not publish, confirmed rather than previewed",
            PRESET,
            Map.of("preset", "cancel-hold-succes", "confirm", "yes"),
            400,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "a preset asked for at a priority that is not a whole number",
            PRESET,
            Map.of("preset", "cancel-hold-success", "priority", "as soon as possible"),
            422,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "a document box holding nothing at all",
            IMPORT,
            Map.of("document", ""),
            400,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "a document box holding something this simulator cannot read",
            IMPORT,
            Map.of("document", "not a document at all"),
            400,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "a document another provider expanded",
            IMPORT,
            Map.of("document", "{\"provider\":\"somebodyelse\",\"rules\":[]}"),
            422,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_HOLD)),
        Arguments.of(
            "deleting a scenario rule this test run never held",
            DELETE,
            Map.of("scenarioId", "scn_never_written"),
            404,
            RefusalSubject.noObjectWasNamed(THE_RULE_TO_FORGET)),
        Arguments.of(
            "pinning the clock to something that is not a moment",
            CLOCK,
            Map.of("fixedNow", "the day before yesterday"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "binding a route alias without naming the URL it binds",
            ROUTE_ALIAS,
            Map.of("from", "", "to", "https://merchant.test/thanks"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "a submitted setting under no key at all",
            SETTINGS,
            Map.of(SettingsForm.SUBMITTED_SETTING_PREFIX, "PT0S,PT1S"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "a retry plan that holds the first delivery attempt back",
            SETTINGS,
            Map.of(SettingsForm.SUBMITTED_SETTING_PREFIX + "webhook.retryPlan", "PT5S,PT10S"),
            422,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "a setting this provider declares nothing under",
            SETTINGS,
            Map.of(SettingsForm.SUBMITTED_SETTING_PREFIX + "webhook.nothing", "PT0S"),
            422,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "releasing a route alias the tester did not confirm releasing",
            RELEASE,
            Map.of("from", "https://never.bound.example/create"),
            400,
            RefusalSubject.noObjectWasNamed(THE_ROUTE_ALIAS)),
        Arguments.of(
            "releasing a URL this test run never bound",
            RELEASE,
            Map.of("from", "https://never.bound.example/create", "confirm", "yes"),
            404,
            RefusalSubject.noObjectWasNamed(THE_ROUTE_ALIAS)),
        Arguments.of(
            "releasing without naming the URL it binds",
            RELEASE,
            Map.of("from", "", "confirm", "yes"),
            400,
            RefusalSubject.noObjectWasNamed(THE_ROUTE_ALIAS)),
        Arguments.of(
            "forgetting an account the tester did not confirm forgetting",
            FORGET,
            Map.of("accountId", "merchant-1"),
            400,
            RefusalSubject.noObjectWasNamed(THE_ACCOUNT)),
        Arguments.of(
            "forgetting an account no configuration of this run named",
            FORGET,
            Map.of("accountId", "merchant-9", "confirm", "yes"),
            404,
            RefusalSubject.noObjectWasNamed(THE_ACCOUNT)),
        Arguments.of(
            "forgetting without naming an account at all",
            FORGET,
            Map.of("accountId", "", "confirm", "yes"),
            400,
            RefusalSubject.noObjectWasNamed(THE_ACCOUNT)),
        Arguments.of(
            "a callback target named for no account",
            ACCOUNT,
            Map.of("accountId", "", "callbackUrl", "https://merchant.test/hook"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "an account leaving out a field this provider requires",
            ACCOUNT,
            Map.of("accountId", "merchant-2", "field.userId", "", "field.pageCode", "a-page"),
            422,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "an account configured under no identity at all and no callback target either",
            ACCOUNT,
            Map.of("accountId", "", "field.userId", "a-user", "field.pageCode", "a-page"),
            400,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "seeding a payment method this simulator cannot read",
            SEED,
            Map.of("type", "credit-card", "merchantId", "merchant-1", "ownerId", "customer-1"),
            400,
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_TO_SEED)),
        Arguments.of(
            "removing a payment method the tester did not confirm removing",
            REMOVE,
            Map.of("fixtureId", "fx_1"),
            400,
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_TO_FORGET)),
        Arguments.of(
            "removing a payment method no test run seeded",
            REMOVE,
            Map.of("fixtureId", "fx_never_seeded", "confirm", "yes"),
            404,
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_TO_FORGET)));
  }

  @ParameterizedTest(name = "{0} leaves a row carrying its verdict, its subject and why")
  @MethodSource("theRefusingPathsOfTheScreens")
  void every_refusing_path_of_the_screens_leaves_one_row_that_names_what_it_refused_and_says_why(
      String what, String path, Map<String, String> form, int status, RefusalSubject subject) {
    assertThat(submit(path, form)).as("the page a tester is left on").isIn(200, 302);

    AuditRecord row = theRowAt(path, status);
    assertThat(row.refusal()).isEqualTo(subject);
    assertThat(row.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(row.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(row.method()).isEqualTo("POST");
    assertThat(theWordsItWasTurnedAwayIn(row))
        .as(
            "the status and the subject of this row tell a tester which thing was refused and"
                + " never why, and the trail is where they look once the answer is off their"
                + " screen, so the row carries the words it was turned away in")
        .isNotBlank();
  }

  private static String theWordsItWasTurnedAwayIn(AuditRecord row) {
    List<String> said = row.sanitizedBody().get(ActionFacts.REFUSED_BECAUSE);
    return said == null ? "" : String.join("", said);
  }

  @Test
  void a_payment_a_run_with_no_configured_account_cannot_start_is_refused_naming_that_payment() {
    forgetMerchantOne();

    submit(START, Map.of("accountId", "merchant-1", "startedAs", "HOSTED_PAGE"));

    assertThat(theRowAt(START, 422).refusal())
        .isEqualTo(RefusalSubject.noObjectWasNamed(THE_PAYMENT));
  }

  @Test
  void a_payment_this_simulator_could_not_read_the_wizard_for_is_rejected_naming_that_payment() {
    submit(START, Map.of("accountId", "merchant-1", "startedAs", "NO_SUCH_WAY"));

    assertThat(theRowAt(START, 400).refusal())
        .isEqualTo(RefusalSubject.noObjectWasNamed(THE_PAYMENT));
  }

  @Test
  void a_payer_step_the_tester_did_not_confirm_taking_leaves_a_row_naming_that_completion() {
    String process = aHostedPageThisRunHolds();

    submit(
        COMPLETE, Map.of("providerObjectId", process, "outcome", "PAID", "card", A_PUBLISHED_CARD));

    AuditRecord row = theRowAt(COMPLETE, 400);
    assertThat(row.refusal()).isEqualTo(RefusalSubject.noObjectWasNamed(THE_COMPLETION));
    assertThat(row.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(row.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(row.method()).isEqualTo("POST");
  }

  private String aHostedPageThisRunHolds() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("accountId", "merchant-1");
    form.put("startedAs", "createPaymentProcess:chargeType=2");
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "sum", "10.00");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "description", "one seat, row F");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "successUrl",
        "https://merchant.example/paid");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "cancelUrl",
        "https://merchant.example/gaveup");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "pageField[fullName]",
        "Dana Levi");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "pageField[phone]", "0501234567");

    HttpResponse<byte[]> started = post(START, form);
    assertThat(started.statusCode()).isEqualTo(302);
    Matcher named =
        A_GROW_PROCESS_ID.matcher(started.headers().firstValue("Location").orElseThrow());
    assertThat(named.find()).isTrue();
    return named.group(1);
  }

  @Test
  void
      a_screen_action_that_could_choose_neither_a_provider_nor_a_test_run_leaves_no_row_anywhere() {
    submit(CLOCK, Map.of("provider", "nosuchprovider", "fixedNow", "the day before yesterday"));
    submit(
        SETTINGS,
        Map.of(
            "provider",
            "nosuchprovider",
            SettingsForm.SUBMITTED_SETTING_PREFIX + "webhook.retryPlan",
            "PT5S,PT10S"));
    submit(
        FORGET, Map.of("provider", "nosuchprovider", "accountId", "merchant-1", "confirm", "yes"));
    submit(SEED, Map.of("provider", "nosuchprovider", "type", "credit-card"));
    submit(RETRY, Map.of("provider", "nosuchprovider", "deliveryId", "whd_1"));
    submit(DELIVER_DUE, Map.of("provider", "nosuchprovider"));

    List<AuditRecord> everywhere = new ArrayList<>();
    for (String known : providers.names()) {
      everywhere.addAll(trail.of(ProviderId.of(known), TEST_RUN));
      everywhere.addAll(trail.of(ProviderId.of(known), TestRunId.UNKNOWN));
    }
    assertThat(everywhere).noneSatisfy(row -> assertThat(row.path()).isEqualTo(CLOCK));
    assertThat(everywhere).noneSatisfy(row -> assertThat(row.path()).isEqualTo(SETTINGS));
    assertThat(everywhere).noneSatisfy(row -> assertThat(row.path()).isEqualTo(FORGET));
    assertThat(everywhere).noneSatisfy(row -> assertThat(row.path()).isEqualTo(SEED));
    assertThat(everywhere).noneSatisfy(row -> assertThat(row.path()).isEqualTo(RETRY));
    assertThat(everywhere).noneSatisfy(row -> assertThat(row.path()).isEqualTo(DELIVER_DUE));
  }

  @Test
  void a_refused_account_form_discloses_neither_a_declared_secret_nor_an_undeclared_keys_value() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("accountId", "merchant-2");
    form.put("field.userId", A_SECRET_A_HARNESS_WOULD_RECOGNISE);
    form.put("field.pageCode", A_PAGE_CODE_A_HARNESS_WOULD_RECOGNISE);
    form.put("field.webhookSecret", A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED);
    form.put("field.callbackAcknowledgement", "WHEN_THE_MOON_IS_FULL");

    submit(ACCOUNT, form);

    AuditRecord row = theRowAt(ACCOUNT, 422);
    assertThat(String.valueOf(row))
        .doesNotContain(A_SECRET_A_HARNESS_WOULD_RECOGNISE)
        .doesNotContain(A_PAGE_CODE_A_HARNESS_WOULD_RECOGNISE)
        .doesNotContain(A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED);
    assertThat(row.sanitizedBody()).containsKey("accounts[0].configuration.userId");
  }

  @Test
  void a_refused_account_form_names_the_box_on_the_screen_and_the_property_in_the_trail() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("accountId", "merchant-3");
    form.put("field.userId", "");
    form.put("field.pageCode", A_PAGE_CODE_A_HARNESS_WOULD_RECOGNISE);

    String screen = new String(post(ACCOUNT, form).body(), UTF_8);

    assertThat(theRefusalPrintedOn(screen))
        .as(
            "the box is labelled Grow user id on the screen a tester filled in, and accounts[0]"
                + " is where the value sits in the body the control API takes; a tester who"
                + " clicked has no accounts[0] to go and look at")
        .contains("Grow user id is required by provider grow")
        .doesNotContain("accounts[0]")
        .doesNotContain("userId");
    assertThat(theWordsItWasTurnedAwayIn(theRowAt(ACCOUNT, 422)))
        .as(
            "and the trail keeps the property the request names, because the row is what a"
                + " developer reads once the screen is gone and the same refusal is answered to"
                + " a CI suite that sent that very property")
        .isEqualTo("accounts[0].configuration.userId is required by provider grow");
  }

  private static String theRefusalPrintedOn(String page) {
    Matcher printed =
        Pattern.compile("<div class=\"refusal\">(.*?)</div>", Pattern.DOTALL).matcher(page);
    assertThat(printed.find()).as("the screen prints a refusal").isTrue();
    return String.valueOf(printed.group(1)).strip();
  }

  private static final String A_SECRET_A_HARNESS_WOULD_RECOGNISE = "grow-user-4b27de91c0";

  private static final String A_PAGE_CODE_A_HARNESS_WOULD_RECOGNISE = "grow-page-7a10bc";

  private static final String A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED = "whsec_4d1f77b2ae90";

  @Test
  void deleting_a_scenario_rule_this_run_never_held_says_so_on_the_screen_it_returns_to() {
    String screen = whereItSentTheTester(DELETE, Map.of("scenarioId", "scn_never_written"));

    assertThat(screen).contains("holds no scenario rule");
  }

  @Test
  void retrying_a_delivery_this_run_never_scheduled_says_so_on_the_screen_it_returns_to() {
    String screen = whereItSentTheTester(RETRY, Map.of("deliveryId", "whd_never_scheduled"));

    assertThat(screen).contains("holds no callback delivery");
  }

  @Test
  void a_retry_and_a_deliver_due_naming_an_unknown_provider_say_so_on_the_screen_they_return_to() {
    String refusal = providers.theRefusalOfAnUnknownProvider();

    assertThat(whereItSentTheTester(RETRY, aRetryNaming("nosuchprovider"))).contains(refusal);
    assertThat(whereItSentTheTester(DELIVER_DUE, Map.of("provider", "nosuchprovider")))
        .contains(refusal);
  }

  @Test
  void a_retry_and_a_deliver_due_naming_no_provider_ask_for_one_on_the_screen_they_return_to() {
    assertThat(whereItSentTheTester(RETRY, aRetryNaming("")))
        .contains("Choose a provider")
        .contains("this request named none");
    assertThat(whereItSentTheTester(DELIVER_DUE, Map.of("provider", "")))
        .contains("Choose a provider")
        .contains("this request named none");
  }

  private static Map<String, String> aRetryNaming(String provider) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("provider", provider);
    fields.put("deliveryId", "whd_1");
    return fields;
  }

  private String whereItSentTheTester(String path, Map<String, String> fields) {
    HttpResponse<byte[]> answered = post(path, fields);
    assertThat(answered.statusCode()).isEqualTo(302);
    return page(answered.headers().firstValue("location").orElseThrow());
  }

  private String page(String uri) {
    HttpResponse<byte[]> answered =
        read(
            HttpRequest.newBuilder(
                    URI.create(uri.startsWith("http") ? uri : "http://localhost:" + port + uri))
                .GET());
    assertThat(answered.statusCode()).isEqualTo(200);
    return new String(answered.body(), UTF_8);
  }

  private HttpResponse<byte[]> read(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private AuditRecord theRowAt(String path, int status) {
    List<AuditRecord> found = new ArrayList<>();
    for (AuditRecord row : trail.of(GROW, TEST_RUN)) {
      if (row.path().equals(path) && row.httpStatus() != null && row.httpStatus() == status) {
        found.add(row);
      }
    }
    assertThat(found).as("rows at %s carrying %s", path, status).hasSize(1);
    return found.getFirst();
  }

  private void configureMerchantOne() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN,
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        "merchant-1",
                        "configuration",
                        Map.of("userId", "refused-screens-user", "pageCode", "refused-screens")))))
        .retrieve()
        .toBodilessEntity();
  }

  private void forgetMerchantOne() {
    http.delete()
        .uri("/__simulator/grow/config/accounts/merchant-1?testRunId=" + RUN)
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN, "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }

  private int submit(String path, Map<String, String> fields) {
    return post(path, fields).statusCode();
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> fields) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN);
    form.putAll(fields);
    if (fields.containsKey("provider")) {
      form.put("provider", fields.get("provider"));
    }
    return read(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }
}
