package dev.marcinromanowski.pspsim.provider.grow;

record GrowRecordedCard(
    String suffix, String type, String typeCode, String brand, String brandCode, String expiry) {

  static final GrowRecordedCard THE_PUBLISHED_SANDBOX_CARD =
      new GrowRecordedCard(
          GrowSandboxCard.SUFFIX,
          GrowSandboxCard.TYPE,
          GrowSandboxCard.TYPE_CODE,
          GrowSandboxCard.BRAND,
          GrowSandboxCard.BRAND_CODE,
          GrowSandboxCard.EXPIRY);

  static GrowRecordedCard chosenBy(GrowPayerCard payer) {
    return new GrowRecordedCard(
        payer.suffix(),
        payer.type(),
        payer.typeCode(),
        payer.brand(),
        payer.brandCode(),
        payer.expiry());
  }
}
