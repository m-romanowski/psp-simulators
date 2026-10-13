package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrowObjectKindsTest {

  private static final TestRunId RUN = TestRunId.of("grow-object-kinds");

  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

  @Test
  void a_transaction_a_payer_finished_a_page_for_is_named_apart_from_one_charged_from_a_token() {
    assertThat(GrowObjectKinds.of(aTransactionOf(GrowTransactionKind.PAYMENT)))
        .isEqualTo(GrowObjectKinds.A_PAYMENT_A_PAYER_FINISHED);
    assertThat(
            GrowObjectKinds.of(aTransactionOf(GrowTransactionKind.PAYMENT)).asThisProviderNamesIt())
        .isEqualTo("a payment a payer finished");
  }

  @Test
  void a_transaction_grow_charged_from_a_saved_token_says_no_payer_and_no_page_was_in_it() {
    assertThat(GrowObjectKinds.of(aTransactionOf(GrowTransactionKind.TOKEN_PAYMENT)))
        .isEqualTo(GrowObjectKinds.A_CHARGE_ON_A_SAVED_CARD);
    assertThat(
            GrowObjectKinds.of(aTransactionOf(GrowTransactionKind.TOKEN_PAYMENT))
                .whatItIsInItsOwnWords())
        .contains("no payer and no hosted page");
  }

  @Test
  void a_refund_grow_lists_as_a_transaction_of_its_own_reads_as_a_refund() {
    assertThat(GrowObjectKinds.of(aTransactionOf(GrowTransactionKind.REFUND)))
        .isEqualTo(GrowObjectKinds.A_REFUND);
    assertThat(
            GrowObjectKinds.of(aTransactionOf(GrowTransactionKind.REFUND)).asThisProviderNamesIt())
        .isEqualTo("a refund");
  }

  @Test
  void a_page_that_holds_the_money_is_named_apart_from_a_page_that_only_saves_the_card() {
    assertThat(GrowObjectKinds.of(aProcessOf(GrowFields.CHARGE_TYPE_SUSPENDED)))
        .isEqualTo(GrowObjectKinds.A_PAGE_THAT_HOLDS_MONEY);
    assertThat(GrowObjectKinds.of(aProcessOf(GrowFields.CHARGE_TYPE_TOKEN_ONLY)))
        .isEqualTo(GrowObjectKinds.A_PAGE_THAT_SAVES_A_CARD);
    assertThat(
            GrowObjectKinds.of(aProcessOf(GrowFields.CHARGE_TYPE_TOKEN_ONLY))
                .asThisProviderNamesIt())
        .isEqualTo("a hosted page that saves a card");
    assertThat(
            GrowObjectKinds.of(aProcessOf(GrowFields.CHARGE_TYPE_SUSPENDED))
                .asThisProviderNamesIt())
        .isEqualTo("a hosted page that holds money");
  }

  private static GrowTransaction aTransactionOf(GrowTransactionKind kind) {
    return new GrowTransaction(
        RUN,
        kind,
        "800001",
        "token",
        "merchant-1",
        "100001",
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

  private static GrowPaymentProcess aProcessOf(String chargeType) {
    return new GrowPaymentProcess(
        RUN,
        "100001",
        "token-100001",
        "merchant-1",
        chargeType,
        "120.50",
        "A hold",
        true,
        "https://merchant.example/thanks",
        "https://merchant.example/cancel",
        "https://merchant.example/hook",
        "Israel Israeli",
        "0505503144",
        "payer@merchant.example",
        Map.of("cField1", "session-abc"),
        "link-100001",
        GrowNativeStatus.NOT_PAID_CODE,
        GrowNativeStatus.NOT_PAID,
        NormalizedStatus.NOT_INITIALIZED,
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }
}
