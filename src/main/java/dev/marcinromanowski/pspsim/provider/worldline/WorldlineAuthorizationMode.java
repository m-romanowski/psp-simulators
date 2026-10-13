package dev.marcinromanowski.pspsim.provider.worldline;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum WorldlineAuthorizationMode {
  PRE_AUTHORIZATION(
      "Only blocks the order.amountOfMoney.amount up to 30 days. You can cancel the authorisation"
          + " any time, both fully and partially. Supports both full and partial captures.",
      true,
      true,
      false),

  FINAL_AUTHORIZATION(
      "Only blocks the order.amountOfMoney.amount for 7 days. You can cancel the authorisation any"
          + " time, but only fully. Supports only full captures.",
      false,
      false,
      false),

  SALE("Blocks and captures the order.amountOfMoney.amount in one go.", false, false, true);

  static final String THE_FOURTH_CASE_HAS_NO_NAME =
      "provider worldline publishes a fourth case of authorizationMode with no name of its own:"
          + " \"Only used with some acquirers, ignored for acquirers that do not support this. In"
          + " case the acquirer does not allow this to be specified the authorizationMode is"
          + " 'unspecified', which behaves similar to a final authorization\". It has no wire"
          + " value, so nothing here can send it and nothing here can be configured into it; an"
          + " account that wants its behaviour sends FINAL_AUTHORIZATION, which the vendor says"
          + " it behaves similar to";

  private final String whatItDoes;
  private final boolean partialCapture;
  private final boolean partialCancel;
  private final boolean capturesAtOnce;

  WorldlineAuthorizationMode(
      String whatItDoes, boolean partialCapture, boolean partialCancel, boolean capturesAtOnce) {
    this.whatItDoes = whatItDoes;
    this.partialCapture = partialCapture;
    this.partialCancel = partialCancel;
    this.capturesAtOnce = capturesAtOnce;
  }

  String whatItDoes() {
    return whatItDoes;
  }

  boolean allowsAPartialCapture() {
    return partialCapture;
  }

  boolean allowsAPartialCancellation() {
    return partialCancel;
  }

  boolean capturesAtOnce() {
    return capturesAtOnce;
  }

  static Optional<WorldlineAuthorizationMode> of(String declared) {
    for (WorldlineAuthorizationMode published : values()) {
      if (published.name().equals(declared)) {
        return Optional.of(published);
      }
    }
    return Optional.empty();
  }

  static String thePublishedValues() {
    List<String> named = new ArrayList<>();
    for (WorldlineAuthorizationMode published : values()) {
      named.add(published.name());
    }
    return String.join(", ", named);
  }
}
