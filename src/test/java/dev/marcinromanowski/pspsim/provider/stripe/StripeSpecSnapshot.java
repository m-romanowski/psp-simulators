package dev.marcinromanowski.pspsim.provider.stripe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

final class StripeSpecSnapshot {

  static final List<String> ROOTS =
      List.of(
          "payment_intent",
          "setup_intent",
          "charge",
          "refund",
          "customer",
          "deleted_customer",
          "payment_method",
          "ephemeral_key",
          "event",
          "error",
          "api_errors");

  private static final Set<String> KEYS =
      Set.of(
          "$ref",
          "type",
          "nullable",
          "enum",
          "properties",
          "anyOf",
          "oneOf",
          "allOf",
          "items",
          "additionalProperties",
          "x-expandableFields",
          "x-stripeBypassValidation");

  private StripeSpecSnapshot() {}

  public static void main(String[] arguments) throws IOException {
    Path input = Path.of(arguments[0]);
    Path output = Path.of(arguments[1]);
    StripeJson.JsonObject full = object(StripeJsonReader.read(Files.readString(input)));
    Files.writeString(output, trimmed(full, arguments[2], arguments[3], arguments[4]));
  }

  static String trimmed(StripeJson.JsonObject full, String version, String commit, String source) {
    if (!member(member(full, "info"), "version").equals(StripeJson.text(version))) {
      throw new IllegalArgumentException("The source spec does not publish " + version);
    }
    if (!commit.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("Provenance needs a full commit SHA");
    }
    StripeJson.JsonObject published = object(member(member(full, "components"), "schemas"));
    Map<String, StripeJson> kept = new TreeMap<>();
    ArrayDeque<String> pending = new ArrayDeque<>(ROOTS);
    while (!pending.isEmpty()) {
      String name = pending.removeFirst();
      if (kept.containsKey(name)) {
        continue;
      }
      StripeJson schema = trim(member(published, name));
      kept.put(name, schema);
      references(schema, pending);
    }
    StripeJson response = full;
    for (String segment :
        List.of(
            "paths",
            "/v1/payment_methods",
            "get",
            "responses",
            "200",
            "content",
            "application/json",
            "schema")) {
      response = member(response, segment);
    }
    StripeJson list = trim(response);
    kept.put("payment_method_list", list);
    references(list, pending);
    if (!pending.stream().allMatch(kept::containsKey)) {
      throw new IllegalArgumentException("The list introduces a schema outside the object closure");
    }
    StringBuilder output = new StringBuilder();
    output
        .append("{\n  \"provenance\": ")
        .append(
            StripeJsonWriter.toText(
                StripeJson.object(
                    "version",
                    StripeJson.text(version),
                    "commit",
                    StripeJson.text(commit),
                    "source",
                    StripeJson.text(source))))
        .append(",\n  \"schemas\": {\n");
    List<String> lines = new ArrayList<>();
    kept.forEach(
        (name, schema) ->
            lines.add(
                "    "
                    + StripeJsonWriter.toText(StripeJson.text(name))
                    + ": "
                    + StripeJsonWriter.toText(schema)));
    output.append(String.join(",\n", lines)).append("\n  }\n}\n");
    return output.toString();
  }

  private static StripeJson trim(StripeJson value) {
    return switch (value) {
      case StripeJson.JsonObject object -> {
        Map<String, StripeJson> kept = new TreeMap<>();
        object
            .members()
            .forEach(
                (name, member) -> {
                  if (KEYS.contains(name)) {
                    if ("properties".equals(name)) {
                      Map<String, StripeJson> properties = new TreeMap<>();
                      object(member)
                          .members()
                          .forEach((field, schema) -> properties.put(field, trim(schema)));
                      kept.put(name, new StripeJson.JsonObject(properties));
                    } else {
                      kept.put(name, trim(member));
                    }
                  }
                });
        yield new StripeJson.JsonObject(kept);
      }
      case StripeJson.JsonArray array ->
          StripeJson.array(array.elements().stream().map(StripeSpecSnapshot::trim).toList());
      default -> value;
    };
  }

  private static void references(StripeJson value, ArrayDeque<String> into) {
    switch (value) {
      case StripeJson.JsonObject object ->
          object
              .members()
              .forEach(
                  (name, member) -> {
                    if ("$ref".equals(name)) {
                      String reference = ((StripeJson.JsonText) member).value();
                      String prefix = "#/components/schemas/";
                      if (!reference.startsWith(prefix)) {
                        throw new IllegalArgumentException("Unsupported reference " + reference);
                      }
                      into.add(reference.substring(prefix.length()));
                    } else {
                      references(member, into);
                    }
                  });
      case StripeJson.JsonArray array ->
          array.elements().forEach(element -> references(element, into));
      default -> {}
    }
  }

  static StripeJson.JsonObject object(StripeJson value) {
    return (StripeJson.JsonObject) value;
  }

  static StripeJson member(StripeJson value, String name) {
    return Objects.requireNonNull(object(value).members().get(name), name);
  }
}
