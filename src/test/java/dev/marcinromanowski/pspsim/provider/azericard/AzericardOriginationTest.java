package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationControl;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzericardOriginationTest {

  private static final TestRunId RUN = TestRunId.of("azericard-origination");

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

  private static final String TERMINAL_ID = "77777777";

  private static final String MERCHANT = "merchant-1";

  private static final String ORDER = "20260915100001";

  private static final String AMOUNT = "25.00";

  private static final String THE_TARGET_THIS_RUN_CONFIGURES = "https://merchant.example/hook";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();

  private final WebhookDispatcher callbackDeliveries =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          AzericardDoubles.clockAt(NOW),
          new AzericardDoubles.CountingIds(),
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new AzericardCallbackAcknowledgement()),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final AzericardDoubles.PayerSessions payerSessions = new AzericardDoubles.PayerSessions();

  private final SimulatorProvider provider =
      new AzericardProvider(
          accounts,
          audit,
          payments,
          cards,
          new InMemoryPaymentMethodFixtures(),
          scenarioRules,
          AzericardDoubles.heldResponses(),
          new AzericardCallbacks(
              payments,
              accounts,
              accounts,
              callbackDeliveries,
              AzericardDoubles.clockAt(NOW),
              new AzericardDoubles.CountingIds()),
          callbackDeliveries,
          AzericardDoubles.clockAt(NOW),
          new AzericardDoubles.CountingIds(),
          new Authorize(payments, cards, new AzericardDoubles.CountingIds(), scenarioEngine),
          new CompleteOrReverse(payments, scenarioEngine),
          payerSessions,
          AzericardDoubles.checkoutOf(
              payerSessions,
              payments,
              cards,
              accounts,
              scenarioEngine,
              audit,
              AzericardDoubles.clockAt(NOW),
              new AzericardDoubles.CountingIds()));

  @Test
  void azericard_offers_a_hold_and_a_charge_and_names_the_trtype_it_sends_for_each() {
    assertThat(provider.originationFields().names()).containsExactly("TRTYPE=0", "TRTYPE=1");
    assertThat(provider.originationFields().wayNamed("TRTYPE=0").orElseThrow().keys())
        .containsExactly("ORDER", "AMOUNT", "CURRENCY", "DESC", "EMAIL", "BACKREF");
  }

  @Test
  void azericard_never_asks_a_tester_for_the_terminal_the_timestamp_the_nonce_or_the_signature() {
    assertThat(provider.originationFields().fieldsItFillsInItself())
        .containsOnlyKeys("TERMINAL", "TRTYPE", "TIMESTAMP", "NONCE", "P_SIGN");
    assertThat(provider.originationFields().fieldsItFillsInItself().get("P_SIGN"))
        .contains("verifies such a MAC and cannot make one");
  }

  @Test
  void a_hold_a_tester_started_is_stored_as_theirs_and_holds_the_funds_it_named() {
    aConfiguredTerminal();

    OriginationOutcome outcome = startedAs("TRTYPE=0", ORDER);

    ProviderObjectState payment = theStateOf(outcome);
    assertThat(payment.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(payment.amount().value()).isEqualTo(AMOUNT);
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_charge_a_tester_started_takes_the_funds_in_the_one_call_and_leaves_no_hold() {
    aConfiguredTerminal();

    OriginationOutcome outcome = startedAs("TRTYPE=1", ORDER);

    assertThat(theStateOf(outcome).normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void the_same_payment_started_by_a_harness_is_stored_as_the_harnesss_and_not_the_testers() {
    aConfiguredTerminal();

    OriginationOutcome outcome =
        provider.originate(asked("TRTYPE=0", ORDER, ObjectOrigin.CONTROL_API));

    assertThat(theStateOf(outcome).origin()).isEqualTo(ObjectOrigin.CONTROL_API);
  }

  @Test
  void a_payment_started_on_an_account_this_run_no_longer_configures_names_no_such_account() {
    assertThat(startedAs("TRTYPE=0", ORDER)).isEqualTo(new OriginationOutcome.NoSuchAccount());
  }

  @Test
  void an_order_azericard_cannot_read_is_refused_in_azericards_own_words_and_stores_nothing() {
    aConfiguredTerminal();

    OriginationOutcome refused = startedAs("TRTYPE=0", "12345");

    assertThat(refused)
        .isEqualTo(
            new OriginationOutcome.RefusedByTheProvider(
                AzericardErrorPage.messageFor(AzericardFields.REQUIRED_ORDER)));
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void a_second_payment_whose_trace_repeats_one_this_terminal_used_today_is_a_duplicate() {
    aConfiguredTerminal();
    startedAs("TRTYPE=0", ORDER);

    OriginationOutcome refused = startedAs("TRTYPE=0", "20260916100001");

    assertThat(refused)
        .isInstanceOf(OriginationOutcome.RefusedByTheProvider.class)
        .extracting(outcome -> ((OriginationOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .asString()
        .contains("ACTION=" + AzericardOutcomes.ACTION_DUPLICATE);
  }

  @Test
  void a_payment_a_scenario_rule_declined_carries_the_code_the_rule_named_and_that_rules_id() {
    aConfiguredTerminal();
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest()
                .naming(AzericardTransactionType.PRE_AUTHORIZATION.operationName()),
            new ScenarioAction.TerminalFailure("116"),
            null));

    OriginationOutcome refused = startedAs("TRTYPE=0", ORDER);

    assertThat(refused)
        .isInstanceOf(OriginationOutcome.RefusedByTheProvider.class)
        .extracting(outcome -> ((OriginationOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .asString()
        .contains("RC=116");
    assertThat(refused.theRuleThatDecidedIt()).isNotNull();
  }

  @Test
  void a_payment_a_scenario_rule_answered_instead_of_running_starts_nothing_and_says_so() {
    aConfiguredTerminal();
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest()
                .naming(AzericardTransactionType.PRE_AUTHORIZATION.operationName()),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    OriginationOutcome outcome = startedAs("TRTYPE=0", ORDER);

    assertThat(outcome).isInstanceOf(OriginationOutcome.AnsweredByAScenarioInstead.class);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void the_message_an_authorization_sends_is_in_the_order_azericard_declares_that_ways_fields() {
    DeclaredOrigination way =
        AzericardOrigination.DECLARED.wayNamed(AzericardOrigination.A_HOLD).orElseThrow();

    AzericardMessage message =
        AzericardOrigination.messageOf(
            aPaymentSubmittedBackToFront(way),
            AzericardDoubles.terminal(RUN, MERCHANT, TERMINAL_ID),
            new AzericardDoubles.CountingIds(),
            NOW);

    List<String> theOrderAzericardSendsThemIn = new ArrayList<>();
    theOrderAzericardSendsThemIn.add(AzericardFields.TERMINAL);
    theOrderAzericardSendsThemIn.add(AzericardFields.TRTYPE);
    theOrderAzericardSendsThemIn.addAll(way.keys());
    theOrderAzericardSendsThemIn.add(AzericardFields.TIMESTAMP);
    theOrderAzericardSendsThemIn.add(AzericardFields.NONCE);
    assertThat(message.form().values().keySet())
        .as(
            "azericard writes its message by iterating the values the payment carries, so the"
                + " terminal and the transaction type come first, the %d fields the way declares"
                + " follow in the order it declares them however the wizard happened to submit"
                + " them, and the timestamp and nonce the simulator draws come last: these were"
                + " submitted back to front, and far too many orders exist for a pass here to be"
                + " a coincidence",
            way.keys().size())
        .containsExactlyElementsOf(theOrderAzericardSendsThemIn);
  }

  private static OriginationRequest aPaymentSubmittedBackToFront(DeclaredOrigination way) {
    Map<String, String> submitted = new LinkedHashMap<>();
    for (AFieldATesterFillsIn field : way.fields().reversed()) {
      submitted.put(field.key(), theValueATesterPutsIn(field));
    }
    return OriginationControl.asked(
        AzericardOrigination.DECLARED,
        RUN,
        MERCHANT,
        way.name(),
        submitted,
        List.of(THE_TARGET_THIS_RUN_CONFIGURES),
        ObjectOrigin.UI);
  }

  private static String theValueATesterPutsIn(AFieldATesterFillsIn field) {
    if (field.namesTheCallbackTarget()) {
      return THE_TARGET_THIS_RUN_CONFIGURES;
    }
    return "what a tester typed into " + field.key();
  }

  private ProviderObjectState theStateOf(OriginationOutcome outcome) {
    assertThat(outcome).isInstanceOf(OriginationOutcome.Originated.class);
    String providerObjectId = ((OriginationOutcome.Originated) outcome).providerObjectId();
    return provider.state(RUN, providerObjectId).orElseThrow();
  }

  private OriginationOutcome startedAs(String way, String order) {
    return provider.originate(asked(way, order, ObjectOrigin.UI));
  }

  private static OriginationRequest asked(String way, String order, ObjectOrigin askedBy) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AzericardFields.ORDER, order);
    values.put(AzericardFields.AMOUNT, AMOUNT);
    values.put(AzericardFields.CURRENCY, "AZN");
    return OriginationRequest.of(RUN, MERCHANT, way, values, askedBy);
  }

  private void aConfiguredTerminal() {
    accounts.with(AzericardDoubles.terminal(RUN, MERCHANT, TERMINAL_ID));
  }
}
