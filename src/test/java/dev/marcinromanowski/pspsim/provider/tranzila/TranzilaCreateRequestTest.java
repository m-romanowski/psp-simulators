package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TranzilaCreateRequestTest {

  private static final String CARD =
      "\"card_number\":\"4580210000004245\",\"expire_month\":7,\"expire_year\":2030";

  private static final String ITEMS =
      "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":5}]";

  private static final String NOT_SERVED =
      " is published and not served by this simulator, so it is refused rather than answered"
          + " with a success that performed something else";

  static Stream<Arguments> refusals() {
    return Stream.of(
        Arguments.of(
            "\"x\":1," + CARD + "," + ITEMS, "x is not a field of the Transactions API schema"),
        Arguments.of(CARD + "," + ITEMS, "terminal_name is required"),
        Arguments.of("\"terminal_name\":\" \"," + CARD + "," + ITEMS, "terminal_name is required"),
        Arguments.of(t("\"txn_type\":1," + CARD + "," + ITEMS), "txn_type must be a string"),
        Arguments.of(t("\"txn_type\":\"sto\"," + CARD + "," + ITEMS), "txn_type sto" + NOT_SERVED),
        Arguments.of(
            t("\"txn_type\":\"refund\"," + CARD + "," + ITEMS),
            "txn_type is none of debit, credit, verify, force, cancel, reversal and sto"),
        Arguments.of(t("\"txn_type\":\"verify\"," + CARD + "," + ITEMS), "verify_mode is required"),
        Arguments.of(
            t("\"txn_type\":\"verify\",\"verify_mode\":6," + CARD + "," + ITEMS),
            "verify_mode 6" + NOT_SERVED),
        Arguments.of(
            t("\"txn_type\":\"verify\",\"verify_mode\":3," + CARD + "," + ITEMS),
            "verify_mode is none of 2, 5 and 6"),
        Arguments.of(
            t("\"txn_type\":\"verify\",\"verify_mode\":\"5\"," + CARD + "," + ITEMS),
            "verify_mode must be an integer"),
        Arguments.of(
            t("\"verify_mode\":5.5," + CARD + "," + ITEMS), "verify_mode must be an integer"),
        Arguments.of(
            t("\"payment_plan\":6," + CARD + "," + ITEMS),
            "payment_plan other than 1 asks for instalments, which are" + NOT_SERVED),
        Arguments.of(
            t("\"payment_plan\":8," + CARD + "," + ITEMS),
            "payment_plan other than 1 asks for instalments, which are" + NOT_SERVED),
        Arguments.of(
            t("\"pan_entry_mode\":53," + CARD + "," + ITEMS),
            "pan_entry_mode is none of 50, 51 and 52"),
        Arguments.of(
            t("\"activate_3ds\":\"y\"," + CARD + "," + ITEMS), "activate_3ds is neither Y nor N"),
        Arguments.of(
            t("\"activate_3ds\":true," + CARD + "," + ITEMS), "activate_3ds must be a string"),
        Arguments.of(
            t("\"expire_month\":7,\"expire_year\":2030," + ITEMS), "card_number is required"),
        Arguments.of(
            t("\"card_number\":\"4580210000004245\",\"expire_year\":2030," + ITEMS),
            "expire_month is required"),
        Arguments.of(
            t("\"card_number\":\"4580210000004245\",\"expire_month\":7," + ITEMS),
            "expire_year is required"),
        Arguments.of(
            t(
                "\"card_number\":\"4580210000004245\",\"expire_month\":0,\"expire_year\":2030,"
                    + ITEMS),
            "expire_month must be 1 to 12"),
        Arguments.of(
            t(
                "\"card_number\":\"4580210000004245\",\"expire_month\":13,\"expire_year\":2030,"
                    + ITEMS),
            "expire_month must be 1 to 12"),
        Arguments.of(
            t(
                "\"card_number\":\"4580210000004245\",\"expire_month\":7,\"expire_year\":-1,"
                    + ITEMS),
            "expire_year must not be negative"),
        Arguments.of(
            t("\"card_number\":4580210000004245,\"expire_month\":7,\"expire_year\":2030," + ITEMS),
            "card_number must be a string"),
        Arguments.of(
            t("\"card_number\":\"4580210\",\"expire_month\":7,\"expire_year\":2030," + ITEMS),
            "card_number must be 8 to 19 letters and digits: a card number, or a token in its"
                + " place"),
        Arguments.of(
            t(
                "\"card_number\":\"45802100000042450000\",\"expire_month\":7,\"expire_year\":2030,"
                    + ITEMS),
            "card_number must be 8 to 19 letters and digits: a card number, or a token in its"
                + " place"),
        Arguments.of(
            t(
                "\"card_number\":\"4580-2100-0000\",\"expire_month\":7,\"expire_year\":2030,"
                    + ITEMS),
            "card_number must be 8 to 19 letters and digits: a card number, or a token in its"
                + " place"),
        Arguments.of(t("\"cvv\":123," + CARD + "," + ITEMS), "cvv must be a string"),
        Arguments.of(t("\"cvv\":\"12\"," + CARD + "," + ITEMS), "cvv must be 3 or 4 digits"),
        Arguments.of(t("\"cvv\":\"12345\"," + CARD + "," + ITEMS), "cvv must be 3 or 4 digits"),
        Arguments.of(
            t("\"card_holder_id\":1," + CARD + "," + ITEMS), "card_holder_id must be a string"),
        Arguments.of(
            t("\"card_holder_id\":\"12345678\"," + CARD + "," + ITEMS),
            "card_holder_id must be 9 characters"),
        Arguments.of(
            t("\"txn_currency_code\":\"GPB\"," + CARD + "," + ITEMS),
            "txn_currency_code is none of ILS, USD, EUR and GBP"),
        Arguments.of(
            t("\"txn_currency_code\":1," + CARD + "," + ITEMS),
            "txn_currency_code must be a string"),
        Arguments.of(t(CARD), "items is required"),
        Arguments.of(t(CARD + ",\"items\":{}"), "items must be an array"),
        Arguments.of(t(CARD + ",\"items\":[]"), "items must name at least one item"),
        Arguments.of(t(CARD + ",\"items\":[1]"), "every item of items is an object"),
        Arguments.of(
            t(CARD + ",\"items\":[{\"units_number\":1,\"unit_price\":5}]"),
            "items.name is required"),
        Arguments.of(
            t(CARD + ",\"items\":[{\"name\":\"a\",\"unit_price\":5}]"),
            "items.units_number is required"),
        Arguments.of(
            t(CARD + ",\"items\":[{\"name\":\"a\",\"units_number\":1}]"),
            "items.unit_price is required"),
        Arguments.of(
            t(CARD + ",\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":\"5\"}]"),
            "items.unit_price is required"),
        Arguments.of(
            t(CARD + ",\"items\":[{\"name\":\"a\",\"units_number\":0,\"unit_price\":5}]"),
            "items carries a negative unit_price or fewer than one units_number"),
        Arguments.of(
            t(CARD + ",\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":-5}]"),
            "items carries a negative unit_price or fewer than one units_number"),
        Arguments.of(
            t(CARD + ",\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":0}]"),
            "items must add up to more than nothing for a debit"),
        Arguments.of(
            t(CARD + "," + ITEMS + ",\"user_defined_fields\":{}"),
            "user_defined_fields must be an array"),
        Arguments.of(
            t(CARD + "," + ITEMS + ",\"user_defined_fields\":[1]"),
            "every element of user_defined_fields is an object"),
        Arguments.of(
            t(CARD + "," + ITEMS + ",\"user_defined_fields\":[{\"value\":\"x\"}]"),
            "user_defined_fields.name is required"),
        Arguments.of(
            t(CARD + "," + ITEMS + ",\"user_defined_fields\":[{\"name\":\"x\"}]"),
            "user_defined_fields.value is required"),
        Arguments.of(
            t(CARD + "," + ITEMS + ",\"reference_txn_id\":\"12\""),
            "reference_txn_id must be an integer"),
        Arguments.of(
            item("\"colour\":\"red\""),
            "items.colour is not a field of the Transactions API schema"),
        Arguments.of(item("\"discount\":5"), "items.discount" + UNPUBLISHED),
        Arguments.of(item("\"discount_type\":\"percent\""), "items.discount_type" + UNPUBLISHED),
        Arguments.of(
            item("\"to_txn_currency_exchange_rate\":2.5"),
            "items.to_txn_currency_exchange_rate" + UNPUBLISHED),
        Arguments.of(item("\"type\":\"C\""), "items.type C" + UNPUBLISHED),
        Arguments.of(
            item("\"currency_code\":\"USD\""),
            "items.currency_code other than txn_currency_code needs an exchange rate"
                + UNPUBLISHED),
        Arguments.of(
            item("\"currency_code\":1"),
            "items.currency_code other than txn_currency_code needs an exchange rate"
                + UNPUBLISHED),
        Arguments.of(item("\"price_type\":\"X\""), "items.price_type is neither G nor N"),
        Arguments.of(item("\"price_type\":1"), "items.price_type is neither G nor N"),
        Arguments.of(
            item("\"price_type\":\"N\",\"vat_percent\":-1"),
            "items.vat_percent must be a number that is not negative"),
        Arguments.of(
            item("\"price_type\":\"N\",\"vat_percent\":\"17\""),
            "items.vat_percent must be a number that is not negative"));
  }

  @ParameterizedTest
  @MethodSource("refusals")
  void a_body_outside_the_published_schema_is_refused_naming_what_is_wrong(
      String members, String why) {
    assertThatThrownBy(() -> read("{" + members + "}"))
        .isInstanceOf(TranzilaSchemaMismatch.class)
        .hasMessage(why);
  }

  @Test
  void
      a_debit_is_the_default_type_ils_the_default_currency_and_nothing_is_read_that_was_not_sent() {
    TranzilaCreateRequest read = read("{" + t(CARD + "," + ITEMS) + "}");

    assertThat(read.type()).isEqualTo(TranzilaTxnType.DEBIT);
    assertThat(read.currencyCode()).isEqualTo("ILS");
    assertThat(read.amount()).isEqualTo("5");
    assertThat(read.verifyMode()).isNull();
    assertThat(read.cvv()).isNull();
    assertThat(read.cardHolderId()).isNull();
    assertThat(read.referenceTxnId()).isNull();
    assertThat(read.authorizationNumber()).isNull();
    assertThat(read.dcDisable()).isNull();
    assertThat(read.namesAToken()).isFalse();
    assertThat(read.expireMonth()).isEqualTo(7);
    assertThat(read.expireYear()).isEqualTo(2030);
    assertThat(read.cardNumber()).isEqualTo("4580210000004245");
    assertThat(read.terminalName()).isEqualTo("myterminal");
  }

  @Test
  void every_value_the_schema_admits_and_this_simulator_serves_is_read() {
    TranzilaCreateRequest read =
        read(
            "{"
                + t(
                    "\"txn_type\":\"credit\",\"verify_mode\":5,\"payment_plan\":1,"
                        + "\"pan_entry_mode\":50,\"activate_3ds\":\"N\",\"3ds_settings\":{},"
                        + "\"cvv\":\"1234\",\"card_holder_id\":\"123456789\","
                        + "\"txn_currency_code\":\"GBP\",\"reference_txn_id\":12,"
                        + "\"authorization_number\":\"0587923\",\"client\":{},\"remarks\":\"r\","
                        + "\"response_language\":\"english\",\"created_by_user\":\"u\","
                        + "\"created_by_system\":\"s\",\"card_number\":\"t1234567894245\","
                        + "\"expire_month\":12,\"expire_year\":25,"
                        + "\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":12}],"
                        + ITEMS)
                + "}");

    assertThat(read.type()).isEqualTo(TranzilaTxnType.CREDIT);
    assertThat(read.verifyMode()).isNull();
    assertThat(read.cvv()).isEqualTo("1234");
    assertThat(read.cardHolderId()).isEqualTo("123456789");
    assertThat(read.currencyCode()).isEqualTo("GBP");
    assertThat(read.referenceTxnId()).isEqualTo(12L);
    assertThat(read.authorizationNumber()).isEqualTo("0587923");
    assertThat(read.dcDisable()).isEqualTo("12");
    assertThat(read.namesAToken()).isTrue();
  }

  @Test
  void the_shortest_card_number_and_the_year_zero_are_read() {
    TranzilaCreateRequest read =
        read(
            "{"
                + t("\"card_number\":\"45802100\",\"expire_month\":7,\"expire_year\":0," + ITEMS)
                + "}");

    assertThat(read.cardNumber()).isEqualTo("45802100");
    assertThat(read.expireYear()).isZero();
  }

  @Test
  void a_cvv_sent_as_null_is_read_as_not_entered() {
    assertThat(read("{" + t("\"cvv\":null," + CARD + "," + ITEMS) + "}").cvv()).isNull();
  }

  @Test
  void a_cancel_and_a_reversal_need_no_card_and_no_items() {
    TranzilaCreateRequest cancel =
        read("{" + t("\"txn_type\":\"cancel\",\"reference_txn_id\":1") + "}");
    TranzilaCreateRequest reversal =
        read("{" + t("\"txn_type\":\"reversal\",\"reference_txn_id\":1") + "}");

    assertThat(cancel.cardNumber()).isNull();
    assertThat(cancel.amount()).isNull();
    assertThat(reversal.type()).isEqualTo(TranzilaTxnType.REVERSAL);
  }

  @Test
  void the_last_dc_disable_among_the_user_defined_fields_is_the_one_read() {
    TranzilaCreateRequest read =
        read(
            "{"
                + t(
                    CARD
                        + ","
                        + ITEMS
                        + ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"a\"},"
                        + "{\"name\":\"DCdisable\",\"value\":\"b\"}]")
                + "}");

    assertThat(read.dcDisable()).isEqualTo("b");
  }

  @Test
  void a_number_too_long_for_an_integer_is_not_one() {
    assertThatThrownBy(
            () ->
                read(
                    "{"
                        + t(CARD + "," + ITEMS + ",\"reference_txn_id\":1234567890123456789")
                        + "}"))
        .hasMessage("reference_txn_id must be an integer");
    assertThat(
            read("{" + t(CARD + "," + ITEMS + ",\"reference_txn_id\":123456789012345678") + "}")
                .referenceTxnId())
        .isEqualTo(123456789012345678L);
  }

  private static final String UNPUBLISHED =
      " changes the amount by arithmetic Tranzila does not publish, so it is refused rather than"
          + " charged at an amount this simulator would have to guess";

  private static String item(String members) {
    return t(
        CARD + ",\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":5," + members + "}]");
  }

  @Test
  void a_net_price_adds_the_items_vat_or_israels_18_percent_rounded_to_agorot() {
    assertThat(read("{" + item("\"price_type\":\"N\",\"vat_percent\":17") + "}").amount())
        .isEqualTo("5.85");
    assertThat(read("{" + item("\"price_type\":\"N\"") + "}").amount()).isEqualTo("5.90");
    assertThat(
            read("{"
                    + t(
                        CARD
                            + ",\"items\":[{\"name\":\"a\",\"units_number\":1,"
                            + "\"unit_price\":0.05,\"price_type\":\"N\"}]")
                    + "}")
                .amount())
        .isEqualTo("0.06");
    assertThat(read("{" + item("\"price_type\":\"G\",\"vat_percent\":17") + "}").amount())
        .isEqualTo("5");
  }

  @Test
  void the_item_fields_the_schema_publishes_with_no_effect_on_the_amount_are_accepted() {
    assertThat(
            read("{"
                    + item(
                        "\"code\":\"A1\",\"type\":\"I\",\"unit_type\":1,"
                            + "\"currency_code\":\"ILS\",\"attributes\":{}")
                    + "}")
                .amount())
        .isEqualTo("5");
  }

  @Test
  void a_request_asking_for_3_d_secure_is_read_as_asking_and_one_saying_n_is_not() {
    assertThat(read("{" + t("\"activate_3ds\":\"Y\"," + CARD + "," + ITEMS) + "}").asksFor3ds())
        .isTrue();
    assertThat(read("{" + t("\"activate_3ds\":\"N\"," + CARD + "," + ITEMS) + "}").asksFor3ds())
        .isFalse();
    assertThat(read("{" + t(CARD + "," + ITEMS) + "}").asksFor3ds()).isFalse();
  }

  private static String t(String members) {
    return "\"terminal_name\":\"myterminal\"," + members;
  }

  private static TranzilaCreateRequest read(String body) {
    return TranzilaCreateRequest.read(TranzilaJsonReader.objectIn(body).orElseThrow());
  }
}
