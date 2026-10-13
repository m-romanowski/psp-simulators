package dev.marcinromanowski.pspsim.provider.tranzila;

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

final class TranzilaOrigination {

  static final String A_DEBIT = "POST /v1/transaction/credit_card/create debit";

  static final String A_J5 = "POST /v1/transaction/credit_card/create verify 5";

  static final String AMOUNT = "amount";

  private static final String IT_IS_LISTED_ALREADY =
      " The object exists the moment this screen answers: it is on the Payments screen now, under"
          + " the transaction_id above, and the actions offered there act on it.";

  private static final ThePayerStep NO_PAYER_PAGE =
      new ThePayerStep.NoPageThisSimulatorServes(
          "Nothing waits for a payer here. The card API charges a card number or a token the"
              + " merchant already holds, so tranzila answers this call with the transaction"
              + " already decided and there is no page and no payer step at all.");

  private static final AFieldATesterFillsIn THE_AMOUNT =
      AFieldATesterFillsIn.typed(
          AMOUNT,
          "amount in currency units",
          true,
          "Tranzila publishes no amount field: the amount is the sum of unit_price times"
              + " units_number over items, in decimal currency units, so 10.53 is ten shekels and"
              + " fifty-three agorot. This screen sends it as one item.",
          "10.53");

  private static final AFieldATesterFillsIn THE_CURRENCY =
      AFieldATesterFillsIn.chosenFrom(
          TranzilaFields.TXN_CURRENCY_CODE,
          "currency",
          true,
          "Tranzila publishes txn_currency_code as ISO 4217 alpha-3, ILS by default, from four"
              + " values.",
          List.of(
              new AValueAFieldOffers("ILS", "Israeli new shekel"),
              new AValueAFieldOffers("USD", "US dollar"),
              new AValueAFieldOffers("EUR", "Euro"),
              new AValueAFieldOffers("GBP", "Pound sterling")));

  private static final AFieldATesterFillsIn THE_CARD =
      AFieldATesterFillsIn.typed(
          TranzilaFields.CARD_NUMBER,
          "card number or token",
          true,
          "Tranzila takes a card number or, in its place, a token: \"A Tranzila token replaces"
              + " only the full card number\". Any 8 to 19 digits are charged as a card; a token"
              + " this run's transactions answered, or one seeded as a payment-method fixture, is"
              + " charged as the card it stands for.",
          null);

  private static final AFieldATesterFillsIn THE_EXPIRY_MONTH =
      AFieldATesterFillsIn.typed(
          TranzilaFields.EXPIRE_MONTH,
          "expiry month",
          true,
          "Tranzila requires expire_month, 1 to 12, also beside a token.",
          "12");

  private static final AFieldATesterFillsIn THE_EXPIRY_YEAR =
      AFieldATesterFillsIn.typed(
          TranzilaFields.EXPIRE_YEAR,
          "expiry year",
          true,
          "Tranzila requires expire_year, written in four digits in its example, also beside a"
              + " token.",
          "2030");

  private static final List<AFieldATesterFillsIn> THE_FIELDS =
      List.of(THE_AMOUNT, THE_CURRENCY, THE_CARD, THE_EXPIRY_MONTH, THE_EXPIRY_YEAR);

  static final ProviderOriginationFields DECLARED =
      new ProviderOriginationFields(
          TranzilaApi.TRANZILA,
          List.of(
              new DeclaredOrigination(
                  A_DEBIT,
                  "Charge a card",
                  "A debit, approved 000 and charged at once, with nothing left to force or"
                      + " reverse; credit it back or cancel it from the Payments screen."
                      + IT_IS_LISTED_ALREADY,
                  NO_PAYER_PAGE,
                  THE_FIELDS),
              new DeclaredOrigination(
                  A_J5,
                  "Hold an amount on a card (J5)",
                  "A J5 that reserves the amount and charges nothing yet, approved with the code"
                      + " this account names, and live for the J5 validity this account names;"
                      + " capture it with a force, or cancel it with a reversal, from the Payments"
                      + " screen."
                      + IT_IS_LISTED_ALREADY,
                  NO_PAYER_PAGE,
                  THE_FIELDS)),
          theFieldsTheSimulatorFillsIn());

  private static SequencedMap<String, String> theFieldsTheSimulatorFillsIn() {
    LinkedHashMap<String, String> filled = new LinkedHashMap<>();
    filled.put(
        TranzilaFields.TERMINAL_NAME,
        "tranzila requires terminal_name in every body, and it is configured on the account this"
            + " payment is started on.");
    filled.put(
        TranzilaFields.ACCESS_TOKEN,
        "tranzila authenticates every call with four headers, and a payment started from this"
            + " screen never reaches the wire, so there is nothing for it to be signed with and"
            + " nothing to verify.");
    return filled;
  }

  private TranzilaOrigination() {}

  static TranzilaApiCall callOf(OriginationRequest request, TranzilaTerminal terminal) {
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.TERMINAL_NAME, TranzilaJson.text(terminal.terminalName()));
    if (A_J5.equals(request.startedAs())) {
      body.put(TranzilaFields.TXN_TYPE, TranzilaJson.text(TranzilaOperations.VERIFY));
      body.put(TranzilaFields.VERIFY_MODE, TranzilaJson.number("5"));
    } else {
      body.put(TranzilaFields.TXN_TYPE, TranzilaJson.text(TranzilaOperations.DEBIT));
    }
    body.put(
        TranzilaFields.CARD_NUMBER,
        TranzilaJson.text(request.value(TranzilaFields.CARD_NUMBER).orElse("")));
    body.put(
        TranzilaFields.EXPIRE_MONTH,
        TranzilaJson.numberOrText(request.value(TranzilaFields.EXPIRE_MONTH).orElse("")));
    body.put(
        TranzilaFields.EXPIRE_YEAR,
        TranzilaJson.numberOrText(request.value(TranzilaFields.EXPIRE_YEAR).orElse("")));
    request
        .value(TranzilaFields.TXN_CURRENCY_CODE)
        .ifPresent(
            currency -> body.put(TranzilaFields.TXN_CURRENCY_CODE, TranzilaJson.text(currency)));
    body.put(TranzilaFields.ITEMS, TranzilaObjectActions.itemsOf(request.value(AMOUNT).orElse("")));
    return TranzilaApiCall.of(
        TranzilaObjectActions.WIRE_METHOD,
        TranzilaApiController.CREATE,
        Map.of(),
        TranzilaJsonWriter.toText(TranzilaJson.object(body)));
  }

  static OriginationOutcome outcomeOf(TranzilaOperationResult result, OriginationRequest request) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new OriginationOutcome.AnsweredByAScenarioInstead(String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new OriginationOutcome.RefusedByTheProvider(
          TranzilaObjectActions.theWordsOf(result), result.scenarioId());
    }
    return new OriginationOutcome.Originated(
        String.valueOf(result.providerObjectId()),
        DECLARED.wayNamed(request.startedAs()).orElseThrow().whatItLeavesBehind(),
        result.scenarioId());
  }
}
