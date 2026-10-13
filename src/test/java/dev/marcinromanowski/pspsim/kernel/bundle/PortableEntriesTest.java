package dev.marcinromanowski.pspsim.kernel.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PortableEntriesTest {

  private static final FixtureCard VISA = new FixtureCard("visa", "4242", 12, 2031, "credit");

  private static final int THE_FIELDS_A_CONFIGURATION_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_HAS =
      12;

  private static final String
      AN_IMPORT_NAMES_A_REFUSED_FIELD_PER_ROW_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS =
          "taking a bundle back walks an account's configuration in its iteration order to name"
              + " the fields this provider reads nothing of and the secrets a bundle may not"
              + " carry, one refusal per field, and the import answer and the Run bundle screen"
              + " list those refusals in that order, so a tester reads them in the order the"
              + " pasted document wrote the fields. Twelve fields leave 479001600 orders an"
              + " unordered copy could draw from the keys' hashes and the salt"
              + " java.util.ImmutableCollections draws once per JVM, so this assertion cannot"
              + " pass by coincidence on a build that copies them unordered";

  private static final String
      THE_FACTS_AN_IMPORT_RECORDS_NAME_A_FIELD_PER_ROW_IN_THE_ORDER_THE_CONFIGURATION_CARRIES_THEM =
          "the configuration an import would apply is walked in its iteration order to build the"
              + " facts the trail records for the import and the refusal a provider raises over a"
              + " key it reads nothing of, so the merged configuration keeps the fields the"
              + " bundle carried in the order it carried them and names the supplied secrets"
              + " after them";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " fields are in such a map there is no way left to tell it from an ordered one,"
              + " so an account keeps and hands out a map whose type carries an order on every"
              + " run rather than one that happened to print in the right order on this one";

  private static final int THE_IDS_A_FIXTURE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES = 12;

  private static final String
      A_BUNDLE_IS_EXPORTED_TWICE_AND_DIFFED_AND_TWELVE_IDS_LEAVE_479001600_ORDERS =
          "a tester exports one run twice and diffs the two documents to reproduce a failing run,"
              + " so a fixture names the provider-native ids it carries in the order the seed"
              + " declared them rather than in one each JVM draws anew. Twelve ids leave"
              + " 479001600 orders an unordered copy could draw from the keys' hashes and the"
              + " salt java.util.ImmutableCollections draws once per JVM, so this assertion"
              + " cannot pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT_FOR_IDS =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the ids"
              + " are in such a map there is no way left to tell it from an ordered one, so a"
              + " fixture keeps and hands out a map whose type carries an order on every run"
              + " rather than one that happened to print in the right order on this one";

  @Test
  void
      a_fixture_carries_the_provider_native_ids_the_seed_declared_in_that_order_and_twelve_ids_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    SequencedMap<String, String> declared = theIdsOfThatWidth();

    PaymentMethodFixture seeded = aFixtureCarrying(declared);
    PortableFixture carried = PortableFixture.of(seeded, null);

    assertThat(List.copyOf(seeded.providerIds().keySet()))
        .as(A_BUNDLE_IS_EXPORTED_TWICE_AND_DIFFED_AND_TWELVE_IDS_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(declared.keySet()));
    assertThat(List.copyOf(carried.carriedProviderIds().keySet()))
        .as(A_BUNDLE_IS_EXPORTED_TWICE_AND_DIFFED_AND_TWELVE_IDS_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(declared.keySet()));
  }

  @Test
  void the_document_a_bundle_is_written_as_names_those_ids_in_the_order_the_seed_declared_them() {
    SequencedMap<String, String> declared = theIdsOfThatWidth();

    String document =
        new ObjectMapper().writeValueAsString(PortableFixture.of(aFixtureCarrying(declared), null));

    assertThat(theOrderTheKeysAppearIn(document, declared))
        .as(A_BUNDLE_IS_EXPORTED_TWICE_AND_DIFFED_AND_TWELVE_IDS_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(declared.keySet()));
  }

  @Test
  void
      a_fixture_keeps_the_provider_native_ids_it_carries_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    PortableFixture carried = PortableFixture.of(aFixtureCarrying(theIdsOfThatWidth()), null);

    assertThat(carried.carriedProviderIds())
        .as(
            AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT_FOR_IDS)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_provider_native_ids_a_fixture_hands_an_import_cannot_be_written_to() {
    PortableFixture carried = PortableFixture.of(aFixtureCarrying(theIdsOfThatWidth()), null);

    assertThatThrownBy(
            () -> carried.carriedProviderIds().put("providerId13", "an id no seed named"))
        .as(
            "an import reads the same fixture to claim each id and to seed the instrument, so"
                + " neither may add to it or reorder it, which also fails if the unmodifiable"
                + " wrapper around the ordered copy is ever stripped")
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static SequencedMap<String, String> theIdsOfThatWidth() {
    LinkedHashMap<String, String> declared = new LinkedHashMap<>();
    for (int at = THE_IDS_A_FIXTURE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES; at >= 1; at--) {
      declared.put("providerId" + at, "the id the seed declared " + at + "th");
    }
    return declared;
  }

  private static List<String> theOrderTheKeysAppearIn(
      String document, Map<String, String> declared) {
    List<String> appearing = new ArrayList<>(declared.keySet());
    for (String key : appearing) {
      assertThat(document).contains('"' + key + '"');
    }
    appearing.sort(Comparator.comparingInt(key -> document.indexOf('"' + key + '"')));
    return List.copyOf(appearing);
  }

  private static PaymentMethodFixture aFixtureCarrying(Map<String, String> providerIds) {
    return new PaymentMethodFixture(
        "fx_1",
        ProviderId.of("stripe"),
        TestRunId.of("run-a"),
        "merchant-1",
        "owner-1",
        PaymentMethodKind.CARD,
        "visa-success",
        VISA,
        providerIds,
        null,
        Instant.EPOCH);
  }

  @Test
  void an_account_carrying_no_configuration_and_naming_no_secret_reads_back_as_empty() {
    PortableAccount account = new PortableAccount("merchant-1", null, null);

    assertThat(account.carriedConfiguration()).isEmpty();
    assertThat(account.theSecretsItNames()).isEmpty();
  }

  @Test
  void an_account_takes_a_supplied_secret_beside_the_fields_it_carries() {
    PortableAccount account =
        new PortableAccount("merchant-1", Map.of("capture.window", "P3D"), List.of("secretKey"));

    assertThat(account.alsoCarrying(Map.of("secretKey", "sk_test_supplied")))
        .containsEntry("capture.window", "P3D")
        .containsEntry("secretKey", "sk_test_supplied")
        .hasSize(2);
  }

  @Test
  void a_supplied_value_wins_over_a_field_of_the_same_name_the_bundle_carried() {
    PortableAccount account =
        new PortableAccount("merchant-1", Map.of("capture.window", "P3D"), List.of());

    assertThat(account.alsoCarrying(Map.of("capture.window", "P1D")))
        .containsExactly(Map.entry("capture.window", "P1D"));
  }

  @Test
  void an_account_supplied_nothing_carries_exactly_what_the_bundle_held() {
    PortableAccount account =
        new PortableAccount("merchant-1", Map.of("capture.window", "P3D"), List.of("secretKey"));

    assertThat(account.alsoCarrying(Map.of())).containsExactly(Map.entry("capture.window", "P3D"));
    assertThat(account.theSecretsItNames()).containsExactly("secretKey");
  }

  @Test
  void the_map_an_account_was_built_from_does_not_change_it_afterwards() {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put("capture.window", "P3D");
    PortableAccount account = new PortableAccount("merchant-1", configuration, List.of());
    configuration.put("secretKey", "sk_test_smuggled");

    assertThat(account.carriedConfiguration()).containsOnlyKeys("capture.window");
  }

  @Test
  void a_card_travels_as_the_four_digits_and_the_descriptors_a_fixture_holds() {
    PortableCard card = PortableCard.of(VISA);

    assertThat(card.brand()).isEqualTo("visa");
    assertThat(card.last4()).isEqualTo("4242");
    assertThat(card.expMonth()).isEqualTo(12);
    assertThat(card.expYear()).isEqualTo(2031);
    assertThat(card.funding()).isEqualTo("credit");
  }

  @Test
  void a_fixture_travels_with_its_card_its_owner_and_the_provider_native_ids_it_carries() {
    PortableFixture carried = PortableFixture.of(aFixture(VISA, null), null);

    assertThat(carried.merchantId()).isEqualTo("merchant-1");
    assertThat(carried.ownerId()).isEqualTo("owner-1");
    assertThat(carried.type()).isEqualTo("card");
    assertThat(carried.alias()).isEqualTo("visa-success");
    assertThat(carried.card()).isEqualTo(PortableCard.of(VISA));
    assertThat(carried.carriedProviderIds()).containsExactly(Map.entry("token", "tok_1"));
    assertThat(carried.triggersRuleAt()).isNull();
  }

  @Test
  void a_fixture_with_no_card_carries_none_and_a_fixture_naming_no_id_carries_none() {
    PaymentMethodFixture walletWithNoIds =
        new PaymentMethodFixture(
            "fx_1",
            ProviderId.of("stripe"),
            TestRunId.of("run-a"),
            "merchant-1",
            "owner-1",
            PaymentMethodKind.WALLET,
            "wallet-1",
            null,
            Map.of(),
            null,
            Instant.EPOCH);

    PortableFixture carried = PortableFixture.of(walletWithNoIds, null);

    assertThat(carried.card()).isNull();
    assertThat(carried.carriedProviderIds()).isEmpty();
    assertThat(carried.type()).isEqualTo("wallet");
  }

  @Test
  void a_fixture_naming_a_rule_this_bundle_carries_is_refused_over_nothing() {
    PortableFixture carried = PortableFixture.of(aFixture(VISA, "scn_1"), 1);

    assertThat(carried.theRuleItNamesThatThisBundleHasNot("fixtures[0]", 2)).isEmpty();
  }

  @Test
  void a_fixture_naming_no_rule_at_all_is_refused_over_nothing_even_where_none_is_carried() {
    PortableFixture carried = PortableFixture.of(aFixture(VISA, null), null);

    assertThat(carried.theRuleItNamesThatThisBundleHasNot("fixtures[0]", 0)).isEmpty();
  }

  @Test
  void a_fixture_naming_a_place_past_the_rules_this_bundle_carries_is_refused_by_that_property() {
    PortableFixture carried = PortableFixture.of(aFixture(VISA, "scn_1"), 2);

    assertThat(carried.theRuleItNamesThatThisBundleHasNot("fixtures[3]", 2))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("fixtures[3].triggersRuleAt");
              assertThat(refusal.reason())
                  .isEqualTo(PortableFixture.A_FIXTURE_NAMES_A_RULE_BY_ITS_PLACE);
            });
  }

  @Test
  void a_fixture_naming_a_place_before_the_first_rule_is_refused_by_that_property() {
    PortableFixture carried = PortableFixture.of(aFixture(VISA, "scn_1"), -1);

    assertThat(carried.theRuleItNamesThatThisBundleHasNot("fixtures[0]", 2))
        .extracting(BundleRefusal::property)
        .containsExactly("fixtures[0].triggersRuleAt");
  }

  private static PaymentMethodFixture aFixture(FixtureCard card, String defaultScenario) {
    return new PaymentMethodFixture(
        "fx_1",
        ProviderId.of("stripe"),
        TestRunId.of("run-a"),
        "merchant-1",
        "owner-1",
        PaymentMethodKind.CARD,
        "visa-success",
        card,
        Map.of("token", "tok_1"),
        defaultScenario,
        Instant.EPOCH);
  }

  private static Map<String, String> aConfigurationOfThatWidth() {
    Map<String, String> carried = new LinkedHashMap<>();
    for (int at = 1;
        at <= THE_FIELDS_A_CONFIGURATION_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_HAS;
        at++) {
      carried.put("field" + at, "the value the bundle carried " + at + "th");
    }
    return carried;
  }

  @Test
  void
      an_account_carries_its_configuration_in_the_order_the_bundle_wrote_it_and_twelve_fields_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, String> carried = aConfigurationOfThatWidth();

    PortableAccount account = new PortableAccount("merchant-1", carried, List.of());

    assertThat(List.copyOf(account.carriedConfiguration().keySet()))
        .as(AN_IMPORT_NAMES_A_REFUSED_FIELD_PER_ROW_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(carried.keySet()));
  }

  @Test
  void
      an_account_merges_a_supplied_secret_after_the_fields_it_carries_and_keeps_their_order_before_it() {
    PortableAccount account =
        new PortableAccount("merchant-1", aConfigurationOfThatWidth(), List.of("secretKey"));

    Map<String, String> together = account.alsoCarrying(Map.of("secretKey", "sk_test_supplied"));

    List<String> inTheOrderTheImportWouldApplyThem =
        new ArrayList<>(aConfigurationOfThatWidth().keySet());
    inTheOrderTheImportWouldApplyThem.add("secretKey");
    assertThat(List.copyOf(together.keySet()))
        .as(
            THE_FACTS_AN_IMPORT_RECORDS_NAME_A_FIELD_PER_ROW_IN_THE_ORDER_THE_CONFIGURATION_CARRIES_THEM)
        .isEqualTo(inTheOrderTheImportWouldApplyThem);
  }

  @Test
  void
      an_account_keeps_its_configuration_and_its_merged_configuration_in_maps_that_carry_an_order_rather_than_ones_drawn_from_a_per_jvm_salt() {
    PortableAccount account =
        new PortableAccount("merchant-1", aConfigurationOfThatWidth(), List.of());

    assertThat(account.carriedConfiguration())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
    assertThat(account.alsoCarrying(Map.of("secretKey", "sk_test_supplied")))
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_configuration_an_account_hands_an_import_cannot_be_written_to() {
    PortableAccount account =
        new PortableAccount("merchant-1", aConfigurationOfThatWidth(), List.of());

    assertThatThrownBy(
            () -> account.carriedConfiguration().put("field13", "a field the bundle never carried"))
        .as(
            "an import reads the same account for its refusals, for the configuration it would"
                + " apply and for the facts it records, so none of them may add to it or reorder"
                + " it, which also fails if the unmodifiable wrapper around the ordered copy is"
                + " ever stripped")
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                account
                    .alsoCarrying(Map.of("secretKey", "sk_test_supplied"))
                    .put("field13", "a field the bundle never carried"))
        .as(
            "the merged configuration is handed to the provider that would apply it, so it may"
                + " not be added to or reordered either, which also fails if the unmodifiable"
                + " wrapper around the ordered copy is ever stripped")
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
