package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaWalletTest {

  private static final String SHOP_OK = "https://shop.example/ok";

  private static final String NOTIFY = "https://shop.example/notify";

  private static final String THE_PUBLISHED_CARD = "published-9022";

  private static final String THE_PUBLISHED_TOKEN = "t5d55d2922ca4029022";

  private static final String THE_FIRST_WALLET_TOKEN = "t000000000000019022";

  private static final String THE_TOKEN_CHECK_FAILURE =
      "{\"error_code\":20111,\"message\":\"Provided token check failure\"}";

  private static final String A_THTK = "w" + "a".repeat(41);

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void
      each_wallet_names_its_page_parameter_its_published_broker_and_whether_its_token_charges_again() {
    assertThat(TranzilaWallet.APPLE_PAY.parameter()).isEqualTo("apple_pay");
    assertThat(TranzilaWallet.APPLE_PAY.broker()).isEqualTo("apple_pay");
    assertThat(TranzilaWallet.APPLE_PAY.label()).isEqualTo("Apple Pay");
    assertThat(TranzilaWallet.APPLE_PAY.itsTokenChargesAgain()).isFalse();
    assertThat(TranzilaWallet.GOOGLE_PAY.parameter()).isEqualTo("google_pay");
    assertThat(TranzilaWallet.GOOGLE_PAY.broker()).isEqualTo("GOOGLE_PAY");
    assertThat(TranzilaWallet.GOOGLE_PAY.label()).isEqualTo("Google Pay");
    assertThat(TranzilaWallet.GOOGLE_PAY.itsTokenChargesAgain()).isTrue();
    assertThat(TranzilaWallet.named("apple_pay")).contains(TranzilaWallet.APPLE_PAY);
    assertThat(TranzilaWallet.named("google_pay")).contains(TranzilaWallet.GOOGLE_PAY);
    assertThat(TranzilaWallet.named("GOOGLE_PAY")).isEmpty();
    assertThat(TranzilaWallet.named("bit_pay")).isEmpty();
    assertThat(TranzilaWallet.ofTheBroker("apple_pay")).contains(TranzilaWallet.APPLE_PAY);
    assertThat(TranzilaWallet.ofTheBroker("GOOGLE_PAY")).contains(TranzilaWallet.GOOGLE_PAY);
    assertThat(TranzilaWallet.ofTheBroker("google_pay")).isEmpty();
    assertThat(TranzilaWallet.ofTheBroker("ENDEAVOUR")).isEmpty();
  }

  @Test
  void a_wallet_is_offered_when_its_parameter_is_1_and_the_tranmode_is_one_a_wallet_pays() {
    for (String paid : List.of("A", "V", "AK", "VK")) {
      open(parameters("5", paid, "apple_pay", "1", "google_pay", "1"));
      assertThat(TranzilaWallet.offeredOn(thePage()))
          .as(paid)
          .containsExactly(TranzilaWallet.APPLE_PAY, TranzilaWallet.GOOGLE_PAY);
    }
    for (String unpaid : List.of("N", "NK", "K")) {
      open(parameters("5", unpaid, "apple_pay", "1", "google_pay", "1"));
      assertThat(TranzilaWallet.offeredOn(thePage())).as(unpaid).isEmpty();
    }
    open(parameters("5", "A", "apple_pay", "0", "google_pay", "1"));
    assertThat(TranzilaWallet.offeredOn(thePage())).containsExactly(TranzilaWallet.GOOGLE_PAY);
    open(parameters("5", "A", "apple_pay", "1"));
    assertThat(TranzilaWallet.offeredOn(thePage())).containsExactly(TranzilaWallet.APPLE_PAY);
    open(parameters("5", "A"));
    assertThat(TranzilaWallet.offeredOn(thePage())).isEmpty();
  }

  @Test
  void the_page_shows_a_stand_in_button_for_each_wallet_it_offers_and_none_for_the_others() {
    TranzilaApiResponse both = open(parameters("5", "A", "apple_pay", "1", "google_pay", "1"));
    String pageId = thePage().pageId();
    TranzilaApiResponse none = open(parameters("5", "N", "apple_pay", "1", "google_pay", "1"));

    assertThat(bodyOf(both))
        .contains(
            "<p>The wallet buttons stand in for Apple Pay and Google Pay, whose own sheets only"
                + " Apple and Google can show.</p>"
                + "<form method=\"get\" action=\"/tranzila/directng/myterminal/payer/"
                + pageId
                + "/wallet/apple_pay\"><button type=\"submit\" class=\"wallet\">Apple Pay</button>"
                + "</form><form method=\"get\" action=\"/tranzila/directng/myterminal/payer/"
                + pageId
                + "/wallet/google_pay\"><button type=\"submit\" class=\"wallet\">Google Pay"
                + "</button></form></main>");
    assertThat(bodyOf(none))
        .doesNotContain("/wallet/")
        .doesNotContain(TranzilaDirectNgPage.THE_WALLETS_STAND_IN)
        .contains("</form></main>");
    assertThat(TranzilaDirectNgPage.walletPathOf("t1", "p1", TranzilaWallet.GOOGLE_PAY))
        .isEqualTo("/tranzila/directng/t1/payer/p1/wallet/google_pay");
    assertThat(TranzilaDirectNgPage.WALLET_ROUTE)
        .isEqualTo("/tranzila/directng/{terminal}/payer/{pageId}/wallet/{wallet}");
  }

  @Test
  void the_wallet_sheet_offers_the_runs_cards_as_the_card_in_the_wallet_and_a_way_back() {
    open(parameters("12.5", "A", "apple_pay", "1", "pdesc", "Seats"));
    String pageId = thePage().pageId();

    TranzilaApiResponse sheet = sheet(pageId, "apple_pay");

    assertThat(sheet.httpStatus()).isEqualTo(200);
    assertThat(sheet.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(sheet.providerObjectId()).isEqualTo(pageId);
    assertThat(sheet.headers().get("Content-Security-Policy"))
        .containsExactly("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    String payer = "/tranzila/directng/myterminal/payer/" + pageId;
    assertThat(bodyOf(sheet))
        .contains("<title>Apple Pay</title>")
        .contains(
            "<main><h1>Apple Pay</h1><dl><dt>Amount</dt><dd>12.5 ILS</dd><dt>For</dt>"
                + "<dd>Seats</dd></dl><p>This sheet stands in for the wallet&#39;s own. Pick the"
                + " card the wallet holds and confirm: the payment is made with that card, as"
                + " tranzila makes a wallet payment.</p><form method=\"post\" action=\""
                + payer
                + "\"><input type=\"hidden\" name=\"wallet\" value=\"apple_pay\"><label"
                + " for=\"card\">Card in the wallet</label><select id=\"card\" name=\"card\">"
                + "<option value=\"published-9022\">Visa ending 9022, the card tranzila publishes"
                + " as a test card</option></select><button type=\"submit\" name=\"outcome\""
                + " value=\"PAID\">Pay with Apple Pay</button></form><form method=\"get\""
                + " action=\""
                + payer
                + "\"><button type=\"submit\" class=\"secondary\">Cancel</button></form></main>");
    AuditRecord inbound = theRow(AuditDirection.INBOUND_REQUEST);
    assertThat(inbound.path()).isEqualTo(payer + "/wallet/apple_pay");
    assertThat(inbound.method()).isEqualTo("GET");
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).httpStatus())
        .isEqualTo(Integer.valueOf(200));
    assertThat(thePage().isSettled()).isFalse();
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void a_sheet_of_a_wallet_not_served_not_offered_or_of_a_page_that_ended_is_refused() {
    open(parameters("5", "N", "apple_pay", "1"));
    String unpaid = thePage().pageId();
    open(parameters("5", "A", "apple_pay", "1"));
    String paid = thePage().pageId();

    TranzilaApiResponse unserved = sheet(paid, "bit_pay");
    TranzilaApiResponse notOffered = sheet(unpaid, "apple_pay");
    TranzilaApiResponse googleNotAsked = sheet(paid, "google_pay");
    TranzilaApiResponse unknown = sheet("directng_nothing", "apple_pay");
    pay(paid, THE_PUBLISHED_CARD);
    TranzilaApiResponse ended = sheet(paid, "apple_pay");

    assertThat(unserved.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(unserved))
        .contains(TranzilaDirectNgPage.escaped(TranzilaDirectNgPage.NO_SUCH_WALLET));
    assertThat(notOffered.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(notOffered))
        .contains(
            "Apple Pay is not offered on this payment page: the page offers a wallet when it is"
                + " opened with that wallet&#39;s parameter set to 1 and a tranmode of A, V, AK"
                + " or VK, the ones the wallets pay.");
    assertThat(googleNotAsked.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(googleNotAsked)).contains("Google Pay is not offered");
    assertThat(unknown.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(unknown))
        .contains(TranzilaDirectNgPage.escaped(TranzilaDirectNgPage.NO_SUCH_PAGE));
    assertThat(ended.httpStatus()).isEqualTo(410);
    assertThat(bodyOf(ended)).contains(TranzilaDirectNgPage.ALREADY_REACHED);
  }

  @Test
  void the_payers_page_is_shown_again_while_it_waits_and_answered_410_once_it_ended() {
    open(parameters("5", "A", "google_pay", "1"));
    String pageId = thePage().pageId();

    TranzilaApiResponse waiting = shown(pageId);
    pay(pageId, THE_PUBLISHED_CARD);
    TranzilaApiResponse ended = shown(pageId);
    TranzilaApiResponse unknown = shown("directng_nothing");

    assertThat(waiting.httpStatus()).isEqualTo(200);
    assertThat(waiting.providerObjectId()).isEqualTo(pageId);
    assertThat(bodyOf(waiting))
        .contains("action=\"/tranzila/directng/myterminal/payer/" + pageId + "\"")
        .contains("/wallet/google_pay\"")
        .contains("<option value=\"published-9022\">");
    assertThat(ended.httpStatus()).isEqualTo(410);
    assertThat(unknown.httpStatus()).isEqualTo(404);
  }

  @Test
  void an_apple_pay_payment_is_the_card_payment_of_the_tranmode_on_a_token_of_its_own() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, NOTIFY);
    open(parameters("10.53", "A", "apple_pay", "1"));

    TranzilaApiResponse returned = payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);

    TranzilaTransaction made = wired.theLastStored();
    assertThat(made.type()).isEqualTo(TranzilaTxnType.DEBIT);
    assertThat(made.processorResponseCode()).isEqualTo("000");
    assertThat(made.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
    assertThat(made.token()).isEqualTo(THE_FIRST_WALLET_TOKEN);
    assertThat(made.card().cardMask()).isEqualTo("xxxxxxxxxxxx9022");
    assertThat(returned.httpStatus()).isEqualTo(200);
    assertThat(returned.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(returned))
        .contains("<form method=\"post\" action=\"https://shop.example/ok\">")
        .contains("<input type=\"hidden\" name=\"apple_pay\" value=\"1\">")
        .contains("<input type=\"hidden\" name=\"ccno\" value=\"9022\">")
        .contains("<input type=\"hidden\" name=\"TranzilaTK\" value=\"t000000000000019022\">")
        .doesNotContain("broker");
    assertThat(thePage().outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(wired.posted)
        .singleElement()
        .asString()
        .contains("&apple_pay=1&")
        .contains("&TranzilaTK=t000000000000019022&")
        .endsWith("&txn_type=debit&tranmode=A&ConfirmationCode=" + made.authNumber());
    assertThat(theRow(AuditDirection.INBOUND_REQUEST).sanitizedBody())
        .containsEntry("wallet", List.of("apple_pay"));
  }

  @Test
  void a_google_pay_j5_holds_the_card_and_its_token_forces_the_hold_on_the_api() {
    open(parameters("40", "V", "google_pay", "1"));
    payWith(thePage().pageId(), "google_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction hold = wired.theLastStored();

    String forced = wired.answered(TranzilaWire.aForce(hold.transactionId(), hold.token(), "40"));

    assertThat(hold.isAJ5()).isTrue();
    assertThat(hold.processorResponseCode()).isEqualTo("777");
    assertThat(hold.broker()).isEqualTo(TranzilaWallet.GOOGLE_PAY);
    assertThat(hold.token()).isEqualTo(THE_FIRST_WALLET_TOKEN);
    assertThat(forced).contains("\"processor_response_code\":\"000\"");
    assertThat(wired.theLastStored().type()).isEqualTo(TranzilaTxnType.FORCE);
    assertThat(wired.theLastStored().broker()).isEqualTo(TranzilaWallet.GOOGLE_PAY);
  }

  @Test
  void a_google_pay_token_charges_a_new_debit_that_names_no_broker() {
    open(parameters("8", "A", "google_pay", "1"));
    payWith(thePage().pageId(), "google_pay", THE_PUBLISHED_CARD);
    String token = wired.theLastStored().token();

    String charged = wired.answered(TranzilaWire.withCard("\"txn_type\":\"debit\"", token, "3"));

    assertThat(charged).contains("\"processor_response_code\":\"000\"");
    assertThat(wired.theLastStored().type()).isEqualTo(TranzilaTxnType.DEBIT);
    assertThat(wired.theLastStored().token()).isEqualTo(THE_FIRST_WALLET_TOKEN);
    assertThat(wired.theLastStored().broker()).isNull();
  }

  @Test
  void an_apple_pay_token_is_refused_for_a_new_debit_or_verify_and_acts_on_its_own_payment() {
    open(parameters("20", "A", "apple_pay", "1"));
    payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction paid = wired.theLastStored();
    String token = paid.token();

    String debit = wired.answered(TranzilaWire.withCard("\"txn_type\":\"debit\"", token, "1"));
    String verify =
        wired.answered(
            TranzilaWire.withCard("\"txn_type\":\"verify\",\"verify_mode\":5", token, "1"));
    String credited = wired.answered(aCredit(paid.transactionId(), paid.authNumber(), token, "5"));
    TranzilaTransaction credit = wired.theLastStored();

    assertThat(debit)
        .isEqualTo("{\"error_code\":20111,\"message\":\"Provided token check failure\"}");
    assertThat(verify).isEqualTo(debit);
    assertThat(credited).contains("\"processor_response_code\":\"000\"");
    assertThat(credit.type()).isEqualTo(TranzilaTxnType.CREDIT);
    assertThat(credit.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
    assertThat(wired.stored()).hasSize(2);
  }

  @Test
  void an_apple_pay_token_cannot_credit_or_force_a_payment_it_did_not_pay() {
    open(parameters("9", "A"));
    pay(thePage().pageId(), THE_PUBLISHED_CARD);
    TranzilaTransaction debit = wired.theLastStored();
    open(parameters("9", "V"));
    pay(thePage().pageId(), THE_PUBLISHED_CARD);
    TranzilaTransaction hold = wired.theLastStored();
    open(parameters("20", "A", "apple_pay", "1"));
    payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);
    String token = wired.theLastStored().token();
    int before = wired.stored().size();

    String credit = wired.answered(aCredit(debit.transactionId(), debit.authNumber(), token, "1"));
    String force = wired.answered(TranzilaWire.aForce(hold.transactionId(), token, "1"));
    String cardCredit =
        wired.answered(
            aCredit(debit.transactionId(), debit.authNumber(), THE_PUBLISHED_TOKEN, "1"));

    assertThat(debit.broker()).isNull();
    assertThat(hold.isAJ5()).isTrue();
    assertThat(cardCredit).contains("\"processor_response_code\":\"000\"");
    assertThat(credit).contains("\"error_code\":20112");
    assertThat(force).contains("\"error_code\":20112");
    assertThat(wired.stored()).hasSize(before + 1);
  }

  @Test
  void an_apple_pay_j5_is_forced_and_its_debit_cancelled_with_its_own_token() {
    open(parameters("30", "VK", "apple_pay", "1"));
    payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction hold = wired.theLastStored();

    String forced =
        wired.answered(
            TranzilaWire.withCard(
                "\"txn_type\":\"force\",\"reference_txn_id\":"
                    + hold.transactionId()
                    + ",\"authorization_number\":\""
                    + hold.authNumber()
                    + "\"",
                hold.token(),
                "30"));
    TranzilaTransaction force = wired.theLastStored();
    String cancelled =
        wired.answered(TranzilaWire.aCancel(force.transactionId(), force.authNumber()));

    assertThat(hold.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
    assertThat(forced).contains("\"processor_response_code\":\"000\"");
    assertThat(force.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
    assertThat(cancelled).contains("\"processor_response_code\":\"000\"");
    assertThat(wired.theLastStored().type()).isEqualTo(TranzilaTxnType.CANCEL);
    assertThat(wired.theLastStored().broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
  }

  @Test
  void a_rule_failing_the_debit_declines_a_wallet_payment_which_keeps_its_broker() {
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    open(parameters("10", "A", "apple_pay", "1"));

    TranzilaApiResponse returned = payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);

    TranzilaTransaction declined = wired.theLastStored();
    assertThat(declined.processorResponseCode()).isEqualTo("004");
    assertThat(declined.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
    assertThat(declined.token()).isNull();
    assertThat(returned.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(thePage().outcome()).isEqualTo(PayerOutcome.DECLINED);
  }

  @Test
  void a_wallet_the_page_does_not_offer_or_serve_is_refused_and_the_page_stays_open() {
    open(parameters("10", "N", "apple_pay", "1"));
    String unpaid = thePage().pageId();
    open(parameters("10", "A", "apple_pay", "1"));
    String paid = thePage().pageId();

    TranzilaApiResponse notOffered = payWith(unpaid, "apple_pay", THE_PUBLISHED_CARD);
    TranzilaApiResponse notAsked = payWith(paid, "google_pay", THE_PUBLISHED_CARD);
    TranzilaApiResponse unserved = payWith(paid, "bit_pay", THE_PUBLISHED_CARD);

    assertThat(notOffered.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(notOffered))
        .contains("Apple Pay is not offered on this payment page")
        .contains(TranzilaDirectNg.STILL_COMPLETABLE.strip());
    assertThat(notAsked.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(notAsked)).contains("Google Pay is not offered");
    assertThat(unserved.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(unserved))
        .contains(
            TranzilaDirectNgPage.escaped(
                TranzilaDirectNgPage.NO_SUCH_WALLET + TranzilaDirectNg.STILL_COMPLETABLE));
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.pages.all()).noneMatch(TranzilaPage::isSettled);
  }

  @Test
  void a_payer_leaves_a_page_naming_a_wallet_it_does_not_offer_but_not_one_never_served() {
    open(parameters("10", "N", "apple_pay", "1"));
    String pageId = thePage().pageId();

    TranzilaApiResponse unserved =
        submit(pageId, Map.of("outcome", List.of("ABANDONED"), "wallet", List.of("bit_pay")));
    boolean openAfterTheUnserved = !thePage().isSettled();
    TranzilaApiResponse left =
        submit(pageId, Map.of("outcome", List.of("ABANDONED"), "wallet", List.of("apple_pay")));

    assertThat(unserved.httpStatus()).isEqualTo(422);
    assertThat(openAfterTheUnserved).isTrue();
    assertThat(left.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(left)).contains(TranzilaDirectNgPage.LEFT);
    assertThat(thePage().outcome()).isEqualTo(PayerOutcome.ABANDONED);
  }

  @Test
  void a_credit_of_an_apple_pay_payment_with_the_cards_own_token_leaves_that_token_a_card_token() {
    open(parameters("9", "A"));
    pay(thePage().pageId(), THE_PUBLISHED_CARD);
    open(parameters("20", "A", "apple_pay", "1"));
    payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction applePaid = wired.theLastStored();

    String credited =
        wired.answered(
            aCredit(applePaid.transactionId(), applePaid.authNumber(), THE_PUBLISHED_TOKEN, "5"));
    TranzilaTransaction credit = wired.theLastStored();
    String debit =
        wired.answered(TranzilaWire.withCard("\"txn_type\":\"debit\"", THE_PUBLISHED_TOKEN, "2"));
    String verify =
        wired.answered(
            TranzilaWire.withCard(
                "\"txn_type\":\"verify\",\"verify_mode\":5", THE_PUBLISHED_TOKEN, "2"));
    open(parameters("3", "A"));
    TranzilaApiResponse paid = pay(thePage().pageId(), THE_PUBLISHED_CARD);

    assertThat(credited).contains("\"processor_response_code\":\"000\"");
    assertThat(credit.type()).isEqualTo(TranzilaTxnType.CREDIT);
    assertThat(credit.token()).isEqualTo(THE_PUBLISHED_TOKEN);
    assertThat(credit.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
    assertThat(debit).contains("\"error_code\":0").contains("\"processor_response_code\":\"000\"");
    assertThat(verify).contains("\"error_code\":0").contains("\"processor_response_code\":\"777\"");
    assertThat(paid.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(thePage().outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(wired.theLastStored().broker()).isNull();
  }

  @Test
  void an_apple_pay_token_stays_refused_for_a_new_charge_after_every_follow_on_it_made() {
    open(parameters("30", "VK", "apple_pay", "1"));
    payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction hold = wired.theLastStored();
    String token = hold.token();
    wired.answered(aForceNaming(hold, token, "30"));
    TranzilaTransaction force = wired.theLastStored();
    String credited =
        wired.answered(aCredit(force.transactionId(), force.authNumber(), token, "10"));
    open(parameters("12", "V", "apple_pay", "1"));
    payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction secondHold = wired.theLastStored();
    String secondToken = secondHold.token();
    wired.answered(TranzilaWire.aReversal(secondHold.transactionId(), secondHold.authNumber()));

    String debit = wired.answered(TranzilaWire.withCard("\"txn_type\":\"debit\"", token, "1"));
    String afterTheReversal =
        wired.answered(TranzilaWire.withCard("\"txn_type\":\"debit\"", secondToken, "1"));

    assertThat(credited).contains("\"processor_response_code\":\"000\"");
    assertThat(wired.stored())
        .extracting(TranzilaTransaction::type)
        .containsExactly(
            TranzilaTxnType.VERIFY,
            TranzilaTxnType.FORCE,
            TranzilaTxnType.CREDIT,
            TranzilaTxnType.VERIFY,
            TranzilaTxnType.REVERSAL);
    assertThat(wired.stored())
        .allSatisfy(made -> assertThat(made.processorResponseCode()).isIn("000", "777"));
    assertThat(debit).isEqualTo(THE_TOKEN_CHECK_FAILURE);
    assertThat(afterTheReversal).isEqualTo(THE_TOKEN_CHECK_FAILURE);
  }

  @Test
  void a_force_over_the_hold_and_a_declined_force_keep_the_wallet_payments_broker() {
    open(parameters("40", "V", "google_pay", "1"));
    payWith(thePage().pageId(), "google_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction hold = wired.theLastStored();

    wired.answered(aForceNaming(hold, hold.token(), "50"));
    TranzilaTransaction overTheHold = wired.theLastStored();
    wired.writes(TranzilaOperations.FORCE, new ScenarioAction.TerminalFailure("004"));
    wired.answered(aForceNaming(hold, hold.token(), "40"));
    TranzilaTransaction declined = wired.theLastStored();

    assertThat(overTheHold.type()).isEqualTo(TranzilaTxnType.FORCE);
    assertThat(overTheHold.processorResponseCode()).isEqualTo("507");
    assertThat(overTheHold.broker()).isEqualTo(TranzilaWallet.GOOGLE_PAY);
    assertThat(declined.type()).isEqualTo(TranzilaTxnType.FORCE);
    assertThat(declined.processorResponseCode()).isEqualTo("004");
    assertThat(declined.broker()).isEqualTo(TranzilaWallet.GOOGLE_PAY);
  }

  @Test
  void a_declined_credit_and_a_declined_cancel_keep_the_wallet_payments_broker() {
    open(parameters("25", "A", "apple_pay", "1"));
    payWith(thePage().pageId(), "apple_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction paid = wired.theLastStored();
    wired.writes(TranzilaOperations.CREDIT, new ScenarioAction.TerminalFailure("004"));
    wired.writes(TranzilaOperations.CANCEL, new ScenarioAction.TerminalFailure("004"));

    wired.answered(aCredit(paid.transactionId(), paid.authNumber(), paid.token(), "5"));
    TranzilaTransaction credit = wired.theLastStored();
    wired.answered(TranzilaWire.aCancel(paid.transactionId(), paid.authNumber()));
    TranzilaTransaction cancel = wired.theLastStored();

    assertThat(credit.type()).isEqualTo(TranzilaTxnType.CREDIT);
    assertThat(credit.processorResponseCode()).isEqualTo("004");
    assertThat(credit.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
    assertThat(cancel.type()).isEqualTo(TranzilaTxnType.CANCEL);
    assertThat(cancel.processorResponseCode()).isEqualTo("004");
    assertThat(cancel.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
  }

  @Test
  void an_approved_and_a_declined_reversal_keep_the_wallet_payments_broker() {
    open(parameters("15", "V", "google_pay", "1"));
    payWith(thePage().pageId(), "google_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction first = wired.theLastStored();
    wired.answered(TranzilaWire.aReversal(first.transactionId(), first.authNumber()));
    TranzilaTransaction approved = wired.theLastStored();
    open(parameters("16", "V", "google_pay", "1"));
    payWith(thePage().pageId(), "google_pay", THE_PUBLISHED_CARD);
    TranzilaTransaction second = wired.theLastStored();
    wired.writes(TranzilaOperations.REVERSAL, new ScenarioAction.TerminalFailure("004"));

    wired.answered(TranzilaWire.aReversal(second.transactionId(), second.authNumber()));
    TranzilaTransaction declined = wired.theLastStored();

    assertThat(approved.type()).isEqualTo(TranzilaTxnType.REVERSAL);
    assertThat(approved.processorResponseCode()).isEqualTo("000");
    assertThat(approved.broker()).isEqualTo(TranzilaWallet.GOOGLE_PAY);
    assertThat(declined.type()).isEqualTo(TranzilaTxnType.REVERSAL);
    assertThat(declined.processorResponseCode()).isEqualTo("004");
    assertThat(declined.broker()).isEqualTo(TranzilaWallet.GOOGLE_PAY);
  }

  @Test
  void a_wallet_payment_under_a_dc_disable_already_approved_answers_the_published_duplicate() {
    open(parameters("30", "A", "apple_pay", "1", "DCdisable", "order-88"));
    open(parameters("30", "A", "apple_pay", "1", "DCdisable", "order-88"));
    payWith(wired.pages.all().get(0).pageId(), "apple_pay", THE_PUBLISHED_CARD);

    TranzilaApiResponse again =
        payWith(wired.pages.all().get(1).pageId(), "apple_pay", THE_PUBLISHED_CARD);

    assertThat(again.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(again)).contains(TranzilaDirectNgPage.THE_DUPLICATE);
    assertThat(wired.stored())
        .singleElement()
        .satisfies(
            made -> {
              assertThat(made.dcDisable()).isEqualTo("order-88");
              assertThat(made.broker()).isEqualTo(TranzilaWallet.APPLE_PAY);
            });
    assertThat(wired.pages.all().get(1).isSettled()).isFalse();
  }

  @Test
  void a_wallet_payment_renews_its_handshake_and_one_under_a_lapsed_handshake_is_refused() {
    wired.handshakes.insert(
        new TranzilaHandshake(
            A_THTK,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            "10",
            null,
            TranzilaWiring.NOW.minusSeconds(1),
            TranzilaWiring.NOW.plusSeconds(60)));
    open(parameters("10", "A", "google_pay", "1", "thtk", A_THTK));
    open(parameters("10", "A", "google_pay", "1", "thtk", A_THTK));
    TranzilaApiResponse mismatched = open(parameters("11", "A", "google_pay", "1", "thtk", A_THTK));
    payWith(wired.pages.all().get(0).pageId(), "google_pay", THE_PUBLISHED_CARD);
    TranzilaHandshake renewed = wired.handshakes.all().get(0);
    wired.clock.moveTo(Instant.parse("2026-10-13T12:21:00Z"));

    TranzilaApiResponse lapsed =
        payWith(wired.pages.all().get(1).pageId(), "google_pay", THE_PUBLISHED_CARD);

    assertThat(mismatched.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(mismatched)).contains("Illegal Operation 912791");
    assertThat(wired.pages.all()).hasSize(2);
    assertThat(renewed.expiresAt()).isEqualTo(Instant.parse("2026-10-13T12:20:00Z"));
    assertThat(lapsed.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(lapsed)).contains("Illegal Operation 912791").contains("still completable");
    assertThat(wired.stored())
        .singleElement()
        .extracting(TranzilaTransaction::broker)
        .isEqualTo(TranzilaWallet.GOOGLE_PAY);
  }

  @Test
  void the_lookups_answer_the_broker_a_transaction_carries_and_nothing_for_a_card() {
    TranzilaTransaction card = wiredTransaction(null);

    assertThat(TranzilaLookupAnswers.theBrokerOf(card)).isEmpty();
    assertThat(TranzilaLookupAnswers.theBrokerOf(wiredTransaction(TranzilaWallet.APPLE_PAY)))
        .isEqualTo("apple_pay");
    assertThat(TranzilaLookupAnswers.theBrokerOf(wiredTransaction(TranzilaWallet.GOOGLE_PAY)))
        .isEqualTo("GOOGLE_PAY");
    assertThat(
            TranzilaJsonWriter.toText(
                TranzilaLookupAnswers.aRowOfTheApi(
                    new TranzilaRecorded(
                        wiredTransaction(TranzilaWallet.GOOGLE_PAY), List.of(), List.of()),
                    List.of())))
        .contains("\"broker\":\"GOOGLE_PAY\",\"broker_product\":\"\"");
    assertThat(
            TranzilaJsonWriter.toText(
                TranzilaLookupAnswers.aRowOfTheReport(
                    new TranzilaRecorded(
                        wiredTransaction(TranzilaWallet.APPLE_PAY), List.of(), List.of()),
                    List.of(),
                    false)))
        .contains("\"broker\":\"apple_pay\",\"broker_product\":\"\"");
  }

  @Test
  void the_store_keeps_a_broker_by_its_published_name_and_refuses_one_no_wallet_names() {
    assertThat(TranzilaStoredForm.brokerNameOf(null)).isNull();
    assertThat(TranzilaStoredForm.brokerNameOf(TranzilaWallet.APPLE_PAY)).isEqualTo("apple_pay");
    assertThat(TranzilaStoredForm.brokerNameOf(TranzilaWallet.GOOGLE_PAY)).isEqualTo("GOOGLE_PAY");
    assertThat(TranzilaStoredForm.brokerOf(null)).isNull();
    assertThat(TranzilaStoredForm.brokerOf("apple_pay")).isEqualTo(TranzilaWallet.APPLE_PAY);
    assertThat(TranzilaStoredForm.brokerOf("GOOGLE_PAY")).isEqualTo(TranzilaWallet.GOOGLE_PAY);
    assertThatThrownBy(() -> TranzilaStoredForm.brokerOf("ENDEAVOUR"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("the store holds ENDEAVOUR as a broker, which no wallet names");
  }

  private TranzilaTransaction wiredTransaction(
      @org.jspecify.annotations.Nullable TranzilaWallet broker) {
    wired.answered(TranzilaWire.aDebit());
    TranzilaTransaction made = wired.theLastStored();
    return new TranzilaTransaction(
        made.testRunId(),
        made.transactionId(),
        made.rootTxnId(),
        made.accountId(),
        made.terminalName(),
        made.type(),
        made.verifyMode(),
        made.referenceTxnId(),
        made.processorResponseCode(),
        made.amount(),
        made.currencyCode(),
        made.authNumber(),
        made.token(),
        made.card(),
        made.responseCvv(),
        made.responseId(),
        made.dcDisable(),
        made.holdExpiresAt(),
        made.origin(),
        made.createdAt(),
        broker);
  }

  private static String aForceNaming(TranzilaTransaction hold, String token, String amount) {
    return TranzilaWire.withCard(
            "\"txn_type\":\"force\",\"reference_txn_id\":"
                + hold.transactionId()
                + ",\"authorization_number\":\""
                + hold.authNumber()
                + "\"",
            token,
            amount)
        .replace("\"expire_month\":7", "\"expire_month\":12");
  }

  private static String aCredit(long reference, String authorization, String token, String amount) {
    return TranzilaWire.aCredit(reference, authorization, token, amount)
        .replace("\"expire_month\":7", "\"expire_month\":12");
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

  private TranzilaApiResponse sheet(String pageId, String wallet) {
    return wired.directNg.sheet(
        TranzilaPageCall.of(
            "GET",
            TranzilaDirectNgPage.payerPathOf(TranzilaWiring.TERMINAL, pageId) + "/wallet/" + wallet,
            Map.of()),
        TranzilaWiring.TERMINAL,
        pageId,
        wallet);
  }

  private TranzilaApiResponse shown(String pageId) {
    return wired.directNg.shown(
        TranzilaPageCall.of(
            "GET", TranzilaDirectNgPage.payerPathOf(TranzilaWiring.TERMINAL, pageId), Map.of()),
        TranzilaWiring.TERMINAL,
        pageId);
  }

  private TranzilaApiResponse pay(String pageId, String card) {
    return submit(pageId, Map.of("outcome", List.of("PAID"), "card", List.of(card)));
  }

  private TranzilaApiResponse payWith(String pageId, String wallet, String card) {
    Map<String, List<String>> form = new LinkedHashMap<>();
    form.put("wallet", List.of(wallet));
    form.put("card", List.of(card));
    form.put("outcome", List.of("PAID"));
    return submit(pageId, form);
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
    for (int at = 0; at < extra.length; at += 2) {
      parameters.put(extra[at], List.of(extra[at + 1]));
    }
    return parameters;
  }

  private static String bodyOf(TranzilaApiResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }
}
