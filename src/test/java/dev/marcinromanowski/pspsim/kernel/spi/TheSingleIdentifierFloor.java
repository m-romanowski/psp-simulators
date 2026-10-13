package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

public final class TheSingleIdentifierFloor {

  private TheSingleIdentifierFloor() {}

  public static void holdsFor(
      SimulatorProvider module, TestRunId testRunId, String providerObjectId) {
    ProviderObjectState object = module.state(testRunId, providerObjectId).orElseThrow();

    assertThat(object.identity().keepsOneIdentifierForLife()).isTrue();
    assertThat(object.identity().members()).containsExactly(providerObjectId);
    assertThat(object.identity().canonical()).isEqualTo(providerObjectId);
    assertThat(object.providerObjectId()).isEqualTo(providerObjectId);

    AddressedProviderObject addressed =
        AddressedProviderObject.read(module, testRunId, providerObjectId).orElseThrow();

    assertThat(addressed.addressedBy()).isEqualTo(providerObjectId);
    assertThat(addressed.addressedByItsCanonicalMember()).isTrue();
    assertThat(addressed.answersTo()).containsExactly(providerObjectId);
  }

  public static void holdsForEveryObjectOf(SimulatorProvider module, TestRunId testRunId) {
    ProviderObjectPage page =
        module.objects(new ProviderObjectQuery(testRunId, null, ProviderObjectQuery.DEFAULT_LIMIT));
    assertThat(page).isInstanceOf(ProviderObjectPage.Listed.class);
    ProviderObjectPage.Listed listed = (ProviderObjectPage.Listed) page;
    assertThat(listed.objects()).isNotEmpty();
    for (ProviderObjectState object : listed.objects()) {
      holdsFor(module, testRunId, object.providerObjectId());
    }
  }
}
