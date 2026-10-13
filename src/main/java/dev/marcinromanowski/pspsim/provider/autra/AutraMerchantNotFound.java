package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AutraMerchantNotFound {
  FORBIDDEN(403),

  NOT_FOUND(404),

  UNPROCESSABLE(422);

  static final String SETTING = "autra.merchant-not-found-status";

  private final int httpStatus;

  AutraMerchantNotFound(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  int httpStatus() {
    return httpStatus;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            FORBIDDEN.name(),
            "an unknown merchant comes back 403, as autra's card-payment reference answers it"),
        new AcceptedValue(
            NOT_FOUND.name(),
            "an unknown merchant comes back 404, as autra's Pix route and both read routes answer"
                + " it"),
        new AcceptedValue(
            UNPROCESSABLE.name(),
            "an unknown merchant comes back 422, as autra's acquiring error table answers it"));
  }

  static AutraMerchantNotFound of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return NOT_FOUND;
    }
    for (AutraMerchantNotFound configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names none of the three statuses provider autra publishes MERCHANT_NOT_FOUND at."
            + " It is 403 on Create credit/debit card payment, 404 on Initialize Pix session and"
            + " on both read routes, and 422 in the acquiring guide's own error table, and nothing"
            + " the vendor publishes chooses between them, so the three values are "
            + FORBIDDEN.name()
            + ", "
            + NOT_FOUND.name()
            + " and "
            + UNPROCESSABLE.name());
  }
}
