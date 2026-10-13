package dev.marcinromanowski.pspsim.provider.azericard;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.ui.Disclosure;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AzericardAuthorizationTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("azericard-run");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("another-run");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String NOW_ON_THE_WIRE = "20260910120000";

  private static final String TERMINAL_ID = "77777777";
  private static final String MERCHANT = "merchant-1";
  private static final String ORDER = "20220628084800";
  private static final String NONCE = "F2B2DD7E603A7ADA";
  private static final String ROUTE = "/cgi-bin/cgi_link";

  private static final String FIRST_INT_REF = "0000000000000001";
  private static final String FIRST_RRN = "100000000001";
  private static final String FIRST_APPROVAL = "100001";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();

  @Test
  void answers_an_approved_pre_authorization_with_the_signed_xml_the_gateway_publishes() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.contentType()).isEqualTo("text/html");
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo(FIRST_INT_REF);
    assertThat(response.testRunId()).isEqualTo(E2E_RUN);
    assertThat(response.declaredContentLength()).isEqualTo(bodyOf(response).length());
    assertThat(response.completesTheBody()).isTrue();
    assertThat(response.delay()).isEqualTo(Duration.ZERO);
    assertThat(bodyOf(response))
        .isEqualTo(
            "<response>"
                + "<desc>Approved</desc>"
                + "<terminal>77777777</terminal>"
                + "<trtype>0</trtype>"
                + "<order>20220628084800</order>"
                + "<amount>11.48</amount>"
                + "<currency>AZN</currency>"
                + "<action>0</action>"
                + "<rc>00</rc>"
                + "<approval>100001</approval>"
                + "<rrn>100000000001</rrn>"
                + "<intref>0000000000000001</intref>"
                + "<timestamp>"
                + NOW_ON_THE_WIRE
                + "</timestamp>"
                + "<nonce>"
                + NONCE
                + "</nonce>"
                + "<p_sign>"
                + theExpectedSignatureOver(
                    "11.48", TERMINAL_ID, FIRST_APPROVAL, FIRST_RRN, FIRST_INT_REF)
                + "</p_sign>"
                + "</response>");
  }

  @Test
  void signs_the_answer_over_the_five_fields_the_vendor_publishes_for_a_callback() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(
            AzericardKeyFixture.verifiedByTheGateway(
                AzericardMac.sourceOf(
                    "11.48", TERMINAL_ID, FIRST_APPROVAL, FIRST_RRN, FIRST_INT_REF),
                signatureIn(bodyOf(response))))
        .isTrue();
  }

  @Test
  void builds_the_request_mac_from_the_seven_fields_the_vendor_publishes_in_their_order() {
    Map<String, String> form = authorization();
    form.put(AzericardFields.AMOUNT, "11.48");
    form.put(AzericardFields.CURRENCY, "USD");
    form.put(AzericardFields.TERMINAL, "99999999");
    form.put(AzericardFields.TRTYPE, "1");
    form.put(AzericardFields.TIMESTAMP, "20030105153021");
    form.put(AzericardFields.NONCE, NONCE);
    form.put(AzericardFields.MERCH_URL, "www.sample.com");

    assertThat(
            new String(
                AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(form)).macSource(), UTF_8))
        .isEqualTo("511.483USD89999999911142003010515302116F2B2DD7E603A7ADA14www.sample.com");
  }

  @Test
  void leaves_a_merchant_url_the_request_did_not_carry_out_of_the_mac_as_a_bare_hyphen() {
    Map<String, String> form = authorization();
    form.remove(AzericardFields.MERCH_URL);

    assertThat(
            new String(
                AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(form)).macSource(), UTF_8))
        .endsWith("-");
  }

  @Test
  void an_approved_pre_authorization_is_ready_for_capture_and_holds_its_amount() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    gateway().cgiLink(call(signed(authorization())));

    AzericardPayment payment = onlyPayment();
    assertThat(payment.testRunId()).isEqualTo(E2E_RUN);
    assertThat(payment.merchantId()).isEqualTo(MERCHANT);
    assertThat(payment.terminalId()).isEqualTo(TERMINAL_ID);
    assertThat(payment.order()).isEqualTo(ORDER);
    assertThat(payment.nativeStatus()).isEqualTo("0/00");
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(payment.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(payment.authorizationOpen()).isTrue();
    assertThat(payment.ledger().operations())
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.kind()).isEqualTo(OperationKind.AUTHORIZATION);
              assertThat(operation.amount()).isEqualTo(Amount.of("11.48"));
              assertThat(operation.closesAuthorization()).isNull();
              assertThat(operation.createdObjectId()).isNull();
              assertThat(operation.normalizedStatus())
                  .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
            });
  }

  @Test
  void an_approved_authorization_succeeds_outright_and_closes_the_hold_it_never_left_open() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    gateway().cgiLink(call(signed(authorization(Map.of(AzericardFields.TRTYPE, "1")))));

    AzericardPayment payment = onlyPayment();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.authorizationOpen()).isFalse();
    assertThat(payment.ledger().operations())
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.kind()).isEqualTo(OperationKind.CAPTURE);
              assertThat(operation.closesAuthorization()).isTrue();
            });
  }

  @ParameterizedTest(name = "echoes the amount {0} exactly as the merchant wrote it")
  @ValueSource(strings = {"10", "5", "1.00", "11.48", "10.0", "0009.90"})
  void echoes_the_amount_exactly_as_it_arrived(String amount) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response =
        gateway().cgiLink(call(signed(authorization(Map.of(AzericardFields.AMOUNT, amount)))));

    assertThat(bodyOf(response)).contains("<amount>" + amount + "</amount>");
    assertThat(onlyPayment().amount()).isEqualTo(Amount.of(amount));
  }

  @Test
  void echoes_the_currency_the_merchant_sent_rather_than_a_numeric_code_of_its_own() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response =
        gateway().cgiLink(call(signed(authorization(Map.of(AzericardFields.CURRENCY, "AZN")))));

    assertThat(bodyOf(response)).contains("<currency>AZN</currency>");
  }

  @Test
  void substitutes_its_own_timestamp_and_echoes_the_nonce_the_merchant_chose() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form =
        authorization(
            Map.of(AzericardFields.TIMESTAMP, "20260910114500", AzericardFields.NONCE, "abcdef01"));

    AzericardApiResponse response = gateway().cgiLink(call(signed(form)));

    assertThat(bodyOf(response))
        .contains("<timestamp>" + NOW_ON_THE_WIRE + "</timestamp>")
        .contains("<nonce>abcdef01</nonce>");
  }

  @Test
  void escapes_a_value_that_would_otherwise_close_an_element_of_its_own() {
    AzericardBody body =
        new AzericardResponse(
                "Approved & <safe>", "t", "0", "o", "1", "AZN", "0", "00", "a", "r", "i", "ts", "n")
            .signedWith(
                AzericardKeyFixture.privateKeyOf(AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8));

    assertThat(body.text()).contains("<desc>Approved &amp; &lt;safe&gt;</desc>");
  }

  @ParameterizedTest(name = "answers a request with no {0} with the human error page naming it")
  @CsvSource({
    "TERMINAL,merchant terminal",
    "TRTYPE,transaction type",
    "ORDER,merchant order",
    "AMOUNT,order total amount",
    "CURRENCY,order currency",
    "TIMESTAMP,merchant transaction timestamp",
    "NONCE,nonce",
    "P_SIGN,merchant MAC"
  })
  void names_the_field_it_cannot_use_on_the_human_error_page(String field, String phrase) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(authorization());
    form.remove(field);

    AzericardApiResponse response = gateway().cgiLink(call(form));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.contentType()).isEqualTo("text/html");
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .contains(AzericardErrorPage.OPENING)
        .contains("Error message: Error in " + phrase + " field")
        .contains(AzericardErrorPage.CLOSING)
        .doesNotContain("<action>")
        .doesNotContain("<rc>")
        .doesNotContain("<p_sign>");
    assertThat(response.headers())
        .as(
            "the payer's webview posts the signed form here, so the page this gateway answers a"
                + " refused request with is read by a browser like any other page it serves, and"
                + " carries the same four defences as the page it stands in for")
        .containsEntry("Cache-Control", List.of("no-store"))
        .containsEntry("X-Content-Type-Options", List.of("nosniff"))
        .containsEntry("Referrer-Policy", List.of("no-referrer"))
        .containsEntry(
            "Content-Security-Policy",
            List.of(
                "default-src 'none'; style-src 'unsafe-inline'; form-action 'self';"
                    + " frame-ancestors 'none'; base-uri 'none'"));
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void treats_a_field_sent_as_nothing_but_spaces_as_a_field_that_was_never_sent() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(authorization());
    form.put(AzericardFields.NONCE, "   ");

    assertThat(bodyOf(gateway().cgiLink(call(form)))).contains("Error in nonce field");
  }

  @Test
  void the_human_error_page_carries_no_field_the_audit_could_record() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(authorization());
    form.remove(AzericardFields.NONCE);

    gateway().cgiLink(call(form));

    assertThat(audit.all().get(1).sanitizedBody()).isEmpty();
  }

  @ParameterizedTest(name = "answers TRTYPE {0}, which this contract does not expose, as unusable")
  @ValueSource(strings = {"185", "3", "01", "23", "2", ""})
  void answers_a_transaction_type_it_does_not_expose_on_the_error_page(String trtype) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(authorization());
    form.put(AzericardFields.TRTYPE, trtype);

    AzericardApiResponse response = gateway().cgiLink(call(form));

    assertThat(bodyOf(response)).contains("Error in transaction type field");
    assertThat(payments.all()).isEmpty();
  }

  @ParameterizedTest(name = "refuses the order {0}, which is not the shape the vendor publishes")
  @ValueSource(strings = {"12345", "2022062808480012345678901234567890123", "2022062a", "abcdef"})
  void refuses_an_order_that_is_not_six_to_thirty_two_digits(String order) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response =
        gateway().cgiLink(call(signed(authorization(Map.of(AzericardFields.ORDER, order)))));

    assertThat(bodyOf(response)).contains("Error in merchant order field");
  }

  @Test
  void accepts_an_order_of_exactly_the_published_bounds() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    AzericardGateway gateway = gateway();

    assertThat(
            gateway
                .cgiLink(call(signed(authorization(Map.of(AzericardFields.ORDER, "123456")))))
                .outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(
            gateway
                .cgiLink(
                    call(
                        signed(
                            authorization(
                                Map.of(
                                    AzericardFields.ORDER, "12345678901234567890123456789012")))))
                .outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
  }

  @ParameterizedTest(name = "refuses the timestamp {0}, which is not YYYYMMDDHHMMSS")
  @ValueSource(strings = {"2026091012000", "202609101200000", "20261310120000", "2026091012000x"})
  void refuses_a_timestamp_that_is_not_the_published_format(String timestamp) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response =
        gateway()
            .cgiLink(call(signed(authorization(Map.of(AzericardFields.TIMESTAMP, timestamp)))));

    assertThat(bodyOf(response)).contains("Error in merchant transaction timestamp field");
  }

  @Test
  void answers_a_terminal_it_was_never_configured_with_on_the_error_page() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response =
        gateway()
            .cgiLink(call(signed(authorization(Map.of(AzericardFields.TERMINAL, "12341234")))));

    assertThat(bodyOf(response)).contains("Error in merchant terminal field");
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void answers_an_invalid_merchant_mac_as_a_signed_access_denial_and_never_as_a_4xx() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(authorization());
    form.put(AzericardFields.P_SIGN, form.get(AzericardFields.P_SIGN).replace('A', 'B'));

    AzericardApiResponse response = gateway().cgiLink(call(form));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(response.providerObjectId()).isNull();
    assertThat(bodyOf(response))
        .isEqualTo(
            "<response>"
                + "<desc>Access denied</desc>"
                + "<terminal>77777777</terminal>"
                + "<trtype>0</trtype>"
                + "<order>20220628084800</order>"
                + "<amount>11.48</amount>"
                + "<currency></currency>"
                + "<action>3</action>"
                + "<rc>-17</rc>"
                + "<approval></approval>"
                + "<rrn></rrn>"
                + "<intref></intref>"
                + "<timestamp>"
                + NOW_ON_THE_WIRE
                + "</timestamp>"
                + "<nonce>"
                + NONCE
                + "</nonce>"
                + "<p_sign>"
                + theExpectedSignatureOver("11.48", TERMINAL_ID, "", "", "")
                + "</p_sign>"
                + "</response>");
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void accepts_a_merchant_mac_written_in_lower_case_because_the_vendor_admits_either() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(authorization());
    form.put(AzericardFields.P_SIGN, form.get(AzericardFields.P_SIGN).toLowerCase(Locale.ROOT));

    assertThat(gateway().cgiLink(call(form)).outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void a_mac_taken_over_a_different_amount_is_refused_although_it_is_a_valid_signature() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(authorization());
    form.put(AzericardFields.AMOUNT, "12.48");

    assertThat(bodyOf(gateway().cgiLink(call(form)))).contains("<rc>-17</rc>");
  }

  @Test
  void checks_the_merchant_mac_before_the_timestamp_window() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> stale = authorization(Map.of(AzericardFields.TIMESTAMP, "20260910093000"));
    Map<String, String> form = signed(stale);
    form.put(AzericardFields.P_SIGN, form.get(AzericardFields.P_SIGN).replace('A', 'B'));

    assertThat(bodyOf(gateway().cgiLink(call(form))))
        .contains("<desc>Access denied</desc>")
        .contains("<rc>-17</rc>");
  }

  @ParameterizedTest(name = "refuses a timestamp {0}, further than an hour from the gateway clock")
  @ValueSource(strings = {"20260910104959", "20260910130001"})
  void refuses_a_timestamp_outside_the_published_one_hour_window(String timestamp) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response =
        gateway()
            .cgiLink(call(signed(authorization(Map.of(AzericardFields.TIMESTAMP, timestamp)))));

    assertThat(bodyOf(response)).contains("<action>3</action>").contains("<rc>-17</rc>");
    assertThat(payments.all()).isEmpty();
  }

  @ParameterizedTest(name = "accepts a timestamp {0}, exactly an hour from the gateway clock")
  @ValueSource(strings = {"20260910110000", "20260910130000"})
  void accepts_a_timestamp_exactly_an_hour_out_because_the_rule_forbids_exceeding_it(
      String timestamp) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response =
        gateway()
            .cgiLink(call(signed(authorization(Map.of(AzericardFields.TIMESTAMP, timestamp)))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void refuses_a_second_order_whose_trailing_six_digits_that_terminal_used_today() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization())));

    AzericardApiResponse repeated =
        gateway.cgiLink(
            call(signed(authorization(Map.of(AzericardFields.ORDER, "20230101084800")))));

    assertThat(repeated.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(repeated.providerObjectId()).isNull();
    assertThat(bodyOf(repeated))
        .contains("<action>1</action>")
        .contains("<rc>" + AzericardDuplicateCode.DEFAULT + "</rc>")
        .contains("<intref></intref>");
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void the_same_trailing_six_digits_on_another_terminal_are_not_a_duplicate() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    accounts.with(azericardAccount(E2E_RUN, "merchant-2", "88888888"));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization())));

    AzericardApiResponse other =
        gateway.cgiLink(call(signed(authorization(Map.of(AzericardFields.TERMINAL, "88888888")))));

    assertThat(other.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void the_same_trailing_six_digits_on_the_next_day_are_not_a_duplicate() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    gateway().cgiLink(call(signed(authorization())));

    Instant tomorrow = NOW.plus(Duration.ofDays(1));
    Map<String, String> form =
        authorization(Map.of(AzericardFields.TIMESTAMP, AzericardTimestamp.write(tomorrow)));

    assertThat(
            gatewayAt(tomorrow, new AzericardDoubles.CountingIds())
                .cgiLink(call(signed(form)))
                .outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void a_scenario_that_lets_the_call_through_does_not_let_a_duplicate_through() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.FixedSuccess(),
            null));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization())));

    AzericardApiResponse repeated = gateway.cgiLink(call(signed(authorization())));

    assertThat(repeated.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(repeated)).contains("<action>1</action>");
    assertThat(theOutboundAudit(1).scenarioId()).isNull();
  }

  @Test
  void a_scenario_that_lets_the_call_through_names_itself_on_the_answer_it_allowed() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    ScenarioRule rule =
        scenarioRules.add(
            new NewScenarioRule(
                AzericardGateway.AZERICARD,
                E2E_RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.FixedSuccess(),
                null));

    gateway().cgiLink(call(signed(authorization())));

    assertThat(theOutboundAudit(0).scenarioId()).isEqualTo(rule.scenarioId());
  }

  @Test
  void a_scenario_declines_the_authorization_as_the_issuer_code_it_names() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    ScenarioRule rule = declineEveryRequestOf(E2E_RUN, "05");

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(response.providerObjectId()).isEqualTo(FIRST_INT_REF);
    assertThat(bodyOf(response))
        .contains("<desc></desc>")
        .contains("<action>2</action>")
        .contains("<rc>05</rc>")
        .contains("<approval></approval>")
        .contains("<rrn>" + FIRST_RRN + "</rrn>")
        .contains("<intref>" + FIRST_INT_REF + "</intref>");
    assertThat(theOutboundAudit(0).scenarioId()).isEqualTo(rule.scenarioId());

    AzericardPayment declined = onlyPayment();
    assertThat(declined.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
    assertThat(declined.nativeStatus()).isEqualTo("2/05");
    assertThat(declined.approval()).isNull();
    assertThat(declined.ledger().operations()).isEmpty();
  }

  @Test
  void a_declined_authorization_consumes_the_trace_number_the_order_named() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    declineEveryRequestOf(E2E_RUN, "05");
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization())));

    assertThat(bodyOf(gateway.cgiLink(call(signed(authorization())))))
        .contains("<action>1</action>");
  }

  @Test
  void a_scenario_never_stands_in_for_the_merchant_mac() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.FixedSuccess(),
            null));
    Map<String, String> form = signed(authorization());
    form.put(AzericardFields.P_SIGN, form.get(AzericardFields.P_SIGN).replace('A', 'B'));

    assertThat(bodyOf(gateway().cgiLink(call(form)))).contains("<rc>-17</rc>");
  }

  @Test
  void a_transport_fault_damages_the_answer_of_a_payment_the_gateway_still_authorized() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()),
            null));

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(response.completesTheBody()).isFalse();
    assertThat(onlyPayment().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void an_answer_that_stops_short_of_its_declared_length_ends_the_connection_it_was_sent_on() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()),
            null));

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(response.declaredContentLength()).isGreaterThan(response.body().length);
    assertThat(response.headers())
        .containsEntry(DeliveredHeaders.CONNECTION, List.of(DeliveredHeaders.CLOSE));
  }

  @Test
  void an_answer_that_writes_every_byte_it_declared_ends_no_connection() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(response.declaredContentLength()).isEqualTo(response.body().length);
    assertThat(response.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(DeliveredHeaders.CONNECTION));
  }

  @Test
  void a_delayed_answer_is_handed_on_whole_with_the_delay_the_scenario_named() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TransportFaultAlways(
                new TransportFault.DelayedResponse(Duration.ofMillis(400))),
            null));

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(response.delay()).isEqualTo(Duration.ofMillis(400));
    assertThat(response.completesTheBody()).isTrue();
    assertThat(response.declaredContentLength()).isEqualTo(bodyOf(response).length());
    assertThat(onlyPayment().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void names_each_transaction_type_by_the_trtype_value_the_vendor_publishes_for_it() {
    assertThat(AzericardTransactionType.PRE_AUTHORIZATION.wireValue()).isEqualTo("0");
    assertThat(AzericardTransactionType.AUTHORIZATION.wireValue()).isEqualTo("1");
    assertThat(AzericardTransactionType.SALES_COMPLETION.wireValue()).isEqualTo("21");
    assertThat(AzericardTransactionType.ONLINE_REVERSAL.wireValue()).isEqualTo("22");
    assertThat(AzericardTransactionType.OFFLINE_REVERSAL.wireValue()).isEqualTo("24");
    assertThat(AzericardTransactionType.of("0"))
        .contains(AzericardTransactionType.PRE_AUTHORIZATION);
    assertThat(AzericardTransactionType.of("1")).contains(AzericardTransactionType.AUTHORIZATION);
    assertThat(AzericardTransactionType.of("23")).isEmpty();
  }

  @Test
  void reads_back_only_a_transaction_type_that_could_have_created_a_stored_payment() {
    assertThat(AzericardTransactionType.storedType("0"))
        .contains(AzericardTransactionType.PRE_AUTHORIZATION);
    assertThat(AzericardTransactionType.storedType("1"))
        .contains(AzericardTransactionType.AUTHORIZATION);
    assertThat(AzericardTransactionType.storedType("21")).isEmpty();
    assertThat(AzericardTransactionType.storedType("22")).isEmpty();
    assertThat(AzericardTransactionType.storedType("24")).isEmpty();
  }

  @Test
  void a_fault_answered_instead_of_the_operation_leaves_the_order_free_for_the_repeat() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.addAll(
        List.of(
            aStep(
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502)),
                ScenarioStory.INITIAL_STATE,
                "lost"),
            aStep(new ScenarioAction.FixedSuccess(), "lost", null)));
    AzericardGateway gateway = gateway();

    AzericardApiResponse lost = gateway.cgiLink(call(signed(authorization())));
    AzericardApiResponse repeated = gateway.cgiLink(call(signed(authorization())));

    assertThat(lost.httpStatus()).isEqualTo(502);
    assertThat(lost.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(repeated.httpStatus()).isEqualTo(200);
    assertThat(repeated.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void two_payments_of_one_terminal_stand_on_two_stories_of_their_own() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.addAll(
        List.of(
            aStep(new ScenarioAction.TerminalFailure("05"), ScenarioStory.INITIAL_STATE, "asked"),
            aStep(new ScenarioAction.FixedSuccess(), "asked", null)));
    AzericardGateway gateway = gateway();

    AzericardApiResponse first = gateway.cgiLink(call(signed(authorization())));
    AzericardApiResponse other =
        gateway.cgiLink(
            call(signed(authorization(Map.of(AzericardFields.ORDER, "20220628084801")))));

    assertThat(first.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(other.outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void resolves_the_test_run_from_the_terminal_the_request_was_sent_to() {
    accounts.with(azericardAccount(ANOTHER_RUN, "merchant-9", "88888888"));
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    gateway().cgiLink(call(signed(authorization())));

    assertThat(audit.all())
        .isNotEmpty()
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(E2E_RUN);
              assertThat(record.resolutionRule())
                  .isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
            });
  }

  @Test
  void refuses_to_choose_between_two_test_runs_configured_with_one_terminal() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    accounts.with(azericardAccount(ANOTHER_RUN, "merchant-2", TERMINAL_ID));

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(bodyOf(response)).contains("Error in merchant terminal field");
    assertThat(payments.all()).isEmpty();
    assertThat(audit.all())
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(TestRunId.UNKNOWN);
              assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.AMBIGUOUS);
            });
  }

  @Test
  void tells_two_test_runs_sharing_one_terminal_apart_by_the_header() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    accounts.with(azericardAccount(ANOTHER_RUN, "merchant-2", TERMINAL_ID));
    AzericardGateway gateway = gateway();

    AzericardApiResponse first =
        gateway.cgiLink(callNaming(E2E_RUN.value(), signed(authorization())));

    assertThat(first.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(payments.all())
        .singleElement()
        .satisfies(p -> assertThat(p.testRunId()).isEqualTo(E2E_RUN));
  }

  @Test
  void falls_back_to_the_default_test_run_when_no_terminal_matches() {
    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(bodyOf(response)).contains("Error in merchant terminal field");
    assertThat(audit.all())
        .isNotEmpty()
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(DEFAULT_RUN);
              assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.DEFAULT);
            });
  }

  @Test
  void audits_the_request_and_the_answer_of_every_call() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    gateway().cgiLink(call(signed(authorization())));

    List<AuditRecord> records = audit.of(AzericardGateway.AZERICARD, E2E_RUN);
    assertThat(records)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    assertThat(records)
        .allSatisfy(
            record -> {
              assertThat(record.method()).isEqualTo("POST");
              assertThat(record.path()).isEqualTo(ROUTE);
              assertThat(record.createdAt()).isEqualTo(NOW);
              assertThat(record.auditId()).startsWith("aud_");
            });
    assertThat(records.get(1).httpStatus()).isEqualTo(200);
    assertThat(records.get(1).providerObjectId()).isEqualTo(FIRST_INT_REF);
    assertThat(records.get(1).sanitizedBody())
        .containsEntry("action", List.of("0"))
        .containsEntry("intref", List.of(FIRST_INT_REF));
    assertThat(records.get(1).sanitizedHeaders())
        .containsEntry("Content-Type", List.of("text/html"));
    assertThat(records).allSatisfy(record -> assertThat(record.accountId()).isEqualTo(MERCHANT));
  }

  @Test
  void a_call_whose_terminal_reaches_no_account_of_the_run_leaves_every_row_naming_none() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(authorization());
    form.put(AzericardFields.TERMINAL, "99999999");

    gateway().cgiLink(call(form));

    assertThat(audit.all()).isNotEmpty();
    assertThat(audit.all()).allSatisfy(record -> assertThat(record.accountId()).isNull());
  }

  @Test
  void keeps_the_cardholder_browser_data_and_the_bearer_header_out_of_the_audit() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form =
        signed(authorization(Map.of(AzericardFields.M_INFO, "eyJhIjoxfQ==")));

    gateway().cgiLink(call(form));

    AuditRecord inbound = audit.of(AzericardGateway.AZERICARD, E2E_RUN).get(0);
    assertThat(inbound.sanitizedBody())
        .containsEntry(AzericardFields.M_INFO, List.of(Redaction.REDACTED))
        .containsEntry(AzericardFields.TERMINAL, List.of(TERMINAL_ID));
    assertThat(inbound.sanitizedHeaders())
        .containsEntry("Authorization", List.of(Redaction.REDACTED));
    assertThat(inbound.toString()).doesNotContain("eyJhIjoxfQ==");
  }

  @Test
  void draws_another_internal_reference_when_the_first_one_is_taken() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    payments.withTheIntRefsAlreadyClaimed(FIRST_INT_REF);

    AzericardApiResponse response = gateway().cgiLink(call(signed(authorization())));

    assertThat(response.providerObjectId()).isEqualTo("0000000000000002");
  }

  @Test
  void gives_up_rather_than_looping_when_no_internal_reference_can_be_claimed() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    payments.withTheIntRefsAlreadyClaimed(theFirstDrawnIntRefs(Authorize.ID_ATTEMPTS));
    AzericardGateway gateway = gateway();
    AzericardApiCall authorizing = call(signed(authorization()));

    assertThatThrownBy(() -> gateway.cgiLink(authorizing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INT_REF");
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void refuses_to_store_a_payment_under_an_internal_reference_the_table_already_holds() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    payments.rejectingTheNextInsert();
    AzericardGateway gateway = gateway();
    AzericardApiCall authorizing = call(signed(authorization()));

    assertThatThrownBy(() -> gateway.cgiLink(authorizing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INT_REF");
    assertThat(payments.all()).isEmpty();
  }

  private static String[] theFirstDrawnIntRefs(int count) {
    String[] drawn = new String[count];
    for (int index = 0; index < count; index++) {
      drawn[index] = "000000000000000" + (index + 1);
    }
    return drawn;
  }

  private NewScenarioRule aStep(ScenarioAction action, String whenState, String thenState) {
    return new NewScenarioRule(
        AzericardGateway.AZERICARD,
        E2E_RUN,
        0,
        ScenarioMatch.anyRequest(),
        action,
        new ScenarioStory("authorization-retries", StoryKeying.FLOW, whenState, thenState));
  }

  private ScenarioRule declineEveryRequestOf(TestRunId testRunId, String providerCode) {
    return scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            testRunId,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure(providerCode),
            null));
  }

  private AuditRecord theOutboundAudit(int index) {
    List<AuditRecord> outbound =
        audit.all().stream()
            .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
            .toList();
    return outbound.get(index);
  }

  private AzericardPayment onlyPayment() {
    assertThat(payments.all()).hasSize(1);
    return payments.all().get(0);
  }

  @Test
  void
      a_trail_row_and_the_request_fields_a_screen_lists_from_it_keep_every_field_of_a_signed_authorization_in_the_order_the_merchant_sent_them() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> sent = signed(authorization());

    gateway().cgiLink(call(sent));

    AuditRecord inbound = audit.of(AzericardGateway.AZERICARD, E2E_RUN).get(0);
    assertThat(inbound.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(sent).hasSizeGreaterThanOrEqualTo(12);
    assertThat(inbound.sanitizedBody().keySet())
        .as(
            "twelve fields or more have at least 479001600 orders, so a copy carrying no order of"
                + " its own landing on the order the merchant sent is not something one run does"
                + " by chance")
        .containsExactlyElementsOf(sent.keySet());
    assertThat(Disclosure.of(inbound.sanitizedBody(), false))
        .as("the trail screen lists the request fields of that row in the same order")
        .extracting(Disclosure.Field::name)
        .containsExactlyElementsOf(sent.keySet());
  }

  @Test
  void
      a_call_and_the_form_read_out_of_it_hand_their_fields_over_as_a_sequenced_map_no_caller_can_write_to() {
    AzericardApiCall call = call(signed(authorization()));

    assertThat(call.form()).isInstanceOf(SequencedMap.class);
    assertThat(call.headers()).isInstanceOf(SequencedMap.class);
    assertThat(AzericardForm.of(call.form()).values()).isInstanceOf(SequencedMap.class);
    assertThat(AzericardForm.of(call.form()).forAudit()).isInstanceOf(SequencedMap.class);
    assertThatThrownBy(() -> call.form().put("extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> call.headers().put("Extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private AzericardGateway gateway() {
    return gatewayAt(NOW, new AzericardDoubles.CountingIds());
  }

  private AzericardGateway gatewayAt(Instant now, IdGenerator ids) {
    accounts.all().forEach(terminal -> testRuns.register(terminal.testRunId()));
    return new AzericardGateway(
        accounts,
        testRuns,
        audit,
        AzericardDoubles.clockAt(now),
        ids,
        DEFAULT_RUN,
        new Authorize(payments, cards, ids, scenarioEngine),
        new CompleteOrReverse(payments, scenarioEngine),
        new LookUpStatus(payments, scenarioEngine),
        AzericardDoubles.checkoutOf(
            payments, cards, accounts, scenarioEngine, audit, AzericardDoubles.clockAt(now), ids));
  }

  private static String theExpectedSignatureOver(String... values) {
    return AzericardKeyFixture.signedWith(
        AzericardMac.sourceOf(values),
        AzericardKeyFixture.privateKeyOf(AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8));
  }

  private static String signatureIn(String xml) {
    int opening = xml.indexOf("<p_sign>") + "<p_sign>".length();
    return xml.substring(opening, xml.indexOf("</p_sign>"));
  }

  private static String bodyOf(AzericardApiResponse response) {
    return new String(response.body(), UTF_8);
  }

  private static AzericardTerminal azericardAccount(
      TestRunId testRunId, String accountId, String terminalId) {
    return AzericardDoubles.terminal(testRunId, accountId, terminalId);
  }

  private static Map<String, String> authorization() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "0");
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, "11.48");
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);
    form.put(AzericardFields.MERCH_URL, "https://merchant.example");
    form.put(AzericardFields.DESC, "IT Books. Qty: 2");
    form.put(AzericardFields.BACKREF, "https://merchant.example/backref");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> authorization(Map<String, String> overrides) {
    Map<String, String> form = authorization();
    form.putAll(overrides);
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

  private static AzericardApiCall call(Map<String, String> form) {
    return callWith(fieldsOf(form), Map.of());
  }

  private static AzericardApiCall callNaming(String testRunId, Map<String, String> form) {
    return callWith(fieldsOf(form), Map.of(TestRunHeader.NAME, List.of(testRunId)));
  }

  private static AzericardApiCall callWith(
      Map<String, List<String>> fields, Map<String, List<String>> extraHeaders) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Content-Type", List.of("application/x-www-form-urlencoded"));
    headers.put("Authorization", List.of("Bearer squirrel"));
    headers.putAll(extraHeaders);
    return new AzericardApiCall("POST", ROUTE, headers, fields);
  }
}
