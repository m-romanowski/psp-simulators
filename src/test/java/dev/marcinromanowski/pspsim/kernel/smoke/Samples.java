package dev.marcinromanowski.pspsim.kernel.smoke;

import java.time.LocalDate;

public final class Samples {

  public static final Provenance A_PUBLISHED_EXAMPLE =
      new Provenance(
          "grow/create-payment-process-approved",
          Evidence.of(EvidenceClass.PUBLISHED_EXAMPLE),
          "https://developers.grow.business/reference/delayed-payment-response",
          "grow.md, Create Hosted Payment Process");

  public static final Provenance A_LIVE_PROBE =
      new Provenance(
          "grow/create-payment-process-unknown-business",
          Evidence.probedOn(LocalDate.of(2026, 9, 15)),
          "https://sandbox.meshulam.co.il/api/light/server/1.0/createPaymentProcess",
          "grow.md, Invalid Credentials");

  private Samples() {}

  public static Published published(String value, Provenance provenance) {
    return new Published(value, provenance);
  }
}
