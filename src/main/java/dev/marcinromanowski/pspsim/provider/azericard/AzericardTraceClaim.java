package dev.marcinromanowski.pspsim.provider.azericard;

import java.time.LocalDate;
import java.util.function.BooleanSupplier;

record AzericardTraceClaim(LocalDate day, BooleanSupplier claim) {

  boolean isTaken() {
    return claim.getAsBoolean();
  }
}
