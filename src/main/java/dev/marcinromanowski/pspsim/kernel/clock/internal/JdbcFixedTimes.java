package dev.marcinromanowski.pspsim.kernel.clock.internal;

import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcFixedTimes implements FixedTimes {

  private final JdbcClient jdbc;

  public JdbcFixedTimes(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Instant> of(TestRunId testRunId) {
    return jdbc.sql(
            """
            select fixed_now from simulator_test_run
            where test_run_id = ? and fixed_now is not null
            """)
        .param(testRunId.value())
        .query(OffsetDateTime.class)
        .optional()
        .map(OffsetDateTime::toInstant);
  }

  @Override
  public void pin(TestRunId testRunId, Instant fixedNow) {
    jdbc.sql(
            """
            insert into simulator_test_run (test_run_id, fixed_now)
            values (?, ?)
            on conflict (test_run_id) do update set fixed_now = excluded.fixed_now
            """)
        .params(TestRunId.storableNamespace(testRunId).value(), fixedNow.atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public void release(TestRunId testRunId) {
    jdbc.sql("update simulator_test_run set fixed_now = null where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }
}
