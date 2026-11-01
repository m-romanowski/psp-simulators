package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.member;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.object;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StripeResponseSchema {

  record Problem(String path, String rule) {}

  private final StripeJson schemas;
  private final Map<String, Set<String>> emitted = new HashMap<>();

  StripeResponseSchema(StripeJson schemas) {
    this.schemas = schemas;
  }

  List<Problem> problems(String name, StripeJson answer) {
    observe(name, answer);
    return problems(member(schemas, name), answer, name);
  }

  private List<Problem> problems(StripeJson shape, StripeJson answer, String path) {
    Map<String, StripeJson> schema = object(shape).members();
    if (schema.get("x-responseBreakingEnumValues") instanceof StripeJson.JsonArray forbidden
        && forbidden.elements().contains(answer)) {
      return List.of(new Problem(path, "breaking-enum"));
    }
    if (answer instanceof StripeJson.JsonNull
        && StripeJson.bool(true).equals(schema.get("nullable"))) {
      return List.of();
    }
    StripeJson reference = schema.get("$ref");
    if (reference instanceof StripeJson.JsonText ref) {
      String target = ref.value().substring("#/components/schemas/".length());
      List<Problem> failures = problems(member(schemas, target), answer, path);
      if (failures.isEmpty()) {
        observe(target, answer);
      }
      return failures;
    }
    for (String union : List.of("anyOf", "oneOf")) {
      if (schema.get(union) instanceof StripeJson.JsonArray alternatives) {
        List<List<Problem>> candidates =
            alternatives.elements().stream()
                .map(candidate -> problems(candidate, answer, path))
                .toList();
        long matching = candidates.stream().filter(List::isEmpty).count();
        if (matching == 0) {
          List<List<Problem>> shaped = new ArrayList<>();
          for (StripeJson candidate : alternatives.elements()) {
            if (acceptsKind(candidate, answer)) {
              shaped.add(problems(candidate, answer, path));
            }
          }
          return (shaped.isEmpty() ? candidates : shaped)
              .stream().min(java.util.Comparator.comparingInt(List::size)).orElseThrow();
        }
        if ("oneOf".equals(union) && matching != 1) {
          return List.of(new Problem(path, "oneOf"));
        }
      }
    }
    List<Problem> failures = new ArrayList<>();
    if (schema.get("allOf") instanceof StripeJson.JsonArray all) {
      for (StripeJson candidate : all.elements()) {
        failures.addAll(problems(candidate, answer, path));
      }
    }
    if (answer instanceof StripeJson.JsonNull) {
      return List.of(new Problem(path, "non-nullable"));
    }
    if (schema.get("type") instanceof StripeJson.JsonText type && !matches(type.value(), answer)) {
      return List.of(new Problem(path, "type:" + type.value()));
    }
    if (schema.get("enum") instanceof StripeJson.JsonArray values
        && !StripeJson.bool(true).equals(schema.get("x-stripeBypassValidation"))
        && !values.elements().contains(answer)) {
      failures.add(new Problem(path, "enum"));
    }
    if (answer instanceof StripeJson.JsonObject obj) {
      StripeJson properties = schema.get("properties");
      StripeJson additional = schema.get("additionalProperties");
      for (Map.Entry<String, StripeJson> field : obj.members().entrySet()) {
        StripeJson child =
            properties instanceof StripeJson.JsonObject props
                ? props.members().get(field.getKey())
                : null;
        if (child != null) {
          failures.addAll(problems(child, field.getValue(), path + "." + field.getKey()));
        } else if (additional instanceof StripeJson.JsonObject) {
          failures.addAll(problems(additional, field.getValue(), path + "." + field.getKey()));
        } else if (properties != null && !StripeJson.bool(true).equals(additional)) {
          failures.add(new Problem(path + "." + field.getKey(), "unpublished"));
        } else if (StripeJson.bool(false).equals(additional)) {
          failures.add(new Problem(path + "." + field.getKey(), "unpublished"));
        }
      }
    }
    if (answer instanceof StripeJson.JsonArray array
        && schema.get("items") instanceof StripeJson.JsonObject items) {
      for (StripeJson element : array.elements()) {
        failures.addAll(problems(items, element, path + "[]"));
      }
    }
    return failures;
  }

  Map<String, Set<String>> emitted() {
    return emitted;
  }

  private void observe(String name, StripeJson answer) {
    if (answer instanceof StripeJson.JsonObject object) {
      emitted.computeIfAbsent(name, absent -> new HashSet<>()).addAll(object.members().keySet());
    }
  }

  private boolean acceptsKind(StripeJson shape, StripeJson answer) {
    Map<String, StripeJson> schema = object(shape).members();
    if (schema.get("$ref") instanceof StripeJson.JsonText ref) {
      return acceptsKind(
          member(schemas, ref.value().substring("#/components/schemas/".length())), answer);
    }
    return !(schema.get("type") instanceof StripeJson.JsonText type)
        || matches(type.value(), answer);
  }

  private static boolean matches(String type, StripeJson value) {
    return switch (type) {
      case "string" -> value instanceof StripeJson.JsonText;
      case "boolean" -> value instanceof StripeJson.JsonBoolean;
      case "object" -> value instanceof StripeJson.JsonObject;
      case "array" -> value instanceof StripeJson.JsonArray;
      case "number" -> value instanceof StripeJson.JsonRawNumber;
      case "integer" ->
          value instanceof StripeJson.JsonRawNumber number
              && new BigDecimal(number.lexicalForm()).stripTrailingZeros().scale() <= 0;
      default -> throw new IllegalArgumentException("Unsupported schema type " + type);
    };
  }
}
