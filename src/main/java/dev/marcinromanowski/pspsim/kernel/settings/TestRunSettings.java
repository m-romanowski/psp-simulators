package dev.marcinromanowski.pspsim.kernel.settings;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Map;

public interface TestRunSettings {

  void merge(ProviderId provider, TestRunId testRunId, Map<SettingKey, String> values);

  Map<SettingKey, String> of(ProviderId provider, TestRunId testRunId);
}
