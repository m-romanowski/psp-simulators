package dev.marcinromanowski.pspsim.provider.grow;

enum GrowDeclineFinality {
  FINAL("סופי"),

  NOT_FINAL("לא סופי");

  private final String publishedAs;

  GrowDeclineFinality(String publishedAs) {
    this.publishedAs = publishedAs;
  }

  String publishedAs() {
    return publishedAs;
  }
}
