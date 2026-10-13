package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GrowStoryKeyTest {

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

  @Test
  void a_call_about_a_transaction_belongs_to_the_story_of_the_process_that_produced_it() {
    assertThat(GrowStoryKey.ofTransaction(transaction("900001", "100001")))
        .isEqualTo(GrowStoryKey.ofProcess("100001"));
  }

  @Test
  void a_transaction_that_belongs_to_no_process_is_a_flow_of_its_own() {
    assertThat(GrowStoryKey.ofTransaction(transaction("900001", null)))
        .isNotEqualTo(GrowStoryKey.ofProcess("900001"));
  }

  @Test
  void two_payments_whose_ids_happen_to_be_equal_are_never_one_flow() {
    assertThat(GrowStoryKey.ofTransaction(transaction("100001", null)))
        .isNotEqualTo(GrowStoryKey.ofProcess("100001"));
  }

  @Test
  void two_transactions_of_one_process_are_one_flow_and_two_processes_are_two() {
    assertThat(GrowStoryKey.ofTransaction(transaction("900001", "100001")))
        .isEqualTo(GrowStoryKey.ofTransaction(transaction("900002", "100001")));
    assertThat(GrowStoryKey.ofProcess("100001")).isNotEqualTo(GrowStoryKey.ofProcess("100002"));
  }

  @Test
  void each_of_the_two_spaces_grow_draws_six_digits_in_is_named_in_the_key_it_makes() {
    assertThat(new StoryKey(StoryKeying.FLOW, GrowStoryKey.ofProcess("100001")).value())
        .isEqualTo("process" + StoryKey.NAMES_THE_SPACE + "100001");
    assertThat(new StoryKey(StoryKeying.FLOW, GrowStoryKey.ofTransactionId("100001")).value())
        .isEqualTo("transaction" + StoryKey.NAMES_THE_SPACE + "100001");
  }

  private static GrowTransaction transaction(String transactionId, @Nullable String processId) {
    return new GrowTransaction(
        RUN,
        GrowTransactionKind.PAYMENT,
        transactionId,
        "token",
        "merchant-1",
        processId,
        null,
        "89474280",
        "07/09/26",
        "A hold",
        "Israel Israeli",
        "0505503144",
        null,
        GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD,
        Amount.of("120.50"),
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }
}
