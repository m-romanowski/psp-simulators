package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TranzilaCardTest {

  @ParameterizedTest
  @CsvSource({
    "4580210000004245, 2, Visa",
    "5326100000001234, 1, Mastercard",
    "2221000000000009, 1, Mastercard",
    "2720990000000007, 1, Mastercard",
    "340000000000009, 4, Amex",
    "370000000000002, 4, Amex",
    "36000000000008, 3, Diners",
    "38000000000006, 3, Diners",
    "30000000000004, 3, Diners",
    "5000000000000003, 6, Maestro",
    "5600000000000003, 6, Maestro",
    "6759000000000000, 6, Maestro",
    "12345678, 5, Isracard",
    "5100000000000008, 1, Mastercard",
    "5500000000000004, 1, Mastercard",
    "2800000000000000, 5, Isracard",
    "3300000000000000, 5, Isracard"
  })
  void a_card_number_answers_the_card_type_of_the_payment_pages_table(
      String pan, int cardType, String name) {
    TranzilaCard card = TranzilaCard.ofThePan(pan, 7, 2030);

    assertThat(card.cardType()).isEqualTo(cardType);
    assertThat(card.cardTypeName()).isEqualTo(name);
  }

  @Test
  void a_card_is_masked_to_its_first_six_and_last_four_digits() {
    assertThat(TranzilaCard.maskOf("4580210000004245")).isEqualTo("458021xxxxxx4245");
    assertThat(TranzilaCard.maskOf("12345678901")).isEqualTo("123456x8901");
    assertThat(TranzilaCard.maskOf("1234567890")).isEqualTo("xxxxxx7890");
    assertThat(TranzilaCard.maskOf("12345678")).isEqualTo("xxxx5678");
  }

  @Test
  void a_card_keeps_a_two_digit_expiry_year_and_its_last_four() {
    TranzilaCard card = TranzilaCard.ofThePan("4580210000004245", 7, 2030);

    assertThat(card).isEqualTo(new TranzilaCard("458021xxxxxx4245", 2, 7, 30));
    assertThat(card.last4()).isEqualTo("4245");
    assertThat(card.theBin()).contains("458021");
    assertThat(card.expiringIn(1, 2031)).isEqualTo(new TranzilaCard("458021xxxxxx4245", 2, 1, 31));
  }

  @Test
  void the_same_card_has_the_same_last_four_expiry_type_and_bin_where_both_show_one() {
    TranzilaCard card = new TranzilaCard("458021xxxxxx4245", 2, 7, 30);

    assertThat(card.isTheSameCardAs(new TranzilaCard("458021xxxxxx4245", 2, 7, 30))).isTrue();
    assertThat(card.isTheSameCardAs(new TranzilaCard("xxxxxxxxxxxx4245", 2, 7, 30)))
        .as("a seeded token shows no bin, so the bin is not compared")
        .isTrue();
    assertThat(new TranzilaCard("xxxxxxxxxxxx4245", 2, 7, 30).isTheSameCardAs(card)).isTrue();
    assertThat(card.isTheSameCardAs(new TranzilaCard("458022xxxxxx4245", 2, 7, 30))).isFalse();
    assertThat(card.isTheSameCardAs(new TranzilaCard("458021xxxxxx4246", 2, 7, 30))).isFalse();
    assertThat(card.isTheSameCardAs(new TranzilaCard("458021xxxxxx4245", 1, 7, 30))).isFalse();
    assertThat(card.isTheSameCardAs(new TranzilaCard("458021xxxxxx4245", 2, 8, 30))).isFalse();
    assertThat(card.isTheSameCardAs(new TranzilaCard("458021xxxxxx4245", 2, 7, 31))).isFalse();
  }

  @Test
  void a_mask_shows_a_bin_only_when_its_first_six_are_digits() {
    assertThat(new TranzilaCard("xxxxxxxxxxxx4245", 2, 7, 30).theBin()).isEmpty();
    assertThat(new TranzilaCard("12345x6789", 2, 7, 30).theBin()).isEmpty();
    assertThat(new TranzilaCard("xxxx5678", 2, 7, 30).theBin()).isEmpty();
    assertThat(new TranzilaCard("123456x8901", 2, 7, 30).theBin()).contains("123456");
  }

  @Test
  void a_brand_names_one_of_the_six_card_types_in_any_case() {
    assertThat(TranzilaCard.cardTypeNamed("MasterCard")).contains(1);
    assertThat(TranzilaCard.cardTypeNamed("visa")).contains(2);
    assertThat(TranzilaCard.cardTypeNamed("diners")).contains(3);
    assertThat(TranzilaCard.cardTypeNamed("AMEX")).contains(4);
    assertThat(TranzilaCard.cardTypeNamed("isracard")).contains(5);
    assertThat(TranzilaCard.cardTypeNamed("maestro")).contains(6);
    assertThat(TranzilaCard.cardTypeNamed("discover")).isEmpty();
  }

  @Test
  void a_seeded_card_is_masked_to_its_last_four_and_typed_by_its_brand() {
    TranzilaCard card =
        TranzilaPayerCards.theCardOf(new FixtureCard("diners", "0004", 3, 2032, "credit"));

    assertThat(card.cardMask()).isEqualTo("xxxxxxxxxxxx0004");
    assertThat(card.cardType()).isEqualTo(3);
    assertThat(card.expiryMonth()).isEqualTo(3);
    assertThat(card.expiryYear()).isEqualTo(32);
    assertThat(
            TranzilaPayerCards.theCardOf(new FixtureCard("other", "0004", 3, 2032, "credit"))
                .cardType())
        .isEqualTo(2);
  }
}
