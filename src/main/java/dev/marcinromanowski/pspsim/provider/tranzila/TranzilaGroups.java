package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TranzilaGroups {

  private TranzilaGroups() {}

  static List<TranzilaPayment> paymentsOf(List<TranzilaTransaction> inArrivalOrder) {
    Map<Long, TranzilaTransaction> roots = new LinkedHashMap<>();
    Map<Long, List<TranzilaTransaction>> followOns = new LinkedHashMap<>();
    for (TranzilaTransaction transaction : inArrivalOrder) {
      Long root = Long.valueOf(transaction.rootTxnId());
      if (transaction.transactionId() == transaction.rootTxnId()) {
        roots.put(root, transaction);
      } else {
        followOns.computeIfAbsent(root, absent -> new ArrayList<>()).add(transaction);
      }
    }
    List<TranzilaPayment> payments = new ArrayList<>();
    roots.forEach(
        (root, transaction) ->
            payments.add(
                new TranzilaPayment(transaction, followOns.getOrDefault(root, List.of()))));
    return List.copyOf(payments);
  }

  static Optional<TranzilaPayment> theOnePaymentOf(List<TranzilaTransaction> inArrivalOrder) {
    return paymentsOf(inArrivalOrder).stream().findFirst();
  }

  static List<ArrivalOf<TranzilaObject>> inTheOrderOf(
      List<ArrivalOf<Long>> listed, List<TranzilaTransaction> members) {
    return theObjectsOf(listed, paymentsOf(members));
  }

  static List<ArrivalOf<TranzilaObject>> theObjectsOf(
      List<ArrivalOf<Long>> listed, List<TranzilaPayment> payments) {
    List<ArrivalOf<TranzilaObject>> ordered = new ArrayList<>();
    for (ArrivalOf<Long> named : listed) {
      payments.stream()
          .flatMap(payment -> TranzilaObject.of(payment, named.row().longValue()).stream())
          .findFirst()
          .ifPresent(found -> ordered.add(new ArrivalOf<>(named.arrival(), found)));
    }
    return List.copyOf(ordered);
  }

  static List<TranzilaObject> theObjectsNamed(List<Long> named, List<TranzilaPayment> payments) {
    List<TranzilaObject> found = new ArrayList<>();
    for (Long transactionId : named) {
      payments.stream()
          .flatMap(payment -> TranzilaObject.of(payment, transactionId.longValue()).stream())
          .findFirst()
          .filter(object -> !found.contains(object))
          .ifPresent(found::add);
    }
    return List.copyOf(found);
  }
}
