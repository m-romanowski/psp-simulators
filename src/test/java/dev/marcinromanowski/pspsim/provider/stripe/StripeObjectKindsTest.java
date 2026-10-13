package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StripeObjectKindsTest {

  private static final TestRunId RUN = TestRunId.of("stripe-object-kinds");

  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

  @Test
  void an_intent_stripe_captures_only_on_a_later_call_reads_as_a_hold() {
    ProviderObjectKind kind = StripeObjectKinds.of(anIntentOf(StripeCaptureMethod.MANUAL));

    assertThat(kind).isEqualTo(StripeObjectKinds.A_HOLD);
    assertThat(kind.asThisProviderNamesIt()).isEqualTo("a hold on a card");
  }

  @ParameterizedTest
  @EnumSource(
      value = StripeCaptureMethod.class,
      names = {"AUTOMATIC", "AUTOMATIC_ASYNC"})
  void an_intent_stripe_takes_the_funds_on_reads_as_a_card_charged_in_one_call(
      StripeCaptureMethod method) {
    ProviderObjectKind kind = StripeObjectKinds.of(anIntentOf(method));

    assertThat(kind).isEqualTo(StripeObjectKinds.A_CHARGE);
    assertThat(kind.asThisProviderNamesIt()).isEqualTo("a card charged in one call");
  }

  @Test
  void the_four_objects_stripe_lists_beside_a_payment_intent_each_read_as_a_different_thing() {
    assertThat(StripeObjectKinds.ofARefund().asThisProviderNamesIt()).isEqualTo("a refund");
    assertThat(StripeObjectKinds.ofACustomer().asThisProviderNamesIt()).isEqualTo("a customer");
    assertThat(StripeObjectKinds.ofASavedCard().asThisProviderNamesIt()).isEqualTo("a saved card");
    assertThat(StripeObjectKinds.ofACardBeingSetUp().asThisProviderNamesIt())
        .isEqualTo("a card being set up for later");
    assertThat(StripeObjectKinds.ofARefund()).isEqualTo(StripeObjectKinds.A_REFUND);
    assertThat(StripeObjectKinds.ofACustomer()).isEqualTo(StripeObjectKinds.A_CUSTOMER);
    assertThat(StripeObjectKinds.ofASavedCard()).isEqualTo(StripeObjectKinds.A_SAVED_CARD);
    assertThat(StripeObjectKinds.ofACardBeingSetUp())
        .isEqualTo(StripeObjectKinds.A_CARD_BEING_SET_UP);
  }

  @Test
  void the_object_that_moves_no_money_ever_says_so_where_a_reader_would_expect_a_payment() {
    assertThat(StripeObjectKinds.ofACardBeingSetUp().whatItIsInItsOwnWords())
        .contains("moves no money ever");
  }

  private static StripePaymentIntent anIntentOf(StripeCaptureMethod method) {
    return new StripePaymentIntent(
        RUN,
        "pi_simulated_1",
        "account-1",
        Amount.of("1000"),
        "pln",
        method,
        StripeIntentStatus.REQUIRES_CONFIRMATION,
        null,
        null,
        null,
        "pi_simulated_1_secret",
        null,
        null,
        null,
        NOW.plus(Duration.ofDays(7)),
        null,
        null,
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }
}
