package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.idempotency.RequestFingerprint;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class WorldlineIdempotence {

  static final int LONGEST_KEY = 40;

  static final Duration WINDOW = Duration.ofHours(24);

  static final char HIGHEST_ASCII = 0x7f;

  static final String BOTH_SPELLINGS_ARE_READ =
      "provider worldline publishes the name of its idempotence-key header twice and differently,"
          + " and this simulator reads both. The idempotent-requests page and the Java SDK page"
          + " spell it "
          + WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT
          + " — \"Using universally unique identifiers (UUIDs) for generating the"
          + " X-GCS-Idempotence-Key, minimising the risk of key collisions\" and \"The SDK will"
          + " send an X-GCS-Idempotence-Key header with the idempotence key as its value\". The"
          + " manual-authentication page spells it "
          + WorldlineFields.IDEMPOTENCY_KEY_AS_THE_MANUAL_AUTHENTICATION_PAGE_SPELLS_IT
          + " and canonicalises it to x-gcs-idempotency-key in its normalisation table and in"
          + " every one of its six language samples. The second spelling is the one a non-SDK"
          + " client signs, so the two cannot be reconciled by preferring the SDK, and a"
          + " simulator that accepted only one would silently give half its callers a first"
          + " request every time. Both reach the same mechanism here. A request sending both at"
          + " once with two different values is refused rather than resolved, because nothing"
          + " worldline publishes says which of them would win";

  static final String TWO_SPELLINGS_ONE_KEY =
      "a request may carry either published spelling of the idempotence-key header, and this one"
          + " carries both with two different values. Provider worldline publishes no precedence"
          + " between them, so this simulator refuses the request rather than picking the"
          + " spelling that happened to be read first";

  static final String THE_KEY_IS_TOO_LONG =
      "the idempotence key provider worldline publishes \"Has a maximum length of 40 characters\","
          + " and this one is longer";

  static final String THE_KEY_IS_NOT_ASCII =
      "the idempotence key provider worldline publishes \"Should be ASCII compliant and not"
          + " support UTF-8\", and this one carries a character outside ASCII";

  private WorldlineIdempotence() {}

  record Sent(String headerName, String value) {}

  static Optional<Sent> keyIn(Map<String, List<String>> headers) {
    Sent idempotence =
        firstOf(headers, WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT);
    Sent idempotency =
        firstOf(
            headers, WorldlineFields.IDEMPOTENCY_KEY_AS_THE_MANUAL_AUTHENTICATION_PAGE_SPELLS_IT);
    if (idempotence == null) {
      return Optional.ofNullable(idempotency);
    }
    return Optional.of(idempotence);
  }

  static boolean carriesTwoSpellingsThatDisagree(Map<String, List<String>> headers) {
    Sent idempotence =
        firstOf(headers, WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT);
    Sent idempotency =
        firstOf(
            headers, WorldlineFields.IDEMPOTENCY_KEY_AS_THE_MANUAL_AUTHENTICATION_PAGE_SPELLS_IT);
    return idempotence != null
        && idempotency != null
        && !idempotence.value().equals(idempotency.value());
  }

  private static @Nullable Sent firstOf(Map<String, List<String>> headers, String spelling) {
    for (Map.Entry<String, List<String>> sent : headers.entrySet()) {
      if (!sent.getKey().equalsIgnoreCase(spelling)) {
        continue;
      }
      for (String value : sent.getValue()) {
        if (!value.isBlank()) {
          return new Sent(spelling, value);
        }
      }
    }
    return null;
  }

  static boolean isTooLong(String key) {
    return key.codePointCount(0, key.length()) > LONGEST_KEY;
  }

  static boolean isNotAscii(String key) {
    return key.codePoints().anyMatch(one -> one > HIGHEST_ASCII);
  }

  static String fingerprintOf(String method, String operation, Map<String, List<String>> fields) {
    return RequestFingerprint.of(method, operation, fields);
  }

  static String theTimestampHeaderValueOf(Instant firstRequest) {
    return Long.toString(firstRequest.toEpochMilli());
  }
}
