package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class AuditScreen {

  public record Damage(
      @Nullable String transportFault,
      @Nullable String faultPlacement,
      long declaredContentLength,
      long writtenBodyBytes,
      long heldForMillis) {}

  public record Row(
      String auditId,
      String testRunId,
      @Nullable String resolutionRule,
      @Nullable String accountId,
      String origin,
      String direction,
      @Nullable StatusBadge outcome,
      String method,
      @Nullable String path,
      @Nullable Integer status,
      @Nullable String providerObjectId,
      @Nullable String refused,
      @Nullable String scenarioId,
      int attempt,
      Instant createdAt,
      @Nullable Damage damage,
      boolean open,
      List<Disclosure.Field> headers,
      List<Disclosure.Field> body) {

    public Row {
      headers = List.copyOf(headers);
      body = List.copyOf(body);
    }
  }

  public record Page(
      List<Row> rows,
      int limit,
      @Nullable String nextCursor,
      boolean valuesShown,
      @Nullable String theAccountNoRowNames,
      @Nullable String theExchangeNoRowAnswersTo) {

    public Page {
      rows = List.copyOf(rows);
    }
  }

  private AuditScreen() {}

  public static List<String> theCursorARelinkShouldCarry(@Nullable String after) {
    return after == null || after.isBlank() ? List.of() : List.of(after);
  }

  public static Page pageOf(
      List<AuditRecord> records,
      AuditQuery narrowed,
      @Nullable String openAuditId,
      boolean valuesAsked) {
    List<Row> rows = new ArrayList<>();
    for (AuditRecord record : records) {
      rows.add(rowOf(record, record.auditId().equals(openAuditId), valuesAsked));
    }
    return new Page(
        rows,
        narrowed.limit(),
        cursorAfter(rows, narrowed.limit()),
        valuesAsked,
        theAccountAnEmptyPageAnswersFor(rows, narrowed),
        theExchangeAnEmptyPageAnswersFor(rows, narrowed));
  }

  private static @Nullable String theAccountAnEmptyPageAnswersFor(
      List<Row> rows, AuditQuery narrowed) {
    if (narrowed.auditId() != null) {
      return null;
    }
    return theNarrowingAnEmptyPageAnswersFor(rows, narrowed, narrowed.accountId());
  }

  private static @Nullable String theExchangeAnEmptyPageAnswersFor(
      List<Row> rows, AuditQuery narrowed) {
    if (narrowed.accountId() != null) {
      return null;
    }
    return theNarrowingAnEmptyPageAnswersFor(rows, narrowed, narrowed.auditId());
  }

  private static @Nullable String theNarrowingAnEmptyPageAnswersFor(
      List<Row> rows, AuditQuery narrowed, @Nullable String asked) {
    if (!rows.isEmpty() || asked == null) {
      return null;
    }
    return nothingElseCouldHaveEmptiedThePage(narrowed) ? asked : null;
  }

  private static boolean nothingElseCouldHaveEmptiedThePage(AuditQuery narrowed) {
    return narrowed.after() == null
        && narrowed.origin() == null
        && narrowed.direction() == null
        && narrowed.method() == null
        && narrowed.httpStatus() == null
        && narrowed.providerObjectIds().isEmpty()
        && narrowed.scenarioId() == null
        && narrowed.from() == null
        && narrowed.until() == null;
  }

  private static @Nullable String cursorAfter(List<Row> rows, int limit) {
    return rows.size() < limit ? null : rows.get(rows.size() - 1).auditId();
  }

  private static Row rowOf(AuditRecord record, boolean open, boolean valuesAsked) {
    TestRunResolutionRule rule = record.resolutionRule();
    ProviderOutcome outcome = record.outcome();
    return new Row(
        record.auditId(),
        record.testRunId().value(),
        rule == null ? null : rule.name(),
        record.accountId(),
        record.origin().name(),
        record.direction().name(),
        outcome == null ? null : StatusBadge.of(outcome),
        record.method(),
        record.path(),
        record.httpStatus(),
        record.providerObjectId(),
        whatARowRefused(record.refusal()),
        record.scenarioId(),
        record.attempt(),
        record.createdAt(),
        damageOf(record.delivered()),
        open,
        open ? Disclosure.of(record.sanitizedHeaders(), valuesAsked) : List.of(),
        open ? Disclosure.of(record.sanitizedBody(), valuesAsked) : List.of());
  }

  public static @Nullable String whatARowRefused(@Nullable RefusalSubject refusal) {
    return switch (refusal) {
      case null -> null;
      case RefusalSubject.TheObject named -> "the object " + named.providerObjectId();
      case RefusalSubject.NoObjectAnsweredTo none ->
          "no object of this run answered to " + none.theFieldThatNamedIt();
      case RefusalSubject.SeveralObjectsAnsweredTo several ->
          several.howManyAnswered()
              + " objects of this run answered to "
              + several.theFieldThatNamedThem();
      case RefusalSubject.AnObjectItWillNotName withheld ->
          "an object this provider will not name: " + withheld.whyItsContractNamesNone();
      case RefusalSubject.NoObjectWasNamed nothing -> nothing.whatItRefusedInstead();
    };
  }

  private static @Nullable Damage damageOf(@Nullable DeliveredResponse delivered) {
    if (delivered == null) {
      return null;
    }
    AppliedFault fault = delivered.transportFault();
    return new Damage(
        fault == null ? null : fault.kind().name(),
        fault == null ? null : fault.placement().name(),
        delivered.declaredContentLength(),
        delivered.writtenBodyBytes(),
        delivered.heldFor().toMillis());
  }
}
