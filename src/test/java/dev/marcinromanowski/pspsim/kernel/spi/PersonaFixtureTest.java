package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class PersonaFixtureTest {

  private static final FixtureCard A_CARD = new FixtureCard("VISA", "1111", 12, 2030, "credit");

  private static final int THE_IDS_AN_INSTRUMENT_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_MINTS = 12;

  private static final String
      A_PERSONA_SEEDS_THE_INSTRUMENTS_A_RUN_EXPORTS_AND_TWELVE_IDS_LEAVE_479001600_ORDERS =
          "the ids a persona instrument mints are seeded as the provider-native ids of a fixture,"
              + " which the Payment methods screen lists and a run bundle carries in the order"
              + " the fixture holds them, so an instrument mints its ids in the order it declares"
              + " the keys it carries rather than in one each JVM draws anew. Twelve ids leave"
              + " 479001600 orders an unordered copy could draw from the keys' hashes and the"
              + " salt java.util.ImmutableCollections draws once per JVM, so this assertion"
              + " cannot pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the ids"
              + " are in such a map there is no way left to tell it from an ordered one, so an"
              + " instrument publishes and mints maps whose type carries an order on every run"
              + " rather than ones that happened to print in the right order on this one";

  @Test
  void
      an_instrument_mints_and_publishes_its_ids_in_the_order_it_declares_the_keys_it_carries_and_twelve_ids_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    List<String> theKeysItCarries = theKeysOfThatWidth();
    Map<String, String> whatTheMintingProduces = Map.copyOf(theIdsOf(theKeysItCarries));
    PersonaFixture instrument =
        new PersonaFixture(
            "an-alias",
            "an-owner",
            PaymentMethodKind.CARD,
            A_CARD,
            theKeysItCarries,
            "a minting of its own",
            () -> whatTheMintingProduces,
            null,
            whatTheMintingProduces);

    assertThat(List.copyOf(instrument.mint().keySet()))
        .as(A_PERSONA_SEEDS_THE_INSTRUMENTS_A_RUN_EXPORTS_AND_TWELVE_IDS_LEAVE_479001600_ORDERS)
        .isEqualTo(theKeysItCarries);
    assertThat(List.copyOf(instrument.theIdsItPublishes().keySet()))
        .as(A_PERSONA_SEEDS_THE_INSTRUMENTS_A_RUN_EXPORTS_AND_TWELVE_IDS_LEAVE_479001600_ORDERS)
        .isEqualTo(theKeysItCarries);
  }

  @Test
  void
      an_instrument_mints_and_publishes_its_ids_in_maps_that_carry_an_order_rather_than_ones_drawn_from_a_per_jvm_salt() {
    List<String> theKeysItCarries = theKeysOfThatWidth();
    Map<String, String> whatTheMintingProduces = Map.copyOf(theIdsOf(theKeysItCarries));
    PersonaFixture instrument =
        new PersonaFixture(
            "an-alias",
            "an-owner",
            PaymentMethodKind.CARD,
            A_CARD,
            theKeysItCarries,
            "a minting of its own",
            () -> whatTheMintingProduces,
            null,
            whatTheMintingProduces);

    assertThat(instrument.mint())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
    assertThat(instrument.theIdsItPublishes())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  private static List<String> theKeysOfThatWidth() {
    List<String> declared = new ArrayList<>();
    for (int at = THE_IDS_AN_INSTRUMENT_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_MINTS;
        at >= 1;
        at--) {
      declared.add("providerId" + at);
    }
    return List.copyOf(declared);
  }

  private static SequencedMap<String, String> theIdsOf(List<String> keys) {
    LinkedHashMap<String, String> minted = new LinkedHashMap<>();
    for (String key : keys) {
      minted.put(key, "the id minted under " + key);
    }
    return minted;
  }

  @Test
  void an_instrument_of_a_card_type_that_describes_no_card_is_refused() {
    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    "an-alias",
                    "an-owner",
                    PaymentMethodKind.CARD,
                    null,
                    List.of(),
                    "",
                    Map::of,
                    null))
        .hasMessage(PersonaFixture.A_CARD_IS_DESCRIBED_BY_THE_TYPE_THAT_CARRIES_ONE);
  }

  @Test
  void an_instrument_of_a_wallet_type_that_describes_a_card_is_refused() {
    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    "an-alias",
                    "an-owner",
                    PaymentMethodKind.WALLET,
                    A_CARD,
                    List.of(),
                    "",
                    Map::of,
                    null))
        .hasMessage(PersonaFixture.A_CARD_IS_DESCRIBED_BY_THE_TYPE_THAT_CARRIES_ONE);
  }

  @Test
  void
      an_instrument_that_carries_a_provider_native_id_and_says_nothing_of_its_minting_is_refused() {
    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    "an-alias",
                    "an-owner",
                    PaymentMethodKind.CARD,
                    A_CARD,
                    List.of(ProviderPaymentMethodFields.TOKEN),
                    "   ",
                    () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok"),
                    null))
        .hasMessage(PersonaFixture.AN_INSTRUMENT_SAYS_WHICH_MINTING_PRODUCED_ITS_IDS);
  }

  @Test
  void an_instrument_that_carries_no_provider_native_id_need_say_nothing_of_minting() {
    PersonaFixture instrument =
        new PersonaFixture(
            "an-alias", "an-owner", PaymentMethodKind.CARD, A_CARD, List.of(), "", Map::of, null);

    assertThat(instrument.mint()).isEmpty();
  }

  @Test
  void an_instrument_that_publishes_one_key_twice_is_refused() {
    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    "an-alias",
                    "an-owner",
                    PaymentMethodKind.CARD,
                    A_CARD,
                    List.of(ProviderPaymentMethodFields.TOKEN, ProviderPaymentMethodFields.TOKEN),
                    "one minting",
                    () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok"),
                    null))
        .hasMessage(PersonaFixture.AN_INSTRUMENT_THAT_CARRIES_NO_ID_MINTS_NONE);
  }

  @Test
  void an_instrument_that_publishes_a_value_under_a_key_it_does_not_carry_is_refused() {
    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    "an-alias",
                    "an-owner",
                    PaymentMethodKind.CARD,
                    A_CARD,
                    List.of(ProviderPaymentMethodFields.TOKEN),
                    "one minting",
                    () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok"),
                    null,
                    Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "pm_published")))
        .hasMessage(PersonaFixture.A_PUBLISHED_VALUE_BELONGS_TO_A_KEY_IT_CARRIES);
  }

  @Test
  void an_instrument_that_publishes_a_value_under_a_key_it_carries_keeps_the_declaration() {
    PersonaFixture instrument =
        new PersonaFixture(
            "an-alias",
            "an-owner",
            PaymentMethodKind.CARD,
            A_CARD,
            List.of(ProviderPaymentMethodFields.TOKEN),
            "one minting",
            () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok"),
            null,
            Map.of(ProviderPaymentMethodFields.TOKEN, "tok"));

    assertThat(instrument.theIdsItPublishes())
        .containsExactly(Map.entry(ProviderPaymentMethodFields.TOKEN, "tok"));
    assertThat(instrument.mint())
        .containsExactly(Map.entry(ProviderPaymentMethodFields.TOKEN, "tok"));
  }

  @Test
  void an_instrument_whose_minting_disagrees_with_its_published_value_is_refused_when_it_mints() {
    PersonaFixture instrument =
        new PersonaFixture(
            "an-alias",
            "an-owner",
            PaymentMethodKind.CARD,
            A_CARD,
            List.of(ProviderPaymentMethodFields.TOKEN),
            "one minting",
            () -> Map.of(ProviderPaymentMethodFields.TOKEN, "another-token"),
            null,
            Map.of(ProviderPaymentMethodFields.TOKEN, "the-published-token"));

    assertThatThrownBy(instrument::mint)
        .hasMessageContaining(PersonaFixture.A_PUBLISHED_VALUE_IS_THE_VALUE_THE_MINTING_PRODUCES)
        .hasMessageContaining("the-published-token")
        .hasMessageContaining("another-token");
  }

  @Test
  void an_instrument_declared_without_a_published_value_publishes_none() {
    PersonaFixture instrument =
        new PersonaFixture(
            "an-alias",
            "an-owner",
            PaymentMethodKind.CARD,
            A_CARD,
            List.of(ProviderPaymentMethodFields.TOKEN),
            "one minting",
            () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok"),
            null);

    assertThat(instrument.theIdsItPublishes()).isEmpty();
  }

  @Test
  void an_instrument_that_names_a_rule_before_the_first_one_is_refused() {
    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    "an-alias",
                    "an-owner",
                    PaymentMethodKind.CARD,
                    A_CARD,
                    List.of(),
                    "",
                    Map::of,
                    -1))
        .hasMessageContaining("counts from zero");
  }

  @Test
  void an_instrument_with_a_blank_alias_or_owner_is_refused() {
    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    " ", "an-owner", PaymentMethodKind.CARD, A_CARD, List.of(), "", Map::of, null))
        .hasMessageContaining("alias of a persona instrument");
    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    "an-alias", " ", PaymentMethodKind.CARD, A_CARD, List.of(), "", Map::of, null))
        .hasMessageContaining("ownerId of a persona instrument");
  }

  @Test
  void an_alias_of_the_widest_length_a_run_files_is_accepted() {
    String widest = "a".repeat(128);

    PersonaFixture instrument =
        new PersonaFixture(
            widest, "an-owner", PaymentMethodKind.CARD, A_CARD, List.of(), "", Map::of, null);

    assertThat(instrument.alias()).hasSize(128);
  }

  @Test
  void an_alias_longer_than_a_run_can_file_is_refused() {
    String tooLong = "a".repeat(129);

    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    tooLong,
                    "an-owner",
                    PaymentMethodKind.CARD,
                    A_CARD,
                    List.of(),
                    "",
                    Map::of,
                    null))
        .hasMessageContaining("at most 128 characters");
  }

  @Test
  void minting_a_key_the_instrument_never_published_is_refused_to_the_module_that_declared_it() {
    PersonaFixture lying =
        new PersonaFixture(
            "an-alias",
            "an-owner",
            PaymentMethodKind.CARD,
            A_CARD,
            List.of(ProviderPaymentMethodFields.TOKEN),
            "a minting of its own",
            () -> Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, "cus_1"),
            null);

    assertThatThrownBy(lying::mint)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("described something other than what it created");
  }

  @Test
  void minting_every_key_it_published_hands_the_ids_over() {
    PersonaFixture honest =
        new PersonaFixture(
            "an-alias",
            "an-owner",
            PaymentMethodKind.CARD,
            A_CARD,
            List.of(ProviderPaymentMethodFields.TOKEN),
            "a minting of its own",
            () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok_1"),
            0);

    assertThat(honest.mint()).containsExactly(Map.entry("token", "tok_1"));
    assertThat(honest.triggersTheRuleAt()).isEqualTo(0);
  }

  @Test
  void an_alias_and_an_owner_are_stripped_of_the_spaces_a_declaration_carried() {
    PersonaFixture instrument =
        new PersonaFixture(
            " an-alias ",
            " an-owner ",
            PaymentMethodKind.CARD,
            A_CARD,
            List.of(),
            "",
            Map::of,
            null);

    assertThat(instrument.alias()).isEqualTo("an-alias");
    assertThat(instrument.ownerId()).isEqualTo("an-owner");
  }

  @Test
  void a_provider_native_key_declared_with_nothing_under_it_is_refused_rather_than_carried() {
    LinkedHashMap<String, String> nothingUnderTheKey = new LinkedHashMap<>();
    nothingUnderTheKey.put(ProviderPaymentMethodFields.TOKEN, null);

    assertThatThrownBy(
            () ->
                new PersonaFixture(
                    "an-alias",
                    "an-owner",
                    PaymentMethodKind.CARD,
                    A_CARD,
                    List.of(ProviderPaymentMethodFields.TOKEN),
                    "a minting of its own",
                    () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok_1"),
                    null,
                    nothingUnderTheKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            PersonaFixture.A_KEY_IS_CARRIED_WITH_A_VALUE_UNDER_IT_OR_NOT_AT_ALL
                + ProviderPaymentMethodFields.TOKEN);
  }

  @Test
  void a_minting_that_hands_back_a_key_with_nothing_under_it_is_refused_to_the_module() {
    LinkedHashMap<String, String> mintedWithNothingUnderIt = new LinkedHashMap<>();
    mintedWithNothingUnderIt.put(ProviderPaymentMethodFields.TOKEN, null);
    PersonaFixture minting =
        new PersonaFixture(
            "an-alias",
            "an-owner",
            PaymentMethodKind.CARD,
            A_CARD,
            List.of(ProviderPaymentMethodFields.TOKEN),
            "a minting of its own",
            () -> mintedWithNothingUnderIt,
            null);

    assertThatThrownBy(minting::mint)
        .as("a null id reached a stored row as an absence filed as an id before this refusal")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            PersonaFixture.A_KEY_IS_CARRIED_WITH_A_VALUE_UNDER_IT_OR_NOT_AT_ALL
                + ProviderPaymentMethodFields.TOKEN);
  }
}
