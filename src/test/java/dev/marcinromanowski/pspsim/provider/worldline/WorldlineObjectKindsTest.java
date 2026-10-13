package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldlineObjectKindsTest {

  private static final TestRunId RUN = TestRunId.of("worldline-object-kinds");

  private static final Instant WHEN = Instant.parse("2026-09-07T12:00:00Z");

  @Test
  void the_mode_that_takes_a_partial_capture_reads_as_a_hold_a_reader_can_capture_in_part() {
    assertThat(WorldlineObjectKinds.of(aPaymentIn(WorldlineAuthorizationMode.PRE_AUTHORIZATION)))
        .isEqualTo(WorldlineObjectKinds.A_HOLD);
    assertThat(
            WorldlineObjectKinds.of(aPaymentIn(WorldlineAuthorizationMode.PRE_AUTHORIZATION))
                .asThisProviderNamesIt())
        .isEqualTo("a hold on a card");
  }

  @Test
  void the_mode_that_takes_only_a_full_capture_is_named_apart_from_the_one_that_takes_a_part() {
    assertThat(WorldlineObjectKinds.of(aPaymentIn(WorldlineAuthorizationMode.FINAL_AUTHORIZATION)))
        .isEqualTo(WorldlineObjectKinds.A_HOLD_TAKEN_ONLY_IN_FULL);
    assertThat(
            WorldlineObjectKinds.of(aPaymentIn(WorldlineAuthorizationMode.FINAL_AUTHORIZATION))
                .asThisProviderNamesIt())
        .isEqualTo("a hold captured only in full");
  }

  @Test
  void the_mode_that_blocks_and_captures_in_one_go_reads_as_a_card_charged_in_one_call() {
    assertThat(WorldlineObjectKinds.of(aPaymentIn(WorldlineAuthorizationMode.SALE)))
        .isEqualTo(WorldlineObjectKinds.A_CHARGE);
    assertThat(
            WorldlineObjectKinds.of(aPaymentIn(WorldlineAuthorizationMode.SALE))
                .asThisProviderNamesIt())
        .isEqualTo("a card charged in one call");
  }

  @Test
  void the_hosted_tokenization_page_is_named_apart_from_every_payment_of_this_provider() {
    assertThat(WorldlineObjectKinds.ofATokenization())
        .isEqualTo(WorldlineObjectKinds.A_TOKENIZATION);
    assertThat(WorldlineObjectKinds.ofATokenization().asThisProviderNamesIt())
        .isEqualTo("a hosted card page");
    assertThat(WorldlineObjectKinds.ofATokenization())
        .isNotEqualTo(WorldlineObjectKinds.of(aPaymentIn(WorldlineAuthorizationMode.SALE)));
  }

  private static WorldlinePayment aPaymentIn(WorldlineAuthorizationMode mode) {
    return new WorldlinePayment(
        RUN,
        "3066019730_0",
        List.of(),
        "account-1",
        "merchant-1",
        null,
        null,
        "token-1",
        null,
        "************1111",
        "1230",
        WorldlineAmounts.asAnAmount(5000L),
        "PLN",
        mode,
        WorldlineStatus.PENDING_CAPTURE,
        WorldlineStatus.CATEGORY_PENDING_MERCHANT,
        WorldlineStatus.CODE_PENDING_CAPTURE,
        false,
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        WHEN,
        WHEN);
  }
}
