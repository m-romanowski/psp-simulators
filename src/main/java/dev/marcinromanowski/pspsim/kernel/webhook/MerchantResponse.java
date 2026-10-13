package dev.marcinromanowski.pspsim.kernel.webhook;

public sealed interface MerchantResponse {

  record Answered(int httpStatus, String bodyPreview) implements MerchantResponse {}

  record NotAnswered(TransportFailure failure) implements MerchantResponse {}
}
