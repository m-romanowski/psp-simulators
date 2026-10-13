package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.RecordedActions;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurations;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

class AccountFormReadsTest {

  private static final String RUN = "ui-account-form-reads";

  private final ProviderModuleDouble module = new ProviderModuleDouble("grow");
  private final Identities identities = new Identities();

  @Test
  void drawing_the_form_for_a_new_account_asks_the_provider_to_disclose_nothing() {
    Model model = new ExtendedModelMap();

    controller().form("grow", RUN, null, null, model);

    assertThat(module.theDisclosuresItWasAskedFor()).isEmpty();
    assertThat(model.getAttribute("account")).isNotNull();
  }

  @Test
  void drawing_the_form_over_an_account_that_exists_still_asks_for_no_disclosure() {
    identities.hold("merchant-1", "merchant-2", "merchant-3");
    Model model = new ExtendedModelMap();

    controller().form("grow", RUN, "merchant-2", null, model);

    assertThat(module.theDisclosuresItWasAskedFor()).isEmpty();
  }

  @Test
  void drawing_the_form_reads_the_run_identities_once_however_many_accounts_it_holds() {
    identities.hold("merchant-1", "merchant-2", "merchant-3");

    controller().form("grow", RUN, null, null, new ExtendedModelMap());

    assertThat(identities.theListReadsItWasAskedFor()).isEqualTo(1);
  }

  @Test
  void a_form_reached_with_no_provider_named_reads_no_account_of_any_run() {
    controller().form(null, RUN, null, null, new ExtendedModelMap());

    assertThat(module.theDisclosuresItWasAskedFor()).isEmpty();
    assertThat(identities.theListReadsItWasAskedFor()).isZero();
  }

  private AccountFormController controller() {
    ProviderConfigurations configurations =
        new ProviderConfigurations(
            identities,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings());
    return new AccountFormController(
        new ProviderRegistry(List.of(module)),
        identities,
        new ConfigurationAdmission(
            configurations,
            new NoClock(),
            new NoClock(),
            new NoRuns(),
            new RecordedUnitsOfWork(),
            TestRunId.of("default")),
        new RecordedActions().asTrail(),
        "default");
  }

  private static final class Identities implements ProviderAccounts {

    private final List<String> held = new ArrayList<>();
    private int listReads;

    void hold(String... accountIds) {
      held.addAll(List.of(accountIds));
    }

    int theListReadsItWasAskedFor() {
      return listReads;
    }

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      plan.store();
      held.addAll(accountIds);
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      if (!held.remove(accountId)) {
        return false;
      }
      plan.remove();
      return true;
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      listReads++;
      return List.copyOf(held);
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }
  }

  private static final class NoClock implements FixedTimes, SimulatorClock {

    @Override
    public Optional<Instant> of(TestRunId testRunId) {
      return Optional.empty();
    }

    @Override
    public void pin(TestRunId testRunId, Instant fixedNow) {}

    @Override
    public void release(TestRunId testRunId) {}

    @Override
    public Instant now(TestRunId testRunId) {
      return Instant.EPOCH;
    }
  }

  private static final class NoRuns implements TestRuns {

    @Override
    public void register(TestRunId testRunId) {}

    @Override
    public int forget(TestRunId testRunId) {
      return 0;
    }

    @Override
    public boolean exists(TestRunId testRunId) {
      return false;
    }

    @Override
    public List<TestRunId> all() {
      return List.of();
    }
  }
}
