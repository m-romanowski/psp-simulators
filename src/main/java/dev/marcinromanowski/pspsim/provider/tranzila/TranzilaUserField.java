package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.List;
import java.util.Locale;

record TranzilaUserField(String name, String value) {

  static List<TranzilaUserField> theOnesASlotKeeps(List<TranzilaUserField> sent) {
    return sent.stream().filter(TranzilaUserField::namesNoIdentity).toList();
  }

  boolean namesNoIdentity() {
    return !TranzilaFields.SENSITIVE_REQUEST_FIELDS.contains(name.toLowerCase(Locale.ROOT));
  }
}
