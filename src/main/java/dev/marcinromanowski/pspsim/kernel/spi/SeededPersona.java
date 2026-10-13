package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;

public record SeededPersona(
    String name,
    PersonaIntent intent,
    TestRunId testRunId,
    List<ScenarioRule> rules,
    List<PaymentMethodFixture> fixtures) {

  public SeededPersona {
    rules = List.copyOf(rules);
    fixtures = List.copyOf(fixtures);
  }

  public List<String> theScenarioIdsItWrote() {
    List<String> ids = new ArrayList<>();
    for (ScenarioRule rule : rules) {
      ids.add(rule.scenarioId());
    }
    return List.copyOf(ids);
  }

  public List<String> theFixtureIdsItSeeded() {
    List<String> ids = new ArrayList<>();
    for (PaymentMethodFixture fixture : fixtures) {
      ids.add(fixture.fixtureId());
    }
    return List.copyOf(ids);
  }
}
