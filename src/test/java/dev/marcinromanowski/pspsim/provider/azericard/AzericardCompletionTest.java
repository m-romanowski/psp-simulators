package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AzericardCompletionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("azericard-run");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String NOW_ON_THE_WIRE = "20260910120000";

  private static final String TERMINAL_ID = "77777777";
  private static final String MERCHANT = "merchant-1";
  private static final String ORDER = "20220628084800";
  private static final String NONCE = "F2B2DD7E603A7ADA";
  private static final String ROUTE = "/cgi-bin/cgi_link";
  private static final String HELD = "11.48";

  private static final String INT_REF = "0000000000000001";
  private static final String RRN = "100000000001";
  private static final String APPROVAL = "100001";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();

  @Test
  void completes_a_hold_in_full_and_answers_the_signed_xml_echoing_the_reference_it_was_sent() {
    AzericardGateway gateway = gatewayHolding(HELD);

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion(HELD))));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.contentType()).isEqualTo("text/html");
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo(INT_REF);
    assertThat(bodyOf(response))
        .isEqualTo(
            "<response>"
                + "<desc>Approved</desc>"
                + "<terminal>77777777</terminal>"
                + "<trtype>21</trtype>"
                + "<order>20220628084800</order>"
                + "<amount>11.48</amount>"
                + "<currency>AZN</currency>"
                + "<action>0</action>"
                + "<rc>00</rc>"
                + "<approval>100001</approval>"
                + "<rrn>"
                + RRN
                + "</rrn>"
                + "<intref>"
                + INT_REF
                + "</intref>"
                + "<timestamp>"
                + NOW_ON_THE_WIRE
                + "</timestamp>"
                + "<nonce>"
                + NONCE
                + "</nonce>"
                + "<p_sign>"
                + theExpectedSignatureOver(HELD, TERMINAL_ID, APPROVAL, RRN, INT_REF)
                + "</p_sign>"
                + "</response>");
  }

  @Test
  void builds_the_completion_mac_from_the_fields_accepted_by_the_real_gateway() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.AMOUNT, "11.48");
    form.put(AzericardFields.CURRENCY, "USD");
    form.put(AzericardFields.TERMINAL, "99999999");
    form.put(AzericardFields.TRTYPE, "21");
    form.put(AzericardFields.ORDER, "20210506070034");
    form.put(AzericardFields.RRN, "112676199769");
    form.put(AzericardFields.INT_REF, "5E3601D7C71745A9");
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);

    assertThat(
            new String(
                AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(form)).macSource(), UTF_8))
        .isEqualTo(
            "511.483USD142021050607003412112676199769165E3601D7C71745A9899999999221142026091012000016F2B2DD7E603A7ADA");
  }

  @Test
  void a_terminal_signing_as_the_manual_publishes_is_verified_against_the_published_order() {
    AzericardGateway gateway = gatewaySigningAsTheManualPublishes();
    Map<String, String> form = completion(HELD);
    form.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(form))
                .macSourceAsTheManualPublishesIt()));

    assertThat(gateway.cgiLink(call(form)).outcome())
        .as(
            "the manual publishes one field order for a completion and a worked example that"
                + " computes from it, and a suite written against that manual is a suite a"
                + " tester brings here, so the terminal it configures is verified against the"
                + " order it names")
        .isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void a_terminal_signing_as_the_manual_publishes_refuses_the_order_the_live_gateway_accepts() {
    AzericardGateway gateway = gatewaySigningAsTheManualPublishes();

    assertThat(gateway.cgiLink(call(signed(completion(HELD)))).outcome())
        .as(
            "the two orders are two signatures, so a terminal that names one refuses the other"
                + " rather than accepting whichever arrives")
        .isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void a_reversal_is_signed_in_the_order_its_terminal_names_as_a_completion_is() {
    AzericardGateway live = gatewayHolding(HELD);
    assertThat(live.cgiLink(call(signed(settlement("22", HELD)))).outcome())
        .as("a reversal shares the shape a completion signs in, so it shares the order as well")
        .isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void refuses_a_completion_signed_in_the_field_order_the_authorization_mac_uses() {
    AzericardGateway gateway = gatewayHolding(HELD);
    Map<String, String> form = completion(HELD);
    form.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMac.sourceOf(
                form.get(AzericardFields.AMOUNT),
                form.get(AzericardFields.CURRENCY),
                form.get(AzericardFields.TERMINAL),
                form.get(AzericardFields.TRTYPE),
                form.get(AzericardFields.TIMESTAMP),
                form.get(AzericardFields.NONCE),
                null)));

    AzericardApiResponse response = gateway.cgiLink(call(form));

    assertThat(bodyOf(response))
        .contains("<action>3</action>")
        .contains("<rc>-17</rc>")
        .contains("<desc>Access denied</desc>");
    assertThat(thePayment().ledger().operations()).hasSize(1);
  }

  @Test
  void a_refused_message_echoes_the_reference_fields_the_completion_carried() {
    AzericardGateway gateway = gatewayHolding(HELD);
    Map<String, String> form = completion(HELD);
    form.put(AzericardFields.P_SIGN, "00");

    assertThat(bodyOf(gateway.cgiLink(call(form))))
        .contains("<rrn>" + RRN + "</rrn>")
        .contains("<intref>" + INT_REF + "</intref>");
  }

  @Test
  void a_completed_hold_is_succeeded_and_holds_a_capture_the_ledger_records() {
    AzericardGateway gateway = gatewayHolding(HELD);

    gateway.cgiLink(call(signed(completion(HELD))));

    AzericardPayment payment = thePayment();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.authorizationOpen()).isFalse();
    assertThat(payment.reversalsAccepted()).isTrue();
    assertThat(payment.updatedAt()).isEqualTo(NOW);
    assertThat(payment.ledger().operations())
        .extracting(MonetaryOperation::kind, MonetaryOperation::amount)
        .containsExactly(
            tuple(OperationKind.AUTHORIZATION, Amount.of(HELD)),
            tuple(OperationKind.CAPTURE, Amount.of(HELD)));
    MonetaryOperation capture = payment.ledger().operations().get(1);
    assertThat(capture.sequence()).isEqualTo(2);
    assertThat(capture.closesAuthorization()).isTrue();
    assertThat(capture.createdObjectId()).isNull();
    assertThat(capture.nativeStatus()).isEqualTo("0/00");
    assertThat(capture.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void a_completion_below_the_held_amount_closes_the_authorization_and_forfeits_the_rest() {
    AzericardGateway gateway = gatewayHolding(HELD);

    gateway.cgiLink(call(signed(completion("5.00"))));

    AzericardPayment payment = thePayment();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.authorizationOpen()).isFalse();
    assertThat(payment.capturableRemainder()).isEqualByComparingTo("0");
    assertThat(payment.reversibleRemainder()).isEqualByComparingTo("5.00");
  }

  @Test
  void a_terminal_configured_to_leave_the_remainder_reports_a_partly_captured_payment() {
    AzericardGateway gateway = gatewayLeavingTheRemainder();

    gateway.cgiLink(call(signed(completion("5.00"))));

    AzericardPayment payment = thePayment();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_CAPTURED);
    assertThat(payment.authorizationOpen()).isTrue();
    assertThat(payment.capturableRemainder()).isEqualByComparingTo("6.48");
    MonetaryOperation capture = payment.ledger().operations().get(1);
    assertThat(capture.closesAuthorization()).isFalse();
    assertThat(capture.normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_CAPTURED);
  }

  @Test
  void a_second_completion_of_the_remainder_closes_an_authorization_left_open() {
    AzericardGateway gateway = gatewayLeavingTheRemainder();
    gateway.cgiLink(call(signed(completion("5.00"))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion("6.48"))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    AzericardPayment payment = thePayment();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.authorizationOpen()).isFalse();
  }

  @Test
  void refuses_a_completion_above_the_amount_the_hold_still_carries() {
    AzericardGateway gateway = gatewayHolding(HELD);

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion("11.49"))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().ledger().operations()).hasSize(1);
  }

  @Test
  void refuses_a_second_completion_of_a_hold_the_first_one_closed() {
    AzericardGateway gateway = gatewayHolding(HELD);
    gateway.cgiLink(call(signed(completion(HELD))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion(HELD))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().ledger().operations()).hasSize(2);
  }

  @Test
  void refuses_a_completion_of_an_authorization_that_was_never_a_hold() {
    AzericardGateway gateway = gatewayThatAnswered(authorization("1", HELD));

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion(HELD))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
  }

  @Test
  void refuses_a_completion_of_a_payment_the_gateway_declined() {
    declineEveryRequest("05");
    AzericardGateway gateway = gatewayThatAnswered(authorization("0", HELD));
    scenarioRules.clear(AzericardGateway.AZERICARD, E2E_RUN);

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion(HELD))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
  }

  @ParameterizedTest(name = "refuses a completion whose {0} addresses no payment of this terminal")
  @ValueSource(strings = {AzericardFields.RRN, AzericardFields.INT_REF, AzericardFields.ORDER})
  void refuses_a_completion_that_addresses_a_payment_this_terminal_never_made(String field) {
    AzericardGateway gateway = gatewayHolding(HELD);
    Map<String, String> form = completion(HELD);
    form.put(field, field.equals(AzericardFields.ORDER) ? "20220628084801" : "999999999999");

    AzericardApiResponse response = gateway.cgiLink(call(signed(form)));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NO_SUCH_TRANSACTION);
    assertThat(thePayment().ledger().operations()).hasSize(1);
  }

  @Test
  void a_completion_this_payment_does_not_admit_is_refused_under_the_payment_it_refused() {
    AzericardGateway gateway = gatewayHolding(HELD);
    Map<String, String> form = completion(HELD);
    form.put(AzericardFields.CURRENCY, "USD");

    AzericardApiResponse response = gateway.cgiLink(call(signed(form)));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(response.providerObjectId()).isEqualTo(thePayment().intRef());
  }

  @Test
  void a_completion_addressing_a_payment_this_terminal_never_made_is_refused_under_no_payment() {
    AzericardGateway gateway = gatewayHolding(HELD);
    Map<String, String> form = completion(HELD);
    form.put(AzericardFields.INT_REF, "999999999999");

    AzericardApiResponse response = gateway.cgiLink(call(signed(form)));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(response.providerObjectId()).isNull();
  }

  @Test
  void refuses_a_completion_in_a_currency_the_payment_was_not_authorized_in() {
    AzericardGateway gateway = gatewayHolding(HELD);
    Map<String, String> form = completion(HELD);
    form.put(AzericardFields.CURRENCY, "USD");

    assertThatItWasRefused(gateway.cgiLink(call(signed(form))), AzericardOutcomes.RC_NOT_ADMITTED);
  }

  @ParameterizedTest(name = "refuses the completion amount {0}, which is not a positive amount")
  @ValueSource(strings = {"0", "-1.00", "0.00", "eleven"})
  void refuses_a_completion_amount_that_is_not_positive(String amount) {
    AzericardGateway gateway = gatewayHolding(HELD);

    assertThatItWasRefused(
        gateway.cgiLink(call(signed(completion(amount)))), AzericardOutcomes.RC_NOT_ADMITTED);
  }

  @ParameterizedTest(name = "names {0} on the human error page when a settlement omits it")
  @ValueSource(strings = {AzericardFields.RRN, AzericardFields.INT_REF})
  void names_the_reference_field_a_settlement_left_out(String field) {
    AzericardGateway gateway = gatewayHolding(HELD);
    Map<String, String> form = signed(completion(HELD));
    form.remove(field);

    assertThat(bodyOf(gateway.cgiLink(call(form))))
        .contains(AzericardErrorPage.OPENING)
        .doesNotContain("<action>");
  }

  @ParameterizedTest(name = "reverses a completed payment in full on TRTYPE={0}")
  @ValueSource(strings = {"22", "24"})
  void reverses_a_completed_payment_in_full(String trtype) {
    AzericardGateway gateway = gatewayHolding(HELD);
    gateway.cgiLink(call(signed(completion(HELD))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement(trtype, HELD))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo(INT_REF);
    assertThat(bodyOf(response)).contains("<trtype>" + trtype + "</trtype>");
    AzericardPayment payment = thePayment();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(payment.reversalsAccepted()).isFalse();
    MonetaryOperation reversal = payment.ledger().operations().get(2);
    assertThat(reversal.kind()).isEqualTo(OperationKind.REFUND);
    assertThat(reversal.closesAuthorization()).isNull();
    assertThat(reversal.normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void reverses_an_authorization_that_captured_at_once_without_a_completion() {
    AzericardGateway gateway = gatewayThatAnswered(authorization("1", HELD));

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement("22", HELD))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void refuses_a_reversal_of_less_than_the_whole_reversible_remainder() {
    AzericardGateway gateway = gatewayHolding(HELD);
    gateway.cgiLink(call(signed(completion(HELD))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement("22", "5.00"))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @ParameterizedTest(name = "releases a hold that was never completed on TRTYPE={0}")
  @ValueSource(strings = {"22", "24"})
  void releases_a_hold_that_was_never_completed(String trtype) {
    AzericardGateway gateway = gatewayHolding(HELD);

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement(trtype, HELD))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo(INT_REF);
    assertThat(bodyOf(response)).contains("<action>0</action>").contains("<rc>00</rc>");
    AzericardPayment payment = thePayment();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(payment.authorizationOpen()).isFalse();
    assertThat(payment.capturableRemainder()).isEqualByComparingTo("0");
    assertThat(payment.reversalsAccepted()).isFalse();
    assertThat(payment.holdReversedAt()).isEqualTo(NOW);
  }

  @Test
  void a_released_hold_leaves_the_ledger_carrying_the_authorization_and_nothing_else() {
    AzericardGateway gateway = gatewayHolding(HELD);

    gateway.cgiLink(call(signed(settlement("22", HELD))));

    assertThat(thePayment().ledger().operations())
        .singleElement()
        .satisfies(only -> assertThat(only.kind()).isEqualTo(OperationKind.AUTHORIZATION));
  }

  @Test
  void refuses_an_offline_partial_hold_release_whose_contract_is_unconfirmed() {
    AzericardGateway gateway = gatewayHolding(HELD);

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement("24", "5.00"))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void refuses_a_second_release_of_a_hold_already_reversed() {
    AzericardGateway gateway = gatewayHolding(HELD);
    gateway.cgiLink(call(signed(settlement("22", HELD))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement("24", HELD))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void refuses_a_completion_of_a_hold_a_reversal_already_released() {
    AzericardGateway gateway = gatewayHolding(HELD);
    gateway.cgiLink(call(signed(settlement("22", HELD))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion(HELD))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void refuses_a_release_whose_hold_another_request_reversed_first() {
    AzericardGateway gateway = gatewayHolding(HELD);
    payments.racingTheNextAppend();

    assertThatItWasRefused(
        gateway.cgiLink(call(signed(settlement("22", HELD)))), AzericardOutcomes.RC_NOT_ADMITTED);
  }

  @Test
  void refuses_a_release_of_a_payment_that_was_cleared_while_it_was_being_decided() {
    AzericardGateway gateway = gatewayHolding(HELD);
    payments.losingThePaymentOnTheNextAppend();

    assertThatItWasRefused(
        gateway.cgiLink(call(signed(settlement("22", HELD)))),
        AzericardOutcomes.RC_NO_SUCH_TRANSACTION);
  }

  @Test
  void refuses_a_release_of_a_hold_the_gateway_declined() {
    declineEveryRequest("05");
    AzericardGateway gateway = gatewayThatAnswered(authorization("0", HELD));
    scenarioRules.clear(AzericardGateway.AZERICARD, E2E_RUN);

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement("22", HELD))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
  }

  @Test
  void refuses_a_second_reversal_of_a_payment_already_reversed_in_full() {
    AzericardGateway gateway = gatewayHolding(HELD);
    gateway.cgiLink(call(signed(completion(HELD))));
    gateway.cgiLink(call(signed(settlement("22", HELD))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement("24", HELD))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().ledger().operations()).hasSize(3);
  }

  @Test
  void refuses_a_completion_of_the_remainder_once_the_captured_part_was_reversed() {
    AzericardGateway gateway = gatewayLeavingTheRemainder();
    gateway.cgiLink(call(signed(completion("5.00"))));
    gateway.cgiLink(call(signed(settlement("22", "5.00"))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion("6.48"))));

    assertThatItWasRefused(response, AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void answers_a_scenario_decline_of_a_completion_as_a_declined_pair_and_writes_no_capture() {
    AzericardGateway gateway = gatewayHolding(HELD);
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=21"),
            new ScenarioAction.TerminalFailure("N7"),
            null));

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion(HELD))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(response.providerObjectId()).isEqualTo(INT_REF);
    assertThat(bodyOf(response)).contains("<action>2</action>").contains("<rc>N7</rc>");
    assertThat(thePayment().ledger().operations()).hasSize(1);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_fault_answered_instead_of_the_completion_leaves_the_hold_uncompleted() {
    AzericardGateway gateway = gatewayHolding(HELD);
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=21"),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion(HELD))));

    assertThat(response.httpStatus()).isEqualTo(502);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(thePayment().ledger().operations()).hasSize(1);
    assertThat(thePayment().authorizationOpen()).isTrue();
  }

  @Test
  void a_completion_continues_the_story_its_own_authorization_began() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID, null));
    scenarioRules.add(aStepOf("TRTYPE=0", ScenarioStory.INITIAL_STATE, "authorized"));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=21"),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            new ScenarioStory(
                "capture-after-authorization", StoryKeying.FLOW, "authorized", null)));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization("0", HELD))));

    AzericardApiResponse response = gateway.cgiLink(call(signed(completion(HELD))));

    assertThat(response.httpStatus()).isEqualTo(502);
  }

  @Test
  void refuses_a_completion_whose_sequence_another_request_took_first() {
    AzericardGateway gateway = gatewayHolding(HELD);
    payments.racingTheNextAppend();

    assertThatItWasRefused(
        gateway.cgiLink(call(signed(completion(HELD)))), AzericardOutcomes.RC_NOT_ADMITTED);
  }

  @Test
  void refuses_a_completion_of_a_payment_that_was_cleared_while_it_was_being_decided() {
    AzericardGateway gateway = gatewayHolding(HELD);
    payments.losingThePaymentOnTheNextAppend();

    assertThatItWasRefused(
        gateway.cgiLink(call(signed(completion(HELD)))), AzericardOutcomes.RC_NO_SUCH_TRANSACTION);
  }

  @Test
  void refuses_a_completion_sent_to_a_test_run_that_never_held_the_payment() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID, null));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization("0", HELD))));
    accounts.with(azericardAccount(TestRunId.of("another-run"), MERCHANT, "12341234", null));

    Map<String, String> form = completion(HELD);
    form.put(AzericardFields.TERMINAL, "12341234");

    assertThatItWasRefused(
        gateway().cgiLink(call(signed(form))), AzericardOutcomes.RC_NO_SUCH_TRANSACTION);
  }

  @ParameterizedTest
  @ValueSource(strings = {"21", "22", "24"})
  void settles_a_hold_on_the_token_route_without_requiring_the_card_token_again(String trtype) {
    AzericardGateway gateway = gatewayHolding(HELD);
    Map<String, String> form = signed(settlement(trtype, HELD));
    AzericardApiCall call =
        new AzericardApiCall("POST", "/token/cgi_link", Map.of(), fieldsOf(form));

    assertThat(gateway.tokenLink(call).outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(thePayment().normalizedStatus())
        .isEqualTo("21".equals(trtype) ? NormalizedStatus.SUCCEEDED : NormalizedStatus.CANCELLED);
  }

  @Test
  void releases_part_of_a_hold_before_capturing_the_remaining_amount_without_a_refund() {
    AzericardGateway gateway = gatewayHolding(HELD);
    assertThat(gateway.cgiLink(call(signed(settlement("22", "6.48")))).outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(thePayment().capturableRemainder()).isEqualByComparingTo("5.00");
    assertThat(thePayment().ledger().holds(OperationKind.CAPTURE)).isFalse();
    assertThat(thePayment().ledger().holds(OperationKind.REFUND)).isFalse();
    assertThatItWasRefused(
        gateway.cgiLink(call(signed(completion(HELD)))), AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(gateway.cgiLink(call(signed(completion("5.00")))).outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(thePayment().capturableRemainder()).isEqualByComparingTo("0");
  }

  @Test
  void records_a_partial_release_as_a_release_that_cannot_close_the_authorization() {
    AzericardGateway gateway = gatewayHolding(HELD);

    gateway.cgiLink(call(signed(settlement("22", "6.48"))));

    MonetaryOperation release = thePayment().ledger().operations().get(1);
    assertThat(release.kind()).isEqualTo(OperationKind.RELEASE);
    assertThat(release.amount()).isEqualTo(Amount.of("6.48"));
    assertThat(release.closesAuthorization()).isNull();
    assertThat(release.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void applies_a_repeated_partial_release_again_because_no_replay_is_detected() {
    AzericardGateway gateway = gatewayHolding(HELD);

    assertThat(gateway.cgiLink(call(signed(settlement("22", "3.00")))).outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(gateway.cgiLink(call(signed(settlement("22", "3.00")))).outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);

    assertThat(thePayment().ledger().count(OperationKind.RELEASE)).isEqualTo(2);
    assertThat(thePayment().capturableRemainder()).isEqualByComparingTo("5.48");
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void
      a_partial_completion_after_a_release_closes_the_authorization_as_any_partial_completion_does() {
    AzericardGateway gateway = gatewayHolding(HELD);
    gateway.cgiLink(call(signed(settlement("22", "6.48"))));

    assertThat(gateway.cgiLink(call(signed(completion("3.00")))).outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);

    AzericardPayment payment = thePayment();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.authorizationOpen()).isFalse();
    assertThat(payment.capturableRemainder()).isEqualByComparingTo("0");
  }

  @Test
  void refuses_a_partial_release_of_the_remainder_once_part_of_the_hold_was_completed() {
    AzericardGateway gateway = gatewayLeavingTheRemainder();
    gateway.cgiLink(call(signed(completion("5.00"))));

    assertThatItWasRefused(
        gateway.cgiLink(call(signed(settlement("22", "3.00")))), AzericardOutcomes.RC_NOT_ADMITTED);
    assertThatItWasRefused(
        gateway.cgiLink(call(signed(settlement("22", "6.48")))), AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().ledger().operations()).hasSize(2);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_CAPTURED);
  }

  @Test
  void refuses_a_partial_release_of_a_hold_whose_completed_part_was_reversed() {
    AzericardGateway gateway = gatewayLeavingTheRemainder();
    gateway.cgiLink(call(signed(completion("5.00"))));
    gateway.cgiLink(call(signed(settlement("22", "5.00"))));
    List<MonetaryOperation> before = thePayment().ledger().operations();

    assertThatItWasRefused(
        gateway.cgiLink(call(signed(settlement("22", "2.00")))), AzericardOutcomes.RC_NOT_ADMITTED);
    assertThat(thePayment().ledger().operations()).isEqualTo(before);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"21", "22", "24"})
  void a_scenario_rule_naming_a_settlement_matches_it_on_the_token_route_too(String trtype) {
    AzericardGateway gateway = gatewayHolding(HELD);
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=" + trtype),
            new ScenarioAction.TerminalFailure("05"),
            null));
    AzericardApiCall call =
        new AzericardApiCall(
            "POST", "/token/cgi_link", Map.of(), fieldsOf(signed(settlement(trtype, HELD))));

    assertThat(gateway.tokenLink(call).outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(thePayment().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(thePayment().ledger().operations()).hasSize(1);
  }

  private void assertThatItWasRefused(AzericardApiResponse response, String responseCode) {
    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .contains("<action>3</action>")
        .contains("<rc>" + responseCode + "</rc>")
        .contains("<p_sign>");
  }

  private NewScenarioRule aStepOf(String operation, String whenState, String thenState) {
    return new NewScenarioRule(
        AzericardGateway.AZERICARD,
        E2E_RUN,
        0,
        ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.FixedSuccess(),
        new ScenarioStory("capture-after-authorization", StoryKeying.FLOW, whenState, thenState));
  }

  private void declineEveryRequest(String providerCode) {
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure(providerCode),
            null));
  }

  private AzericardPayment thePayment() {
    assertThat(payments.all()).hasSize(1);
    return payments.all().get(0);
  }

  private AzericardGateway gatewayHolding(String amount) {
    return gatewayThatAnswered(authorization("0", amount));
  }

  private AzericardGateway gatewayLeavingTheRemainder() {
    accounts.with(
        azericardAccount(
            E2E_RUN,
            MERCHANT,
            TERMINAL_ID,
            AzericardPartialCompletion.LEAVES_THE_REMAINDER_CAPTURABLE.name()));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization("0", HELD))));
    return gateway;
  }

  private AzericardGateway gatewaySigningAsTheManualPublishes() {
    accounts.with(
        AzericardDoubles.terminal(
            E2E_RUN,
            MERCHANT,
            TERMINAL_ID,
            Map.of(
                AzericardSettlementMac.SETTING, AzericardSettlementMac.PUBLISHED_EXAMPLE.name())));
    AzericardGateway gateway = gateway();
    Map<String, String> opening = authorization("0", HELD);
    Map<String, String> openingSigned = new LinkedHashMap<>(opening);
    openingSigned.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(openingSigned)).macSource()));
    gateway.cgiLink(call(openingSigned));
    return gateway;
  }

  private AzericardGateway gatewayThatAnswered(Map<String, String> opening) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID, null));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(opening)));
    return gateway;
  }

  private AzericardGateway gateway() {
    accounts.all().forEach(terminal -> testRuns.register(terminal.testRunId()));
    AzericardDoubles.CountingIds ids = new AzericardDoubles.CountingIds();
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

  private static String theExpectedSignatureOver(String... values) {
    return AzericardKeyFixture.signedWith(
        AzericardMac.sourceOf(values),
        AzericardKeyFixture.privateKeyOf(AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8));
  }

  private static String bodyOf(AzericardApiResponse response) {
    return new String(response.body(), UTF_8);
  }

  private static AzericardTerminal azericardAccount(
      TestRunId testRunId,
      String accountId,
      String terminalId,
      @Nullable String partialCompletion) {
    return AzericardDoubles.terminal(
        testRunId,
        accountId,
        terminalId,
        partialCompletion == null
            ? Map.of()
            : Map.of(AzericardPartialCompletion.SETTING, partialCompletion));
  }

  private static Map<String, String> authorization(String trtype, String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, amount);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);
    form.put(AzericardFields.MERCH_URL, "https://merchant.example");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> completion(String amount) {
    return settlement("21", amount);
  }

  private static Map<String, String> settlement(String trtype, String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, amount);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.RRN, RRN);
    form.put(AzericardFields.INT_REF, INT_REF);
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);
    form.put(AzericardFields.LANG, "AZ");
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
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Content-Type", List.of("application/x-www-form-urlencoded"));
    return new AzericardApiCall("POST", ROUTE, headers, fieldsOf(form));
  }
}
