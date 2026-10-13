package dev.marcinromanowski.pspsim.provider.stripe;

enum StripeErrorType {
  API_ERROR("api_error"),
  CARD_ERROR("card_error"),
  IDEMPOTENCY_ERROR("idempotency_error"),
  INVALID_REQUEST_ERROR("invalid_request_error");

  private final String wireValue;

  StripeErrorType(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }
}
