package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum WorldlineAuthentication {
  THE_SIGNATURE_IS_VERIFIED,

  THE_SIGNATURE_IS_NOT_VERIFIED;

  static final String SETTING = "worldline.authentication";

  static final String WHY_THERE_ARE_TWO =
      "provider worldline authenticates every call with an HMAC-SHA256 signature presented as"
          + " \"Authorization: GCS v1HMAC:<your-api-key>:<base64-encoded-signature>\", and this"
          + " simulator verifies it by default so that a client whose signing is wrong finds out"
          + " here rather than in production. The second value is this simulator's own decision"
          + " and no reading of the vendor, which publishes no permissive mode: it exists so a"
          + " local test may drive the wire without signing, and under it the apiKey must still"
          + " name this account while the signature and the Date are left unchecked";

  boolean verifiesTheSignature() {
    return this == THE_SIGNATURE_IS_VERIFIED;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            THE_SIGNATURE_IS_VERIFIED.name(),
            "every call must carry an HMAC signature this simulator can recompute, so a client"
                + " that signs wrongly finds out here"),
        new AcceptedValue(
            THE_SIGNATURE_IS_NOT_VERIFIED.name(),
            "the apiKey must still name this account, but the signature and the Date go"
                + " unchecked, so a test may drive the wire without signing"));
  }

  static WorldlineAuthentication of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return THE_SIGNATURE_IS_VERIFIED;
    }
    for (WorldlineAuthentication configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two authentication modes this simulator offers for provider"
            + " worldline. "
            + WHY_THERE_ARE_TWO
            + ". The two values are "
            + THE_SIGNATURE_IS_VERIFIED.name()
            + ", the default, and "
            + THE_SIGNATURE_IS_NOT_VERIFIED.name());
  }
}
