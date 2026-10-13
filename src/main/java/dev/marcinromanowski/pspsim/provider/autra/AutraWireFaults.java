package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.util.List;

final class AutraWireFaults {

  static final String OAUTH = "/v1/oauth";

  static final String ACQUIRING = "/v1/acquiring";

  static final String CARD_PAGE = "/tokenize/sessions";

  static final int WHAT_NOTHING_DECIDED = 500;

  static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  static final ProviderWireRoutes ROUTES =
      new ProviderWireRoutes(AutraApi.AUTRA, List.of(OAUTH, ACQUIRING, CARD_PAGE));

  static final String WHAT_AUTRA_PUBLISHES_FOR_A_FAILURE_INSIDE_IT = "Falha interna.";

  static final String WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD =
      "two error shapes and a catalogue of codes for the operations it serves: the acquiring"
          + " errors[] of code and msg, and the single error string of the OAuth token route."
          + " Every code in that catalogue is about the call autra read — PAYMENT_NOT_FOUND and"
          + " MERCHANT_NOT_FOUND are about a payment and a merchant the body named, INVALID_JSON"
          + " is about a body this endpoint could not read, and BAD_REQUEST is about a required"
          + " field. None of them is about an address autra does not serve or a method one of"
          + " its addresses does not answer; its OpenAPI publishes no catch-all response for"
          + " either. The one 404 recorded outside the catalogue is DEPLOYMENT_NOT_FOUND, which"
          + " is a dead Vercel preview answering for the documentation site and not for the API"
          + " at all. A failure inside autra it does publish, but only on the acquiring"
          + " namespace: the token route publishes no 5xx of any kind, and the hosted card page"
          + " is html rather than an envelope";

  private AutraWireFaults() {}

  static ProviderWireAnswer theAnswerTo(WireFault fault, String address) {
    if (fault != WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED
        || !underTheAcquiringNamespace(address)) {
      return new ProviderWireAnswer.NothingThisContractPublishes(
          WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD);
    }
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        WHAT_NOTHING_DECIDED,
        AutraFields.JSON,
        AutraJsonWriter.toBytes(
            AutraErrors.envelope(INTERNAL_ERROR, WHAT_AUTRA_PUBLISHES_FOR_A_FAILURE_INSIDE_IT)));
  }

  static boolean underTheAcquiringNamespace(String address) {
    return address.equals(ACQUIRING) || address.startsWith(ACQUIRING + "/");
  }
}
