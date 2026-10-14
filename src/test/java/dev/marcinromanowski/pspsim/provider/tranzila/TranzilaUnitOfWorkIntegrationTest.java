package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class TranzilaUnitOfWorkIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-unit-of-work-it");

  private static final Instant AT = Instant.parse("2026-10-13T12:00:00Z");

  @Autowired private TranzilaTransactions transactions;
  @Autowired private TranzilaExchanges exchanges;
  @Autowired private JdbcClient jdbc;

  @AfterEach
  void clearTheRun() {
    transactions.clear(RUN);
    exchanges.clear(RUN);
  }

  @Test
  void a_transaction_and_its_exchange_written_together_are_both_undone_when_the_work_fails() {
    assertThatThrownBy(
            () ->
                transactions.together(
                    () -> {
                      transactions.insert(aDebit());
                      exchanges.observed(anExchange());
                      throw new IllegalStateException("the exchange could not be recorded");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(count("tranzila_transaction")).isZero();
    assertThat(count("tranzila_keyed_exchange")).isZero();
  }

  @Test
  void a_transaction_and_its_exchange_written_together_both_stay_when_the_work_succeeds() {
    transactions.together(
        () -> {
          transactions.insert(aDebit());
          exchanges.observed(anExchange());
          return aDebit();
        });

    assertThat(count("tranzila_transaction")).isEqualTo(1);
    assertThat(count("tranzila_keyed_exchange")).isEqualTo(1);
  }

  private int count(String table) {
    return jdbc.sql("select count(*) from " + table + " where test_run_id = ?")
        .param(RUN.value())
        .query(Integer.class)
        .single();
  }

  private static TranzilaTransaction aDebit() {
    return new TranzilaTransaction(
        RUN,
        7001L,
        7001L,
        "account-1",
        "myterminal",
        TranzilaTxnType.DEBIT,
        null,
        null,
        "000",
        "10",
        "ILS",
        "1000001",
        "t000000000000014245",
        new TranzilaCard("458021xxxxxx4245", 2, 7, 30),
        "0",
        "0",
        "order-1",
        null,
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static KeyedObservation anExchange() {
    return new KeyedObservation(
        RUN,
        "account-1",
        TranzilaDcDisableKey.keyNameOf(TranzilaTxnType.DEBIT, null),
        "order-1",
        "debit",
        KeyedOutcome.RAN_THE_OPERATION,
        new FingerprintedRequest("POST", "debit", Map.of()),
        new KeyedAnswer(200, Map.of()),
        "7001",
        true,
        null,
        AT);
  }
}
