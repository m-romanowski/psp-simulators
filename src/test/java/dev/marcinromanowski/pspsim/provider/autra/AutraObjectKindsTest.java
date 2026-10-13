package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AutraObjectKindsTest {

  private static final TestRunId RUN = TestRunId.of("autra-object-kinds");

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  @Test
  void a_payment_whose_operation_list_holds_a_reservation_reads_as_a_hold() {
    AutraPayment held = paymentAt(AutraStatus.ACCEPTED, AutraFields.CREDIT, aReservation());

    assertThat(AutraObjectKinds.of(held)).isEqualTo(AutraObjectKinds.A_HOLD);
    assertThat(AutraObjectKinds.of(held).asThisProviderNamesIt()).isEqualTo("a hold on a card");
  }

  @Test
  void a_reservation_autra_has_since_captured_still_reads_as_a_hold_and_not_as_a_sale() {
    AutraPayment captured =
        paymentAt(
            AutraStatus.CAPTURED,
            AutraFields.CREDIT,
            aReservation()
                .with(
                    new MonetaryOperation(
                        2,
                        OperationKind.CAPTURE,
                        Amount.of("500.00"),
                        Boolean.TRUE,
                        null,
                        AutraStatus.CAPTURED,
                        NormalizedStatus.SUCCEEDED,
                        NOW)));

    assertThat(AutraObjectKinds.of(captured)).isEqualTo(AutraObjectKinds.A_HOLD);
  }

  @Test
  void a_sale_autra_accepted_reads_as_a_sale_although_its_operation_list_holds_nothing() {
    AutraPayment accepted = paymentAt(AutraStatus.ACCEPTED, AutraFields.CREDIT, Ledger.empty());

    assertThat(AutraObjectKinds.of(accepted)).isEqualTo(AutraObjectKinds.A_SALE);
    assertThat(AutraObjectKinds.of(accepted).asThisProviderNamesIt())
        .isEqualTo("a sale on a stored card");
  }

  @Test
  void a_sale_autra_has_since_reversed_reads_as_a_sale_from_the_money_it_took() {
    AutraPayment reversed =
        paymentAt(
            AutraStatus.VOIDED,
            AutraFields.CREDIT,
            aCapture()
                .with(
                    new MonetaryOperation(
                        2,
                        OperationKind.REFUND,
                        Amount.of("500.00"),
                        null,
                        null,
                        AutraStatus.VOIDED,
                        NormalizedStatus.REFUNDED,
                        NOW)));

    assertThat(AutraObjectKinds.of(reversed)).isEqualTo(AutraObjectKinds.A_SALE);
  }

  @Test
  void a_declined_payment_on_a_card_product_only_a_sale_accepts_reads_as_a_sale() {
    AutraPayment declined = paymentAt(AutraStatus.DECLINED, AutraFields.DEBIT, Ledger.empty());

    assertThat(AutraObjectKinds.of(declined)).isEqualTo(AutraObjectKinds.A_SALE);
  }

  @Test
  void a_declined_credit_payment_says_autra_keeps_nothing_that_tells_a_hold_from_a_sale() {
    AutraPayment declined = paymentAt(AutraStatus.DECLINED, AutraFields.CREDIT, Ledger.empty());

    ProviderObjectKind kind = AutraObjectKinds.of(declined);

    assertThat(kind).isEqualTo(AutraObjectKinds.A_CARD_PAYMENT_AUTRA_DECLINED);
    assertThat(kind.asThisProviderNamesIt()).isEqualTo("a card payment");
    assertThat(kind.whatItIsInItsOwnWords())
        .contains("keeps nothing that says which call asked for it")
        .contains("transactionType DEBIT is a sale");
  }

  @Test
  void a_pix_and_a_hosted_card_page_are_named_apart_from_each_other_and_from_every_payment() {
    assertThat(AutraObjectKinds.ofAPix()).isEqualTo(AutraObjectKinds.A_PIX);
    assertThat(AutraObjectKinds.ofAPix().asThisProviderNamesIt()).isEqualTo("a Pix");
    assertThat(AutraObjectKinds.ofACardSession()).isEqualTo(AutraObjectKinds.A_CARD_SESSION);
    assertThat(AutraObjectKinds.ofACardSession().asThisProviderNamesIt())
        .isEqualTo("a hosted card page");
    assertThat(AutraObjectKinds.ofAPix()).isNotEqualTo(AutraObjectKinds.ofACardSession());
  }

  @Test
  void what_a_pix_is_says_what_one_of_them_is_and_never_what_state_this_one_is_in() {
    String whatOneIs = AutraObjectKinds.ofAPix().whatItIsInItsOwnWords();

    assertThat(whatOneIs)
        .as(
            "a screen prints this sentence under a Pix in every state a Pix can be in, so a"
                + " sentence that reports the state of the one in front of a reader is a falsehood"
                + " on the first one that expired or settled")
        .startsWith("A Pix is ")
        .doesNotContain("waiting for its payer");
    assertThat(whatOneIs)
        .as("autra publishes this in Portuguese, and a tester reading the screen reads English")
        .contains("\"Sem endpoint público de status\"")
        .contains("no public status endpoint");
  }

  private static Ledger aReservation() {
    return Ledger.empty()
        .with(
            new MonetaryOperation(
                1,
                OperationKind.AUTHORIZATION,
                Amount.of("500.00"),
                Boolean.FALSE,
                null,
                AutraStatus.ACCEPTED,
                NormalizedStatus.READY_FOR_CAPTURE,
                NOW));
  }

  private static Ledger aCapture() {
    return Ledger.empty()
        .with(
            new MonetaryOperation(
                1,
                OperationKind.CAPTURE,
                Amount.of("500.00"),
                Boolean.TRUE,
                null,
                AutraStatus.ACCEPTED,
                NormalizedStatus.SUCCEEDED,
                NOW));
  }

  private static AutraPayment paymentAt(
      String nativeStatus, String transactionType, Ledger ledger) {
    return paymentAt(theTypeOf(ledger), nativeStatus, transactionType, ledger);
  }

  private static AutraPayment paymentAt(
      String type, String nativeStatus, String transactionType, Ledger ledger) {
    return new AutraPayment(
        RUN,
        "550e8400-e29b-41d4-a716-446655440000",
        "merchant-1",
        "25055662000190",
        null,
        type,
        transactionType,
        Amount.of("500.00"),
        "BRL",
        1,
        nativeStatus,
        "A1B2C3",
        "112676199769",
        "900123456789",
        null,
        null,
        ledger,
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static String theTypeOf(Ledger ledger) {
    return ledger.holds(OperationKind.AUTHORIZATION) ? AutraTimeline.PREAUTH : AutraTimeline.SALE;
  }
}
