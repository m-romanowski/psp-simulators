package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AutraAcquirerReport {
  REPORTED,

  NOT_REPORTED;

  static final String SETTING = "autra.acquirer-transaction-identifier";

  static final String THE_VENDOR_PUBLISHES_THE_ABSENCE =
      "provider autra describes trackingNumber as \"Identificador da transacao no adquirente (o"
          + " \\\"NSU\\\"). Use para conciliacao.\" and then \"Ausente se o adquirente nao"
          + " informar\", so a card payment whose acquirer named no transaction carries neither"
          + " that field nor nsu, which is \"Mesmo valor de trackingNumber, mantido por"
          + " compatibilidade\"";

  boolean isReported() {
    return this == REPORTED;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            REPORTED.name(),
            "the acquirer names the transaction it took, and every answer that publishes that name"
                + " carries it"),
        new AcceptedValue(
            NOT_REPORTED.name(),
            "the acquirer names no transaction, and every answer that would publish that name"
                + " leaves it out instead"));
  }

  static AutraAcquirerReport of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return REPORTED;
    }
    for (AutraAcquirerReport configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two things provider autra publishes an acquirer doing with"
            + " the transaction identifier. "
            + THE_VENDOR_PUBLISHES_THE_ABSENCE
            + ", so the two values are "
            + REPORTED.name()
            + ", the acquirer named the transaction, and "
            + NOT_REPORTED.name()
            + ", the acquirer named none and the field the vendor publishes as absent is absent");
  }
}
