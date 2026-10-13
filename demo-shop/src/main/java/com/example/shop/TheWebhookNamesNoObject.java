package com.example.shop;

public final class TheWebhookNamesNoObject extends RuntimeException {

  private static final long serialVersionUID = 1L;

  TheWebhookNamesNoObject(String testRunId, String eventId) {
    super("the event " + eventId + " for " + testRunId + " names no object in data.object.id");
  }
}
