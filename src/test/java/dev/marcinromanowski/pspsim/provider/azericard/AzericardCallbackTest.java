package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantEndpoint;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.net.URLDecoder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AzericardCallbackTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("azericard-callback");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String NOW_ON_THE_WIRE = "20260910120000";

  private static final String TERMINAL_ID = "77777777";
  private static final String MERCHANT = "merchant-1";
  private static final String ORDER = "20220628084800";
  private static final String NONCE = "F2B2DD7E603A7ADA";
  private static final String CGI_ROUTE = "/cgi-bin/cgi_link";
  private static final String TOKEN_ROUTE = "/token/cgi_link";
  private static final String AMOUNT = "11.48";

  private static final String BACKREF = "https://merchant.example/azericard/result";
  private static final String ANOTHER_TARGET = "https://merchant.example/other";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();
  private final RecordingMerchant merchant = new RecordingMerchant();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();

  @Test
  void posts_the_authorization_result_to_the_backref_the_request_carried() {
    String intRef = anApprovedAuthorization(Map.of(AzericardFields.BACKREF, BACKREF));

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(merchant.posted).hasSize(1);
    assertThat(merchant.posted.get(0).url()).isEqualTo(BACKREF);
    assertThat(merchant.posted.get(0).payload().contentType())
        .isEqualTo("application/x-www-form-urlencoded");
  }

  @Test
  void writes_the_callback_as_the_form_fields_the_only_published_example_names_in_its_order() {
    String intRef = anApprovedAuthorization(Map.of(AzericardFields.BACKREF, BACKREF));

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(sentFields().keySet())
        .containsExactly(
            "AMOUNT",
            "CURRENCY",
            "ORDER",
            "ACTION",
            "RC",
            "APPROVAL",
            "RRN",
            "INT_REF",
            "CARD",
            "TERMINAL",
            "TRTYPE",
            "TIMESTAMP",
            "NONCE",
            "P_SIGN");
    assertThat(sentFields())
        .containsEntry("AMOUNT", AMOUNT)
        .containsEntry("CURRENCY", "AZN")
        .containsEntry("ORDER", ORDER)
        .containsEntry("ACTION", "0")
        .containsEntry("RC", "00")
        .containsEntry("TERMINAL", TERMINAL_ID)
        .containsEntry("TRTYPE", "1")
        .containsEntry("TIMESTAMP", NOW_ON_THE_WIRE);
  }

  @Test
  void signs_the_callback_over_the_five_fields_the_vendor_publishes_for_it() {
    String intRef = anApprovedAuthorization(Map.of(AzericardFields.BACKREF, BACKREF));

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    Map<String, String> sent = sentFields();
    assertThat(
            AzericardKeyFixture.verifiedByTheGateway(
                AzericardMac.sourceOf(
                    sent.get("AMOUNT"),
                    sent.get("TERMINAL"),
                    sent.get("APPROVAL"),
                    sent.get("RRN"),
                    sent.get("INT_REF")),
                sent.get("P_SIGN")))
        .isTrue();
  }

  @Test
  void substitutes_a_hyphen_with_no_length_for_the_empty_approval_a_decline_reports() {
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=1"),
            new ScenarioAction.TerminalFailure("05"),
            null));
    String intRef = anApprovedAuthorization(Map.of(AzericardFields.BACKREF, BACKREF));

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    Map<String, String> sent = sentFields();
    assertThat(sent).containsEntry("ACTION", "2").containsEntry("RC", "05");
    assertThat(sent).containsEntry("APPROVAL", "");
    assertThat(
            AzericardKeyFixture.verifiedByTheGateway(
                AzericardMac.sourceOf(
                    sent.get("AMOUNT"), sent.get("TERMINAL"), null, sent.get("RRN"), intRef),
                sent.get("P_SIGN")))
        .isTrue();
  }

  @Test
  void carries_the_token_of_the_card_the_registration_stored() {
    String intRef = aRegisteredCard();

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(sentFields())
        .containsEntry("TOKEN", cards.all().get(0).token())
        .containsKeys("INT_REF", "TOKEN");
  }

  @Test
  void keeps_the_stored_card_token_and_the_signature_readable_in_the_delivery_row() {
    String intRef = aRegisteredCard();

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    Map<String, String> sent = sentFields();
    String token = cards.all().get(0).token();
    assertThat(sent.get("TOKEN"))
        .as("the token travels to the merchant's own endpoint unredacted")
        .isEqualTo(token);
    assertThat(theCallbackAttemptRows())
        .allSatisfy(
            row ->
                assertThat(row.sanitizedBody())
                    .as(
                        "the stored card's own identity, which this module records as delivered in"
                            + " the clear because a tester driving the stored-card flow has no"
                            + " other row to read it from")
                    .containsEntry("TOKEN", List.of(token))
                    .as(
                        "the MAC a tester's whole work on this contract is verifying stays"
                            + " readable")
                    .containsEntry("P_SIGN", List.of(sent.get("P_SIGN")))
                    .containsEntry("CARD", List.of(sent.get("CARD")))
                    .containsEntry("INT_REF", List.of(intRef))
                    .containsEntry("AMOUNT", List.of(AMOUNT))
                    .containsEntry("TERMINAL", List.of(TERMINAL_ID))
                    .containsEntry("TIMESTAMP", List.of(NOW_ON_THE_WIRE)));
  }

  @Test
  void keeps_every_field_the_callback_sent_and_replaces_the_value_of_none_with_a_redaction() {
    String intRef = anApprovedAuthorization(Map.of(AzericardFields.BACKREF, BACKREF));

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(theCallbackAttemptRows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.sanitizedBody())
                  .as("an authorization that stored no card carries no TOKEN at all")
                  .doesNotContainKey("TOKEN");
              assertThat(row.sanitizedBody().values())
                  .as(
                      "the whole of the readable claim: Redaction replaces a value and never a"
                          + " key, so a value standing where the redaction would be is the only"
                          + " thing that tells a withheld field from one a tester can read")
                  .allSatisfy(values -> assertThat(values).doesNotContain(Redaction.REDACTED));
              assertThat(row.sanitizedBody().keySet())
                  .as(
                      "and the row keeps every field this callback sent and invents none, which"
                          + " is what a key set states and all it states")
                  .containsExactlyElementsOf(sentFields().keySet());
            });
  }

  @Test
  void carries_the_notification_address_the_authorization_asked_to_be_told_at() {
    String intRef =
        anApprovedAuthorization(
            Map.of(
                AzericardFields.BACKREF, BACKREF, AzericardFields.EMAIL, "payer@merchant.example"));

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(sentFields()).containsEntry("EMAIL", "payer@merchant.example");
  }

  @Test
  void posts_to_the_terminals_one_configured_target_when_the_request_named_none() {
    String intRef = anApprovedAuthorization(Map.of());

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(merchant.posted.get(0).url()).isEqualTo(BACKREF);
    assertThat(sentFields()).doesNotContainKey("EMAIL");
  }

  @Test
  void refuses_to_post_to_a_backref_no_test_of_this_run_declared() {
    String intRef =
        anApprovedAuthorization(
            Map.of(AzericardFields.BACKREF, "https://somewhere-else.example/hook"));

    AzericardCallbacks callbacks = callbacks();
    WebhookScheduleRequest asking =
        new WebhookScheduleRequest(E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> callbacks.schedule(asking))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("BACKREF");
    assertThat(merchant.posted).isEmpty();
  }

  @Test
  void refuses_to_choose_between_two_targets_for_a_request_that_carried_no_backref() {
    String intRef = anApprovedAuthorization(Map.of());
    declareTheTargets(
        new WebhookTarget(MERCHANT, BACKREF), new WebhookTarget(MERCHANT, ANOTHER_TARGET));

    AzericardCallbacks callbacks = callbacks();
    WebhookScheduleRequest asking =
        new WebhookScheduleRequest(E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> callbacks.schedule(asking))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("single configured callback target");
  }

  @Test
  void refuses_to_sign_a_result_for_a_terminal_this_run_configures_no_account_for() {
    String intRef = anApprovedAuthorization(Map.of(AzericardFields.BACKREF, BACKREF));
    accounts.forget(E2E_RUN);

    AzericardCallbacks callbacks = callbacks();
    WebhookScheduleRequest asking =
        new WebhookScheduleRequest(E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> callbacks.schedule(asking))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("gateway key");
    assertThat(merchant.posted).isEmpty();
  }

  @Test
  void refuses_an_event_name_the_vendor_publishes_nowhere() {
    String intRef = anApprovedAuthorization(Map.of());

    AzericardCallbacks callbacks = callbacks();
    WebhookScheduleRequest namingAnEventType =
        new WebhookScheduleRequest(
            E2E_RUN, intRef, "payment.approved", null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> callbacks.schedule(namingAnEventType))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("eventType");
  }

  @Test
  void refuses_to_report_an_authorization_this_run_never_answered() {
    accounts.with(terminal(Map.of()));

    AzericardCallbacks callbacks = callbacks();
    WebhookScheduleRequest namingNoPayment =
        new WebhookScheduleRequest(
            E2E_RUN, "NOSUCHINTREF0001", null, null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> callbacks.schedule(namingNoPayment))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessageContaining("no azericard payment with that providerObjectId in this test run");
  }

  @Test
  void emits_a_signature_a_merchant_must_refuse_when_the_terminal_is_configured_to_corrupt_it() {
    String intRef =
        anApprovedAuthorization(
            Map.of(AzericardFields.BACKREF, BACKREF),
            Map.of(AzericardCallbackSignature.SETTING, "CORRUPTED"));

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    Map<String, String> sent = sentFields();
    assertThat(sent).containsKey("P_SIGN");
    assertThat(
            AzericardKeyFixture.verifiedByTheGateway(
                AzericardMac.sourceOf(
                    sent.get("AMOUNT"),
                    sent.get("TERMINAL"),
                    sent.get("APPROVAL"),
                    sent.get("RRN"),
                    sent.get("INT_REF")),
                sent.get("P_SIGN")))
        .isFalse();
  }

  @Test
  void sends_no_signature_at_all_when_the_terminal_is_configured_to_omit_it() {
    String intRef =
        anApprovedAuthorization(
            Map.of(AzericardFields.BACKREF, BACKREF),
            Map.of(AzericardCallbackSignature.SETTING, "ABSENT"));

    callbacks()
        .schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(sentFields()).doesNotContainKey("P_SIGN").containsKey("NONCE");
  }

  @Test
  void corrupts_a_signature_whose_last_character_is_already_the_one_it_would_substitute() {
    assertThat(AzericardCallback.corrupted("AAAF")).isEqualTo("AAA0");
    assertThat(AzericardCallback.corrupted("AAA0")).isEqualTo("AAAF");
  }

  @ParameterizedTest(name = "treats HTTP {0} from the merchant as the callback being accepted")
  @ValueSource(ints = {200, 201, 204, 299})
  void accepts_a_callback_the_merchant_answered_in_the_two_hundreds(int httpStatus) {
    assertThat(judged(new MerchantResponse.Answered(httpStatus, "")))
        .isEqualTo(Acknowledgement.ACKNOWLEDGED);
  }

  @ParameterizedTest(name = "retries a callback the merchant answered HTTP {0}")
  @ValueSource(ints = {199, 300, 404, 500})
  void refuses_a_callback_the_merchant_did_not_answer_in_the_two_hundreds(int httpStatus) {
    assertThat(judged(new MerchantResponse.Answered(httpStatus, "")))
        .isEqualTo(Acknowledgement.REFUSED);
  }

  @Test
  void retries_a_callback_the_merchant_never_answered_at_all() {
    assertThat(judged(new MerchantResponse.NotAnswered(TransportFailure.READ_TIMEOUT)))
        .isEqualTo(Acknowledgement.REFUSED);
  }

  @Test
  void sends_the_bytes_of_the_first_attempt_again_on_a_retry_because_nothing_it_signs_moves() {
    String intRef = anApprovedAuthorization(Map.of(AzericardFields.BACKREF, BACKREF));
    merchant.refusing();
    AzericardCallbacks scheduling = callbacks();
    WebhookDispatcher dispatcher = dispatcher();

    List<ScheduledWebhook> scheduled =
        scheduling.schedule(
            new WebhookScheduleRequest(
                E2E_RUN, intRef, null, null, null, ObjectOrigin.CONTROL_API));
    merchant.accepting();
    WebhookDelivery retried =
        dispatcher
            .retry(AzericardGateway.AZERICARD, E2E_RUN, scheduled.get(0).webhookJobId())
            .orElseThrow();

    assertThat(merchant.posted).hasSize(2);
    assertThat(new String(merchant.posted.get(1).payload().body(), UTF_8))
        .isEqualTo(new String(merchant.posted.get(0).payload().body(), UTF_8));
    assertThat(retried.state()).isEqualTo(DeliveryState.DELIVERED);
  }

  private Acknowledgement judged(MerchantResponse response) {
    return new AzericardCallbackAcknowledgement()
        .judge(
            deliveries.of(AzericardGateway.AZERICARD, E2E_RUN).stream().findFirst().orElse(null),
            response);
  }

  private List<AuditRecord> theCallbackAttemptRows() {
    List<AuditRecord> attempts =
        audit.of(AzericardGateway.AZERICARD, E2E_RUN).stream()
            .filter(record -> record.direction() == AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT)
            .toList();
    assertThat(attempts)
        .as("a run that kept no delivery row would let every assertion on one pass")
        .isNotEmpty();
    return attempts;
  }

  private String aRegisteredCard() {
    accounts.with(terminal(Map.of()));
    declareTheTargets(new WebhookTarget(MERCHANT, BACKREF));
    Map<String, String> form = authorization();
    form.put(AzericardFields.BACKREF, BACKREF);
    form.put(AzericardFields.TOKEN_ACTION, AzericardTokenFlow.REGISTER);
    form.put(AzericardFields.MERCH_TRAN_STATE, AzericardTokenFlow.STORING_A_CARD);
    gateway()
        .tokenLink(new AzericardApiCall("POST", TOKEN_ROUTE, Map.of(), fieldsOf(signed(form))));
    return payments.all().get(0).intRef();
  }

  private String anApprovedAuthorization(Map<String, String> extraFields) {
    return anApprovedAuthorization(extraFields, Map.of());
  }

  private String anApprovedAuthorization(
      Map<String, String> extraFields, Map<String, String> configuration) {
    accounts.with(terminal(configuration));
    declareTheTargets(new WebhookTarget(MERCHANT, BACKREF));
    Map<String, String> form = authorization();
    form.putAll(extraFields);
    gateway().cgiLink(new AzericardApiCall("POST", CGI_ROUTE, Map.of(), fieldsOf(signed(form))));
    assertThat(payments.all()).hasSize(1);
    return payments.all().get(0).intRef();
  }

  private void declareTheTargets(WebhookTarget... targets) {
    accounts.merge(
        AzericardGateway.AZERICARD,
        E2E_RUN,
        List.of(MERCHANT),
        List.of(targets),
        new ProviderConfigurationPlan(AzericardTerminal.CONFIGURATION_KEYS, List.of(), () -> {}));
  }

  private Map<String, String> sentFields() {
    assertThat(merchant.posted).isNotEmpty();
    String body =
        new String(merchant.posted.get(merchant.posted.size() - 1).payload().body(), UTF_8);
    Map<String, String> fields = new LinkedHashMap<>();
    for (String pair : body.split("&", -1)) {
      int separator = pair.indexOf('=');
      fields.put(
          URLDecoder.decode(pair.substring(0, separator), UTF_8),
          URLDecoder.decode(pair.substring(separator + 1), UTF_8));
    }
    return fields;
  }

  private AzericardTerminal terminal(Map<String, String> configuration) {
    return AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID, configuration);
  }

  private static Map<String, String> authorization() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "1");
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, AMOUNT);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(NOW));
    form.put(AzericardFields.NONCE, NONCE);
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(signed)).macSource()));
    return signed;
  }

  private static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private AzericardCallbacks callbacks() {
    return new AzericardCallbacks(
        payments,
        accounts,
        accounts,
        dispatcher(),
        AzericardDoubles.clockAt(NOW),
        new AzericardDoubles.CountingIds());
  }

  private WebhookDispatcher dispatcher() {
    return new WebhookDispatcher(
        deliveries,
        merchant,
        audit,
        AzericardDoubles.clockAt(NOW),
        new AzericardDoubles.CountingIds(),
        new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1))),
        new DeliveryShaping(scenarioRules),
        List.of(new AzericardCallbackAcknowledgement()),
        List.of(),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }

  private AzericardGateway gateway() {
    IdGenerator ids = new AzericardDoubles.CountingIds();
    accounts.all().forEach(terminal -> testRuns.register(terminal.testRunId()));
    return new AzericardGateway(
        accounts,
        testRuns,
        audit,
        AzericardDoubles.clockAt(NOW),
        ids,
        DEFAULT_RUN,
        new Authorize(payments, cards, ids, scenarioEngine),
        new CompleteOrReverse(payments, scenarioEngine),
        new LookUpStatus(payments, scenarioEngine),
        AzericardDoubles.checkoutOf(
            payments, cards, accounts, scenarioEngine, audit, AzericardDoubles.clockAt(NOW), ids));
  }

  private static final class RecordingMerchant implements MerchantEndpoint {

    private record Post(String url, WebhookPayload payload) {}

    private final List<Post> posted = new ArrayList<>();
    private int httpStatus = 200;

    void refusing() {
      httpStatus = 500;
    }

    void accepting() {
      httpStatus = 200;
    }

    @Override
    public MerchantResponse post(String url, WebhookPayload payload) {
      posted.add(new Post(url, payload));
      return new MerchantResponse.Answered(httpStatus, "");
    }
  }
}
