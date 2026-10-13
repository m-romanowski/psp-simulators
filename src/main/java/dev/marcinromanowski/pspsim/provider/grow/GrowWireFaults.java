package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import java.util.List;

final class GrowWireFaults {

  static final String API = "/api/light";

  static final ProviderWireRoutes ROUTES =
      new ProviderWireRoutes(GrowLightApi.GROW, List.of(API, GrowHostedPage.PATH));

  static final String WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD =
      "one rule about its statuses and one error row that is itself a status. The rule is that"
          + " every answer on POST /api/light/server/1.0/<name> is HTTP 200, with the outcome in"
          + " the body as status 0 beside an err object. The row is 403 Forbidden, which belongs"
          + " to the x-api-key endpoints. Neither covers an address grow does not serve, a method"
          + " one of its addresses does not answer, or a failure inside the gateway: no code in"
          + " the published error table names any of the three, and 170 transaction does not"
          + " exist and 180 record not found are about a record the operation looked for rather"
          + " than about the address it was asked at. Answering the envelope at HTTP 200 would"
          + " tell a client that grow ran the operation and refused it, which is a different"
          + " thing from grow never having seen the request at all";

  static final ProviderWireAnswer THE_ANSWER =
      new ProviderWireAnswer.NothingThisContractPublishes(WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD);

  private GrowWireFaults() {}
}
