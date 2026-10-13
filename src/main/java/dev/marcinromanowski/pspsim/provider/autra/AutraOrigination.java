package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

final class AutraOrigination {

  static final String A_HOLD = AutraOperations.PRE_AUTHORIZATION;

  static final String A_PIX = AutraOperations.PIX_CHECKOUT;

  static final String A_CARD_SESSION = AutraOperations.CARD_SESSION;

  static final String A_SALE = AutraOperations.SALE;

  static final String WIRE_METHOD = "POST";

  private static final String IT_IS_LISTED_ALREADY =
      " The payment exists the moment this screen answers: it is on the Payments screen now, under"
          + " the paymentID above, and the actions offered there act on it.";

  private static final ThePayerStep AUTRA_DECIDES_THE_RESERVATION_AT_ONCE =
      new ThePayerStep.NoPageThisSimulatorServes(
          "Nothing waits for a payer here. Autra answers the pre-authorization with the"
              + " reservation already made or already refused, so this way of starting leaves no"
              + " page and no payer step at all.");

  private static final ThePayerStep THE_PAYER_IS_NOT_PRESENT_FOR_A_SALE =
      new ThePayerStep.NoPageThisSimulatorServes(
          "Nothing waits for a payer here. A sale is charged off-session against a card the payer"
              + " stored earlier, so the payer is not present for it at all.");

  private static final ThePayerStep THE_PAYER_PAYS_THE_QR_IN_THEIR_BANK_APP =
      new ThePayerStep.NoPageThisSimulatorServes(
          "Autra publishes no page for a Pix. The payer copies the QR payload into their own bank"
              + " app, which is not a page anything here could serve, and the payer step on the"
              + " Payments screen stands in for that app.");

  private static final ThePayerStep THE_HOSTED_CARD_PAGE =
      new ThePayerStep.APageThisSimulatorServes(AutraCardPage.PATH);

  private static final AFieldATesterFillsIn AMOUNT =
      AFieldATesterFillsIn.typed(
          AutraFields.AMOUNT,
          "amount to reserve",
          true,
          "Autra reserves this much of the card's limit without charging it, and publishes only"
              + " that it must be above zero: it names no minimum, no maximum and no limit on"
              + " decimal places anywhere in this contract. It is written in units of the"
              + " currency and never in cents.",
          "500.00");

  private static final AFieldATesterFillsIn CURRENCY =
      AFieldATesterFillsIn.typed(
          AutraFields.CURRENCY,
          "currency",
          true,
          "Autra types this as a plain string and never as an enumeration, writes BRL in every"
              + " example and instructs \"Use BRL\" in prose. This simulator refuses no code,"
              + " because the contract refuses none.",
          "BRL");

  private static final AFieldATesterFillsIn INSTALLMENTS =
      AFieldATesterFillsIn.typed(
          AutraFields.INSTALLMENTS,
          "number of instalments",
          false,
          "Autra publishes this as \"1 = a vista. 2-12 = parcelado.\" Left empty, the simulator"
              + " sends 1, which is the value the guide's own pre-authorization example leaves"
              + " out.",
          "1");

  private static final AFieldATesterFillsIn ORDER_ID =
      AFieldATesterFillsIn.typed(
          AutraFields.ORDER_ID,
          "the merchant's own order reference",
          false,
          "Autra claims this order before it calls the acquirer, per merchant within the tenant,"
              + " and answers a second pre-authorization under it 409"
              + " ORDER_ALREADY_PREAUTHORIZED. An attempt that ends in failure releases it for"
              + " reuse. Left empty, Autra turns idempotency off and every call creates a new"
              + " reservation.",
          "pedido-abc-123");

  private static final AFieldATesterFillsIn PIX_AMOUNT =
      AFieldATesterFillsIn.typed(
          AutraFields.AMOUNT,
          "amount to collect",
          true,
          "Autra publishes no minimum, no maximum and no limit on decimal places for a Pix at all,"
              + " and types the field as a number in units of the currency and never in cents."
              + " Refusing an amount that is not above zero is this simulator's own narrowing.",
          "99.90");

  private static final AFieldATesterFillsIn PIX_DESCRIPTION =
      AFieldATesterFillsIn.typed(
          AutraFields.DESCRIPTION,
          "what the payer sees in their bank app",
          false,
          "Autra publishes this as optional and says where it shows up: \"Aparece no app do banco"
              + " do pagador\".",
          "Pedido #1234");

  private static final AFieldATesterFillsIn PIX_ORDER_ID =
      AFieldATesterFillsIn.typed(
          AutraFields.ORDER_ID,
          "the merchant's own order reference",
          false,
          "Autra publishes this under a heading that says idempotency and then denies it: \"Nao e"
              + " tecnicamente uma chave de idempotencia (a API nao bloqueia 2 Pix com mesmo"
              + " orderId hoje), mas e a referencia cruzada que voce vai querer ter\". Two"
              + " checkouts under one orderId produce two QR codes here, because that is what"
              + " Autra does.",
          "pedido-abc-123");

  private static final AFieldATesterFillsIn SALE_AMOUNT =
      AFieldATesterFillsIn.typed(
          AutraFields.AMOUNT,
          "amount to charge",
          true,
          "Autra charges this much of the stored card at once and publishes zero as meaningful"
              + " here and nowhere else: \"Com amount: 0 funciona como validacao de cartao"
              + " (zero-dollar): verifica o cartao junto ao emissor sem cobrar\". It is written in"
              + " units of the currency and never in cents.",
          "150.00");

  private static final AFieldATesterFillsIn TRANSACTION_TYPE =
      AFieldATesterFillsIn.chosenFrom(
          AutraFields.TRANSACTION_TYPE,
          "credit or debit",
          true,
          "Autra publishes this as an enumeration of exactly CREDIT and DEBIT on the sale. The"
              + " pre-authorization takes CREDIT alone, so debit is offered here and nowhere else"
              + " in this contract.",
          List.of(
              new AValueAFieldOffers(AutraFields.CREDIT, "CREDIT"),
              new AValueAFieldOffers(AutraFields.DEBIT, "DEBIT")));

  private static final AFieldATesterFillsIn SLUG_TOKEN =
      AFieldATesterFillsIn.typed(
          AutraFields.SLUG_TOKEN,
          "the network token half of the stored card",
          false,
          "Autra names a stored card by a pair inside tokenData and publishes this half as"
              + " \"Network token — preferir quando disponivel\". A hosted card session of this"
              + " simulator hands both halves back when a payer finishes it, and they are on that"
              + " session's own screen. Send one of the two; sending neither is refused.",
          "slug-token-abc");

  private static final AFieldATesterFillsIn SLUG_STORED_CARD =
      AFieldATesterFillsIn.typed(
          AutraFields.SLUG_STORED_CARD,
          "the stored-card half of the stored card",
          false,
          "The other half of the same pair, which Autra publishes as \"Cartao armazenado — expira"
              + " junto com o cartao fisico\" on the pre-authorization schema and as \"Reference"
              + " your backend uses to charge the card later\" on its card SDK page. The two"
              + " pages lean opposite ways about which to prefer for a repeat charge, and this"
              + " simulator sends whichever the tester fills in.",
          "slug-stored-card-xyz");

  private static final AFieldATesterFillsIn SALE_ORDER_ID =
      AFieldATesterFillsIn.typed(
          AutraFields.ORDER_ID,
          "the merchant's own order reference",
          false,
          "Autra refuses a second sale under an orderId that already has an approved payment —"
              + " \"Se um pagamento com o mesmo orderId ja estiver ACCEPTED ou CAPTURED, retorna"
              + " 409 sem criar um novo debito\" — and that 409 names no payment at all. A"
              + " declined attempt leaves the order free. Left empty, nothing is checked.",
          "pedido-abc-123");

  private static final AFieldATesterFillsIn NICKNAME =
      AFieldATesterFillsIn.typed(
          AutraFields.NICKNAME,
          "what the cardholder calls this card",
          false,
          "Autra publishes nickname as \"an optional alias the cardholder types on the hosted"
              + " page\", \"Empty/absent if left blank\", and hands it back in"
              + " AutraCardTokenizedResult. The card page this simulator serves asks a payer only"
              + " which test card to store and never for an alias, so the value is collected here"
              + " and the tokenized session carries it.",
          "Personal Visa");

  static final ProviderOriginationFields DECLARED =
      new ProviderOriginationFields(
          AutraApi.AUTRA,
          List.of(
              new DeclaredOrigination(
                  A_HOLD,
                  "Reserve the amount on the card and capture it later",
                  "A pre-authorization that reserves the limit and charges nothing yet — \"O"
                      + " limite do cartao esta reservado, mas o cliente ainda nao foi"
                      + " cobrado\". Capture it for the whole amount or less, or cancel it, from"
                      + " the Payments screen; Autra publishes that a capture above the reserved"
                      + " value is refused. Autra publishes CREDIT as the only transactionType a"
                      + " pre-authorization accepts, so this simulator sends it and the wizard"
                      + " does not ask; debit is offered on the sale and nowhere else."
                      + IT_IS_LISTED_ALREADY,
                  AUTRA_DECIDES_THE_RESERVATION_AT_ONCE,
                  List.of(AMOUNT, CURRENCY, INSTALLMENTS, ORDER_ID)),
              new DeclaredOrigination(
                  A_CARD_SESSION,
                  "Open a hosted card page so a payer can store a card to charge later",
                  "A hosted card session waiting for its payer, with the hostedCardUrl autra"
                      + " publishes and an expiresAt exactly fifteen minutes after it was opened"
                      + " — \"E a criacao mais 15 minutos (TTL)\". Autra publishes that page on a"
                      + " second origin of its own, cards.autra.io, and this simulator serves it"
                      + " under its own base URL instead, so the hostedCardUrl points here. What"
                      + " tokenizing a card leaves behind is the slugToken and slugStoredCard an"
                      + " off-session sale charges. No money moves and no callback is queued:"
                      + " autra publishes no webhook domain for tokenization at all."
                      + IT_IS_LISTED_ALREADY,
                  THE_HOSTED_CARD_PAGE,
                  List.of(NICKNAME)),
              new DeclaredOrigination(
                  A_SALE,
                  "Charge a stored card the payer is not present for",
                  "A direct sale charged off-session against a stored card, ACCEPTED and captured"
                      + " the moment autra answers. Autra puts a decline of this call in the body"
                      + " and not in the status line, and publishes two statuses for that body on"
                      + " one page, so which one this account answers with is the setting "
                      + AutraSaleDecline.SETTING
                      + ". Reverse it from the Payments screen; autra publishes no capture on a"
                      + " sale, because a sale has nothing left reserved."
                      + IT_IS_LISTED_ALREADY,
                  THE_PAYER_IS_NOT_PRESENT_FOR_A_SALE,
                  List.of(
                      SALE_AMOUNT,
                      CURRENCY,
                      TRANSACTION_TYPE,
                      INSTALLMENTS,
                      SLUG_TOKEN,
                      SLUG_STORED_CARD,
                      SALE_ORDER_ID)),
              new DeclaredOrigination(
                  A_PIX,
                  "Hand out a Pix payload",
                  "A Pix waiting for its payer, PENDING with a copy-and-paste QR payload and an"
                      + " expiresAt. Autra publishes no status endpoint for a Pix at all — \"Sem"
                      + " endpoint publico de status\" — so the payer step on the Payments screen"
                      + " is what moves it to AUTHORIZED or EXPIRED, and the callback the merchant"
                      + " would receive is what reports it."
                      + IT_IS_LISTED_ALREADY,
                  THE_PAYER_PAYS_THE_QR_IN_THEIR_BANK_APP,
                  List.of(PIX_AMOUNT, CURRENCY, PIX_DESCRIPTION, PIX_ORDER_ID))),
          theFieldsTheSimulatorFillsIn());

  private static SequencedMap<String, String> theFieldsTheSimulatorFillsIn() {
    LinkedHashMap<String, String> filled = new LinkedHashMap<>();
    filled.put(
        AutraFields.DOCUMENT_ID,
        "autra carries the merchant on documentId, and that document is configured on the account"
            + " this payment is started on.");
    filled.put(
        AutraFields.TRANSACTIONAL_TOKEN,
        "autra requires an acquiring session token on this call and mints one at"
            + " POST /v1/acquiring/payments/initialize for a card and at"
            + " POST /v1/acquiring/pix/initialize for a Pix. The simulator opens a session for the"
            + " account and spends it here, so a payment started from this screen needs no token"
            + " of its own.");
    filled.put(
        AutraFields.CARD,
        "autra requires card or tokenData and never both, and this simulator reads nothing inside"
            + " card: doc/autra.md quotes one field name of the card object and none of its"
            + " types. A pre-authorization started from this screen therefore carries a card"
            + " object the simulator never reads and never stores, and the audit trail redacts it"
            + " whole. A sale started here carries tokenData instead, built from the stored-card"
            + " pair the tester names, because that is the instrument a sale charges off-session.");
    return filled;
  }

  private AutraOrigination() {}

  static AutraApiCall pixCallOf(
      OriginationRequest request, AutraMerchant merchant, String transactionalToken) {
    Map<String, AutraJson> body = new LinkedHashMap<>();
    body.put(AutraFields.TRANSACTIONAL_TOKEN, AutraJson.text(transactionalToken));
    body.put(AutraFields.DOCUMENT_ID, AutraJson.text(merchant.documentId()));
    body.put(
        AutraFields.AMOUNT, AutraJson.numberOrText(request.value(AutraFields.AMOUNT).orElse("0")));
    body.put(AutraFields.CURRENCY, AutraJson.text(request.value(AutraFields.CURRENCY).orElse("")));
    request
        .value(AutraFields.DESCRIPTION)
        .ifPresent(description -> body.put(AutraFields.DESCRIPTION, AutraJson.text(description)));
    request
        .value(AutraFields.ORDER_ID)
        .ifPresent(orderId -> body.put(AutraFields.ORDER_ID, AutraJson.text(orderId)));
    return AutraApiCall.of(
        WIRE_METHOD,
        AutraApiController.PIX_CHECKOUT,
        Map.of(),
        Map.of(),
        AutraJsonWriter.toText(AutraJson.object(body)));
  }

  static AutraApiCall cardSessionCallOf(OriginationRequest request, AutraMerchant merchant) {
    Map<String, AutraJson> body = new LinkedHashMap<>();
    body.put(AutraFields.DOCUMENT_ID, AutraJson.text(merchant.documentId()));
    request
        .value(AutraFields.NICKNAME)
        .ifPresent(nickname -> body.put(AutraFields.NICKNAME, AutraJson.text(nickname)));
    return AutraApiCall.of(
        WIRE_METHOD,
        AutraApiController.CARD_SESSIONS,
        Map.of(),
        Map.of(),
        AutraJsonWriter.toText(AutraJson.object(body)));
  }

  static AutraApiCall saleCallOf(
      OriginationRequest request, AutraMerchant merchant, String transactionalToken) {
    Map<String, AutraJson> body = new LinkedHashMap<>();
    body.put(AutraFields.TRANSACTIONAL_TOKEN, AutraJson.text(transactionalToken));
    body.put(AutraFields.DOCUMENT_ID, AutraJson.text(merchant.documentId()));
    body.put(
        AutraFields.TRANSACTION_TYPE,
        AutraJson.text(request.value(AutraFields.TRANSACTION_TYPE).orElse("")));
    body.put(
        AutraFields.AMOUNT, AutraJson.numberOrText(request.value(AutraFields.AMOUNT).orElse("0")));
    body.put(AutraFields.CURRENCY, AutraJson.text(request.value(AutraFields.CURRENCY).orElse("")));
    body.put(
        AutraFields.INSTALLMENTS,
        AutraJson.numberOrText(request.value(AutraFields.INSTALLMENTS).orElse("1")));
    request
        .value(AutraFields.ORDER_ID)
        .ifPresent(orderId -> body.put(AutraFields.ORDER_ID, AutraJson.text(orderId)));
    body.put(AutraFields.TOKEN_DATA, AutraJson.object(theTokenDataOf(request)));
    return AutraApiCall.of(
        WIRE_METHOD,
        AutraApiController.PAYMENTS,
        Map.of(),
        Map.of(),
        AutraJsonWriter.toText(AutraJson.object(body)));
  }

  private static Map<String, AutraJson> theTokenDataOf(OriginationRequest request) {
    Map<String, AutraJson> tokenData = new LinkedHashMap<>();
    request
        .value(AutraFields.SLUG_TOKEN)
        .ifPresent(slug -> tokenData.put(AutraFields.SLUG_TOKEN, AutraJson.text(slug)));
    request
        .value(AutraFields.SLUG_STORED_CARD)
        .ifPresent(slug -> tokenData.put(AutraFields.SLUG_STORED_CARD, AutraJson.text(slug)));
    return tokenData;
  }

  static AutraApiCall callOf(
      OriginationRequest request, AutraMerchant merchant, String transactionalToken) {
    Map<String, AutraJson> body = new LinkedHashMap<>();
    body.put(AutraFields.TRANSACTIONAL_TOKEN, AutraJson.text(transactionalToken));
    body.put(AutraFields.DOCUMENT_ID, AutraJson.text(merchant.documentId()));
    body.put(AutraFields.TRANSACTION_TYPE, AutraJson.text(AutraFields.CREDIT));
    body.put(
        AutraFields.AMOUNT, AutraJson.numberOrText(request.value(AutraFields.AMOUNT).orElse("0")));
    body.put(AutraFields.CURRENCY, AutraJson.text(request.value(AutraFields.CURRENCY).orElse("")));
    body.put(
        AutraFields.INSTALLMENTS,
        AutraJson.numberOrText(request.value(AutraFields.INSTALLMENTS).orElse("1")));
    request
        .value(AutraFields.ORDER_ID)
        .ifPresent(orderId -> body.put(AutraFields.ORDER_ID, AutraJson.text(orderId)));
    body.put(AutraFields.CARD, AutraJson.empty());
    return AutraApiCall.of(
        WIRE_METHOD,
        AutraApiController.PRE_AUTHORIZATION,
        Map.of(),
        Map.of(),
        AutraJsonWriter.toText(AutraJson.object(body)));
  }

  static OriginationOutcome outcomeOf(AutraOperationResult result, OriginationRequest request) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new OriginationOutcome.AnsweredByAScenarioInstead(String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new OriginationOutcome.RefusedByTheProvider(theWordsOf(result), result.scenarioId());
    }
    return new OriginationOutcome.Originated(
        String.valueOf(result.providerObjectId()),
        DECLARED.wayNamed(request.startedAs()).orElseThrow().whatItLeavesBehind(),
        result.scenarioId());
  }

  static String theWordsOf(AutraOperationResult result) {
    Map<String, List<String>> answered = result.body().fields();
    return "provider autra answered HTTP "
        + result.body().httpStatus()
        + " with "
        + AutraFields.ERRORS
        + "[]."
        + AutraFields.CODE
        + "="
        + firstOf(answered, AutraFields.ERRORS + "." + AutraFields.CODE)
        + " and "
        + AutraFields.MSG
        + "="
        + firstOf(answered, AutraFields.ERRORS + "." + AutraFields.MSG);
  }

  private static String firstOf(Map<String, List<String>> answered, String field) {
    return answered.getOrDefault(field, List.of()).stream().findFirst().orElse("");
  }
}
