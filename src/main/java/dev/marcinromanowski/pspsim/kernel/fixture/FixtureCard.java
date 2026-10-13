package dev.marcinromanowski.pspsim.kernel.fixture;

import java.util.regex.Pattern;

public record FixtureCard(String brand, String last4, int expMonth, int expYear, String funding) {

  public static final int LONGEST_DESCRIPTOR = 32;

  public static final String A_FIXTURE_HOLDS_NO_CARD_NUMBER =
      "card.last4 must be the last four digits of the card and nothing else: this simulator holds"
          + " no card number and no verification code anywhere, so a fixture has none to leak";

  private static final Pattern FOUR_DIGITS = Pattern.compile("[0-9]{4}");

  public FixtureCard {
    brand = descriptor("card.brand", brand);
    funding = descriptor("card.funding", funding);
    if (!FOUR_DIGITS.matcher(last4).matches()) {
      throw new IllegalArgumentException(A_FIXTURE_HOLDS_NO_CARD_NUMBER);
    }
    if (expMonth < 1 || expMonth > 12) {
      throw new IllegalArgumentException("card.expMonth must be a month, 1 through 12");
    }
    if (!FOUR_DIGITS.matcher(String.valueOf(expYear)).matches()) {
      throw new IllegalArgumentException("card.expYear must be a four-digit year");
    }
  }

  private static String descriptor(String property, String value) {
    String named = value.strip();
    if (named.isEmpty()) {
      throw new IllegalArgumentException(
          property + " must name what the provider prints for this card");
    }
    if (named.length() > LONGEST_DESCRIPTOR) {
      throw new IllegalArgumentException(
          property + " must be at most " + LONGEST_DESCRIPTOR + " characters");
    }
    return named;
  }
}
