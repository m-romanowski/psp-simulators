package com.example.shop;

public interface Payments {

  String placeAHold(StoryRun run, String orderId, Product product);

  CaptureOutcome capture(StoryRun run, String paymentIntentId, long amountInMinorUnits);
}
