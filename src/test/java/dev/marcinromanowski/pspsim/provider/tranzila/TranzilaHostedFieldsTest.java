package dev.marcinromanowski.pspsim.provider.tranzila;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TranzilaHostedFieldsTest {

  private static final String A_THTK = "w0123456789abcdef0123456789abcdef01234567a";

  private static final String THE_PUBLISHED_CARD = "4907639999909022";

  private static final String A_CVV = "737";

  private static final String A_NOTIFY_URL = "https://shop.example/notify";

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void a_debit_typed_into_the_fields_answers_the_published_shape_hashed_with_the_secret() {
    aHandshake("10.5");

    TranzilaApiResponse answered =
        charge(aCharge("10.5", "contact", "Dana Levi", "tokenize", "true"));

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(answered.headers())
        .containsEntry("Content-Type", List.of("application/json"))
        .containsEntry("Cache-Control", List.of("no-store"))
        .containsEntry("X-Content-Type-Options", List.of("nosniff"));
    assertThat(bodyOf(answered))
        .isEqualTo(
            "{\"errors\":null,\"transaction_response\":{\"success\":true,\"error\":null,"
                + "\"processor_response_code\":\"000\",\"transaction_id\":\"10000001\","
                + "\"auth_number\":\"1000001\",\"amount\":\"10.50\",\"currency_code\":1,"
                + "\"credit_card_last_4_digits\":\"9022\",\"expiry_month\":\"12\","
                + "\"expiry_year\":\"30\",\"user_form_data\":{\"contact\":\"Dana Levi\"},"
                + "\"card_type\":\"2\",\"card_mask\":\"************9022\","
                + "\"card_locality\":\"domestic\",\"txn_type\":\"debit\",\"tranmode\":\"A\","
                + "\"card_type_name\":\"Visa\",\"cvv_status\":\"1\",\"id_status\":\"0\","
                + "\"card_issuer\":\"1\",\"token\":\"t000000000000019022\",\"payment_plan\":1,"
                + "\"total_installments_number\":null},"
                + "\"response_hash\":\"20a2c647df59919439f8eb3dccea2f99f79b004d4f84141ee83591723843eeb9\"}");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.providerObjectId()).isEqualTo("10000001");
    TranzilaTransaction made = wired.theLastStored();
    assertThat(made.type()).isEqualTo(TranzilaTxnType.DEBIT);
    assertThat(made.card().cardMask()).isEqualTo("xxxxxxxxxxxx9022");
    assertThat(made.token()).isEqualTo("t000000000000019022");
    assertThat(made.responseCvv()).isEqualTo("1");
    assertThat(made.responseId()).isEqualTo("0");
    assertThat(made.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
  }

  @Test
  void a_v_holds_the_amount_as_a_j5_and_an_n_checks_the_card_as_a_j2() {
    aHandshake("42");

    String held = bodyOf(charge(aCharge("42", "tran_mode", "V", "tokenize", "true")));
    String checked = bodyOf(charge(aCharge("42", "tran_mode", "N")));

    assertThat(held)
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"txn_type\":\"verify\",\"tranmode\":\"V\"")
        .contains("\"token\":\"t000000000000019022\"");
    assertThat(checked)
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"txn_type\":\"verify\",\"tranmode\":\"N\"")
        .doesNotContain("\"token\"");
    assertThat(wired.stored()).extracting(TranzilaTransaction::verifyMode).containsExactly(5, 2);
    assertThat(wired.stored().get(0).holdExpiresAt())
        .isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
  }

  @Test
  void the_token_is_answered_only_when_tokenize_asks_for_it_and_kept_either_way() {
    aHandshake("10");

    String answered = bodyOf(charge(aCharge("10", "tokenize", "false")));

    assertThat(answered).doesNotContain("\"token\"");
    assertThat(wired.theLastStored().token()).isEqualTo("t000000000000019022");
  }

  @Test
  void a_charge_in_another_currency_is_made_in_it_and_answers_its_numeric_code() {
    aHandshake("10");

    String dollars = bodyOf(charge(aCharge("10", "currency_code", "USD")));
    String euros = bodyOf(charge(aCharge("10", "currency_code", "EUR")));

    assertThat(dollars).contains("\"currency_code\":2,");
    assertThat(euros).contains("\"currency_code\":978,");
    assertThat(wired.stored())
        .extracting(TranzilaTransaction::currencyCode)
        .containsExactly("USD", "EUR");
  }

  @Test
  void a_declined_charge_answers_success_false_with_the_codes_text_and_no_token() {
    aHandshake("10");
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));

    TranzilaApiResponse answered = charge(aCharge("10", "tokenize", "true"));

    assertThat(bodyOf(answered))
        .startsWith(
            "{\"errors\":null,\"transaction_response\":{\"success\":false,\"error\":\"Refusal."
                + " Please contact the card owner to check the reason with his credit company.\","
                + "\"processor_response_code\":\"004\",\"transaction_id\":\"10000001\","
                + "\"auth_number\":null,")
        .doesNotContain("\"token\"");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).refusal())
        .isEqualTo(RefusalSubject.theObject("10000001"));
  }

  @Test
  void an_approved_charge_renews_the_handshake_and_a_declined_one_does_not() {
    aHandshake("10");
    wired.clock.moveTo(Instant.parse("2026-10-13T12:05:00Z"));

    charge(aCharge("10"));
    Instant renewed = wired.handshakes.all().get(0).expiresAt();
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    wired.clock.moveTo(Instant.parse("2026-10-13T12:10:00Z"));
    charge(aCharge("10"));

    assertThat(renewed).isEqualTo(Instant.parse("2026-10-13T12:25:00Z"));
    assertThat(wired.handshakes.all().get(0).expiresAt())
        .isEqualTo(Instant.parse("2026-10-13T12:25:00Z"));
  }

  @Test
  void a_charge_without_a_thtk_is_refused_with_the_published_code_and_charges_nothing() {
    Map<String, List<String>> asked = aCharge("10");
    asked.remove("thtk");

    TranzilaApiResponse answered = charge(asked);

    assertThat(bodyOf(answered))
        .isEqualTo(
            "{\"errors\":[{\"code\":10000,\"param\":\"thtk\",\"message\":\"MANDATORY KEY IS"
                + " MISSING\"}],\"transaction_response\":null,"
                + "\"response_hash\":\"1a7ffd87c88dadcadbdc654f5d16c14d6619c1b69730cff4ab583e92ba5ead51\"}");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(wired.stored()).isEmpty();
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).refusal())
        .isEqualTo(RefusalSubject.noObjectWasNamed("10000 thtk MANDATORY KEY IS MISSING"));
  }

  @Test
  void an_amount_the_thtk_does_not_lock_is_an_illegal_operation_and_charges_nothing() {
    aHandshake("10");

    String answered = bodyOf(charge(aCharge("11")));

    assertThat(answered)
        .startsWith(
            "{\"errors\":[{\"code\":912791,\"param\":\"thtk\",\"message\":\"Illegal Operation"
                + " 912791\"}],\"transaction_response\":null,\"response_hash\":\"");
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void a_thtk_past_its_twenty_minutes_or_of_another_terminal_locks_nothing() {
    aHandshake("10");
    wired.handshakes.insert(
        new TranzilaHandshake(
            "w1",
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            "another",
            "10",
            null,
            TranzilaWiring.NOW,
            TranzilaWiring.NOW.plus(Duration.ofMinutes(20))));

    String ofAnother = bodyOf(charge(aChargeUnder("w1", "10")));
    wired.clock.moveTo(Instant.parse("2026-10-13T12:20:00Z"));
    String lapsed = bodyOf(charge(aCharge("10")));

    assertThat(ofAnother).contains("\"code\":912791");
    assertThat(lapsed).contains("\"code\":912791");
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void a_terminal_no_run_configures_is_not_found_and_its_answer_carries_no_hash() {
    Map<String, List<String>> asked = aCharge("10");
    asked.put("terminal_name", List.of("nobody"));

    TranzilaApiResponse answered = charge(asked);

    assertThat(bodyOf(answered))
        .isEqualTo(
            "{\"errors\":[{\"code\":10013,\"param\":\"terminal_name\",\"message\":\"TERMINAL NOT"
                + " FOUND OR BAD CONFIGURATION\"}],\"transaction_response\":null,"
                + "\"response_hash\":null}");
    assertThat(theRow(AuditDirection.INBOUND_REQUEST).accountId()).isNull();
  }

  @Test
  void every_refusal_of_one_request_is_answered_together_in_the_order_the_table_reads() {
    Map<String, List<String>> asked = new LinkedHashMap<>();
    asked.put("terminal_name", List.of(TranzilaWiring.TERMINAL));
    asked.put("thtk", List.of(A_THTK));
    asked.put("amount", List.of("0"));
    asked.put("tran_mode", List.of("K"));
    asked.put("payment_plan", List.of("6"));
    asked.put("credit_card_number", List.of("4907639999909023"));
    asked.put("expiry_month", List.of("12"));
    asked.put("expiry_year", List.of("2030"));

    String answered = bodyOf(charge(asked));

    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).refusal())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                "10006 amount INVALID AMOUNT OR AMOUNT ZERO; 10014 tran_mode INVALID TRAN MODE;"
                    + " 10007 payment_plan INVALID PAYMENT PLAN; 10002 credit_card_number INVALID"
                    + " CREDIT CARD NUMBER"));
    assertThat(answered)
        .startsWith(
            "{\"errors\":[{\"code\":10006,\"param\":\"amount\",\"message\":\"INVALID AMOUNT OR"
                + " AMOUNT ZERO\"},{\"code\":10014,\"param\":\"tran_mode\",\"message\":\"INVALID"
                + " TRAN MODE\"},{\"code\":10007,\"param\":\"payment_plan\",\"message\":\"INVALID"
                + " PAYMENT PLAN\"},{\"code\":10002,\"param\":\"credit_card_number\","
                + "\"message\":\"INVALID CREDIT CARD NUMBER\"}],\"transaction_response\":null,");
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void a_dcdisable_already_approved_answers_the_original_and_charges_nothing_more() {
    aHandshake("10");

    String first = bodyOf(charge(aCharge("10", "DCdisable", "order-77", "tokenize", "true")));
    String again = bodyOf(charge(aCharge("10", "DCdisable", "order-77", "tokenize", "true")));

    assertThat(wired.stored()).hasSize(1);
    assertThat(again)
        .contains("\"transaction_id\":\"10000001\"")
        .contains("\"user_form_data\":{\"DCdisable\":\"order-77\"}")
        .contains("\"token\":\"t000000000000019022\"");
    assertThat(again.substring(0, again.indexOf("response_hash")))
        .isEqualTo(first.substring(0, first.indexOf("response_hash")));
    assertThat(wired.stored().get(0).dcDisable()).isEqualTo("order-77");
  }

  @Test
  void a_duplicate_does_not_renew_the_handshake_its_original_renewed() {
    aHandshake("10");
    charge(aCharge("10", "DCdisable", "order-77"));
    wired.clock.moveTo(Instant.parse("2026-10-13T12:10:00Z"));

    charge(aCharge("10", "DCdisable", "order-77"));

    assertThat(wired.handshakes.all().get(0).expiresAt())
        .isEqualTo(Instant.parse("2026-10-13T12:20:00Z"));
  }

  @Test
  void a_typed_card_a_fixture_of_the_run_names_charges_that_fixtures_token() {
    aHandshake("10");
    wired.fixtures.seed(
        new NewPaymentMethodFixture(
            TranzilaApi.TRANZILA,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            "owner",
            PaymentMethodKind.CARD,
            "visa-9022",
            new FixtureCard("visa", "9022", 2, 2031, "credit"),
            Map.of("token", "seededTok9022"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());

    String answered = bodyOf(charge(aCharge("10", "tokenize", "true")));

    assertThat(answered).contains("\"token\":\"seededTok9022\"");
    assertThat(wired.theLastStored().token()).isEqualTo("seededTok9022");
    assertThat(wired.theLastStored().card().expiryYear()).isEqualTo(30);
  }

  @Test
  void a_fixture_ending_alike_but_of_another_card_type_is_not_the_card_typed() {
    aHandshake("10");
    wired.fixtures.seed(
        new NewPaymentMethodFixture(
            TranzilaApi.TRANZILA,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            "owner",
            PaymentMethodKind.CARD,
            "mastercard-9022",
            new FixtureCard("mastercard", "9022", 2, 2031, "credit"),
            Map.of("token", "seededTok9022"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());

    charge(aCharge("10", "tokenize", "true"));

    assertThat(wired.theLastStored().token()).isEqualTo("t000000000000019022");
    assertThat(wired.theLastStored().card().cardType()).isEqualTo(TranzilaCard.VISA);
  }

  @Test
  void a_currency_no_page_numbers_is_named_rather_than_answered() {
    assertThat(TranzilaHostedFieldsAnswer.theNumericCodeOf("GBP")).isEqualTo("826");
    assertThatThrownBy(() -> TranzilaHostedFieldsAnswer.theNumericCodeOf("JPY"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("JPY has no numeric code the hosted fields answer could carry");
  }

  @Test
  void a_rule_answering_instead_of_the_charge_is_delivered_as_it_stands() {
    aHandshake("10");
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));

    TranzilaApiResponse answered = charge(aCharge("10"));

    assertThat(answered.httpStatus()).isEqualTo(502);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(answered.headers()).containsEntry("Cache-Control", List.of("no-store"));
    assertThat(wired.stored()).isEmpty();
    AuditRecord row = theRow(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(row.httpStatus()).isEqualTo(Integer.valueOf(502));
    assertThat(row.scenarioId()).isNotNull();
    assertThat(row.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(row.delivered()).isNotNull();
  }

  @Test
  void a_rule_disrupting_the_charge_faults_the_answer_after_the_charge_was_made() {
    aHandshake("10");
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(503)));

    TranzilaApiResponse answered = charge(aCharge("10"));

    assertThat(answered.httpStatus()).isEqualTo(503);
    assertThat(wired.stored()).hasSize(1);
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).httpStatus())
        .isEqualTo(Integer.valueOf(503));
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).scenarioId()).isNotNull();
  }

  @Test
  void the_trail_keeps_neither_the_typed_card_nor_the_cvv_nor_the_ids_nor_the_token() {
    aHandshake("10");

    TranzilaApiResponse answered =
        charge(
            aCharge(
                "10",
                "card_holder_id_number",
                "123456782",
                "identity_number",
                "987654321",
                "tokenize",
                "true"));

    AuditRecord inbound = theRow(AuditDirection.INBOUND_REQUEST);
    AuditRecord outbound = theRow(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(inbound.sanitizedBody())
        .containsEntry("credit_card_number", List.of(Redaction.REDACTED))
        .containsEntry("cvv", List.of(Redaction.REDACTED))
        .containsEntry("card_holder_id_number", List.of(Redaction.REDACTED))
        .containsEntry("identity_number", List.of(Redaction.REDACTED))
        .containsEntry("expiry_month", List.of("12"));
    assertThat(inbound.path()).isEqualTo("/tranzila/hf/process/ajax_process_transaction");
    assertThat(inbound.resolutionRule()).isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
    assertThat(inbound.accountId()).isEqualTo(TranzilaWiring.ACCOUNT);
    assertThat(outbound.sanitizedBody())
        .containsEntry("transaction_response.token", List.of(Redaction.REDACTED))
        .containsEntry(
            "transaction_response.user_form_data.identity_number", List.of(Redaction.REDACTED))
        .containsEntry("transaction_response.id_status", List.of("1"))
        .containsEntry("transaction_response.card_mask", List.of("************9022"));
    assertThat(outbound.providerObjectId()).isEqualTo("10000001");
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.delivered()).isNotNull();
    assertThat(wired.audit.all().toString() + wired.stored() + bodyOf(answered))
        .doesNotContain(THE_PUBLISHED_CARD)
        .doesNotContain("490763")
        .doesNotContain("123456782");
    assertThat(wired.theLastStored().responseId()).isEqualTo("1");
  }

  @Test
  void the_script_and_the_frames_are_served_as_themselves() {
    TranzilaApiResponse script = wired.hostedFields.script();
    TranzilaApiResponse field = wired.hostedFields.field();
    TranzilaApiResponse control = wired.hostedFields.control();

    assertThat(new String(script.body(), StandardCharsets.UTF_8))
        .isEqualTo(TranzilaWiring.THE_SCRIPT);
    assertThat(script.httpStatus()).isEqualTo(200);
    assertThat(script.headers())
        .containsEntry("Content-Type", List.of("text/javascript; charset=utf-8"))
        .containsEntry("Cache-Control", List.of("no-cache"))
        .containsEntry("X-Content-Type-Options", List.of("nosniff"))
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Content-Security-Policy"));
    assertThat(new String(field.body(), StandardCharsets.UTF_8))
        .isEqualTo(TranzilaHostedFieldsPage.THE_DOCUMENT);
    assertThat(field.headers())
        .containsEntry("Content-Type", List.of("text/html; charset=utf-8"))
        .containsEntry("Cache-Control", List.of("no-store"))
        .containsEntry(
            "Content-Security-Policy",
            List.of(
                "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-"
                    + PayerScriptHashes.of(TranzilaHostedFieldsPage.THE_SCRIPT)
                    + "'"));
    assertThat(new String(control.body(), StandardCharsets.UTF_8))
        .isEqualTo(TranzilaHostedControlPage.THE_DOCUMENT);
    assertThat(control.headers())
        .containsEntry(
            "Content-Security-Policy",
            List.of(
                "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-"
                    + PayerScriptHashes.of(TranzilaHostedControlPage.THE_SCRIPT)
                    + "'; connect-src 'self'"));
    assertThat(wired.audit.all()).isEmpty();
  }

  @Test
  void the_kept_script_is_the_one_on_the_classpath() {
    assertThat(new String(TranzilaHostedFieldsScript.read(), StandardCharsets.UTF_8))
        .startsWith("var TzlaHostedFields = (function () {")
        .contains("create: function (config)")
        .contains("base + \"/assets/js/hfields_depend/controlfields.html?instance_identifier=\"")
        .contains("base + \"/assets/js/hfields_depend/genfield.php?field_name=\"");
  }

  @Test
  void a_script_missing_from_the_classpath_is_named_rather_than_served_empty() {
    assertThatThrownBy(() -> TranzilaHostedFieldsScript.read("/tranzila/hf/nothing.js"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "/tranzila/hf/nothing.js is not on the classpath, so this build has no"
                + " TzlaHostedFields script to serve");
  }

  @Test
  void an_admitted_charge_the_card_api_still_refuses_is_a_defect_named_by_its_answer() {
    TranzilaOperationResult refused =
        TranzilaAnswers.refusedSchema("a field this simulator's own call carried");

    assertThat(TranzilaHostedFields.theCardApiRefusedWhatItAdmitted(refused))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "the card API refused a charge the hosted fields admitted, which the hosted fields'"
                + " own checks are there to make impossible: {\"error_code\":\"20004\","
                + "\"message\":\"JSON Schema mismatch: a field this simulator's own call"
                + " carried\"}");
  }

  @Test
  void a_charge_naming_no_terminal_is_refused_unhashed_in_the_default_run() {
    TranzilaApiResponse answered = charge(new LinkedHashMap<>());

    assertThat(bodyOf(answered))
        .startsWith("{\"errors\":[{\"code\":10000,\"param\":\"terminal_name\",")
        .endsWith("],\"transaction_response\":null,\"response_hash\":null}");
    assertThat(answered.testRunId()).isEqualTo(TranzilaWiring.RUN);
    assertThat(theRow(AuditDirection.INBOUND_REQUEST).accountId()).isNull();
  }

  @Test
  void a_thtk_resolves_the_run_holding_its_handshake_when_two_runs_configure_the_terminal() {
    TestRunId other = TestRunId.of("tranzila-other");
    wired.terminals.with(TranzilaDoubles.terminal(other, "account-2", TranzilaWiring.TERMINAL));
    wired.handshakes.insert(
        new TranzilaHandshake(
            A_THTK,
            other,
            "account-2",
            TranzilaWiring.TERMINAL,
            "10",
            null,
            TranzilaWiring.NOW,
            TranzilaWiring.NOW.plus(Duration.ofMinutes(20))));

    TranzilaApiResponse answered = charge(aCharge("10"));

    assertThat(answered.testRunId()).isEqualTo(other);
    assertThat(wired.theLastStored().testRunId()).isEqualTo(other);
    assertThat(wired.theLastStored().accountId()).isEqualTo("account-2");
    assertThat(theRow(AuditDirection.INBOUND_REQUEST).testRunId()).isEqualTo(other);
    assertThat(bodyOf(answered)).contains("\"success\":true");
  }

  @Test
  void the_hash_is_written_over_what_json_dumps_with_ensure_ascii_writes() {
    TranzilaJson.JsonObject response =
        TranzilaJson.object(Map.of("contact", TranzilaJson.text("Dana\u007fL\u00e9vi \u05d3")));

    String answered =
        TranzilaJsonWriter.toText(
            TranzilaHostedFieldsAnswer.answered(null, response, "secret-account-1"));

    assertThat(answered)
        .as(
            "DEL and every character above it are escaped as Python's json.dumps escapes them,"
                + " so a shop re-serialising in Python recomputes the same hash")
        .isEqualTo(
            "{\"errors\":null,\"transaction_response\":{\"contact\":\"Dana\\u007fL\\u00e9vi"
                + " \\u05d3\"},\"response_hash\":"
                + "\"4461467fb37ea16d208895a30a163a88b32d3b8e77ee9b0b60c9a9ec87815265\"}");
  }

  @Test
  void an_amount_is_answered_in_two_places_as_every_published_example_writes_it() {
    assertThat(TranzilaHostedFieldsAnswer.inTwoPlaces("10")).isEqualTo("10.00");
    assertThat(TranzilaHostedFieldsAnswer.inTwoPlaces("42.5")).isEqualTo("42.50");
    assertThat(TranzilaHostedFieldsAnswer.inTwoPlaces("0.01")).isEqualTo("0.01");
    assertThat(TranzilaHostedFieldsAnswer.inTwoPlaces("1234567890123.99"))
        .isEqualTo("1234567890123.99");
  }

  @Test
  void a_charge_notifies_its_notify_url_in_the_payment_pages_format() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshakeWith("10.5", "{\"order_id\":\"ORD-1\",\"contact\":\"from the handshake\"}");

    charge(aCharge("10.5", "contact", "Dana Levi", "notify_url", A_NOTIFY_URL, "tokenize", "true"));

    assertThat(wired.posted)
        .containsExactly(
            A_NOTIFY_URL
                + " supplier=myterminal&sum=10.5&currency=1&Response=000&contact=Dana+Levi"
                + "&notify_url=https%3A%2F%2Fshop.example%2Fnotify&transaction_id=10000001"
                + "&index=10000001&ccno=9022&Responsecv=1&Responseid=0&cardtype=2&cardissuer=1"
                + "&cardacquirer=2&TranzilaTK=t000000000000019022&txn_type=debit&tranmode=A"
                + "&order_id=ORD-1&ConfirmationCode=1000001");
  }

  @Test
  void a_declined_charge_is_notified_without_a_token_and_a_duplicate_is_not_notified() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshake("10");
    String first =
        bodyOf(charge(aCharge("10", "DCdisable", "order-1", "tran_mode", "V", "tokenize", "true")));
    String duplicate =
        bodyOf(charge(aCharge("10", "DCdisable", "order-1", "tran_mode", "V", "tokenize", "true")));
    wired.writes(TranzilaOperations.VERIFY, new ScenarioAction.TerminalFailure("004"));

    charge(aCharge("10", "tran_mode", "V", "tokenize", "true"));

    assertThat(duplicate)
        .as("the duplicate answers the original transaction, which was notified once")
        .startsWith(
            "{\"errors\":null,\"transaction_response\":{\"success\":true,\"error\":null,"
                + "\"processor_response_code\":\"777\",\"transaction_id\":\"10000001\",")
        .isEqualTo(first);
    assertThat(wired.posted).hasSize(2);
    assertThat(wired.posted.get(0))
        .contains("&Response=777&")
        .contains("&TranzilaTK=t000000000000019022&txn_type=verify&tranmode=V&");
    assertThat(wired.posted.get(1))
        .contains("&Response=004&")
        .doesNotContain("TranzilaTK")
        .doesNotContain("ConfirmationCode")
        .endsWith("&txn_type=verify&tranmode=V");
  }

  @Test
  void a_charge_without_tokenize_notifies_no_token_and_one_account_target_stands_in() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshake("10");

    charge(aCharge("10"));

    assertThat(wired.posted).singleElement().asString().startsWith(A_NOTIFY_URL + " supplier=");
    assertThat(wired.posted.get(0)).doesNotContain("TranzilaTK");
  }

  @Test
  void a_notify_url_no_callback_target_of_the_account_names_is_refused_and_charges_nothing() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshake("10");

    String answered =
        bodyOf(charge(aCharge("10", "notify_url", "https://elsewhere.example/notify")));

    assertThat(answered)
        .startsWith(
            "{\"errors\":[{\"code\":400,\"param\":\"notify_url\",\"message\":\""
                + TranzilaNotify.NOT_A_TARGET_OF_THE_ACCOUNT
                + "\"}],\"transaction_response\":null,");
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.posted).isEmpty();
  }

  @Test
  void the_notify_sets_its_own_fields_over_any_the_merchant_sent_under_the_same_names() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshake("10");
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));

    charge(
        aCharge(
            "10",
            "sum",
            "999",
            "Response",
            "000",
            "ccno",
            "1111",
            "TranzilaTK",
            "forged",
            "ConfirmationCode",
            "7777777",
            "contact",
            "Dana"));

    assertThat(wired.posted)
        .containsExactly(
            A_NOTIFY_URL
                + " supplier=myterminal&sum=10&currency=1&Response=004&contact=Dana"
                + "&transaction_id=10000001&index=10000001&ccno=9022&Responsecv=1&Responseid=0"
                + "&cardtype=2&cardissuer=1&cardacquirer=2&txn_type=debit&tranmode=A");
  }

  @Test
  void a_number_only_the_isracard_check_accepts_is_charged_and_answered_as_an_isracard() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshake("10");
    Map<String, List<String>> asked = aCharge("10");
    asked.put("credit_card_number", List.of("40000001"));

    String answered = bodyOf(charge(asked));

    assertThat(answered)
        .contains("\"credit_card_last_4_digits\":\"0001\"")
        .contains("\"card_type\":\"5\",\"card_mask\":\"****0001\"")
        .contains("\"card_type_name\":\"Isracard\"");
    assertThat(wired.theLastStored().card())
        .isEqualTo(new TranzilaCard("xxxx0001", TranzilaCard.ISRACARD, 12, 30));
    assertThat(wired.posted.get(0)).contains("&cardtype=5&cardissuer=1&cardacquirer=1&");
  }

  @Test
  void the_notify_withholds_the_token_and_the_id_it_carries() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("TranzilaTK", "t1");
    fields.put("identity_number", "987654321");
    fields.put("contact", "Dana");

    assertThat(TranzilaNotify.payloadOf(fields).sanitizedBody())
        .containsEntry("TranzilaTK", List.of(Redaction.REDACTED))
        .containsEntry("identity_number", List.of(Redaction.REDACTED))
        .containsEntry("contact", List.of("Dana"));
  }

  private void aHandshakeWith(String sum, String requestParams) {
    wired.handshakes.insert(
        new TranzilaHandshake(
            A_THTK,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            sum,
            requestParams,
            TranzilaWiring.NOW,
            TranzilaWiring.NOW.plus(Duration.ofMinutes(20))));
  }

  private void aHandshake(String sum) {
    wired.handshakes.insert(
        new TranzilaHandshake(
            A_THTK,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            sum,
            null,
            TranzilaWiring.NOW,
            TranzilaWiring.NOW.plus(Duration.ofMinutes(20))));
  }

  private Map<String, List<String>> aCharge(String amount, String... more) {
    return aChargeUnder(A_THTK, amount, more);
  }

  private static Map<String, List<String>> aChargeUnder(
      String thtk, String amount, String... more) {
    Map<String, List<String>> asked = new LinkedHashMap<>();
    asked.put("terminal_name", List.of(TranzilaWiring.TERMINAL));
    asked.put("thtk", List.of(thtk));
    asked.put("amount", List.of(amount));
    asked.put("credit_card_number", List.of(THE_PUBLISHED_CARD));
    asked.put("cvv", List.of(A_CVV));
    asked.put("expiry_month", List.of("12"));
    asked.put("expiry_year", List.of("2030"));
    for (int at = 0; at < more.length; at += 2) {
      asked.put(more[at], List.of(more[at + 1]));
    }
    return asked;
  }

  private TranzilaApiResponse charge(Map<String, List<String>> asked) {
    return wired.hostedFields.charge(
        TranzilaPageCall.of("POST", "/tranzila/hf/process/ajax_process_transaction", asked));
  }

  private static String bodyOf(TranzilaApiResponse answered) {
    return new String(answered.body(), StandardCharsets.UTF_8);
  }

  private AuditRecord theRow(AuditDirection direction) {
    List<AuditRecord> rows =
        wired.audit.all().stream().filter(row -> row.direction() == direction).toList();
    return rows.get(rows.size() - 1);
  }
}
