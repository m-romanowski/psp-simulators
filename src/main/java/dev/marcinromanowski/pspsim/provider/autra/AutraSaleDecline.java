package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AutraSaleDecline {
  TWO_HUNDRED_WITH_ERRORS(AutraAnswers.OK, true),

  UNPROCESSABLE_WITH_ERRORS(AutraAnswers.UNPROCESSABLE, false);

  static final String SETTING = "autra.sale-decline-status";

  static final String THE_VENDOR_PUBLISHES_BOTH_ON_ONE_PAGE =
      "provider autra publishes where a declined card sale lives two ways on one reference page."
          + " Its machine-readable contract declares the 200 of POST /v1/acquiring/payments as"
          + " \"Pagamento processado. Verifique o campo status (sucesso) ou errors (recusado pelo"
          + " adquirente)\", as a oneOf between an object requiring paymentID, status, amount and"
          + " currency and an object requiring paymentID and errors, with a saved example named"
          + " Recusado at 200; the 422 it declares on the same operation is about a different"
          + " thing entirely, \"documentId do split nao e um merchant cadastrado no tenant\". The"
          + " prose beside it says instead \"Quando a transacao nao e aprovada a resposta e 422"
          + " com errors[]\". Both are the vendor's and they cannot both be the API";

  private final int httpStatus;

  private final boolean namesThePayment;

  AutraSaleDecline(int httpStatus, boolean namesThePayment) {
    this.httpStatus = httpStatus;
    this.namesThePayment = namesThePayment;
  }

  int httpStatus() {
    return httpStatus;
  }

  boolean namesThePayment() {
    return namesThePayment;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            TWO_HUNDRED_WITH_ERRORS.name(),
            "a declined card sale comes back 200, and the body carries paymentID beside errors[]"),
        new AcceptedValue(
            UNPROCESSABLE_WITH_ERRORS.name(),
            "a declined card sale comes back 422, and the body is the error envelope alone with no"
                + " paymentID in it"));
  }

  static AutraSaleDecline of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return TWO_HUNDRED_WITH_ERRORS;
    }
    for (AutraSaleDecline configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two answers provider autra publishes a declined card sale"
            + " with. "
            + THE_VENDOR_PUBLISHES_BOTH_ON_ONE_PAGE
            + ", so the two values are "
            + TWO_HUNDRED_WITH_ERRORS.name()
            + ", the declared 200 whose body carries paymentID beside errors[], and "
            + UNPROCESSABLE_WITH_ERRORS.name()
            + ", the 422 the prose names, whose body is the error envelope alone because errors"
            + " is the only top-level property that envelope has");
  }
}
