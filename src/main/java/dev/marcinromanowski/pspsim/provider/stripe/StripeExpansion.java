package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class StripeExpansion {

  static final String EXPAND = "expand";

  static final String DATA = "data";

  static final int DEEPEST = 4;

  private static final Set<String> WHAT_A_LIST_CARRIES_BESIDE_ITS_DATA =
      Set.of("object", "has_more", "url");

  private final @Nullable StripeExpandable kind;
  private final boolean listed;

  private StripeExpansion(@Nullable StripeExpandable kind, boolean listed) {
    this.kind = kind;
    this.listed = listed;
  }

  static StripeExpansion ofAnObject(StripeExpandable kind) {
    return new StripeExpansion(kind, false);
  }

  static StripeExpansion ofAList(StripeExpandable kind) {
    return new StripeExpansion(kind, true);
  }

  static StripeExpansion ofAnObjectThatExpandsNothing() {
    return new StripeExpansion(null, false);
  }

  StripeOperationResult answered(
      StripeForm carrying,
      StripeRelatedObjects.Reach reach,
      Supplier<StripeOperationResult> operation) {
    List<String> paths = carrying.listOf(EXPAND);
    if (paths == null) {
      return refused(StripeErrors.unreadableParameter(EXPAND, StripeForm.theListShapesOf(EXPAND)));
    }
    Level asked = new Level();
    for (String path : paths) {
      StripeError refusal = theRefusalOf(path, asked);
      if (refusal != null) {
        return refused(refusal);
      }
    }
    StripeOperationResult result = operation.get();
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return result;
    }
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
    StripeExpandable at = kind;
    if (at == null) {
      return StripeErrors.notExpandable(segments.get(0));
    }
    boolean inAList = listed;
    Level level = asked;
    for (int index = 0; index < segments.size(); index++) {
      String segment = segments.get(index);
      String reached = String.join(".", segments.subList(0, index + 1));
      if (inAList) {
        if (DATA.equals(segment)) {
          inAList = false;
          level = level.step(segment, new Step.IntoTheList());
          continue;
        }
        if (WHAT_A_LIST_CARRIES_BESIDE_ITS_DATA.contains(segment)) {
          return null;
        }
        return StripeErrors.notExpandableOnAList(reached, throughData(segments, index));
      }
      if (!at.publishes(segment)) {
        return StripeErrors.doesNotExist(reached);
      }
      Optional<StripeRelation> relation = at.holds(segment);
      if (relation.isPresent()) {
        level = level.step(segment, new Step.Related(relation.get()));
        at = relation.get().target();
        continue;
      }
      Optional<StripeIncludable> includable = at.includes(segment);
      if (includable.isEmpty()) {
        return null;
      }
      level = level.step(segment, new Step.Included(includable.get()));
      Optional<StripeExpandable> items = includable.get().listing();
      if (items.isEmpty()) {
        return null;
      }
      inAList = true;
      at = items.get();
    }
    return null;
  }

  private static String throughData(List<String> segments, int index) {
    List<String> suggested = new ArrayList<>(segments.subList(0, index));
    suggested.add(DATA);
    suggested.addAll(segments.subList(index, segments.size()));
    return String.join(".", suggested);
  }

  private static StripeJson expanded(
      StripeJson answered, Level level, StripeRelatedObjects.Reach reach) {
    Map<String, StripeJson> members =
        new LinkedHashMap<>(((StripeJson.JsonObject) answered).members());
    level.next.forEach(
        (field, child) -> {
          switch (child.step()) {
            case Step.IntoTheList _ ->
                members.computeIfPresent(
                    field, (name, member) -> eachExpanded(member, child.level(), reach));
            case Step.Related related ->
                members.computeIfPresent(
                    field,
                    (name, member) -> relatedExpanded(member, related, child.level(), reach));
            case Step.Included included ->
                reach
                    .included(included.includable(), idOf(members))
                    .ifPresent(
                        value ->
                            members.put(
                                field,
                                child.level().next.isEmpty()
                                    ? value
                                    : expanded(value, child.level(), reach)));
          }
        });
    return new StripeJson.JsonObject(members);
  }

  private static String idOf(Map<String, StripeJson> members) {
    return ((StripeJson.JsonText) Objects.requireNonNull(members.get("id"))).value();
  }

  private static StripeJson eachExpanded(
      StripeJson member, Level level, StripeRelatedObjects.Reach reach) {
    List<StripeJson> items = new ArrayList<>();
    for (StripeJson item : ((StripeJson.JsonArray) member).elements()) {
      items.add(expanded(item, level, reach));
    }
    return StripeJson.array(items);
  }

  private static StripeJson relatedExpanded(
      StripeJson member, Step.Related related, Level level, StripeRelatedObjects.Reach reach) {
    if (!(member instanceof StripeJson.JsonText id)) {
      return member;
    }
    return reach
        .find(related.relation(), id.value())
        .map(found -> expanded(found, level, reach))
        .orElse(member);
  }

  private sealed interface Step {

    record IntoTheList() implements Step {}

    record Related(StripeRelation relation) implements Step {}

    record Included(StripeIncludable includable) implements Step {}
  }

  private record Child(Step step, Level level) {}

  private static final class Level {

    private final Map<String, Child> next = new LinkedHashMap<>();

    Level step(String field, Step reached) {
      return next.computeIfAbsent(field, absent -> new Child(reached, new Level())).level();
    }
  }
}
