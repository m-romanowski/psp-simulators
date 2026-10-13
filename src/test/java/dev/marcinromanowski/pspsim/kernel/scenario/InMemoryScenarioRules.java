package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class InMemoryScenarioRules implements ScenarioRules {

  private static final Instant CREATED_AT = Instant.parse("2026-09-07T12:00:00Z");

  private record OneFlow(ProviderId provider, TestRunId testRunId, StoryKey key) {}

  private final List<ScenarioRule> stored = new ArrayList<>();
  private final Map<OneFlow, Map<String, String>> states = new LinkedHashMap<>();
  private final Map<OneFlow, ReentrantLock> locks = new LinkedHashMap<>();
  private long arrivals;
  private boolean oldestFirst;
  private boolean everyMoveIsLostWithoutTheStoryMoving;

  public InMemoryScenarioRules handingBackTheOldestFirst() {
    oldestFirst = true;
    return this;
  }

  public InMemoryScenarioRules losingEveryMoveWithoutTheStoryMoving() {
    everyMoveIsLostWithoutTheStoryMoving = true;
    return this;
  }

  @Override
  public ScenarioRule add(NewScenarioRule rule) {
    arrivals++;
    ScenarioRule registered =
        new ScenarioRule(
            "scn_" + arrivals,
            rule.provider(),
            rule.testRunId(),
            rule.priority(),
            arrivals,
            rule.match(),
            rule.action(),
            rule.story(),
            CREATED_AT);
    stored.add(registered);
    return registered;
  }

  @Override
  public List<ScenarioRule> addAll(List<NewScenarioRule> rules) {
    List<ScenarioRule> added = new ArrayList<>();
    for (NewScenarioRule rule : rules) {
      added.add(add(rule));
    }
    return List.copyOf(added);
  }

  @Override
  public Optional<ScenarioRule> find(ProviderId provider, TestRunId testRunId, String scenarioId) {
    return of(provider, testRunId).stream()
        .filter(rule -> rule.scenarioId().equals(scenarioId))
        .findFirst();
  }

  @Override
  public List<ScenarioRule> of(ProviderId provider, TestRunId testRunId) {
    List<ScenarioRule> matching = new ArrayList<>();
    for (ScenarioRule rule : stored) {
      if (rule.provider().equals(provider) && rule.testRunId().equals(testRunId)) {
        matching.add(rule);
      }
    }
    if (!oldestFirst) {
      matching.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
    }
    return matching;
  }

  @Override
  public boolean remove(ProviderId provider, TestRunId testRunId, String scenarioId) {
    return find(provider, testRunId, scenarioId).map(stored::remove).orElse(false);
  }

  @Override
  public <T> T decidingOneRequestAtATimeAbout(
      ProviderId provider, TestRunId testRunId, List<StoryKey> keys, Supplier<T> decision) {
    List<ReentrantLock> taken = new ArrayList<>();
    for (StoryKey key : keys) {
      OneFlow one = new OneFlow(provider, testRunId, key);
      ReentrantLock held;
      synchronized (locks) {
        held = locks.computeIfAbsent(one, flow -> new ReentrantLock());
      }
      held.lock();
      taken.add(held);
    }
    try {
      return decision.get();
    } finally {
      taken.reversed().forEach(ReentrantLock::unlock);
    }
  }

  @Override
  public SequencedMap<String, String> storyStatesOf(
      ProviderId provider, TestRunId testRunId, StoryKey key) {
    return Collections.unmodifiableSequencedMap(
        new LinkedHashMap<>(
            new TreeMap<>(states.getOrDefault(new OneFlow(provider, testRunId, key), Map.of()))));
  }

  @Override
  public boolean moveStory(
      ProviderId provider,
      TestRunId testRunId,
      StoryKey key,
      String story,
      String from,
      String to) {
    if (everyMoveIsLostWithoutTheStoryMoving) {
      return false;
    }
    Map<String, String> held =
        states.computeIfAbsent(
            new OneFlow(provider, testRunId, key), flow -> new LinkedHashMap<>());
    if (!from.equals(ScenarioStory.stateOrInitial(held.get(story)))) {
      return false;
    }
    held.put(story, to);
    return true;
  }

  @Override
  public int clear(ProviderId provider, TestRunId testRunId) {
    states
        .keySet()
        .removeIf(key -> key.provider().equals(provider) && key.testRunId().equals(testRunId));
    List<ScenarioRule> doomed = of(provider, testRunId);
    stored.removeAll(doomed);
    return doomed.size();
  }

  @Override
  public int clearAll(ProviderId provider) {
    states.keySet().removeIf(key -> key.provider().equals(provider));
    List<ScenarioRule> doomed =
        stored.stream().filter(rule -> rule.provider().equals(provider)).toList();
    stored.removeAll(doomed);
    return doomed.size();
  }
}
