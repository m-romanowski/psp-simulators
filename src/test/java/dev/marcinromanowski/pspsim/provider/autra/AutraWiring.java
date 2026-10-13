package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

final class AutraWiring {

  static final String PUBLIC_BASE_URL = "https://simulator.test";

  private AutraWiring() {}

  static AutraApi api(
      AutraMerchants merchants,
      AutraSessions sessions,
      AutraPayments payments,
      AuditTrail audit,
      TestRunId defaultRun,
      Instant now) {
    return api(
        merchants,
        sessions,
        payments,
        audit,
        defaultRun,
        now,
        new ScenarioEngine(new InMemoryScenarioRules()));
  }

  static AutraApi api(
      AutraMerchants merchants,
      AutraSessions sessions,
      AutraPayments payments,
      AuditTrail audit,
      TestRunId defaultRun,
      Instant now,
      ScenarioEngine scenarios) {
    return api(
        merchants,
        sessions,
        payments,
        audit,
        defaultRun,
        now,
        scenarios,
        new AutraDoubles.Runs(defaultRun));
  }

  static AutraApi api(
      AutraMerchants merchants,
      AutraSessions sessions,
      AutraPayments payments,
      AuditTrail audit,
      TestRunId defaultRun,
      Instant now,
      ScenarioEngine scenarios,
      AutraDoubles.Runs runs) {
    return api(
        merchants,
        sessions,
        payments,
        new AutraDoubles.PixPayments(),
        audit,
        defaultRun,
        now,
        scenarios,
        runs);
  }

  static AutraApi api(
      AutraMerchants merchants,
      AutraSessions sessions,
      AutraPayments payments,
      AutraPixPayments pixPayments,
      AuditTrail audit,
      TestRunId defaultRun,
      Instant now,
      ScenarioEngine scenarios,
      AutraDoubles.Runs runs) {
    return api(
        merchants,
        sessions,
        payments,
        pixPayments,
        new AutraDoubles.CardSessions(),
        audit,
        defaultRun,
        now,
        scenarios,
        runs);
  }

  static AutraApi api(
      AutraMerchants merchants,
      AutraSessions sessions,
      AutraPayments payments,
      AutraPixPayments pixPayments,
      AutraCardSessions cardSessions,
      AuditTrail audit,
      TestRunId defaultRun,
      Instant now,
      ScenarioEngine scenarios,
      AutraDoubles.Runs runs) {
    IdGenerator ids = new AutraDoubles.CountingIds();
    return new AutraApi(
        merchants,
        sessions,
        runs,
        audit,
        AutraDoubles.clockAt(now),
        ids,
        defaultRun,
        new IssueAccessToken(sessions, ids),
        new OpenAcquiringSession(sessions, ids, scenarios),
        new OpenCardSession(cardSessions, ids, scenarios, PUBLIC_BASE_URL),
        new ChargeStoredCard(
            payments,
            sessions,
            new AutraStoredCards(
                cardSessions, AutraDoubles.fixturesHoldingTheExamplePair(defaultRun)),
            ids,
            scenarios),
        new PreAuthorize(
            payments,
            sessions,
            new AutraStoredCards(
                cardSessions, AutraDoubles.fixturesHoldingTheExamplePair(defaultRun)),
            ids,
            scenarios),
        new CaptureHold(payments, scenarios),
        new ReverseOrRelease(payments, scenarios),
        new CreatePixCheckout(pixPayments, sessions, ids, scenarios),
        new LookUpPayment(payments),
        new ListPayments(payments));
  }
}
