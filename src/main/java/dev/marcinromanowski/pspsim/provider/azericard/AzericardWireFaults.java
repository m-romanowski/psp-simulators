package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import java.util.List;

final class AzericardWireFaults {

  static final String GATEWAY = "/cgi-bin";

  static final String TOKEN_GATEWAY = "/token";

  static final String PAYER_PAGE = "/azericard";

  static final ProviderWireRoutes ROUTES =
      new ProviderWireRoutes(
          AzericardGateway.AZERICARD, List.of(GATEWAY, TOKEN_GATEWAY, PAYER_PAGE));

  static final String WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD =
      "one answer for a request it will not process, observed on the live gateway and served here"
          + " for a missing required field: HTTP 200, text/html, a human page carrying no ACTION,"
          + " no RC and no signature, whose only published variation is the line \""
          + AzericardErrorPage.OPENING
          + "\" followed by Error message naming the field. That page cannot stand in for an"
          + " address azericard does not serve or a method one of its addresses does not answer:"
          + " its varying line names a request field, and there is none to name, while HTTP 200"
          + " would tell a client the gateway had processed the request. Azericard publishes two"
          + " routes and no behaviour for anything beside them";

  static final ProviderWireAnswer THE_ANSWER =
      new ProviderWireAnswer.NothingThisContractPublishes(WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD);

  private AzericardWireFaults() {}
}
