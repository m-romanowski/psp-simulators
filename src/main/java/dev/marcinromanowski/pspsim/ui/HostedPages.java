package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HostedPages {

  public record Split(
      List<String> thatKeepAnObjectAPayerFinishes,
      List<String> withNoPageAPayerFinishes,
      int howManyObjectsWait,
      String theObjectsTheyKeep) {

    public Split {
      thatKeepAnObjectAPayerFinishes = List.copyOf(thatKeepAnObjectAPayerFinishes);
      withNoPageAPayerFinishes = List.copyOf(withNoPageAPayerFinishes);
    }

    public int providers() {
      return thatKeepAnObjectAPayerFinishes.size() + withNoPageAPayerFinishes.size();
    }

    public String theObjectsAsACount() {
      return howManyObjectsWait + (howManyObjectsWait == 1 ? " object" : " objects");
    }
  }

  private record Hosting(String provider, List<String> theObjectsItKeepsWaiting) {}

  private HostedPages() {}

  public static Split of(List<SimulatorProvider> modules) {
    List<Hosting> hosting = new ArrayList<>();
    List<String> without = new ArrayList<>();
    for (SimulatorProvider module : modules) {
      switch (module.hostedPages()) {
        case HostedPageContract.ObjectsAPayerFinishes keeping ->
            hosting.add(new Hosting(module.id().value(), keeping.asThisProviderNamesThem()));
        case HostedPageContract.NoPageAPayerFinishes _ -> without.add(module.id().value());
      }
    }
    hosting.sort(Comparator.comparing(Hosting::provider));
    without.sort(Comparator.naturalOrder());
    List<String> named = new ArrayList<>();
    List<String> described = new ArrayList<>();
    int waiting = 0;
    for (Hosting keeping : hosting) {
      named.add(keeping.provider());
      described.add(
          keeping.provider() + " " + asOneEnglishPhrase(keeping.theObjectsItKeepsWaiting()));
      waiting += keeping.theObjectsItKeepsWaiting().size();
    }
    return new Split(
        List.copyOf(named), List.copyOf(without), waiting, String.join("; ", described));
  }

  static String asOneEnglishPhrase(List<String> objects) {
    if (objects.size() == 1) {
      return objects.get(0);
    }
    List<String> allButTheLast = objects.subList(0, objects.size() - 1);
    return String.join(", ", allButTheLast) + " and " + objects.get(objects.size() - 1);
  }
}
