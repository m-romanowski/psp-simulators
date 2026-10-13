package dev.marcinromanowski.pspsim.kernel.audit;

import java.util.List;

public sealed interface AuditPage {

  record Listed(List<AuditRecord> records) implements AuditPage {

    public Listed {
      records = List.copyOf(records);
    }
  }

  record NoSuchCursor() implements AuditPage {}
}
