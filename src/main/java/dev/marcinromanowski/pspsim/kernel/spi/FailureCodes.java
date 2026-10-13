package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.regex.Pattern;

public final class FailureCodes {

  private final Pattern shape;
  private final String describedAs;

  public FailureCodes(String shape, String describedAs) {
    if (describedAs.isBlank()) {
      throw new IllegalArgumentException(
          "a provider must say, in its own words, what shape the code of a decline it can answer"
              + " with takes");
    }
    this.shape = Pattern.compile(shape);
    this.describedAs = describedAs;
  }

  public boolean admits(String providerCode) {
    return shape.matcher(providerCode).matches();
  }

  public String shape() {
    return shape.pattern();
  }

  public String describedAs() {
    return describedAs;
  }
}
