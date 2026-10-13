package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AutraRequestShape {
  PUBLISHED_SCHEMA,

  PUBLISHED_GUIDE_EXAMPLE;

  static final String PRE_AUTHORIZATION_SETTING = "autra.pre-authorization-request-shape";

  static final String VOID_SETTING = "autra.void-request-shape";

  boolean isTheSchema() {
    return this == PUBLISHED_SCHEMA;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            PUBLISHED_SCHEMA.name(),
            "the request must carry the fields autra's OpenAPI document declares, and one shaped"
                + " the other way is refused"),
        new AcceptedValue(
            PUBLISHED_GUIDE_EXAMPLE.name(),
            "the request must carry the fields autra's prose guide calls this endpoint with, and"
                + " one shaped the other way is refused"));
  }

  static AutraRequestShape of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return PUBLISHED_SCHEMA;
    }
    for (AutraRequestShape configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two request shapes provider autra publishes for this"
            + " operation. Autra publishes each of them twice and the two publications disagree"
            + " about which fields the request carries, so the two values are "
            + PUBLISHED_SCHEMA.name()
            + ", the OpenAPI document the reference pages are generated from, and "
            + PUBLISHED_GUIDE_EXAMPLE.name()
            + ", the example the prose guide calls the same endpoint with");
  }
}
