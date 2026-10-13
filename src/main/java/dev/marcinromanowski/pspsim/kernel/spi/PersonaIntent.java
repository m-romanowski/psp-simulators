package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum PersonaIntent {
  A_SAVED_CARD_THAT_SUCCEEDS(
      "a-saved-card-that-succeeds",
      "A stored instrument this provider will hold and then capture without a hitch, for a test"
          + " that needs a payment to get all the way through."),
  A_SAVED_CARD_THAT_IS_DECLINED(
      "a-saved-card-that-is-declined",
      "A stored instrument whose capture the provider declines, for a test that needs the failure"
          + " path a tester cannot get from a sandbox on demand."),
  A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST(
      "a-saved-card-whose-capture-answer-is-lost",
      "A stored instrument whose capture the provider takes and never answers, for a test of the"
          + " one case a merchant integration cannot tell apart from a failure by looking at its"
          + " own request.");

  private final String publishedName;

  private final String whatATesterGets;

  PersonaIntent(String publishedName, String whatATesterGets) {
    this.publishedName = publishedName;
    this.whatATesterGets = whatATesterGets;
  }

  public String publishedName() {
    return publishedName;
  }

  public String whatATesterGets() {
    return whatATesterGets;
  }

  public static Optional<PersonaIntent> of(String publishedName) {
    for (PersonaIntent intent : values()) {
      if (intent.publishedName.equals(publishedName)) {
        return Optional.of(intent);
      }
    }
    return Optional.empty();
  }

  public static String thePublishedNames() {
    List<String> names = new ArrayList<>();
    for (PersonaIntent intent : values()) {
      names.add(intent.publishedName);
    }
    return String.join(", ", names);
  }
}
