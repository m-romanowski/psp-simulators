package dev.marcinromanowski.pspsim.kernel.bundle;

public record BundleEntry(String kind, String name, BundleVerdict verdict) {

  public static final String ACCOUNT = "account";

  public static final String WEBHOOK_TARGET = "webhookTarget";

  public static final String ROUTE_ALIAS = "routeAlias";

  public static final String SETTING = "setting";

  public static final String CLOCK = "clock";

  public static final String FIXTURE = "fixture";

  public static final String SCENARIO_RULE = "scenarioRule";

  public BundleEntry {
    if (kind.isBlank()) {
      throw new IllegalArgumentException(
          "an entry of an import report must name the kind of object it reports on, because a"
              + " reader counting what an import created cannot tell an account from a fixture by"
              + " its verdict alone");
    }
    if (name.isBlank()) {
      throw new IllegalArgumentException(
          "an entry of an import report must name the object it reports on, and a blank name"
              + " leaves a reader a verdict about nothing they could look up afterwards");
    }
  }

  public static BundleEntry created(String kind, String name) {
    return new BundleEntry(kind, name, BundleVerdict.CREATED);
  }

  public static BundleEntry updated(String kind, String name) {
    return new BundleEntry(kind, name, BundleVerdict.UPDATED);
  }

  public static BundleEntry skipped(String kind, String name) {
    return new BundleEntry(kind, name, BundleVerdict.SKIPPED);
  }
}
