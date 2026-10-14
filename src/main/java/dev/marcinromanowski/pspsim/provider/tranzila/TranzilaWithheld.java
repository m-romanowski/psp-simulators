package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class TranzilaWithheld {

  private TranzilaWithheld() {}

  static String theTextOf(TranzilaBody body) {
    TranzilaJson json = body.json();
    return json == null ? body.text() : TranzilaJsonWriter.toText(withheld(json, null));
  }

  static boolean isWithheld(String path) {
    return Redaction.redact(Map.of(path, List.of(path)), TranzilaFields.SENSITIVE_RESPONSE_FIELDS)
        .getOrDefault(path, List.of())
        .contains(Redaction.REDACTED);
  }

  static TranzilaJson withheld(TranzilaJson value, @Nullable String path) {
    return switch (value) {
      case TranzilaJson.JsonObject object -> {
        Map<String, TranzilaJson> kept = new LinkedHashMap<>();
        object
            .members()
            .forEach(
                (name, member) -> {
                  String at = path == null ? name : path + "." + name;
                  kept.put(
                      name,
                      isWithheld(at)
                          ? TranzilaJson.text(Redaction.REDACTED)
                          : withheld(member, at));
                });
        yield TranzilaJson.object(kept);
      }
      case TranzilaJson.JsonArray array ->
          TranzilaJson.array(
              array.elements().stream().map(element -> withheld(element, path)).toList());
      default -> value;
    };
  }
}
