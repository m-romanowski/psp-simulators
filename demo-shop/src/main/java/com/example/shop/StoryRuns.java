package com.example.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StoryRuns {

  private final Map<String, StoryRun> kept = Collections.synchronizedMap(new LinkedHashMap<>());

  void keep(StoryRun run) {
    kept.put(run.testRunId(), run);
  }

  public StoryRun called(String testRunId) {
    return keptAs(testRunId).orElseThrow(() -> new NoSuchStoryRun(testRunId));
  }

  public Optional<StoryRun> keptAs(String testRunId) {
    return Optional.ofNullable(kept.get(testRunId));
  }

  public List<StoryRun> newestFirst() {
    List<StoryRun> all;
    synchronized (kept) {
      all = new ArrayList<>(kept.values());
    }
    Collections.reverse(all);
    return all;
  }

  boolean stillKeeps(StoryRun run) {
    return kept.get(run.testRunId()) == run;
  }

  void forget(StoryRun run) {
    kept.remove(run.testRunId());
  }
}
