package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaPageRequestTest {

  @Test
  void the_published_parameters_are_read_and_the_predetermined_fields_kept_in_the_tables_order() {
    Map<String, List<String>> sent = minimal();
    sent.put("success_url_address", List.of("https://shop.example/ok"));
    sent.put("fail_url_address", List.of("http://shop.example/fail"));
    sent.put("notify_url_address", List.of("https://shop.example/notify"));
    sent.put("DCdisable", List.of("order-1"));
    sent.put("thtk", List.of("w123"));
    sent.put("cred_type", List.of("1"));
    sent.put("remarks", List.of("second"));
    sent.put("contact", List.of("Dana"));
    sent.put("lang", List.of("il"));
    sent.put("myid", List.of("123456789", "ignored"));

    TranzilaPageRequest read = admitted(sent);

    assertThat(read.sum()).isEqualTo("10.53");
    assertThat(read.currency()).isEqualTo(TranzilaPageCurrency.US_DOLLAR);
    assertThat(read.tranmode()).isEqualTo(TranzilaTranmode.VK);
    assertThat(read.successUrl()).isEqualTo("https://shop.example/ok");
    assertThat(read.failUrl()).isEqualTo("http://shop.example/fail");
    assertThat(read.notifyUrl()).isEqualTo("https://shop.example/notify");
    assertThat(read.dcDisable()).isEqualTo("order-1");
    assertThat(read.thtk()).isEqualTo("w123");
    assertThat(read.echoed())
        .containsExactly(
            Map.entry("myid", "123456789"),
            Map.entry("contact", "Dana"),
            Map.entry("remarks", "second"));
  }

  @Test
  void a_page_with_nothing_optional_reads_no_address_no_key_and_echoes_nothing() {
    TranzilaPageRequest read = admitted(minimal());

    assertThat(read.successUrl()).isNull();
    assertThat(read.failUrl()).isNull();
    assertThat(read.notifyUrl()).isNull();
    assertThat(read.dcDisable()).isNull();
    assertThat(read.thtk()).isNull();
    assertThat(read.echoed()).isEmpty();
  }

  @Test
  void a_sum_that_is_no_positive_decimal_is_refused() {
    for (String sum : List.of("", "0", "0.00", "-1", "1,5", "1.", ".5", "1e2", "ten")) {
      Map<String, List<String>> sent = minimal();
      sent.put("sum", List.of(sum));
      assertThat(refused(sent)).as(sum).isEqualTo(TranzilaPageRequest.THE_SUM_IS_REQUIRED);
    }
    Map<String, List<String>> missing = minimal();
    missing.remove("sum");
    assertThat(refused(missing)).isEqualTo(TranzilaPageRequest.THE_SUM_IS_REQUIRED);
    Map<String, List<String>> none = minimal();
    none.put("sum", List.of());
    assertThat(refused(none)).isEqualTo(TranzilaPageRequest.THE_SUM_IS_REQUIRED);
  }

  @Test
  void a_currency_outside_the_four_published_numbers_is_refused() {
    for (String currency : List.of("ILS", "3", "376", "")) {
      Map<String, List<String>> sent = minimal();
      sent.put("currency", List.of(currency));
      assertThat(refused(sent))
          .as(currency)
          .isEqualTo(TranzilaPageRequest.THE_CURRENCY_IS_REQUIRED);
    }
    Map<String, List<String>> missing = minimal();
    missing.remove("currency");
    assertThat(refused(missing)).isEqualTo(TranzilaPageRequest.THE_CURRENCY_IS_REQUIRED);
  }

  @Test
  void every_served_tranmode_is_read_j_is_refused_as_a_standing_order_and_any_other_as_unknown() {
    for (String tranmode : List.of("A", "V", "N", "K", "AK", "VK", "NK")) {
      Map<String, List<String>> sent = minimal();
      sent.put("tranmode", List.of(tranmode));
      assertThat(admitted(sent).tranmode().wire()).isEqualTo(tranmode);
    }
    Map<String, List<String>> standing = minimal();
    standing.put("tranmode", List.of("J"));
    Map<String, List<String>> lower = minimal();
    lower.put("tranmode", List.of("a"));
    Map<String, List<String>> missing = minimal();
    missing.remove("tranmode");

    assertThat(refused(standing)).isEqualTo(TranzilaPageRequest.A_STANDING_ORDER_IS_NOT_SERVED);
    assertThat(refused(lower)).isEqualTo(TranzilaPageRequest.THE_TRANMODE_IS_REQUIRED);
    assertThat(refused(missing)).isEqualTo(TranzilaPageRequest.THE_TRANMODE_IS_REQUIRED);
  }

  @Test
  void instalments_are_refused() {
    for (String credType : List.of("6", "8", "2")) {
      Map<String, List<String>> sent = minimal();
      sent.put("cred_type", List.of(credType));
      assertThat(refused(sent)).isEqualTo(TranzilaPageRequest.INSTALMENTS_ARE_NOT_SERVED);
    }
  }

  @Test
  void a_return_or_notify_address_a_browser_could_not_be_sent_to_is_refused_by_its_name() {
    for (String address :
        List.of("success_url_address", "fail_url_address", "notify_url_address")) {
      for (String value : List.of("/relative", "ftp://shop.example/x", "https://", "http://a b")) {
        Map<String, List<String>> sent = minimal();
        sent.put(address, List.of(value));
        assertThat(refused(sent))
            .as(address + " " + value)
            .isEqualTo(address + TranzilaPageRequest.NOT_AN_ADDRESS);
      }
    }
  }

  @Test
  void a_dc_disable_of_254_characters_is_read_and_one_more_is_refused() {
    Map<String, List<String>> longest = minimal();
    longest.put("DCdisable", List.of("d".repeat(254)));
    Map<String, List<String>> tooLong = minimal();
    tooLong.put("DCdisable", List.of("d".repeat(255)));

    assertThat(admitted(longest).dcDisable()).hasSize(254);
    assertThat(refused(tooLong)).isEqualTo(TranzilaPageRequest.THE_DC_DISABLE_IS_TOO_LONG);
  }

  @Test
  void a_sum_is_compared_with_a_locked_one_by_value_and_not_by_spelling() {
    TranzilaPageRequest read = admitted(minimal());

    assertThat(read.answersOnTheSum(new BigDecimal("10.530"))).isTrue();
    assertThat(read.answersOnTheSum(new BigDecimal("10.54"))).isFalse();
  }

  @Test
  void the_echoed_fields_cannot_be_changed_after_they_were_read() {
    TranzilaPageRequest read = admitted(minimal());

    assertThat(read.echoed()).isUnmodifiable();
  }

  private static Map<String, List<String>> minimal() {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    sent.put("sum", List.of("10.53"));
    sent.put("currency", List.of("2"));
    sent.put("tranmode", List.of("VK"));
    return sent;
  }

  private static TranzilaPageRequest admitted(Map<String, List<String>> sent) {
    return ((TranzilaPageRequest.Read.Admitted) TranzilaPageRequest.read(sent)).request();
  }

  private static String refused(Map<String, List<String>> sent) {
    return ((TranzilaPageRequest.Read.Refused) TranzilaPageRequest.read(sent)).why();
  }
}
