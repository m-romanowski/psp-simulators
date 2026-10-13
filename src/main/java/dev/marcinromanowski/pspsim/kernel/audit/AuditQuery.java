package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public record AuditQuery(
    TestRunId testRunId,
    @Nullable String auditId,
    @Nullable String accountId,
    @Nullable ObjectOrigin origin,
    @Nullable AuditDirection direction,
    @Nullable String method,
    @Nullable Integer httpStatus,
    List<String> providerObjectIds,
    @Nullable String scenarioId,
    @Nullable Instant from,
    @Nullable Instant until,
    @Nullable String after,
    int limit) {

  public static final int DEFAULT_LIMIT = 100;

  public static final int LARGEST_LIMIT = 500;

  public static final int LONGEST_AUDIT_ID_A_ROW_HOLDS = 64;

  private static final int LOWEST_HTTP_STATUS = 100;

  private static final int HIGHEST_HTTP_STATUS = 599;

  public AuditQuery {
    providerObjectIds = List.copyOf(providerObjectIds);
    refuseAnIdentifierNoRowCouldCarry(providerObjectIds);
    if (auditId != null) {
      refuseAnAuditIdNoRowCouldCarry(auditId);
    }
    if (accountId != null) {
      TheAccountARowNames.refuseAnIdentityNoRowCouldCarry("accountId", accountId);
    }
    if (limit < 1 || limit > LARGEST_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + LARGEST_LIMIT);
    }
    if (httpStatus != null
        && (httpStatus < LOWEST_HTTP_STATUS || httpStatus > HIGHEST_HTTP_STATUS)) {
      throw new IllegalArgumentException(
          "status must be an HTTP status between "
              + LOWEST_HTTP_STATUS
              + " and "
              + HIGHEST_HTTP_STATUS);
    }
    if (from != null && until != null && !until.isAfter(from)) {
      throw new IllegalArgumentException("until must be later than from");
    }
  }

  public static AuditQuery of(DeclaredAuditQuery declared, TestRunId whenTestRunAbsent) {
    return new AuditQuery(
        testRunOf(declared.testRunId(), whenTestRunAbsent),
        givenOrAbsent(declared.auditId()),
        givenOrAbsent(declared.accountId()),
        originOf(declared.origin()),
        directionOf(declared.direction()),
        methodOf(declared.method()),
        statusOf(declared.status()),
        theObjectAddressed(declared.providerObjectId()),
        givenOrAbsent(declared.scenarioId()),
        instantOf("from", declared.from()),
        instantOf("until", declared.until()),
        declared.after(),
        limitOf(declared.limit()));
  }

  private static void refuseAnAuditIdNoRowCouldCarry(String auditId) {
    if (auditId.isBlank()) {
      throw new IllegalArgumentException(
          "auditId is blank, and a trail narrowed to a blank audit id would hold no row at all"
              + " rather than the one exchange an audit id names");
    }
    if (auditId.length() > LONGEST_AUDIT_ID_A_ROW_HOLDS) {
      throw new IllegalArgumentException(
          "auditId must be at most "
              + LONGEST_AUDIT_ID_A_ROW_HOLDS
              + " characters, which is the widest audit id a row holds, so a longer one names an"
              + " exchange no row could carry");
    }
  }

  private static void refuseAnIdentifierNoRowCouldCarry(List<String> providerObjectIds) {
    for (int named = 0; named < providerObjectIds.size(); named++) {
      if (providerObjectIds.get(named).isBlank()) {
        throw new IllegalArgumentException(
            "a trail is narrowed to the rows carrying any identifier one object answers to, and"
                + " identifier "
                + named
                + " is blank, so the narrowing would keep every row that names no object at all");
      }
    }
  }

  private static List<String> theObjectAddressed(@Nullable String declared) {
    String given = givenOrAbsent(declared);
    return given == null ? List.of() : List.of(given);
  }

  private static TestRunId testRunOf(@Nullable String declared, TestRunId whenAbsent) {
    if (declared == null || declared.isBlank()) {
      return whenAbsent;
    }
    return TestRunId.storedNamespace(declared);
  }

  private static @Nullable ObjectOrigin originOf(@Nullable String declared) {
    String given = givenOrAbsent(declared);
    if (given == null) {
      return null;
    }
    for (ObjectOrigin known : ObjectOrigin.values()) {
      if (known.name().equalsIgnoreCase(given)) {
        return known;
      }
    }
    throw new IllegalArgumentException(
        "origin must be one of " + String.join(", ", theOriginsThisTrailHolds()));
  }

  public static List<String> theOriginsThisTrailHolds() {
    List<String> named = new ArrayList<>();
    for (ObjectOrigin known : ObjectOrigin.values()) {
      named.add(known.name());
    }
    return List.copyOf(named);
  }

  private static @Nullable AuditDirection directionOf(@Nullable String declared) {
    String given = givenOrAbsent(declared);
    if (given == null) {
      return null;
    }
    for (AuditDirection known : AuditDirection.values()) {
      if (known.name().equalsIgnoreCase(given)) {
        return known;
      }
    }
    throw new IllegalArgumentException(
        "direction must be one of " + String.join(", ", theDirectionsThisTrailHolds()));
  }

  public static List<String> theDirectionsThisTrailHolds() {
    List<String> named = new ArrayList<>();
    for (AuditDirection known : AuditDirection.values()) {
      named.add(known.name());
    }
    return List.copyOf(named);
  }

  private static @Nullable String methodOf(@Nullable String declared) {
    String given = givenOrAbsent(declared);
    return given == null ? null : given.toUpperCase(Locale.ROOT);
  }

  private static @Nullable Integer statusOf(@Nullable String declared) {
    String given = givenOrAbsent(declared);
    if (given == null) {
      return null;
    }
    return wholeNumberOf("status", given);
  }

  private static int limitOf(@Nullable String declared) {
    String given = givenOrAbsent(declared);
    return given == null ? DEFAULT_LIMIT : wholeNumberOf("limit", given);
  }

  private static int wholeNumberOf(String property, String given) {
    try {
      return Integer.parseInt(given);
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException(property + " must be a whole number");
    }
  }

  private static @Nullable Instant instantOf(String property, @Nullable String declared) {
    String given = givenOrAbsent(declared);
    if (given == null) {
      return null;
    }
    try {
      return Instant.parse(given);
    } catch (DateTimeParseException notAnInstant) {
      throw new IllegalArgumentException(
          property + " must be an ISO-8601 instant such as 2026-09-07T12:00:00Z");
    }
  }

  private static @Nullable String givenOrAbsent(@Nullable String declared) {
    return declared == null || declared.isBlank() ? null : declared.strip();
  }
}
