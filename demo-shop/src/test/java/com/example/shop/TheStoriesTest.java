package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TheStoriesTest {

  private static final long PATIENCE_NANOS = 10_000_000_000L;

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00.750Z");

  private final AnInMemorySimulator simulator = new AnInMemorySimulator();
  private final StoryRuns runs = new StoryRuns();
  private final ShopSettings settings =
      new ShopSettings(
          "http://simulator:8080/",
          "http://localhost:8080",
          "http://shop:8081/",
          Duration.ofSeconds(2),
          Duration.ofSeconds(10));
  private final Stories stories =
      new Stories(simulator, runs, settings, Clock.fixed(NOW, ZoneOffset.UTC));
  private final Checkout checkout = new Checkout(simulator, runs);

  {
    simulator.deliversWebhooksTo(new StripeWebhooks(runs));
  }

  @Test
  @DisplayName("every play opens a test run of its own, keyed by a secret no other run holds")
  void everyPlayOpensATestRunOfItsOwn() {
    StoryRun first = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    StoryRun second = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    assertThat(first.testRunId()).isNotEqualTo(second.testRunId());
    assertThat(simulator.opened()).containsOnlyKeys(first.testRunId(), second.testRunId());
    assertThat(simulator.opened().get(first.testRunId()).secretKey())
        .startsWith("sk_test_")
        .isNotEqualTo(simulator.opened().get(second.testRunId()).secretKey());
    assertThat(simulator.opened().get(first.testRunId()).webhookSecret()).startsWith("whsec_");
  }

  @Test
  @DisplayName("a run's secrets are drawn at random, so its run id alone does not give them away")
  void aRunsSecretsAreNotDerivedFromItsRunId() {
    StoryRun one = new StoryRun("shop-same-id", Story.A_CAPTURE_THAT_GOES_THROUGH, NOW);
    StoryRun again = new StoryRun("shop-same-id", Story.A_CAPTURE_THAT_GOES_THROUGH, NOW);

    assertThat(one.secretKey()).isNotEqualTo(again.secretKey()).doesNotContain("shopsameid");
    assertThat(one.webhookSecret())
        .isNotEqualTo(again.webhookSecret())
        .doesNotContain("shopsameid");
    assertThat(one.secretKey().substring("sk_test_".length())).hasSizeGreaterThanOrEqualTo(32);
    assertThat(one.webhookSecret().substring("whsec_".length())).hasSizeGreaterThanOrEqualTo(32);
  }

  @Test
  @DisplayName("a run's webhooks are aimed at the shop's own endpoint for that run")
  void aRunsWebhooksAreAimedAtTheShop() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    assertThat(simulator.opened().get(run.testRunId()).callbackUrl())
        .isEqualTo("http://shop:8081/webhooks/stripe/" + run.testRunId());
  }

  @Test
  @DisplayName("a run's clock is pinned to the second the story was played")
  void aRunsClockIsPinnedToTheSecondTheStoryWasPlayed() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    assertThat(simulator.opened().get(run.testRunId()).clock()).isEqualTo("2026-09-15T10:00:00Z");
  }

  @Test
  @DisplayName("the card checkout pays with is seeded into the run before anyone shops")
  void theCardCheckoutPaysWithIsSeeded() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    assertThat(simulator.savedCards()).containsEntry(run.testRunId(), run.savedCard());
  }

  @Test
  @DisplayName("the decline story writes a rule refusing every capture of its run as expired_card")
  void theDeclineStoryWritesTheRule() {
    StoryRun run = stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR);

    assertThat(simulator.captureRefusals()).containsEntry(run.testRunId(), "expired_card");
    assertThat(run.captureRefusalRule()).contains("scn_" + run.testRunId());
  }

  @Test
  @DisplayName("the happy story writes no rule at all")
  void theHappyStoryWritesNoRule() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    assertThat(simulator.captureRefusals()).doesNotContainKey(run.testRunId());
    assertThat(run.captureRefusalRule()).isEmpty();
  }

  @Test
  @DisplayName("the decline story ends with the capture refused for the reason the story named")
  void theDeclineStoryEndsRefused() {
    StoryRun run = stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR);

    checkout.buy(run.testRunId());
    Order order = checkout.capture(run.testRunId());

    assertThat(order.status()).isEqualTo(OrderStatus.CAPTURE_REFUSED);
    assertThat(order.refusal()).contains("expired_card");
    assertThat(simulator.captured()).isEmpty();
    assertThat(run.ended()).isTrue();
  }

  @Test
  @DisplayName(
      "the happy story ends captured when Stripe's signed webhook arrives, not when it is asked for")
  void theHappyStoryEndsCapturedWithTheWebhookReceived() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    Order order = checkout.buy(run.testRunId());
    checkout.capture(run.testRunId());
    assertThat(order.status()).isEqualTo(OrderStatus.CAPTURED);
    assertThat(run.ended()).isFalse();

    stories.stripeReportsTheFinishedPayment(run.testRunId());

    assertThat(simulator.sent())
        .containsExactly(
            new AnInMemorySimulator.SentEvent(
                run.testRunId(), order.paymentIntentId(), "payment_intent.succeeded"));
    assertThat(run.received()).isEmpty();
    assertThat(run.awaitsStripe()).isTrue();
    assertThat(run.ended()).isFalse();

    simulator.deliverWhatIsDue();

    assertThat(run.awaitsStripe()).isFalse();
    assertThat(run.received())
        .extracting(ReceivedEvent::type, ReceivedEvent::objectId)
        .containsExactly(tuple("payment_intent.succeeded", order.paymentIntentId()));
    assertThat(run.ended()).isTrue();
  }

  @Test
  @DisplayName(
      "Stripe is asked once per run to report the finished payment, however often it is pressed")
  void stripeIsAskedOncePerRunToReportTheFinishedPayment() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    checkout.buy(run.testRunId());
    checkout.capture(run.testRunId());

    stories.stripeReportsTheFinishedPayment(run.testRunId());
    stories.stripeReportsTheFinishedPayment(run.testRunId());
    simulator.deliverWhatIsDue();
    stories.stripeReportsTheFinishedPayment(run.testRunId());
    simulator.deliverWhatIsDue();

    assertThat(simulator.sent()).hasSize(1);
    assertThat(run.received()).hasSize(1);
  }

  @Test
  @DisplayName("a report the simulator refused to send can be asked for again")
  void aRefusedReportCanBeAskedForAgain() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    checkout.buy(run.testRunId());
    checkout.capture(run.testRunId());
    simulator.refusesTheNextEvent();

    assertThatThrownBy(() -> stories.stripeReportsTheFinishedPayment(run.testRunId()))
        .isInstanceOf(TheSimulatorRefused.class);
    assertThat(run.stripeAskedToReport()).isFalse();

    stories.stripeReportsTheFinishedPayment(run.testRunId());

    assertThat(simulator.sent()).hasSize(1);
    assertThat(run.stripeAskedToReport()).isTrue();
  }

  @Test
  @DisplayName("Stripe is not asked to report a payment the shop has not captured")
  void stripeIsNotAskedToReportAnUncapturedPayment() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    checkout.buy(run.testRunId());

    assertThatThrownBy(() -> stories.stripeReportsTheFinishedPayment(run.testRunId()))
        .isInstanceOf(NotAtThatStepYet.class);
    assertThat(simulator.sent()).isEmpty();
  }

  @Test
  @DisplayName("pressing Send twice at the same moment in one run asks the simulator to send once")
  void reportingTwiceAtOnceInOneRunSendsOnce() throws InterruptedException {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    checkout.buy(run.testRunId());
    checkout.capture(run.testRunId());

    twoAtOnce(
        () -> {
          stories.stripeReportsTheFinishedPayment(run.testRunId());
          return run.testRunId();
        });

    assertThat(simulator.sent()).hasSize(1);
    assertThat(run.stripeAskedToReport()).isTrue();
  }

  @Test
  @DisplayName("capturing before anything was bought is refused as too early")
  void capturingBeforeBuyingIsTooEarly() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    assertThatThrownBy(() -> checkout.capture(run.testRunId()))
        .isInstanceOf(NotAtThatStepYet.class);
    assertThat(simulator.captured()).isEmpty();
  }

  @Test
  @DisplayName("paying twice in one run places one hold")
  void payingTwicePlacesOneHold() {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    Order first = checkout.buy(run.testRunId());
    Order second = checkout.buy(run.testRunId());

    assertThat(second).isSameAs(first);
    assertThat(simulator.holds()).hasSize(1);
  }

  @Test
  @DisplayName("paying twice at the same moment in one run places one hold")
  void payingTwiceAtOnceInOneRunPlacesOneHold() throws InterruptedException {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);

    List<Order> orders = twoAtOnce(() -> checkout.buy(run.testRunId()));

    assertThat(simulator.holds()).hasSize(1);
    assertThat(orders).hasSize(2);
    assertThat(orders.get(0)).isSameAs(orders.get(1));
  }

  @Test
  @DisplayName("capturing twice at the same moment in one run asks Stripe to capture once")
  void capturingTwiceAtOnceInOneRunAsksStripeOnce() throws InterruptedException {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    checkout.buy(run.testRunId());

    List<Order> orders = twoAtOnce(() -> checkout.capture(run.testRunId()));

    assertThat(simulator.capturesAsked()).isEqualTo(1);
    assertThat(orders).extracting(Order::status).containsOnly(OrderStatus.CAPTURED);
  }

  @Test
  @DisplayName("capturing an order Stripe has already answered for asks Stripe nothing more")
  void capturingAnAnsweredOrderAsksNothing() {
    StoryRun run = stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR);
    checkout.buy(run.testRunId());
    checkout.capture(run.testRunId());
    simulator.captureRefusals().clear();

    Order order = checkout.capture(run.testRunId());

    assertThat(order.status()).isEqualTo(OrderStatus.CAPTURE_REFUSED);
    assertThat(simulator.captured()).isEmpty();
  }

  @Test
  @DisplayName("a run is kept after its story ends, so it can be read in the simulator")
  void aRunIsKeptAfterItsStoryEnds() {
    StoryRun run = stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR);
    checkout.buy(run.testRunId());
    checkout.capture(run.testRunId());

    assertThat(runs.newestFirst()).containsExactly(run);
    assertThat(simulator.resets()).isEmpty();
    assertThat(simulator.removedAccounts()).isEmpty();
  }

  @Test
  @DisplayName("tidying up resets every kept run, removes its account, and forgets it")
  void tidyingUpRemovesEveryKeptRun() {
    StoryRun first = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    StoryRun second = stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR);

    TidyUpOutcome outcome = stories.tidyUp();

    assertThat(outcome.tidied()).containsExactly(second.testRunId(), first.testRunId());
    assertThat(outcome.leftBehind()).isEmpty();
    assertThat(simulator.resets()).containsExactlyInAnyOrder(first.testRunId(), second.testRunId());
    assertThat(simulator.removedAccounts())
        .containsExactlyInAnyOrder(
            new AnInMemorySimulator.RemovedAccount(first.testRunId(), StoryRun.ACCOUNT),
            new AnInMemorySimulator.RemovedAccount(second.testRunId(), StoryRun.ACCOUNT));
    assertThat(runs.newestFirst()).isEmpty();
    assertThatThrownBy(() -> runs.called(first.testRunId())).isInstanceOf(NoSuchStoryRun.class);
  }

  @Test
  @DisplayName(
      "a run whose account the simulator would not remove is named and stays kept, the runs before"
          + " and after it are tidied, and the next tidy-up tries it again")
  void aRunWhoseAccountWasNotRemovedDoesNotStopTheOthers() {
    StoryRun older = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    StoryRun stuck = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    StoryRun newer = stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR);
    simulator.refusesToRemoveTheAccountOf(stuck.testRunId());

    TidyUpOutcome outcome = stories.tidyUp();

    assertThat(outcome.tidied()).containsExactly(newer.testRunId(), older.testRunId());
    assertThat(outcome.leftBehind())
        .singleElement()
        .satisfies(
            left -> {
              assertThat(left.testRunId()).isEqualTo(stuck.testRunId());
              assertThat(left.why()).contains("HTTP 404");
            });
    assertThat(runs.newestFirst()).containsExactly(stuck);

    simulator.agreesToRemoveEveryAccount();

    assertThat(stories.tidyUp().tidied()).containsExactly(stuck.testRunId());
    assertThat(runs.newestFirst()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(AnInMemorySimulator.SetUpStep.class)
  @DisplayName(
      "a run whose setup failed at any step is not kept, and what that step and the ones before it"
          + " wrote is taken out of the simulator again")
  void aRunWhoseSetUpFailedIsNotKeptAndWhatItWroteIsTakenOut(AnInMemorySimulator.SetUpStep step) {
    simulator.writesButDoesNotAnswer(step);

    assertThatThrownBy(() -> stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR))
        .isInstanceOf(TheSimulatorDidNotAnswer.class)
        .hasMessageContaining(step.toString());

    assertThat(runs.newestFirst()).isEmpty();
    assertThat(simulator.resets()).hasSize(1);
    assertThat(simulator.removedAccounts()).hasSize(1);
    assertThat(simulator.opened()).isEmpty();
    assertThat(simulator.savedCards()).isEmpty();
    assertThat(simulator.captureRefusals()).isEmpty();
    assertThat(stories.tidyUp().tidied()).isEmpty();
  }

  @Test
  @DisplayName(
      "a run whose setup failed is not kept even when the simulator will not reset it, its account"
          + " is removed all the same, and the failure reported is the setup's own")
  void aFailedSetUpWhoseResetIsRefusedStillLosesItsAccount() {
    simulator.writesButDoesNotAnswer(AnInMemorySimulator.SetUpStep.SEEDING_THE_SAVED_CARD);
    simulator.refusesEveryReset();

    assertThatThrownBy(() -> stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH))
        .isInstanceOf(TheSimulatorDidNotAnswer.class)
        .hasMessageContaining("SEEDING_THE_SAVED_CARD")
        .satisfies(
            failed ->
                assertThat(failed.getSuppressed())
                    .singleElement()
                    .isInstanceOf(TheSimulatorRefused.class));

    assertThat(runs.newestFirst()).isEmpty();
    assertThat(simulator.opened()).isEmpty();
  }

  @Test
  @DisplayName(
      "a run whose setup failed and could not be taken out again either is not kept, and the"
          + " failure reported is the setup's own, carrying both refusals")
  void aFailedSetUpThatCouldNotBeTakenOutReportsItsOwnFailure() {
    simulator.writesButDoesNotAnswer(AnInMemorySimulator.SetUpStep.WRITING_THE_CAPTURE_RULE);
    simulator.refusesEveryReset();
    simulator.refusesToRemoveAnyAccount();

    assertThatThrownBy(() -> stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR))
        .isInstanceOf(TheSimulatorDidNotAnswer.class)
        .hasMessageContaining("WRITING_THE_CAPTURE_RULE")
        .satisfies(
            failed ->
                assertThat(failed.getSuppressed())
                    .hasSize(2)
                    .allSatisfy(
                        refusal -> assertThat(refusal).isInstanceOf(TheSimulatorRefused.class)));

    assertThat(runs.newestFirst()).isEmpty();
  }

  @Test
  @DisplayName("tidying up while a story is still being set up leaves that run's setup alone")
  void tidyingUpDuringASetUpLeavesItAlone() throws InterruptedException {
    AnInMemorySimulator.Pause pause = simulator.pauseInsideTheProvider();
    List<StoryRun> played = new CopyOnWriteArrayList<>();
    Thread playing =
        Thread.ofPlatform()
            .start(() -> played.add(stories.play(Story.A_DECLINE_THE_TEST_ASKED_FOR)));
    pause.awaitTheFirstEntrant();
    List<TidyUpOutcome> outcomes = new CopyOnWriteArrayList<>();
    Thread tidying = Thread.ofPlatform().start(() -> outcomes.add(stories.tidyUp()));
    long deadline = System.nanoTime() + PATIENCE_NANOS;
    while (tidying.getState() != Thread.State.BLOCKED
        && tidying.isAlive()
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    List<String> resetDuringTheSetUp = simulator.resets();
    List<AnInMemorySimulator.RemovedAccount> removedDuringTheSetUp = simulator.removedAccounts();
    pause.release();
    playing.join(Duration.ofSeconds(10));
    tidying.join(Duration.ofSeconds(10));

    assertThat(resetDuringTheSetUp).isEmpty();
    assertThat(removedDuringTheSetUp).isEmpty();
    StoryRun run = played.getFirst();
    assertThat(runs.newestFirst()).containsExactly(run);
    assertThat(simulator.opened()).containsOnlyKeys(run.testRunId());
    assertThat(simulator.savedCards()).containsEntry(run.testRunId(), run.savedCard());
    assertThat(simulator.captureRefusals()).containsEntry(run.testRunId(), "expired_card");
  }

  @Test
  @DisplayName("tidying up a run waits for a capture of that run already under way")
  void tidyingUpARunWaitsForItsCaptureUnderWay() throws InterruptedException {
    StoryRun run = stories.play(Story.A_CAPTURE_THAT_GOES_THROUGH);
    checkout.buy(run.testRunId());
    AnInMemorySimulator.Pause pause = simulator.pauseInsideTheProvider();
    Thread capturing = Thread.ofPlatform().start(() -> checkout.capture(run.testRunId()));
    pause.awaitTheFirstEntrant();
    List<TidyUpOutcome> outcomes = new CopyOnWriteArrayList<>();
    Thread tidying = Thread.ofPlatform().start(() -> outcomes.add(stories.tidyUp()));
    long deadline = System.nanoTime() + PATIENCE_NANOS;
    while (tidying.getState() != Thread.State.BLOCKED
        && tidying.isAlive()
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    boolean tidyingWaited = tidying.getState() == Thread.State.BLOCKED;
    List<String> resetWhileTheCaptureWasUnderWay = simulator.resets();
    pause.release();
    capturing.join(Duration.ofSeconds(10));
    tidying.join(Duration.ofSeconds(10));

    assertThat(tidyingWaited).isTrue();
    assertThat(resetWhileTheCaptureWasUnderWay).isEmpty();
    assertThat(run.order().orElseThrow().status()).isEqualTo(OrderStatus.CAPTURED);
    assertThat(outcomes)
        .singleElement()
        .extracting(TidyUpOutcome::tidied)
        .isEqualTo(List.of(run.testRunId()));
  }

  @Test
  @DisplayName("a story the shop does not tell is refused by name")
  void aStoryTheShopDoesNotTellIsRefused() {
    assertThatThrownBy(() -> Story.calledBy("a-refund")).isInstanceOf(NoSuchStory.class);
  }

  private <T> List<T> twoAtOnce(Supplier<T> step) throws InterruptedException {
    List<T> answered = new CopyOnWriteArrayList<>();
    List<Throwable> thrown = new CopyOnWriteArrayList<>();
    Runnable asking =
        () -> {
          try {
            answered.add(step.get());
          } catch (RuntimeException | Error failed) {
            thrown.add(failed);
          }
        };
    AnInMemorySimulator.Pause pause = simulator.pauseInsideTheProvider();
    Thread first = Thread.ofPlatform().start(asking);
    pause.awaitTheFirstEntrant();
    Thread second = Thread.ofPlatform().start(asking);
    long deadline = System.nanoTime() + PATIENCE_NANOS;
    while (pause.entrants() < 2
        && second.getState() != Thread.State.BLOCKED
        && second.isAlive()
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    boolean bothWereUnderway = pause.entrants() == 2 || second.getState() == Thread.State.BLOCKED;
    pause.release();
    first.join(Duration.ofSeconds(10));
    second.join(Duration.ofSeconds(10));
    assertThat(bothWereUnderway).isTrue();
    assertThat(thrown).isEmpty();
    return List.copyOf(answered);
  }
}
