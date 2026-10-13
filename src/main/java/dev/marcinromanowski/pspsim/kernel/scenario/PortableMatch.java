package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import org.jspecify.annotations.Nullable;

public record PortableMatch(
    @Nullable String merchantId,
    @Nullable String orderId,
    @Nullable String providerObjectId,
    @Nullable String customerId,
    @Nullable String paymentMethodId,
    @Nullable String amount,
    @Nullable String currency,
    @Nullable String operation,
    @Nullable String eventType) {

  public static PortableMatch of(ScenarioMatch match) {
    Amount amount = match.amount();
    return new PortableMatch(
        match.merchantId(),
        match.orderId(),
        match.providerObjectId(),
        match.customerId(),
        match.paymentMethodId(),
        amount == null ? null : amount.value(),
        match.currency(),
        match.operation(),
        match.eventType());
  }

  public DeclaredMatch asDeclared() {
    return new DeclaredMatch(
        merchantId,
        orderId,
        providerObjectId,
        customerId,
        paymentMethodId,
        amount,
        currency,
        operation,
        eventType,
        null);
  }
}
