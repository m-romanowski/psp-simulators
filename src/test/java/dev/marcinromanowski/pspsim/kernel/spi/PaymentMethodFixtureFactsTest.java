package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaymentMethodFixtureFactsTest {

  private static final ProviderId PROVIDER = ProviderId.of("twokeys");

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final Instant AT = Instant.parse("2026-09-10T12:00:00Z");

  @Test
  void the_trail_names_the_fixture_a_seed_created_and_what_a_tester_called_it() {
    Map<String, List<String>> recorded = PaymentMethodFixtureFacts.of(aVisaCard(null));

    assertThat(recorded)
        .containsEntry("fixtureId", List.of("fx_1"))
        .containsEntry("alias", List.of("visa-success"))
        .containsEntry("type", List.of("card"))
        .containsEntry("merchantId", List.of("merchant-1"))
        .containsEntry("ownerId", List.of("customer-123"));
  }

  @Test
  void the_trail_names_the_provider_native_ids_a_fixture_carries_and_never_their_values() {
    Map<String, List<String>> recorded = PaymentMethodFixtureFacts.of(aVisaCard(null));

    assertThat(recorded)
        .containsEntry(
            "providerIds",
            List.of(ProviderPaymentMethodFields.CUSTOMER_ID, ProviderPaymentMethodFields.TOKEN));
    assertThat(String.valueOf(recorded))
        .doesNotContain("tok_sim_123")
        .doesNotContain("cus_sim_123");
  }

  @Test
  void
      the_trail_carries_the_card_descriptors_a_fixture_holds_and_there_is_no_card_number_to_hold() {
    Map<String, List<String>> recorded = PaymentMethodFixtureFacts.of(aVisaCard(null));

    assertThat(recorded)
        .containsEntry("card.brand", List.of("visa"))
        .containsEntry("card.last4", List.of("1111"))
        .containsEntry("card.funding", List.of("credit"));
  }

  @Test
  void a_fixture_that_names_a_scenario_says_so_in_the_trail_and_one_that_does_not_stays_silent() {
    assertThat(PaymentMethodFixtureFacts.of(aVisaCard("scn_1")))
        .containsEntry("defaultScenario", List.of("scn_1"));
    assertThat(PaymentMethodFixtureFacts.of(aVisaCard(null))).doesNotContainKey("defaultScenario");
  }

  @Test
  void a_wallet_leaves_no_card_descriptor_in_the_trail_rather_than_an_empty_one() {
    Map<String, List<String>> recorded =
        PaymentMethodFixtureFacts.of(
            new PaymentMethodFixture(
                "fx_2",
                PROVIDER,
                RUN,
                "merchant-1",
                "customer-123",
                PaymentMethodKind.WALLET,
                "a-wallet",
                null,
                Map.of(),
                null,
                AT));

    assertThat(recorded)
        .doesNotContainKey("card.brand")
        .doesNotContainKey("card.last4")
        .doesNotContainKey("card.funding")
        .doesNotContainKey("providerIds")
        .containsEntry("type", List.of("wallet"));
  }

  private static PaymentMethodFixture aVisaCard(@Nullable String defaultScenario) {
    return new PaymentMethodFixture(
        "fx_1",
        PROVIDER,
        RUN,
        "merchant-1",
        "customer-123",
        PaymentMethodKind.CARD,
        "visa-success",
        new FixtureCard("visa", "1111", 12, 2030, "credit"),
        Map.of(
            ProviderPaymentMethodFields.TOKEN,
            "tok_sim_123",
            ProviderPaymentMethodFields.CUSTOMER_ID,
            "cus_sim_123"),
        defaultScenario,
        AT);
  }
}
