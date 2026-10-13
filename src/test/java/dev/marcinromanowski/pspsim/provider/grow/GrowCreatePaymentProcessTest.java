package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.ui.Disclosure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrowCreatePaymentProcessTest {

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("another-run");
  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String BASE_URL = "https://simulator.internal";

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(DEFAULT_RUN);
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();

  @Test
  void answers_a_valid_hold_with_the_enveloped_process_grow_publishes() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":1,\"err\":\"\",\"data\":{"
                + "\"processId\":\"100001\","
                + "\"processToken\":\"00000000000000000000000000000001\","
                + "\"url\":\"https://simulator.internal/far?l=00000000000000000000000000000002\""
                + "}}");
    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.contentType()).isEqualTo("text/html; charset=UTF-8");
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo("100001");
  }

  @Test
  void stores_the_process_in_the_test_run_its_credentials_belong_to() {
    accounts.with(account(TestRunId.of("someone-else"), "other", "other-user", "other-page"));
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    api().createPaymentProcess(call(hold()));

    GrowPaymentProcess process = onlyProcess();
    assertThat(process.testRunId()).isEqualTo(E2E_RUN);
    assertThat(process.merchantId()).isEqualTo("merchant-1");
    assertThat(process.chargeType()).isEqualTo("2");
    assertThat(process.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
  }

  @Test
  void a_created_process_carries_both_the_grow_native_status_and_the_normalized_one() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    api().createPaymentProcess(call(hold()));

    GrowPaymentProcess process = onlyProcess();
    assertThat(process.nativeStatusCode()).isEqualTo("0");
    assertThat(process.nativeStatus()).isEqualTo(GrowNativeStatus.NOT_PAID);
    assertThat(process.normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
  }

  @ParameterizedTest(name = "keeps the amount {0} exactly as it arrived")
  @ValueSource(strings = {"1000", "10.00", "10.0", "0009.90", "1e3"})
  void keeps_the_amount_exactly_as_it_arrived(String amount) {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    api().createPaymentProcess(call(hold(Map.of(GrowFields.SUM, amount))));

    assertThat(onlyProcess().chargeSum()).isEqualTo(amount);
  }

  @Test
  void accepts_a_platform_partners_company_commission_and_reads_nothing_from_it() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response =
        api().createPaymentProcess(call(hold(Map.of("companyCommission", "12.50"))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(response)).doesNotContain("companyCommission");
    assertThat(onlyProcess().chargeSum()).isEqualTo("120.50");
    assertThat(audit.of(GrowLightApi.GROW, E2E_RUN).get(0).sanitizedBody())
        .containsEntry("companyCommission", List.of("12.50"));
  }

  @Test
  void keeps_the_custom_fields_the_merchant_sent_for_correlation() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    api()
        .createPaymentProcess(call(hold(Map.of("cField1", "session-abc", "cField2", "intent-99"))));

    assertThat(onlyProcess().customFields())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("cField1", "session-abc", "cField2", "intent-99"));
  }

  @Test
  void reports_a_credential_failure_as_a_normal_http_200_with_error_701() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response =
        api().createPaymentProcess(call(hold(Map.of(GrowFields.USER_ID, "wrong-user"))));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(response.providerObjectId()).isNull();
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":701,"
                + "\"message\":\"פרמטר קוד זיהוי אינו תקין userId\"},\"data\":\"\"}");
    assertThat(processes.all()).isEmpty();
  }

  @ParameterizedTest(name = "rejects a request with no {0} as error 54 naming it")
  @ValueSource(
      strings = {
        "pageCode",
        "userId",
        "chargeType",
        "description",
        "successUrl",
        "cancelUrl",
        "pageField[fullName]",
        "pageField[phone]"
      })
  void names_the_missing_required_field(String field) {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> withoutField = hold();
    withoutField.remove(field);

    GrowApiResponse response = api().createPaymentProcess(call(withoutField));

    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,"
                + "\"message\":\"חסרים נתונים:"
                + field
                + "\"},\"data\":\"\"}");
  }

  @Test
  void treats_a_field_sent_empty_as_a_field_that_was_not_supplied() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response =
        api().createPaymentProcess(call(hold(Map.of(GrowFields.DESCRIPTION, "   "))));

    assertThat(bodyOf(response)).contains("\"id\":54").contains("description");
  }

  @Test
  void reports_a_missing_field_before_a_credential_failure() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> form = hold(Map.of(GrowFields.USER_ID, "wrong-user"));
    form.remove(GrowFields.DESCRIPTION);

    GrowApiResponse response = api().createPaymentProcess(call(form));

    assertThat(bodyOf(response)).contains("\"id\":54");
  }

  @Test
  void judges_the_amount_before_the_credential_the_way_the_live_wire_does() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> form = hold(Map.of(GrowFields.USER_ID, "wrong-user", GrowFields.SUM, "-1"));

    GrowApiResponse response = api().createPaymentProcess(call(form));

    assertThat(bodyOf(response)).contains("\"id\":707").doesNotContain("\"id\":701");
  }

  @ParameterizedTest(name = "refuses to open a payment page for the amount {0}")
  @ValueSource(strings = {"-0.01", "0", "0.00", "not-a-number"})
  void refuses_an_amount_the_live_wire_calls_invalid(String amount) {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response =
        api().createPaymentProcess(call(hold(Map.of(GrowFields.SUM, amount))));

    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":707,\"message\":\"הסכום אינו תקין\"},\"data\":\"\"}");
    assertThat(processes.all()).isEmpty();
  }

  @Test
  void refuses_a_payment_page_for_an_amount_no_form_field_carried() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> withoutSum = hold();
    withoutSum.remove(GrowFields.SUM);

    assertThat(bodyOf(api().createPaymentProcess(call(withoutSum)))).contains("\"id\":707");
  }

  @Test
  void rejects_both_instalment_fields_in_one_call_with_error_736() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response =
        api()
            .createPaymentProcess(
                call(
                    hold(
                        Map.of(
                            GrowFields.PAYMENT_NUM, "3",
                            GrowFields.MAX_PAYMENT_NUM, "6"))));

    assertThat(bodyOf(response)).contains("\"id\":736");
  }

  @ParameterizedTest(name = "accepts {0} on its own")
  @CsvSource({"paymentNum,3", "maxPaymentNum,6"})
  void accepts_either_instalment_field_on_its_own(String field, String value) {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response = api().createPaymentProcess(call(hold(Map.of(field, value))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void creates_a_token_only_process_for_the_charge_type_that_registers_a_card() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response = api().createPaymentProcess(call(registration()));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(onlyProcess().chargeType()).isEqualTo("3");
    assertThat(onlyProcess().saveCardToken()).isTrue();
  }

  @ParameterizedTest(name = "rejects the charge type {0}, which provider grow does not publish")
  @ValueSource(strings = {"1", "4", "0", "two"})
  void rejects_a_charge_type_provider_grow_does_not_publish(String chargeType) {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response =
        api().createPaymentProcess(call(hold(Map.of(GrowFields.CHARGE_TYPE, chargeType))));

    assertThat(bodyOf(response)).contains("\"id\":54").contains("chargeType");
    assertThat(processes.all()).isEmpty();
  }

  @ParameterizedTest(name = "refuses to register a card where saveCardToken is {0}")
  @ValueSource(strings = {"0", "true", "  ", "11"})
  void refuses_to_register_a_card_the_request_does_not_ask_to_save(String saveCardToken) {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response =
        api()
            .createPaymentProcess(
                call(registration(Map.of(GrowFields.SAVE_CARD_TOKEN, saveCardToken))));

    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,"
                + "\"message\":\"חסרים נתונים:saveCardToken\"},\"data\":\"\"}");
    assertThat(processes.all()).isEmpty();
  }

  @Test
  void refuses_to_register_a_card_where_the_request_asks_to_save_none() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> form = registration();
    form.remove(GrowFields.SAVE_CARD_TOKEN);

    assertThat(bodyOf(api().createPaymentProcess(call(form))))
        .contains("\"id\":54")
        .contains("saveCardToken");
  }

  @Test
  void a_hold_may_leave_the_card_unsaved_because_grow_makes_the_field_optional_there() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> form = hold();
    form.remove(GrowFields.SAVE_CARD_TOKEN);

    GrowApiResponse response = api().createPaymentProcess(call(form));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(onlyProcess().saveCardToken()).isFalse();
  }

  @Test
  void reports_a_card_that_is_not_asked_to_be_saved_before_a_credential_failure() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response =
        api()
            .createPaymentProcess(
                call(
                    registration(
                        Map.of(
                            GrowFields.SAVE_CARD_TOKEN, "0",
                            GrowFields.USER_ID, "wrong-user"))));

    assertThat(bodyOf(response)).contains("\"id\":54").contains("saveCardToken");
  }

  @Test
  void accepts_a_request_with_no_notify_url_because_grow_does() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> form = hold();
    form.remove(GrowFields.NOTIFY_URL);

    GrowApiResponse response = api().createPaymentProcess(call(form));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(onlyProcess().notifyUrl()).isNull();
  }

  @Test
  void falls_back_to_the_default_test_run_when_no_account_matches() {
    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(bodyOf(response)).contains("\"id\":701");
    assertThat(audit.all())
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(DEFAULT_RUN);
              assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.DEFAULT);
            });
  }

  @Test
  void refuses_to_choose_between_two_test_runs_configured_with_the_same_credentials() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    accounts.with(growAccount(ANOTHER_RUN, "merchant-2"));

    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(bodyOf(response)).contains("\"id\":701");
    assertThat(processes.all()).isEmpty();
    assertThat(audit.all())
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(TestRunId.UNKNOWN);
              assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.AMBIGUOUS);
            });
  }

  @Test
  void refuses_two_accounts_of_one_test_run_that_share_credentials() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    accounts.with(growAccount(E2E_RUN, "merchant-2"));

    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(bodyOf(response)).contains("\"id\":701");
  }

  @Test
  void requires_both_credential_halves_to_match_the_same_account() {
    accounts.with(account(E2E_RUN, "merchant-1", USER_ID, "a-different-page"));

    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(bodyOf(response)).contains("\"id\":701");
  }

  @Test
  void tells_two_test_runs_sharing_one_set_of_credentials_apart_by_the_header() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    accounts.with(growAccount(ANOTHER_RUN, "merchant-2"));
    GrowLightApi api = api();

    GrowApiResponse first = api.createPaymentProcess(callNaming(E2E_RUN.value(), hold()));
    GrowApiResponse second = api.createPaymentProcess(callNaming(ANOTHER_RUN.value(), hold()));

    assertThat(first.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(second.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(processes.all())
        .extracting(GrowPaymentProcess::testRunId, GrowPaymentProcess::merchantId)
        .containsExactly(tuple(E2E_RUN, "merchant-1"), tuple(ANOTHER_RUN, "merchant-2"));
    assertThat(audit.all())
        .allSatisfy(
            record ->
                assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.TEST_HEADER));
    assertThat(audit.of(GrowLightApi.GROW, E2E_RUN)).hasSize(2);
    assertThat(audit.of(GrowLightApi.GROW, ANOTHER_RUN)).hasSize(2);
  }

  @Test
  void a_header_does_not_admit_credentials_the_named_test_run_was_not_configured_with() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    accounts.with(account(ANOTHER_RUN, "merchant-2", "another-user", "another-page"));

    GrowApiResponse response = api().createPaymentProcess(callNaming(ANOTHER_RUN.value(), hold()));

    assertThat(bodyOf(response)).contains("\"id\":701");
    assertThat(processes.all()).isEmpty();
    assertThat(audit.all())
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(ANOTHER_RUN);
              assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.TEST_HEADER);
            });
  }

  @Test
  void refuses_a_header_naming_a_test_run_that_does_not_exist_instead_of_falling_through() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response = api().createPaymentProcess(callNaming("e2e-run-124", hold()));

    assertThat(bodyOf(response)).contains("\"id\":701");
    assertThat(processes.all()).isEmpty();
    assertThat(audit.all())
        .isNotEmpty()
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(TestRunId.UNKNOWN);
              assertThat(record.resolutionRule())
                  .isEqualTo(TestRunResolutionRule.TEST_HEADER_UNMATCHED);
            });
    assertThat(audit.of(GrowLightApi.GROW, E2E_RUN)).isEmpty();
  }

  @Test
  void a_header_naming_the_default_run_routes_to_it_although_grow_has_no_account_there() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    GrowApiResponse response = api().createPaymentProcess(callNaming(DEFAULT_RUN.value(), hold()));

    assertThat(bodyOf(response)).contains("\"id\":701");
    assertThat(processes.all()).isEmpty();
    assertThat(audit.all())
        .isNotEmpty()
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(DEFAULT_RUN);
              assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.TEST_HEADER);
            });
  }

  @Test
  void a_header_naming_a_test_run_configured_without_a_grow_account_routes_to_it() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    testRuns.register(ANOTHER_RUN);

    GrowApiResponse response = api().createPaymentProcess(callNaming(ANOTHER_RUN.value(), hold()));

    assertThat(bodyOf(response)).contains("\"id\":701");
    assertThat(processes.all()).isEmpty();
    assertThat(audit.all())
        .isNotEmpty()
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(ANOTHER_RUN);
              assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.TEST_HEADER);
            });
  }

  @ParameterizedTest(name = "{0} stores nothing, beside a test run named {1}")
  @MethodSource("everyVerdictAgainstEveryTestRunName")
  void a_verdict_of_resolution_stores_nothing_whatever_a_test_run_is_called(
      TestRunResolutionRule verdict, TestRunId secondRun) {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    accounts.with(growAccount(secondRun, "merchant-2"));

    GrowApiResponse response = requestResolvingTo(verdict);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).contains("\"id\":701");
    assertThat(processes.all()).isEmpty();
    assertThat(audit.all())
        .isNotEmpty()
        .allSatisfy(
            record -> {
              assertThat(record.resolutionRule()).isEqualTo(verdict);
              assertThat(record.testRunId())
                  .isEqualTo(verdict.namesATestRun() ? DEFAULT_RUN : TestRunId.UNKNOWN);
            });
  }

  private GrowApiResponse requestResolvingTo(TestRunResolutionRule verdict) {
    return switch (verdict) {
      case DEFAULT ->
          api().createPaymentProcess(call(hold(Map.of(GrowFields.USER_ID, "nobodys-user"))));
      case AMBIGUOUS -> api().createPaymentProcess(call(hold()));
      case TEST_HEADER_UNMATCHED ->
          api().createPaymentProcess(callNaming("no-such-test-run", hold()));
      case TEST_HEADER, ROUTE_ALIAS, ACCOUNT_CREDENTIALS, OBJECT_CORRELATION ->
          throw new IllegalArgumentException(verdict.name() + " is a rule, not a verdict");
    };
  }

  static List<Arguments> everyVerdictAgainstEveryTestRunName() {
    List<Arguments> scenarios = new ArrayList<>();
    for (TestRunResolutionRule rule : TestRunResolutionRule.values()) {
      if (rule.producesCandidates()) {
        continue;
      }
      for (TestRunId secondRun : List.of(ANOTHER_RUN, TestRunId.UNKNOWN)) {
        scenarios.add(Arguments.of(rule, secondRun));
      }
    }
    return scenarios;
  }

  @Test
  void resolves_by_credentials_when_the_request_names_no_test_run() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    api().createPaymentProcess(call(hold()));

    assertThat(audit.all())
        .allSatisfy(
            record ->
                assertThat(record.resolutionRule())
                    .isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS));
    assertThat(onlyProcess().testRunId()).isEqualTo(E2E_RUN);
  }

  @Test
  void audits_the_request_and_the_response_of_every_call() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    api().createPaymentProcess(call(hold()));

    List<AuditRecord> records = audit.of(GrowLightApi.GROW, E2E_RUN);
    assertThat(records)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    assertThat(records)
        .allSatisfy(
            record -> {
              assertThat(record.method()).isEqualTo("POST");
              assertThat(record.path()).isEqualTo("/api/light/server/1.0/createPaymentProcess");
              assertThat(record.createdAt()).isEqualTo(NOW);
              assertThat(record.attempt()).isEqualTo(1);
              assertThat(record.auditId()).startsWith("aud_");
            });
  }

  @Test
  void records_the_providers_verdict_next_to_the_http_status_it_travelled_under() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    api().createPaymentProcess(call(hold(Map.of(GrowFields.SUM, "-5"))));

    AuditRecord outbound = audit.of(GrowLightApi.GROW, E2E_RUN).get(1);
    assertThat(outbound.httpStatus()).isEqualTo(200);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void keeps_credentials_and_tokens_out_of_the_audit() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> form = hold(Map.of("cField1", "merchant-session-secret"));

    GrowApiResponse response = api().createPaymentProcess(call(form));

    List<AuditRecord> records = audit.of(GrowLightApi.GROW, E2E_RUN);
    String rendered = records.toString();
    assertThat(rendered).doesNotContain(USER_ID);
    assertThat(rendered).doesNotContain(PAGE_CODE);
    assertThat(rendered).doesNotContain("merchant-session-secret");
    assertThat(rendered).doesNotContain("00000000000000000000000000000001");
    assertThat(rendered).doesNotContain("Bearer squirrel");

    assertThat(records.get(0).sanitizedBody())
        .containsEntry(GrowFields.USER_ID, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.PAGE_CODE, List.of(Redaction.REDACTED))
        .containsEntry("cField1", List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.DESCRIPTION, List.of("A hold"));
    assertThat(records.get(0).sanitizedHeaders())
        .containsEntry("Authorization", List.of(Redaction.REDACTED));
    assertThat(records.get(1).sanitizedBody())
        .containsEntry("data.processToken", List.of(Redaction.REDACTED))
        .containsEntry("data.url", List.of(Redaction.REDACTED))
        .containsEntry("data.processId", List.of("100001"));

    assertThat(onlyProcess().customFields()).containsEntry("cField1", "merchant-session-secret");
    assertThat(bodyOf(response)).contains("00000000000000000000000000000001");
  }

  @Test
  void audits_every_value_of_a_field_that_was_sent_twice() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, List<String>> form = fieldsOf(hold());
    form.put(GrowFields.SUM, List.of("120.50", "1.00"));

    api().createPaymentProcess(callWith(form));

    assertThat(audit.of(GrowLightApi.GROW, E2E_RUN).get(0).sanitizedBody())
        .containsEntry(GrowFields.SUM, List.of("120.50", "1.00"));
  }

  @Test
  void audits_a_repeated_credential_as_two_redacted_entries() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, List<String>> form = fieldsOf(hold());
    form.put(GrowFields.PAGE_CODE, List.of(PAGE_CODE, "a-second-page"));

    api().createPaymentProcess(callWith(form));

    assertThat(audit.of(GrowLightApi.GROW, E2E_RUN).get(0).sanitizedBody())
        .containsEntry(GrowFields.PAGE_CODE, List.of(Redaction.REDACTED, Redaction.REDACTED));
  }

  @Test
  void draws_another_process_id_when_the_first_one_is_taken() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    processes.withTheProcessIdsAlreadyClaimed("100001");

    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo("100002");
    assertThat(onlyProcess().processId()).isEqualTo("100002");
  }

  @Test
  void keeps_drawing_up_to_the_attempt_limit() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    processes.withTheProcessIdsAlreadyClaimed(
        theFirstDrawnIds(CreatePaymentProcess.ID_ATTEMPTS - 1));

    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(onlyProcess().processId())
        .isEqualTo("1" + "0".repeat(4) + CreatePaymentProcess.ID_ATTEMPTS);
  }

  @Test
  void gives_up_rather_than_looping_when_no_process_id_can_be_allocated() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    processes.withTheProcessIdsAlreadyClaimed(theFirstDrawnIds(CreatePaymentProcess.ID_ATTEMPTS));
    GrowLightApi api = api();
    GrowApiCall holding = call(hold());

    assertThatThrownBy(() -> api.createPaymentProcess(holding))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("processId");
    assertThat(processes.all()).isEmpty();
  }

  @Test
  void refuses_to_store_a_hold_under_a_process_id_the_table_already_holds() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    processes.rejectingTheNext(1);
    GrowLightApi api = api();
    GrowApiCall holding = call(hold());

    assertThatThrownBy(() -> api.createPaymentProcess(holding))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("processId");
    assertThat(processes.all()).isEmpty();
  }

  @Test
  void a_process_id_a_declined_hold_was_decided_under_is_never_drawn_by_a_later_hold() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    aStoryDecliningTheFirstHoldOfItsOwnInstance();
    GrowLightApi api = api(new SimulatorDoubles.ScriptedIds("100001", "100001", "100007"));

    GrowApiResponse declined = api.createPaymentProcess(call(hold()));
    GrowApiResponse next = api.createPaymentProcess(call(hold()));

    assertThat(declined.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(next.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(theStoriesStandingOn("100001")).containsExactly(Map.entry("hold-retries", "asked"));
    assertThat(theStoriesStandingOn("100007")).containsExactly(Map.entry("hold-retries", "asked"));
  }

  @Test
  void a_process_id_a_preempted_hold_was_decided_under_is_never_drawn_by_a_later_hold() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            new ScenarioStory(
                "hold-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "lost")));
    GrowLightApi api = api(new SimulatorDoubles.ScriptedIds("100001", "100001", "100007"));

    GrowApiResponse lost = api.createPaymentProcess(call(hold()));
    GrowApiResponse next = api.createPaymentProcess(call(hold()));

    assertThat(lost.httpStatus()).isEqualTo(502);
    assertThat(next.httpStatus()).isEqualTo(502);
    assertThat(processes.all()).isEmpty();
    assertThat(theStoriesStandingOn("100001")).containsExactly(Map.entry("hold-retries", "lost"));
    assertThat(theStoriesStandingOn("100007")).containsExactly(Map.entry("hold-retries", "lost"));
  }

  private Map<String, String> theStoriesStandingOn(String processId) {
    return scenarioRules.storyStatesOf(
        GrowLightApi.GROW,
        E2E_RUN,
        new StoryKey(StoryKeying.FLOW, GrowStoryKey.ofProcess(processId)));
  }

  private void aStoryDecliningTheFirstHoldOfItsOwnInstance() {
    scenarioRules.addAll(
        List.of(
            new NewScenarioRule(
                GrowLightApi.GROW,
                E2E_RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.TerminalFailure("813"),
                new ScenarioStory(
                    "hold-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked")),
            new NewScenarioRule(
                GrowLightApi.GROW,
                E2E_RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.FixedSuccess(),
                new ScenarioStory("hold-retries", StoryKeying.FLOW, "asked", null))));
  }

  private static String[] theFirstDrawnIds(int count) {
    String[] drawn = new String[count];
    for (int index = 0; index < count; index++) {
      drawn[index] = "1" + "0".repeat(4) + (index + 1);
    }
    return drawn;
  }

  @Test
  void a_matched_scenario_declines_the_hold_with_the_numeric_err_id_the_scenario_names() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    declineEveryRequestOf(E2E_RUN, "813");

    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(response.providerObjectId()).isNull();
    assertThat(bodyOf(response))
        .isEqualTo("{\"status\":0,\"err\":{\"id\":813,\"message\":\"\"},\"data\":\"\"}");
    assertThat(processes.all()).isEmpty();
  }

  @Test
  void the_audit_record_of_a_declined_hold_names_the_scenario_that_decided_it() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    ScenarioRule rule = declineEveryRequestOf(E2E_RUN, "813");

    api().createPaymentProcess(call(hold()));

    List<AuditRecord> records = audit.of(GrowLightApi.GROW, E2E_RUN);
    assertThat(records.get(1).scenarioId()).isEqualTo(rule.scenarioId());
  }

  @Test
  void the_audit_record_of_a_hold_no_scenario_matched_names_none() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));

    api().createPaymentProcess(call(hold()));

    assertThat(audit.of(GrowLightApi.GROW, E2E_RUN).get(1).scenarioId()).isNull();
  }

  @Test
  void every_hold_begins_its_own_story_so_a_second_hold_meets_the_first_step_again() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    List<ScenarioRule> steps =
        scenarioRules.addAll(
            List.of(
                new NewScenarioRule(
                    GrowLightApi.GROW,
                    E2E_RUN,
                    0,
                    ScenarioMatch.anyRequest(),
                    new ScenarioAction.TerminalFailure("813"),
                    new ScenarioStory(
                        "hold-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked")),
                new NewScenarioRule(
                    GrowLightApi.GROW,
                    E2E_RUN,
                    0,
                    ScenarioMatch.anyRequest(),
                    new ScenarioAction.FixedSuccess(),
                    new ScenarioStory("hold-retries", StoryKeying.FLOW, "asked", null))));
    GrowLightApi api = api();

    GrowApiResponse first = api.createPaymentProcess(call(hold()));
    GrowApiResponse second = api.createPaymentProcess(call(hold()));

    assertThat(first.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(second.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(processes.all()).isEmpty();
    assertThat(audit.of(GrowLightApi.GROW, E2E_RUN))
        .filteredOn(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .extracting(AuditRecord::scenarioId)
        .containsExactly(steps.get(0).scenarioId(), steps.get(0).scenarioId());
  }

  @Test
  void a_scenario_never_stands_in_for_the_credential_check() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    declineEveryRequestOf(E2E_RUN, "813");

    GrowApiResponse response =
        api().createPaymentProcess(call(hold(Map.of(GrowFields.USER_ID, "wrong-user"))));

    assertThat(bodyOf(response)).contains("\"id\":701");
  }

  @Test
  void a_scenario_never_stands_in_for_the_required_field_check() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    declineEveryRequestOf(E2E_RUN, "813");
    Map<String, String> withoutDescription = hold();
    withoutDescription.remove(GrowFields.DESCRIPTION);

    GrowApiResponse response = api().createPaymentProcess(call(withoutDescription));

    assertThat(bodyOf(response)).contains("\"id\":54");
  }

  @Test
  void a_scenario_of_another_merchant_leaves_the_hold_alone() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            E2E_RUN,
            0,
            new ScenarioMatch(
                "merchant-2", null, null, null, null, null, null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("813"),
            null));

    GrowApiResponse response = api().createPaymentProcess(call(hold()));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void a_scenario_matches_the_hold_amount_exactly_as_the_merchant_wrote_it() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            E2E_RUN,
            0,
            new ScenarioMatch(
                null, null, null, null, null, Amount.of("120.5"), null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("813"),
            null));

    assertThat(api().createPaymentProcess(call(hold())).outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(api().createPaymentProcess(call(hold(Map.of(GrowFields.SUM, "120.5")))).outcome())
        .isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void a_scenario_selected_by_the_explicit_header_declines_only_the_call_that_carries_it() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            E2E_RUN,
            0,
            new ScenarioMatch(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("X-Simulator-Scenario", "card-add-failed")),
            new ScenarioAction.TerminalFailure("813"),
            null));
    GrowLightApi api = api();

    assertThat(api.createPaymentProcess(call(hold())).outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(
            api.createPaymentProcess(
                    callWith(
                        fieldsOf(hold()),
                        Map.of("X-Simulator-Scenario", List.of("card-add-failed"))))
                .outcome())
        .isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void a_scenario_of_another_test_run_leaves_this_run_alone() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    accounts.with(account(ANOTHER_RUN, "merchant-9", "other-user", "other-page"));
    declineEveryRequestOf(ANOTHER_RUN, "813");

    assertThat(api().createPaymentProcess(call(hold())).outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
  }

  private ScenarioRule declineEveryRequestOf(TestRunId testRunId, String providerCode) {
    return scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            testRunId,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure(providerCode),
            null));
  }

  private GrowLightApi api() {
    return api(new SimulatorDoubles.CountingIds());
  }

  private GrowLightApi api(IdGenerator ids) {
    accounts.all().forEach(account -> testRuns.register(account.testRunId()));
    return new GrowLightApi(
        accounts,
        testRuns,
        audit,
        SimulatorDoubles.clockAt(NOW),
        ids,
        DEFAULT_RUN,
        new CreatePaymentProcess(processes, ids, BASE_URL, scenarioEngine, GrowScalarType.INTEGER),
        new CreateTransactionWithToken(
            processes, transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
        new ApproveTransaction(
            processes,
            SimulatorDoubles.idleDeliveries(audit, ids, NOW),
            scenarioEngine,
            GrowScalarType.INTEGER),
        new SettleSuspendedTransaction(transactions, scenarioEngine, GrowScalarType.INTEGER),
        new RefundTransaction(transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
        new GetTransactionInfo(transactions, processes, scenarioEngine, GrowScalarType.INTEGER),
        new GetPaymentProcessInfo(processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
        new GetTokenTransactionsByExternalIdentifiers(
            processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
        new CancelBitTransaction(GrowScalarType.INTEGER),
        new UpdateMyUrl(SimulatorDoubles.idleDeliveries(audit, ids, NOW), GrowScalarType.INTEGER));
  }

  @Test
  void
      a_trail_row_and_the_request_fields_a_screen_lists_from_it_keep_the_twelve_fields_of_a_hold_in_the_order_the_merchant_sent_them() {
    accounts.with(growAccount(E2E_RUN, "merchant-1"));
    Map<String, String> sent = hold();

    api().createPaymentProcess(call(sent));

    AuditRecord inbound = audit.of(GrowLightApi.GROW, E2E_RUN).get(0);
    assertThat(inbound.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(sent).hasSize(12);
    assertThat(inbound.sanitizedBody().keySet())
        .as(
            "twelve fields have 479001600 orders, so a copy carrying no order of its own landing"
                + " on the order the merchant sent is not something one run does by chance")
        .containsExactlyElementsOf(sent.keySet());
    assertThat(Disclosure.of(inbound.sanitizedBody(), false))
        .as("the trail screen lists the request fields of that row in the same order")
        .extracting(Disclosure.Field::name)
        .containsExactlyElementsOf(sent.keySet());
  }

  @Test
  void
      a_call_and_the_form_read_out_of_it_hand_their_fields_over_as_a_sequenced_map_no_caller_can_write_to() {
    GrowApiCall call = call(hold());

    assertThat(call.form()).isInstanceOf(SequencedMap.class);
    assertThat(call.headers()).isInstanceOf(SequencedMap.class);
    assertThat(GrowForm.of(call.form()).values()).isInstanceOf(SequencedMap.class);
    assertThat(GrowForm.of(call.form()).forAudit()).isInstanceOf(SequencedMap.class);
    assertThatThrownBy(() -> call.form().put("extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> call.headers().put("Extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> GrowForm.of(call.form()).values().put("extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private GrowPaymentProcess onlyProcess() {
    assertThat(processes.all()).hasSize(1);
    return processes.all().get(0);
  }

  private static String bodyOf(GrowApiResponse response) {
    return GrowWireText.readable(new String(response.body(), UTF_8));
  }

  private static GrowAccount growAccount(TestRunId testRunId, String merchantId) {
    return account(testRunId, merchantId, USER_ID, PAGE_CODE);
  }

  private static GrowAccount account(
      TestRunId testRunId, String merchantId, String userId, String pageCode) {
    return SimulatorDoubles.account(testRunId, merchantId, userId, pageCode);
  }

  private static Map<String, String> hold() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CHARGE_TYPE, "2");
    form.put(GrowFields.SUM, "120.50");
    form.put(GrowFields.SAVE_CARD_TOKEN, "1");
    form.put(GrowFields.DESCRIPTION, "A hold");
    form.put(GrowFields.SUCCESS_URL, "https://merchant.example/thanks");
    form.put(GrowFields.CANCEL_URL, "https://merchant.example/cancel");
    form.put(GrowFields.NOTIFY_URL, "https://merchant.example/callbacks/grow");
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    form.put(GrowFields.EMAIL, "payer@merchant.example");
    return form;
  }

  private static Map<String, String> registration() {
    Map<String, String> form = hold();
    form.put(GrowFields.CHARGE_TYPE, GrowFields.CHARGE_TYPE_TOKEN_ONLY);
    form.put(GrowFields.SUM, "1");
    form.remove(GrowFields.PAYMENT_INTENT_FIELD);
    return form;
  }

  private static Map<String, String> registration(Map<String, String> overrides) {
    Map<String, String> form = registration();
    form.putAll(overrides);
    return form;
  }

  private static Map<String, String> hold(Map<String, String> overrides) {
    Map<String, String> form = hold();
    form.putAll(overrides);
    return form;
  }

  private static GrowApiCall call(Map<String, String> form) {
    return callWith(fieldsOf(form));
  }

  private static GrowApiCall callNaming(String testRunId, Map<String, String> form) {
    return callWith(fieldsOf(form), Map.of(TestRunHeader.NAME, List.of(testRunId)));
  }

  private static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static GrowApiCall callWith(Map<String, List<String>> fields) {
    return callWith(fields, Map.of());
  }

  private static GrowApiCall callWith(
      Map<String, List<String>> fields, Map<String, List<String>> extraHeaders) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Content-Type", List.of("multipart/form-data; boundary=x"));
    headers.put("Authorization", List.of("Bearer squirrel"));
    headers.putAll(extraHeaders);
    return new GrowApiCall("POST", "/api/light/server/1.0/createPaymentProcess", headers, fields);
  }
}
