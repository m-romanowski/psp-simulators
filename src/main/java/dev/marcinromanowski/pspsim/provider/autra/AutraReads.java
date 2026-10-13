package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.AnsweredValue;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.Fact;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

final class AutraReads {

  static final String WIRE_METHOD = "GET";

  static final String ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS =
      WIRE_METHOD + " " + AutraApiController.OF_A_PAYMENT;

  static final String THE_PAYMENTS_OF_ONE_ESTABLISHMENT =
      WIRE_METHOD + " " + AutraApiController.PAYMENTS;

  static final String AN_ANSWERED_READ_OF_AUTRA_IS_ONE_JSON_OBJECT =
      "provider autra answers both of its read-only addresses with one JSON object, and this"
          + " answer carries something else, so the facts a screen would print are facts nothing"
          + " answered. This simulator builds those two answers in one place, AutraPaymentRead,"
          + " and an answer of any other shape reaching here is a change to that place and not an"
          + " answer to read facts out of";

  static final String THE_LISTING_ANSWERS_ITS_PAYMENTS_UNDER_ITEMS =
      "provider autra answers the listing as items, page, limit, hasMore, from and to, and this"
          + " answer carries no items array at all: the rows a screen would print are rows"
          + " nothing answered";

  static final String EVERY_ITEM_OF_THE_LISTING_IS_ONE_PAYMENT =
      "provider autra answers each item of the listing as one payment object, and this answer"
          + " carries an item that is not one";

  static final String A_PAYMENT_AUTRA_ANSWERS_NAMES_ITSELF_UNDER_PAYMENT_ID =
      "provider autra names every payment it answers under paymentID, and this answer carries a"
          + " payment that names none. A row with no identifier is a row a tester cannot follow"
          + " to the Payments screen, where the actions on that payment are";

  private static final AFieldATesterFillsIn THE_PAYMENT_THIS_RUN_HAS_ALREADY_TAKEN =
      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
          AutraFields.PAYMENT_ID_LOWERCASE_D,
          "the payment you want read back",
          true,
          "Provider autra draws a uuid for a payment when it charges a sale or takes a"
              + " reservation, answers it under paymentID and reads it back as paymentId in the"
              + " path of this route, which is the one spelling difference this contract carries."
              + " The box suggests the card payments the establishment you picked above has taken"
              + " in this test run, newest first, and takes any identifier you type instead:"
              + " autra is what reads this one, and it answers 400 "
              + AutraErrors.INVALID_PAYMENT_ID
              + " for an identifier not shaped like a uuid and 404 "
              + AutraErrors.PAYMENT_NOT_FOUND
              + " for a uuid no payment of this establishment answers to. A Pix of the same run"
              + " is not suggested: autra publishes no status endpoint for a Pix at all, and a"
              + " Pix is not a payment of this address.");

  private static final AFieldATesterFillsIn FROM =
      AFieldATesterFillsIn.typed(
          AutraFields.FROM,
          "the moment the window starts at",
          false,
          "Provider autra reads this as \"RFC3339 ou YYYY-MM-DD (UTC)\", so send either a whole"
              + " moment such as 2026-09-22T10:00:00Z or a bare date such as 2026-09-22, which"
              + " starts at the first moment of that day in UTC. Left empty, autra starts the"
              + " window "
              + AutraPaymentWindow.THE_WINDOW_A_CALL_THAT_NAMES_NONE_GETS
              + " days before it ends, which is the default window it publishes. A value of"
              + " neither shape is refused 400 "
              + AutraErrors.INVALID_FROM
              + " by autra, and a from after the to is refused 400 "
              + AutraErrors.INVALID_RANGE,
          "2026-09-22");

  private static final AFieldATesterFillsIn TO =
      AFieldATesterFillsIn.typed(
          AutraFields.TO,
          "the moment the window stops before",
          false,
          "The other end of the window, in the same two shapes, and a bare date here covers the"
              + " whole of that day. The window stops before this moment rather than on it, so a"
              + " payment autra took at exactly this moment is not in it. Left empty, autra"
              + " passes the moment this test run's clock is at. A window wider than the "
              + AutraPaymentWindow.LONGEST_WINDOW_IN_DAYS
              + " days autra publishes as its maximum is refused 400 "
              + AutraErrors.RANGE_TOO_LARGE
              + ", whichever end you widen.",
          "2026-09-23");

  private static final AFieldATesterFillsIn STATUS =
      AFieldATesterFillsIn.typed(
          AutraFields.STATUS,
          "the statuses to keep, separated by commas",
          false,
          "Provider autra publishes this as a \"lista separada por virgula (ex.:"
              + " ACCEPTED,CAPTURED)\" and names these and nothing else on this route: "
              + String.join(", ", AutraStatus.THE_VALUES_THE_LOOKUP_CALLS_USUAL)
              + ". It answers 400 "
              + AutraErrors.INVALID_STATUS
              + " for a value it does not name, and you type this rather than pick it for both"
              + " reasons: a list of two statuses is a value no list of single values could"
              + " offer, and the refusal autra publishes for an unpublished status is one a"
              + " tester has to be able to meet here. A card payment of this simulator is stored"
              + " at five of them, "
              + AutraStatus.ACCEPTED
              + ", "
              + AutraStatus.CAPTURED
              + ", "
              + AutraStatus.VOIDED
              + ", "
              + AutraStatus.PARTIALLY_VOIDED
              + " and "
              + AutraStatus.DECLINED
              + ", so the rest match nothing here and are still the vendor's own.",
          AutraStatus.ACCEPTED);

  private static final AFieldATesterFillsIn TYPE =
      AFieldATesterFillsIn.typed(
          AutraFields.TYPE,
          "the kind of payment to keep",
          false,
          "Provider autra enumerates "
              + String.join(" and ", AutraTimeline.THE_TYPES_A_PAYMENT_IS)
              + " on this route and nothing else, takes one value here and never a list, and"
              + " answers 400 "
              + AutraErrors.INVALID_TYPE
              + " for anything else, which is why this is typed rather than picked. A reservation"
              + " autra has captured is still a "
              + AutraTimeline.PREAUTH
              + ", because \"PREAUTH permanece mesmo apos captura\", so the pair autra gives as"
              + " its own example of this query, status "
              + AutraStatus.ACCEPTED
              + " with type "
              + AutraTimeline.PREAUTH
              + ", is what selects the reservations still holding a cardholder's limit.",
          AutraTimeline.PREAUTH);

  private static final AFieldATesterFillsIn PAGE =
      AFieldATesterFillsIn.typed(
          AutraFields.PAGE,
          "which page of the listing to answer",
          false,
          "Pages are counted from "
              + ListPayments.SMALLEST_PAGE
              + " and this is page "
              + ListPayments.THE_PAGE_A_CALL_THAT_NAMES_NONE_GETS
              + " when left empty. Provider autra publishes no upper bound for it, and a page"
              + " past the end answers no payment at all with hasMore false rather than a"
              + " refusal. Anything that is not a whole number of "
              + ListPayments.SMALLEST_PAGE
              + " or more is refused 400 "
              + AutraErrors.INVALID_PAGE,
          "1");

  private static final AFieldATesterFillsIn LIMIT =
      AFieldATesterFillsIn.typed(
          AutraFields.LIMIT,
          "how many payments one page carries",
          false,
          "From "
              + ListPayments.SMALLEST_LIMIT
              + " to "
              + ListPayments.LARGEST_LIMIT
              + ", and "
              + ListPayments.THE_LIMIT_A_CALL_THAT_NAMES_NONE_GETS
              + " when left empty, which are provider autra's own bounds and default. Anything"
              + " else is refused 400 "
              + AutraErrors.INVALID_LIMIT
              + ". The answer says whether a page follows this one, because autra publishes"
              + " neither a total nor a cursor on this address.",
          "50");

  private static final String IT_MOVES_NOTHING =
      " Autra publishes this address as \"Somente leitura — nunca chama o adquirente\", so asking"
          + " it twice leaves this test run exactly as it was.";

  static final String THE_ESTABLISHMENT_IS_THE_ACCOUNTS_OWN =
      "Autra names the establishment by the documentId configured on the account you pick, which"
          + " this simulator sends in the "
          + AutraFields.MERCHANT_DOCUMENT_HEADER
          + " header autra calls the preferred one because it \"mantem o documento fora da URL e"
          + " dos logs de acesso\", so you are never asked to type it and never read another"
          + " establishment's payments by accident. Autra publishes \"um dos dois (header ou"
          + " documentId) e obrigatorio\" and refuses a call that carries neither 400 "
          + AutraErrors.MISSING_DOCUMENT_ID
          + ", a document that is not \"CPF ou CNPJ, sem mascara\" 422 "
          + AutraErrors.INVALID_DOCUMENT
          + " and a document no establishment of this token answers to 404 "
          + AutraErrors.MERCHANT_NOT_FOUND
          + ", which are refusals a tester meets on the wire and not here.";

  static final ProviderReads DECLARED =
      ProviderReads.theOnesItPublishes(
          AutraApi.AUTRA,
          List.of(
              new DeclaredRead(
                  ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS,
                  "Read one payment back with every step it has been through",
                  "A merchant whose own call never answered, or whose webhook never arrived, asks"
                      + " autra this one. It answers every property autra publishes for a payment"
                      + " and the timeline of every step that payment has been through, which is"
                      + " the half of this read no other surface of this simulator prints as"
                      + " autra answers it."
                      + IT_MOVES_NOTHING,
                  DeclaredRead.EmptyAnswer.itNeverAnswersNothingBecause(
                      "Autra answers the whole payment or refuses the call, and it publishes one"
                          + " refusal for all three of \"inexistente, de outro tenant ou de outro"
                          + " estabelecimento\": "
                          + AutraErrors.PAYMENT_NOT_FOUND
                          + " at 404, and "
                          + AutraErrors.INVALID_PAYMENT_ID
                          + " at 400 for an identifier that is not shaped like a uuid. A refusal"
                          + " is printed in autra's own words, so a payment this establishment"
                          + " does not hold is read as the refusal autra publishes for it and"
                          + " never as an answer with nothing in it."),
                  List.of(THE_PAYMENT_THIS_RUN_HAS_ALREADY_TAKEN),
                  theDocumentTheSimulatorFillsIn()),
              new DeclaredRead(
                  THE_PAYMENTS_OF_ONE_ESTABLISHMENT,
                  "Find the payments one establishment took in a window of dates",
                  "This is the question autra publishes the listing for: what has this"
                      + " establishment taken, newest first, and which reservations is it still"
                      + " holding, so a void can release a cardholder's limit instead of waiting"
                      + " for a daily routine. It reads what you type before it answers anything,"
                      + " and every refusal below is autra's own: an unpublished status 400 "
                      + AutraErrors.INVALID_STATUS
                      + ", a type it does not enumerate 400 "
                      + AutraErrors.INVALID_TYPE
                      + ", a window past its published maximum 400 "
                      + AutraErrors.RANGE_TOO_LARGE
                      + ". The window and the page it applied are answered beside the payments"
                      + " themselves, so a page that has more behind it says so."
                      + IT_MOVES_NOTHING,
                  DeclaredRead.EmptyAnswer.whatItMeans(
                      "Autra answers an empty items list, which is its answer and not a failed"
                          + " read: this establishment took no payment that falls inside the"
                          + " window and matches the status and the type you named. The window it"
                          + " applied is answered beside the payments whether any matched or not,"
                          + " and when you named neither end it is the last "
                          + AutraPaymentWindow.THE_WINDOW_A_CALL_THAT_NAMES_NONE_GETS
                          + " days, ending at the moment this test run's clock is at, so a"
                          + " payment taken under a clock you have since moved can fall outside"
                          + " it. A Pix of this run is never listed here whatever you ask: this"
                          + " address answers the card payments of one establishment, and a Pix"
                          + " is on its own Pix screen."),
                  List.of(FROM, TO, STATUS, TYPE, PAGE, LIMIT),
                  theDocumentTheSimulatorFillsIn())));

  private static SequencedMap<String, String> theDocumentTheSimulatorFillsIn() {
    LinkedHashMap<String, String> filled = new LinkedHashMap<>();
    filled.put(AutraFields.MERCHANT_DOCUMENT_HEADER, THE_ESTABLISHMENT_IS_THE_ACCOUNTS_OWN);
    return filled;
  }

  private AutraReads() {}

  static ReadOutcome theAnswerTo(
      ReadRequest request,
      AutraMerchant merchant,
      LookUpPayment lookUpPayment,
      ListPayments listPayments,
      Instant now) {
    if (ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS.equals(request.readNamed())) {
      String paymentId = request.required(AutraFields.PAYMENT_ID_LOWERCASE_D);
      return theOnePaymentItAnswered(
          lookUpPayment.handle(
              theLookUpCallOf(paymentId, merchant), request.testRunId(), merchant, paymentId));
    }
    return theListingItAnswered(
        listPayments.handle(
            theListingCallOf(request, merchant), request.testRunId(), merchant, now));
  }

  static AutraApiCall theLookUpCallOf(String paymentId, AutraMerchant merchant) {
    return AutraApiCall.of(
        WIRE_METHOD,
        AutraApiController.PAYMENTS + "/" + paymentId,
        theDocumentHeaderOf(merchant),
        Map.of(),
        "");
  }

  static AutraApiCall theListingCallOf(ReadRequest request, AutraMerchant merchant) {
    Map<String, List<String>> query = new LinkedHashMap<>();
    for (Map.Entry<String, String> asked : request.values().entrySet()) {
      query.put(asked.getKey(), List.of(asked.getValue()));
    }
    return AutraApiCall.of(
        WIRE_METHOD, AutraApiController.PAYMENTS, theDocumentHeaderOf(merchant), query, "");
  }

  static ReadOutcome theOnePaymentItAnswered(AutraOperationResult result) {
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return theRefusalOf(result);
    }
    return new ReadOutcome.Answered(
        List.of(theRowOfOnePayment(theObjectItAnswered(result))), result.scenarioId());
  }

  static ReadOutcome theListingItAnswered(AutraOperationResult result) {
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return theRefusalOf(result);
    }
    AutraJson.JsonObject answered = theObjectItAnswered(result);
    List<AnsweredRow> rows = new ArrayList<>();
    for (AutraJson item : theItemsIn(answered)) {
      rows.add(theRowOfOnePayment(theOnePaymentOf(item)));
    }
    return new ReadOutcome.Answered(
        rows, theWindowAndThePageItAnsweredBesideTheItems(answered), result.scenarioId());
  }

  static List<AValueAFieldOffers> thePaymentsThisTestRunHasAlreadyTaken(List<AutraPayment> held) {
    List<AValueAFieldOffers> offered = new ArrayList<>();
    for (AutraPayment payment : held) {
      offered.add(
          new AValueAFieldOffers(payment.paymentId(), theWayATesterTellsThemApart(payment)));
    }
    return List.copyOf(offered);
  }

  static List<Fact> theFactsOf(AutraJson.JsonObject answered) {
    List<Fact> facts = new ArrayList<>();
    for (Map.Entry<String, AutraJson> member : answered.members().entrySet()) {
      facts.add(new Fact(member.getKey(), theValueAutraAnsweredUnderIt(member.getValue())));
    }
    return List.copyOf(facts);
  }

  private static ReadOutcome theRefusalOf(AutraOperationResult result) {
    return new ReadOutcome.RefusedByTheProvider(
        AutraOrigination.theWordsOf(result), result.scenarioId());
  }

  private static Map<String, List<String>> theDocumentHeaderOf(AutraMerchant merchant) {
    return Map.of(AutraFields.MERCHANT_DOCUMENT_HEADER, List.of(merchant.documentId()));
  }

  private static AutraJson.JsonObject theObjectItAnswered(AutraOperationResult result) {
    if (result.body().json() instanceof AutraJson.JsonObject answered) {
      return answered;
    }
    throw new IllegalStateException(AN_ANSWERED_READ_OF_AUTRA_IS_ONE_JSON_OBJECT);
  }

  private static List<AutraJson> theItemsIn(AutraJson.JsonObject answered) {
    if (answered.member(AutraFields.ITEMS) instanceof AutraJson.JsonArray items) {
      return items.elements();
    }
    throw new IllegalStateException(THE_LISTING_ANSWERS_ITS_PAYMENTS_UNDER_ITEMS);
  }

  private static AutraJson.JsonObject theOnePaymentOf(AutraJson item) {
    if (item instanceof AutraJson.JsonObject payment) {
      return payment;
    }
    throw new IllegalStateException(EVERY_ITEM_OF_THE_LISTING_IS_ONE_PAYMENT);
  }

  private static AnsweredRow theRowOfOnePayment(AutraJson.JsonObject payment) {
    return new AnsweredRow(
        payment
            .text(AutraFields.PAYMENT_ID_UPPERCASE_D)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        A_PAYMENT_AUTRA_ANSWERS_NAMES_ITSELF_UNDER_PAYMENT_ID)),
        theFactsOf(payment));
  }

  static List<Fact> theWindowAndThePageItAnsweredBesideTheItems(AutraJson.JsonObject answered) {
    Map<String, AutraJson> besideTheItems = new LinkedHashMap<>(answered.members());
    besideTheItems.remove(AutraFields.ITEMS);
    return theFactsOf(AutraJson.object(besideTheItems));
  }

  private static AnsweredValue theValueAutraAnsweredUnderIt(AutraJson answered) {
    return switch (answered) {
      case AutraJson.JsonText text -> new AnsweredValue.TheValueItAnswered(text.value());
      case AutraJson.JsonNumber number -> new AnsweredValue.TheValueItAnswered(number.lexical());
      case AutraJson.JsonFlag flag ->
          new AnsweredValue.TheValueItAnswered(Boolean.toString(flag.value()));
      case AutraJson.JsonNothing _ -> new AnsweredValue.NoValueAtAll();
      case AutraJson.JsonObject nested -> new AnsweredValue.TheMembersUnderIt(theFactsOf(nested));
      case AutraJson.JsonArray elements -> theElementsAutraAnsweredUnderIt(elements);
    };
  }

  private static AnsweredValue theElementsAutraAnsweredUnderIt(AutraJson.JsonArray elements) {
    List<AnsweredValue> answered = new ArrayList<>();
    for (AutraJson element : elements.elements()) {
      answered.add(theValueAutraAnsweredUnderIt(element));
    }
    return new AnsweredValue.TheElementsUnderIt(answered);
  }

  private static String theWayATesterTellsThemApart(AutraPayment payment) {
    String orderId = payment.orderId();
    return "the "
        + payment.type()
        + " of "
        + payment.amount().value()
        + " "
        + payment.currency()
        + " autra took at "
        + CreatePixCheckout.asAutraWritesIt(payment.createdAt())
        + ", which holds "
        + payment.nativeStatus()
        + (orderId == null ? "" : ", your order " + orderId);
  }
}
