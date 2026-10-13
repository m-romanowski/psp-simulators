package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.List;

public final class ProviderObjectPages {

  private ProviderObjectPages() {}

  public static ProviderObjectPage.Listed newestFirst(
      List<PlacedProviderObject> found, int pageSize) {
    List<PlacedProviderObject> ordered = new ArrayList<>(found);
    ordered.sort(PlacedProviderObject::newestFirstAgainst);
    List<PlacedProviderObject> page = new ArrayList<>();
    for (PlacedProviderObject placed : ordered) {
      if (page.size() == pageSize) {
        break;
      }
      page.add(placed);
    }
    return new ProviderObjectPage.Listed(page);
  }
}
