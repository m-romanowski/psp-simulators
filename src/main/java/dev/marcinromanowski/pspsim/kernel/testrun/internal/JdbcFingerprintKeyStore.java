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
            select fingerprint_key from simulator_fingerprint_key
            where test_run_id = ?
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
                insert into simulator_fingerprint_key (test_run_id, fingerprint_key)
                values (?, ?)
                on conflict (test_run_id) do update
                    set fingerprint_key = simulator_fingerprint_key.fingerprint_key
                returning fingerprint_key
                """)
            .params(testRunId.value(), minted.secret())
            .query(String.class)
            .single());
  }
}
