package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class CreateTransactionTest {

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void an_approved_debit_answers_every_field_of_the_published_create_answer() {
    assertThat(wired.answered(TranzilaWire.aDebit()))
        .isEqualTo(
            "{\"error_code\":0,\"message\":\"success\",\"transaction_result\":{"
                + "\"processor_response_code\":\"000\",\"transaction_id\":10000001,"
                + "\"auth_number\":\"1000001\",\"Responsecvv\":\"0\",\"Responseid\":\"0\","
                + "\"card_type\":2,\"card_type_name\":\"Visa\",\"currency_code\":\"ILS\","
                + "\"expiry_month\":7,\"expiry_year\":30,\"payment_plan\":1,"
                + "\"token\":\"t000000000000014245\",\"last_4\":\"4245\","
                + "\"card_mask\":\"458021xxxxxx4245\",\"card_locality\":\"domestic\","
                + "\"amount\":10.53,\"txn_type\":\"debit\",\"tranmode\":\"A\"},"
                + "\"original_request\":{\"terminal_name\":\"myterminal\","
                + "\"txn_type\":\"debit\",\"card_number\":\"458021xxxxxx4245\","
                + "\"expire_month\":7,\"expire_year\":2030,\"items\":[{\"name\":\"a\","
                + "\"units_number\":1,\"unit_price\":10.53}]}}");
    TranzilaTransaction stored = wired.theLastStored();
    assertThat(stored.transactionId()).isEqualTo(10000001L);
    assertThat(stored.rootTxnId()).isEqualTo(10000001L);
    assertThat(stored.referenceTxnId()).isNull();
    assertThat(stored.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(stored.createdAt()).isEqualTo(TranzilaWiring.NOW);
    assertThat(stored.terminalName()).isEqualTo(TranzilaWiring.TERMINAL);
    assertThat(stored.accountId()).isEqualTo(TranzilaWiring.ACCOUNT);
    assertThat(stored.holdExpiresAt()).isNull();
    assertThat(stored.dcDisable()).isNull();
  }

  @Test
  void a_debit_with_a_cvv_and_an_id_answers_both_entered_and_echoes_the_cvv_masked() {
    String answered =
        wired.answered(
            TranzilaWire.aDebit()
                .replace("}]}", "}],\"cvv\":\"123\",\"card_holder_id\":\"123456789\"}"));

    assertThat(answered)
        .contains("\"Responsecvv\":\"1\",\"Responseid\":\"1\"")
        .contains("\"cvv\":\"***\",\"card_holder_id\":\"123456789\"");
    assertThat(wired.theLastStored().responseCvv()).isEqualTo("1");
    assertThat(wired.theLastStored().responseId()).isEqualTo("1");
  }

  @Test
  void a_j5_is_approved_777_by_default_and_held_for_the_validity_of_the_account() {
    String answered = wired.answered(TranzilaWire.aJ5("100"));

    assertThat(answered)
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"txn_type\":\"verify\",\"tranmode\":\"V\"")
        .contains("\"amount\":100");
    assertThat(wired.theLastStored().verifyMode()).isEqualTo(5);
    assertThat(wired.theLastStored().holdExpiresAt())
        .isEqualTo(TranzilaWiring.NOW.plus(Duration.ofDays(7)));
  }

  @Test
  void a_j5_of_an_account_naming_the_vendor_answer_is_approved_000_for_its_own_validity() {
    wired.configuring(
        Map.of(
            TranzilaAuthentication.SETTING,
            TranzilaAuthentication.THE_ACCESS_TOKEN_IS_NOT_VERIFIED.name(),
            TranzilaVerificationApprovalCode.SETTING,
            "000",
            TranzilaJ5Validity.SETTING,
            "PT1H"));

    assertThat(wired.answered(TranzilaWire.aJ5("100")))
        .contains("\"processor_response_code\":\"000\"");
    assertThat(wired.theLastStored().holdExpiresAt())
        .isEqualTo(TranzilaWiring.NOW.plus(Duration.ofHours(1)));
  }

  @Test
  void a_j2_is_approved_at_the_verification_code_holds_nothing_and_answers_tranmode_n() {
    String answered = wired.answered(TranzilaWire.aJ2());

    assertThat(answered)
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"tranmode\":\"N\"");
    assertThat(wired.theLastStored().holdExpiresAt()).isNull();
    assertThat(wired.theLastStored().verifyMode()).isEqualTo(2);
  }

  @Test
  void a_j2_without_items_checks_the_card_for_no_amount() {
    String answered =
        wired.answered(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"verify\",\"verify_mode\":2,"
                + "\"card_number\":\"4580210000004245\",\"expire_month\":7,\"expire_year\":2030}");

    assertThat(answered).contains("\"amount\":0");
    assertThat(wired.theLastStored().amount()).isEqualTo("0");
  }

  @Test
  void the_amount_is_the_sum_of_every_item_in_the_lexical_form_of_its_prices() {
    String answered =
        wired.answered(
            "{\"terminal_name\":\"myterminal\",\"card_number\":\"4580210000004245\","
                + "\"expire_month\":7,\"expire_year\":2030,\"txn_currency_code\":\"USD\","
                + "\"items\":[{\"name\":\"a\",\"units_number\":2,\"unit_price\":10.50},"
                + "{\"name\":\"b\",\"units_number\":3,\"unit_price\":1.5}]}");

    assertThat(answered).contains("\"amount\":25.50").contains("\"currency_code\":\"USD\"");
    assertThat(wired.theLastStored().amount()).isEqualTo("25.50");
    assertThat(wired.theLastStored().currencyCode()).isEqualTo("USD");
  }

  @Test
  void a_mastercard_number_answers_card_type_1() {
    String answered =
        wired.answered(
            TranzilaWire.withCard("\"txn_type\":\"debit\"", TranzilaWire.A_MASTERCARD, "5"));

    assertThat(answered)
        .contains("\"card_type\":1,\"card_type_name\":\"Mastercard\"")
        .contains("\"card_mask\":\"532610xxxxxx1234\"")
        .contains("\"token\":\"t000000000000011234\"");
  }

  @Test
  void a_token_an_earlier_answer_carried_charges_the_same_card_and_answers_the_same_token() {
    String first = wired.answered(TranzilaWire.aDebit());
    String token = TranzilaWire.textIn(first, "token");

    String second = wired.answered(TranzilaWire.withCard("\"txn_type\":\"debit\"", token, "2"));

    assertThat(TranzilaWire.textIn(second, "token")).isEqualTo(token);
    assertThat(second).contains("\"card_mask\":\"458021xxxxxx4245\"").contains("\"card_type\":2");
    assertThat(second).contains("\"card_number\":\"t00000xxxxxxxxx4245\"");
  }

  @Test
  void a_token_answered_with_a_new_expiry_charges_with_the_expiry_the_request_carried() {
    String token = TranzilaWire.textIn(wired.answered(TranzilaWire.aDebit()), "token");

    String second =
        wired.answered(
            TranzilaWire.withCard("\"txn_type\":\"debit\"", token, "2")
                .replace(
                    "\"expire_month\":7,\"expire_year\":2030",
                    "\"expire_month\":11,\"expire_year\":2031"));

    assertThat(second).contains("\"expiry_month\":11,\"expiry_year\":31");
  }

  @Test
  void a_token_this_run_never_answered_is_refused_20111() {
    TranzilaOperationResult refused =
        wired.create(TranzilaWire.withCard("\"txn_type\":\"debit\"", "tunknowntoken4245", "2"));

    assertThat(refused.body().text())
        .isEqualTo("{\"error_code\":20111,\"message\":\"Provided token check failure\"}");
    assertThat(refused.body().httpStatus()).isEqualTo(200);
    assertThat(refused.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(refused.refusal()).isInstanceOf(RefusalSubject.NoObjectWasNamed.class);
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void the_published_page_cards_token_charges_nothing_on_the_api_of_a_run_that_never_minted_it() {
    TranzilaOperationResult refused =
        wired.create(TranzilaWire.withCard("\"txn_type\":\"debit\"", "t5d55d2922ca4029022", "3"));

    assertThat(refused.body().text())
        .isEqualTo("{\"error_code\":20111,\"message\":\"Provided token check failure\"}");
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void a_token_seeded_as_a_fixture_charges_the_card_the_fixture_describes() {
    wired.fixtures.seed(
        new NewPaymentMethodFixture(
            TranzilaApi.TRANZILA,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            "owner",
            PaymentMethodKind.CARD,
            "decoy",
            new FixtureCard("visa", "0009", 2, 2032, "credit"),
            Map.of("token", "decoytoken0009"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
    wired.fixtures.seed(
        new NewPaymentMethodFixture(
            TranzilaApi.TRANZILA,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            "owner",
            PaymentMethodKind.CARD,
            "seeded",
            new FixtureCard("amex", "0005", 1, 2031, "credit"),
            Map.of("token", "seededtoken0005"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());

    String answered =
        wired.answered(TranzilaWire.withCard("\"txn_type\":\"debit\"", "seededtoken0005", "3"));

    assertThat(answered)
        .contains("\"card_type\":4,\"card_type_name\":\"Amex\"")
        .contains("\"token\":\"seededtoken0005\"")
        .contains("\"card_mask\":\"xxxxxxxxxxxx0005\"");
  }

  @Test
  void a_debit_a_scenario_declines_is_stored_and_answered_with_the_processor_code_as_error_code() {
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));

    TranzilaOperationResult declined = wired.create(TranzilaWire.aDebit());

    assertThat(declined.body().text())
        .isEqualTo(
            "{\"error_code\":4,\"message\":\"Refusal. Please contact the card owner to check the"
                + " reason with his credit company.\",\"transaction_result\":{"
                + "\"processor_response_code\":\"004\",\"transaction_id\":10000001}}");
    assertThat(declined.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(declined.scenarioId()).isNotNull();
    assertThat(declined.refusal()).isEqualTo(RefusalSubject.theObject("10000001"));
    TranzilaTransaction stored = wired.theLastStored();
    assertThat(stored.processorResponseCode()).isEqualTo("004");
    assertThat(stored.authNumber()).isNull();
    assertThat(stored.token()).isNull();
  }

  @Test
  void every_decline_a_scenario_may_name_is_answered_in_the_published_english_words() {
    assertThat(TranzilaResponseCodes.THE_DECLINES_A_SCENARIO_MAY_NAME)
        .containsExactly(
            Map.entry(
                "001", "Blocked confiscate card. Please check and enter the credit number again."),
            Map.entry(
                "002", "Stolen confiscate card. Please check and enter the credit number again."),
            Map.entry("003", "Contact credit company to approve the transaction."),
            Map.entry(
                "004",
                "Refusal. Please contact the card owner to check the reason with his credit"
                    + " company."),
            Map.entry("005", "Forged confiscate card."),
            Map.entry("006", "Incorrect identity number or CVV."),
            Map.entry("012", "Unauthorized card for this terminal."),
            Map.entry("015", "Expired card. Please check the expiration date again."),
            Map.entry("016", "Unauthorized currency."),
            Map.entry("017", "Unauthorized credit type for this transaction."));
    assertThat(TranzilaResponseCodes.theTextOf("000")).isEqualTo("Transaction approved.");
    assertThat(TranzilaResponseCodes.theTextOf("507"))
        .isEqualTo("actual transaction amount higher than the approved amount");
    assertThat(TranzilaResponseCodes.theTextOf("777"))
        .startsWith("operation completed (success code for operations");
    assertThat(TranzilaResponseCodes.theTextOf("999")).isEqualTo("999");
  }

  @Test
  void a_rule_answering_instead_of_the_operation_stores_nothing_and_observes_nothing() {
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));

    TranzilaOperationResult answered =
        wired.create(
            TranzilaWire.aDebit()
                .replace(
                    "}]}",
                    "}],\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"x\"}]}"));

    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(answered.insteadOfTheOperation()).isNotNull();
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.exchanges.everythingItObserved()).isEmpty();
  }

  @Test
  void a_rule_that_lets_the_operation_proceed_names_itself_on_the_approval() {
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.FixedSuccess());

    TranzilaOperationResult approved = wired.create(TranzilaWire.aDebit());

    assertThat(approved.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(approved.scenarioId()).isNotNull();
    assertThat(approved.providerObjectId()).isEqualTo("10000001");
    assertThat(wired.stored()).hasSize(1);
  }

  @Test
  void a_rule_that_disrupts_the_answer_still_stores_the_transaction_and_carries_the_fault() {
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()));

    TranzilaOperationResult approved = wired.create(TranzilaWire.aDebit());

    assertThat(approved.fault()).isNotNull();
    assertThat(approved.scenarioId()).isNotNull();
    assertThat(wired.stored()).hasSize(1);
  }

  @Test
  void a_transaction_id_another_transaction_holds_is_drawn_again() {
    wired.transactions.theNextIdsAreTaken(2);

    wired.answered(TranzilaWire.aDebit());

    assertThat(wired.theLastStored().transactionId()).isEqualTo(10000003L);
    assertThat(wired.theLastStored().rootTxnId()).isEqualTo(10000003L);
  }

  @Test
  void five_transaction_ids_all_held_already_end_the_request_instead_of_drawing_forever() {
    wired.transactions.theNextIdsAreTaken(5);

    assertThatThrownBy(() -> wired.create(TranzilaWire.aDebit()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("5 attempts");
  }

  @Test
  void a_body_that_is_not_a_json_object_is_refused_400_20001() {
    TranzilaOperationResult refused = wired.create("[1]");

    assertThat(refused.body().httpStatus()).isEqualTo(400);
    assertThat(refused.body().text())
        .isEqualTo(
            "{\"error_code\":\"20001\",\"message\":\"Invalid JSON Format: the request body is not"
                + " a JSON object\"}");
    assertThat(refused.refusal()).isInstanceOf(RefusalSubject.NoObjectWasNamed.class);
  }

  @Test
  void a_body_outside_the_schema_is_refused_400_20004_naming_what_is_wrong() {
    TranzilaOperationResult refused =
        wired.create(TranzilaWire.aDebit().replace("{\"terminal", "{\"verfiy_mode\":5,\"terminal"));

    assertThat(refused.body().httpStatus()).isEqualTo(400);
    assertThat(refused.body().text())
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: verfiy_mode is not a"
                + " field of the Transactions API schema\"}");
  }

  @Test
  void a_request_asking_for_3_d_secure_is_refused_400_20004_and_charges_nothing() {
    TranzilaOperationResult answered =
        wired.create(
            TranzilaWire.aDebit().replace("{\"terminal", "{\"activate_3ds\":\"Y\",\"terminal"));

    assertThat(answered.body().httpStatus()).isEqualTo(400);
    assertThat(answered.body().text())
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: activate_3ds Y asks"
                + " for 3-D Secure, which this simulator does not perform on this route; nothing"
                + " was charged\"}");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void a_request_for_another_terminal_is_refused_401_before_its_3_d_secure_is_read() {
    TranzilaOperationResult answered =
        wired.create(
            TranzilaWire.aDebit()
                .replace("{\"terminal", "{\"activate_3ds\":\"Y\",\"terminal")
                .replace("myterminal", "otherterminal"));

    assertThat(answered.body().httpStatus()).isEqualTo(401);
    assertThat(answered.body().text()).contains("\"error_code\":\"20002\"");
  }

  @Test
  void a_request_saying_no_3_d_secure_with_its_settings_is_charged() {
    assertThat(
            wired.answered(
                TranzilaWire.aDebit()
                    .replace(
                        "{\"terminal",
                        "{\"activate_3ds\":\"N\",\"3ds_settings\":{\"force_challenge\":0},\"terminal")))
        .contains("\"processor_response_code\":\"000\"");
  }

  @Test
  void a_terminal_name_other_than_the_app_keys_is_refused_401_20002() {
    TranzilaOperationResult refused =
        wired.create(TranzilaWire.aDebit().replace("myterminal", "otherterminal"));

    assertThat(refused.body().httpStatus()).isEqualTo(401);
    assertThat(refused.body().text())
        .isEqualTo(
            "{\"error_code\":\"20002\",\"message\":\"Authorization failed: terminal_name names a"
                + " terminal other than the one the X-tranzila-api-app-key belongs to\"}");
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void a_dc_disable_longer_than_254_characters_is_refused_22103_and_254_is_not() {
    TranzilaOperationResult refused = wired.create(underDcDisable("x".repeat(255)));
    TranzilaOperationResult accepted = wired.create(underDcDisable("y".repeat(254)));

    assertThat(refused.body().text())
        .isEqualTo("{\"error_code\":22103,\"message\":\"Invalid DCdisable\"}");
    assertThat(accepted.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(wired.stored())
        .singleElement()
        .extracting(TranzilaTransaction::dcDisable)
        .isEqualTo("y".repeat(254));
  }

  @Test
  void a_repeat_under_one_dc_disable_answers_the_original_result_and_charges_nothing_more() {
    String original = wired.answered(underDcDisable("order-1"));

    TranzilaOperationResult repeat = wired.create(underDcDisable("order-1"));

    assertThat(repeat.body().text())
        .isEqualTo(
            original.replace(
                "\"message\":\"success\"",
                "\"message\":\"This payment has already been made, please contact the"
                    + " business\""));
    assertThat(repeat.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(repeat.providerObjectId()).isEqualTo("10000001");
    assertThat(wired.stored()).hasSize(1);
    assertThat(wired.exchanges.everythingItObserved())
        .extracting(TranzilaObservedRequest::verdict, TranzilaObservedRequest::stateChanged)
        .containsExactly(
            Tuple.tuple(ExchangeVerdict.FIRST_REQUEST, true),
            Tuple.tuple(ExchangeVerdict.DUPLICATE_KEY_REFUSAL, false));
  }

  @Test
  void a_repeat_24_hours_after_the_original_is_admitted_and_charged_again() {
    wired.answered(underDcDisable("order-2"));
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofHours(24)));

    TranzilaOperationResult repeat = wired.create(underDcDisable("order-2"));

    assertThat(repeat.body().text()).contains("\"message\":\"success\"");
    assertThat(wired.stored()).hasSize(2);
    assertThat(wired.exchanges.everythingItObserved().get(1).verdict())
        .isEqualTo(ExchangeVerdict.REPEAT_ADMITTED);
  }

  @Test
  void a_repeat_just_inside_24_hours_is_still_a_duplicate() {
    wired.answered(underDcDisable("order-3"));
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofHours(24)).minusSeconds(1));

    assertThat(wired.answered(underDcDisable("order-3"))).contains("already been made");
  }

  @Test
  void a_repeat_of_a_declined_original_is_let_through() {
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    wired.create(underDcDisable("order-4"));
    wired.rules.clear(TranzilaApi.TRANZILA, TranzilaWiring.RUN);

    String retried = wired.answered(underDcDisable("order-4"));

    assertThat(retried).contains("\"message\":\"success\"");
    assertThat(wired.stored()).hasSize(2);
  }

  @Test
  void the_first_request_under_a_dc_disable_is_observed_with_what_it_answered() {
    String answered = wired.answered(underDcDisable("order-5"));

    TranzilaObservedRequest observed = wired.exchanges.everythingItObserved().get(0);
    assertThat(observed.keyName()).isEqualTo("DCdisable debit");
    assertThat(observed.keyValue()).isEqualTo("order-5");
    assertThat(observed.operation()).isEqualTo("debit");
    assertThat(observed.httpStatus()).isEqualTo(200);
    assertThat(observed.contentType()).isEqualTo("application/json");
    assertThat(observed.body())
        .isEqualTo(
            answered
                .replace("\"token\":\"t000000000000014245\"", "\"token\":\"[redacted]\"")
                .replace("\"card_number\":\"458021xxxxxx4245\"", "\"card_number\":\"[redacted]\""));
    assertThat(observed.providerObjectId()).isEqualTo("10000001");
    assertThat(observed.accountId()).isEqualTo(TranzilaWiring.ACCOUNT);
    assertThat(observed.testRunId()).isEqualTo(TranzilaWiring.RUN);
    assertThat(observed.at()).isEqualTo(TranzilaWiring.NOW);
    assertThat(observed.fingerprint()).hasSize(64);
    assertThat(wired.transactions.theKeysHeld()).hasSize(1);
  }

  @Test
  void an_observation_another_request_took_the_place_of_is_written_again_until_it_is_recorded() {
    wired.exchanges.takenMeanwhile(99);

    wired.answered(underDcDisable("order-8"));

    assertThat(wired.exchanges.everythingItObserved()).hasSize(1);
    assertThat(wired.stored()).hasSize(1);
  }

  @Test
  void an_observation_that_cannot_be_recorded_in_a_hundred_attempts_undoes_the_transaction() {
    wired.exchanges.takenMeanwhile(100);

    assertThatThrownBy(() -> wired.create(underDcDisable("order-9")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("100 attempts");
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.exchanges.everythingItObserved()).isEmpty();
  }

  @Test
  void a_follow_on_observation_that_cannot_be_recorded_undoes_the_follow_on_too() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    String auth = String.valueOf(wired.theLastStored().authNumber());
    wired.exchanges.takenMeanwhile(100);

    assertThatThrownBy(() -> wired.create(TranzilaWire.aCancel(debit, auth)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(wired.stored()).hasSize(1);
  }

  @Test
  void two_requests_differing_only_in_their_card_share_one_fingerprint() {
    wired.answered(underDcDisable("order-10"));
    wired.answered(
        underDcDisable("order-10")
            .replace(TranzilaWire.A_VISA, TranzilaWire.A_MASTERCARD)
            .replace(
                "\"expire_month\":7,\"expire_year\":2030",
                "\"expire_month\":1,\"expire_year\":2031")
            .replace("}],\"user", "}],\"cvv\":\"999\",\"card_holder_id\":\"123456789\",\"user"));
    wired.answered(underDcDisable("order-10").replace("10.53", "10.54"));

    List<String> fingerprints =
        wired.exchanges.everythingItObserved().stream()
            .map(TranzilaObservedRequest::fingerprint)
            .toList();
    assertThat(fingerprints.get(1)).isEqualTo(fingerprints.get(0));
    assertThat(fingerprints.get(2)).isNotEqualTo(fingerprints.get(0));
  }

  @Test
  void an_exchange_keeps_no_card_number_no_cvv_and_no_holder_id() {
    wired.answered(
        underDcDisable("order-11")
            .replace("}],\"user", "}],\"cvv\":\"987\",\"card_holder_id\":\"123456789\",\"user"));

    TranzilaObservedRequest observed = wired.exchanges.everythingItObserved().get(0);
    assertThat(observed.body())
        .doesNotContain("4580210000004245")
        .doesNotContain("987")
        .doesNotContain("123456789")
        .doesNotContain("t000000000000014245")
        .contains("\"card_holder_id\":\"[redacted]\"")
        .contains("\"cvv\":\"***\"");
    assertThat(
            CreateTransaction.theFieldsAFingerprintReads(
                TranzilaApiCall.of(
                    "POST",
                    "/",
                    Map.of(),
                    "{\"card_number\":\"1\",\"cvv\":\"2\","
                        + "\"card_holder_id\":\"3\",\"expire_month\":4,\"expire_year\":5,\"remarks\":\"r\"}")))
        .containsOnlyKeys("remarks");
  }

  @Test
  void a_refused_request_under_a_dc_disable_is_observed_as_changing_nothing() {
    wired.create(
        TranzilaWire.withCard("\"txn_type\":\"debit\"", "tunknowntoken4245", "2")
            .replace(
                "}]}", "}],\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"o\"}]}"));

    assertThat(wired.exchanges.everythingItObserved())
        .singleElement()
        .extracting(TranzilaObservedRequest::stateChanged)
        .isEqualTo(false);
  }

  @Test
  void a_declined_request_under_a_dc_disable_is_observed_as_storing_its_transaction() {
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    wired.create(underDcDisable("order-6"));

    assertThat(wired.exchanges.everythingItObserved())
        .singleElement()
        .extracting(TranzilaObservedRequest::stateChanged)
        .isEqualTo(true);
  }

  @Test
  void every_call_naming_one_original_is_observed_under_it_first_then_as_a_retry() {
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));
    String token = wired.theLastStored().token();
    wired.create(TranzilaWire.aForce(j5, String.valueOf(token), "10"));
    wired.create(TranzilaWire.aForce(j5, String.valueOf(token), "10"));

    assertThat(wired.exchanges.everythingItObserved())
        .extracting(
            TranzilaObservedRequest::keyName,
            TranzilaObservedRequest::keyValue,
            TranzilaObservedRequest::verdict)
        .containsExactly(
            Tuple.tuple("reference_txn_id", Long.toString(j5), ExchangeVerdict.FIRST_REQUEST),
            Tuple.tuple("reference_txn_id", Long.toString(j5), ExchangeVerdict.CORRELATED_RETRY));
  }

  @Test
  void a_repeat_of_a_follow_on_under_a_dc_disable_is_observed_under_both_keys() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    String authorization = String.valueOf(wired.theLastStored().authNumber());

    wired.create(
        TranzilaWire.aCancel(debit, authorization)
            .replace("}", ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"c\"}]}"));

    assertThat(wired.exchanges.everythingItObserved())
        .extracting(TranzilaObservedRequest::keyName)
        .containsExactly("DCdisable cancel", "reference_txn_id");
  }

  @Test
  void the_provider_reads_its_own_name_on_every_request_it_decides() {
    assertThat(TranzilaApi.TRANZILA).isEqualTo(ProviderId.of("tranzila"));
  }

  private static String underDcDisable(String value) {
    return TranzilaWire.aDebit()
        .replace(
            "}]}",
            "}],\"user_defined_fields\":[{\"name\":\"other\",\"value\":1},"
                + "{\"name\":\"DCdisable\",\"value\":\""
                + value
                + "\"}]}");
  }
}
