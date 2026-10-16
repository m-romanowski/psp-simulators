package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaHostedFieldsRequestTest {

  @Test
  void a_charge_naming_only_the_mandatory_keys_is_an_ils_debit_without_a_token() {
    TranzilaHostedFieldsRequest read = admitted(aCharge());

    assertThat(read.terminalName()).isEqualTo("myterminal");
    assertThat(read.thtk()).isEqualTo("w1");
    assertThat(read.amount()).isEqualTo("10.50");
    assertThat(read.theAmount()).isEqualByComparingTo("10.5");
    assertThat(read.currency()).isEqualTo(TranzilaPageCurrency.NIS);
    assertThat(read.tranmode()).isEqualTo(TranzilaTranmode.A);
    assertThat(read.tokenize()).isFalse();
    assertThat(read.expiryMonth()).isEqualTo(7);
    assertThat(read.expiryYear()).isEqualTo(2030);
    assertThat(read.dcDisable()).isNull();
    assertThat(read.card().lastFour()).isEqualTo("9022");
    assertThat(read.card().cvvEntered()).isFalse();
    assertThat(read.card().idEntered()).isFalse();
    assertThat(read.userFormData()).isEmpty();
  }

  @Test
  void the_keys_a_charge_reads_are_not_echoed_and_every_other_key_is_in_the_order_sent() {
    Map<String, List<String>> sent = aCharge();
    sent.put("contact", List.of("Dana Levi"));
    sent.put("cvv", List.of("737"));
    sent.put("tran_mode", List.of("V"));
    sent.put("currency_code", List.of("EUR"));
    sent.put("tokenize", List.of("TRUE"));
    sent.put("payment_plan", List.of("1"));
    sent.put("card_holder_id_number", List.of("123456782"));
    sent.put("DCdisable", List.of("order-77"));
    sent.put("business_id", List.of("515123456", "ignored"));
    sent.put("pdesc", List.of());

    TranzilaHostedFieldsRequest read = admitted(sent);

    assertThat(read.userFormData())
        .containsExactly(
            Map.entry("contact", "Dana Levi"),
            Map.entry("DCdisable", "order-77"),
            Map.entry("business_id", "515123456"));
    assertThat(read.tranmode()).isEqualTo(TranzilaTranmode.V);
    assertThat(read.currency()).isEqualTo(TranzilaPageCurrency.EURO);
    assertThat(read.tokenize()).isTrue();
    assertThat(read.dcDisable()).isEqualTo("order-77");
    assertThat(read.card().cvvEntered()).isTrue();
    assertThat(read.card().idEntered()).isTrue();
  }

  @Test
  void each_missing_mandatory_key_is_its_own_10000_in_the_order_the_keys_are_published() {
    assertThat(refusalsOf(new LinkedHashMap<>()))
        .containsExactly(
            "10000 terminal_name MANDATORY KEY IS MISSING",
            "10000 thtk MANDATORY KEY IS MISSING",
            "10000 amount MANDATORY KEY IS MISSING",
            "10000 credit_card_number MANDATORY KEY IS MISSING",
            "10000 expiry_month MANDATORY KEY IS MISSING",
            "10000 expiry_year MANDATORY KEY IS MISSING");
  }

  @Test
  void an_empty_value_is_a_missing_key() {
    Map<String, List<String>> sent = aCharge();
    sent.put("thtk", List.of(""));

    assertThat(refusalsOf(sent)).containsExactly("10000 thtk MANDATORY KEY IS MISSING");
  }

  @Test
  void an_amount_is_a_positive_decimal_of_at_most_two_places_and_thirteen_whole_digits() {
    assertThat(refusalsWith("amount", "0"))
        .containsExactly("10006 amount INVALID AMOUNT OR AMOUNT ZERO");
    assertThat(refusalsWith("amount", "0.00")).hasSize(1);
    assertThat(refusalsWith("amount", "-1")).hasSize(1);
    assertThat(refusalsWith("amount", "1.005")).hasSize(1);
    assertThat(refusalsWith("amount", "01")).hasSize(1);
    assertThat(refusalsWith("amount", "1e2")).hasSize(1);
    assertThat(refusalsWith("amount", "12345678901234")).hasSize(1);
    assertThat(refusalsWith("amount", "1234567890123.99")).isEmpty();
    assertThat(refusalsWith("amount", "0.01")).isEmpty();
    assertThat(refusalsWith("amount", "5")).isEmpty();
  }

  @Test
  void a_currency_is_ils_usd_or_eur_as_the_iso_code() {
    assertThat(refusalsWith("currency_code", "GBP"))
        .containsExactly("10012 currency_code INVALID CURRENCY");
    assertThat(refusalsWith("currency_code", "1")).hasSize(1);
    assertThat(refusalsWith("currency_code", "ils")).hasSize(1);
    assertThat(admitted(with("currency_code", "USD")).currency())
        .isEqualTo(TranzilaPageCurrency.US_DOLLAR);
    assertThat(admitted(with("currency_code", "ILS")).currency())
        .isEqualTo(TranzilaPageCurrency.NIS);
  }

  @Test
  void a_tran_mode_is_a_v_or_n_and_nothing_the_payment_page_adds() {
    assertThat(refusalsWith("tran_mode", "K")).containsExactly("10014 tran_mode INVALID TRAN MODE");
    assertThat(refusalsWith("tran_mode", "VK")).hasSize(1);
    assertThat(refusalsWith("tran_mode", "a")).hasSize(1);
    assertThat(admitted(with("tran_mode", "N")).tranmode()).isEqualTo(TranzilaTranmode.N);
    assertThat(admitted(with("tran_mode", "A")).tranmode()).isEqualTo(TranzilaTranmode.A);
  }

  @Test
  void a_payment_plan_other_than_one_is_refused_10007() {
    assertThat(refusalsWith("payment_plan", "6"))
        .containsExactly("10007 payment_plan INVALID PAYMENT PLAN");
    assertThat(refusalsWith("payment_plan", "8")).hasSize(1);
    assertThat(refusalsWith("payment_plan", "1")).isEmpty();
  }

  @Test
  void a_card_number_is_eight_to_nineteen_digits_passing_luhn_and_spaces_are_dropped() {
    assertThat(refusalsWith("credit_card_number", "4907639999909023"))
        .containsExactly("10002 credit_card_number INVALID CREDIT CARD NUMBER");
    assertThat(refusalsWith("credit_card_number", "4907 6399 9990 9022")).isEmpty();
    assertThat(refusalsWith("credit_card_number", "4907-6399-9990-9022")).hasSize(1);
    assertThat(refusalsWith("credit_card_number", "4242424")).hasSize(1);
    assertThat(refusalsWith("credit_card_number", "18")).hasSize(1);
    assertThat(refusalsWith("credit_card_number", "12345674")).isEmpty();
    assertThat(refusalsWith("credit_card_number", "0000000000000000000")).isEmpty();
    assertThat(refusalsWith("credit_card_number", "00000000000000000000")).hasSize(1);
    assertThat(admitted(with("credit_card_number", "4918 9141 0719 5005")).card().lastFour())
        .isEqualTo("5005");
  }

  @Test
  void luhn_doubles_every_second_digit_from_the_right_and_subtracts_nine_above_nine() {
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("4907639999909022")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("4907639999990022")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("4918914107195005")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("79927398713")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("79927398710")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("59")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("18")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("19")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("91")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("4580210000004245")).isFalse();
  }

  @Test
  void an_isracard_number_of_eight_to_ten_digits_checks_by_its_weighted_sum_modulo_eleven() {
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("10000003")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("100000002")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("1000000001")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("10000004")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("12345674")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("1000004")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("20000000000")).isFalse();
    assertThat(refusalsWith("credit_card_number", "10000003")).isEmpty();
    assertThat(refusalsWith("credit_card_number", "10000004"))
        .containsExactly("10002 credit_card_number INVALID CREDIT CARD NUMBER");
  }

  @Test
  void a_number_only_the_isracard_check_accepts_is_typed_isracard_whatever_its_prefix() {
    assertThat(new TranzilaHostedCard("40000001", false, false).cardType())
        .isEqualTo(TranzilaCard.ISRACARD);
    assertThat(new TranzilaHostedCard("10000003", false, false).cardType())
        .isEqualTo(TranzilaCard.ISRACARD);
    assertThat(new TranzilaHostedCard("40000002", false, false).cardType())
        .as("a number Luhn accepts is typed by its prefix")
        .isEqualTo(TranzilaCard.VISA);
    assertThat(new TranzilaHostedCard("4907639999909022", false, false).cardType())
        .isEqualTo(TranzilaCard.VISA);
    assertThat(new TranzilaHostedCard("5326100000002716", false, false).cardType())
        .as("a number neither check accepts is typed by its prefix")
        .isEqualTo(TranzilaCard.MASTERCARD);
    assertThat(new TranzilaHostedCard("40000001", false, false).withoutItsNumber(1, 2031))
        .isEqualTo(new TranzilaCard("xxxx0001", TranzilaCard.ISRACARD, 1, 31));
  }

  @Test
  void a_doubled_digit_from_five_up_loses_nine_and_below_five_is_only_doubled() {
    assertThat(TranzilaHostedFieldsRequest.theDoubledSumOf(0)).isZero();
    assertThat(TranzilaHostedFieldsRequest.theDoubledSumOf(4)).isEqualTo(8);
    assertThat(TranzilaHostedFieldsRequest.theDoubledSumOf(5)).isEqualTo(1);
    assertThat(TranzilaHostedFieldsRequest.theDoubledSumOf(9)).isEqualTo(9);
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("50")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("4002")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("5009")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("5000")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("0000000000000000000")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.passesLuhn("0000000000000000001")).isFalse();
  }

  @Test
  void every_weight_of_the_isracard_check_counts() {
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("31000002")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("13000002")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("12000000")).isTrue();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("21000000")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("00000020")).isFalse();
    assertThat(TranzilaHostedFieldsRequest.isAnIsracardNumber("00000000")).isTrue();
  }

  @Test
  void a_cvv_when_sent_is_three_or_four_digits() {
    assertThat(refusalsWith("cvv", "12")).containsExactly("10003 cvv INVALID CVV");
    assertThat(refusalsWith("cvv", "12345")).hasSize(1);
    assertThat(refusalsWith("cvv", "12a")).hasSize(1);
    assertThat(refusalsWith("cvv", "123")).isEmpty();
    assertThat(refusalsWith("cvv", "1234")).isEmpty();
  }

  @Test
  void an_expiry_month_is_one_to_twelve_and_a_year_four_digits() {
    assertThat(refusalsWith("expiry_month", "13"))
        .containsExactly("10004 expiry_month INVALID EXPIRATION MONTH");
    assertThat(refusalsWith("expiry_month", "0")).hasSize(1);
    assertThat(refusalsWith("expiry_month", "007")).hasSize(1);
    assertThat(refusalsWith("expiry_month", "x")).hasSize(1);
    assertThat(admitted(with("expiry_month", "01")).expiryMonth()).isEqualTo(1);
    assertThat(admitted(with("expiry_month", "12")).expiryMonth()).isEqualTo(12);
    assertThat(refusalsWith("expiry_year", "30"))
        .containsExactly("10005 expiry_year INVALID EXPIRATION YEAR");
    assertThat(refusalsWith("expiry_year", "20301")).hasSize(1);
  }

  @Test
  void a_card_holder_id_number_when_sent_is_one_to_nine_digits() {
    assertThat(refusalsWith("card_holder_id_number", "1234567890"))
        .containsExactly("10011 card_holder_id_number INVALID CARD HOLDER ID NUMBER");
    assertThat(refusalsWith("card_holder_id_number", "12345678a")).hasSize(1);
    assertThat(refusalsWith("card_holder_id_number", "1")).isEmpty();
  }

  @Test
  void a_dcdisable_longer_than_254_is_refused_22103_as_the_card_api_refuses_it() {
    assertThat(refusalsWith("DCdisable", "d".repeat(255)))
        .containsExactly("22103 DCdisable Invalid DCdisable");
    assertThat(refusalsWith("DCdisable", "d".repeat(254))).isEmpty();
  }

  @Test
  void tokenize_is_true_only_when_it_says_true() {
    assertThat(admitted(with("tokenize", "True")).tokenize()).isTrue();
    assertThat(admitted(with("tokenize", "1")).tokenize()).isFalse();
    assertThat(admitted(with("tokenize", "false")).tokenize()).isFalse();
  }

  @Test
  void the_published_error_table_is_read_as_published() {
    assertThat(TranzilaHostedFieldsErrors.THE_PUBLISHED_TABLE)
        .containsExactly(
            Map.entry(10000, "MANDATORY KEY IS MISSING"),
            Map.entry(10001, "INVALID TERMINAL NAME"),
            Map.entry(10002, "INVALID CREDIT CARD NUMBER"),
            Map.entry(10003, "INVALID CVV"),
            Map.entry(10004, "INVALID EXPIRATION MONTH"),
            Map.entry(10005, "INVALID EXPIRATION YEAR"),
            Map.entry(10006, "INVALID AMOUNT OR AMOUNT ZERO"),
            Map.entry(10007, "INVALID PAYMENT PLAN"),
            Map.entry(10008, "INVALID TOTAL INSTALLMENTS NUMBER"),
            Map.entry(10009, "INVALID FIRST INSTALLMENT AMOUNT"),
            Map.entry(10010, "INVALID OTHER INSTALLMENTS AMOUNT"),
            Map.entry(10011, "INVALID CARD HOLDER ID NUMBER"),
            Map.entry(10012, "INVALID CURRENCY"),
            Map.entry(10013, "TERMINAL NOT FOUND OR BAD CONFIGURATION"),
            Map.entry(10014, "INVALID TRAN MODE"),
            Map.entry(10015, "TOTAL INSTALLMENTS NUMBER MISSING"),
            Map.entry(10016, "INSTALLMENTS AMOUNT FIELDS MISSING"));
    assertThat(TranzilaHostedFieldsErrors.theHandshakeDoesNotLockIt())
        .isEqualTo(
            new TranzilaHostedFieldsRequest.Refusal(912791, "thtk", "Illegal Operation 912791"));
    assertThatThrownBy(() -> TranzilaHostedFieldsErrors.of(20004, "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("20004 is no code of the hosted fields error table tranzila publishes");
  }

  @Test
  void a_typed_card_keeps_its_last_four_and_type_and_prints_neither_its_number_nor_its_bin() {
    TranzilaHostedCard typed = new TranzilaHostedCard("5326100000002716", true, false);

    assertThat(typed.withoutItsNumber(2, 2026))
        .isEqualTo(new TranzilaCard("xxxxxxxxxxxx2716", TranzilaCard.MASTERCARD, 2, 26));
    assertThat(typed.cardType()).isEqualTo(TranzilaCard.MASTERCARD);
    assertThat(typed).hasToString("a card the payer typed, ending 2716");
    assertThat(typed.cvvEntered()).isTrue();
    assertThat(typed.idEntered()).isFalse();
    assertThat(admitted(aCharge()).toString()).doesNotContain("4907639999909022");
  }

  private static Map<String, List<String>> aCharge() {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    sent.put("terminal_name", List.of("myterminal"));
    sent.put("thtk", List.of("w1"));
    sent.put("amount", List.of("10.50"));
    sent.put("credit_card_number", List.of("4907639999909022"));
    sent.put("expiry_month", List.of("7"));
    sent.put("expiry_year", List.of("2030"));
    return sent;
  }

  private static Map<String, List<String>> with(String key, String value) {
    Map<String, List<String>> sent = aCharge();
    sent.put(key, List.of(value));
    return sent;
  }

  private static List<String> refusalsWith(String key, String value) {
    return refusalsOf(with(key, value));
  }

  private static TranzilaHostedFieldsRequest admitted(Map<String, List<String>> sent) {
    if (TranzilaHostedFieldsRequest.read(sent)
        instanceof TranzilaHostedFieldsRequest.Read.Admitted admitted) {
      return admitted.request();
    }
    throw new AssertionError("refused " + refusalsOf(sent));
  }

  private static List<String> refusalsOf(Map<String, List<String>> sent) {
    if (TranzilaHostedFieldsRequest.read(sent)
        instanceof TranzilaHostedFieldsRequest.Read.Refused refused) {
      return refused.refusals().stream()
          .map(refusal -> refusal.code() + " " + refusal.param() + " " + refusal.message())
          .toList();
    }
    return List.of();
  }
}
