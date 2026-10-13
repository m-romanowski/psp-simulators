package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeclaredCheckTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");

  private static final Provenance PROVENANCE = Samples.A_PUBLISHED_EXAMPLE;

  @Test
  void an_applying_check_carries_the_expectations_it_was_declared_with_and_runs_its_observation() {
    ContractCheck check =
        DeclaredCheck.applying(
            PROVIDER,
            "POST /x",
            PROVENANCE,
            List.of(new ContractExpectation("status", Samples.published("1", PROVENANCE))),
            transport -> Map.of("status", "1"));

    assertThat(check.applicability()).isInstanceOf(Applicability.Applies.class);
    assertThat(check.expectations()).hasSize(1);
    assertThat(check.observe(request -> new CheckResponse(200, Map.of(), "{}")))
        .containsEntry("status", "1");
  }

  @Test
  void a_check_that_does_not_apply_declares_no_expectation_and_observes_nothing() {
    ContractCheck check =
        DeclaredCheck.notApplying(PROVIDER, "POST notifyUrl", PROVENANCE, "nothing signs it");

    assertThat(check.applicability()).isEqualTo(new Applicability.DoesNotApply("nothing signs it"));
    assertThat(check.provenance()).isEqualTo(PROVENANCE);
    assertThat(check.expectations()).isEmpty();
    assertThat(check.observe(request -> new CheckResponse(200, Map.of(), "{}"))).isEmpty();
  }

  @Test
  void a_check_that_does_not_apply_without_saying_why_is_refused() {
    assertThatThrownBy(() -> new Applicability.DoesNotApply(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("say why");
    assertThatThrownBy(() -> new Applicability.DoesNotApply(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("say why");
  }
}
