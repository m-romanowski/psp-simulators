package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AzericardKeyMechanismsTest {

  private static final TestRunId RUN = TestRunId.of("azericard-run");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
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
          new Authorize(
              payments,
              cards,
              new AzericardDoubles.CountingIds(),
              new ScenarioEngine(scenarioRules)),
          new CompleteOrReverse(payments, new ScenarioEngine(scenarioRules)),
          payerSessions,
          AzericardDoubles.checkoutOf(
              payerSessions,
              payments,
              cards,
              accounts,
              new ScenarioEngine(scenarioRules),
              audit,
              AzericardDoubles.clockAt(NOW),
              new AzericardDoubles.CountingIds()));

  @Test
  void a_provider_with_no_idempotency_key_at_all_says_so_rather_than_showing_an_empty_row() {
    assertThat(AzericardKeyMechanisms.MECHANISMS.has(KeyRole.TRUE_IDEMPOTENCY_KEY)).isFalse();
    assertThat(AzericardKeyMechanisms.MECHANISMS.theAbsenceOf(KeyRole.TRUE_IDEMPOTENCY_KEY))
        .contains("publishes no idempotency header")
        .contains("ORDER is a duplicate check whose repeat is refused")
        .contains("TRTYPE=90");
  }

  @Test
  void the_order_a_merchant_chooses_is_declared_a_duplicate_check_and_never_a_replay() {
    DeclaredKey order =
        AzericardKeyMechanisms.MECHANISMS.key(AzericardKeyMechanisms.ORDER).orElseThrow();

    assertThat(order.role()).isEqualTo(KeyRole.DUPLICATE_DETECTION_KEY);
    assertThat(order.role().whatARepeatDoes()).contains("refused and never replayed");
    assertThat(order.scopedBy())
        .contains("last " + AzericardOrderTrace.TRACE_LENGTH + " digits")
        .contains("unique within a day for the terminal")
        .contains("ACTION=1");
  }

  @Test
  void the_references_a_completion_carries_are_declared_correlation_and_not_duplicate_checks() {
    assertThat(AzericardKeyMechanisms.MECHANISMS.inTheRoleOf(KeyRole.CORRELATION_KEY))
        .extracting(DeclaredKey::name)
        .containsExactly(AzericardKeyMechanisms.RRN, AzericardKeyMechanisms.INT_REF);
    assertThat(AzericardKeyMechanisms.MECHANISMS.roleOf(AzericardKeyMechanisms.RRN))
        .isEqualTo(KeyRole.CORRELATION_KEY);
  }

  @Test
  void a_provider_that_records_no_exchange_of_its_own_still_answers_the_inspector() {
    assertThat(
            provider
                .keyedExchanges(new KeyedExchangeQuery(RUN, KeyedExchangeQuery.FROM_THE_NEWEST, 50))
                .exchanges())
        .isEmpty();
    assertThat(provider.keyMechanisms()).isSameAs(AzericardKeyMechanisms.MECHANISMS);
  }
}
