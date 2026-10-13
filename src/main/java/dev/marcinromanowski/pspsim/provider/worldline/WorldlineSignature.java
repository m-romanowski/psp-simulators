package dev.marcinromanowski.pspsim.provider.worldline;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class WorldlineSignature {

  static final String SCHEME = "GCS v1HMAC:";

  static final char FIELD_SEPARATOR = ':';

  static final char LINE_SEPARATOR = '\n';

  static final String ALGORITHM = "HmacSHA256";

  static final Duration OLDEST_DATE_THE_PLATFORM_ACCEPTS = Duration.ofMinutes(5);

  static final String THE_EMPTY_CONTENT_TYPE = "";

  static final DateTimeFormatter RFC_1123 =
      DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);

  static final String THE_SKEW_IS_THE_VENDORS_AND_NOT_A_KNOB =
      "provider worldline fixes the clock skew itself and this simulator does not offer a knob"
          + " wider or narrower than the one it publishes: \"Our platform will reject requests"
          + " with a timestamp older than 5 minutes\", repeated as advice as \"Keep the Date"
          + " header within 5 minutes and use the same date in the header and the signature\"."
          + " A configurable window would be a simulator decision standing where a published rule"
          + " already is";

  private WorldlineSignature() {}

  static Optional<String> apiKeyIn(String authorization) {
    if (!authorization.startsWith(SCHEME)) {
      return Optional.empty();
    }
    String presented = authorization.substring(SCHEME.length());
    int separator = presented.indexOf(FIELD_SEPARATOR);
    if (separator <= 0) {
      return Optional.empty();
    }
    return Optional.of(presented.substring(0, separator));
  }

  static Optional<String> signatureIn(String authorization) {
    if (!authorization.startsWith(SCHEME)) {
      return Optional.empty();
    }
    String presented = authorization.substring(SCHEME.length());
    int separator = presented.indexOf(FIELD_SEPARATOR);
    if (separator < 0 || separator + 1 >= presented.length()) {
      return Optional.empty();
    }
    return Optional.of(presented.substring(separator + 1));
  }

  static String subjectOf(
      String method,
      String contentType,
      String date,
      List<String> canonicalHeaderLines,
      String signedPath) {
    StringBuilder subject = new StringBuilder();
    subject.append(method).append(LINE_SEPARATOR);
    subject.append(contentType).append(LINE_SEPARATOR);
    subject.append(date).append(LINE_SEPARATOR);
    for (String canonical : canonicalHeaderLines) {
      subject.append(canonical).append(LINE_SEPARATOR);
    }
    subject.append(signedPath).append(LINE_SEPARATOR);
    return subject.toString();
  }

  static List<String> canonicalHeaderLinesOf(Map<String, List<String>> headers) {
    List<String> lines = new ArrayList<>();
    for (Map.Entry<String, List<String>> sent : headers.entrySet()) {
      String lowercased = sent.getKey().toLowerCase(Locale.ROOT);
      if (!lowercased.startsWith(WorldlineFields.CANONICAL_HEADER_PREFIX)) {
        continue;
      }
      for (String value : sent.getValue()) {
        lines.add(lowercased + FIELD_SEPARATOR + normalizedValueOf(value));
      }
    }
    Collections.sort(lines);
    return List.copyOf(lines);
  }

  static String normalizedValueOf(String value) {
    return value.strip().replaceAll("\\s+", " ");
  }

  static String signed(String secret, String subject) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return Base64.getEncoder()
          .encodeToString(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException unavailable) {
      throw new IllegalStateException(
          "a signed provider worldline request needs " + ALGORITHM, unavailable);
    }
  }

  static Optional<Instant> dateIn(String date) {
    try {
      return Optional.of(Instant.from(RFC_1123.parse(date)));
    } catch (DateTimeParseException notRfc1123) {
      return Optional.empty();
    }
  }

  static boolean isOlderThanThePlatformAccepts(Instant sent, Instant now) {
    return Duration.between(sent, now).compareTo(OLDEST_DATE_THE_PLATFORM_ACCEPTS) > 0;
  }

  static String asWorldlineWritesIt(Instant at) {
    return RFC_1123.format(at);
  }
}
