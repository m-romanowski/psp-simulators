package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public sealed interface ProviderObjectPage {

  record Listed(List<PlacedProviderObject> placed) implements ProviderObjectPage {

    public Listed {
      placed = List.copyOf(placed);
      refuseAnythingButNewestFirst(placed);
      refuseTwoObjectsAnsweringToOneIdentifier(placed);
    }

    public List<ProviderObjectState> objects() {
      List<ProviderObjectState> objects = new ArrayList<>();
      for (PlacedProviderObject listed : placed) {
        objects.add(listed.object());
      }
      return List.copyOf(objects);
    }

    public @Nullable String cursorToCarryOnFrom(int pageSize) {
      if (placed.size() < pageSize) {
        return null;
      }
      return placed.get(placed.size() - 1).at().providerObjectId();
    }

    private static void refuseTwoObjectsAnsweringToOneIdentifier(
        List<PlacedProviderObject> placed) {
      Map<String, String> theObjectEachIdentifierReaches = new LinkedHashMap<>();
      for (PlacedProviderObject listed : placed) {
        String canonical = listed.object().identity().canonical();
        for (String member : listed.object().identity().members()) {
          String taken = theObjectEachIdentifierReaches.putIfAbsent(member, canonical);
          if (taken != null && !taken.equals(canonical)) {
            throw new IllegalArgumentException(
                "an identifier reaches one provider object, and this page lists two objects"
                    + " answering to one identifier, so a lookup by it would reach whichever the"
                    + " provider happened to search first and every row of the other would be"
                    + " read as the first one's");
          }
        }
      }
    }

    private static void refuseAnythingButNewestFirst(List<PlacedProviderObject> placed) {
      for (int following = 1; following < placed.size(); following++) {
        PlacedProviderObject earlier = placed.get(following - 1);
        PlacedProviderObject later = placed.get(following);
        if (!earlier.isNewerThan(later)) {
          throw new IllegalArgumentException(
              "a page of provider objects is newest first and every row strictly older than the"
                  + " one above it, and "
                  + later.at().providerObjectId()
                  + " is not older than "
                  + earlier.at().providerObjectId()
                  + ", so a reader paging on the id of the last row it saw would either loop on"
                  + " it or skip everything between the two");
        }
      }
    }
  }

  record NoSuchCursor() implements ProviderObjectPage {}
}
