package dev.marcinromanowski.pspsim.provider.azericard;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class AzericardCardCheck {

  static final int LAST_FOUR = 4;

  static final String MASK = "************";

  static final String EXPIRY_SEPARATOR = "/";

  static final int CENTURY = 2000;

  private static final Pattern DIGITS = Pattern.compile("[0-9]+");

  private static final Pattern TWO_DIGITS = Pattern.compile("[0-9]{2}");

  private static final Pattern THREE_DIGITS = Pattern.compile("[0-9]{3}");

  private static final int FIRST_MONTH = 1;

  private static final int LAST_MONTH = 12;

  private static final int MONTH_AND_YEAR = 2;

  private AzericardCardCheck() {}

  static AzericardPresentedCard ofTheWire(AzericardMessage message, Instant now) {
    AzericardForm form = message.form();
    return of(
        form.first(AzericardFields.CARD),
        form.first(AzericardFields.EXP),
        form.first(AzericardFields.EXP_YEAR),
        form.first(AzericardFields.CVC2),
        message.carriesTheCvc2(),
        now);
  }

  static AzericardPresentedCard ofThePage(
      @Nullable String pan, @Nullable String expiry, @Nullable String cvc, Instant now) {
    String written = expiry == null ? "" : expiry.strip();
    List<String> parts = List.of(written.split(EXPIRY_SEPARATOR, -1));
    if (parts.size() != MONTH_AND_YEAR) {
      return of(pan, null, null, cvc, true, now);
    }
    return of(pan, parts.get(0).strip(), parts.get(1).strip(), cvc, true, now);
  }

  static AzericardPresentedCard ofATestCard(AzericardTestCard card, Instant now) {
    return of(card.pan(), card.expiryMonth(), card.expiryYear(), card.cvc(), true, now);
  }

  static AzericardPresentedCard of(
      @Nullable String pan,
      @Nullable String month,
      @Nullable String year,
      @Nullable String cvc,
      boolean cvcCarried,
      Instant now) {
    String number = pan == null ? "" : pan.replace(" ", "");
    String last4 = keptOf(number);
    if (!AzericardTestCard.isPublished(number)) {
      return new AzericardPresentedCard.Declined(last4);
    }
    if (!unexpired(month, year, now) || (cvcCarried && !wellFormed(cvc))) {
      return new AzericardPresentedCard.Declined(last4);
    }
    return new AzericardPresentedCard.Accepted(lastFourOf(number));
  }

  private static boolean wellFormed(@Nullable String cvc) {
    return cvc != null && THREE_DIGITS.matcher(cvc).matches();
  }

  private static boolean unexpired(@Nullable String month, @Nullable String year, Instant now) {
    if (month == null || year == null) {
      return false;
    }
    if (!TWO_DIGITS.matcher(month).matches() || !TWO_DIGITS.matcher(year).matches()) {
      return false;
    }
    int monthOfYear = Integer.parseInt(month);
    if (monthOfYear < FIRST_MONTH || monthOfYear > LAST_MONTH) {
      return false;
    }
    YearMonth expires = YearMonth.of(CENTURY + Integer.parseInt(year), monthOfYear);
    return !expires.isBefore(YearMonth.from(now.atZone(ZoneOffset.UTC)));
  }

  private static @Nullable String keptOf(String number) {
    if (number.length() < LAST_FOUR || !DIGITS.matcher(number).matches()) {
      return null;
    }
    return lastFourOf(number);
  }

  static String lastFourOf(String pan) {
    return pan.substring(pan.length() - LAST_FOUR);
  }

  static String masked(String last4) {
    return MASK + last4;
  }
}
