package dev.marcinromanowski.pspsim.provider.azericard;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

final class AzericardTimestamp {

  static final int LENGTH = 14;

  static final Duration WINDOW = Duration.ofHours(1);

  static final Duration STATUS_WINDOW = Duration.ofHours(24);

  private static final DateTimeFormatter WIRE =
      DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);

  private AzericardTimestamp() {}

  static Optional<Instant> read(String value) {
    if (value.length() != LENGTH) {
      return Optional.empty();
    }
    try {
      return Optional.of(LocalDateTime.parse(value, WIRE).toInstant(ZoneOffset.UTC));
    } catch (DateTimeParseException notATimestamp) {
      return Optional.empty();
    }
  }

  static String write(Instant at) {
    return WIRE.format(LocalDateTime.ofInstant(at, ZoneOffset.UTC));
  }

  static boolean withinTheWindow(Instant sentAt, Instant now) {
    return Duration.between(sentAt, now).abs().compareTo(WINDOW) <= 0;
  }

  static boolean withinTheStatusWindow(Instant requestedAt, Instant now) {
    return Duration.between(requestedAt, now).compareTo(STATUS_WINDOW) <= 0;
  }
}
