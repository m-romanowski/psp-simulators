package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.ThisSimulatorsOwnAddresses;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ProviderWireRoutes(ProviderId provider, List<String> namespaces) {

  public ProviderWireRoutes {
    if (namespaces.isEmpty()) {
      throw new IllegalArgumentException(
          "a provider module that names no address of its own cannot be asked what it answers"
              + " when a client gets one of its addresses wrong, and the client would be handed"
              + " this application's own answer instead of the vendor's");
    }
    Set<String> named = new LinkedHashSet<>();
    for (String namespace : namespaces) {
      if (!namespace.startsWith("/") || namespace.equals("/")) {
        throw new IllegalArgumentException(
            "a wire namespace is an absolute path under which only this provider answers, and "
                + namespace
                + " is not one; a namespace of / would claim every address of this application,"
                + " including the screens");
      }
      if (ThisSimulatorsOwnAddresses.claims(namespace)) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " names the wire namespace "
                + namespace
                + ", which lies inside "
                + ThisSimulatorsOwnAddresses.RESERVED_FOR_THIS_SIMULATOR
                + ", addresses this simulator serves itself; the module whose namespace covers an"
                + " address is asked what that address answers, so such a namespace would have a"
                + " vendor envelope answer a tester on the control API or on a screen");
      }
      if (!named.add(namespace)) {
        throw new IllegalArgumentException(
            "provider " + provider.value() + " names the wire namespace " + namespace + " twice");
      }
    }
    namespaces = List.copyOf(namespaces);
  }

  public static final int COVERS_NOTHING = -1;

  public int howDeeplyItCovers(String address) {
    int deepest = COVERS_NOTHING;
    for (String namespace : namespaces) {
      if (isUnder(address, namespace)) {
        deepest = Math.max(deepest, theSegmentsOf(namespace));
      }
    }
    return deepest;
  }

  static int theSegmentsOf(String namespace) {
    int segments = 0;
    for (String segment : namespace.split("/", -1)) {
      if (!segment.isEmpty()) {
        segments++;
      }
    }
    return segments;
  }

  public static void refuseTwoClaimingOneNamespace(List<SimulatorProvider> providers) {
    for (int one = 0; one < providers.size(); one++) {
      for (int other = 0; other < providers.size(); other++) {
        if (one == other) {
          continue;
        }
        ProviderWireRoutes first = providers.get(one).wireRoutes();
        ProviderWireRoutes second = providers.get(other).wireRoutes();
        for (String namespace : first.namespaces()) {
          if (second.howDeeplyItCovers(namespace) == theSegmentsOf(namespace)) {
            throw new IllegalStateException(
                "providers "
                    + first.provider().value()
                    + " and "
                    + second.provider().value()
                    + " both claim the wire namespace "
                    + namespace
                    + ", and the address a client gets wrong under it is answered by the provider"
                    + " whose namespace covers it most deeply, so two claims of one depth would"
                    + " leave the answer to the order the modules happened to be listed in");
          }
        }
      }
    }
  }

  public static Optional<SimulatorProvider> theDeepestClaimOn(
      List<SimulatorProvider> providers, String address) {
    SimulatorProvider claiming = null;
    int deepest = COVERS_NOTHING;
    for (SimulatorProvider provider : providers) {
      int depth = provider.wireRoutes().howDeeplyItCovers(address);
      if (depth > deepest) {
        deepest = depth;
        claiming = provider;
      }
    }
    return Optional.ofNullable(claiming);
  }

  public boolean covers(String address) {
    for (String namespace : namespaces) {
      if (isUnder(address, namespace)) {
        return true;
      }
    }
    return false;
  }

  public List<String> theNamespacesUnder(String path) {
    List<String> under = new ArrayList<>();
    for (String namespace : namespaces) {
      if (isUnder(namespace, path)) {
        under.add(namespace);
      }
    }
    return List.copyOf(under);
  }

  public static boolean isUnder(String inner, String outer) {
    return inner.equals(outer) || inner.startsWith(withinIt(outer));
  }

  private static String withinIt(String namespace) {
    return namespace.endsWith("/") ? namespace : namespace + "/";
  }
}
