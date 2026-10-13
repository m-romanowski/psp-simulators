package dev.marcinromanowski.pspsim.kernel.money;

public record DerivedBalances(
    Amount capturedAmount,
    Amount refundedAmount,
    Amount capturableRemainder,
    Amount refundableRemainder) {}
