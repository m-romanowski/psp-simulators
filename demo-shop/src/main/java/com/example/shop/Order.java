package com.example.shop;

import java.util.Optional;

public final class Order {

  private final String orderId;
  private final Product product;
  private final String paymentIntentId;
  private OrderStatus status = OrderStatus.AWAITING_CAPTURE;
  private String refusal;

  Order(String orderId, Product product, String paymentIntentId) {
    this.orderId = orderId;
    this.product = product;
    this.paymentIntentId = paymentIntentId;
  }

  public String orderId() {
    return orderId;
  }

  public Product product() {
    return product;
  }

  public String paymentIntentId() {
    return paymentIntentId;
  }

  public synchronized OrderStatus status() {
    return status;
  }

  public synchronized Optional<String> refusal() {
    return Optional.ofNullable(refusal);
  }

  public synchronized boolean awaitsCapture() {
    return status == OrderStatus.AWAITING_CAPTURE;
  }

  synchronized void captured() {
    status = OrderStatus.CAPTURED;
  }

  synchronized void captureRefusedFor(String code) {
    status = OrderStatus.CAPTURE_REFUSED;
    refusal = code;
  }
}
