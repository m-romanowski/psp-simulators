package dev.marcinromanowski.pspsim.kernel.settings.internal;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcTestRunSettings implements TestRunSettings {

  private record StoredSetting(String key, String value) {}

  private final JdbcClient jdbc;
  private final SimulatorClock clock;

  public JdbcTestRunSettings(JdbcClient jdbc, SimulatorClock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public void merge(ProviderId provider, TestRunId testRunId, Map<SettingKey, String> values) {
    OffsetDateTime updatedAt = clock.now(testRunId).atOffset(ZoneOffset.UTC);
    for (Map.Entry<SettingKey, String> value : values.entrySet()) {
      jdbc.sql(
              """
              insert into simulator_provider_setting
                  (provider, test_run_id, setting_key, setting_value, updated_at)
              values (?, ?, ?, ?, ?)
              on conflict (provider, test_run_id, setting_key) do update
                  set setting_value = excluded.setting_value,
                      updated_at = excluded.updated_at
              """)
          .param(provider.value())
          .param(testRunId.value())
          .param(value.getKey().key())
          .param(value.getValue())
          .param(updatedAt)
          .update();
    }
  }

  @Override
  public Map<SettingKey, String> of(ProviderId provider, TestRunId testRunId) {
    List<StoredSetting> stored =
        jdbc.sql(
                """
                select setting_key, setting_value from simulator_provider_setting
                where provider = ? and test_run_id = ?
                order by setting_key
                """)
            .params(provider.value(), testRunId.value())
            .query(
                (rs, rowNumber) ->
                    new StoredSetting(rs.getString("setting_key"), rs.getString("setting_value")))
            .list();
    Map<SettingKey, String> values = new EnumMap<>(SettingKey.class);
    for (StoredSetting setting : stored) {
      Optional<SettingKey> known = SettingKey.named(setting.key());
      if (known.isPresent()) {
        values.put(known.get(), setting.value());
      }
    }
    return Map.copyOf(values);
  }
}
