package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record TranzilaHandshake(
    String thtk,
    TestRunId testRunId,
    String accountId,
    String terminalName,
    String sum,
    @Nullable String requestParams,
    Instant createdAt,
    Instant expiresAt) {

  static final Duration IT_IS_RETAINED_FOR = Duration.ofMinutes(20);

  static final String THE_LEAD = "w";

  static final int HEX_DIGITS = 41;

  boolean isLiveAt(Instant now) {
    return now.isBefore(expiresAt);
  }

  boolean locks(String terminal, BigDecimal amount) {
    return terminalName.equals(terminal) && new BigDecimal(sum).compareTo(amount) == 0;
  }
}
