package dev.marcinromanowski.pspsim.kernel.settings;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InMemoryTestRunSettings implements TestRunSettings {

  private record OneRun(ProviderId provider, TestRunId testRunId) {}

  private final Map<OneRun, Map<SettingKey, String>> stored = new LinkedHashMap<>();

  @Override
  public void merge(ProviderId provider, TestRunId testRunId, Map<SettingKey, String> values) {
    stored
        .computeIfAbsent(new OneRun(provider, testRunId), _ -> new LinkedHashMap<>())
        .putAll(values);
  }

  @Override
  public Map<SettingKey, String> of(ProviderId provider, TestRunId testRunId) {
    return Map.copyOf(stored.getOrDefault(new OneRun(provider, testRunId), Map.of()));
  }
}
