package dev.marcinromanowski.pspsim.kernel;

public record ProviderId(String value) {

  private static final AShapeSaidInWords SHAPE =
      AShapeSaidInWords.of("[a-z][a-z0-9-]{0,31}", "grow");

  public ProviderId {
    if (value == null || !SHAPE.admits(value)) {
      throw new IllegalArgumentException(SHAPE.theRefusalOf("provider id"));
    }
  }

  public static ProviderId of(String value) {
    return new ProviderId(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
