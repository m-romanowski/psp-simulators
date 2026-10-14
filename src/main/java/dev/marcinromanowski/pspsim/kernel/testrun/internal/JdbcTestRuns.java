package dev.marcinromanowski.pspsim.kernel.testrun.internal;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcTestRuns implements TestRuns {

  private final JdbcClient jdbc;
  private final TestRunId defaultTestRun;

  public JdbcTestRuns(JdbcClient jdbc, TestRunId defaultTestRun) {
    this.jdbc = jdbc;
    this.defaultTestRun = defaultTestRun;
  }

  @Override
  public void register(TestRunId testRunId) {
    jdbc.sql(
            """
            insert into simulator_test_run (test_run_id)
            values (?)
            on conflict (test_run_id) do nothing
            """)
        .param(TestRunId.storableNamespace(testRunId).value())
        .update();
  }

  @Override
  public int forget(TestRunId testRunId) {
    jdbc.sql("delete from simulator_fingerprint_key where test_run_id = ?")
        .param(TestRunId.storableNamespace(testRunId).value())
        .update();
    return jdbc.sql("delete from simulator_test_run where test_run_id = ?")
        .param(TestRunId.storableNamespace(testRunId).value())
        .update();
  }

  @Override
  public boolean exists(TestRunId testRunId) {
    return defaultTestRun.equals(testRunId)
        || jdbc.sql("select 1 from simulator_test_run where test_run_id = ?")
            .param(testRunId.value())
            .query(Integer.class)
            .optional()
            .isPresent();
  }

  @Override
  public List<TestRunId> all() {
    List<TestRunId> registered =
        jdbc.sql("select test_run_id from simulator_test_run order by test_run_id")
            .query((rs, row) -> TestRunId.of(rs.getString("test_run_id")))
            .list();
    Set<TestRunId> named = new LinkedHashSet<>();
    named.add(defaultTestRun);
    named.addAll(registered);
    return List.copyOf(named);
  }
}
