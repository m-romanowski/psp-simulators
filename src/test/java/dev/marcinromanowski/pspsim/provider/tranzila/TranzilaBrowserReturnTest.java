package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TranzilaBrowserReturnTest {

  @Test
  void an_approved_charge_returns_the_published_fields_in_the_published_order() {
    SequencedMap<String, String> echoed = new LinkedHashMap<>();
    echoed.put("company", "Test Company");
    echoed.put("pdesc", "Seats");
    TranzilaPage page =
        aPage(TranzilaTranmode.AK, echoed, "{\"order_id\":\"ORD-1\",\"sum\":\"999\"}");

    assertThat(
            TranzilaBrowserReturn.theFieldsOf(
                page, aDebit("000", "0587923", "t5d55d2922ca4024245")))
        .containsExactly(
            Map.entry("supplier", "myterminal"),
            Map.entry("sum", "10.53"),
            Map.entry("currency", "978"),
            Map.entry("Response", "000"),
            Map.entry("company", "Test Company"),
            Map.entry("pdesc", "Seats"),
            Map.entry("transaction_id", "41044"),
            Map.entry("index", "41044"),
            Map.entry("ccno", "4245"),
            Map.entry("Responsecv", "1"),
            Map.entry("Responseid", "0"),
            Map.entry("cardtype", "2"),
            Map.entry("cardissuer", "1"),
            Map.entry("cardacquirer", "2"),
            Map.entry("TranzilaTK", "t5d55d2922ca4024245"),
            Map.entry("txn_type", "debit"),
            Map.entry("tranmode", "AK"),
            Map.entry("order_id", "ORD-1"));
  }

  @Test
  void
      the_notify_adds_the_authorization_number_as_the_confirmation_code_after_every_return_field() {
    TranzilaPage page = aPage(TranzilaTranmode.A, new LinkedHashMap<>(), null);
    SequencedMap<String, String> notified =
        TranzilaBrowserReturn.theNotifyOf(page, aDebit("000", "0587923", "tok12345678"));

    assertThat(notified)
        .containsAllEntriesOf(
            TranzilaBrowserReturn.theFieldsOf(page, aDebit("000", "0587923", "tok12345678")))
        .containsEntry("ConfirmationCode", "0587923")
        .hasSize(16)
        .isUnmodifiable();
    assertThat(notified.lastEntry()).isEqualTo(Map.entry("ConfirmationCode", "0587923"));
  }

  @Test
  void the_issuer_is_the_samples_for_every_card_and_the_acquirer_is_read_off_the_card_type() {
    List<String> read = new ArrayList<>();
    for (int cardType = 1; cardType <= 6; cardType++) {
      TranzilaPage saved =
          aPage(TranzilaTranmode.K, new LinkedHashMap<>(), null)
              .settledAs(
                  PayerOutcome.PAID,
                  null,
                  "777",
                  "tok12345678",
                  new TranzilaCard("xxxxxxxxxxxx1111", cardType, 1, 30),
                  TranzilaWiring.NOW);
      SequencedMap<String, String> fields = TranzilaBrowserReturn.theFieldsOf(saved, null);
      read.add(cardType + " " + fields.get("cardissuer") + " " + fields.get("cardacquirer"));
    }

    assertThat(read).containsExactly("1 1 2", "2 1 2", "3 1 3", "4 1 4", "5 1 1", "6 1 2");
  }

  @Test
  void the_names_never_echoed_are_exactly_the_names_a_full_return_and_its_notify_set_and_thtk() {
    TranzilaPage page = aPage(TranzilaTranmode.AK, new LinkedHashMap<>(), null);

    assertThat(TranzilaPageRequest.NEVER_ECHOED)
        .containsExactlyInAnyOrder(
            "supplier",
            "sum",
            "currency",
            "Response",
            "transaction_id",
            "index",
            "ccno",
            "Responsecv",
            "Responseid",
            "cardtype",
            "cardissuer",
            "cardacquirer",
            "TranzilaTK",
            "txn_type",
            "tranmode",
            "ConfirmationCode",
            "thtk");
    assertThat(
            TranzilaBrowserReturn.theNotifyOf(page, aDebit("000", "0587923", "tok12345678"))
                .keySet())
        .containsExactlyInAnyOrderElementsOf(TranzilaBrowserReturn.THE_NAMES_THE_RETURN_SETS);
  }

  @Test
  void every_parameter_the_page_received_comes_back_but_the_thtk_and_the_names_the_return_sets() {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    sent.put("orderid", List.of("A-77"));
    sent.put("sum", List.of("10.53"));
    sent.put("currency", List.of("978"));
    sent.put("tranmode", List.of("AK"));
    sent.put("thtk", List.of("w123"));
    sent.put("Response", List.of("999"));
    sent.put("TranzilaTK", List.of("forged"));
    sent.put("myid", List.of("123456789"));
    sent.put("lang", List.of("il"));
    sent.put("empty", List.of(""));
    TranzilaPage page =
        aPage(TranzilaTranmode.AK, TranzilaPageRequest.theEchoedFieldsOf(sent), null);

    SequencedMap<String, String> returned =
        TranzilaBrowserReturn.theFieldsOf(page, aDebit("000", "0587923", "real1234567"));

    assertThat(page.echoed())
        .containsExactly(
            Map.entry("orderid", "A-77"), Map.entry("myid", "123456789"), Map.entry("lang", "il"));
    assertThat(returned)
        .containsEntry("orderid", "A-77")
        .containsEntry("myid", "123456789")
        .containsEntry("Response", "000")
        .containsEntry("TranzilaTK", "real1234567")
        .doesNotContainKey("thtk");
    assertThat(
            TranzilaNotify.payloadOf(page, aDebit("000", "0587923", "real1234567")).sanitizedBody())
        .containsEntry("myid", List.of("[redacted]"))
        .containsEntry("orderid", List.of("A-77"));
  }

  @Test
  void a_declined_charge_returns_its_code_and_neither_a_token_nor_a_confirmation_code() {
    TranzilaPage page = aPage(TranzilaTranmode.A, new LinkedHashMap<>(), null);
    TranzilaTransaction declined = aDebit("004", null, null);

    assertThat(TranzilaBrowserReturn.theNotifyOf(page, declined))
        .containsEntry("Response", "004")
        .containsEntry("ccno", "4245")
        .doesNotContainKey("TranzilaTK")
        .doesNotContainKey("ConfirmationCode");
    assertThat(TranzilaBrowserReturn.isApproved(page, declined)).isFalse();
    assertThat(TranzilaBrowserReturn.theTokenOf(page, aDebit("004", null, "kept1234567")))
        .isEmpty();
  }

  @Test
  void a_token_only_page_returns_its_saved_card_and_token_and_names_no_transaction() {
    TranzilaPage saved =
        aPage(TranzilaTranmode.K, new LinkedHashMap<>(), null)
            .settledAs(
                PayerOutcome.PAID,
                null,
                "777",
                "t5d55d2922ca4029022",
                new TranzilaCard("xxxxxxxxxxxx9022", TranzilaCard.ISRACARD, 12, 30),
                TranzilaWiring.NOW);

    assertThat(TranzilaBrowserReturn.theNotifyOf(saved, null))
        .containsExactly(
            Map.entry("supplier", "myterminal"),
            Map.entry("sum", "10.53"),
            Map.entry("currency", "978"),
            Map.entry("Response", "777"),
            Map.entry("ccno", "9022"),
            Map.entry("Responsecv", "0"),
            Map.entry("Responseid", "0"),
            Map.entry("cardtype", "5"),
            Map.entry("cardissuer", "1"),
            Map.entry("cardacquirer", "1"),
            Map.entry("TranzilaTK", "t5d55d2922ca4029022"),
            Map.entry("tranmode", "K"));
    assertThat(TranzilaBrowserReturn.isApproved(saved, null)).isTrue();
  }

  @Test
  void a_page_that_saved_no_card_has_no_card_to_return() {
    TranzilaPage open = aPage(TranzilaTranmode.K, new LinkedHashMap<>(), null);

    assertThat(TranzilaBrowserReturn.theCardOf(open, null)).isEmpty();
    assertThat(
            TranzilaBrowserReturn.theCardOf(
                open.settledAs(
                    PayerOutcome.PAID, null, "777", "tok12345678", null, TranzilaWiring.NOW),
                null))
        .isEmpty();
  }

  @Test
  void request_params_come_back_as_text_numbers_and_json_and_never_replace_a_published_field() {
    TranzilaPage page =
        aPage(
            TranzilaTranmode.A,
            new LinkedHashMap<>(),
            "{\"name\":\"serial\",\"user_id\":12,\"vip\":true,\"tags\":[\"a\"],\"Response\":\"x\"}");

    assertThat(TranzilaBrowserReturn.theRequestParamsOf(page))
        .containsExactly(
            Map.entry("name", "serial"),
            Map.entry("user_id", "12"),
            Map.entry("vip", "true"),
            Map.entry("tags", "[\"a\"]"),
            Map.entry("Response", "x"));
    assertThat(TranzilaBrowserReturn.theFieldsOf(page, aDebit("000", "1", "tok12345678")))
        .containsEntry("Response", "000");
    assertThat(
            TranzilaBrowserReturn.theRequestParamsOf(
                aPage(TranzilaTranmode.A, new LinkedHashMap<>(), "not json")))
        .isEmpty();
  }

  @Test
  void the_notify_is_posted_form_encoded_and_its_trail_withholds_the_token() {
    TranzilaPage page = aPage(TranzilaTranmode.A, new LinkedHashMap<>(), null);

    WebhookPayload payload =
        TranzilaNotify.payloadOf(page, aDebit("000", "0587923", "t5d55d2922ca4024245"));

    assertThat(payload.contentType()).isEqualTo("application/x-www-form-urlencoded");
    assertThat(payload.providerHeaders()).isEmpty();
    assertThat(new String(payload.body(), StandardCharsets.UTF_8))
        .isEqualTo(
            "supplier=myterminal&sum=10.53&currency=978&Response=000&transaction_id=41044"
                + "&index=41044&ccno=4245&Responsecv=1&Responseid=0&cardtype=2&cardissuer=1"
                + "&cardacquirer=2&TranzilaTK=t5d55d2922ca4024245&txn_type=debit&tranmode=A"
                + "&ConfirmationCode=0587923");
    assertThat(payload.sanitizedBody())
        .containsEntry("TranzilaTK", List.of("[redacted]"))
        .containsEntry("ConfirmationCode", List.of("0587923"));
  }

  static TranzilaPage aPage(
      TranzilaTranmode tranmode,
      SequencedMap<String, String> echoed,
      @Nullable String requestParams) {
    return new TranzilaPage(
        "directng_" + "0".repeat(23) + "1",
        TranzilaWiring.RUN,
        TranzilaWiring.ACCOUNT,
        TranzilaWiring.TERMINAL,
        "10.53",
        TranzilaPageCurrency.EURO,
        tranmode,
        null,
        null,
        null,
        null,
        null,
        requestParams,
        echoed,
        ObjectOrigin.PROVIDER_API,
        TranzilaWiring.NOW,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  static TranzilaTransaction aDebit(
      String code, @Nullable String authNumber, @Nullable String token) {
    return new TranzilaTransaction(
        TranzilaWiring.RUN,
        41044,
        41044,
        TranzilaWiring.ACCOUNT,
        TranzilaWiring.TERMINAL,
        TranzilaTxnType.DEBIT,
        null,
        null,
        code,
        "10.53",
        "EUR",
        authNumber,
        token,
        new TranzilaCard("458021xxxxxx4245", TranzilaCard.VISA, 7, 30),
        "1",
        "0",
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        TranzilaWiring.NOW,
        null);
  }
}
