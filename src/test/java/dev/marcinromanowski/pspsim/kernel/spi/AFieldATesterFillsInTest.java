package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AFieldATesterFillsInTest {

  private static final String WHAT_IT_IS_FOR = "what this provider puts on the payer's card.";

  private static final List<AValueAFieldOffers> TWO_CODES =
      List.of(
          new AValueAFieldOffers("2", "hold the money"),
          new AValueAFieldOffers("3", "save the card"));

  @Test
  void a_field_that_names_no_key_reaches_nothing_the_provider_reads_and_is_refused() {
    assertThatThrownBy(() -> AFieldATesterFillsIn.typed("  ", "amount", true, WHAT_IT_IS_FOR, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names nothing the provider reads");
  }

  @Test
  void a_field_a_wizard_could_only_print_by_its_key_is_refused() {
    assertThatThrownBy(() -> AFieldATesterFillsIn.typed("sum", "  ", true, WHAT_IT_IS_FOR, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("prints the label and never the key");
  }

  @Test
  void a_field_that_never_says_what_the_provider_does_with_it_is_refused() {
    assertThatThrownBy(() -> AFieldATesterFillsIn.typed("sum", "amount", true, "   ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "the Look a payment up screen both lead a tester who has read no provider contract");
  }

  @Test
  void a_field_that_offers_one_value_under_two_labels_is_refused() {
    List<AValueAFieldOffers> oneValueTwice =
        List.of(
            new AValueAFieldOffers("2", "hold the money"),
            new AValueAFieldOffers("2", "hold it differently"));

    assertThatThrownBy(
            () ->
                AFieldATesterFillsIn.chosenFrom(
                    "chargeType", "which kind", true, WHAT_IT_IS_FOR, oneValueTwice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("offers the value 2 twice");
  }

  @Test
  void a_field_that_says_it_offers_the_providers_own_values_and_offers_none_is_refused() {
    assertThatThrownBy(
            () ->
                AFieldATesterFillsIn.chosenFrom(
                    "kind", "which kind", true, WHAT_IT_IS_FOR, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("offer a tester an empty list to pick from");
  }

  @Test
  void a_field_whose_values_come_from_the_run_and_which_lists_its_own_as_well_is_refused() {
    assertThatThrownBy(
            () ->
                new AFieldATesterFillsIn(
                    "notifyUrl",
                    "where the callback goes",
                    false,
                    WHAT_IT_IS_FOR,
                    WhereAFieldsValuesComeFrom.THE_CALLBACK_TARGETS_THIS_RUN_CONFIGURES,
                    TWO_CODES,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("would not know which list to offer");
  }

  @Test
  void a_typed_field_that_lists_values_as_well_is_refused_for_the_same_reason() {
    assertThatThrownBy(
            () ->
                new AFieldATesterFillsIn(
                    "sum",
                    "amount",
                    true,
                    WHAT_IT_IS_FOR,
                    WhereAFieldsValuesComeFrom.WHATEVER_THE_TESTER_TYPES,
                    TWO_CODES,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("would not know which list to offer");
  }

  @Test
  void a_field_that_suggests_a_value_it_would_refuse_is_refused_before_a_tester_sees_it() {
    assertThatThrownBy(
            () ->
                new AFieldATesterFillsIn(
                    "chargeType",
                    "which kind",
                    true,
                    WHAT_IT_IS_FOR,
                    WhereAFieldsValuesComeFrom.THE_VALUES_THE_PROVIDER_DECLARES,
                    TWO_CODES,
                    "9"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("suggests 9, which is not among the values it accepts");
  }

  @Test
  void a_field_that_suggests_one_of_its_own_values_is_taken_as_declared() {
    AFieldATesterFillsIn declared =
        new AFieldATesterFillsIn(
            "chargeType",
            "which kind",
            true,
            WHAT_IT_IS_FOR,
            WhereAFieldsValuesComeFrom.THE_VALUES_THE_PROVIDER_DECLARES,
            TWO_CODES,
            "3");

    assertThat(declared.example()).isEqualTo("3");
    assertThat(declared.choices()).hasSize(2);
  }

  @Test
  void a_typed_field_suggests_an_answer_without_that_answer_being_a_value_it_accepts() {
    AFieldATesterFillsIn declared =
        AFieldATesterFillsIn.typed("sum", "amount", true, WHAT_IT_IS_FOR, "10.00");

    assertThat(declared.example()).isEqualTo("10.00");
    assertThat(declared.choices()).isEmpty();
    assertThat(declared.namesTheCallbackTarget()).isFalse();
  }

  @Test
  void a_field_that_names_the_callback_target_takes_its_values_from_the_run_and_requires_none() {
    AFieldATesterFillsIn declared =
        AFieldATesterFillsIn.theCallbackTarget(
            "notifyUrl", "where the callback goes", WHAT_IT_IS_FOR);

    assertThat(declared.namesTheCallbackTarget()).isTrue();
    assertThat(declared.required()).isFalse();
    assertThat(declared.choices()).isEmpty();
    assertThat(declared.example()).isNull();
  }

  @Test
  void this_simulator_consumes_the_callback_target_alone_and_the_provider_consumes_every_other() {
    assertThat(
            AFieldATesterFillsIn.theCallbackTarget(
                    "notifyUrl", "where the callback goes", WHAT_IT_IS_FOR)
                .thisSimulatorIsTheOneThatConsumesTheValue())
        .as(
            "this simulator is the party that posts a callback, so it is the party that reads"
                + " this address and the party that refuses one this run does not configure")
        .isTrue();
    assertThat(
            AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
                    "cardToken", "the saved card", true, WHAT_IT_IS_FOR)
                .thisSimulatorIsTheOneThatConsumesTheValue())
        .as(
            "a value this run produced is still forwarded to the provider, which resolves it and"
                + " refuses one it never issued, so this simulator consumes none of it and judges"
                + " none of it")
        .isFalse();
    assertThat(
            AFieldATesterFillsIn.typed("sum", "amount", true, WHAT_IT_IS_FOR, "10.00")
                .thisSimulatorIsTheOneThatConsumesTheValue())
        .isFalse();
    assertThat(
            AFieldATesterFillsIn.chosenFrom(
                    "chargeType", "which kind", true, WHAT_IT_IS_FOR, TWO_CODES)
                .thisSimulatorIsTheOneThatConsumesTheValue())
        .isFalse();
  }

  @Test
  void the_field_naming_the_callback_target_is_the_one_this_simulator_consumes_the_value_of() {
    assertThat(
            AFieldATesterFillsIn.theCallbackTarget(
                    "notifyUrl", "where the callback goes", WHAT_IT_IS_FOR)
                .namesTheCallbackTarget())
        .as(
            "the two questions are one question asked twice, because the callback target is the"
                + " only value of a form this simulator reads instead of the provider")
        .isTrue();
    assertThat(
            AFieldATesterFillsIn.typed("sum", "amount", true, WHAT_IT_IS_FOR, "10.00")
                .namesTheCallbackTarget())
        .isFalse();
  }

  @Test
  void a_field_whose_values_the_run_produced_is_built_by_the_factory_that_names_that_source() {
    AFieldATesterFillsIn declared =
        AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
            "cardToken", "the saved card to charge", true, WHAT_IT_IS_FOR);

    assertThat(declared)
        .as(
            "the field a wizard draws a list of this run's own values from, read from the factory"
                + " itself rather than from a constant a provider builds at class load, because a"
                + " field only ever constructed in a static initializer is a field no test can be"
                + " shown to have built")
        .isNotNull();
    assertThat(declared.namesAValueThisTestRunHasAlreadyProduced()).isTrue();
    assertThat(declared.namesTheCallbackTarget()).isFalse();
    assertThat(declared.key()).isEqualTo("cardToken");
    assertThat(declared.label()).isEqualTo("the saved card to charge");
    assertThat(declared.required()).isTrue();
    assertThat(declared.whatItIsFor()).isEqualTo(WHAT_IT_IS_FOR);
    assertThat(declared.choices()).isEmpty();
    assertThat(declared.example()).isNull();
  }

  @Test
  void a_field_offering_values_offers_them_and_holds_no_way_of_judging_another_one() {
    AFieldATesterFillsIn declared =
        AFieldATesterFillsIn.chosenFrom(
            "chargeType", "which kind", true, WHAT_IT_IS_FOR, TWO_CODES);

    assertThat(declared.choices())
        .as(
            "a declared list is what this provider publishes, which a wizard offers as"
                + " suggestions beside a box a tester may type anything into; asking this field"
                + " whether it publishes a value is what made the core a second validator, so"
                + " nothing here answers that question any more")
        .isEqualTo(TWO_CODES);
  }

  @Test
  void the_values_a_field_accepts_are_printed_by_their_label_and_their_wire_value_together() {
    AFieldATesterFillsIn declared =
        AFieldATesterFillsIn.chosenFrom(
            "chargeType", "which kind", true, WHAT_IT_IS_FOR, TWO_CODES);

    assertThat(declared.theValuesItAccepts()).isEqualTo("hold the money (2), save the card (3)");
  }

  @Test
  void a_typed_field_accepts_anything_and_says_so_by_publishing_no_values_at_all() {
    AFieldATesterFillsIn declared =
        AFieldATesterFillsIn.typed("sum", "amount", true, WHAT_IT_IS_FOR, null);

    assertThat(declared.theValuesItAccepts()).isEmpty();
    assertThat(declared.choices()).isEmpty();
  }

  @Test
  void a_choice_that_sends_nothing_to_the_provider_is_refused() {
    assertThatThrownBy(() -> new AValueAFieldOffers("  ", "hold the money"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a tester who picked it would have started a payment");
  }

  @Test
  void a_choice_a_tester_would_have_to_read_a_provider_contract_to_understand_is_refused() {
    assertThatThrownBy(() -> new AValueAFieldOffers("2", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a wizard offers a tester the label and never the value");
  }
}
