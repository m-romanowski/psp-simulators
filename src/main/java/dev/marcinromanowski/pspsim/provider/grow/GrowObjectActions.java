package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GrowObjectActions {

  static final String NO_CANCEL =
      "provider grow publishes no cancel and no void for a card transaction. The only way it"
          + " documents for ending a hold without a payment is settleSuspendedTransaction for the"
          + " full held amount followed by refundTransaction for that same amount, so capture it"
          + " in full and then refund it in full; the end state grow reports is REFUNDED and not"
          + " CANCELLED. The cancelBitTransaction endpoint this build exposes belongs to Bit and"
          + " answers a card transaction with nothing else.";

  static final ObjectActions DECLARED =
      new ObjectActions(GrowLightApi.GROW, Map.of(ObjectAction.CANCEL, NO_CANCEL));

  private static final String ERROR_ID = "err.id";

  private static final String ERROR_MESSAGE = "err.message";

  private GrowObjectActions() {}

  static GrowForm formOf(
      ObjectActionRequest request, GrowAccount account, GrowTransaction transaction) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put(GrowFields.USER_ID, List.of(account.userId()));
    fields.put(GrowFields.TRANSACTION_ID, List.of(transaction.transactionId()));
    String token = transaction.transactionToken();
    if (token != null) {
      fields.put(GrowFields.TRANSACTION_TOKEN, List.of(token));
    }
    fields.put(amountFieldOf(request.action()), List.of(request.theAmountAsked().value()));
    return GrowForm.of(fields);
  }

  private static String amountFieldOf(ObjectAction action) {
    return action == ObjectAction.REFUND ? GrowFields.REFUND_SUM : GrowFields.SUM;
  }

  static ObjectActionOutcome outcomeOf(GrowOperationResult result, GrowTransaction acted) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new ObjectActionOutcome.AnsweredByAScenarioInstead(
          String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new ObjectActionOutcome.RefusedByTheProvider(theWordsOf(result), result.scenarioId());
    }
    String answered = String.valueOf(result.providerObjectId());
    return new ObjectActionOutcome.Applied(
        acted.transactionId(),
        answered.equals(acted.transactionId()) ? null : answered,
        result.scenarioId());
  }

  static ObjectActionOutcome theRunConfiguresNoAccountFor(GrowTransaction transaction) {
    return new ObjectActionOutcome.RefusedByTheProvider(
        wordedAs(GrowErrors.noBusinessForTheUserId())
            + ". This test run configures no grow account under the merchant id "
            + transaction.merchantId()
            + ", which is the account this transaction was charged on, so no credential this run"
            + " holds reaches it");
  }

  private static String theWordsOf(GrowOperationResult result) {
    Map<String, List<String>> answered = result.body().flatten();
    String id = firstOf(answered, ERROR_ID);
    String message = firstOf(answered, ERROR_MESSAGE);
    return message.isBlank() ? id : id + " " + message;
  }

  private static String firstOf(Map<String, List<String>> answered, String name) {
    return answered.getOrDefault(name, List.of("")).get(0);
  }

  private static String wordedAs(GrowError error) {
    return error.id() + " " + error.message();
  }
}
