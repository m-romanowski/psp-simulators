package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GrowHostedCompletionOfferTest {

  private static final TestRunId RUN = TestRunId.of("grow-hosted-completion-offer");
  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String NOTIFY = "https://merchant.example/callbacks/grow";

  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();

  @Test
  void a_process_no_payer_has_finished_offers_the_payer_step_and_the_cards_of_this_run() {
    HostedCompletionOffer offer =
        GrowHostedCompletionOffer.of(awaitingItsPayer(), GrowPayerCards.sandboxCards());

    assertThat(offer).isInstanceOf(HostedCompletionOffer.Offered.class);
    assertThat(((HostedCompletionOffer.Offered) offer).cardsAPayerMayName())
        .extracting(PayerCard::alias)
        .containsExactly("sandbox-4580", "sandbox-0000", "sandbox-1121");
  }

  @Test
  void a_card_is_offered_by_what_a_payer_would_see_on_it_and_never_by_a_card_number() {
    HostedCompletionOffer.Offered offered =
        (HostedCompletionOffer.Offered)
            GrowHostedCompletionOffer.of(awaitingItsPayer(), GrowPayerCards.sandboxCards());

    assertThat(offered.cardsAPayerMayName().get(0).label()).isEqualTo("Visa •••• 4580, 0424");
    assertThat(offered.theCardsItOffers()).doesNotContain("4580458045804580");
  }

  @Test
  void a_process_a_payer_has_already_finished_offers_no_second_payer_step() {
    GrowPaymentProcess finished =
        awaitingItsPayer()
            .reaching(
                GrowProcessOutcome.abandoned(),
                GrowNativeStatus.NOT_PAID_CODE,
                GrowNativeStatus.NOT_PAID,
                NormalizedStatus.NOT_INITIALIZED,
                NOW);

    HostedCompletionOffer offer =
        GrowHostedCompletionOffer.of(finished, GrowPayerCards.sandboxCards());

    assertThat(offer)
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                GrowLightApi.GROW, CompleteHostedProcess.ALREADY_COMPLETED));
  }

  @Test
  void an_object_that_is_no_process_of_this_run_is_told_grow_leaves_a_payer_step_on_nothing_else() {
    HostedCompletionOffer offer = GrowHostedCompletionOffer.of(null, GrowPayerCards.sandboxCards());

    assertThat(offer)
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                GrowLightApi.GROW, GrowHostedCompletionOffer.ONLY_A_PROCESS_HAS_A_PAYER_STEP));
  }

  @Test
  void the_offer_a_screen_reads_for_a_process_carries_the_cards_this_run_has_seeded() {
    GrowPaymentProcess process = stored();
    InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
    fixtures.seed(
        new NewPaymentMethodFixture(
            GrowLightApi.GROW,
            RUN,
            "merchant-1",
            "owner-1",
            PaymentMethodKind.CARD,
            "the-declining-one",
            new FixtureCard("Mastercard", "0002", 12, 2029, "Local"),
            Map.of(),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());

    HostedCompletionOffer offer = completions(fixtures).offeredOn(RUN, process.processId());

    assertThat(((HostedCompletionOffer.Offered) offer).cardsAPayerMayName())
        .extracting(PayerCard::alias)
        .contains("the-declining-one")
        .contains("sandbox-4580");
  }

  @Test
  void a_process_the_screen_asks_about_and_this_run_does_not_hold_is_offered_no_payer_step() {
    HostedCompletionOffer offer =
        completions(new InMemoryPaymentMethodFixtures()).offeredOn(RUN, "000000");

    assertThat(offer)
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                GrowLightApi.GROW, GrowHostedCompletionOffer.ONLY_A_PROCESS_HAS_A_PAYER_STEP));
  }

  @Test
  void the_cards_the_screen_offers_are_the_cards_the_completion_itself_resolves() {
    GrowPaymentProcess process = stored();
    InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();

    HostedCompletionOffer.Offered offered =
        (HostedCompletionOffer.Offered) completions(fixtures).offeredOn(RUN, process.processId());

    for (PayerCard card : offered.cardsAPayerMayName()) {
      assertThat(new GrowPayerCards(fixtures).resolve(RUN, card.alias())).isPresent();
    }
  }

  private CompleteHostedProcess completions(InMemoryPaymentMethodFixtures fixtures) {
    SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
    SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
    SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
    SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
    return new CompleteHostedProcess(
        processes,
        SimulatorDoubles.callbacks(
            processes,
            transactions,
            accounts,
            SimulatorDoubles.idleDeliveries(audit, ids, NOW),
            SimulatorDoubles.clockAt(NOW),
            ids),
        new GrowPayerCards(fixtures));
  }

  private GrowPaymentProcess stored() {
    GrowPaymentProcess process = awaitingItsPayer();
    assertThat(processes.insertIfAbsent(process)).isTrue();
    return process;
  }

  private static GrowPaymentProcess awaitingItsPayer() {
    return new GrowPaymentProcess(
        RUN,
        "734754",
        "token-734754",
        "merchant-1",
        GrowFields.CHARGE_TYPE_SUSPENDED,
        "120.50",
        "A hold",
        false,
        "https://merchant.example/thanks",
        "https://merchant.example/cancel",
        NOTIFY,
        "Israel Israeli",
        "0505503144",
        "payer@merchant.example",
        Map.of(),
        "link-734754",
        GrowNativeStatus.NOT_PAID_CODE,
        GrowNativeStatus.NOT_PAID,
        NormalizedStatus.NOT_INITIALIZED,
        null,
        ObjectOrigin.UI,
        NOW,
        NOW);
  }
}
