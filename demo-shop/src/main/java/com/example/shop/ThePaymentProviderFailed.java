package com.example.shop;

public final class ThePaymentProviderFailed extends RuntimeException {

  private static final long serialVersionUID = 1L;

  ThePaymentProviderFailed(String what, Throwable cause) {
    super("Stripe could not " + what + ": " + cause.getMessage(), cause);
  }
}
