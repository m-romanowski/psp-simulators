package dev.marcinromanowski.pspsim.kernel.idempotency;

public enum KeyRole {
  TRUE_IDEMPOTENCY_KEY(
      "true idempotency key",
      "a repeat under this key returns the original response: the provider stored the whole answer"
          + " the first time and replays it unchanged"),
  DUPLICATE_DETECTION_KEY(
      "duplicate-detection key",
      "a repeat under this key is refused and never replayed: the provider answers a duplicate"
          + " error instead of the answer it gave the first time"),
  CORRELATION_KEY(
      "correlation key",
      "a repeat under this key addresses the object it names and is neither replayed nor refused:"
          + " the provider answers what it holds now");

  private final String publishedName;

  private final String whatARepeatDoes;

  KeyRole(String publishedName, String whatARepeatDoes) {
    this.publishedName = publishedName;
    this.whatARepeatDoes = whatARepeatDoes;
  }

  public String publishedName() {
    return publishedName;
  }

  public String whatARepeatDoes() {
    return whatARepeatDoes;
  }
}
