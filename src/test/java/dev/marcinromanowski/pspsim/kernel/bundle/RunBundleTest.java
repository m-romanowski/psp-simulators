package dev.marcinromanowski.pspsim.kernel.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredAction;
import dev.marcinromanowski.pspsim.kernel.scenario.PortableScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class RunBundleTest {

  private static final ProviderId STRIPE = ProviderId.of("stripe");

  private static final BundleCompatibility DECLARED_HERE =
      new BundleCompatibility(
          List.of("secretKey"), List.of("payment_method"), List.of(), List.of("FIXED_SUCCESS"));

  private static final int THE_SETTINGS_A_BUNDLE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES = 12;

  private static final String
      AN_IMPORT_PLANS_A_SETTING_PER_ROW_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS =
          "taking a bundle back walks its settings in their iteration order to plan one created,"
              + " updated or skipped entry per setting, and the import answer and the"
              + " Run bundle screen list that plan in the same order, so a tester reads the"
              + " settings in the order the pasted document wrote them. Twelve settings leave 479001600 orders an"
              + " unordered copy could draw from the keys' hashes and the salt"
              + " java.util.ImmutableCollections draws once per JVM, so this assertion cannot"
              + " pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " settings are in such a map there is no way left to tell it from an ordered one,"
              + " so a bundle keeps a map whose type carries an order on every run rather than"
              + " one that happened to print in the right order on this one";

  @Test
  void a_bundle_of_this_provider_written_against_this_build_is_refused_over_nothing() {
    assertThat(
            aBundle("stripe", DECLARED_HERE).theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE))
        .isEmpty();
  }

  @Test
  void a_bundle_of_another_provider_is_refused_by_the_provider_it_names() {
    assertThat(
            aBundle("grow", DECLARED_HERE).theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("provider");
              assertThat(refusal.reason())
                  .as(
                      "a tester pasting a document exported from another provider has picked this"
                          + " provider in the bar of the screen, so a refusal that names neither"
                          + " of the two leaves them reading that provider must name the provider")
                  .contains("grow")
                  .contains("stripe")
                  .contains(RunBundle.A_BUNDLE_OF_ONE_PROVIDER_DESCRIBES_NOTHING_OF_ANOTHER);
            });
  }

  @Test
  void a_bundle_naming_no_provider_at_all_is_refused_by_the_provider_property() {
    assertThat(aBundle(null, DECLARED_HERE).theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE))
        .extracting(BundleRefusal::property)
        .containsExactly("provider");
    assertThat(
            aBundle(null, DECLARED_HERE)
                .theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE)
                .getFirst()
                .reason())
        .as(
            "a document carrying no provider cannot be reported as one exported from nothing, and"
                + " the provider this request addresses is still worth naming")
        .contains(RunBundle.THE_DOCUMENT_NAMES_NO_PROVIDER)
        .contains("stripe");
  }

  @Test
  void a_bundle_naming_the_vocabulary_of_no_build_is_refused_by_the_compatibility_property() {
    assertThat(aBundle("stripe", null).theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("compatibility");
              assertThat(refusal.reason())
                  .isEqualTo(BundleCompatibility.A_BUNDLE_NAMES_WHAT_IT_WAS_WRITTEN_AGAINST);
            });
  }

  @Test
  void a_term_this_build_no_longer_declares_refuses_the_bundle_by_the_term() {
    BundleCompatibility written =
        new BundleCompatibility(List.of("apiSecret"), List.of(), List.of(), List.of());

    assertThat(aBundle("stripe", written).theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE))
        .extracting(BundleRefusal::property)
        .containsExactly("compatibility.accountFields.apiSecret");
  }

  @Test
  void a_bundle_whose_two_halves_name_different_providers_is_refused_by_the_document_it_carries() {
    RunBundle bundle =
        new RunBundle(
            DECLARED_HERE,
            "run-a",
            "stripe",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new ScenarioDocument("run-a", "grow", null, List.of(aRule())));

    assertThat(bundle.theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("scenarios.provider");
              assertThat(refusal.reason()).isEqualTo(RunBundle.ONE_BUNDLE_NAMES_ONE_PROVIDER);
            });
  }

  @Test
  void a_document_naming_no_provider_of_its_own_is_refused_over_nothing() {
    RunBundle bundle =
        new RunBundle(
            DECLARED_HERE,
            "run-a",
            "stripe",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new ScenarioDocument("run-a", null, null, List.of(aRule())));

    assertThat(bundle.theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE)).isEmpty();
  }

  @Test
  void a_fixture_naming_a_rule_this_bundle_does_not_carry_is_refused_by_its_own_place() {
    RunBundle bundle =
        new RunBundle(
            DECLARED_HERE,
            "run-a",
            "stripe",
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(
                new PortableFixture("m", "o", "card", "a", null, Map.of(), null),
                new PortableFixture("m", "o", "card", "b", null, Map.of(), 4)),
            new ScenarioDocument("run-a", "stripe", null, List.of(aRule())));

    assertThat(bundle.theReasonsItCannotBeTakenBackBy(STRIPE, DECLARED_HERE))
        .extracting(BundleRefusal::property)
        .containsExactly("fixtures[1].triggersRuleAt");
  }

  @Test
  void a_bundle_carrying_nothing_at_all_reads_back_as_empty_under_every_entry() {
    RunBundle bundle =
        new RunBundle(
            DECLARED_HERE, "run-a", "stripe", null, null, null, null, null, null, null, null);

    assertThat(bundle.carriedAccounts()).isEmpty();
    assertThat(bundle.carriedWebhooks()).isEmpty();
    assertThat(bundle.carriedRouteAliases()).isEmpty();
    assertThat(bundle.carriedSettings()).isEmpty();
    assertThat(bundle.carriedFixtures()).isEmpty();
    assertThat(bundle.carriedRules()).isEmpty();
  }

  @Test
  void a_bundle_carrying_every_entry_reads_each_of_them_back() {
    RunBundle bundle = aBundle("stripe", DECLARED_HERE);

    assertThat(bundle.carriedAccounts()).hasSize(1);
    assertThat(bundle.carriedWebhooks()).hasSize(1);
    assertThat(bundle.carriedRouteAliases()).hasSize(1);
    assertThat(bundle.carriedSettings()).containsExactly(Map.entry("webhook.retryPlan", "NONE"));
    assertThat(bundle.carriedFixtures()).hasSize(1);
    assertThat(bundle.carriedRules()).hasSize(1);
    assertThat(bundle.clock()).isEqualTo(new DeclaredClock("2026-09-24T12:00:00Z", null, null));
  }

  @Test
  void the_collections_a_bundle_was_built_from_do_not_change_it_afterwards() {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("webhook.retryPlan", "NONE");
    RunBundle bundle =
        new RunBundle(
            DECLARED_HERE, "run-a", "stripe", null, null, null, null, null, settings, null, null);
    settings.put("webhook.ceiling", "PT1H");

    assertThat(bundle.carriedSettings()).containsOnlyKeys("webhook.retryPlan");
  }

  private static RunBundle aBundle(String provider, BundleCompatibility compatibility) {
    return new RunBundle(
        compatibility,
        "run-a",
        provider,
        "2026-09-24T12:00:00Z",
        new DeclaredClock("2026-09-24T12:00:00Z", null, null),
        List.of(new PortableAccount("merchant-1", Map.of(), List.of("secretKey"))),
        List.of(new PortableWebhookTarget("merchant-1", "https://merchant.example/hook")),
        List.of(new PortableRouteAlias("https://api.example/v1", "/v1")),
        Map.of("webhook.retryPlan", "NONE"),
        List.of(new PortableFixture("m", "o", "card", "a", null, Map.of(), 0)),
        new ScenarioDocument("run-a", provider, null, List.of(aRule())));
  }

  private static PortableScenario aRule() {
    return new PortableScenario(
        0,
        null,
        null,
        null,
        null,
        null,
        new DeclaredAction("FIXED_SUCCESS", null, null, null, null, null, null, null));
  }

  private static Map<String, String> theSettingsOfThatWidth() {
    Map<String, String> carried = new LinkedHashMap<>();
    for (int at = 1;
        at <= THE_SETTINGS_A_BUNDLE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES;
        at++) {
      carried.put("setting" + at, "the value the bundle carried " + at + "th");
    }
    return carried;
  }

  private static RunBundle aBundleCarrying(Map<String, String> settings) {
    return new RunBundle(
        DECLARED_HERE,
        "run-a",
        "stripe",
        "2026-09-24T12:00:00Z",
        null,
        List.of(),
        List.of(),
        List.of(),
        settings,
        List.of(),
        null);
  }

  @Test
  void
      a_bundle_carries_its_settings_in_the_order_the_document_wrote_them_and_twelve_settings_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, String> carried = theSettingsOfThatWidth();

    RunBundle bundle = aBundleCarrying(carried);

    assertThat(List.copyOf(bundle.carriedSettings().keySet()))
        .as(AN_IMPORT_PLANS_A_SETTING_PER_ROW_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(carried.keySet()));
  }

  @Test
  void
      a_bundle_keeps_its_settings_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    assertThat(aBundleCarrying(theSettingsOfThatWidth()).carriedSettings())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_settings_a_bundle_hands_an_import_cannot_be_written_to() {
    RunBundle bundle = aBundleCarrying(theSettingsOfThatWidth());

    assertThatThrownBy(
            () -> bundle.carriedSettings().put("setting13", "a setting the bundle never carried"))
        .as(
            "an import reads the same bundle for the plan it reports and for the configuration it"
                + " would apply, so neither may add to its settings or reorder them, which also"
                + " fails if the unmodifiable wrapper around the ordered copy is ever stripped")
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
