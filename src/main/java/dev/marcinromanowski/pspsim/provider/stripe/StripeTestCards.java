package dev.marcinromanowski.pspsim.provider.stripe;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class StripeTestCards {

  static final String UNKNOWN = "unknown";

  static final String CREDIT = "credit";

  static final String DEBIT = "debit";

  static final String PREPAID = "prepaid";

  static final String VISA = "visa";

  static final String MASTERCARD = "mastercard";

  static final String AMEX = "amex";

  static final String DISCOVER = "discover";

  static final String DINERS = "diners";

  static final String JCB = "jcb";

  static final String UNIONPAY = "unionpay";

  static final String UNREGULATED = "unregulated";

  static final String PAYMENT_METHOD_STEM = "pm_card_";

  static final String TOKEN_STEM = "tok_";

  static final String PM_CARD_VISA = PAYMENT_METHOD_STEM + "visa";

  static final String THE_VISA_THAT_SUCCEEDS = "4242424242424242";

  record TestCard(
      String number,
      String brand,
      String funding,
      @Nullable String country,
      boolean recordedByStripe,
      List<String> names) {

    TestCard {
      names = List.copyOf(names);
    }
  }

  private StripeTestCards() {}

  static List<TestCard> catalogue() {
    return List.of(
        recorded(THE_VISA_THAT_SUCCEEDS, VISA, CREDIT, "US", "visa", "us"),
        published("4000056655665556", VISA, DEBIT, null, "visa_debit"),
        published("5555555555554444", MASTERCARD, UNKNOWN, null, "mastercard"),
        published("2223003122003222", MASTERCARD, UNKNOWN, null),
        published("5200828282828210", MASTERCARD, DEBIT, null, "mastercard_debit"),
        published("5105105105105100", MASTERCARD, PREPAID, null, "mastercard_prepaid"),
        published("378282246310005", AMEX, UNKNOWN, null, "amex"),
        published("371449635398431", AMEX, UNKNOWN, null),
        published("6011111111111117", DISCOVER, UNKNOWN, null, "discover"),
        published("6011000990139424", DISCOVER, UNKNOWN, null),
        published("6011981111111113", DISCOVER, DEBIT, null),
        published("3056930009020004", DINERS, UNKNOWN, null, "diners"),
        published("36227206271667", DINERS, UNKNOWN, null),
        published("3566002020360505", JCB, UNKNOWN, null),
        published("6200000000000005", UNIONPAY, UNKNOWN, null, "unionpay"),
        published("6200000000000047", UNIONPAY, DEBIT, null),
        published("6205500000000000004", UNIONPAY, UNKNOWN, null),
        published("4000000320000021", VISA, UNKNOWN, "AR", "ar"),
        published("4000000760000002", VISA, UNKNOWN, "BR", "br"),
        published("4000001240000000", VISA, UNKNOWN, "CA", "ca"),
        published("4000001520000001", VISA, UNKNOWN, "CL", "cl"),
        published("4000001700000003", VISA, UNKNOWN, "CO", "co"),
        published("4000001880000005", VISA, UNKNOWN, "CR", "cr"),
        published("4000002180000000", VISA, UNKNOWN, "EC", "ec"),
        published("4000004840008001", VISA, UNKNOWN, "MX", "mx"),
        published("4000005910000000", VISA, UNKNOWN, "PA", "pa"),
        published("4000006000000066", VISA, UNKNOWN, "PY", "py"),
        published("4000006040000068", VISA, UNKNOWN, "PE", "pe"),
        published("4000008580000003", VISA, UNKNOWN, "UY", "uy"),
        published("4000007840000001", VISA, UNKNOWN, "AE", "ae"),
        published("5200007840000022", MASTERCARD, UNKNOWN, "AE", "ae_mastercard"),
        published("4000000400000008", VISA, UNKNOWN, "AT", "at"),
        published("4000000560000004", VISA, UNKNOWN, "BE", "be"),
        published("4000001000000000", VISA, UNKNOWN, "BG", "bg"),
        published("4000001120000005", VISA, UNKNOWN, "BY", "by"),
        published("4000001910000009", VISA, UNKNOWN, "HR", "hr"),
        published("4000001960000008", VISA, UNKNOWN, "CY", "cy"),
        published("4000002030000002", VISA, UNKNOWN, "CZ", "cz"),
        published("4000002080000001", VISA, UNKNOWN, "DK", "dk"),
        published("4000002330000009", VISA, UNKNOWN, "EE", "ee"),
        published("4000002460000001", VISA, UNKNOWN, "FI", "fi"),
        published("4000002500000003", VISA, UNKNOWN, "FR", "fr"),
        published("4000002760000016", VISA, UNKNOWN, "DE", "de"),
        published("4000002920000005", VISA, UNKNOWN, "GI", "gi"),
        published("4000003000000030", VISA, UNKNOWN, "GR", "gr"),
        published("4000003480000005", VISA, UNKNOWN, "HU", "hu"),
        published("4000003720000005", VISA, UNKNOWN, "IE", "ie"),
        published("4000003800000008", VISA, UNKNOWN, "IT", "it"),
        published("4000004280000005", VISA, UNKNOWN, "LV", "lv"),
        published("4000004380000004", VISA, UNKNOWN, "LI", "li"),
        published("4000004400000000", VISA, UNKNOWN, "LT", "lt"),
        published("4000004420000006", VISA, UNKNOWN, "LU", "lu"),
        published("4000004700000007", VISA, UNKNOWN, "MT", "mt"),
        published("4000005280000002", VISA, UNKNOWN, "NL", "nl"),
        published("4000005780000007", VISA, UNKNOWN, "NO", "no"),
        published("4000006160000005", VISA, UNKNOWN, "PL", "pl"),
        published("4000006200000007", VISA, UNKNOWN, "PT", "pt"),
        published("4000006420000001", VISA, UNKNOWN, "RO", "ro"),
        published("4000006820000007", VISA, UNKNOWN, "SA"),
        published("4000007050000006", VISA, UNKNOWN, "SI", "si"),
        published("4000007030000001", VISA, UNKNOWN, "SK", "sk"),
        published("4000007240000007", VISA, UNKNOWN, "ES", "es"),
        published("4000007520000008", VISA, UNKNOWN, "SE", "se"),
        published("4000007560000009", VISA, UNKNOWN, "CH", "ch"),
        published("4000008260000000", VISA, UNKNOWN, "GB", "gb"),
        published("4000058260000005", VISA, DEBIT, "GB", "gb_debit"),
        published("5555558265554449", MASTERCARD, UNKNOWN, "GB", "gb_mastercard"),
        published("4000000360000006", VISA, UNKNOWN, "AU", "au"),
        published("4000001560000002", VISA, UNKNOWN, "CN", "cn"),
        published("4000003440000004", VISA, UNKNOWN, "HK", "hk"),
        published("4000003560000008", VISA, UNKNOWN, "IN", "in"),
        published("4000003920000003", VISA, UNKNOWN, "JP", "jp"),
        published("3530111333300000", JCB, UNKNOWN, "JP", "jcb"),
        published("4000004580000002", VISA, UNKNOWN, "MY", "my"),
        published("4000005540000008", VISA, UNKNOWN, "NZ", "nz"),
        published("4000007020000003", VISA, UNKNOWN, "SG", "sg"),
        published("4000001580000008", VISA, UNKNOWN, "TW", "tw"),
        published("4000007640000003", VISA, CREDIT, "TH", "th_credit"),
        published("4000057640000008", VISA, DEBIT, "TH", "th_debit"),
        recorded("4000000000000002", VISA, CREDIT, "US", "visa_chargeDeclined"),
        recorded("4000000000009995", VISA, CREDIT, "US", "visa_chargeDeclinedInsufficientFunds"));
  }

  private static TestCard published(
      String number, String brand, String funding, @Nullable String country, String... names) {
    return new TestCard(number, brand, funding, country, false, List.of(names));
  }

  private static TestCard recorded(
      String number, String brand, String funding, String country, String... names) {
    return new TestCard(number, brand, funding, country, true, List.of(names));
  }

  static Optional<TestCard> byNumber(String digits) {
    return catalogue().stream().filter(card -> card.number().equals(digits)).findFirst();
  }

  static Optional<TestCard> byPaymentMethodId(@Nullable String paymentMethodId) {
    return byName(PAYMENT_METHOD_STEM, paymentMethodId);
  }

  static Optional<TestCard> byToken(@Nullable String token) {
    return byName(TOKEN_STEM, token);
  }

  private static Optional<TestCard> byName(String stem, @Nullable String sent) {
    if (sent == null || !sent.startsWith(stem)) {
      return Optional.empty();
    }
    String name = sent.substring(stem.length());
    return catalogue().stream().filter(card -> card.names().contains(name)).findFirst();
  }

  static StripeCard theCardOf(TestCard card, int expMonth, int expYear) {
    return new StripeCard(
        card.brand(),
        card.number().substring(card.number().length() - 4),
        expMonth,
        expYear,
        card.funding(),
        card.country(),
        theDisplayBrandOf(card.brand()),
        List.of(card.brand()),
        card.recordedByStripe() ? UNREGULATED : null,
        card.recordedByStripe() ? Boolean.TRUE : null);
  }

  static StripeCard theCardOfANumberStripePublishesNothingFor(
      String digits, int expMonth, int expYear) {
    return new StripeCard(
        UNKNOWN,
        digits.substring(digits.length() - 4),
        expMonth,
        expYear,
        UNKNOWN,
        null,
        null,
        null,
        null,
        null);
  }

  static StripeCard theCardATestNameStandsFor(TestCard card, Instant now) {
    ZonedDateTime at = now.atZone(ZoneOffset.UTC);
    return theCardOf(card, at.getMonthValue(), at.getYear() + 1);
  }

  static @Nullable String theDisplayBrandOf(String brand) {
    return switch (brand) {
      case VISA -> "visa";
      case MASTERCARD -> "mastercard";
      case AMEX -> "american_express";
      case DISCOVER -> "discover";
      case DINERS -> "diners_club";
      case JCB -> "jcb";
      case UNIONPAY -> "union_pay";
      default -> null;
    };
  }
}
