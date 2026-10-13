package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import java.util.LinkedHashMap;
import java.util.Map;

final class WorldlineObjectActions {

  static final String THE_REFUND_ROUTE_IS_OUT_OF_THIS_SLICE =
      "provider worldline publishes a refund, POST /v2/{merchantId}/payments/{paymentId}/refund,"
          + " and this slice does not serve it. The reason is this simulator's and not the"
          + " vendor's: the operator scoped this module to hosted tokenization, a"
          + " pre-authorization with its capture and its cancel, and an off-session charge, and"
          + " put the refund outside it. What worldline publishes in its place, for a payment"
          + " whose funds the acquirer has not yet moved, is the refund route's own deletion"
          + " branch — \"Depending on whether your acquirer has already transferred funds or not"
          + " at the time of the refund request, the payment will be either refunded"
          + " (statusOutput.statusCode=81) or deleted (statusOutput.statusCode=71)\" — and that"
          + " branch is on the same route and is out of this slice with it";

  static final ObjectActions DECLARED =
      new ObjectActions(
          WorldlineApi.WORLDLINE,
          Map.of(ObjectAction.REFUND, THE_REFUND_ROUTE_IS_OUT_OF_THIS_SLICE));

  static final String THE_RUN_CONFIGURES_NO_MERCHANT =
      "this test run configures no provider worldline account under the merchantId this payment"
          + " was created for, so no call of this run reaches it and the apiKey and apiSecret"
          + " that would sign for it are not held here";

  private WorldlineObjectActions() {}

  static WorldlineApiCall callOf(ObjectActionRequest request, WorldlineMerchant merchant) {
    Map<String, WorldlineJson> body = new LinkedHashMap<>();
    if (request.action() == ObjectAction.CAPTURE) {
      body.put(
          WorldlineFields.AMOUNT, WorldlineJson.numberOrText(request.theAmountAsked().value()));
      body.put(WorldlineFields.IS_FINAL, WorldlineJson.flag(false));
    }
    return WorldlineApiCall.of(
        WorldlineOrigination.WIRE_METHOD,
        theRouteOf(request, merchant),
        Map.of(),
        Map.of(),
        WorldlineJsonWriter.toText(WorldlineJson.object(body)));
  }

  private static String theRouteOf(ObjectActionRequest request, WorldlineMerchant merchant) {
    String of =
        WorldlineApiController.thePaymentResourceOf(
            merchant.merchantId(), request.providerObjectId());
    return request.action() == ObjectAction.CAPTURE
        ? of + WorldlineApiController.CAPTURE
        : of + WorldlineApiController.CANCEL;
  }

  static ObjectActionOutcome outcomeOf(WorldlineOperationResult result, WorldlinePayment acted) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new ObjectActionOutcome.AnsweredByAScenarioInstead(
          String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new ObjectActionOutcome.RefusedByTheProvider(
          WorldlineOrigination.theWordsOf(result), result.scenarioId());
    }
    return new ObjectActionOutcome.Applied(acted.paymentId(), null, result.scenarioId());
  }

  static ObjectActionOutcome theRunConfiguresNoMerchantFor(WorldlinePayment payment) {
    return new ObjectActionOutcome.RefusedByTheProvider(THE_RUN_CONFIGURES_NO_MERCHANT);
  }
}
