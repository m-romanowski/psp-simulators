package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class TranzilaWiring {

  static final TestRunId RUN = TestRunId.of("tranzila-unit");

  static final String ACCOUNT = "account-1";

  static final String TERMINAL = "myterminal";

  static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

  final TranzilaDoubles.Terminals terminals = new TranzilaDoubles.Terminals();
  final TranzilaDoubles.Transactions transactions = new TranzilaDoubles.Transactions();
  final TranzilaDoubles.Nonces nonces = new TranzilaDoubles.Nonces();
  final TranzilaDoubles.Exchanges exchanges = new TranzilaDoubles.Exchanges();
  final TranzilaDoubles.Audit audit = new TranzilaDoubles.Audit();
  final TranzilaDoubles.HeldResponses held = new TranzilaDoubles.HeldResponses();
  final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  final TranzilaDoubles.MovableClock clock = new TranzilaDoubles.MovableClock(NOW);
  final TranzilaDoubles.CountingIds ids = new TranzilaDoubles.CountingIds();
  final CreateTransaction create =
      new CreateTransaction(
          transactions,
          new TranzilaPayerCards(fixtures),
          exchanges,
          ids,
          new ScenarioEngine(rules));
  final TranzilaApi api =
      new TranzilaApi(
          terminals, nonces, new TranzilaDoubles.Runs(RUN), audit, clock, ids, RUN, create);
  final TranzilaProvider provider =
      new TranzilaProvider(
          terminals, transactions, nonces, exchanges, audit, fixtures, rules, held, clock, create);

  TranzilaWiring() {
    terminals.with(
        TranzilaDoubles.terminal(
            RUN,
            ACCOUNT,
            TERMINAL,
            Map.of(
                TranzilaAuthentication.SETTING,
                TranzilaAuthentication.THE_ACCESS_TOKEN_IS_NOT_VERIFIED.name())));
  }

  TranzilaWiring configuring(Map<String, String> configuration) {
    terminals.with(TranzilaDoubles.terminal(RUN, ACCOUNT, TERMINAL, configuration));
    return this;
  }

  TranzilaTerminal terminal() {
    return terminals.find(RUN, ACCOUNT).orElseThrow();
  }

  TranzilaOperationResult create(String body) {
    return create.handle(
        TranzilaApiCall.of("POST", TranzilaApiController.CREATE, Map.of(), body),
        Map.of(),
        RUN,
        terminal(),
        clock.now(RUN),
        ObjectOrigin.PROVIDER_API);
  }

  String answered(String body) {
    return create(body).body().text();
  }

  void writes(String operation, ScenarioAction action) {
    rules.add(
        new NewScenarioRule(
            TranzilaApi.TRANZILA,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(operation),
            action,
            null));
  }

  TranzilaPayment thePaymentOf(long transactionId) {
    return transactions.paymentHolding(RUN, transactionId).orElseThrow();
  }

  List<TranzilaTransaction> stored() {
    return transactions.all();
  }

  TranzilaTransaction theLastStored() {
    List<TranzilaTransaction> all = transactions.all();
    return all.get(all.size() - 1);
  }
}
