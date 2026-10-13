package dev.marcinromanowski.pspsim.provider.grow;

final class GrowEnvelope {

  private GrowEnvelope() {}

  static GrowJson success(GrowJson data, GrowScalarType statusType) {
    return GrowJson.object(
        "status", statusType.of(1),
        "err", GrowJson.text(""),
        "data", data);
  }

  static GrowJson failure(GrowError error, GrowScalarType statusType) {
    return GrowJson.object(
        "status",
        statusType.of(0),
        "err",
        GrowJson.object(
            "id", statusType.of(error.id()),
            "message", GrowJson.text(error.message())),
        "data",
        GrowJson.text(""));
  }
}
