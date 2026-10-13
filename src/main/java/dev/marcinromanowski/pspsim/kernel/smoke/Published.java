package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class Published {

  private final String value;
  private final Provenance provenance;

  Published(@Nullable String value, Provenance provenance) {
    if (value == null || value.isBlank()) {
      throw new MalformedVendorSampleException(
          provenance.sample() + " yields no value where a check reads one");
    }
    this.value = value;
    this.provenance = provenance;
  }

  public String value() {
    return value;
  }

  public Provenance provenance() {
    return provenance;
  }

  public Published map(Function<String, @Nullable String> derivation) {
    return new Published(derivation.apply(value), provenance);
  }

  @Override
  public String toString() {
    return value + " from " + provenance.sample();
  }
}
