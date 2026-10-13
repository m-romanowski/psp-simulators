package dev.marcinromanowski.pspsim.kernel.webhook;

import org.springframework.scheduling.annotation.Scheduled;

public final class WebhookDeliveryPoll {

  private final WebhookDispatcher dispatcher;

  public WebhookDeliveryPoll(WebhookDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Scheduled(fixedDelayString = "${simulator.webhook.poll-interval}")
  public void deliverDue() {
    dispatcher.deliverDueEverywhere();
  }
}
