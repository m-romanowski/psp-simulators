package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.List;

enum StripeApiMajor {
  ACACIA("acacia", false),
  BASIL("basil", true),
  CLOVER("clover", true),
  DAHLIA("dahlia", true),
  ENDIVE("endive", true);

  private final String codename;
  private final boolean answeredHere;

  StripeApiMajor(String codename, boolean answeredHere) {
    this.codename = codename;
    this.answeredHere = answeredHere;
  }

  String codename() {
    return codename;
  }

  boolean answeredHere() {
    return answeredHere;
  }

  List<StripeApiVersion> releases() {
    return releaseDates().stream()
        .map(date -> new StripeApiVersion(date + StripeApiVersion.CODENAME_SEPARATOR + codename))
        .toList();
  }

  StripeApiVersion opening() {
    return releases().getFirst();
  }

  private List<String> releaseDates() {
    return switch (this) {
      case ACACIA ->
          List.of(
              "2024-09-30", "2024-10-28", "2024-11-20", "2024-12-18", "2025-01-27", "2025-02-24");
      case BASIL ->
          List.of(
              "2025-03-31", "2025-04-30", "2025-05-28", "2025-06-30", "2025-07-30", "2025-08-27");
      case CLOVER ->
          List.of(
              "2025-09-30", "2025-10-29", "2025-11-17", "2025-12-15", "2026-01-28", "2026-02-25");
      case DAHLIA ->
          List.of(
              "2026-03-25", "2026-04-22", "2026-05-27", "2026-06-24", "2026-07-29", "2026-08-26");
      case ENDIVE -> List.of("2026-09-30", "2026-10-28");
    };
  }
}
