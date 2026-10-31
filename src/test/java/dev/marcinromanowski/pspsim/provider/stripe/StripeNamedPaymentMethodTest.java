package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StripeNamedPaymentMethodTest {

  private static final Instant AT = Instant.parse("2026-10-31T09:00:00Z");

  private static final StripePaymentMethod A_CARD = aCard("pm_held", null, null);

  private static final StripePaymentMethod A_DETACHED_CARD = aCard("pm_detached", null, AT);

  private static final StripePaymentMethod A_WALLET =
      new StripePaymentMethod(
          TestRunId.of("run"),
          "pm_wallet",
          "merchant-1",
          null,
          StripePaymentMethodType.PAYPAL,
          null,
          null,
          ObjectOrigin.CONTROL_API,
          AT,
          AT);

  @Test
  void only_a_held_method_that_was_detached_reads_as_detached() {
    assertThat(new StripeNamedPaymentMethod.Held(A_DETACHED_CARD).detached()).isTrue();
    assertThat(new StripeNamedPaymentMethod.Held(A_CARD).detached()).isFalse();
    assertThat(new StripeNamedPaymentMethod.Minted(A_CARD).detached()).isFalse();
    assertThat(new StripeNamedPaymentMethod.Unknown("pm_x").detached()).isFalse();
    assertThat(new StripeNamedPaymentMethod.NoneNamed().detached()).isFalse();
  }

  @Test
  void a_held_or_minted_method_has_its_type_and_an_unknown_or_absent_one_has_none() {
    assertThat(new StripeNamedPaymentMethod.Held(A_WALLET).type())
        .contains(StripePaymentMethodType.PAYPAL);
    assertThat(new StripeNamedPaymentMethod.Minted(A_CARD).type())
        .contains(StripePaymentMethodType.CARD);
    assertThat(new StripeNamedPaymentMethod.Unknown("pm_x").type()).isEqualTo(Optional.empty());
    assertThat(new StripeNamedPaymentMethod.NoneNamed().type()).isEqualTo(Optional.empty());
  }

  @Test
  void each_kind_answers_its_own_id_its_method_and_only_a_minted_one_is_minted() {
    StripeNamedPaymentMethod held = new StripeNamedPaymentMethod.Held(A_CARD);
    StripeNamedPaymentMethod minted = new StripeNamedPaymentMethod.Minted(A_DETACHED_CARD);
    StripeNamedPaymentMethod unknown = new StripeNamedPaymentMethod.Unknown("pm_x");
    StripeNamedPaymentMethod none = new StripeNamedPaymentMethod.NoneNamed();

    assertThat(held.id()).isEqualTo("pm_held");
    assertThat(minted.id()).isEqualTo("pm_detached");
    assertThat(unknown.id()).isEqualTo("pm_x");
    assertThat(none.id()).isNull();
    assertThat(held.method()).isEqualTo(A_CARD);
    assertThat(unknown.method()).isNull();
    assertThat(none.method()).isNull();
    assertThat(minted.minted()).isEqualTo(A_DETACHED_CARD);
    assertThat(held.minted()).isNull();
    assertThat(unknown.minted()).isNull();
    assertThat(unknown.theIdARuleNames()).isEqualTo("pm_x");
    assertThat(none.theIdARuleNames()).isNull();
    assertThat(
            new StripeNamedPaymentMethod.Minted(aCard("pm_sim_1", "pm_card_visa", null))
                .theIdARuleNames())
        .isEqualTo("pm_card_visa");
    assertThat(held.theIdARuleNames()).isEqualTo("pm_held");
  }

  @Test
  void only_an_unknown_id_outside_the_published_stems_is_no_such_method() {
    assertThat(new StripeNamedPaymentMethod.Unknown("pm_123").theRefusalOfAMethodNoOneHolds())
        .isNotNull();
    assertThat(
            new StripeNamedPaymentMethod.Unknown("pm_card_uncatalogued")
                .theRefusalOfAMethodNoOneHolds())
        .isNull();
    assertThat(new StripeNamedPaymentMethod.Held(A_CARD).theRefusalOfAMethodNoOneHolds()).isNull();
    assertThat(new StripeNamedPaymentMethod.NoneNamed().theRefusalOfAMethodNoOneHolds()).isNull();
  }

  @Test
  void only_an_unknown_id_that_reads_as_a_card_number_is_refused_as_one() {
    assertThat(
            new StripeNamedPaymentMethod.Unknown("4242424242424242")
                .theRefusalOfAnIdThatReadsAsACardNumber())
        .isNotNull();
    assertThat(
            new StripeNamedPaymentMethod.Unknown("pm_x").theRefusalOfAnIdThatReadsAsACardNumber())
        .isNull();
    assertThat(new StripeNamedPaymentMethod.Held(A_CARD).theRefusalOfAnIdThatReadsAsACardNumber())
        .isNull();
  }

  private static StripePaymentMethod aCard(
      String id,
      @org.jspecify.annotations.Nullable String copiedFrom,
      @org.jspecify.annotations.Nullable Instant detachedAt) {
    StripePaymentMethod card =
        new StripePaymentMethod(
            TestRunId.of("run"),
            id,
            "merchant-1",
            null,
            StripePaymentMethodType.CARD,
            new FixtureCard("visa", "4242", 12, 2030, "credit"),
            null,
            ObjectOrigin.CONTROL_API,
            AT,
            AT);
    StripePaymentMethod copied =
        new StripePaymentMethod(
            card.testRunId(),
            card.paymentMethodId(),
            card.accountId(),
            null,
            card.type(),
            card.card(),
            card.billingDetails(),
            card.metadata(),
            card.allowRedisplay(),
            copiedFrom,
            null,
            card.origin(),
            AT,
            AT);
    return detachedAt == null ? copied : copied.detachedAt(detachedAt);
  }
}
