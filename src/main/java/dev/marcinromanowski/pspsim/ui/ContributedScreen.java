package dev.marcinromanowski.pspsim.ui;

public record ContributedScreen(
    String provider, String screen, String label, RailGroup group, int order) {

  public static final String WHERE_THEY_LIVE = "src/main/resources/templates/extensions/";

  public String file() {
    return WHERE_THEY_LIVE + provider + "/" + screen + ".html";
  }

  public String path() {
    return ContributedScreenController.UNDER + provider + "/" + screen;
  }

  public String view() {
    return "extensions/" + provider + "/" + screen;
  }

  public String asAScreenNamesItself() {
    return "x/" + provider + "/" + screen;
  }

  public RailDestination asATab() {
    return new RailDestination(group, label, path(), asAScreenNamesItself(), order, true);
  }
}
