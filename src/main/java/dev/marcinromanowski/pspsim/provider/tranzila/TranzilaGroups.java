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

  static List<ArrivalOf<TranzilaPayment>> inTheOrderOf(
      List<ArrivalOf<Long>> roots, List<TranzilaTransaction> members) {
    Map<Long, TranzilaPayment> byRoot = new LinkedHashMap<>();
    for (TranzilaPayment payment : paymentsOf(members)) {
      byRoot.put(Long.valueOf(payment.root().transactionId()), payment);
    }
    List<ArrivalOf<TranzilaPayment>> ordered = new ArrayList<>();
    for (ArrivalOf<Long> root : roots) {
      TranzilaPayment payment = byRoot.get(root.row());
      if (payment != null) {
        ordered.add(new ArrivalOf<>(root.arrival(), payment));
      }
    }
    return List.copyOf(ordered);
  }
}
