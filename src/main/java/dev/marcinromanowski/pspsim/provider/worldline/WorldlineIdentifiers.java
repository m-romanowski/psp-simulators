package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.util.Locale;
import java.util.regex.Pattern;

final class WorldlineIdentifiers {

  static final int PAYID_DIGITS = 10;

  static final int HOSTED_TOKENIZATION_HEX_DIGITS = 32;

  static final int TOKEN_HEX_DIGITS = 32;

  static final char PAYIDSUB_SEPARATOR = '_';

  static final int THE_FIRST_PAYIDSUB = 0;

  static final Pattern A_PAYMENT_ID = Pattern.compile("[0-9]{" + PAYID_DIGITS + "}_[0-9]+");

  static final String THE_PUBLISHED_FORMULA =
      "provider worldline publishes one grammar for a payment identifier and this simulator draws"
          + " it: \"Our platform returns a separate, unique payment.id for every CreatePayment"
          + " request and every maintenance operation, which implements the formula"
          + " PAYID_PAYIDSUB\". The published examples are 3066019730_0, 3066019730_1 and"
          + " 4373719828_0, so the PAYID half is ten digits and the PAYIDSUB half counts from"
          + " zero. The vendor warns against reading the increment — \"as this is not the case in"
          + " some specific scenarios, we strongly recommend not building your business"
          + " operations around it\" — so nothing in this simulator derives a payment's state"
          + " from the number after the underscore";

  private WorldlineIdentifiers() {}

  static String aPayId(IdGenerator ids) {
    return ids.digits(PAYID_DIGITS);
  }

  static String paymentIdOf(String payId, int payIdSub) {
    return payId + PAYIDSUB_SEPARATOR + payIdSub;
  }

  static String thePayIdIn(String paymentId) {
    int separator = paymentId.indexOf(PAYIDSUB_SEPARATOR);
    return separator < 0 ? paymentId : paymentId.substring(0, separator);
  }

  static String aHostedTokenizationId(IdGenerator ids) {
    return ids.hex(HOSTED_TOKENIZATION_HEX_DIGITS).toLowerCase(Locale.ROOT);
  }

  static String aUuid(IdGenerator ids) {
    String hex = ids.hex(HOSTED_TOKENIZATION_HEX_DIGITS).toLowerCase(Locale.ROOT);
    return hex.substring(0, 8)
        + "-"
        + hex.substring(8, 12)
        + "-"
        + hex.substring(12, 16)
        + "-"
        + hex.substring(16, 20)
        + "-"
        + hex.substring(20, 32);
  }

  static String aToken(IdGenerator ids) {
    return ids.hex(TOKEN_HEX_DIGITS).toLowerCase(Locale.ROOT);
  }
}
