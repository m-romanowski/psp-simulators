package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.member;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.object;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StripeSpecEvidence {

  private static final List<StripeResponseEvolution.Snapshot> SNAPSHOTS = loadSnapshots();

  private StripeSpecEvidence() {}

  static List<StripeResponseEvolution.Snapshot> snapshots() {
    return SNAPSHOTS;
  }

  static StripeJson schemasAt(String version) throws IOException {
    StripeResponseEvolution evolution =
        new StripeResponseEvolution(
            StripeResponseEvolution.changes(
                StripeResponseConformanceTest.resource("breaking-changes.json")));
    Map<String, StripeJson> schemas =
        new LinkedHashMap<>(object(evolution.at(version, SNAPSHOTS)).members());
    StripeJson evidence = StripeResponseConformanceTest.resource("evidence-overrides.json");
    for (StripeJson patch : ((StripeJson.JsonArray) member(evidence, "fields")).elements()) {
      String name = ((StripeJson.JsonText) member(patch, "schema")).value();
      String field = ((StripeJson.JsonText) member(patch, "field")).value();
      Map<String, StripeJson> shape =
          new LinkedHashMap<>(object(member(new StripeJson.JsonObject(schemas), name)).members());
      Map<String, StripeJson> properties =
          new LinkedHashMap<>(
              object(member(new StripeJson.JsonObject(shape), "properties")).members());
      properties.put(field, member(patch, "shape"));
      shape.put("properties", new StripeJson.JsonObject(properties));
      schemas.put(name, new StripeJson.JsonObject(shape));
    }
    return new StripeJson.JsonObject(schemas);
  }

  private static List<StripeResponseEvolution.Snapshot> loadSnapshots() {
    try {
      List<StripeResponseEvolution.Snapshot> snapshots = new ArrayList<>();
      for (String version : StripeResponseConformanceTest.versions().toList()) {
        snapshots.add(
            new StripeResponseEvolution.Snapshot(
                version,
                member(StripeResponseConformanceTest.resource(version + ".json"), "schemas")));
      }
      snapshots.sort(Comparator.comparing(StripeResponseEvolution.Snapshot::version));
      return List.copyOf(snapshots);
    } catch (IOException unreadableSnapshot) {
      throw new UncheckedIOException(unreadableSnapshot);
    }
  }
}
