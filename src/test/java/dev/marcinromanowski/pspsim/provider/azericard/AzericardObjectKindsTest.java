package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AzericardObjectKindsTest {

  private static final TestRunId RUN = TestRunId.of("azericard-object-kinds");

  private static final Instant AT = Instant.parse("2026-09-07T12:00:00Z");

  @Test
  void a_payment_azericard_opened_on_trtype_zero_reads_as_a_hold() {
    assertThat(AzericardObjectKinds.of(aPaymentOf(AzericardTransactionType.PRE_AUTHORIZATION)))
        .isEqualTo(AzericardObjectKinds.A_HOLD);
    assertThat(
            AzericardObjectKinds.of(aPaymentOf(AzericardTransactionType.PRE_AUTHORIZATION))
                .asThisProviderNamesIt())
        .isEqualTo("a hold on a card");
  }

  @Test
  void a_payment_azericard_opened_on_trtype_one_reads_as_a_card_charged_in_one_call() {
    assertThat(AzericardObjectKinds.of(aPaymentOf(AzericardTransactionType.AUTHORIZATION)))
        .isEqualTo(AzericardObjectKinds.A_CHARGE);
    assertThat(
            AzericardObjectKinds.of(aPaymentOf(AzericardTransactionType.AUTHORIZATION))
                .asThisProviderNamesIt())
        .isEqualTo("a card charged in one call");
  }

  @Test
  void a_saved_card_and_a_hosted_payer_page_are_named_apart_from_each_other_and_from_a_payment() {
    assertThat(AzericardObjectKinds.ofASavedCard()).isEqualTo(AzericardObjectKinds.A_SAVED_CARD);
    assertThat(AzericardObjectKinds.ofASavedCard().asThisProviderNamesIt())
        .isEqualTo("a saved card");
    assertThat(AzericardObjectKinds.ofAPayerPage()).isEqualTo(AzericardObjectKinds.A_PAYER_PAGE);
    assertThat(AzericardObjectKinds.ofAPayerPage().asThisProviderNamesIt())
        .isEqualTo("a hosted payer page");
    assertThat(AzericardObjectKinds.ofASavedCard())
        .isNotEqualTo(AzericardObjectKinds.ofAPayerPage());
  }

  private static AzericardPayment aPaymentOf(AzericardTransactionType transactionType) {
    return new AzericardPayment(
        RUN,
        "INTREF0000000001",
        "terminal-1",
        "merchant-1",
        "20220628084800",
        transactionType,
        Amount.of("120.50"),
        "AZN",
        "111111",
        "112676199769",
        transactionType.wireValue(),
        "00",
        Ledger.empty(),
        null,
        null,
        null,
        null,
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        AT,
        AT);
  }
}
