package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.nio.charset.StandardCharsets;
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

    assertThat(TranzilaBrowserReturn.theNotifyOf(page, aDebit("000", "0587923", "tok12345678")))
        .containsAllEntriesOf(
            TranzilaBrowserReturn.theFieldsOf(page, aDebit("000", "0587923", "tok12345678")))
        .containsEntry("ConfirmationCode", "0587923")
        .hasSize(16)
        .isUnmodifiable();
    assertThat(
            List.copyOf(
                    TranzilaBrowserReturn.theNotifyOf(page, aDebit("000", "0587923", "tok12345678"))
                        .keySet())
                .getLast())
        .isEqualTo("ConfirmationCode");
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
            Map.entry("cardacquirer", "2"),
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
        TranzilaWiring.NOW);
  }
}
