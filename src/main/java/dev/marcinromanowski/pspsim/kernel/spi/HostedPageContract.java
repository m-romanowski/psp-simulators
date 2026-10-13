package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public sealed interface HostedPageContract {

  record AnObjectItKeepsWaiting(String asThisProviderNamesIt, boolean aPayerNamesACardOnIt) {

    public AnObjectItKeepsWaiting {
      if (asThisProviderNamesIt.isBlank()) {
        throw new IllegalArgumentException(
            "a provider module that keeps an object waiting for a payer must name that object in"
                + " its own words, because a screen listing this provider among the ones that host"
                + " a page would otherwise have nothing to call what waits");
      }
    }
  }

  record ObjectsAPayerFinishes(List<AnObjectItKeepsWaiting> theObjectsItKeepsWaiting)
      implements HostedPageContract {

    public ObjectsAPayerFinishes {
      if (theObjectsItKeepsWaiting.isEmpty()) {
        throw new IllegalArgumentException(
            "a provider module whose contract keeps objects waiting for a payer must name at least"
                + " one of them, because a screen counting what waits would read this as a"
                + " provider that hosts a page and keeps nothing on it; a module that keeps none"
                + " says so as NoPageAPayerFinishes and names what it has instead");
      }
      Set<String> named = new LinkedHashSet<>();
      for (AnObjectItKeepsWaiting waiting : theObjectsItKeepsWaiting) {
        if (!named.add(waiting.asThisProviderNamesIt())) {
          throw new IllegalArgumentException(
              "a provider module names the object "
                  + waiting.asThisProviderNamesIt()
                  + " twice among the ones it keeps waiting for a payer, so a screen would print"
                  + " one object under two entries and a count of what waits would be one too"
                  + " many");
        }
      }
      theObjectsItKeepsWaiting = List.copyOf(theObjectsItKeepsWaiting);
    }

    public List<String> asThisProviderNamesThem() {
      List<String> named = new ArrayList<>();
      for (AnObjectItKeepsWaiting waiting : theObjectsItKeepsWaiting) {
        named.add(waiting.asThisProviderNamesIt());
      }
      return List.copyOf(named);
    }

    public int howManyOfThemAPayerNamesACardOn() {
      int naming = 0;
      for (AnObjectItKeepsWaiting waiting : theObjectsItKeepsWaiting) {
        if (waiting.aPayerNamesACardOnIt()) {
          naming++;
        }
      }
      return naming;
    }
  }

  record NoPageAPayerFinishes(String whatThisProviderHasInstead) implements HostedPageContract {

    public NoPageAPayerFinishes {
      if (whatThisProviderHasInstead.isBlank()) {
        throw new IllegalArgumentException(
            "a provider module whose contract publishes no page a payer finishes must say what it"
                + " has instead, because a screen reporting that absence in this simulator's words"
                + " would be reporting a decision this simulator did not make");
      }
    }
  }
}
