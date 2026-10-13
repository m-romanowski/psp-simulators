package dev.marcinromanowski.pspsim;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import java.util.ArrayList;
import java.util.List;

public final class ProviderTraffic {

  private ProviderTraffic() {}

  public static List<AuditRecord> in(List<AuditRecord> trail) {
    List<AuditRecord> traffic = new ArrayList<>();
    for (AuditRecord record : trail) {
      if (record.origin() == ObjectOrigin.PROVIDER_API) {
        traffic.add(record);
      }
    }
    return List.copyOf(traffic);
  }
}
