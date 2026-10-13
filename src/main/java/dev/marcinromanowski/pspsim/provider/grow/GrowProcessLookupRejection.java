package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum GrowProcessLookupRejection {
  PUBLISHED_EXAMPLE {
    @Override
    GrowError rejection() {
      return GrowErrors.invalidLookupCredentials();
    }
  },

  PUBLISHED_TABLE {
    @Override
    GrowError rejection() {
      return GrowErrors.invalidProcessLookupCredentials();
    }
  };

  static final String SETTING = "processLookupRejection";

  abstract GrowError rejection();

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            PUBLISHED_EXAMPLE.name(),
            "a process lookup with the wrong credentials is refused 716, as grow's own worked"
                + " example answers it"),
        new AcceptedValue(
            PUBLISHED_TABLE.name(),
            "a process lookup with the wrong credentials is refused 730, as grow's published error"
                + " table answers it"));
  }

  static GrowProcessLookupRejection of(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return PUBLISHED_EXAMPLE;
    }
    for (GrowProcessLookupRejection rejection : values()) {
      if (rejection.name().equals(configured)) {
        return rejection;
      }
    }
    throw new IllegalArgumentException(
        "must be one of " + PUBLISHED_EXAMPLE + ", " + PUBLISHED_TABLE);
  }
}
