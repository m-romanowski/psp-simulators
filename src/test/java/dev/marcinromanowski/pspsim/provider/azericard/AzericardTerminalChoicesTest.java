package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AzericardTerminalChoicesTest {

  @Test
  void what_a_partial_completion_leaves_is_offered_as_what_is_left_to_capture() {
    assertThat(AzericardPartialCompletion.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "CLOSES_THE_AUTHORIZATION",
                "completing part of the amount closes the authorization, so nothing is left to"
                    + " capture"),
            new AcceptedValue(
                "LEAVES_THE_REMAINDER_CAPTURABLE",
                "completing part of the amount leaves the rest capturable by a second"
                    + " completion"));
  }

  @Test
  void the_three_callback_signatures_say_what_each_one_gives_a_merchant_check_to_do() {
    assertThat(valuesOf(AzericardCallbackSignature.SETTING))
        .containsExactly("VALID", "CORRUPTED", "ABSENT");
    assertThat(sentencesOf(AzericardCallbackSignature.SETTING))
        .allSatisfy(said -> assertThat(said).contains("P_SIGN"));
  }

  @Test
  void the_values_offered_for_each_field_are_every_value_that_field_has() {
    assertThat(valuesOf(AzericardPartialCompletion.SETTING))
        .containsExactlyElementsOf(namesOf(AzericardPartialCompletion.values()));
    assertThat(valuesOf(AzericardCallbackSignature.SETTING))
        .containsExactlyElementsOf(namesOf(AzericardCallbackSignature.values()));
  }

  @Test
  void every_value_a_field_offers_is_one_this_provider_takes() {
    takes(AzericardPartialCompletion.SETTING, AzericardPartialCompletion::of);
    takes(AzericardCallbackSignature.SETTING, AzericardCallbackSignature::of);
  }

  @ParameterizedTest
  @CsvSource({
    AzericardTerminal.TERMINAL_PROPERTY,
    AzericardTerminal.MERCHANT_KEY_PROPERTY,
    AzericardTerminal.GATEWAY_KEY_PROPERTY,
    AzericardDuplicateCode.SETTING
  })
  void a_field_whose_values_are_not_a_closed_set_offers_none_and_stays_a_box(String key) {
    assertThat(field(key).acceptedValues())
        .as(
            "a terminal, a key pair, and a response code azericard publishes as two characters"
                + " rather than as a list: none of them is a set this provider could write out")
        .isEmpty();
  }

  @Test
  void the_code_this_provider_sizes_rather_than_lists_shows_an_example_of_that_size() {
    ConfigurableField code = field(AzericardDuplicateCode.SETTING);

    assertThat(code.acceptedValues()).isEmpty();
    assertThat(code.example()).isEqualTo(AzericardDuplicateCode.DEFAULT);
    assertThat(code.whatItIsFor()).contains("one or two characters");
  }

  @Test
  void the_terminal_box_shows_a_terminal_this_provider_takes_rather_than_the_name_of_the_field() {
    ConfigurableField terminal = field(AzericardTerminal.TERMINAL_PROPERTY);

    assertThat(terminal.example())
        .isEqualTo("77777777")
        .isNotEqualTo(AzericardTerminal.TERMINAL_PROPERTY);
    assertThat(
            AzericardDoubles.terminal(
                    TestRunId.of("the-hint-is-a-terminal-this-provider-takes"),
                    "merchant-1",
                    String.valueOf(terminal.example()))
                .terminalId())
        .as("the terminal the box hints is one this provider configures an account on")
        .isEqualTo(terminal.example());
  }

  @Test
  void a_key_box_hints_the_line_its_block_begins_with_and_that_line_alone_is_no_key_at_all() {
    ConfigurableField merchantKey = field(AzericardTerminal.MERCHANT_KEY_PROPERTY);
    ConfigurableField gatewayKey = field(AzericardTerminal.GATEWAY_KEY_PROPERTY);

    assertThat(merchantKey.holdsABlockOfManyLines()).isTrue();
    assertThat(gatewayKey.holdsABlockOfManyLines()).isTrue();
    assertThat(AzericardKeyFixture.MERCHANT_PUBLIC.strip())
        .as(
            "a key is 2048 bits of base64, which no box could hint, so the box hints the line"
                + " the block begins with and that line opens the very keys this provider reads")
        .startsWith(String.valueOf(merchantKey.example()));
    assertThat(AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8.strip())
        .startsWith(String.valueOf(gatewayKey.example()));
    assertThatNoException()
        .isThrownBy(
            () ->
                AzericardKeys.publicKeyOf(
                    AzericardTerminal.MERCHANT_KEY_PROPERTY, AzericardKeyFixture.MERCHANT_PUBLIC));
    assertThatThrownBy(
            () ->
                AzericardKeys.publicKeyOf(
                    AzericardTerminal.MERCHANT_KEY_PROPERTY, merchantKey.example()))
        .as(
            "the hint is a shape and never a value: a tester who pasted the hint alone is"
                + " refused, and the sentence beside the box is where the rest of the block and"
                + " the command that writes one are said")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must hold");
    assertThat(merchantKey.whatItIsFor())
        .contains(AzericardKeys.PUBLIC_KEY_SHAPE)
        .contains("openssl genrsa");
    assertThat(gatewayKey.whatItIsFor())
        .contains(AzericardKeys.PRIVATE_KEY_SHAPE)
        .contains("openssl genrsa");
  }

  @Test
  void the_box_whose_value_has_a_grammar_shows_that_grammar_rather_than_the_name_of_the_field() {
    ConfigurableField sources = field(AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY);

    assertThat(sources.example())
        .as(
            "a tester reading this box is shown whatever it offers as an example, so offering the"
                + " developer name of the field teaches them nothing about the commas it reads")
        .isNotNull()
        .isNotEqualTo(AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY);

    AzericardTerminal lender =
        AzericardDoubles.terminal(
            TestRunId.of("an-example-is-a-value-this-provider-takes"),
            "borrower",
            "70000012",
            Map.of(
                AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY,
                String.valueOf(sources.example())));

    assertThat(List.of("70000013", "70000014"))
        .as("the example names two terminals, and this terminal reads a card from each of them")
        .allSatisfy(named -> assertThat(lender.mayUseStoredCardFrom(named)).isTrue());
    assertThat(lender.mayUseStoredCardFrom("70000015"))
        .as("and names nobody it does not spell out")
        .isFalse();
  }

  @Test
  void the_two_settlement_mac_orders_are_declared_with_the_fields_each_one_signs() {
    assertThat(AzericardSettlementMac.acceptedValues())
        .as(
            "a tester picks between these two on the account screen, and the only thing that"
                + " tells them which to pick is the field order written out beside each name")
        .containsExactly(
            new AcceptedValue(
                "LIVE_WIRE",
                "sign as the gateway a real merchant reaches does: AMOUNT, CURRENCY, ORDER, RRN,"
                    + " INT_REF, TERMINAL, TRTYPE, TIMESTAMP, NONCE"),
            new AcceptedValue(
                "PUBLISHED_EXAMPLE",
                "sign as the manual publishes and its worked example computes: AMOUNT, CURRENCY,"
                    + " TERMINAL, TRTYPE, ORDER, RRN, INT_REF, with TIMESTAMP and NONCE left"
                    + " out"));
  }

  @Test
  void the_three_callback_signatures_are_declared_with_the_check_each_one_leaves_a_merchant() {
    assertThat(AzericardCallbackSignature.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "VALID",
                "the callback carries the P_SIGN this gateway signs it with, so your own check"
                    + " passes"),
            new AcceptedValue(
                "CORRUPTED",
                "the callback carries a P_SIGN that cannot verify, so your check has something to"
                    + " refuse"),
            new AcceptedValue(
                "ABSENT",
                "the callback carries no P_SIGN at all, so your check has nothing to verify"));
  }

  private static void takes(String key, BiFunction<String, String, Object> parser) {
    for (String offered : valuesOf(key)) {
      assertThatNoException()
          .as(key + " offers " + offered)
          .isThrownBy(() -> parser.apply(key, offered));
    }
  }

  private static List<String> namesOf(Enum<?>[] values) {
    List<String> names = new ArrayList<>();
    for (Enum<?> value : values) {
      names.add(value.name());
    }
    return names;
  }

  private static List<String> valuesOf(String key) {
    List<String> values = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      values.add(accepted.value());
    }
    return values;
  }

  private static List<String> sentencesOf(String key) {
    List<String> said = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      said.add(accepted.whatItMeans());
    }
    return said;
  }

  private static ConfigurableField field(String key) {
    return AzericardTerminal.ACCOUNT_FIELDS.field(key).orElseThrow();
  }
}
