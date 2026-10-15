package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TranzilaDirectNgTest {

  private static final String SHOP_OK = "https://shop.example/ok";

  private static final String SHOP_FAIL = "https://shop.example/fail";

  private static final String NOTIFY = "https://shop.example/notify";

  private static final String THE_PUBLISHED_CARD = "published-9022";

  private static final String THE_PUBLISHED_TOKEN = "t5d55d2922ca4029022";

  private static final String A_THTK = "w" + "a".repeat(41);

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void a_page_opened_with_the_published_parameters_offers_the_cards_and_leaves_the_page_waiting() {
    TranzilaApiResponse answered = open(parameters("10.53", "A", "pdesc", "Two seats"));

    TranzilaPage page = thePage();
    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.providerObjectId()).isEqualTo(page.pageId());
    assertThat(answered.headers().get("Content-Security-Policy"))
        .containsExactly("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    assertThat(answered.headers().get("Content-Type")).containsExactly("text/html; charset=utf-8");
    assertThat(bodyOf(answered))
        .contains("action=\"/tranzila/directng/myterminal/payer/" + page.pageId() + "\"")
        .contains("<option value=\"published-9022\">")
        .contains("<dd>10.53 ILS</dd>")
        .contains("<dd>Two seats</dd>")
        .contains("<button type=\"submit\" name=\"outcome\" value=\"PAID\">Pay</button>")
        .contains(
            "<button type=\"submit\" class=\"secondary\" name=\"outcome\""
                + " value=\"ABANDONED\">Leave without paying</button>");
    assertThat(page.pageId()).matches("directng_[0-9a-f]{24}");
    assertThat(page.terminalName()).isEqualTo("myterminal");
    assertThat(page.accountId()).isEqualTo(TranzilaWiring.ACCOUNT);
    assertThat(page.tranmode()).isEqualTo(TranzilaTranmode.A);
    assertThat(page.successUrl()).isEqualTo(SHOP_OK);
    assertThat(page.failUrl()).isEqualTo(SHOP_FAIL);
    assertThat(page.notifyUrl()).isNull();
    assertThat(page.echoed())
        .containsExactly(
            Map.entry("success_url_address", SHOP_OK),
            Map.entry("fail_url_address", SHOP_FAIL),
            Map.entry("pdesc", "Two seats"));
    assertThat(page.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(page.createdAt()).isEqualTo(TranzilaWiring.NOW);
    assertThat(page.isSettled()).isFalse();
    AuditRecord inbound = theRow(AuditDirection.INBOUND_REQUEST);
    AuditRecord outbound = theRow(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(inbound.resolutionRule()).isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
    assertThat(inbound.accountId()).isEqualTo(TranzilaWiring.ACCOUNT);
    assertThat(inbound.sanitizedBody()).containsEntry("pdesc", List.of("Two seats"));
    assertThat(inbound.path()).isEqualTo("/tranzila/directng/myterminal/iframenew.php");
    assertThat(outbound.httpStatus()).isEqualTo(Integer.valueOf(200));
    assertThat(outbound.providerObjectId()).isEqualTo(page.pageId());
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void the_payers_national_id_travels_back_to_the_shop_and_is_withheld_from_every_trail_row() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    open(parameters("10", "A", "myid", "123456789", "orderid", "SHOP-1"));
    AuditRecord opened = theRow(AuditDirection.INBOUND_REQUEST);

    TranzilaApiResponse answered = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(opened.sanitizedBody())
        .containsEntry("myid", List.of("[redacted]"))
        .containsEntry("orderid", List.of("SHOP-1"));
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).sanitizedBody())
        .containsEntry("myid", List.of("[redacted]"))
        .containsEntry("orderid", List.of("SHOP-1"));
    assertThat(bodyOf(answered))
        .contains("<input type=\"hidden\" name=\"myid\" value=\"123456789\">")
        .contains("<input type=\"hidden\" name=\"orderid\" value=\"SHOP-1\">");
    assertThat(wired.posted).singleElement().asString().contains("&myid=123456789&orderid=SHOP-1&");
    assertThat(
            wired
                .deliveries
                .of(TranzilaApi.TRANZILA, TranzilaWiring.RUN)
                .get(0)
                .payload()
                .sanitizedBody())
        .containsEntry("myid", List.of("[redacted]"));
  }

  @Test
  void a_token_a_k_page_returned_charges_on_the_api_of_its_run_and_nowhere_else() {
    open(parameters("1", "K"));
    pay(thePage().pageId(), THE_PUBLISHED_CARD);
    TranzilaWiring anotherRun = new TranzilaWiring();

    String charged =
        wired.answered(TranzilaWire.withCard("\"txn_type\":\"debit\"", THE_PUBLISHED_TOKEN, "5"));
    String refused =
        anotherRun.answered(
            TranzilaWire.withCard("\"txn_type\":\"debit\"", THE_PUBLISHED_TOKEN, "5"));

    assertThat(charged)
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"token\":\"t5d55d2922ca4029022\"")
        .contains("\"card_mask\":\"xxxxxxxxxxxx9022\"")
        .contains("\"expiry_month\":7");
    assertThat(refused)
        .isEqualTo("{\"error_code\":20111,\"message\":\"Provided token check failure\"}");
  }

  @Test
  void a_return_field_a_shop_forged_as_a_parameter_comes_back_with_the_real_value() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    String[] forged = {
      "Response", "999",
      "TranzilaTK", "forgedtoken1",
      "ccno", "0000",
      "supplier", "another",
      "index", "1",
      "ConfirmationCode", "0000000",
      "orderid", "SHOP-1"
    };
    open(parameters("10", "A", forged));
    pay(thePage().pageId(), THE_PUBLISHED_CARD);
    TranzilaTransaction approved = wired.theLastStored();
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    open(parameters("10", "A", forged));

    TranzilaApiResponse declined = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(wired.posted.get(0))
        .contains("supplier=myterminal&")
        .contains("&Response=000&")
        .contains("&orderid=SHOP-1&")
        .contains("&index=" + approved.transactionIdAsText() + "&")
        .contains("&ccno=9022&")
        .contains("&TranzilaTK=t5d55d2922ca4029022&")
        .endsWith("&ConfirmationCode=" + approved.authNumber())
        .doesNotContain("999")
        .doesNotContain("forgedtoken1")
        .doesNotContain("another")
        .doesNotContain("ConfirmationCode=0000000");
    assertThat(bodyOf(declined))
        .contains("name=\"Response\" value=\"004\"")
        .contains("name=\"supplier\" value=\"myterminal\"")
        .contains("name=\"ccno\" value=\"9022\"")
        .contains("name=\"orderid\" value=\"SHOP-1\"")
        .doesNotContain("TranzilaTK")
        .doesNotContain("ConfirmationCode")
        .doesNotContain("999")
        .doesNotContain("another");
    assertThat(wired.posted.get(1))
        .contains("&Response=004&")
        .doesNotContain("TranzilaTK")
        .doesNotContain("ConfirmationCode")
        .doesNotContain("index=1&");
  }

  @Test
  void the_page_shown_without_a_return_address_prints_the_payers_own_id_and_withholds_the_token() {
    open(
        Map.of(
            "sum", List.of("5"),
            "currency", List.of("1"),
            "tranmode", List.of("A"),
            "myid", List.of("123456789")));

    TranzilaApiResponse answered = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(bodyOf(answered))
        .contains("<dt>myid</dt><dd>123456789</dd>")
        .contains("<dt>TranzilaTK</dt><dd>[redacted]</dd>");
  }

  @Test
  void a_page_charge_under_a_dc_disable_is_logged_with_the_token_and_the_payers_id_withheld() {
    open(parameters("30", "A", "DCdisable", "order-9", "myid", "123456789"));
    open(parameters("30", "A", "DCdisable", "order-9", "myid", "123456789"));
    pay(wired.pages.all().get(0).pageId(), THE_PUBLISHED_CARD);
    pay(wired.pages.all().get(1).pageId(), THE_PUBLISHED_CARD);

    assertThat(wired.exchanges.everythingItObserved())
        .extracting(
            exchange ->
                exchange.keyName()
                    + " "
                    + exchange.keyValue()
                    + " "
                    + exchange.verdict()
                    + " "
                    + exchange.httpStatus())
        .containsExactly(
            "DCdisable debit order-9 FIRST_REQUEST 200",
            "DCdisable debit order-9 DUPLICATE_KEY_REFUSAL 200");
    assertThat(wired.exchanges.everythingItObserved())
        .allSatisfy(
            exchange ->
                assertThat(String.valueOf(exchange.body()))
                    .doesNotContain(THE_PUBLISHED_TOKEN)
                    .doesNotContain("123456789"));
    assertThat(wired.exchanges.everythingItObserved().get(0).body())
        .contains("\"TranzilaTK\":[\"[redacted]\"]")
        .contains("\"myid\":[\"[redacted]\"]");
  }

  @Test
  void a_page_charge_under_a_dc_disable_is_kept_as_the_return_and_the_page_the_payer_got() {
    open(parameters("30", "A", "DCdisable", "order-10", "orderid", "SHOP-1"));
    open(parameters("30", "A", "DCdisable", "order-10", "orderid", "SHOP-1"));
    pay(wired.pages.all().get(0).pageId(), THE_PUBLISHED_CARD);
    pay(wired.pages.all().get(1).pageId(), THE_PUBLISHED_CARD);

    List<RecordedKeyedRequest> kept = wired.exchanges.everythingItObserved();

    assertThat(kept.get(0).body())
        .startsWith("{\"supplier\":[\"myterminal\"],\"sum\":[\"30\"],\"currency\":[\"1\"],")
        .contains("\"Response\":[\"000\"]")
        .contains("\"orderid\":[\"SHOP-1\"]")
        .contains("\"transaction_id\":[\"10000001\"]")
        .contains("\"TranzilaTK\":[\"[redacted]\"]")
        .doesNotContain("original_request")
        .doesNotContain("error_code");
    assertThat(kept.get(1).body())
        .isEqualTo(
            "{\"message\":[\"This payment has already been paid. Please contact customer"
                + " service.\"]}");
  }

  @Test
  void the_payers_submission_is_kept_on_the_trail_as_it_arrived_with_the_page_it_names() {
    open(parameters("10", "A"));
    String pageId = thePage().pageId();

    pay(pageId, THE_PUBLISHED_CARD);

    AuditRecord submitted =
        wired.audit.all().stream()
            .filter(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
            .reduce((first, last) -> last)
            .orElseThrow();
    assertThat(submitted.path()).isEqualTo("/tranzila/directng/myterminal/payer/" + pageId);
    assertThat(submitted.method()).isEqualTo("POST");
    assertThat(submitted.accountId()).isEqualTo(TranzilaWiring.ACCOUNT);
    assertThat(submitted.resolutionRule()).isEqualTo(TestRunResolutionRule.OBJECT_CORRELATION);
    assertThat(submitted.sanitizedBody())
        .containsEntry("card", List.of(THE_PUBLISHED_CARD))
        .containsEntry("outcome", List.of("PAID"));
  }

  @Test
  void a_terminal_no_run_configures_is_answered_404_and_opens_nothing() {
    TranzilaApiResponse answered =
        wired.directNg.open(
            TranzilaPageCall.of("GET", "/tranzila/directng/nobody/iframenew.php", Map.of()),
            "nobody");

    assertThat(answered.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(answered)).contains(TranzilaDirectNgPage.NO_SUCH_TERMINAL.substring(0, 40));
    assertThat(answered.headers().get("Content-Security-Policy")).isNotEmpty();
    assertThat(wired.pages.all()).isEmpty();
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).refusal())
        .isEqualTo(RefusalSubject.noObjectWasNamed(TranzilaDirectNgPage.NO_SUCH_TERMINAL));
  }

  @Test
  void parameters_the_page_does_not_serve_are_answered_400_in_the_simulators_words() {
    TranzilaApiResponse answered = open(parameters("10", "J"));

    assertThat(answered.httpStatus()).isEqualTo(400);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(answered)).contains("tranmode J starts a standing order");
    assertThat(wired.pages.all()).isEmpty();
  }

  @Test
  void a_thtk_locking_this_terminal_and_sum_opens_the_page_with_the_params_it_carried() {
    aHandshake(A_THTK, "150", "{\"order_id\":\"ORD-1\"}", TranzilaWiring.NOW.plusSeconds(60));

    TranzilaApiResponse answered = open(parameters("150.00", "A", "thtk", A_THTK));

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(thePage().thtk()).isEqualTo(A_THTK);
    assertThat(thePage().requestParams()).isEqualTo("{\"order_id\":\"ORD-1\"}");
  }

  @Test
  void a_thtk_that_locks_another_sum_has_expired_or_was_never_made_is_an_illegal_operation() {
    aHandshake(A_THTK, "150", null, TranzilaWiring.NOW.plusSeconds(60));
    String expired = "w" + "b".repeat(41);
    aHandshake(expired, "10", null, TranzilaWiring.NOW);

    List<TranzilaApiResponse> refused =
        List.of(
            open(parameters("149.99", "A", "thtk", A_THTK)),
            open(parameters("10", "A", "thtk", expired)),
            open(parameters("10", "A", "thtk", "w" + "c".repeat(41))));

    assertThat(refused)
        .allSatisfy(
            answered -> {
              assertThat(answered.httpStatus()).isEqualTo(400);
              assertThat(bodyOf(answered)).contains("Illegal Operation 912791");
            });
    assertThat(wired.pages.all()).isEmpty();
  }

  @Test
  void a_notify_address_the_account_does_not_configure_opens_no_page() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);

    TranzilaApiResponse refused =
        open(parameters("10", "A", "notify_url_address", "https://elsewhere.example/n"));
    TranzilaApiResponse opened = open(parameters("10", "A", "notify_url_address", NOTIFY));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("notify_url_address is not among the callback targets");
    assertThat(opened.httpStatus()).isEqualTo(200);
    assertThat(wired.pages.all())
        .singleElement()
        .extracting(TranzilaPage::notifyUrl)
        .isEqualTo(NOTIFY);
  }

  @Test
  void a_page_id_already_taken_is_drawn_again_and_five_taken_in_a_row_fail_the_simulator() {
    wired.pages.theNextIdsAreTaken(4);
    open(parameters("10", "A"));
    wired.pages.theNextIdsAreTaken(5);

    assertThat(wired.pages.all()).hasSize(1);
    assertThatThrownBy(() -> open(parameters("10", "A")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("no Tranzila page id could be claimed in 5 attempts");
  }

  @Test
  void a_payer_who_pays_a_standard_charge_is_posted_back_to_the_success_address_and_notified() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    open(parameters("10.53", "A", "notify_url_address", NOTIFY, "contact", "Dana"));
    String pageId = thePage().pageId();

    TranzilaApiResponse answered = pay(pageId, THE_PUBLISHED_CARD);

    TranzilaTransaction made = wired.theLastStored();
    assertThat(made.type()).isEqualTo(TranzilaTxnType.DEBIT);
    assertThat(made.processorResponseCode()).isEqualTo("000");
    assertThat(made.amount()).isEqualTo("10.53");
    assertThat(made.currencyCode()).isEqualTo("ILS");
    assertThat(made.token()).isEqualTo(THE_PUBLISHED_TOKEN);
    assertThat(made.card().cardMask()).isEqualTo("xxxxxxxxxxxx9022");
    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.providerObjectId()).isEqualTo(made.transactionIdAsText());
    assertThat(answered.headers().get("Content-Security-Policy"))
        .containsExactly(
            "default-src 'none'; style-src 'unsafe-inline';"
                + " script-src 'sha256-Zd9Ppkm6KJvfEle1ogzptKkD+DOonO1DYBPhi02doj8='");
    assertThat(bodyOf(answered))
        .contains("<form method=\"post\" action=\"https://shop.example/ok\">")
        .contains("<input type=\"hidden\" name=\"Response\" value=\"000\">")
        .contains("<input type=\"hidden\" name=\"TranzilaTK\" value=\"t5d55d2922ca4029022\">")
        .contains("<script>HTMLFormElement.prototype.submit.call(document.forms[0]);</script>");
    TranzilaPage settled = thePage();
    assertThat(settled.outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(settled.transactionId()).isEqualTo(Long.valueOf(made.transactionId()));
    assertThat(settled.settledAt()).isEqualTo(TranzilaWiring.NOW);
    assertThat(wired.posted)
        .singleElement()
        .asString()
        .startsWith(
            NOTIFY
                + " supplier=myterminal&sum=10.53&currency=1&Response=000"
                + "&success_url_address=https%3A%2F%2Fshop.example%2Fok"
                + "&fail_url_address=https%3A%2F%2Fshop.example%2Ffail"
                + "&notify_url_address=https%3A%2F%2Fshop.example%2Fnotify&contact=Dana"
                + "&transaction_id=")
        .contains("&TranzilaTK=t5d55d2922ca4029022")
        .endsWith("&txn_type=debit&tranmode=A&ConfirmationCode=" + made.authNumber());
    AuditRecord outbound = theRow(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(outbound.sanitizedBody()).containsEntry("TranzilaTK", List.of("[redacted]"));
    assertThat(outbound.sanitizedBody()).containsEntry("Response", List.of("000"));
    assertThat(outbound.resolutionRule()).isEqualTo(TestRunResolutionRule.OBJECT_CORRELATION);
  }

  @Test
  void with_no_return_address_the_page_shows_the_result_itself_and_withholds_the_token() {
    open(Map.of("sum", List.of("5"), "currency", List.of("1"), "tranmode", List.of("A")));

    TranzilaApiResponse answered = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(answered.headers().get("Content-Security-Policy"))
        .containsExactly("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    assertThat(bodyOf(answered))
        .contains(TranzilaDirectNgPage.APPROVED)
        .contains("<dt>Response</dt><dd>000</dd>")
        .contains("<dt>TranzilaTK</dt><dd>[redacted]</dd>")
        .doesNotContain(THE_PUBLISHED_TOKEN)
        .doesNotContain("<script");
  }

  @Test
  void a_declined_card_is_posted_back_to_the_fail_address_and_notified_without_a_token() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    open(parameters("10", "A"));

    TranzilaApiResponse answered = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(answered))
        .contains("action=\"https://shop.example/fail\"")
        .contains("name=\"Response\" value=\"004\"")
        .doesNotContain("TranzilaTK");
    assertThat(thePage().outcome()).isEqualTo(PayerOutcome.DECLINED);
    assertThat(wired.posted)
        .singleElement()
        .asString()
        .contains("Response=004")
        .doesNotContain("ConfirmationCode")
        .doesNotContain("TranzilaTK");
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).scenarioId()).isNotNull();
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).refusal())
        .isEqualTo(RefusalSubject.theObject(wired.theLastStored().transactionIdAsText()));
  }

  @Test
  void a_token_only_page_records_no_transaction_and_returns_the_cards_token() {
    wired.configuring(Map.of(TranzilaVerificationApprovalCode.SETTING, "000"));
    open(parameters("1", "K"));

    TranzilaApiResponse answered = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(wired.stored()).isEmpty();
    TranzilaPage saved = thePage();
    assertThat(saved.outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(saved.response()).isEqualTo("000");
    assertThat(saved.token()).isEqualTo(THE_PUBLISHED_TOKEN);
    assertThat(saved.cardMask()).isEqualTo("xxxxxxxxxxxx9022");
    assertThat(saved.cardType()).isEqualTo(Integer.valueOf(2));
    assertThat(saved.transactionId()).isNull();
    assertThat(answered.providerObjectId()).isEqualTo(saved.pageId());
    assertThat(bodyOf(answered))
        .contains("name=\"Response\" value=\"000\"")
        .contains("name=\"TranzilaTK\" value=\"t5d55d2922ca4029022\"")
        .contains("name=\"tranmode\" value=\"K\"")
        .doesNotContain("transaction_id");
  }

  @Test
  void a_j5_and_a_j2_page_verify_the_card_as_the_api_would() {
    open(parameters("100", "VK"));
    pay(thePage().pageId(), THE_PUBLISHED_CARD);
    TranzilaTransaction j5 = wired.theLastStored();
    open(parameters("1", "N"));
    pay(wired.pages.all().get(1).pageId(), THE_PUBLISHED_CARD);
    TranzilaTransaction j2 = wired.theLastStored();

    assertThat(j5.isAJ5()).isTrue();
    assertThat(j5.processorResponseCode()).isEqualTo("777");
    assertThat(j5.holdExpiresAt()).isEqualTo(Instant.parse("2026-10-20T12:00:00Z"));
    assertThat(j2.isAJ2()).isTrue();
  }

  @Test
  void a_payer_who_leaves_ends_the_page_with_no_transaction_and_no_notify() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    open(parameters("10", "A"));

    TranzilaApiResponse answered =
        submit(thePage().pageId(), Map.of("outcome", List.of("ABANDONED")));

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(answered)).contains(TranzilaDirectNgPage.LEFT);
    assertThat(thePage().outcome()).isEqualTo(PayerOutcome.ABANDONED);
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.posted).isEmpty();
  }

  @Test
  void a_card_the_page_does_not_offer_is_refused_and_the_page_stays_open() {
    open(parameters("10", "A"));

    TranzilaApiResponse unknown = pay(thePage().pageId(), "a-card-nobody-seeded");
    TranzilaApiResponse none = submit(thePage().pageId(), Map.of());

    assertThat(unknown.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(unknown))
        .contains("no card this page offers is named a-card-nobody-seeded")
        .contains("The cards a payer may name here are: published-9022");
    assertThat(none.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(none)).contains("no card this page offers is named null");
    assertThat(thePage().isSettled()).isFalse();
  }

  @Test
  void a_seeded_fixture_is_offered_beside_the_published_card_and_charged_by_its_token() {
    seedAFixture("seeded", "amex", "0005", "seededtoken0005");
    open(parameters("3", "A"));

    TranzilaApiResponse page = open(parameters("3", "A"));
    pay(thePage().pageId(), "seeded");

    assertThat(bodyOf(page)).contains("<option value=\"seeded\">amex ending 0005</option>");
    assertThat(wired.theLastStored().token()).isEqualTo("seededtoken0005");
    assertThat(wired.theLastStored().card().cardType()).isEqualTo(TranzilaCard.AMEX);
  }

  @Test
  void a_dc_disable_approved_once_answers_the_published_duplicate_and_charges_nothing_more() {
    open(parameters("30", "A", "DCdisable", "order-77"));
    open(parameters("30", "A", "DCdisable", "order-77"));
    pay(wired.pages.all().get(0).pageId(), THE_PUBLISHED_CARD);

    TranzilaApiResponse again = pay(wired.pages.all().get(1).pageId(), THE_PUBLISHED_CARD);

    assertThat(again.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(again))
        .contains("This payment has already been paid. Please contact customer service.");
    assertThat(wired.stored()).hasSize(1);
    assertThat(wired.stored().get(0).dcDisable()).isEqualTo("order-77");
    assertThat(wired.pages.all().get(1).isSettled()).isFalse();
  }

  @Test
  void a_rule_answering_instead_of_the_charge_is_delivered_and_leaves_the_page_open() {
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));
    open(parameters("10", "A"));

    TranzilaApiResponse answered = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(answered.httpStatus()).isEqualTo(502);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(thePage().isSettled()).isFalse();
    assertThat(wired.stored()).isEmpty();
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).scenarioId()).isNotNull();
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).httpStatus())
        .isEqualTo(Integer.valueOf(502));
  }

  @Test
  void a_rule_disrupting_the_charge_faults_the_return_after_the_charge_was_made() {
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(503)));
    open(parameters("10", "A"));

    TranzilaApiResponse answered = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(answered.httpStatus()).isEqualTo(503);
    assertThat(wired.stored()).hasSize(1);
    assertThat(thePage().outcome()).isEqualTo(PayerOutcome.PAID);
  }

  @Test
  void an_approved_charge_renews_the_handshake_and_a_lapsed_one_refuses_the_payment() {
    aHandshake(A_THTK, "10", null, TranzilaWiring.NOW.plusSeconds(60));
    open(parameters("10", "A", "thtk", A_THTK));
    open(parameters("10", "A", "thtk", A_THTK));
    pay(wired.pages.all().get(0).pageId(), THE_PUBLISHED_CARD);
    TranzilaHandshake renewed = wired.handshakes.all().get(0);
    wired.clock.moveTo(Instant.parse("2026-10-13T12:21:00Z"));

    TranzilaApiResponse lapsed = pay(wired.pages.all().get(1).pageId(), THE_PUBLISHED_CARD);

    assertThat(renewed.expiresAt()).isEqualTo(Instant.parse("2026-10-13T12:20:00Z"));
    assertThat(lapsed.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(lapsed)).contains("Illegal Operation 912791").contains("still completable");
    assertThat(wired.stored()).hasSize(1);
  }

  @Test
  void a_declined_charge_does_not_renew_the_handshake() {
    aHandshake(A_THTK, "10", null, TranzilaWiring.NOW.plusSeconds(60));
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    open(parameters("10", "A", "thtk", A_THTK));

    pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(wired.handshakes.all().get(0).expiresAt())
        .isEqualTo(TranzilaWiring.NOW.plusSeconds(60));
  }

  @Test
  void a_terminal_the_run_no_longer_configures_cannot_be_charged() {
    open(parameters("10", "A"));
    wired.terminals.remove(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT);

    TranzilaApiResponse answered = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(answered.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(answered)).contains("no longer configured in this test run");
  }

  @Test
  void a_notify_target_removed_after_the_page_opened_refuses_the_payment_and_keeps_the_page() {
    TranzilaWiring other = new TranzilaWiring();
    other.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    other.directNg.open(
        TranzilaPageCall.of(
            "POST",
            "/tranzila/directng/myterminal/iframenew.php",
            parameters("10", "A", "notify_url_address", NOTIFY)),
        "myterminal");
    TranzilaPage opened = other.pages.all().get(0);
    wired.pages.open(opened);

    TranzilaApiResponse answered = pay(opened.pageId(), THE_PUBLISHED_CARD);

    assertThat(answered.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(answered))
        .contains("notify_url_address is not among the callback targets")
        .contains("still completable");
  }

  @Test
  void a_page_that_reached_its_outcome_is_answered_410_and_an_unknown_one_404() {
    open(parameters("10", "A"));
    String pageId = thePage().pageId();
    pay(pageId, THE_PUBLISHED_CARD);

    TranzilaApiResponse again = pay(pageId, THE_PUBLISHED_CARD);
    TranzilaApiResponse unknown = pay("directng_" + "f".repeat(24), THE_PUBLISHED_CARD);
    TranzilaApiResponse elsewhere =
        wired.directNg.submit(
            TranzilaPageCall.of("POST", "/x", Map.of()), "anotherterminal", pageId);

    assertThat(again.httpStatus()).isEqualTo(410);
    assertThat(bodyOf(again)).contains(TranzilaDirectNgPage.ALREADY_REACHED);
    assertThat(unknown.httpStatus()).isEqualTo(404);
    assertThat(unknown.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(elsewhere.httpStatus()).isEqualTo(404);
    assertThat(wired.stored()).hasSize(1);
  }

  @Test
  void a_page_another_submission_settled_or_holds_is_reported_as_where_it_stands() {
    open(parameters("10", "A"));
    open(parameters("10", "A"));
    String first = wired.pages.all().get(0).pageId();
    String second = wired.pages.all().get(1).pageId();
    wired.pages.settledByAnotherSubmissionFirst(first);
    wired.pages.heldByAnotherSubmission(second);

    TranzilaApiResponse settled = pay(first, THE_PUBLISHED_CARD);
    TranzilaApiResponse held = pay(second, THE_PUBLISHED_CARD);

    assertThat(settled.httpStatus()).isEqualTo(410);
    assertThat(held.httpStatus()).isEqualTo(409);
    assertThat(bodyOf(held)).contains(TranzilaDirectNgPage.BEING_COMPLETED);
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void a_page_another_submission_held_while_the_payer_left_is_reported_as_being_completed() {
    open(parameters("10", "A"));
    String pageId = thePage().pageId();
    wired.pages.heldByAnotherSubmission(pageId);

    TranzilaApiResponse answered = submit(pageId, Map.of("outcome", List.of("ABANDONED")));

    assertThat(answered.httpStatus()).isEqualTo(409);
  }

  @Test
  void a_screen_offers_the_published_card_and_the_fixtures_and_pays_or_leaves_the_page() {
    seedAFixture("seeded", "visa", "1111", "seededtoken1111");
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    open(parameters("10", "A"));
    open(parameters("1", "K"));
    open(parameters("2", "A"));
    TranzilaPage charged = wired.pages.all().get(0);
    TranzilaPage saved = wired.pages.all().get(1);
    TranzilaPage left = wired.pages.all().get(2);

    HostedCompletionOffer offered = wired.directNg.offeredOn(charged);
    HostedCompletionOutcome paid = complete(charged, PayerOutcome.PAID, "seeded");
    HostedCompletionOutcome token = complete(saved, PayerOutcome.PAID, THE_PUBLISHED_CARD);
    HostedCompletionOutcome gone = complete(left, PayerOutcome.ABANDONED, null);

    assertThat(offered)
        .isEqualTo(
            new HostedCompletionOffer.Offered(
                TranzilaApi.TRANZILA,
                List.of(
                    new PayerCard(
                        THE_PUBLISHED_CARD,
                        "Visa ending 9022, the card tranzila publishes as a test card"),
                    new PayerCard("seeded", "visa ending 1111")),
                TranzilaDirectNg.THE_PAYER_STEPS));
    HostedCompletionOutcome.Completed completed = (HostedCompletionOutcome.Completed) paid;
    assertThat(completed.providerObjectId()).isEqualTo(charged.pageId());
    assertThat(completed.createdObjectId()).isEqualTo(wired.theLastStored().transactionIdAsText());
    assertThat(completed.outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(completed.webhookJobIds()).hasSize(1);
    assertThat(wired.theLastStored().origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(token)
        .isEqualTo(
            new HostedCompletionOutcome.Completed(
                saved.pageId(),
                null,
                PayerOutcome.PAID,
                ((HostedCompletionOutcome.Completed) token).webhookJobIds()));
    assertThat(gone)
        .isEqualTo(
            new HostedCompletionOutcome.Completed(
                left.pageId(), null, PayerOutcome.ABANDONED, List.of()));
    assertThat(wired.directNg.offeredOn(wired.pages.all().get(0)))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                TranzilaApi.TRANZILA, TranzilaDirectNgPage.ALREADY_REACHED));
    assertThat(complete(wired.pages.all().get(0), PayerOutcome.PAID, THE_PUBLISHED_CARD))
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNgPage.ALREADY_REACHED));
  }

  @Test
  void a_screen_reads_each_refusal_of_the_page_in_the_pages_own_words() {
    open(parameters("30", "A", "DCdisable", "k"));
    open(parameters("30", "A", "DCdisable", "k"));
    open(parameters("30", "A"));
    open(parameters("30", "A"));
    open(parameters("30", "A"));
    List<TranzilaPage> pages = wired.pages.all();
    pay(pages.get(0).pageId(), THE_PUBLISHED_CARD);
    wired.pages.heldByAnotherSubmission(pages.get(3).pageId());
    wired.pages.settledByAnotherSubmissionFirst(pages.get(4).pageId());

    assertThat(complete(pages.get(1), PayerOutcome.PAID, THE_PUBLISHED_CARD))
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNgPage.THE_DUPLICATE));
    assertThat(complete(pages.get(2), PayerOutcome.PAID, "nobody"))
        .isInstanceOfSatisfying(
            HostedCompletionOutcome.RefusedByTheProvider.class,
            refused -> assertThat(refused.inItsOwnWords()).contains("no card this page offers"));
    assertThat(complete(pages.get(3), PayerOutcome.PAID, THE_PUBLISHED_CARD))
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNgPage.BEING_COMPLETED));
    assertThat(complete(pages.get(4), PayerOutcome.PAID, THE_PUBLISHED_CARD))
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNgPage.ALREADY_REACHED));
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));
    assertThat(complete(pages.get(2), PayerOutcome.PAID, THE_PUBLISHED_CARD))
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(TranzilaDirectNg.ANSWERED_INSTEAD));
  }

  @Test
  void a_page_removed_while_it_was_being_completed_is_no_object_any_more() {
    open(parameters("10", "A"));
    TranzilaPage page = thePage();
    wired.pages.heldByAnotherSubmission(page.pageId());
    wired.pages.clearAll();

    assertThat(complete(page, PayerOutcome.PAID, THE_PUBLISHED_CARD))
        .isEqualTo(new HostedCompletionOutcome.NoSuchObject());
  }

  @Test
  void a_terminal_two_runs_configure_is_resolved_by_the_run_its_thtk_belongs_to() {
    TestRunId other = TestRunId.of("tranzila-other");
    wired.terminals.with(TranzilaDoubles.terminal(other, "account-2", TranzilaWiring.TERMINAL));
    aHandshake(A_THTK, "10", null, TranzilaWiring.NOW.plusSeconds(60));

    TranzilaApiResponse ambiguous = open(parameters("10", "A"));
    TranzilaApiResponse resolved = open(parameters("10", "A", "thtk", A_THTK));
    TranzilaApiResponse unresolved = open(parameters("10", "A", "thtk", "w" + "d".repeat(41)));

    assertThat(ambiguous.httpStatus()).isEqualTo(404);
    assertThat(ambiguous.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(resolved.httpStatus()).isEqualTo(200);
    assertThat(resolved.testRunId()).isEqualTo(TranzilaWiring.RUN);
    assertThat(unresolved.httpStatus()).isEqualTo(404);
  }

  private void aHandshake(
      String thtk, String sum, @org.jspecify.annotations.Nullable String params, Instant expires) {
    wired.handshakes.insert(
        new TranzilaHandshake(
            thtk,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            sum,
            params,
            TranzilaWiring.NOW.minusSeconds(1),
            expires));
  }

  private void seedAFixture(String alias, String brand, String last4, String token) {
    wired.fixtures.seed(
        new NewPaymentMethodFixture(
            TranzilaApi.TRANZILA,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            "owner",
            PaymentMethodKind.CARD,
            alias,
            new FixtureCard(brand, last4, 2, 2031, "credit"),
            Map.of("token", token),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
  }

  private HostedCompletionOutcome complete(
      TranzilaPage page, PayerOutcome outcome, @org.jspecify.annotations.Nullable String card) {
    return wired.directNg.complete(
        page,
        new HostedCompletionRequest(
            page.testRunId(), page.pageId(), outcome, card, ObjectOrigin.UI));
  }

  private TranzilaPage thePage() {
    List<TranzilaPage> pages = wired.pages.all();
    return pages.get(pages.size() - 1);
  }

  private AuditRecord theRow(AuditDirection direction) {
    return wired.audit.all().stream()
        .filter(record -> record.direction() == direction)
        .reduce((first, last) -> last)
        .orElseThrow();
  }

  private TranzilaApiResponse open(Map<String, List<String>> parameters) {
    return wired.directNg.open(
        TranzilaPageCall.of("POST", "/tranzila/directng/myterminal/iframenew.php", parameters),
        TranzilaWiring.TERMINAL);
  }

  private TranzilaApiResponse pay(String pageId, String card) {
    return submit(pageId, Map.of("outcome", List.of("PAID"), "card", List.of(card)));
  }

  private TranzilaApiResponse submit(String pageId, Map<String, List<String>> form) {
    return wired.directNg.submit(
        TranzilaPageCall.of(
            "POST", TranzilaDirectNgPage.payerPathOf(TranzilaWiring.TERMINAL, pageId), form),
        TranzilaWiring.TERMINAL,
        pageId);
  }

  private static Map<String, List<String>> parameters(
      String sum, String tranmode, String... extra) {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    parameters.put("sum", List.of(sum));
    parameters.put("currency", List.of("1"));
    parameters.put("tranmode", List.of(tranmode));
    parameters.put("success_url_address", List.of(SHOP_OK));
    parameters.put("fail_url_address", List.of(SHOP_FAIL));
    for (int at = 0; at < extra.length; at += 2) {
      parameters.put(extra[at], List.of(extra[at + 1]));
    }
    return parameters;
  }

  private static String bodyOf(TranzilaApiResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }
}
