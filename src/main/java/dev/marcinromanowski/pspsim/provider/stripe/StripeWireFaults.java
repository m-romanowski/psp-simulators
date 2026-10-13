package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.util.List;

final class StripeWireFaults {

  static final ProviderWireRoutes ROUTES =
      new ProviderWireRoutes(
          StripeApi.STRIPE,
          List.of(
              StripeApiController.PAYMENT_INTENTS,
              StripeApiController.SETUP_INTENTS,
              StripeApiController.PAYMENT_METHODS,
              StripeApiController.CUSTOMERS,
              StripeApiController.CHARGES,
              StripeApiController.REFUNDS,
              StripeApiController.EPHEMERAL_KEYS));

  static final String NO_ROUTE_ANSWERS_THIS_ADDRESS =
      "The requested resource doesn't exist: no route of provider stripe answers this address."
          + " Stripe publishes no error code for an unrecognized address, so this answer carries"
          + " none, and resource_missing is about an id a request named rather than about the"
          + " address it was sent to.";

  static final String THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD =
      "The requested resource doesn't exist: provider stripe answers this address, and not with"
          + " this HTTP method. Stripe's published list of the statuses it returns is 200, 400,"
          + " 401, 402, 403, 404, 409, 424, 429, 500, 502, 503 and 504, which does not include"
          + " 405, so a method an address does not answer is answered as the address not"
          + " existing.";

  static final String WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT =
      "four error types, of which api_error is the only one that is about neither the request nor"
          + " the card nor the idempotency key, and the statuses 500, 502, 503 and 504 with the"
          + " instruction to \"Treat requests that return 500 errors as indeterminate\". What it"
          + " does not publish is a code, a message or a body for a failure inside the gateway,"
          + " and an error envelope carrying only a type would tell a client that stripe had"
          + " answered when this simulator is what failed. The wire routes of this build answer"
          + " the same failure the same way, in text/plain, wherever it is raised";

  private StripeWireFaults() {}

  static ProviderWireAnswer theAnswerTo(WireFault fault) {
    return switch (fault) {
      case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE -> envelopeOf(NO_ROUTE_ANSWERS_THIS_ADDRESS);
      case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
          envelopeOf(THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD);
      case A_FAILURE_NOTHING_ELSE_ANSWERED ->
          new ProviderWireAnswer.NothingThisContractPublishes(
              WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
    };
  }

  private static ProviderWireAnswer envelopeOf(String message) {
    StripeError error =
        new StripeError(
            StripeErrors.NOT_FOUND,
            StripeErrorType.INVALID_REQUEST_ERROR,
            null,
            null,
            message,
            null,
            null,
            null);
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        error.httpStatus(), StripeApi.CONTENT_TYPE, StripeJsonWriter.toBytes(error.body()));
  }
}
