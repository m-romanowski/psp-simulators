package dev.marcinromanowski.pspsim.provider.worldline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WorldlineMintedIdentifiers {

  private WorldlineMintedIdentifiers() {}

  record MintedFor(String paymentId, String identifier) {}

  static Map<String, List<String>> keyedByPayment(List<MintedFor> inPayIdSubOrder) {
    Map<String, List<String>> gathered = new LinkedHashMap<>();
    for (MintedFor minted : inPayIdSubOrder) {
      gathered.computeIfAbsent(minted.paymentId(), _ -> new ArrayList<>()).add(minted.identifier());
    }
    Map<String, List<String>> kept = new LinkedHashMap<>();
    gathered.forEach((paymentId, minted) -> kept.put(paymentId, List.copyOf(minted)));
    return Collections.unmodifiableMap(kept);
  }

  static List<String> theOnesMintedFor(Map<String, List<String>> keyedByPayment, String paymentId) {
    return keyedByPayment.getOrDefault(paymentId, List.of());
  }
}
