package dev.marcinromanowski.pspsim.provider.grow;

record GrowPayerCard(
    String alias,
    String suffix,
    String type,
    String typeCode,
    String brand,
    String brandCode,
    String expiry) {

  static final String UNMAPPED = "";

  String describedForAPayer() {
    return brand + " " + GrowPayerCards.MASKED + suffix + ", " + expiry;
  }
}
