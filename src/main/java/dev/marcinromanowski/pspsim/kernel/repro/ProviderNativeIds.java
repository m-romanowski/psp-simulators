package dev.marcinromanowski.pspsim.kernel.repro;

import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableFixture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProviderNativeIds {

  public static final String REMAP = "remapProviderIds";

  public static final String NOTHING_THIS_BUNDLE_CARRIES_ANSWERS_TO_IT =
      "a remapping replaces a provider-native id this bundle's instruments carry, and no instrument"
          + " of this bundle carries that id under a key that names one instrument, so the"
          + " replacement would be written nowhere and the ids the bundle does carry would be"
          + " preserved without anyone being told";

  public static final String A_KEY_THAT_NAMES_AN_OWNER_IS_SHARED_ON_PURPOSE =
      "the only key this bundle carries that id under names the owner an instrument belongs to, and"
          + " this provider lets many instruments of one run name one owner, so that id cannot"
          + " collide and remapping it would rename an owner rather than free an identifier";

  private ProviderNativeIds() {}

  public sealed interface Preservation {

    record Refused(List<BundleRefusal> refusals) implements Preservation {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }

    record Conflicting(List<BundleRefusal> refusals) implements Preservation {

      public Conflicting {
        refusals = List.copyOf(refusals);
      }
    }

    record Preserved(List<PortableFixture> fixtures, List<String> remapped)
        implements Preservation {

      public Preserved {
        fixtures = List.copyOf(fixtures);
        remapped = List.copyOf(remapped);
      }
    }
  }

  public static Preservation of(
      List<PortableFixture> carried,
      Map<String, String> remapping,
      Set<String> theKeysManyFixturesMayShare,
      List<ClaimedProviderNativeId> theRunAlreadyClaims) {
    List<BundleRefusal> refused =
        theRemappingsNoInstrumentOfThisBundleAnswersTo(
            carried, remapping, theKeysManyFixturesMayShare);
    if (!refused.isEmpty()) {
      return new Preservation.Refused(refused);
    }

    List<PortableFixture> asWritten = new ArrayList<>();
    List<String> remapped = new ArrayList<>();
    for (PortableFixture fixture : carried) {
      asWritten.add(rewritten(fixture, remapping, theKeysManyFixturesMayShare, remapped));
    }

    List<BundleRefusal> conflicting =
        theIdsTheTargetRunAlreadyClaims(
            asWritten, theKeysManyFixturesMayShare, theRunAlreadyClaims, remapping);
    if (!conflicting.isEmpty()) {
      return new Preservation.Conflicting(conflicting);
    }
    return new Preservation.Preserved(List.copyOf(asWritten), List.copyOf(remapped));
  }

  public static String theProperty(int place, String key) {
    return "setup.fixtures[" + place + "].providerIds." + key;
  }

  private static List<BundleRefusal> theRemappingsNoInstrumentOfThisBundleAnswersTo(
      List<PortableFixture> carried,
      Map<String, String> remapping,
      Set<String> theKeysManyFixturesMayShare) {
    Set<String> exclusive = new LinkedHashSet<>();
    Set<String> shared = new LinkedHashSet<>();
    for (PortableFixture fixture : carried) {
      for (Map.Entry<String, String> id : fixture.carriedProviderIds().entrySet()) {
        if (theKeysManyFixturesMayShare.contains(id.getKey())) {
          shared.add(id.getValue());
          continue;
        }
        exclusive.add(id.getValue());
      }
    }
    List<BundleRefusal> refused = new ArrayList<>();
    for (String from : remapping.keySet()) {
      if (exclusive.contains(from)) {
        continue;
      }
      refused.add(
          new BundleRefusal(
              REMAP + "." + from,
              shared.contains(from)
                  ? A_KEY_THAT_NAMES_AN_OWNER_IS_SHARED_ON_PURPOSE
                  : NOTHING_THIS_BUNDLE_CARRIES_ANSWERS_TO_IT));
    }
    return List.copyOf(refused);
  }

  private static PortableFixture rewritten(
      PortableFixture fixture,
      Map<String, String> remapping,
      Set<String> theKeysManyFixturesMayShare,
      List<String> remapped) {
    LinkedHashMap<String, String> ids = new LinkedHashMap<>();
    boolean changed = false;
    for (Map.Entry<String, String> id : fixture.carriedProviderIds().entrySet()) {
      String to =
          theKeysManyFixturesMayShare.contains(id.getKey()) ? null : remapping.get(id.getValue());
      if (to == null) {
        ids.put(id.getKey(), id.getValue());
        continue;
      }
      ids.put(id.getKey(), to);
      remapped.add(id.getKey());
      changed = true;
    }
    if (!changed) {
      return fixture;
    }
    return new PortableFixture(
        fixture.merchantId(),
        fixture.ownerId(),
        fixture.type(),
        fixture.alias(),
        fixture.card(),
        Collections.unmodifiableSequencedMap(ids),
        fixture.triggersRuleAt());
  }

  private static List<BundleRefusal> theIdsTheTargetRunAlreadyClaims(
      List<PortableFixture> asWritten,
      Set<String> theKeysManyFixturesMayShare,
      List<ClaimedProviderNativeId> theRunAlreadyClaims,
      Map<String, String> remapping) {
    List<BundleRefusal> conflicting = new ArrayList<>();
    for (int place = 0; place < asWritten.size(); place++) {
      for (Map.Entry<String, String> id : asWritten.get(place).carriedProviderIds().entrySet()) {
        if (theKeysManyFixturesMayShare.contains(id.getKey())) {
          continue;
        }
        for (ClaimedProviderNativeId claimed : theRunAlreadyClaims) {
          if (!claimed.key().equals(id.getKey()) || !claimed.value().equals(id.getValue())) {
            continue;
          }
          conflicting.add(
              new BundleRefusal(
                  theProperty(place, id.getKey()),
                  theRunAlreadyHoldsIt(
                      claimed, id.getKey(), remapping.containsValue(id.getValue()))));
        }
      }
    }
    return List.copyOf(conflicting);
  }

  private static String theRunAlreadyHoldsIt(
      ClaimedProviderNativeId claimed, String key, boolean itWasRemappedOnto) {
    return "this test run already holds the instrument "
        + claimed.byInstrument()
        + " under providerIds."
        + key
        + ", and a run gives that id to one instrument, so the instrument this bundle carries could"
        + " not be told apart from it on any read route. "
        + (itWasRemappedOnto
            ? "The id was asked for by the remapping this import supplied, so name a free one"
                + " there instead."
            : "Provider-native ids are preserved unless a remapping is supplied, so supply one"
                + " for this id under "
                + REMAP
                + ", forget that instrument on the Payment methods screen, or import into another"
                + " test run.");
  }
}
