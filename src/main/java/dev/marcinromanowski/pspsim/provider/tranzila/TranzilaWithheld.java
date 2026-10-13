package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

final class TranzilaWithheld {

  private TranzilaWithheld() {}

  static String theTextOf(TranzilaBody body, Map<String, List<String>> redacted) {
    TranzilaJson json = body.json();
    return json == null
        ? body.text()
        : TranzilaJsonWriter.toText(withheld(json, null, theKeysItRedacted(redacted)));
  }

  static Set<String> theKeysItRedacted(Map<String, List<String>> redacted) {
    return redacted.entrySet().stream()
        .filter(field -> !field.getValue().isEmpty())
        .filter(field -> field.getValue().stream().allMatch(Redaction.REDACTED::equals))
        .map(field -> field.getKey().toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  static TranzilaJson withheld(TranzilaJson value, @Nullable String path, Set<String> redacted) {
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
                      redacted.contains(at.toLowerCase(Locale.ROOT))
                          ? TranzilaJson.text(Redaction.REDACTED)
                          : withheld(member, at, redacted));
                });
        yield TranzilaJson.object(kept);
      }
      case TranzilaJson.JsonArray array ->
          TranzilaJson.array(
              array.elements().stream().map(element -> withheld(element, path, redacted)).toList());
      default -> value;
    };
  }
}
