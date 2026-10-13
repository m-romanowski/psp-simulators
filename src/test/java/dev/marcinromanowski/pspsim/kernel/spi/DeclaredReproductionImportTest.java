package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DeclaredReproductionImportTest {

  @Test
  void an_import_writes_nothing_only_where_a_dry_run_was_asked_for_in_so_many_words() {
    assertThat(anImport(true, null, null).writesNothing()).isTrue();
    assertThat(anImport(false, null, null).writesNothing()).isFalse();
    assertThat(anImport(null, null, null).writesNothing()).isFalse();
  }

  @Test
  void an_import_supplying_no_secret_supplies_an_empty_map_rather_than_null() {
    assertThat(anImport(false, null, null).theSecretsItSupplies()).isEmpty();
    assertThat(
            anImport(false, Map.of("merchant-1", Map.of("secretKey", "sk_test_x")), null)
                .theSecretsItSupplies())
        .containsOnlyKeys("merchant-1");
  }

  @Test
  void an_import_asking_for_no_remapping_asks_for_an_empty_one_and_preserves_the_ids() {
    assertThat(anImport(false, null, null).theRemappingItAsksFor()).isEmpty();
    assertThat(anImport(false, null, null).preservesProviderNativeIds()).isTrue();
    assertThat(anImport(false, null, Map.of()).preservesProviderNativeIds()).isTrue();
  }

  @Test
  void an_import_asking_for_one_remapping_no_longer_preserves_the_ids_the_bundle_carried() {
    DeclaredReproductionImport asking = anImport(false, null, Map.of("tok_a", "tok_b"));

    assertThat(asking.theRemappingItAsksFor())
        .containsExactlyInAnyOrderEntriesOf(Map.of("tok_a", "tok_b"));
    assertThat(asking.preservesProviderNativeIds()).isFalse();
  }

  private static DeclaredReproductionImport anImport(
      Boolean dryRun,
      Map<String, Map<String, String>> secrets,
      Map<String, String> remapProviderIds) {
    return new DeclaredReproductionImport("run-b", dryRun, null, secrets, remapProviderIds);
  }
}
