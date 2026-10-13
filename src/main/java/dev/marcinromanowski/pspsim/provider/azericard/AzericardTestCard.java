package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum AzericardTestCard {
  FIRST("4127208104942601", "05", "31", "796"),
  SECOND("5167513332880780", "05", "31", "609"),
  THIRD("4127214121630724", "04", "31", "536");

  static final String ALIAS_PREFIX = "azericard-";

  private final String pan;
  private final String expiryMonth;
  private final String expiryYear;
  private final String cvc;

  AzericardTestCard(String pan, String expiryMonth, String expiryYear, String cvc) {
    this.pan = pan;
    this.expiryMonth = expiryMonth;
    this.expiryYear = expiryYear;
    this.cvc = cvc;
  }

  String pan() {
    return pan;
  }

  String expiryMonth() {
    return expiryMonth;
  }

  String expiryYear() {
    return expiryYear;
  }

  String cvc() {
    return cvc;
  }

  String last4() {
    return AzericardCardCheck.lastFourOf(pan);
  }

  String alias() {
    return ALIAS_PREFIX + last4();
  }

  PayerCard asPayerCard() {
    return new PayerCard(
        alias(),
        "published test card "
            + AzericardCardCheck.masked(last4())
            + ", "
            + expiryMonth
            + "/"
            + expiryYear);
  }

  static boolean isPublished(String pan) {
    for (AzericardTestCard card : values()) {
      if (card.pan.equals(pan)) {
        return true;
      }
    }
    return false;
  }

  static Optional<AzericardTestCard> byAlias(String alias) {
    for (AzericardTestCard card : values()) {
      if (card.alias().equals(alias)) {
        return Optional.of(card);
      }
    }
    return Optional.empty();
  }

  static List<PayerCard> asPayerCards() {
    List<PayerCard> cards = new ArrayList<>();
    for (AzericardTestCard card : values()) {
      cards.add(card.asPayerCard());
    }
    return List.copyOf(cards);
  }

  static String aliases() {
    List<String> aliases = new ArrayList<>();
    for (AzericardTestCard card : values()) {
      aliases.add(card.alias());
    }
    return String.join(", ", aliases);
  }
}
