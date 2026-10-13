package dev.marcinromanowski.pspsim.kernel.fixture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum PaymentMethodKind {
  CARD("card"),
  WALLET("wallet"),
  PSP_TOKEN("psp-token");

  private final String publishedName;

  PaymentMethodKind(String publishedName) {
    this.publishedName = publishedName;
  }

  public String publishedName() {
    return publishedName;
  }

  public boolean describesACard() {
    return this == CARD;
  }

  public static Optional<PaymentMethodKind> of(String publishedName) {
    for (PaymentMethodKind kind : values()) {
      if (kind.publishedName.equals(publishedName)) {
        return Optional.of(kind);
      }
    }
    return Optional.empty();
  }

  public static String thePublishedNames() {
    List<String> names = new ArrayList<>();
    for (PaymentMethodKind kind : values()) {
      names.add(kind.publishedName);
    }
    return String.join(", ", names);
  }
}
