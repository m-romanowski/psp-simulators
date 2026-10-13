package dev.marcinromanowski.pspsim.kernel.repro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record RecordedObject(
    @Nullable String providerObjectId,
    @Nullable List<String> answersTo,
    @Nullable String nativeStatus,
    @Nullable String normalizedStatus,
    @Nullable String amount,
    @Nullable String currency,
    boolean authorizationOpen,
    boolean capturesAccepted,
    boolean refundsAccepted,
    @Nullable String origin,
    @Nullable String updatedAt,
    @Nullable Map<String, String> relatedObjects,
    @Nullable List<RecordedOperation> ledger) {

  public RecordedObject {
    answersTo = answersTo == null ? null : List.copyOf(answersTo);
    relatedObjects =
        relatedObjects == null
            ? null
            : Collections.unmodifiableSequencedMap(new LinkedHashMap<>(relatedObjects));
    ledger = ledger == null ? null : List.copyOf(ledger);
  }

  public List<String> carriedIdentifiers() {
    List<String> carried = answersTo;
    return carried == null ? List.of() : carried;
  }

  public Map<String, String> carriedRelatedObjects() {
    Map<String, String> carried = relatedObjects;
    return carried == null ? Map.of() : carried;
  }

  public List<RecordedOperation> carriedLedger() {
    List<RecordedOperation> carried = ledger;
    return carried == null ? List.of() : carried;
  }
}
