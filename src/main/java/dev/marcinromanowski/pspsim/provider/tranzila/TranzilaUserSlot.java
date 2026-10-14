package dev.marcinromanowski.pspsim.provider.tranzila;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

record TranzilaUserSlot(int fieldId, String name, Instant firstRecordedAt) {

  record FirstRecorded(String name, Instant at) {}

  static List<TranzilaUserSlot> numbered(List<FirstRecorded> inTheOrderFirstRecorded) {
    List<TranzilaUserSlot> slots = new ArrayList<>();
    for (FirstRecorded first : inTheOrderFirstRecorded) {
      slots.add(new TranzilaUserSlot(slots.size() + 1, first.name(), first.at()));
    }
    return List.copyOf(slots);
  }
}
