package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ProviderObjectAddressing {

  private ProviderObjectAddressing() {}

  public static List<String> everyIdentifierRowsOfThatObjectCarry(
      SimulatorProvider module, TestRunId testRunId, @Nullable String addressedBy) {
    if (addressedBy == null || addressedBy.isBlank()) {
      return List.of();
    }
    String asked = addressedBy.strip();
    return AddressedProviderObject.read(module, testRunId, asked)
        .map(AddressedProviderObject::answersTo)
        .orElseGet(() -> List.of(asked));
  }

  public static AuditQuery overEveryIdentifierOfTheSameObject(
      SimulatorProvider module, AuditQuery query) {
    List<String> narrowed = query.providerObjectIds();
    if (narrowed.size() != 1) {
      return query;
    }
    List<String> widened =
        everyIdentifierRowsOfThatObjectCarry(module, query.testRunId(), narrowed.get(0));
    if (widened.equals(narrowed)) {
      return query;
    }
    return new AuditQuery(
        query.testRunId(),
        query.auditId(),
        query.accountId(),
        query.origin(),
        query.direction(),
        query.method(),
        query.httpStatus(),
        widened,
        query.scenarioId(),
        query.from(),
        query.until(),
        query.after(),
        query.limit());
  }
}
