package dev.marcinromanowski.pspsim.provider.tranzila;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

final class TranzilaStoredForm {

  private TranzilaStoredForm() {}

  static @Nullable OffsetDateTime timestampOf(@Nullable Instant at) {
    return at == null ? null : at.atOffset(ZoneOffset.UTC);
  }

  static @Nullable Instant instantOf(@Nullable OffsetDateTime stored) {
    return stored == null ? null : stored.toInstant();
  }

  static TranzilaTxnType typeOf(String stored) {
    return TranzilaTxnType.of(stored)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "the store holds " + stored + " as a txn_type, which no type names"));
  }
}
