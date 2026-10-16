package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

record TranzilaUserField(String name, String value) {

  static List<TranzilaUserField> theOnesASlotKeeps(List<TranzilaUserField> sent) {
    return sent.stream().filter(TranzilaUserField::namesNoIdentity).toList();
  }

  boolean namesNoIdentity() {
    return !namesAnIdentity(name);
  }

  static boolean namesAnIdentity(String name) {
    return TranzilaFields.SENSITIVE_REQUEST_FIELDS.contains(name.toLowerCase(Locale.ROOT));
  }

  static TranzilaJson withTheIdentitiesWithheld(TranzilaJson json) {
    return switch (json) {
      case TranzilaJson.JsonObject object -> withheldIn(object);
      case TranzilaJson.JsonArray array ->
          TranzilaJson.array(
              array.elements().stream().map(TranzilaUserField::withTheIdentitiesWithheld).toList());
      default -> json;
    };
  }

  private static TranzilaJson.JsonObject withheldIn(TranzilaJson.JsonObject object) {
    Map<String, TranzilaJson> members = new LinkedHashMap<>();
    object
        .members()
        .forEach(
            (name, member) ->
                members.put(
                    name,
                    TranzilaFields.USER_DEFINED_FIELDS.equals(name)
                        ? entriesWithheld(member)
                        : withTheIdentitiesWithheld(member)));
    return TranzilaJson.object(members);
  }

  private static TranzilaJson entriesWithheld(TranzilaJson member) {
    if (!(member instanceof TranzilaJson.JsonArray entries)) {
      return withTheIdentitiesWithheld(member);
    }
    return TranzilaJson.array(
        entries.elements().stream().map(TranzilaUserField::anEntryWithheld).toList());
  }

  private static TranzilaJson anEntryWithheld(TranzilaJson element) {
    if (!(element instanceof TranzilaJson.JsonObject entry)
        || !entry.has(TranzilaFields.VALUE)
        || entry.text(TranzilaFields.NAME).filter(TranzilaUserField::namesAnIdentity).isEmpty()) {
      return element;
    }
    Map<String, TranzilaJson> members = new LinkedHashMap<>(entry.members());
    members.put(TranzilaFields.VALUE, TranzilaJson.text(Redaction.REDACTED));
    return TranzilaJson.object(members);
  }
}
