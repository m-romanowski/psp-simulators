package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentMethodFixtureGrammarTest {

  private static final ProviderId PROVIDER = ProviderId.of("twokeys");

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final int THE_IDS_A_FIXTURE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES = 12;

  private static final String
      A_SEEDED_ID_IS_READ_BACK_ON_A_SCREEN_AND_IN_A_BUNDLE_AND_TWELVE_LEAVE_479001600_ORDERS =
          "the Payment methods screen lists one row per provider-native id in the order the"
              + " fixture carries them, the store writes them into one JSON column in that order"
              + " and an exported bundle names them in that order, so a tester reads the ids of a"
              + " seeded instrument where the seed declared them. Twelve ids leave 479001600"
              + " orders an unordered copy could draw from the keys' hashes and the salt"
              + " java.util.ImmutableCollections draws once per JVM, so this assertion cannot"
              + " pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the ids"
              + " are in such a map there is no way left to tell it from an ordered one, so a"
              + " fixture keeps and hands out a map whose type carries an order on every run"
              + " rather than one that happened to print in the right order on this one";

  @Test
  void a_card_a_tester_described_is_read_as_the_brand_the_last_four_the_expiry_and_the_funding() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER, RUN, aCard(aVisa()), ObjectOrigin.CONTROL_API);

    FixtureCard card = fixture.card();
    assertThat(card).isNotNull();
    assertThat(card.brand()).isEqualTo("visa");
    assertThat(card.last4()).isEqualTo("1111");
    assertThat(card.expMonth()).isEqualTo(12);
    assertThat(card.expYear()).isEqualTo(2030);
    assertThat(card.funding()).isEqualTo("credit");
  }

  @ParameterizedTest(name = "refuses a card described by last4 {0}")
  @ValueSource(strings = {"4111111111111111", "111", "11111", "11a1", "", "  "})
  void a_fixture_holds_the_last_four_digits_and_never_a_card_number(String last4) {
    DeclaredPaymentMethodFixture declared =
        aCard(new DeclaredCard("visa", last4, 12, 2030, "credit"));

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "refuses the expiry month {0}")
  @ValueSource(ints = {0, 13, -1})
  void an_expiry_month_that_is_no_month_is_refused(int month) {
    DeclaredPaymentMethodFixture declared =
        aCard(new DeclaredCard("visa", "1111", month, 2030, "credit"));

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("card.expMonth");
  }

  @ParameterizedTest(name = "refuses the expiry year {0}")
  @ValueSource(ints = {30, 203, 20300})
  void an_expiry_year_that_is_not_four_digits_is_refused(int year) {
    DeclaredPaymentMethodFixture declared =
        aCard(new DeclaredCard("visa", "1111", 12, year, "credit"));

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("card.expYear");
  }

  @Test
  void a_card_that_has_already_expired_is_accepted_because_a_tester_may_want_exactly_that() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER,
            RUN,
            aCard(new DeclaredCard("visa", "1111", 1, 2001, "credit")),
            ObjectOrigin.CONTROL_API);

    assertThat(fixture.card()).isNotNull();
    assertThat(fixture.card().expYear()).isEqualTo(2001);
  }

  @Test
  void a_brand_exactly_as_long_as_a_provider_prints_is_taken() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER,
            RUN,
            aCard(
                new DeclaredCard(
                    "v".repeat(FixtureCard.LONGEST_DESCRIPTOR), "1111", 12, 2030, "credit")),
            ObjectOrigin.CONTROL_API);

    assertThat(fixture.card()).isNotNull();
    assertThat(fixture.card().brand()).hasSize(FixtureCard.LONGEST_DESCRIPTOR);
  }

  @Test
  void an_identity_exactly_as_long_as_the_store_holds_is_taken() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER,
            RUN,
            new DeclaredPaymentMethodFixture(
                RUN.value(),
                "m".repeat(PaymentMethodFixtureGrammar.LONGEST_IDENTITY),
                "customer-123",
                "card",
                "visa-success",
                aVisa(),
                Map.of(),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(fixture.merchantId()).hasSize(PaymentMethodFixtureGrammar.LONGEST_IDENTITY);
  }

  @Test
  void a_brand_longer_than_a_provider_would_ever_print_is_refused() {
    DeclaredPaymentMethodFixture declared =
        aCard(
            new DeclaredCard(
                "v".repeat(FixtureCard.LONGEST_DESCRIPTOR + 1), "1111", 12, 2030, "credit"));

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("card.brand");
  }

  @ParameterizedTest(name = "refuses the type {0}")
  @ValueSource(strings = {"CARD", "credit-card", "pan", " "})
  void a_type_the_route_never_published_is_refused_by_naming_the_ones_it_did(String type) {
    DeclaredPaymentMethodFixture declared =
        new DeclaredPaymentMethodFixture(
            RUN.value(), "merchant-1", "customer-123", type, "an-alias", aVisa(), Map.of(), null);

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("card")
        .hasMessageContaining("wallet")
        .hasMessageContaining("psp-token");
  }

  @Test
  void a_wallet_carries_no_card_and_is_read_as_one_that_carries_none() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER,
            RUN,
            new DeclaredPaymentMethodFixture(
                RUN.value(),
                "merchant-1",
                "customer-123",
                "wallet",
                "a-wallet",
                null,
                Map.of(),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(fixture.type()).isEqualTo(PaymentMethodKind.WALLET);
    assertThat(fixture.card()).isNull();
  }

  @ParameterizedTest(name = "refuses a fixture whose {0} is missing")
  @ValueSource(strings = {"merchantId", "ownerId", "alias"})
  void a_fixture_missing_a_fact_the_core_owns_is_refused_by_name(String property) {
    DeclaredPaymentMethodFixture declared = aCardWithout(property);

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(property);
  }

  @Test
  void a_provider_native_id_sent_with_no_value_is_refused_rather_than_silently_dropped() {
    DeclaredPaymentMethodFixture declared =
        new DeclaredPaymentMethodFixture(
            RUN.value(),
            "merchant-1",
            "customer-123",
            "card",
            "visa-success",
            aVisa(),
            Map.of(ProviderPaymentMethodFields.TOKEN, "  "),
            null);

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("providerIds.token");
  }

  @Test
  void a_provider_native_id_longer_than_the_index_that_claims_it_is_refused_where_it_is_typed() {
    DeclaredPaymentMethodFixture declared =
        new DeclaredPaymentMethodFixture(
            RUN.value(),
            "merchant-1",
            "customer-123",
            "card",
            "visa-success",
            aVisa(),
            Map.of(
                ProviderPaymentMethodFields.TOKEN,
                "t".repeat(PaymentMethodFixtureGrammar.LONGEST_PROVIDER_NATIVE_ID + 1)),
            null);

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("providerIds.token");
  }

  @Test
  void a_provider_native_id_exactly_as_long_as_the_index_holds_is_taken() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER,
            RUN,
            new DeclaredPaymentMethodFixture(
                RUN.value(),
                "merchant-1",
                "customer-123",
                "card",
                "visa-success",
                aVisa(),
                Map.of(
                    ProviderPaymentMethodFields.TOKEN,
                    "t".repeat(PaymentMethodFixtureGrammar.LONGEST_PROVIDER_NATIVE_ID)),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(fixture.providerIds().get(ProviderPaymentMethodFields.TOKEN))
        .hasSize(PaymentMethodFixtureGrammar.LONGEST_PROVIDER_NATIVE_ID);
  }

  @Test
  void an_identity_longer_than_the_store_holds_is_refused_where_it_is_typed() {
    DeclaredPaymentMethodFixture declared =
        new DeclaredPaymentMethodFixture(
            RUN.value(),
            "m".repeat(PaymentMethodFixtureGrammar.LONGEST_IDENTITY + 1),
            "customer-123",
            "card",
            "visa-success",
            aVisa(),
            Map.of(),
            null);

    assertThatThrownBy(
            () ->
                PaymentMethodFixtureGrammar.fixtureOf(
                    PROVIDER, RUN, declared, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("merchantId");
  }

  @Test
  void the_names_a_tester_typed_are_stripped_so_a_trailing_space_never_makes_a_second_fixture() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER,
            RUN,
            new DeclaredPaymentMethodFixture(
                RUN.value(),
                " merchant-1 ",
                " customer-123 ",
                " card ",
                " visa-success ",
                aVisa(),
                Map.of(ProviderPaymentMethodFields.TOKEN, " tok_sim_123 "),
                " scn_1 "),
            ObjectOrigin.CONTROL_API);

    assertThat(fixture.merchantId()).isEqualTo("merchant-1");
    assertThat(fixture.ownerId()).isEqualTo("customer-123");
    assertThat(fixture.fixtureAlias()).isEqualTo("visa-success");
    assertThat(fixture.defaultScenario()).isEqualTo("scn_1");
    assertThat(fixture.providerIds())
        .containsExactly(Map.entry(ProviderPaymentMethodFields.TOKEN, "tok_sim_123"));
  }

  @Test
  void a_fixture_naming_no_scenario_carries_none_rather_than_an_empty_name() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER,
            RUN,
            new DeclaredPaymentMethodFixture(
                RUN.value(),
                "merchant-1",
                "customer-123",
                "card",
                "visa-success",
                aVisa(),
                null,
                "   "),
            ObjectOrigin.CONTROL_API);

    assertThat(fixture.defaultScenario()).isNull();
    assertThat(fixture.providerIds()).isEmpty();
  }

  private static SequencedMap<String, String> theIdsOfThatWidth() {
    LinkedHashMap<String, String> declared = new LinkedHashMap<>();
    for (int at = THE_IDS_A_FIXTURE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES; at >= 1; at--) {
      declared.put("providerId" + at, "the id the seed declared " + at + "th");
    }
    return declared;
  }

  @Test
  void
      a_seed_carries_its_provider_native_ids_in_the_order_the_declaration_named_them_and_twelve_ids_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    SequencedMap<String, String> declared = theIdsOfThatWidth();

    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER, RUN, aCardCarrying(declared), ObjectOrigin.CONTROL_API);

    assertThat(List.copyOf(fixture.providerIds().keySet()))
        .as(A_SEEDED_ID_IS_READ_BACK_ON_A_SCREEN_AND_IN_A_BUNDLE_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(declared.keySet()));
  }

  @Test
  void
      a_seed_hands_the_store_its_provider_native_ids_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    NewPaymentMethodFixture fixture =
        PaymentMethodFixtureGrammar.fixtureOf(
            PROVIDER, RUN, aCardCarrying(theIdsOfThatWidth()), ObjectOrigin.CONTROL_API);

    assertThat(fixture.providerIds())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  private static DeclaredPaymentMethodFixture aCardCarrying(Map<String, String> providerIds) {
    return new DeclaredPaymentMethodFixture(
        RUN.value(),
        "merchant-1",
        "customer-123",
        "card",
        "visa-success",
        aVisa(),
        providerIds,
        null);
  }

  private static DeclaredPaymentMethodFixture aCard(DeclaredCard card) {
    return new DeclaredPaymentMethodFixture(
        RUN.value(), "merchant-1", "customer-123", "card", "visa-success", card, Map.of(), null);
  }

  private static DeclaredPaymentMethodFixture aCardWithout(String property) {
    return new DeclaredPaymentMethodFixture(
        RUN.value(),
        left("merchantId", property, "merchant-1"),
        left("ownerId", property, "customer-123"),
        "card",
        left("alias", property, "visa-success"),
        aVisa(),
        Map.of(),
        null);
  }

  private static @Nullable String left(String named, String missing, String value) {
    return named.equals(missing) ? null : value;
  }

  private static DeclaredCard aVisa() {
    return new DeclaredCard("visa", "1111", 12, 2030, "credit");
  }
}
