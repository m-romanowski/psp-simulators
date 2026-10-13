package dev.marcinromanowski.pspsim.kernel.repro;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableCard;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableFixture;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderNativeIdsTest {

  private static final String CARRIED_TOKEN = "tok_carried";

  private static final String ANOTHER_CARRIED_TOKEN = "tok_carried_too";

  private static final String A_FREE_TOKEN = "tok_free";

  private static final String OWNER = "cus_shared";

  private static final int THE_IDS_A_FIXTURE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES = 12;

  private static final String
      A_REMAPPED_BUNDLE_IS_EXPORTED_AGAIN_AND_DIFFED_AND_TWELVE_IDS_LEAVE_479001600_ORDERS =
          "an import writes the instruments it reseeded into the run a tester then exports and"
              + " diffs against the bundle it came from, so remapping one id leaves every id"
              + " where the bundle carried it. Twelve ids leave 479001600 orders an unordered"
              + " copy could draw from the keys' hashes and the salt"
              + " java.util.ImmutableCollections draws once per JVM, so this assertion cannot"
              + " pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the ids"
              + " are in such a map there is no way left to tell it from an ordered one, so a"
              + " reseeded instrument carries a map whose type carries an order on every run"
              + " rather than one that happened to print in the right order on this one";

  @Test
  void an_import_asking_for_no_remapping_keeps_every_id_the_bundle_carries() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(List.of(carrying(CARRIED_TOKEN)), Map.of(), Set.of(), List.of());

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Preserved.class,
            preserved -> {
              assertThat(preserved.remapped()).isEmpty();
              assertThat(preserved.fixtures())
                  .singleElement()
                  .satisfies(
                      fixture ->
                          assertThat(fixture.carriedProviderIds())
                              .containsExactlyInAnyOrderEntriesOf(Map.of("token", CARRIED_TOKEN)));
            });
  }

  @Test
  void an_import_asking_for_a_remapping_writes_the_id_it_named_and_reports_the_key_it_changed() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carrying(CARRIED_TOKEN)),
            Map.of(CARRIED_TOKEN, A_FREE_TOKEN),
            Set.of(),
            List.of());

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Preserved.class,
            preserved -> {
              assertThat(preserved.remapped()).containsExactly("token");
              assertThat(preserved.fixtures().getFirst().carriedProviderIds())
                  .containsExactlyInAnyOrderEntriesOf(Map.of("token", A_FREE_TOKEN));
              assertThat(preserved.fixtures().getFirst().carriedProviderIds())
                  .doesNotContainValue(CARRIED_TOKEN);
            });
  }

  @Test
  void a_remapping_leaves_the_instruments_it_does_not_name_carrying_the_ids_they_carried() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carrying(CARRIED_TOKEN), carrying(ANOTHER_CARRIED_TOKEN)),
            Map.of(CARRIED_TOKEN, A_FREE_TOKEN),
            Set.of(),
            List.of());

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Preserved.class,
            preserved ->
                assertThat(preserved.fixtures())
                    .extracting(fixture -> fixture.carriedProviderIds().get("token"))
                    .containsExactly(A_FREE_TOKEN, ANOTHER_CARRIED_TOKEN));
  }

  @Test
  void
      an_id_the_target_run_already_gave_another_instrument_conflicts_and_names_that_instrument_and_never_the_id() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carrying(CARRIED_TOKEN)),
            Map.of(),
            Set.of(),
            List.of(new ClaimedProviderNativeId("token", CARRIED_TOKEN, "visa-success")));

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .singleElement()
                    .satisfies(
                        refusal -> {
                          assertThat(refusal.property())
                              .isEqualTo("setup.fixtures[0].providerIds.token");
                          assertThat(refusal.reason()).contains("visa-success");
                          assertThat(refusal.reason()).contains(ProviderNativeIds.REMAP);
                          assertThat(refusal.reason()).doesNotContain(CARRIED_TOKEN);
                        }));
  }

  @Test
  void
      a_remapping_onto_an_id_the_target_run_already_gave_away_conflicts_and_blames_the_remapping() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carrying(CARRIED_TOKEN)),
            Map.of(CARRIED_TOKEN, A_FREE_TOKEN),
            Set.of(),
            List.of(new ClaimedProviderNativeId("token", A_FREE_TOKEN, "visa-success")));

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .singleElement()
                    .satisfies(
                        refusal -> {
                          assertThat(refusal.property())
                              .isEqualTo("setup.fixtures[0].providerIds.token");
                          assertThat(refusal.reason()).contains("name a free one");
                          assertThat(refusal.reason()).doesNotContain(A_FREE_TOKEN);
                        }));
  }

  @Test
  void an_id_the_target_run_gave_the_instrument_in_the_second_place_conflicts_under_that_place() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carrying(A_FREE_TOKEN), carrying(CARRIED_TOKEN)),
            Map.of(),
            Set.of(),
            List.of(new ClaimedProviderNativeId("token", CARRIED_TOKEN, "visa-success")));

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("setup.fixtures[1].providerIds.token"));
  }

  @Test
  void an_id_a_key_that_names_an_owner_carries_cannot_conflict_and_is_never_remapped() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carryingAnOwner(OWNER)),
            Map.of(),
            Set.of("customer"),
            List.of(new ClaimedProviderNativeId("customer", OWNER, "visa-success")));

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Preserved.class,
            preserved -> assertThat(preserved.remapped()).isEmpty());
  }

  @Test
  void a_remapping_of_an_id_only_a_key_that_names_an_owner_carries_is_refused_as_a_rename() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carryingAnOwner(OWNER)),
            Map.of(OWNER, "cus_other"),
            Set.of("customer"),
            List.of());

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .singleElement()
                    .satisfies(
                        refusal -> {
                          assertThat(refusal.property())
                              .isEqualTo(ProviderNativeIds.REMAP + "." + OWNER);
                          assertThat(refusal.reason())
                              .isEqualTo(
                                  ProviderNativeIds.A_KEY_THAT_NAMES_AN_OWNER_IS_SHARED_ON_PURPOSE);
                        }));
  }

  @Test
  void a_remapping_of_an_id_no_instrument_of_this_bundle_carries_is_refused_by_the_line_it_named() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carrying(CARRIED_TOKEN)),
            Map.of("tok_nothing", A_FREE_TOKEN),
            Set.of(),
            List.of());

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .singleElement()
                    .satisfies(
                        refusal -> {
                          assertThat(refusal.property())
                              .isEqualTo(ProviderNativeIds.REMAP + ".tok_nothing");
                          assertThat(refusal.reason())
                              .isEqualTo(
                                  ProviderNativeIds.NOTHING_THIS_BUNDLE_CARRIES_ANSWERS_TO_IT);
                        }));
  }

  @Test
  void a_remapping_this_bundle_names_nothing_for_is_refused_before_any_conflict_is_reported() {
    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carrying(CARRIED_TOKEN)),
            Map.of("tok_nothing", A_FREE_TOKEN),
            Set.of(),
            List.of(new ClaimedProviderNativeId("token", CARRIED_TOKEN, "visa-success")));

    assertThat(preservation).isInstanceOf(ProviderNativeIds.Preservation.Refused.class);
  }

  @Test
  void a_bundle_carrying_no_instrument_preserves_nothing_and_refuses_nothing() {
    assertThat(ProviderNativeIds.of(List.of(), Map.of(), Set.of(), List.of()))
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Preserved.class,
            preserved -> {
              assertThat(preserved.fixtures()).isEmpty();
              assertThat(preserved.remapped()).isEmpty();
            });
  }

  @Test
  void the_refusals_a_preservation_was_built_from_do_not_change_it_afterwards() {
    List<BundleRefusal> refusals =
        new ArrayList<>(List.of(new BundleRefusal("remapProviderIds.a", "no")));
    ProviderNativeIds.Preservation.Refused refused =
        new ProviderNativeIds.Preservation.Refused(refusals);
    ProviderNativeIds.Preservation.Conflicting conflicting =
        new ProviderNativeIds.Preservation.Conflicting(refusals);
    refusals.add(new BundleRefusal("remapProviderIds.b", "no"));

    assertThat(refused.refusals()).hasSize(1);
    assertThat(conflicting.refusals()).hasSize(1);
  }

  @Test
  void the_property_a_conflict_names_is_the_place_of_the_instrument_and_the_key_of_the_id() {
    assertThat(ProviderNativeIds.theProperty(3, "token"))
        .isEqualTo("setup.fixtures[3].providerIds.token");
  }

  private static SequencedMap<String, String> theIdsOfThatWidth(String token) {
    LinkedHashMap<String, String> carried = new LinkedHashMap<>();
    for (int at = THE_IDS_A_FIXTURE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES; at >= 2; at--) {
      carried.put("providerId" + at, "the id the bundle carried " + at + "th");
    }
    carried.put("token", token);
    return carried;
  }

  @Test
  void
      a_reseeded_instrument_carries_every_id_in_the_order_the_bundle_carried_them_and_twelve_ids_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    SequencedMap<String, String> carried = theIdsOfThatWidth(CARRIED_TOKEN);

    ProviderNativeIds.Preservation preservation =
        ProviderNativeIds.of(
            List.of(carryingAll(carried)),
            Map.of(CARRIED_TOKEN, A_FREE_TOKEN),
            Set.of(),
            List.of());

    assertThat(preservation)
        .isInstanceOfSatisfying(
            ProviderNativeIds.Preservation.Preserved.class,
            preserved -> {
              assertThat(List.copyOf(preserved.fixtures().getFirst().carriedProviderIds().keySet()))
                  .as(
                      A_REMAPPED_BUNDLE_IS_EXPORTED_AGAIN_AND_DIFFED_AND_TWELVE_IDS_LEAVE_479001600_ORDERS)
                  .isEqualTo(List.copyOf(carried.keySet()));
              assertThat(preserved.fixtures().getFirst().carriedProviderIds())
                  .as(
                      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
                  .isInstanceOf(SequencedMap.class);
              assertThat(preserved.fixtures().getFirst().carriedProviderIds())
                  .containsEntry("token", A_FREE_TOKEN);
            });
  }

  private static PortableFixture carryingAll(Map<String, String> ids) {
    return new PortableFixture(
        "merchant-1",
        "owner-1",
        "card",
        "visa-many-ids",
        new PortableCard("visa", "4242", 12, 2031, "credit"),
        ids,
        null);
  }

  private static PortableFixture carrying(String token) {
    return new PortableFixture(
        "merchant-1",
        "owner-1",
        "card",
        "visa-" + token,
        new PortableCard("visa", "4242", 12, 2031, "credit"),
        Map.of("token", token),
        null);
  }

  private static PortableFixture carryingAnOwner(String customer) {
    return new PortableFixture(
        "merchant-1",
        "owner-1",
        "card",
        "visa-owned",
        new PortableCard("visa", "4242", 12, 2031, "credit"),
        Map.of("customer", customer),
        null);
  }
}
