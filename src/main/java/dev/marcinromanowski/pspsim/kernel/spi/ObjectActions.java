package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.Map;

public record ObjectActions(ProviderId provider, Map<ObjectAction, String> whatItHasInstead) {

  public ObjectActions {
    for (Map.Entry<ObjectAction, String> absent : whatItHasInstead.entrySet()) {
      if (absent.getValue().isBlank()) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " offers no way to "
                + absent.getKey().asked()
                + ", and must say in its own words what its contract has in place of it, because"
                + " a screen reporting that absence in this simulator's words would be reporting"
                + " a decision this simulator did not make");
      }
    }
    whatItHasInstead = Map.copyOf(whatItHasInstead);
  }

  public boolean offers(ObjectAction action) {
    return !whatItHasInstead.containsKey(action);
  }

  public String whatItHasInsteadOf(ObjectAction action) {
    String instead = whatItHasInstead.get(action);
    if (instead == null) {
      throw new IllegalStateException(
          "provider "
              + provider.value()
              + " offers "
              + action.name()
              + ", so it publishes nothing in place of it");
    }
    return instead;
  }
}
