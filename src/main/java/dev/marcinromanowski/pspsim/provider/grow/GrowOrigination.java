package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

final class GrowOrigination {

  static final String A_HOLD = "createPaymentProcess:chargeType=2";

  static final String A_SAVED_CARD = "createPaymentProcess:chargeType=3";

  static final String A_SAVED_CARD_CHARGE = GrowOperations.CREATE_TRANSACTION_WITH_TOKEN;

  private static final ThePayerStep THE_HOSTED_PAGE =
      new ThePayerStep.APageThisSimulatorServes(GrowHostedPage.PATH);

  private static final ThePayerStep NO_PAYER_IS_PART_OF_IT =
      new ThePayerStep.NoPageThisSimulatorServes(
          "Grow takes a stored-card charge server to server. createTransactionWithToken carries"
              + " the cardToken a hosted page already minted, so there is no payer in this call,"
              + " no page for one to reach and nothing to wait for: grow charges the saved card"
              + " as it answers.");

  private static final String ERROR_ID = "err.id";

  private static final String ERROR_MESSAGE = "err.message";

  private static final String WHAT_THE_PAYMENTS_SCREEN_SHOWS =
      " The Payments screen lists this process as one object from the moment you start it, and as"
          + " two objects once a payer has finished the page: the process and the transaction its"
          + " completion created, each naming the other under relatedObjects.";

  private static final String A_PAYERS_OWN_DATA_IS_NOT_REDACTED =
      " It is a payer's personal data: this simulator stores it as it was typed and does not"
          + " redact it in the audit trail.";

  private static final AFieldATesterFillsIn SUM =
      AFieldATesterFillsIn.typed(
          GrowFields.SUM,
          "amount",
          true,
          "The amount grow puts on the payer's card, written the way grow writes money. Grow"
              + " refuses a process whose amount is not a positive number, before it looks at"
              + " anything else on the request.",
          "10.00");

  private static final AFieldATesterFillsIn DESCRIPTION =
      AFieldATesterFillsIn.typed(
          GrowFields.DESCRIPTION,
          "what the payer is paying for",
          true,
          "The line grow prints on its hosted page as the thing being paid for. The payer reads"
              + " it; nothing in this simulator matches on it.",
          "one seat, row F");

  private static final AFieldATesterFillsIn SUCCESS_URL =
      AFieldATesterFillsIn.typed(
          GrowFields.SUCCESS_URL,
          "where the payer's browser goes after paying",
          true,
          "Grow sends the payer's browser here once the hosted page is finished. This simulator"
              + " stores the address and never opens it, so any address your application would"
              + " use is fine here.",
          "https://merchant.example/paid");

  private static final AFieldATesterFillsIn CANCEL_URL =
      AFieldATesterFillsIn.typed(
          GrowFields.CANCEL_URL,
          "where the payer's browser goes if they give up",
          true,
          "Grow sends the payer's browser here if they leave the hosted page without paying. This"
              + " simulator stores the address and never opens it.",
          "https://merchant.example/abandoned");

  private static final AFieldATesterFillsIn FULL_NAME =
      AFieldATesterFillsIn.typed(
          GrowFields.FULL_NAME,
          "the payer's name",
          true,
          "Grow requires a name on every hosted process and prefills its page with this one."
              + A_PAYERS_OWN_DATA_IS_NOT_REDACTED,
          "Dana Levi");

  private static final AFieldATesterFillsIn PHONE =
      AFieldATesterFillsIn.typed(
          GrowFields.PHONE,
          "the payer's phone number",
          true,
          "Grow requires a phone number on every hosted process."
              + A_PAYERS_OWN_DATA_IS_NOT_REDACTED,
          "0501234567");

  private static final AFieldATesterFillsIn EMAIL =
      AFieldATesterFillsIn.typed(
          GrowFields.EMAIL,
          "the payer's email address",
          false,
          "Grow accepts an email address on a hosted process and does not require one. Leaving it"
              + " empty sends nothing, and grow behaves as it does for a process that carried"
              + " none.",
          "dana@example.com");

  private static final AFieldATesterFillsIn NOTIFY_URL =
      AFieldATesterFillsIn.theCallbackTarget(
          GrowFields.NOTIFY_URL,
          "where grow posts the callback",
          "Grow posts this payment's server-to-server callback here. It refuses to post one to an"
              + " address this test run does not configure as a callback target, so pick one of"
              + " the addresses this run configures, or leave it empty and grow uses the single"
              + " target configured for this account.");

  private static final AFieldATesterFillsIn SAVE_CARD_TOKEN =
      AFieldATesterFillsIn.chosenFrom(
          GrowFields.SAVE_CARD_TOKEN,
          "keep the payer's card for later",
          false,
          "Grow can save the card the payer types on this page and hand back a token to charge it"
              + " again later, on top of holding the money now.",
          List.of(
              new AValueAFieldOffers(
                  GrowFields.SAVED_CARD_TOKEN, "yes, save the card as well as holding the money"),
              new AValueAFieldOffers("0", "no, hold the money and keep no card")));

  private static final AFieldATesterFillsIn CARD_TOKEN =
      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
          GrowFields.CARD_TOKEN,
          "the saved card to charge",
          true,
          "The cardToken grow minted when a payer finished a hosted page that was asked to keep"
              + " their card. The box suggests the cards the finished processes of this test run"
              + " saved on the account above, and takes any token you type instead: grow is what"
              + " reads this token, resolving it against the process that saved it on the same"
              + " business, and it answers err.id 140 for a token that business never saved. If"
              + " nothing is suggested, start one of the two hosted ways of starting a payment"
              + " with the card kept and finish its page as the payer: the card that page saves"
              + " is suggested here afterwards.");

  private static final AFieldATesterFillsIn SUM_CHARGED =
      AFieldATesterFillsIn.typed(
          GrowFields.SUM,
          "the amount to take off the saved card",
          true,
          "The amount grow charges the saved card now. Grow refuses a charge whose amount is not"
              + " a positive number, before it looks at anything else on the request.",
          "10.00");

  private static final AFieldATesterFillsIn DESCRIPTION_CHARGED =
      AFieldATesterFillsIn.typed(
          GrowFields.DESCRIPTION,
          "what this charge is for",
          true,
          "The line grow records on the transaction as the thing being paid for, and the one"
              + " getTransactionInfo reads back. Grow's own wire does not check it on a"
              + " stored-card charge; its published table requires it and this simulator enforces"
              + " the table.",
          "one seat, row F");

  private static final AFieldATesterFillsIn PAYMENT_TYPE =
      AFieldATesterFillsIn.chosenFrom(
          GrowFields.PAYMENT_TYPE,
          "the kind of payment this charge is",
          true,
          "Grow requires paymentType on a stored-card charge and reads none of it: every record"
              + " it answers renders the constant 2, whichever of these was sent.",
          List.of(
              new AValueAFieldOffers(
                  "1",
                  "a standing order, which grow's parameter mapping calls Direct Debit and its"
                      + " token page calls Recurring Payments"),
              new AValueAFieldOffers("2", "a regular payment"),
              new AValueAFieldOffers("4", "an instalment payment")));

  private static final AFieldATesterFillsIn PAYMENT_NUM =
      AFieldATesterFillsIn.typed(
          GrowFields.PAYMENT_NUM,
          "how many payments to split this charge into",
          true,
          "Grow publishes paymentNum as optional and \"1-12\", and its wire answers a charge that"
              + " carries none with missing data, so this wizard asks for it.",
          "1");

  private static final AFieldATesterFillsIn TRANSACTION_UNIQUE_IDENTIFIER =
      AFieldATesterFillsIn.typed(
          GrowFields.TRANSACTION_UNIQUE_IDENTIFIER,
          "your own identifier for this charge",
          true,
          "The identifier grow holds this charge under, so that the same call sent twice is"
              + " rejected rather than charged twice. Grow rejects a repeat of one it has already"
              + " seen whatever became of the first charge, so use a number this business has not"
              + " carried before. It is a positive whole number, no wider than 32 bits and with no"
              + " leading zeros: grow answers err.id 1013 for anything else, and err.id 104 when"
              + " none is sent at all.",
          "1001");

  private static final AFieldATesterFillsIn TRANSACTION_GROUP_IDENTIFIER =
      AFieldATesterFillsIn.typed(
          GrowFields.TRANSACTION_GROUP_IDENTIFIER,
          "your own identifier for the group this charge belongs to",
          false,
          "Grow accepts a group identifier on a stored-card charge and rejects a charge whose"
              + " group one of its successful payments already carries. Leaving it empty sends"
              + " nothing.",
          "group-1");

  private static final AFieldATesterFillsIn FULL_NAME_CHARGED =
      AFieldATesterFillsIn.typed(
          GrowFields.FULL_NAME,
          "the name to record on the charge",
          true,
          "Grow's published table requires a name on a stored-card charge and records it on the"
              + " transaction it creates; nobody is shown a page to read it on."
              + A_PAYERS_OWN_DATA_IS_NOT_REDACTED,
          "Dana Levi");

  private static final AFieldATesterFillsIn PHONE_CHARGED =
      AFieldATesterFillsIn.typed(
          GrowFields.PHONE,
          "the phone number to record on the charge",
          true,
          "Grow's published table requires a phone number on a stored-card charge and records it"
              + " on the transaction it creates."
              + A_PAYERS_OWN_DATA_IS_NOT_REDACTED,
          "0501234567");

  private static final AFieldATesterFillsIn EMAIL_CHARGED =
      AFieldATesterFillsIn.typed(
          GrowFields.EMAIL,
          "the email address to record on the charge",
          false,
          "Grow accepts an email address on a stored-card charge and does not require one."
              + " Leaving it empty sends nothing.",
          "dana@example.com");

  static final ProviderOriginationFields DECLARED =
      new ProviderOriginationFields(
          GrowLightApi.GROW,
          List.of(
              new DeclaredOrigination(
                  A_HOLD,
                  "Hold the money on the payer's card and settle it later",
                  "A payment process waiting for the payer on grow's hosted page. Nothing is held"
                      + " on any card yet: grow holds the money when the payer picks a card and"
                      + " finishes that page, and the transaction that completion creates is the"
                      + " one settleSuspendedTransaction settles and refundTransaction refunds."
                      + WHAT_THE_PAYMENTS_SCREEN_SHOWS,
                  THE_HOSTED_PAGE,
                  List.of(
                      SUM,
                      DESCRIPTION,
                      SUCCESS_URL,
                      CANCEL_URL,
                      FULL_NAME,
                      PHONE,
                      EMAIL,
                      NOTIFY_URL,
                      SAVE_CARD_TOKEN)),
              new DeclaredOrigination(
                  A_SAVED_CARD,
                  "Save the payer's card and take no money at all",
                  "A registration process waiting for the payer on grow's hosted page. It moves no"
                      + " money ever: when the payer finishes it grow saves the card and hands"
                      + " back a token to charge later, and the transaction that completion"
                      + " creates holds no operation and reports SUCCEEDED, because nothing was"
                      + " charged and nothing is waiting to be. That token is what"
                      + " createTransactionWithToken charges when you decide to take the money."
                      + " Grow still requires an amount on the request and charges none of it. The"
                      + " wizard sends saveCardToken=1 with this process, because grow refuses a"
                      + " chargeType=3 process that does not carry it."
                      + WHAT_THE_PAYMENTS_SCREEN_SHOWS,
                  THE_HOSTED_PAGE,
                  List.of(
                      SUM,
                      DESCRIPTION,
                      SUCCESS_URL,
                      CANCEL_URL,
                      FULL_NAME,
                      PHONE,
                      EMAIL,
                      NOTIFY_URL)),
              new DeclaredOrigination(
                  A_SAVED_CARD_CHARGE,
                  "Charge a card one of this run's hosted pages already saved",
                  "A transaction that is already paid. createTransactionWithToken takes the money"
                      + " off the saved card as it answers, so nothing is held, nothing waits for"
                      + " a payer and no process of any kind is created: this way leaves one"
                      + " object behind. That transaction reports SUCCEEDED, so"
                      + " settleSuspendedTransaction has nothing left to settle on it and"
                      + " refundTransaction is the one operation that still moves money on it."
                      + " Grow puts no processId on a token charge, so under relatedObjects the"
                      + " transaction names the business it was taken on and never the process"
                      + " whose payer saved the card.",
                  NO_PAYER_IS_PART_OF_IT,
                  List.of(
                      CARD_TOKEN,
                      SUM_CHARGED,
                      DESCRIPTION_CHARGED,
                      PAYMENT_TYPE,
                      PAYMENT_NUM,
                      TRANSACTION_UNIQUE_IDENTIFIER,
                      TRANSACTION_GROUP_IDENTIFIER,
                      FULL_NAME_CHARGED,
                      PHONE_CHARGED,
                      EMAIL_CHARGED))),
          theFieldsTheSimulatorFillsIn());

  private static SequencedMap<String, String> theFieldsTheSimulatorFillsIn() {
    LinkedHashMap<String, String> filled = new LinkedHashMap<>();
    filled.put(
        GrowAccount.USER_ID,
        "grow names the merchant by the userId configured for the account this payment is started"
            + " on. The simulator holds it as a secret, sends it with the call and never reads it"
            + " back onto a screen.");
    filled.put(
        GrowAccount.PAGE_CODE,
        "grow names the hosted page by the pageCode configured for the account a hosted process is"
            + " started on. The simulator holds it as a secret, sends it with the call and never"
            + " reads it back onto a screen. A stored-card charge reaches no page and carries no"
            + " pageCode at all, so nothing is sent for it there.");
    filled.put(
        GrowFields.CHARGE_TYPE,
        "grow decides on chargeType whether a process holds money or only saves a card, and the"
            + " way of starting the payment you picked above is that choice: the simulator sends"
            + " the chargeType that belongs to it. A stored-card charge is not a process and"
            + " carries no chargeType at all, so nothing is sent for it there.");
    return filled;
  }

  private GrowOrigination() {}

  static boolean chargesACardAHostedPageSaved(OriginationRequest request) {
    return A_SAVED_CARD_CHARGE.equals(request.startedAs());
  }

  static GrowForm theHostedProcessFormOf(OriginationRequest request, GrowAccount account) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put(GrowFields.USER_ID, List.of(account.userId()));
    fields.put(GrowFields.PAGE_CODE, List.of(account.pageCode()));
    fields.put(GrowFields.CHARGE_TYPE, List.of(chargeTypeOf(request)));
    for (Map.Entry<String, String> value : request.values().entrySet()) {
      fields.put(value.getKey(), List.of(value.getValue()));
    }
    if (A_SAVED_CARD.equals(request.startedAs())) {
      fields.put(GrowFields.SAVE_CARD_TOKEN, List.of(GrowFields.SAVED_CARD_TOKEN));
    }
    return GrowForm.of(fields);
  }

  static GrowForm theSavedCardChargeFormOf(OriginationRequest request, GrowAccount account) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put(GrowFields.USER_ID, List.of(account.userId()));
    for (Map.Entry<String, String> value : request.values().entrySet()) {
      fields.put(value.getKey(), List.of(value.getValue()));
    }
    return GrowForm.of(fields);
  }

  static List<AValueAFieldOffers> theSavedCardsThisRunHolds(List<GrowPaymentProcess> saved) {
    List<AValueAFieldOffers> offered = new ArrayList<>();
    for (GrowPaymentProcess process : saved) {
      GrowProcessOutcome outcome = process.outcome();
      String cardToken = outcome == null ? null : outcome.cardToken();
      if (cardToken != null) {
        offered.add(new AValueAFieldOffers(cardToken, theWayATesterTellsThemApart(process)));
      }
    }
    return List.copyOf(offered);
  }

  private static String theWayATesterTellsThemApart(GrowPaymentProcess process) {
    GrowProcessOutcome outcome = process.outcome();
    GrowRecordedCard card = outcome == null ? null : outcome.cardCharged();
    return "the card "
        + process.payerFullName()
        + " saved on process "
        + process.processId()
        + (card == null ? "" : ", " + card.brand() + " ending " + card.suffix());
  }

  private static String chargeTypeOf(OriginationRequest request) {
    return A_SAVED_CARD.equals(request.startedAs())
        ? GrowFields.CHARGE_TYPE_TOKEN_ONLY
        : GrowFields.CHARGE_TYPE_SUSPENDED;
  }

  static OriginationOutcome outcomeOf(GrowOperationResult result, OriginationRequest request) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new OriginationOutcome.AnsweredByAScenarioInstead(String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new OriginationOutcome.RefusedByTheProvider(
          theErrorInItsOwnWords(result), result.scenarioId());
    }
    return new OriginationOutcome.Originated(
        String.valueOf(result.providerObjectId()),
        DECLARED.wayNamed(request.startedAs()).orElseThrow().whatItLeavesBehind(),
        result.scenarioId());
  }

  static String theErrorInItsOwnWords(GrowOperationResult result) {
    Map<String, List<String>> answered = result.body().flatten();
    String id = firstOf(answered, ERROR_ID);
    String message = firstOf(answered, ERROR_MESSAGE);
    return message.isBlank() ? id : id + " " + message;
  }

  private static String firstOf(Map<String, List<String>> answered, String name) {
    List<String> values = answered.getOrDefault(name, new ArrayList<>());
    return values.isEmpty() ? "" : values.get(0);
  }
}
