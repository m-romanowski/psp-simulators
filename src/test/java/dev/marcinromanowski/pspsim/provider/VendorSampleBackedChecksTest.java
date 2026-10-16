package dev.marcinromanowski.pspsim.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.smoke.Applicability;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.provider.autra.AutraCompatibilityChecks;
import dev.marcinromanowski.pspsim.provider.azericard.AzericardCompatibilityChecks;
import dev.marcinromanowski.pspsim.provider.grow.GrowCompatibilityChecks;
import dev.marcinromanowski.pspsim.provider.stripe.StripeCompatibilityChecks;
import dev.marcinromanowski.pspsim.provider.tranzila.TranzilaCompatibilityChecks;
import dev.marcinromanowski.pspsim.provider.worldline.WorldlineCompatibilityChecks;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VendorSampleBackedChecksTest {

  private static final CheckJson JSON = new CheckJson();

  private static final IdGenerator IDS = new CountingIds();

  private static final SimulatorClock CLOCK = testRunId -> Instant.now();

  private static List<ContractCheck> everyCheck() {
    List<ContractCheck> checks = new ArrayList<>(new GrowCompatibilityChecks(JSON).all());
    checks.addAll(new StripeCompatibilityChecks(JSON, IDS).all());
    checks.addAll(new AzericardCompatibilityChecks(JSON, IDS, CLOCK).all());
    checks.addAll(new WorldlineCompatibilityChecks(JSON, IDS, CLOCK).all());
    checks.addAll(new AutraCompatibilityChecks(JSON, IDS).all());
    checks.addAll(new TranzilaCompatibilityChecks(JSON, IDS, CLOCK).all());
    return checks;
  }

  @Test
  void every_applicable_check_of_every_provider_expects_at_least_one_sampled_value() {
    for (ContractCheck check : everyCheck()) {
      if (check.applicability() instanceof Applicability.Applies) {
        assertThat(check.expectations())
            .describedAs("the expectations of %s %s", check.provider().value(), check.operation())
            .isNotEmpty();
      }
    }
  }

  @Test
  void a_check_that_does_not_apply_expects_nothing_and_says_why() {
    List<ContractCheck> withheld =
        everyCheck().stream()
            .filter(check -> check.applicability() instanceof Applicability.DoesNotApply)
            .toList();

    assertThat(withheld).isNotEmpty();
    for (ContractCheck check : withheld) {
      assertThat(check.expectations()).isEmpty();
      assertThat(((Applicability.DoesNotApply) check.applicability()).reason()).isNotBlank();
    }
  }

  @Test
  void every_check_reads_samples_of_its_own_provider_or_of_the_simulator_control_api() {
    for (ContractCheck check : everyCheck()) {
      String own = check.provider().value() + "/";
      assertThat(check.provenance().sample())
          .satisfiesAnyOf(
              sample -> assertThat(sample).startsWith(own),
              sample -> assertThat(sample).startsWith("simulator/"));
      for (ContractExpectation expectation : check.expectations()) {
        assertThat(expectation.published().provenance().sample())
            .describedAs("the sample %s %s reads %s", own, check.operation(), expectation.field())
            .satisfiesAnyOf(
                sample -> assertThat(sample).startsWith(own),
                sample -> assertThat(sample).startsWith("simulator/"));
      }
    }
  }
}
