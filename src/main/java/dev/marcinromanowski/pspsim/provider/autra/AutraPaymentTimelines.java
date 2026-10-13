package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AutraPaymentTimelines {

  private AutraPaymentTimelines() {}

  static Map<String, List<AutraPaymentEvent>> keyedByPayment(List<AutraPaymentEvent> events) {
    Map<String, List<AutraPaymentEvent>> gathered = new LinkedHashMap<>();
    for (AutraPaymentEvent event : events) {
      gathered.computeIfAbsent(event.paymentId(), _ -> new ArrayList<>()).add(event);
    }
    Map<String, List<AutraPaymentEvent>> kept = new LinkedHashMap<>();
    gathered.forEach((paymentId, timeline) -> kept.put(paymentId, List.copyOf(timeline)));
    return Collections.unmodifiableMap(kept);
  }

  static List<AutraPaymentEvent> theTimelineOf(
      Map<String, List<AutraPaymentEvent>> keyedByPayment, String paymentId) {
    return keyedByPayment.getOrDefault(paymentId, List.of());
  }

  static List<String> theIdentifiersOf(List<AutraPayment> payments) {
    List<String> named = new ArrayList<>();
    for (AutraPayment payment : payments) {
      named.add(payment.paymentId());
    }
    return List.copyOf(named);
  }

  static List<String> theIdentifiersArrivedAt(List<ArrivalOf<AutraPayment>> arrived) {
    List<AutraPayment> rows = new ArrayList<>();
    for (ArrivalOf<AutraPayment> one : arrived) {
      rows.add(one.row());
    }
    return theIdentifiersOf(List.copyOf(rows));
  }
}
