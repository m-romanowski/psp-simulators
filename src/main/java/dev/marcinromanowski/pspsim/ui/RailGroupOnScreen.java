package dev.marcinromanowski.pspsim.ui;

import java.util.List;

public record RailGroupOnScreen(String name, List<RailDestination> items) {

  public RailGroupOnScreen {
    items = List.copyOf(items);
  }
}
