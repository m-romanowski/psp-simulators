package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraPaymentTimelineTest {

  private static final TestRunId RUN = TestRunId.of("autra-timeline");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String A_PAYMENT_ID = "550e8400-e29b-41d4-a716-446655440000";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final AutraApi api = apiFor();

  private AutraApi apiFor() {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, Map.of()));
    return AutraWiring.api(merchants, sessions, payments, audit, RUN, NOW, scenarios);
  }

  private void declining(String operation, String code) {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.TerminalFailure(code),
            null));
  }

  private String aSale(String amount) {
    AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null);
    return payments.all().getLast().paymentId();
  }

  private String aHold(String amount) {
    AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null);
    return payments.all().getLast().paymentId();
  }

  private void capture(String paymentId) {
    api.capture(
        AutraWire.call(
            AutraApiController.PAYMENTS + "/" + paymentId + "/capture",
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            "{\"transactionalToken\":\""
                + AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT)
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\"}"),
        paymentId);
  }

  private void reverse(String paymentId, String amount) {
    api.voidPayment(
        AutraWire.call(
            AutraApiController.PAYMENTS + "/" + paymentId + "/void",
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            "{\"transactionalToken\":\""
                + AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT)
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\",\"amount\":"
                + amount
                + "}"),
        paymentId);
  }

  private AutraPaymentEvent theOnlyEvent() {
    assertThat(payments.everyEvent()).hasSize(1);
    return payments.everyEvent().getFirst();
  }

  @Test
  void a_sale_the_acquirer_accepted_records_one_SALE_event_at_the_status_it_reached() {
    String paymentId = aSale("500.00");

    AutraPaymentEvent event = theOnlyEvent();
    assertThat(event.paymentId()).isEqualTo(paymentId);
    assertThat(event.type()).isEqualTo("SALE");
    assertThat(event.status()).isEqualTo("ACCEPTED");
    assertThat(event.amount()).isEqualTo(Amount.of("500.00"));
    assertThat(event.at()).isEqualTo(NOW);
    assertThat(event.reportsARefusal()).isFalse();
  }

  @Test
  void a_sale_the_acquirer_declined_records_one_SALE_event_carrying_the_code_and_the_message() {
    declining(AutraOperations.SALE, AutraErrors.INSUFFICIENT_FUNDS);

    aSale("500.00");

    AutraPaymentEvent event = theOnlyEvent();
    assertThat(event.type()).isEqualTo("SALE");
    assertThat(event.status()).isEqualTo("DECLINED");
    assertThat(event.theCodeItWasRefusedWith()).isEqualTo(AutraErrors.INSUFFICIENT_FUNDS);
    assertThat(event.theMessageItWasRefusedWith())
        .isEqualTo(AutraDeclineMessages.THE_PUBLISHED_INSUFFICIENT_FUNDS_MESSAGE);
  }

  @Test
  void a_hold_the_acquirer_took_records_one_PREAUTH_event() {
    aHold("500.00");

    AutraPaymentEvent event = theOnlyEvent();
    assertThat(event.type()).isEqualTo("PREAUTH");
    assertThat(event.status()).isEqualTo("ACCEPTED");
    assertThat(event.reportsARefusal()).isFalse();
  }

  @Test
  void a_hold_the_acquirer_declined_records_one_PREAUTH_event_carrying_the_refusal() {
    declining(AutraOperations.PRE_AUTHORIZATION, AutraErrors.CARD_DECLINED);

    aHold("500.00");

    AutraPaymentEvent event = theOnlyEvent();
    assertThat(event.type()).isEqualTo("PREAUTH");
    assertThat(event.status()).isEqualTo("DECLINED");
    assertThat(event.theCodeItWasRefusedWith()).isEqualTo(AutraErrors.CARD_DECLINED);
  }

  @Test
  void a_capture_the_acquirer_took_records_a_CAPTURE_event_after_the_hold_that_opened_it() {
    String paymentId = aHold("500.00");

    capture(paymentId);

    assertThat(payments.everyEvent()).hasSize(2);
    AutraPaymentEvent event = payments.everyEvent().getLast();
    assertThat(event.type()).isEqualTo("CAPTURE");
    assertThat(event.status()).isEqualTo("CAPTURED");
    assertThat(event.amount()).isEqualTo(Amount.of("500.00"));
  }

  @Test
  void a_capture_the_acquirer_declined_records_a_CAPTURE_event_at_the_status_the_hold_keeps() {
    String paymentId = aHold("500.00");
    declining(AutraOperations.CAPTURE, AutraErrors.AUTHORIZER_REJECTED);

    capture(paymentId);

    assertThat(payments.everyEvent()).hasSize(2);
    AutraPaymentEvent event = payments.everyEvent().getLast();
    assertThat(event.type()).isEqualTo("CAPTURE");
    assertThat(event.status()).isEqualTo("ACCEPTED");
    assertThat(event.theCodeItWasRefusedWith()).isEqualTo(AutraErrors.AUTHORIZER_REJECTED);
  }

  @Test
  void a_reversal_the_acquirer_took_records_a_VOID_event_at_the_status_it_left_behind() {
    String paymentId = aSale("500.00");

    reverse(paymentId, "200.00");

    assertThat(payments.everyEvent()).hasSize(2);
    AutraPaymentEvent event = payments.everyEvent().getLast();
    assertThat(event.type()).isEqualTo("VOID");
    assertThat(event.status()).isEqualTo("PARTIALLY_VOIDED");
    assertThat(event.amount()).isEqualTo(Amount.of("200.00"));
  }

  @Test
  void a_released_hold_records_a_VOID_event_although_it_leaves_no_operation_on_the_ledger() {
    String paymentId = aHold("500.00");

    reverse(paymentId, "500.00");

    AutraPaymentEvent event = payments.everyEvent().getLast();
    assertThat(event.type()).isEqualTo("VOID");
    assertThat(event.status()).isEqualTo("VOIDED");
    assertThat(payments.findById(RUN, paymentId).orElseThrow().ledger().operations()).hasSize(1);
  }

  @Test
  void a_reversal_the_acquirer_declined_records_a_VOID_event_carrying_the_refusal() {
    String paymentId = aSale("500.00");
    declining(AutraOperations.VOID, AutraErrors.CANT_REFUND_TRANSACTION_FROM_TODAY);

    reverse(paymentId, "200.00");

    AutraPaymentEvent event = payments.everyEvent().getLast();
    assertThat(event.type()).isEqualTo("VOID");
    assertThat(event.status()).isEqualTo("ACCEPTED");
    assertThat(event.theCodeItWasRefusedWith())
        .isEqualTo(AutraErrors.CANT_REFUND_TRANSACTION_FROM_TODAY);
  }

  @Test
  void an_event_carrying_one_half_of_a_refusal_and_not_the_other_is_refused_by_the_record() {
    assertThatThrownBy(
            () ->
                new AutraPaymentEvent(
                    RUN, A_PAYMENT_ID, "SALE", "DECLINED", Amount.of("1"), "CODE", null, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AutraPaymentEvent.A_REFUSAL_CARRIES_BOTH_HALVES_OR_NEITHER);
    assertThatThrownBy(
            () ->
                new AutraPaymentEvent(
                    RUN, A_PAYMENT_ID, "SALE", "DECLINED", Amount.of("1"), null, "why", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AutraPaymentEvent.A_REFUSAL_CARRIES_BOTH_HALVES_OR_NEITHER);
  }

  @Test
  void an_event_that_reports_no_refusal_refuses_to_hand_out_a_code_or_a_message() {
    AutraPaymentEvent event =
        AutraPaymentEvent.of(RUN, A_PAYMENT_ID, "SALE", "ACCEPTED", Amount.of("1"), NOW);

    assertThat(event.reportsARefusal()).isFalse();
    assertThatThrownBy(event::theCodeItWasRefusedWith)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(AutraPaymentEvent.THE_EVENT_REPORTS_NO_REFUSAL);
    assertThatThrownBy(event::theMessageItWasRefusedWith)
        .isInstanceOf(NullPointerException.class)
        .hasMessage(AutraPaymentEvent.THE_EVENT_REPORTS_NO_REFUSAL);
  }

  @Test
  void a_refused_event_hands_out_both_halves_of_the_refusal_it_carries() {
    AutraPaymentEvent event =
        AutraPaymentEvent.refused(
            RUN, A_PAYMENT_ID, "SALE", "DECLINED", Amount.of("1"), "CODE", "why", NOW);

    assertThat(event.reportsARefusal()).isTrue();
    assertThat(event.theCodeItWasRefusedWith()).isEqualTo("CODE");
    assertThat(event.theMessageItWasRefusedWith()).isEqualTo("why");
  }

  @Test
  void the_two_types_a_payment_is_are_the_two_the_vendor_enumerates_and_nothing_else() {
    assertThat(AutraTimeline.THE_TYPES_A_PAYMENT_IS).containsExactly("SALE", "PREAUTH");
    assertThat(AutraTimeline.isATypeAPaymentIs("SALE")).isTrue();
    assertThat(AutraTimeline.isATypeAPaymentIs("PREAUTH")).isTrue();
    assertThat(AutraTimeline.isATypeAPaymentIs("CAPTURE")).isFalse();
    assertThat(AutraTimeline.isATypeAPaymentIs("preauth")).isFalse();
    assertThat(AutraTimeline.CAPTURE).isEqualTo("CAPTURE");
    assertThat(AutraTimeline.VOID).isEqualTo("VOID");
  }

  @Test
  void a_declined_sale_and_a_second_attempt_each_leave_their_own_event() {
    declining(AutraOperations.SALE, AutraErrors.INSUFFICIENT_FUNDS);
    aSale("500.00");
    rules.clear(AutraApi.AUTRA, RUN);
    String accepted = aSale("500.00");

    List<AutraPaymentEvent> recorded = payments.everyEvent();
    assertThat(recorded).hasSize(2);
    assertThat(recorded.getFirst().status()).isEqualTo("DECLINED");
    assertThat(recorded.getLast().status()).isEqualTo("ACCEPTED");
    assertThat(recorded.getLast().paymentId()).isEqualTo(accepted);
  }
}
