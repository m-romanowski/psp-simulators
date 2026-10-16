package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcTranzilaNonces implements TranzilaNonces {

  private final JdbcClient jdbc;

  JdbcTranzilaNonces(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int claim(
      TestRunId testRunId, String accountId, String nonce, Instant now, Instant seenBefore) {
    return jdbc.sql(
            """
            insert into tranzila_nonce (test_run_id, account_id, nonce, seen_at)
            values (?, ?, ?, ?)
            on conflict (test_run_id, account_id, nonce) do update set seen_at = excluded.seen_at
            where tranzila_nonce.seen_at < ?
            """)
        .param(testRunId.value())
        .param(accountId)
        .param(nonce)
        .param(now.atOffset(ZoneOffset.UTC))
        .param(seenBefore.atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from tranzila_nonce where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from tranzila_nonce").update();
  }
}
