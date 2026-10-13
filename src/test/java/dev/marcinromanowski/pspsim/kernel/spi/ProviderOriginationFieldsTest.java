package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class ProviderOriginationFieldsTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final ThePayerStep THE_PAYER_STEP =
      new ThePayerStep.NoPageThisSimulatorServes("this provider publishes no page a payer opens.");

  private static final AFieldATesterFillsIn AMOUNT =
      AFieldATesterFillsIn.typed(
          "amount", "amount", true, "what this provider puts on the payer's card.", "10.00");

  @Test
  void a_provider_that_asks_a_tester_for_a_value_it_also_takes_from_the_account_is_refused() {
    List<DeclaredOrigination> asking =
        List.of(aWayAsking(AFieldATesterFillsIn.typed("userId", "user id", true, "a.", null)));
    SequencedMap<String, String> filledIn =
        new LinkedHashMap<>(Map.of("userId", "taken from the account this payment is started on"));

    assertThatThrownBy(() -> new ProviderOriginationFields(APROVIDER, asking, filledIn))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("put a credential of the account on a screen");
  }

  @Test
  void a_provider_that_fills_a_field_in_itself_without_saying_where_from_is_refused() {
    List<DeclaredOrigination> asking = List.of(aWayAsking(AMOUNT));
    SequencedMap<String, String> filledInWithNothingSaid =
        new LinkedHashMap<>(Map.of("userId", "  "));

    assertThatThrownBy(
            () -> new ProviderOriginationFields(APROVIDER, asking, filledInWithNothingSaid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say, in its own words, where the value comes from");
  }

  @Test
  void a_way_of_starting_a_payment_that_never_says_what_it_leaves_behind_is_refused() {
    List<AFieldATesterFillsIn> fields = List.of(AMOUNT);

    assertThatThrownBy(
            () -> new DeclaredOrigination("a sale", "Take the money", "  ", THE_PAYER_STEP, fields))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say what a tester is left holding once it has run");
  }

  @Test
  void a_provider_that_declares_no_way_of_starting_a_payment_is_refused() {
    assertThatThrownBy(
            () -> new ProviderOriginationFields(APROVIDER, List.of(), new LinkedHashMap<>()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declares no way of starting a payment");
  }

  @Test
  void a_provider_that_asks_for_nothing_the_account_holds_is_taken_as_declared() {
    ProviderOriginationFields declared =
        new ProviderOriginationFields(
            APROVIDER,
            List.of(aWayAsking(AMOUNT)),
            new LinkedHashMap<>(
                Map.of("userId", "taken from the account this payment is started on")));

    assertThat(declared.names()).containsExactly("a sale");
    assertThat(declared.wayNamed("a sale").orElseThrow().keys()).containsExactly("amount");
    assertThat(declared.wayNamed("a refund")).isEmpty();
  }

  private static DeclaredOrigination aWayAsking(AFieldATesterFillsIn field) {
    return new DeclaredOrigination(
        "a sale",
        "Take the money",
        "a payment nothing can capture later",
        THE_PAYER_STEP,
        List.of(field));
  }
}
