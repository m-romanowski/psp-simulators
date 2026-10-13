package com.example.shop;

import org.springframework.stereotype.Service;

@Service
public class Checkout {

  private final Payments payments;
  private final StoryRuns runs;

  public Checkout(Payments payments, StoryRuns runs) {
    this.payments = payments;
    this.runs = runs;
  }

  public Order buy(String testRunId) {
    StoryRun run = runs.called(testRunId);
    synchronized (run) {
      if (run.order().isPresent()) {
        return run.order().get();
      }
      Product product = Product.THE_ONE_WE_SELL;
      String orderId = "order-" + run.testRunId();
      String paymentIntentId = payments.placeAHold(run, orderId, product);
      return run.ordered(new Order(orderId, product, paymentIntentId));
    }
  }

  public Order capture(String testRunId) {
    StoryRun run = runs.called(testRunId);
    synchronized (run) {
      Order order =
          run.order()
              .orElseThrow(() -> new NotAtThatStepYet(testRunId, "there is an order to capture"));
      if (!order.awaitsCapture()) {
        return order;
      }
      CaptureOutcome outcome =
          payments.capture(run, order.paymentIntentId(), order.product().amountInMinorUnits());
      switch (outcome) {
        case CaptureOutcome.Captured captured -> order.captured();
        case CaptureOutcome.Refused refused -> order.captureRefusedFor(refused.code());
      }
      return order;
    }
  }
}
