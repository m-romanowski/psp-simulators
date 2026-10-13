package com.example.shop;

public enum OrderStatus {
  AWAITING_CAPTURE("Paid, awaiting capture"),
  CAPTURED("Captured"),
  CAPTURE_REFUSED("Capture refused");

  private final String words;

  OrderStatus(String words) {
    this.words = words;
  }

  public String words() {
    return words;
  }
}
