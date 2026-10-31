package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripePaymentMethodMintingTest {

  private final StripeCardWorld world = new StripeCardWorld();
  private final StripePaymentMethodMinting minting =
      new StripePaymentMethodMinting(world.paymentMethods, world.ids);

  @Test
  void a_minted_id_is_the_prefix_and_twenty_hexadecimal_characters() {
    assertThat(StripePaymentMethodMinting.aPaymentMethodId(new StripeDoubles.CountingIds()))
        .isEqualTo("pm_sim_00000000000000000001");
  }

  @Test
  void an_id_this_run_already_holds_is_passed_over_for_the_next_one() {
    world.seedAFixture(StripeCardWorld.ACCOUNT, "pm_sim_00000000000000000001", null);

    StripePaymentMethod minted = aCopy();

    assertThat(minted.paymentMethodId()).isEqualTo("pm_sim_00000000000000000002");
  }

  @Test
  void minting_gives_up_once_every_id_it_may_draw_is_held() {
    for (int held = 1; held <= StripePaymentMethodMinting.ID_ATTEMPTS; held++) {
      world.seedAFixture(StripeCardWorld.ACCOUNT, String.format("pm_sim_%020x", held), null);
    }

    assertThatThrownBy(this::aCopy)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("no Stripe PaymentMethod id could be drawn in 5 attempts");
  }

  @Test
  void a_minted_method_another_request_stored_first_is_not_stored_twice() {
    StripePaymentMethod minted = aCopy();
    world.paymentMethods.insertIfAbsent(minted);

    assertThatThrownBy(() -> minting.stored(minted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(minted.paymentMethodId());
    assertThat(world.theMethodsStored()).hasSize(1);
  }

  @Test
  void a_method_described_by_a_request_carries_what_the_request_described() {
    StripeCard card =
        StripeTestCards.theCardOf(StripeTestCards.byToken("tok_visa").orElseThrow(), 10, 2028);
    StripeBillingDetails billing =
        new StripeBillingDetails(null, "PL", null, null, null, null, null, "Jan", null, null);
    StripeNewPaymentMethod.Description description =
        new StripeNewPaymentMethod.Description(card, billing, Map.of("order", "o-1"), "limited");

    StripePaymentMethod described =
        minting.described(
            StripeCardWorld.RUN,
            world.accounts.bySecretKey(StripeCardWorld.ANOTHER_SECRET_KEY).get(0),
            description,
            ObjectOrigin.UI,
            StripeCardWorld.NOW);

    assertThat(described.paymentMethodId()).isEqualTo("pm_sim_00000000000000000001");
    assertThat(described.testRunId()).isEqualTo(StripeCardWorld.RUN);
    assertThat(described.accountId()).isEqualTo(StripeCardWorld.ANOTHER_ACCOUNT);
    assertThat(described.type()).isEqualTo(StripePaymentMethodType.CARD);
    assertThat(described.card()).isEqualTo(card);
    assertThat(described.billingDetails()).isEqualTo(billing);
    assertThat(described.metadata()).containsExactly(Map.entry("order", "o-1"));
    assertThat(described.allowRedisplay()).isEqualTo("limited");
    assertThat(described.copiedFrom()).isNull();
    assertThat(described.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(described.customerId()).isNull();
    assertThat(described.detachedAt()).isNull();
    assertThat(described.createdAt()).isEqualTo(StripeCardWorld.NOW);
    assertThat(described.updatedAt()).isEqualTo(StripeCardWorld.NOW);
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_copy_carries_the_card_of_the_test_id_and_names_the_test_id_it_was_copied_from() {
    StripePaymentMethod copy = aCopy();

    assertThat(copy.copiedFrom()).isEqualTo("pm_card_us");
    assertThat(copy.theIdARuleNames()).isEqualTo("pm_card_us");
    assertThat(copy.accountId()).isEqualTo(StripeCardWorld.ACCOUNT);
    assertThat(copy.card())
        .isEqualTo(
            new StripeCard(
                "visa",
                "4242",
                10,
                2027,
                "credit",
                "US",
                "visa",
                List.of("visa"),
                "unregulated",
                true));
    assertThat(copy.billingDetails()).isEqualTo(StripeBillingDetails.NONE);
    assertThat(copy.metadata()).isEmpty();
    assertThat(copy.allowRedisplay()).isEqualTo("unspecified");
    assertThat(copy.customerId()).isNull();
  }

  private StripePaymentMethod aCopy() {
    return minting.clonedFrom(
        "pm_card_us",
        StripeTestCards.byPaymentMethodId("pm_card_us").orElseThrow(),
        StripeCardWorld.RUN,
        world.accounts.bySecretKey(StripeCardWorld.SECRET_KEY).get(0),
        ObjectOrigin.PROVIDER_API,
        StripeCardWorld.NOW);
  }
}
