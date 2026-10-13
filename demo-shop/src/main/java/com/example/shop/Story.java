package com.example.shop;

import java.util.Arrays;
import java.util.Optional;

public enum Story {
  A_CAPTURE_THAT_GOES_THROUGH(
      "a-capture-that-goes-through",
      "A capture that goes through",
      "The happy path, for comparison. The hold is placed, the capture is accepted, and Stripe"
          + " tells the shop about the finished payment with a signed webhook.",
      null),
  A_DECLINE_THE_TEST_ASKED_FOR(
      "a-decline-the-test-asked-for",
      "A decline the test asked for",
      "The story writes a scenario rule before anyone shops: every capture of this run is refused"
          + " because the card has expired. The hold is placed as usual, and the capture comes"
          + " back refused, in Stripe's own words.",
      "expired_card");

  private final String slug;
  private final String title;
  private final String whatHappens;
  private final String theCaptureIsRefusedFor;

  Story(String slug, String title, String whatHappens, String theCaptureIsRefusedFor) {
    this.slug = slug;
    this.title = title;
    this.whatHappens = whatHappens;
    this.theCaptureIsRefusedFor = theCaptureIsRefusedFor;
  }

  public static Story calledBy(String slug) {
    return Arrays.stream(values())
        .filter(story -> story.slug.equals(slug))
        .findFirst()
        .orElseThrow(() -> new NoSuchStory(slug));
  }

  public String slug() {
    return slug;
  }

  public String title() {
    return title;
  }

  public String whatHappens() {
    return whatHappens;
  }

  public Optional<String> theReasonTheCaptureIsRefusedFor() {
    return Optional.ofNullable(theCaptureIsRefusedFor);
  }

  public boolean endsWithAWebhook() {
    return theCaptureIsRefusedFor == null;
  }
}
