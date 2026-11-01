package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.member;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeSpecSnapshot.object;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StripeResponseEvolution {

  record Snapshot(String version, StripeJson schemas) {}

  record BreakingChange(
      String version, String field, String kind, String oldValue, String newValue, String url) {}

  record Difference(String version, String field, String kind) {}

  private final List<BreakingChange> changes;

  StripeResponseEvolution(List<BreakingChange> changes) {
    this.changes = List.copyOf(changes);
  }

  static List<BreakingChange> changes(StripeJson data) {
    return ((StripeJson.JsonArray) data)
        .elements().stream()
            .map(
                entry ->
                    new BreakingChange(
                        text(entry, "version"),
                        text(entry, "field"),
                        text(entry, "kind"),
                        optionalText(entry, "old_value"),
                        optionalText(entry, "new_value"),
                        text(entry, "url")))
            .toList();
  }

  StripeJson at(String version, List<Snapshot> snapshots) {
    Map<String, StripeJson> schemas =
        new LinkedHashMap<>(
            object(
                    snapshots.stream()
                        .filter(snapshot -> snapshot.version().equals(version))
                        .findFirst()
                        .orElseThrow()
                        .schemas())
                .members());
    for (Snapshot snapshot : snapshots) {
      if (snapshot.version().compareTo(version) <= 0) {
        continue;
      }
      object(snapshot.schemas())
          .members()
          .forEach(
              (name, schema) ->
                  schemas.merge(
                      name,
                      schema,
                      (earlier, later) ->
                          additive(earlier, later, name, version, snapshot.version())));
    }
    return new StripeJson.JsonObject(schemas);
  }

  private StripeJson additive(
      StripeJson earlier, StripeJson later, String path, String answeredAt, String introducedAt) {
    if (!(earlier instanceof StripeJson.JsonObject oldShape)
        || !(later instanceof StripeJson.JsonObject newShape)) {
      return earlier;
    }
    Map<String, StripeJson> merged = new LinkedHashMap<>(oldShape.members());
    StripeJson oldProperties = oldShape.members().get("properties");
    StripeJson newProperties = newShape.members().get("properties");
    if (oldProperties instanceof StripeJson.JsonObject oldFields
        && newProperties instanceof StripeJson.JsonObject newFields) {
      Map<String, StripeJson> fields = new LinkedHashMap<>(oldFields.members());
      newFields
          .members()
          .forEach(
              (name, shape) -> {
                String field = path + "." + name;
                StripeJson previous = fields.get(name);
                if (previous == null) {
                  if (!blocked(field, answeredAt, introducedAt)) {
                    fields.put(name, shape);
                  }
                } else {
                  fields.put(name, additive(previous, shape, field, answeredAt, introducedAt));
                }
              });
      merged.put("properties", new StripeJson.JsonObject(fields));
    }
    if (oldShape.members().get("enum") instanceof StripeJson.JsonArray oldEnum
        && newShape.members().get("enum") instanceof StripeJson.JsonArray newEnum) {
      Set<StripeJson> values = new LinkedHashSet<>(oldEnum.elements());
      for (StripeJson value : newEnum.elements()) {
        if (!blocked(path, answeredAt, introducedAt)
            && !breakingValue(path, value, answeredAt, introducedAt)) {
          values.add(value);
        }
      }
      merged.put("enum", StripeJson.array(List.copyOf(values)));
    }
    for (String child : List.of("items", "additionalProperties")) {
      StripeJson oldChild = oldShape.members().get(child);
      StripeJson newChild = newShape.members().get(child);
      if (oldChild != null && newChild != null) {
        merged.put(
            child,
            additive(
                oldChild,
                newChild,
                path + (child.equals("items") ? "[]" : ".*"),
                answeredAt,
                introducedAt));
      }
    }
    for (String union : List.of("anyOf", "oneOf", "allOf")) {
      if (oldShape.members().get(union) instanceof StripeJson.JsonArray oldBranches
          && newShape.members().get(union) instanceof StripeJson.JsonArray newBranches) {
        List<StripeJson> branches = new ArrayList<>();
        for (StripeJson oldBranch : oldBranches.elements()) {
          StripeJson match =
              newBranches.elements().stream()
                  .filter(branch -> identity(branch).equals(identity(oldBranch)))
                  .findFirst()
                  .orElse(oldBranch);
          branches.add(additive(oldBranch, match, path, answeredAt, introducedAt));
        }
        merged.put(union, StripeJson.array(branches));
      }
    }
    List<StripeJson> forbidden =
        changes.stream()
            .filter(
                change ->
                    crossing(change, answeredAt, introducedAt)
                        && change.field().equals(path)
                        && change.kind().equals("enum_replacement"))
            .map(change -> (StripeJson) StripeJson.text(change.newValue()))
            .toList();
    if (!forbidden.isEmpty()) {
      merged.put("x-responseBreakingEnumValues", StripeJson.array(forbidden));
    }
    return new StripeJson.JsonObject(merged);
  }

  List<Difference> undeclared(Snapshot earlier, Snapshot later) {
    List<Difference> differences = new ArrayList<>();
    object(earlier.schemas())
        .members()
        .forEach(
            (name, schema) -> {
              StripeJson next = object(later.schemas()).members().get(name);
              if (next == null) {
                differences.add(new Difference(later.version(), name, "removed"));
              } else {
                compare(schema, next, name, later.version(), differences);
              }
            });
    return differences.stream()
        .filter(
            difference ->
                changes.stream()
                    .noneMatch(
                        change ->
                            change.version().equals(difference.version())
                                && change.field().equals(difference.field())
                                && (change.kind().equals(difference.kind())
                                    || (change.kind().equals("enum_replacement")
                                        && difference.kind().equals("enum_narrowed")))))
        .toList();
  }

  private static void compare(
      StripeJson earlier, StripeJson later, String path, String version, List<Difference> into) {
    if (!(earlier instanceof StripeJson.JsonObject oldShape)
        || !(later instanceof StripeJson.JsonObject newShape)) {
      if (!earlier.equals(later)) {
        into.add(new Difference(version, path, "type_changed"));
      }
      return;
    }
    for (String key : List.of("type", "$ref")) {
      if (!java.util.Objects.equals(oldShape.members().get(key), newShape.members().get(key))) {
        into.add(new Difference(version, path, "type_changed"));
      }
    }
    if (StripeJson.bool(true).equals(oldShape.members().get("nullable"))
        && !StripeJson.bool(true).equals(newShape.members().get("nullable"))) {
      into.add(new Difference(version, path, "nullability_narrowed"));
    }
    StripeJson oldEnum = oldShape.members().get("enum");
    StripeJson newEnum = newShape.members().get("enum");
    if (newEnum instanceof StripeJson.JsonArray newValues
        && (!(oldEnum instanceof StripeJson.JsonArray oldValues)
            || !newValues.elements().containsAll(oldValues.elements())
            || (StripeJson.bool(true).equals(oldShape.members().get("x-stripeBypassValidation"))
                && !StripeJson.bool(true)
                    .equals(newShape.members().get("x-stripeBypassValidation"))))) {
      into.add(new Difference(version, path, "enum_narrowed"));
    }
    if (oldShape.members().get("properties") instanceof StripeJson.JsonObject fields) {
      Map<String, StripeJson> next =
          newShape.members().get("properties") instanceof StripeJson.JsonObject properties
              ? properties.members()
              : Map.of();
      fields
          .members()
          .forEach(
              (name, shape) -> {
                StripeJson target = next.get(name);
                if (target == null) {
                  into.add(new Difference(version, path + "." + name, "removed"));
                } else {
                  compare(shape, target, path + "." + name, version, into);
                }
              });
    }
    for (String key : List.of("items", "additionalProperties")) {
      StripeJson oldChild = oldShape.members().get(key);
      StripeJson newChild = newShape.members().get(key);
      if (oldChild == null && newChild != null && !StripeJson.bool(true).equals(newChild)) {
        into.add(new Difference(version, path, "type_changed"));
      } else if (oldChild != null && newChild != null) {
        compare(oldChild, newChild, path + (key.equals("items") ? "[]" : ".*"), version, into);
      }
    }
    for (String union : List.of("anyOf", "oneOf", "allOf")) {
      if (!oldShape.members().containsKey(union) && newShape.members().containsKey(union)) {
        into.add(new Difference(version, path, "type_changed"));
      }
      if (oldShape.members().get(union) instanceof StripeJson.JsonArray branches) {
        List<StripeJson> next =
            newShape.members().get(union) instanceof StripeJson.JsonArray array
                ? array.elements()
                : List.of();
        Set<String> oldIdentities = new LinkedHashSet<>();
        Set<String> newIdentities = new LinkedHashSet<>();
        branches.elements().forEach(branch -> oldIdentities.add(identity(branch)));
        next.forEach(branch -> newIdentities.add(identity(branch)));
        if (!oldIdentities.equals(newIdentities)) {
          into.add(new Difference(version, path, "type_changed"));
        } else {
          for (StripeJson branch : branches.elements()) {
            compare(
                branch,
                next.stream()
                    .filter(candidate -> identity(candidate).equals(identity(branch)))
                    .findFirst()
                    .orElseThrow(),
                path,
                version,
                into);
          }
        }
      }
    }
  }

  private boolean blocked(String path, String answeredAt, String introducedAt) {
    return changes.stream()
        .anyMatch(
            change ->
                crossing(change, answeredAt, introducedAt)
                    && !change.kind().equals("enum_replacement")
                    && (change.field().equals(path) || path.startsWith(change.field() + ".")));
  }

  private boolean breakingValue(
      String path, StripeJson value, String answeredAt, String introducedAt) {
    return changes.stream()
        .anyMatch(
            change ->
                crossing(change, answeredAt, introducedAt)
                    && change.field().equals(path)
                    && change.kind().equals("enum_replacement")
                    && StripeJson.text(change.newValue()).equals(value));
  }

  private static boolean crossing(BreakingChange change, String answeredAt, String introducedAt) {
    return change.version().compareTo(answeredAt) > 0
        && change.version().compareTo(introducedAt) <= 0;
  }

  private static String identity(StripeJson shape) {
    Map<String, StripeJson> members = object(shape).members();
    return StripeJsonWriter.toText(
        members.getOrDefault("$ref", members.getOrDefault("type", shape)));
  }

  private static String text(StripeJson value, String name) {
    return ((StripeJson.JsonText) member(value, name)).value();
  }

  private static String optionalText(StripeJson value, String name) {
    StripeJson field = object(value).members().get(name);
    return field instanceof StripeJson.JsonText text ? text.value() : "";
  }
}
