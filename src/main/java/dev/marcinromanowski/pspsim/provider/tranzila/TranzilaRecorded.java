package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record TranzilaRecorded(
    TranzilaTransaction transaction, List<TranzilaUserField> userDefinedFields, List<Item> items) {

  TranzilaRecorded {
    userDefinedFields = List.copyOf(userDefinedFields);
    items = List.copyOf(items);
  }

  record Item(long itemId, TranzilaItem item) {}

  record Owned<T>(long transactionId, T value) {}

  static List<TranzilaRecorded> assembled(
      List<TranzilaTransaction> transactions,
      List<Owned<TranzilaUserField>> fields,
      List<Owned<Item>> items) {
    Map<Long, List<TranzilaUserField>> fieldsOf = new LinkedHashMap<>();
    for (Owned<TranzilaUserField> field : fields) {
      fieldsOf
          .computeIfAbsent(Long.valueOf(field.transactionId()), absent -> new ArrayList<>())
          .add(field.value());
    }
    Map<Long, List<Item>> itemsOf = new LinkedHashMap<>();
    for (Owned<Item> item : items) {
      itemsOf
          .computeIfAbsent(Long.valueOf(item.transactionId()), absent -> new ArrayList<>())
          .add(item.value());
    }
    List<TranzilaRecorded> recorded = new ArrayList<>();
    for (TranzilaTransaction transaction : transactions) {
      Long id = Long.valueOf(transaction.transactionId());
      recorded.add(
          new TranzilaRecorded(
              transaction,
              fieldsOf.getOrDefault(id, List.of()),
              itemsOf.getOrDefault(id, List.of())));
    }
    return List.copyOf(recorded);
  }
}
