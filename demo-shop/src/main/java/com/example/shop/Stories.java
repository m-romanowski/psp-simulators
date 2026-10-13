package com.example.shop;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class Stories {

  static final String THE_EVENT_OF_A_FINISHED_PAYMENT = "payment_intent.succeeded";

  private final SimulatorControl simulator;
  private final StoryRuns runs;
  private final ShopSettings settings;
  private final Clock clock;

  public Stories(SimulatorControl simulator, StoryRuns runs, ShopSettings settings, Clock clock) {
    this.simulator = simulator;
    this.runs = runs;
    this.settings = settings;
    this.clock = clock;
  }

  public StoryRun play(Story story) {
    StoryRun run =
        new StoryRun(
            "shop-" + UUID.randomUUID().toString().substring(0, 13),
            story,
            clock.instant().truncatedTo(ChronoUnit.SECONDS));
    try {
      simulator.openTheRun(run, settings.whereStripeCallsBackFor(run.testRunId()));
      simulator.seedTheSavedCard(run);
      story
          .theReasonTheCaptureIsRefusedFor()
          .ifPresent(
              code ->
                  run.captureRefusalRuleWritten(
                      simulator.refuseEveryCapture(run.testRunId(), code)));
    } catch (RuntimeException setUpFailed) {
      undoWhatTheSetUpWrote(run, setUpFailed);
      throw setUpFailed;
    }
    runs.keep(run);
    return run;
  }

  private void undoWhatTheSetUpWrote(StoryRun run, RuntimeException setUpFailed) {
    try {
      simulator.reset(run.testRunId());
    } catch (TheSimulatorRefused | TheSimulatorDidNotAnswer leftAsItWas) {
      setUpFailed.addSuppressed(leftAsItWas);
    }
    try {
      simulator.removeTheAccount(run.testRunId(), StoryRun.ACCOUNT);
    } catch (TheSimulatorRefused | TheSimulatorDidNotAnswer leftAsItWas) {
      setUpFailed.addSuppressed(leftAsItWas);
    }
  }

  public void stripeReportsTheFinishedPayment(String testRunId) {
    StoryRun run = runs.called(testRunId);
    Order order =
        run.order()
            .filter(placed -> placed.status() == OrderStatus.CAPTURED)
            .orElseThrow(
                () -> new NotAtThatStepYet(testRunId, "Stripe has a finished payment to report"));
    synchronized (run) {
      if (run.stripeAskedToReport()) {
        return;
      }
      simulator.sendTheEvent(testRunId, order.paymentIntentId(), THE_EVENT_OF_A_FINISHED_PAYMENT);
      run.stripeWasAskedToReport();
    }
  }

  public TidyUpOutcome tidyUp() {
    List<String> tidied = new ArrayList<>();
    List<TidyUpOutcome.RunLeftBehind> leftBehind = new ArrayList<>();
    for (StoryRun run : runs.newestFirst()) {
      synchronized (run) {
        if (!runs.stillKeeps(run)) {
          continue;
        }
        try {
          simulator.reset(run.testRunId());
          simulator.removeTheAccount(run.testRunId(), StoryRun.ACCOUNT);
          runs.forget(run);
          tidied.add(run.testRunId());
        } catch (TheSimulatorRefused | TheSimulatorDidNotAnswer failed) {
          leftBehind.add(new TidyUpOutcome.RunLeftBehind(run.testRunId(), failed.getMessage()));
        }
      }
    }
    return new TidyUpOutcome(tidied, leftBehind);
  }
}
