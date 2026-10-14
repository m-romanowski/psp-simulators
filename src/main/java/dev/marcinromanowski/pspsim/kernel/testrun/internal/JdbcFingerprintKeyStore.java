package dev.marcinromanowski.pspsim.kernel.testrun.internal;

import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKey;
import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKeyStore;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcFingerprintKeyStore implements FingerprintKeyStore {

  private final JdbcClient jdbc;

  public JdbcFingerprintKeyStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<FingerprintKey> held(TestRunId testRunId) {
    return jdbc.sql(
            """
            select fingerprint_key from simulator_test_run
            where test_run_id = ? and fingerprint_key is not null
            """)
        .param(testRunId.value())
        .query(String.class)
        .optional()
        .map(FingerprintKey::new);
  }

  @Override
  public FingerprintKey keptOr(TestRunId testRunId, FingerprintKey minted) {
    return new FingerprintKey(
        jdbc.sql(
                """
                insert into simulator_test_run (test_run_id, fingerprint_key)
                values (?, ?)
                on conflict (test_run_id) do update
                    set fingerprint_key =
                        coalesce(simulator_test_run.fingerprint_key, excluded.fingerprint_key)
                returning fingerprint_key
                """)
            .params(testRunId.value(), minted.secret())
            .query(String.class)
            .single());
  }
}
