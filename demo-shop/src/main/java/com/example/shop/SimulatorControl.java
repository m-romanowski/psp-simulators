package com.example.shop;

public interface SimulatorControl {

  void openTheRun(StoryRun run, String callbackUrl);

  void seedTheSavedCard(StoryRun run);

  String refuseEveryCapture(String testRunId, String providerCode);

  void sendTheEvent(String testRunId, String paymentIntentId, String eventType);

  void reset(String testRunId);

  void removeTheAccount(String testRunId, String accountId);
}
