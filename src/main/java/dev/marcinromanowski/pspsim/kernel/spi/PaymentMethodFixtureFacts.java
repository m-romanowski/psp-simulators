package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class PaymentMethodFixtureFacts {

  private PaymentMethodFixtureFacts() {}

  public static Map<String, List<String>> of(PaymentMethodFixture fixture) {
    FixtureCard card = fixture.card();
    ActionFacts facts =
        ActionFacts.none()
            .and("fixtureId", fixture.fixtureId())
            .and("alias", fixture.fixtureAlias())
            .and("type", fixture.type().publishedName())
            .and("merchantId", fixture.merchantId())
            .and("ownerId", fixture.ownerId())
            .and("defaultScenario", fixture.defaultScenario())
            .andEach("providerIds", theKeysItCarries(fixture));
    if (card != null) {
      facts =
          facts
              .and("card.brand", card.brand())
              .and("card.last4", card.last4())
              .and("card.funding", card.funding());
    }
    return facts.asRecorded();
  }

  private static List<String> theKeysItCarries(PaymentMethodFixture fixture) {
    return new ArrayList<>(new TreeSet<>(fixture.providerIds().keySet()));
  }
}
