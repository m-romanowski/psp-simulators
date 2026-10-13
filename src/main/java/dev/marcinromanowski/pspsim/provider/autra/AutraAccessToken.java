package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;

record AutraAccessToken(
    TestRunId testRunId, String value, String accountId, Instant issuedAt, Instant expiresAt) {

  static final Duration PUBLISHED_VALIDITY = Duration.ofHours(1);

  static final String TOKEN_TYPE = "Bearer";

  boolean hasExpiredBy(Instant now) {
    return !now.isBefore(expiresAt);
  }

  long expiresInSeconds() {
    return Duration.between(issuedAt, expiresAt).toSeconds();
  }
}
