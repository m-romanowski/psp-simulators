package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.RenderedResponse;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TranzilaPagePartsTest {

  @Test
  void the_return_page_posts_every_field_escaped_and_its_policy_admits_exactly_its_own_script()
      throws NoSuchAlgorithmException {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("contact", "Dana \"D\" <Levi>");
    fields.put("sum", "10");
    fields.put("submit", "1");
    fields.put("action", "elsewhere");
    fields.put("method", "get");

    String page = TranzilaReturnPage.posting("https://shop.example/ok?a=1&b=2", fields);
    String hash =
        Base64.getEncoder()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        "HTMLFormElement.prototype.submit.call(document.forms[0]);"
                            .getBytes(StandardCharsets.UTF_8)));

    assertThat(page)
        .startsWith("<!DOCTYPE html>")
        .contains("<form method=\"post\" action=\"https://shop.example/ok?a=1&amp;b=2\">")
        .contains(
            "<input type=\"hidden\" name=\"contact\" value=\"Dana &quot;D&quot; &lt;Levi&gt;\">")
        .contains("<input type=\"hidden\" name=\"sum\" value=\"10\">")
        .contains("<input type=\"hidden\" name=\"submit\" value=\"1\">")
        .contains("<input type=\"hidden\" name=\"action\" value=\"elsewhere\">")
        .contains("<button type=\"submit\">Continue to the shop</button>")
        .endsWith(
            "<script>HTMLFormElement.prototype.submit.call(document.forms[0]);</script>"
                + "</body></html>");
    assertThat(hash).isEqualTo("Zd9Ppkm6KJvfEle1ogzptKkD+DOonO1DYBPhi02doj8=");
    assertThat(TranzilaReturnPage.WHAT_THIS_PAGE_PERMITS)
        .isEqualTo(
            "default-src 'none'; style-src 'unsafe-inline';"
                + " script-src 'sha256-Zd9Ppkm6KJvfEle1ogzptKkD+DOonO1DYBPhi02doj8='");
    assertThat(TranzilaReturnPage.DEFENCE_HEADERS)
        .containsEntry(
            "Content-Security-Policy",
            List.of(
                "default-src 'none'; style-src 'unsafe-inline';"
                    + " script-src 'sha256-Zd9Ppkm6KJvfEle1ogzptKkD+DOonO1DYBPhi02doj8='"))
        .containsEntry("Cache-Control", List.of("no-store"));
  }

  @Test
  void the_payment_page_says_each_outcome_in_its_own_words_and_escapes_what_it_was_sent() {
    TranzilaPage page =
        TranzilaBrowserReturnTest.aPage(TranzilaTranmode.A, new LinkedHashMap<>(), null);
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("Response", "004");
    fields.put("TranzilaTK", "tok12345678");

    assertThat(TranzilaDirectNgPage.refusing("a <b> refusal"))
        .contains("<h1>Tranzila payment page</h1><p>a &lt;b&gt; refusal</p>");
    assertThat(TranzilaDirectNgPage.left()).contains(TranzilaDirectNgPage.LEFT);
    assertThat(TranzilaDirectNgPage.reporting(page, fields, false))
        .contains("<p>The payment was declined.</p>")
        .contains("<dl><dt>Amount</dt><dd>10.53 EUR</dd></dl>")
        .contains("<dt>Response</dt><dd>004</dd>")
        .contains("<dt>TranzilaTK</dt><dd>[redacted]</dd>")
        .doesNotContain("tok12345678");
    assertThat(
            TranzilaDirectNgPage.offering(page, List.of(new PayerCard("a\"b", "Visa <ending> 1"))))
        .contains("<option value=\"a&quot;b\">Visa &lt;ending&gt; 1</option>")
        .contains("action=\"/tranzila/directng/myterminal/payer/" + page.pageId() + "\"")
        .doesNotContain("<dt>For</dt>");
    assertThat(TranzilaDirectNgPage.pathOf("t1")).isEqualTo("/tranzila/directng/t1/iframenew.php");
    assertThat(TranzilaDirectNgPage.payerPathOf("t1", "p1"))
        .isEqualTo("/tranzila/directng/t1/payer/p1");
    assertThat(TranzilaDirectNgPage.ROUTE).isEqualTo("/tranzila/directng/{terminal}/iframenew.php");
    assertThat(TranzilaDirectNgPage.PAYER_ROUTE)
        .isEqualTo("/tranzila/directng/{terminal}/payer/{pageId}");
  }

  @Test
  void every_tranmode_names_the_transaction_it_records_and_k_records_none() {
    assertThat(TranzilaTranmode.named("A")).contains(TranzilaTranmode.A);
    assertThat(TranzilaTranmode.named("NK")).contains(TranzilaTranmode.NK);
    assertThat(TranzilaTranmode.named("J")).isEmpty();
    assertThat(TranzilaTranmode.A.type()).contains(TranzilaTxnType.DEBIT);
    assertThat(TranzilaTranmode.AK.type()).contains(TranzilaTxnType.DEBIT);
    assertThat(TranzilaTranmode.V.verifyMode()).isEqualTo(Integer.valueOf(5));
    assertThat(TranzilaTranmode.VK.verifyMode()).isEqualTo(Integer.valueOf(5));
    assertThat(TranzilaTranmode.N.verifyMode()).isEqualTo(Integer.valueOf(2));
    assertThat(TranzilaTranmode.NK.verifyMode()).isEqualTo(Integer.valueOf(2));
    assertThat(TranzilaTranmode.A.verifyMode()).isNull();
    assertThat(TranzilaTranmode.K.type()).isEmpty();
    assertThat(TranzilaTranmode.K.recordsATransaction()).isFalse();
    assertThat(TranzilaTranmode.VK.recordsATransaction()).isTrue();
  }

  @Test
  void the_four_page_currencies_are_numbered_as_published_and_answer_their_iso_letters() {
    assertThat(TranzilaPageCurrency.numbered("1")).contains(TranzilaPageCurrency.NIS);
    assertThat(TranzilaPageCurrency.numbered("2")).contains(TranzilaPageCurrency.US_DOLLAR);
    assertThat(TranzilaPageCurrency.numbered("978")).contains(TranzilaPageCurrency.EURO);
    assertThat(TranzilaPageCurrency.numbered("826")).contains(TranzilaPageCurrency.POUND_STERLING);
    assertThat(TranzilaPageCurrency.numbered("376")).isEmpty();
    assertThat(TranzilaPageCurrency.NIS.alpha()).isEqualTo("ILS");
    assertThat(TranzilaPageCurrency.POUND_STERLING.alpha()).isEqualTo("GBP");
  }

  @Test
  void the_stored_forms_of_a_page_read_back_what_was_written() {
    SequencedMap<String, String> fields = new LinkedHashMap<>();
    fields.put("contact", "Dana Levi & co=1");
    fields.put("json_purchase_data", "[{\"a\":1}]");
    fields.put("empty", "");

    assertThat(TranzilaStoredForm.decoded(TranzilaStoredForm.encoded(fields)))
        .containsExactlyEntriesOf(fields);
    assertThat(TranzilaStoredForm.decoded("")).isEmpty();
    assertThat(TranzilaStoredForm.encoded(Map.of())).isEmpty();
    assertThat(TranzilaStoredForm.nameOf(null)).isNull();
    assertThat(TranzilaStoredForm.nameOf(PayerOutcome.DECLINED)).isEqualTo("DECLINED");
    assertThat(TranzilaStoredForm.outcomeOf(null)).isNull();
    assertThat(TranzilaStoredForm.outcomeOf("ABANDONED")).isEqualTo(PayerOutcome.ABANDONED);
    assertThat(TranzilaStoredForm.tranmodeOf("VK")).isEqualTo(TranzilaTranmode.VK);
    assertThat(TranzilaStoredForm.currencyOf("978")).isEqualTo(TranzilaPageCurrency.EURO);
    assertThatThrownBy(() -> TranzilaStoredForm.tranmodeOf("J"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("the store holds J as a tranmode, which no tranmode names");
    assertThatThrownBy(() -> TranzilaStoredForm.currencyOf("3"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("the store holds 3 as a page currency, which no currency numbers");
  }

  @Test
  void a_page_charges_its_card_as_the_api_body_its_tranmode_names() {
    TranzilaPage page =
        new TranzilaPage(
            "directng_" + "0".repeat(24),
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            "10.5",
            TranzilaPageCurrency.US_DOLLAR,
            TranzilaTranmode.VK,
            null,
            null,
            null,
            "order-1",
            null,
            null,
            new LinkedHashMap<>(),
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

    TranzilaApiCall call = TranzilaPageCharge.callOf(page, TranzilaPageCards.thePublishedCard());

    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path())
        .isEqualTo("/tranzila/directng/myterminal/payer/directng_" + "0".repeat(24));
    assertThat(call.rawBody())
        .isEqualTo(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"verify\",\"verify_mode\":5,"
                + "\"card_number\":\"t5d55d2922ca4029022\",\"expire_month\":12,"
                + "\"expire_year\":2030,\"txn_currency_code\":\"USD\",\"items\":[{\"name\":"
                + "\"simulator\",\"units_number\":1,\"unit_price\":10.5}],"
                + "\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"order-1\"}]}");
    assertThat(
            TranzilaPageCharge.callOf(
                    TranzilaBrowserReturnTest.aPage(
                        TranzilaTranmode.A, new LinkedHashMap<>(), null),
                    TranzilaPageCards.thePublishedCard())
                .rawBody())
        .contains("\"txn_type\":\"debit\",\"card_number\"")
        .doesNotContain("verify_mode")
        .doesNotContain("user_defined_fields");
    assertThatThrownBy(
            () ->
                TranzilaPageCharge.callOf(
                    TranzilaBrowserReturnTest.aPage(
                        TranzilaTranmode.K, new LinkedHashMap<>(), null),
                    TranzilaPageCards.thePublishedCard()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("tranmode K records no transaction, so the page charges nothing for it");
  }

  @Test
  void the_page_offers_the_published_card_and_every_seeded_card_that_carries_a_token() {
    TranzilaWiring wired = new TranzilaWiring();
    seed(wired, "seeded", new FixtureCard("mastercard", "5100", 3, 2033, "credit"), "tokseeded1");
    seed(wired, "no-token", new FixtureCard("visa", "4000", 3, 2033, "credit"), null);
    TranzilaPageCards cards = new TranzilaPageCards(wired.fixtures);

    assertThat(cards.asPayerCards(TranzilaWiring.RUN))
        .containsExactly(
            new PayerCard(
                "published-9022", "Visa ending 9022, the card tranzila publishes as a test card"),
            new PayerCard("seeded", "mastercard ending 5100"));
    assertThat(cards.resolve(TranzilaWiring.RUN, "seeded"))
        .hasValueSatisfying(
            seeded -> {
              assertThat(seeded.token()).isEqualTo("tokseeded1");
              assertThat(seeded.expiryMonth()).isEqualTo(3);
              assertThat(seeded.expiryYear()).isEqualTo(2033);
              assertThat(seeded.card())
                  .isEqualTo(new TranzilaCard("xxxxxxxxxxxx5100", TranzilaCard.MASTERCARD, 3, 33));
            });
    assertThat(cards.resolve(TranzilaWiring.RUN, "no-token")).isEmpty();
    assertThat(cards.aliasesOffered(TranzilaWiring.RUN)).isEqualTo("published-9022, seeded");
    assertThat(TranzilaPageCards.thePublishedCardAsCharged())
        .isEqualTo(
            new TranzilaCard(
                "xxxxxxxxxxxx9022",
                TranzilaCard.VISA,
                12,
                30,
                TranzilaThreeDSecureOutcome.AUTHENTICATED));
    assertThat(
            new TranzilaPayerCards(wired.fixtures)
                .holdingTheToken(TranzilaWiring.RUN, "t5d55d2922ca4029022"))
        .as("the published card is the page's own and is charged only through it")
        .isEmpty();
  }

  @Test
  void only_the_published_duplicate_message_marks_an_answer_as_a_duplicate() {
    TranzilaTransaction original =
        TranzilaBrowserReturnTest.aDebit("000", "0587923", "tok12345678");

    assertThat(
            TranzilaAnswers.isADuplicate(
                TranzilaAnswers.aDuplicateOf(original, TranzilaJson.object(Map.of()))))
        .isTrue();
    assertThat(
            TranzilaAnswers.isADuplicate(
                TranzilaAnswers.approved(original, TranzilaJson.object(Map.of()))))
        .isFalse();
    assertThat(TranzilaAnswers.isADuplicate(TranzilaAnswers.declined(original))).isFalse();
  }

  @Test
  void a_page_answer_carries_its_defences_beside_the_headers_of_its_delivery() {
    TranzilaApiResponse answered =
        new TranzilaApiResponse(
            TranzilaWiring.RUN,
            new RenderedResponse(200, "text/html", new byte[] {1}).asDelivered(),
            ProviderOutcome.SUCCESS,
            null,
            Map.of("X-One", List.of("1")));
    TranzilaApiResponse plain =
        new TranzilaApiResponse(
            TranzilaWiring.RUN,
            new RenderedResponse(200, "text/html", new byte[] {1}).asDelivered(),
            ProviderOutcome.SUCCESS,
            null);

    assertThat(answered.headers()).containsEntry("X-One", List.of("1")).containsKey("Content-Type");
    assertThat(answered.headers()).isUnmodifiable();
    assertThat(plain.onEveryAnswer()).isEmpty();
  }

  @Test
  void a_page_call_reads_the_first_value_of_a_parameter_and_cannot_be_changed() {
    List<String> sent = new ArrayList<>(List.of("10", "11"));
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("sum", sent);
    TranzilaPageCall call =
        new TranzilaPageCall(
            "GET", InboundRoute.notAliased("/p"), Map.of("H", List.of("v")), parameters);
    sent.add("12");

    assertThat(call.parameter("sum")).isEqualTo("10");
    assertThat(call.parameter("currency")).isNull();
    assertThat(call.parameters().get("sum")).containsExactly("10", "11");
    assertThat(call.path()).isEqualTo("/p");
    assertThat(call.headers()).isUnmodifiable();
  }

  @Test
  void the_handshake_and_the_page_answer_their_own_faults_and_the_card_api_keeps_its_schema() {
    ProviderWireAnswer.InThisProvidersOwnWords handshakeUnknown =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            TranzilaWireFaults.theAnswerTo(
                WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/v2/handshake/create/x");
    ProviderWireAnswer.InThisProvidersOwnWords handshakeMethod =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            TranzilaWireFaults.theAnswerTo(
                WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER, "/v2/handshake/create");
    ProviderWireAnswer.InThisProvidersOwnWords pageUnknown =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            TranzilaWireFaults.theAnswerTo(
                WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/tranzila/directng/x");
    ProviderWireAnswer.InThisProvidersOwnWords pageMethod =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            TranzilaWireFaults.theAnswerTo(
                WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER,
                "/tranzila/directng/t/iframenew.php");

    assertThat(handshakeUnknown.httpStatus()).isEqualTo(404);
    assertThat(handshakeUnknown.contentType()).isEqualTo("application/json");
    assertThat(new String(handshakeUnknown.body(), StandardCharsets.UTF_8))
        .isEqualTo("{\"error_code\":404,\"message\":\"The specified resource was not found\"}");
    assertThat(handshakeMethod.httpStatus()).isEqualTo(405);
    assertThat(new String(handshakeMethod.body(), StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"error_code\":405,\"message\":\"This address does not answer this HTTP method\"}");
    assertThat(pageUnknown.headers())
        .containsEntry(
            "Content-Security-Policy",
            "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'")
        .containsEntry("Cache-Control", "no-store");
    assertThat(handshakeUnknown.headers()).isEmpty();
    assertThat(pageUnknown.httpStatus()).isEqualTo(404);
    assertThat(pageUnknown.contentType()).isEqualTo("text/html; charset=utf-8");
    assertThat(new String(pageUnknown.body(), StandardCharsets.UTF_8))
        .contains(TranzilaDirectNgPage.NO_SUCH_ADDRESS);
    assertThat(pageMethod.httpStatus()).isEqualTo(405);
    assertThat(new String(pageMethod.body(), StandardCharsets.UTF_8))
        .contains(TranzilaDirectNgPage.escaped(TranzilaDirectNgPage.NO_SUCH_METHOD));
    assertThat(TranzilaDirectNgPage.NO_SUCH_METHOD)
        .endsWith(
            "takes GET or POST, the payer's page takes GET and the payer's submission on it POST,"
                + " and a wallet sheet takes GET only.");
    assertThat(pageMethod.headers()).containsEntry("Cache-Control", "no-store");
    assertThat(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    TranzilaWireFaults.theAnswerTo(
                        WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/v1/transaction/x"))
                .contentType())
        .isEqualTo("application/json");
    assertThat(
            TranzilaWireFaults.theAnswerTo(
                WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, "/v2/handshake/create"))
        .isEqualTo(
            new ProviderWireAnswer.NothingThisContractPublishes(
                TranzilaWireFaults.WHAT_THE_HANDSHAKE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT));
    assertThat(
            TranzilaWireFaults.theAnswerTo(
                WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, "/tranzila/directng/x"))
        .isEqualTo(
            new ProviderWireAnswer.NothingThisContractPublishes(
                TranzilaWireFaults.WHAT_THE_PAGE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT));
  }

  @Test
  void pairing_publishes_the_api_its_second_version_and_the_payment_page_host() {
    assertThat(TranzilaPairing.PAIRING.settings())
        .extracting(setting -> setting.name() + " " + setting.value())
        .startsWith(
            "TRANZILA_API_BASE_URL TheProviderBaseUrl[]",
            "TRANZILA_API_V2_BASE_URL AnAddressOfThisSimulator[path=/v2]",
            "TRANZILA_DIRECTNG_BASE_URL AnAddressOfThisSimulator[path=/tranzila/directng]");
  }

  @Test
  void a_page_reports_the_operation_it_was_opened_for_and_forgets_no_card_when_none_was_saved() {
    TranzilaPage page =
        TranzilaBrowserReturnTest.aPage(TranzilaTranmode.VK, new LinkedHashMap<>(), null);

    TranzilaPage left =
        page.settledAs(PayerOutcome.ABANDONED, null, null, null, null, TranzilaWiring.NOW);

    assertThat(page.theOperation()).isEqualTo("tranmode VK");
    assertThat(left.cardMask()).isNull();
    assertThat(left.cardType()).isNull();
    assertThat(left.isSettled()).isTrue();
    assertThat(page.isSettled()).isFalse();
    assertThat(page.echoed()).isUnmodifiable();
  }

  private static void seed(
      TranzilaWiring wired,
      String alias,
      FixtureCard card,
      @org.jspecify.annotations.Nullable String token) {
    wired.fixtures.seed(
        new NewPaymentMethodFixture(
            TranzilaApi.TRANZILA,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            "owner",
            PaymentMethodKind.CARD,
            alias,
            card,
            token == null ? Map.of() : Map.of("token", token),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
  }
}
