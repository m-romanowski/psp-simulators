package dev.marcinromanowski.pspsim.kernel.repro;

public record CarriedContent(String content, int carried, String why) {

  public CarriedContent {
    if (content.isBlank()) {
      throw new IllegalArgumentException(
          "a content a bundle lists must be named, because a reader deciding whether a part of a"
              + " bundle can be replayed here reads the name and not the place in the list");
    }
    if (carried < 0) {
      throw new IllegalArgumentException(
          "a content a bundle lists carries nothing or something, and a negative count would read"
              + " as a part of the bundle that was removed rather than one that is empty");
    }
  }
}
