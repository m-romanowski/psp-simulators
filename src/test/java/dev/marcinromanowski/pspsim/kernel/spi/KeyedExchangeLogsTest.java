package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

class KeyedExchangeLogsTest {

  private static final FingerprintKey KEY =
      new FingerprintKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");

  private static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          ProviderId.of("grow"),
          List.of(
              new DeclaredKey(
                  "order",
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  "every order",
                  TestKeyClaims.NEVER_HELD)),
          Map.of(
              KeyRole.TRUE_IDEMPOTENCY_KEY, "none",
              KeyRole.CORRELATION_KEY, "none"),
          answered -> answered);

  private final SequencedRows sequenced = new SequencedRows();

  private final KeyedExchangeLogs logs =
      new KeyedExchangeLogs(
          JdbcClient.create(new DriverManagerDataSource()),
          testRunId -> KEY,
          sequenced,
          JsonMapper.builder().build());

  @Test
  void a_log_made_for_a_provider_fingerprints_under_the_key_of_the_run() {
    KeyedExchangeLog log =
        logs.of(MECHANISMS, KeyedExchangeTable.underANamedKey("x_exchange", "account_id", false));

    assertThat(
            log.fingerprintOf(
                TestRunId.of("a-run"),
                new FingerprintedRequest("POST", "/v1/charges", Map.of("amount", List.of("1099")))))
        .isEqualTo("6e1c0ef8fca94b66fa10790a1aa72ea39462fc5b58671a92286432005af98f67");
  }

  @Test
  void every_table_of_a_build_is_numbered_by_the_one_retry_the_logs_hold() {
    assertThat(logs.sequenced()).isSameAs(sequenced);
  }
}
