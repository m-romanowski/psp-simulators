package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AzericardObjectActions {

  static final ObjectActions DECLARED = new ObjectActions(AzericardGateway.AZERICARD, Map.of());

  static final String A_STORED_CARD_IS_NOT_A_TRANSACTION =
      "provider azericard answers "
          + AzericardFields.ACTION
          + "="
          + AzericardOutcomes.ACTION_ERROR
          + " "
          + AzericardFields.RC
          + "="
          + AzericardOutcomes.RC_NO_SUCH_TRANSACTION
          + " to a settlement naming no transaction, and a stored card is not one. The only"
          + " fields azericard publishes for "
          + AzericardTransactionType.SALES_COMPLETION.operationName()
          + ", "
          + AzericardTransactionType.ONLINE_REVERSAL.operationName()
          + " and "
          + AzericardTransactionType.OFFLINE_REVERSAL.operationName()
          + " to address a transaction by are "
          + AzericardFields.RRN
          + " and "
          + AzericardFields.INT_REF
          + ", both of them from an authorization response, and a saved-card "
          + AzericardFields.TOKEN
          + " is neither. Pay with the token on the token route first, and act on the payment"
          + " that call creates.";

  private AzericardObjectActions() {}

  static AzericardMessage messageOf(
      ObjectActionRequest request, AzericardPayment payment, IdGenerator ids, Instant now) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put(AzericardFields.TERMINAL, List.of(payment.terminalId()));
    fields.put(AzericardFields.TRTYPE, List.of(theTypeThatCarries(request.action()).wireValue()));
    fields.put(AzericardFields.ORDER, List.of(payment.order()));
    fields.put(AzericardFields.AMOUNT, List.of(theAmountItSends(request, payment).value()));
    fields.put(AzericardFields.CURRENCY, List.of(payment.currency()));
    fields.put(AzericardFields.RRN, List.of(payment.rrn()));
    fields.put(AzericardFields.INT_REF, List.of(payment.intRef()));
    fields.put(AzericardFields.TIMESTAMP, List.of(AzericardTimestamp.write(now)));
    fields.put(
        AzericardFields.NONCE,
        List.of(ids.hex(AzericardCallbacks.NONCE_LENGTH).toUpperCase(Locale.ROOT)));
    return AzericardMessage.of(AzericardRoute.CGI_LINK, fields);
  }

  private static AzericardTransactionType theTypeThatCarries(ObjectAction action) {
    return action == ObjectAction.CAPTURE
        ? AzericardTransactionType.SALES_COMPLETION
        : AzericardTransactionType.ONLINE_REVERSAL;
  }

  private static Amount theAmountItSends(ObjectActionRequest request, AzericardPayment payment) {
    return request.action().takesAnAmount()
        ? request.theAmountAsked()
        : Amount.of(payment.reversibleAmount());
  }

  static ObjectActionOutcome outcomeOf(AzericardOperationResult result, AzericardPayment acted) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new ObjectActionOutcome.AnsweredByAScenarioInstead(
          String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new ObjectActionOutcome.RefusedByTheProvider(theWordsOf(result), result.scenarioId());
    }
    return new ObjectActionOutcome.Applied(acted.intRef(), null, result.scenarioId());
  }

  static ObjectActionOutcome theRunConfiguresNoTerminalFor(AzericardPayment payment) {
    return new ObjectActionOutcome.RefusedByTheProvider(
        AzericardErrorPage.messageFor(AzericardFields.REQUIRED_TERMINAL)
            + ". This test run configures no provider azericard account under the terminal "
            + payment.terminalId()
            + ", which is the "
            + AzericardFields.TERMINAL
            + " this payment was authorized on, so no request of this run reaches it and the"
            + " gateway key that signs its answers is not held here");
  }

  private static String theWordsOf(AzericardOperationResult result) {
    Map<String, List<String>> answered = result.body().fields();
    return "provider azericard answered "
        + AzericardFields.ACTION
        + "="
        + firstOf(answered, AzericardResponse.ACTION_ELEMENT)
        + " "
        + AzericardFields.RC
        + "="
        + firstOf(answered, AzericardResponse.RC_ELEMENT)
        + " "
        + AzericardFields.DESC
        + "="
        + firstOf(answered, AzericardResponse.DESC_ELEMENT);
  }

  private static String firstOf(Map<String, List<String>> answered, String element) {
    return answered.getOrDefault(element, List.of()).stream().findFirst().orElse("");
  }
}
