package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.util.List;

final class WorldlineWireFaults {

  static final int METHOD_NOT_ALLOWED = 405;

  static final ProviderWireRoutes ROUTES =
      new ProviderWireRoutes(
          WorldlineApi.WORLDLINE,
          List.of(
              WorldlineApiController.VERSION, CreateHostedTokenization.THE_PUBLISHED_FORM_PATH));

  static final String NO_ROUTE_ANSWERS_THIS_ADDRESS =
      "no route of provider worldline answers this address. This answer carries no errors.id and"
          + " no errors.errorCode: the four published identifiers for something unknown name a"
          + " payment, a refund, a payout and a capture, every published errorCode is an"
          + " eight-digit number for a technical error or a transaction exception, and none of"
          + " them is about the address a request was sent to";

  static final String THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD =
      "provider worldline answers this address, and not with this HTTP method. 405 is outside the"
          + " published status list, which the same page instructs a caller to expect: \"Make"
          + " sure your application can handle HTTP error codes not included in this list.\" This"
          + " answer carries no errors.id and no errors.errorCode for the same reason an"
          + " unanswered address does";

  static final String WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT =
      "the statuses 500, 502 and 503, the category IO_ERROR as \"a technical error has occurred"
          + " within the payment platform or between the payment platform and third party"
          + " systems\", and an errorResponse whose errorId and errors are both required. What it"
          + " does not publish is an errors.id or an errors.errorCode for a failure of the"
          + " platform itself, and an envelope in worldline's field names would tell a client"
          + " that worldline answered when this simulator is what failed. The wire routes of this"
          + " build answer the same failure the same way, in text/plain, wherever it is raised";

  private final IdGenerator ids;

  WorldlineWireFaults(IdGenerator ids) {
    this.ids = ids;
  }

  ProviderWireAnswer theAnswerTo(WireFault fault) {
    return switch (fault) {
      case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
          envelopeOf(WorldlineAnswers.NOT_FOUND, NO_ROUTE_ANSWERS_THIS_ADDRESS);
      case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
          envelopeOf(METHOD_NOT_ALLOWED, THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD);
      case A_FAILURE_NOTHING_ELSE_ANSWERED ->
          new ProviderWireAnswer.NothingThisContractPublishes(
              WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
    };
  }

  private ProviderWireAnswer envelopeOf(int httpStatus, String message) {
    WorldlineJson.JsonObject body =
        WorldlineErrors.envelope(
            WorldlineIdentifiers.aUuid(ids),
            WorldlineErrors.item(
                null,
                WorldlineErrors.DIRECT_PLATFORM_ERROR,
                null,
                httpStatus,
                null,
                message,
                null,
                false));
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        httpStatus, WorldlineFields.JSON, WorldlineJsonWriter.toBytes(body));
  }
}
