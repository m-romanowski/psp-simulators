package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TranzilaObjectActions {

  static final ObjectActions DECLARED = new ObjectActions(TranzilaApi.TRANZILA, Map.of());

  static final String WIRE_METHOD = "POST";

  static final String AN_ITEM_NAME = "simulator";

  static final String THE_RUN_CONFIGURES_NO_TERMINAL =
      "this test run configures no provider tranzila account for the terminal this transaction"
          + " was made on, so no call of this run reaches it and the app key and secret that would"
          + " sign for it are not held here";

  private TranzilaObjectActions() {}

  static TranzilaApiCall callOf(
      ObjectActionRequest request, TranzilaObject object, TranzilaTerminal terminal, Instant now) {
    TranzilaPayment payment = object.payment();
    TranzilaTransaction root = object.subject();
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.TERMINAL_NAME, TranzilaJson.text(terminal.terminalName()));
    switch (request.action()) {
      case CAPTURE -> {
        body.put(TranzilaFields.TXN_TYPE, TranzilaJson.text(TranzilaOperations.FORCE));
        theCardOf(body, root);
        body.put(TranzilaFields.ITEMS, itemsOf(request.theAmountAsked().value()));
        referencing(body, root, false);
      }
      case CANCEL -> {
        TranzilaTransaction target = theOneACancelActsOn(object, now);
        body.put(
            TranzilaFields.TXN_TYPE,
            TranzilaJson.text(
                target.isTheOriginal() && payment.isAJ5StillHeldAt(now)
                    ? TranzilaOperations.REVERSAL
                    : TranzilaOperations.CANCEL));
        referencing(body, target, true);
      }
      case REFUND -> {
        TranzilaTransaction original =
            object.isTheOriginal() ? payment.whatACreditIsAgainst().orElse(root) : root;
        body.put(TranzilaFields.TXN_TYPE, TranzilaJson.text(TranzilaOperations.CREDIT));
        theCardOf(body, original);
        body.put(TranzilaFields.ITEMS, itemsOf(request.theAmountAsked().value()));
        referencing(body, original, true);
      }
    }
    return TranzilaApiCall.of(
        WIRE_METHOD,
        TranzilaApiController.CREATE,
        Map.of(),
        TranzilaJsonWriter.toText(TranzilaJson.object(body)));
  }

  static TranzilaTransaction theOneACancelActsOn(TranzilaObject object, Instant now) {
    TranzilaPayment payment = object.payment();
    if (!object.isTheOriginal()
        || payment.root().type() == TranzilaTxnType.DEBIT
        || payment.isAJ5StillHeldAt(now)) {
      return object.subject();
    }
    return payment.theForce().orElse(payment.root());
  }

  private static void theCardOf(Map<String, TranzilaJson> body, TranzilaTransaction of) {
    body.put(TranzilaFields.CARD_NUMBER, TranzilaJson.text(String.valueOf(of.token())));
    body.put(
        TranzilaFields.EXPIRE_MONTH,
        TranzilaJson.number(Integer.toString(of.card().expiryMonth())));
    body.put(
        TranzilaFields.EXPIRE_YEAR, TranzilaJson.number(Integer.toString(of.card().expiryYear())));
  }

  private static void referencing(
      Map<String, TranzilaJson> body, TranzilaTransaction target, boolean withTheAuthorization) {
    body.put(TranzilaFields.REFERENCE_TXN_ID, TranzilaJson.number(target.transactionIdAsText()));
    String authorization = target.authNumber();
    if (withTheAuthorization && authorization != null) {
      body.put(TranzilaFields.AUTHORIZATION_NUMBER, TranzilaJson.text(authorization));
    }
  }

  static TranzilaJson itemsOf(String amount) {
    Map<String, TranzilaJson> item = new LinkedHashMap<>();
    item.put(TranzilaFields.NAME, TranzilaJson.text(AN_ITEM_NAME));
    item.put(TranzilaFields.UNITS_NUMBER, TranzilaJson.number("1"));
    item.put(TranzilaFields.UNIT_PRICE, TranzilaJson.numberOrText(amount));
    return TranzilaJson.array(List.of(TranzilaJson.object(item)));
  }

  static ObjectActionOutcome outcomeOf(TranzilaOperationResult result, TranzilaObject acted) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new ObjectActionOutcome.AnsweredByAScenarioInstead(
          String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new ObjectActionOutcome.RefusedByTheProvider(theWordsOf(result), result.scenarioId());
    }
    return new ObjectActionOutcome.Applied(
        acted.objectId(), result.providerObjectId(), result.scenarioId());
  }

  static String theWordsOf(TranzilaOperationResult result) {
    Map<String, List<String>> answered = result.body().fields();
    return "provider tranzila answered HTTP "
        + result.body().httpStatus()
        + " with "
        + TranzilaFields.ERROR_CODE
        + "="
        + firstOf(answered, TranzilaFields.ERROR_CODE)
        + " and "
        + TranzilaFields.MESSAGE
        + "="
        + firstOf(answered, TranzilaFields.MESSAGE);
  }

  private static String firstOf(Map<String, List<String>> answered, String field) {
    return answered.getOrDefault(field, List.of()).stream().findFirst().orElse("");
  }

  static ObjectActionOutcome theRunConfiguresNoTerminal() {
    return new ObjectActionOutcome.RefusedByTheProvider(THE_RUN_CONFIGURES_NO_TERMINAL);
  }
}
