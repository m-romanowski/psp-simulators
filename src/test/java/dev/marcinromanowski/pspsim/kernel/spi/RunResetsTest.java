package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class RunResetsTest {

  private static final TestRunId RUN = TestRunId.of("run-resets");

  private final List<String> happened = new ArrayList<>();

  private final UnitOfWork unit =
      new UnitOfWork() {
        @Override
        public <T> T keepingOnly(Predicate<? super T> kept, Supplier<T> work) {
          happened.add("unit opens");
          T done = work.get();
          happened.add("unit kept " + kept.test(done));
          return done;
        }
      };

  private final RunResets resets = new RunResets(unit, recording(new InMemoryScenarioRules()));

  @Test
  void one_providers_reset_holds_its_stories_first_inside_the_unit_and_answers_what_it_cleared() {
    ResetOutcome outcome = resets.ofOne(aProvider("grow"), RUN, ResetScope.TEST_RUN);

    assertThat(outcome).isEqualTo(new ResetOutcome(3, 1));
    assertThat(happened)
        .containsExactly(
            "unit opens",
            "hold grow run-resets",
            "reset grow run-resets TEST_RUN",
            "unit kept true");
  }

  @Test
  void a_reset_of_every_run_holds_every_run_of_that_provider() {
    resets.ofOne(aProvider("grow"), RUN, ResetScope.ALL);

    assertThat(happened)
        .containsExactly(
            "unit opens", "hold every run of grow", "reset grow run-resets ALL", "unit kept true");
  }

  @Test
  void every_provider_holds_its_stories_before_any_of_them_is_reset_and_all_inside_one_unit() {
    SimulatorProvider grow = aProvider("grow");
    SimulatorProvider stripe = aProvider("stripe");

    String answered =
        resets.ofEvery(
            List.of(grow, stripe),
            RUN,
            () -> {
              grow.reset(RUN, ResetScope.TEST_RUN);
              stripe.reset(RUN, ResetScope.TEST_RUN);
              return "emptied";
            });

    assertThat(answered).isEqualTo("emptied");
    assertThat(happened)
        .containsExactly(
            "unit opens",
            "hold grow run-resets",
            "hold stripe run-resets",
            "reset grow run-resets TEST_RUN",
            "reset stripe run-resets TEST_RUN",
            "unit kept true");
  }

  private SimulatorProvider aProvider(String name) {
    ProviderDouble behind = new ProviderDouble(name);
    return (SimulatorProvider)
        Proxy.newProxyInstance(
            SimulatorProvider.class.getClassLoader(),
            new Class<?>[] {SimulatorProvider.class},
            (proxy, method, arguments) -> {
              if ("reset".equals(method.getName())) {
                happened.add(
                    "reset "
                        + name
                        + " "
                        + ((TestRunId) arguments[0]).value()
                        + " "
                        + arguments[1]);
                return new ResetOutcome(3, 1);
              }
              try {
                return method.invoke(behind, arguments);
              } catch (InvocationTargetException thrown) {
                throw thrown.getCause();
              }
            });
  }

  private ScenarioRules recording(ScenarioRules behind) {
    return (ScenarioRules)
        Proxy.newProxyInstance(
            ScenarioRules.class.getClassLoader(),
            new Class<?>[] {ScenarioRules.class},
            (proxy, method, arguments) -> {
              if ("holdForAReset".equals(method.getName())) {
                happened.add("hold " + arguments[0] + " " + ((TestRunId) arguments[1]).value());
              }
              if ("holdEveryRunForAReset".equals(method.getName())) {
                happened.add("hold every run of " + arguments[0]);
              }
              try {
                return method.invoke(behind, arguments);
              } catch (InvocationTargetException thrown) {
                throw thrown.getCause();
              }
            });
  }
}
