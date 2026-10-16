package dev.marcinromanowski.pspsim.kernel.testrun;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class FingerprintKeyStoreIntegrationTest extends PostgresBackedTest {

  private static final TestRunId NEVER_REGISTERED = TestRunId.of("a-run-only-a-key-names");

  private static final TestRunId REGISTERED = TestRunId.of("a-run-a-tester-registered");

  @Autowired private FingerprintKeys keys;
  @Autowired private TestRuns runs;
  @Autowired private JdbcClient jdbc;

  @AfterEach
  void forgetBothRuns() {
    runs.forget(NEVER_REGISTERED);
    runs.forget(REGISTERED);
  }

  @Test
  void minting_a_key_registers_no_test_run() {
    List<TestRunId> before = runs.all();

    FingerprintKey minted = keys.of(NEVER_REGISTERED);

    assertThat(runs.all()).isEqualTo(before);
    assertThat(runs.exists(NEVER_REGISTERED)).isFalse();
    assertThat(keys.of(NEVER_REGISTERED)).isEqualTo(minted);
    assertThat(keyRowsOf(NEVER_REGISTERED)).isEqualTo(1);
  }

  @Test
  void forgetting_a_run_removes_its_key_and_counts_only_the_registration() {
    runs.register(REGISTERED);
    FingerprintKey minted = keys.of(REGISTERED);

    int forgotten = runs.forget(REGISTERED);

    assertThat(forgotten).isEqualTo(1);
    assertThat(keyRowsOf(REGISTERED)).isZero();
    assertThat(keys.of(REGISTERED)).isNotEqualTo(minted);
  }

  private int keyRowsOf(TestRunId testRunId) {
    return jdbc.sql("select count(*) from simulator_fingerprint_key where test_run_id = ?")
        .param(testRunId.value())
        .query(Integer.class)
        .single();
  }
}
