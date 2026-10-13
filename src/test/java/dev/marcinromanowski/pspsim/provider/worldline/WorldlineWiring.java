package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

final class WorldlineWiring {

  private WorldlineWiring() {}

  static WorldlineApi api(
      WorldlineMerchants merchants,
      WorldlineTokenizations sessions,
      WorldlinePayments payments,
      WorldlineIdempotenceKeys keys,
      AuditTrail audit,
      TestRunId defaultRun,
      Instant now) {
    return api(
        merchants,
        sessions,
        payments,
        keys,
        audit,
        defaultRun,
        now,
        new ScenarioEngine(new InMemoryScenarioRules()),
        new WorldlineDoubles.CountingIds(),
        new InMemoryPaymentMethodFixtures());
  }

  static WorldlineApi api(
      WorldlineMerchants merchants,
      WorldlineTokenizations sessions,
      WorldlinePayments payments,
      WorldlineIdempotenceKeys keys,
      AuditTrail audit,
      TestRunId defaultRun,
      Instant now,
      ScenarioEngine scenarios,
      IdGenerator ids,
      InMemoryPaymentMethodFixtures fixtures) {
    WorldlineAnswers answers = new WorldlineAnswers(ids);
    WorldlinePayerCards cards = new WorldlinePayerCards(fixtures);
    return new WorldlineApi(
        merchants,
        new WorldlineDoubles.Runs(defaultRun),
        audit,
        WorldlineDoubles.clockAt(now),
        ids,
        answers,
        defaultRun,
        new CreateHostedTokenization(
            sessions, answers, ids, scenarios, WorldlineWire.PUBLIC_BASE_URL),
        new GetHostedTokenization(sessions, answers, scenarios),
        new CreatePayment(payments, sessions, cards, keys, answers, ids, scenarios),
        new CapturePayment(payments, answers, keys, scenarios),
        new CancelPayment(payments, answers, keys, scenarios));
  }
}
