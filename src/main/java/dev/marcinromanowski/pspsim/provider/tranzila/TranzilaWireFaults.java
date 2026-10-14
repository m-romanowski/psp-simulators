package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.util.List;

final class TranzilaWireFaults {

  static final ProviderWireRoutes ROUTES =
      new ProviderWireRoutes(TranzilaApi.TRANZILA, List.of(TranzilaApiController.TRANSACTION));

  static final String THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD =
      "This address does not answer this HTTP method";

  static final String WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT =
      "an Error schema of error_code and message for 400, 401 and 404, and application error"
          + " codes answered at 200. It publishes no 5xx at all, and an Error body in tranzila's"
          + " field names would tell a client that tranzila answered when this simulator is what"
          + " failed";

  private TranzilaWireFaults() {}

  static ProviderWireAnswer theAnswerTo(WireFault fault) {
    return switch (fault) {
      case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
          errorSchema(
              TranzilaAnswers.NOT_FOUND,
              TranzilaErrors.RESOURCE_NOT_FOUND,
              TranzilaErrors.RESOURCE_NOT_FOUND_TEXT);
      case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
          errorSchema(
              TranzilaAnswers.METHOD_NOT_ALLOWED,
              TranzilaErrors.METHOD_NOT_ALLOWED,
              THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD);
      case A_FAILURE_NOTHING_ELSE_ANSWERED ->
          new ProviderWireAnswer.NothingThisContractPublishes(
              WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
    };
  }

  private static ProviderWireAnswer errorSchema(int httpStatus, String code, String message) {
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        httpStatus,
        TranzilaFields.JSON,
        TranzilaJsonWriter.toBytes(TranzilaAnswers.theErrorSchema(code, message)));
  }
}
