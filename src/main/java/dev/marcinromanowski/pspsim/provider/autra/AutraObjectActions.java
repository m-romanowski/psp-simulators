package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import java.util.LinkedHashMap;
import java.util.Map;

final class AutraObjectActions {

  static final ObjectActions DECLARED = new ObjectActions(AutraApi.AUTRA, Map.of());

  static final String THE_RUN_CONFIGURES_NO_MERCHANT =
      "this test run configures no provider autra account under the documentId this payment was"
          + " reserved for, so no acquiring call of this run reaches it and the credentials that"
          + " would answer for it are not held here";

  private AutraObjectActions() {}

  static AutraApiCall callOf(
      ObjectActionRequest request, AutraPayment payment, String transactionalToken) {
    Map<String, AutraJson> body = new LinkedHashMap<>();
    body.put(AutraFields.TRANSACTIONAL_TOKEN, AutraJson.text(transactionalToken));
    body.put(AutraFields.DOCUMENT_ID, AutraJson.text(payment.documentId()));
    body.put(AutraFields.AMOUNT, AutraJson.numberOrText(theAmountItSends(request, payment)));
    if (request.action() != ObjectAction.CAPTURE) {
      body.put(AutraFields.CURRENCY, AutraJson.text(payment.currency()));
    }
    return AutraApiCall.of(
        AutraOrigination.WIRE_METHOD,
        theRouteOf(request, payment),
        Map.of(),
        Map.of(),
        AutraJsonWriter.toText(AutraJson.object(body)));
  }

  private static String theRouteOf(ObjectActionRequest request, AutraPayment payment) {
    String of = AutraApiController.PAYMENTS + "/" + payment.paymentId();
    return request.action() == ObjectAction.CAPTURE ? of + "/capture" : of + "/void";
  }

  private static String theAmountItSends(ObjectActionRequest request, AutraPayment payment) {
    return request.action().takesAnAmount()
        ? request.theAmountAsked().value()
        : ReverseOrRelease.theRemainingBalanceOf(payment).toPlainString();
  }

  static ObjectActionOutcome outcomeOf(AutraOperationResult result, AutraPayment acted) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new ObjectActionOutcome.AnsweredByAScenarioInstead(
          String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new ObjectActionOutcome.RefusedByTheProvider(
          AutraOrigination.theWordsOf(result), result.scenarioId());
    }
    return new ObjectActionOutcome.Applied(acted.paymentId(), null, result.scenarioId());
  }

  static ObjectActionOutcome theRunConfiguresNoMerchantFor(AutraPayment payment) {
    return new ObjectActionOutcome.RefusedByTheProvider(THE_RUN_CONFIGURES_NO_MERCHANT);
  }
}
