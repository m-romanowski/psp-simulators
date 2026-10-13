package dev.marcinromanowski.pspsim.kernel.money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Ledgers {

  private Ledgers() {}

  public static Map<String, Ledger> keyedByObject(List<OperationOf> stored) {
    Map<String, List<MonetaryOperation>> gathered = new LinkedHashMap<>();
    for (OperationOf one : stored) {
      gathered.computeIfAbsent(one.objectId(), _ -> new ArrayList<>()).add(one.operation());
    }
    Map<String, Ledger> kept = new LinkedHashMap<>();
    gathered.forEach((objectId, operations) -> kept.put(objectId, new Ledger(operations)));
    return Collections.unmodifiableMap(kept);
  }

  public static Ledger theLedgerOf(Map<String, Ledger> keyedByObject, String objectId) {
    return keyedByObject.getOrDefault(objectId, Ledger.empty());
  }
}
