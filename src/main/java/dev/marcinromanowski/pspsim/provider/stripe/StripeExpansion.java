package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class StripeExpansion {

  static final String EXPAND = "expand";

  static final String DATA = "data";

  static final int DEEPEST = 4;

  private final StripeExpandable kind;
  private final boolean listed;

  private StripeExpansion(StripeExpandable kind, boolean listed) {
    this.kind = kind;
    this.listed = listed;
  }

  static StripeExpansion ofAnObject(StripeExpandable kind) {
    return new StripeExpansion(kind, false);
  }

  static StripeExpansion ofAList(StripeExpandable kind) {
    return new StripeExpansion(kind, true);
  }

  StripeOperationResult answered(
      StripeForm carrying,
      StripeRelatedObjects.Reach reach,
      Supplier<StripeOperationResult> operation) {
    List<String> paths = carrying.listOf(EXPAND);
    if (paths == null) {
      return refused(StripeErrors.unreadableParameter(EXPAND, StripeForm.theListShapesOf(EXPAND)));
    }
    Level asked = new Level(null);
    for (String path : paths) {
      StripeError refusal = theRefusalOf(path, asked);
      if (refusal != null) {
        return refused(refusal);
      }
    }
    StripeOperationResult result = operation.get();
    return result.answeringWith(expanded(result.body(), asked, reach));
  }

  private static StripeOperationResult refused(StripeError error) {
    return StripeOperationResult.refusedBeforeExecuting(
        StripeRefusals.theRequestWasRefused(), error);
  }

  private @Nullable StripeError theRefusalOf(String path, Level asked) {
    List<String> segments = List.of(path.split("\\.", -1));
    if (segments.size() > DEEPEST) {
      return StripeErrors.expandedTooDeep(path);
    }
    boolean inAList = listed;
    StripeExpandable at = kind;
    Level level = asked;
    for (int index = 0; index < segments.size(); index++) {
      String segment = segments.get(index);
      String reached = String.join(".", segments.subList(0, index + 1));
      if (inAList) {
        if (!DATA.equals(segment)) {
          return StripeErrors.notExpandableOnAList(reached, DATA + "." + path);
        }
        inAList = false;
        level = level.step(segment, null);
        continue;
      }
      if (!at.publishes(segment)) {
        return StripeErrors.notExpandable(reached);
      }
      Optional<StripeRelation> relation = at.holds(segment);
      if (relation.isEmpty()) {
        return null;
      }
      level = level.step(segment, relation.get());
      at = relation.get().target();
    }
    return null;
  }

  private static StripeJson expanded(
      StripeJson answered, Level level, StripeRelatedObjects.Reach reach) {
    Map<String, StripeJson> members =
        new LinkedHashMap<>(((StripeJson.JsonObject) answered).members());
    members.replaceAll(
        (field, member) -> {
          Level deeper = level.next.get(field);
          return deeper == null ? member : stepped(member, deeper, reach);
        });
    return new StripeJson.JsonObject(members);
  }

  private static StripeJson stepped(
      StripeJson member, Level deeper, StripeRelatedObjects.Reach reach) {
    StripeRelation relation = deeper.relation;
    if (relation == null) {
      List<StripeJson> items = new ArrayList<>();
      for (StripeJson item : ((StripeJson.JsonArray) member).elements()) {
        items.add(expanded(item, deeper, reach));
      }
      return StripeJson.array(items);
    }
    if (!(member instanceof StripeJson.JsonText id)) {
      return member;
    }
    return reach
        .find(relation, id.value())
        .map(found -> expanded(found, deeper, reach))
        .orElse(member);
  }

  private static final class Level {

    private final @Nullable StripeRelation relation;
    private final Map<String, Level> next = new LinkedHashMap<>();

    private Level(@Nullable StripeRelation relation) {
      this.relation = relation;
    }

    Level step(String field, @Nullable StripeRelation reached) {
      return next.computeIfAbsent(field, absent -> new Level(reached));
    }
  }
}
