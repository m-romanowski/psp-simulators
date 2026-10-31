package dev.marcinromanowski.pspsim.provider.stripe;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class StripeNewPaymentMethod {

  static final String TYPE = "type";

  static final String CARD = "card";

  static final String TOKEN = "token";

  static final String NUMBER = "number";

  static final String EXP_MONTH = "exp_month";

  static final String EXP_YEAR = "exp_year";

  static final String CVC = "cvc";

  static final String BILLING_DETAILS = "billing_details";

  static final String METADATA = "metadata";

  static final String ALLOW_REDISPLAY = "allow_redisplay";

  static final String PAYMENT_METHOD_DATA = "payment_method_data";

  static final int FEWEST_DIGITS = 12;

  static final int MOST_DIGITS = 19;

  static final int MOST_METADATA_KEYS = 50;

  static final int LONGEST_METADATA_KEY = 40;

  static final int LONGEST_METADATA_VALUE = 500;

  static final int YEARS_A_TWO_DIGIT_YEAR_REACHES = 50;

  static final String THE_SHAPE_OF_A_CARD =
      "a hash carrying number, exp_month, exp_year and optionally cvc, or a hash carrying token"
          + " alone";

  static final String THE_SHAPE_OF_BILLING_DETAILS =
      "a hash carrying address (a hash of city, country, line1, line2, postal_code and state),"
          + " email, name, phone and tax_id, each a string of at most "
          + StripeBillingDetails.LONGEST_VALUE
          + " characters";

  static final String THE_SHAPE_OF_METADATA =
      "a hash of at most "
          + MOST_METADATA_KEYS
          + " keys, each key at most "
          + LONGEST_METADATA_KEY
          + " characters long and each value a string of at most "
          + LONGEST_METADATA_VALUE
          + " characters";

  static final String THE_SHAPE_OF_PAYMENT_METHOD_DATA =
      "a hash describing the PaymentMethod to create: type, the hash of that type, and"
          + " optionally billing_details, metadata and allow_redisplay";

  private static final Pattern AN_INTEGER = Pattern.compile("-?[0-9]+");

  private static final Pattern DIGITS = Pattern.compile("[0-9]+");

  private static final Pattern A_SECURITY_CODE = Pattern.compile("[0-9]{3,4}");

  record Description(
      StripeCard card,
      StripeBillingDetails billingDetails,
      Map<String, String> metadata,
      String allowRedisplay) {

    Description {
      metadata = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(metadata));
    }
  }

  sealed interface Outcome {

    record Described(Description description) implements Outcome {}

    record Refused(StripeError error) implements Outcome {}
  }

  private StripeNewPaymentMethod() {}

  static @Nullable StripeParameter thePaymentMethodDataIn(StripeForm form) {
    StripeParameter sent = form.parameters().get(PAYMENT_METHOD_DATA);
    return sent == null || isEmptyText(sent) ? null : sent;
  }

  static Outcome ofTheRequest(StripeForm form, Instant now) {
    return read(form.parameters(), "", now);
  }

  static Outcome ofThePaymentMethodData(StripeParameter sent, Instant now) {
    if (!(sent instanceof StripeParameter.Hashed hashed)) {
      return refused(
          StripeErrors.unreadableParameter(PAYMENT_METHOD_DATA, THE_SHAPE_OF_PAYMENT_METHOD_DATA));
    }
    return read(hashed.entries(), PAYMENT_METHOD_DATA, now);
  }

  private static Outcome read(Map<String, StripeParameter> entries, String prefix, Instant now) {
    String type = textOf(entries.get(TYPE));
    if (type == null) {
      return refused(StripeErrors.missingRequiredParam(named(prefix, TYPE)));
    }
    if (!StripePaymentMethodType.CARD.wireValue().equals(type)) {
      return refused(StripeErrors.paymentMethodTypeNotCreated(named(prefix, TYPE), type));
    }
    String cardParam = named(prefix, CARD);
    StripeParameter card = entries.get(CARD);
    if (card == null || isEmptyText(card)) {
      return refused(StripeErrors.missingRequiredParam(cardParam));
    }
    if (!(card instanceof StripeParameter.Hashed hashed)) {
      return refused(StripeErrors.unreadableParameter(cardParam, THE_SHAPE_OF_A_CARD));
    }
    CardRead read = cardOf(hashed.entries(), cardParam, now);
    StripeError cardRefusal = read.refusal();
    if (cardRefusal != null) {
      return refused(cardRefusal);
    }
    BillingRead billing = billingOf(entries.get(BILLING_DETAILS), named(prefix, BILLING_DETAILS));
    StripeError billingRefusal = billing.refusal();
    if (billingRefusal != null) {
      return refused(billingRefusal);
    }
    MetadataRead metadata = metadataOf(entries.get(METADATA), named(prefix, METADATA));
    StripeError metadataRefusal = metadata.refusal();
    if (metadataRefusal != null) {
      return refused(metadataRefusal);
    }
    String redisplay = textOf(entries.get(ALLOW_REDISPLAY));
    if (redisplay == null) {
      redisplay = StripePaymentMethod.UNSPECIFIED;
    }
    if (!StripePaymentMethod.THE_PUBLISHED_REDISPLAY_VALUES.contains(redisplay)) {
      return refused(
          StripeErrors.invalidValue(
              named(prefix, ALLOW_REDISPLAY),
              StripePublishedPaymentMethodTypes.oneOf(
                  StripePaymentMethod.THE_PUBLISHED_REDISPLAY_VALUES)));
    }
    return new Outcome.Described(
        new Description(
            Objects.requireNonNull(read.card()),
            Objects.requireNonNull(billing.details()),
            metadata.metadata(),
            redisplay));
  }

  private record CardRead(@Nullable StripeCard card, @Nullable StripeError refusal) {

    static CardRead refusedWith(StripeError refusal) {
      return new CardRead(null, refusal);
    }
  }

  private static CardRead cardOf(Map<String, StripeParameter> card, String cardParam, Instant now) {
    String token = textOf(card.get(TOKEN));
    if (token != null) {
      for (String other : List.of(NUMBER, EXP_MONTH, EXP_YEAR, CVC)) {
        if (textOf(card.get(other)) != null) {
          return CardRead.refusedWith(
              StripeErrors.parametersExclusive(
                  cardParam + "[" + TOKEN + "]", cardParam + "[" + other + "]"));
        }
      }
      return StripeTestCards.byToken(token)
          .map(
              published ->
                  new CardRead(StripeTestCards.theCardATestNameStandsFor(published, now), null))
          .orElseGet(
              () ->
                  CardRead.refusedWith(
                      StripeErrors.noSuchToken(cardParam + "[" + TOKEN + "]", token)));
    }
    for (String required : List.of(NUMBER, EXP_MONTH, EXP_YEAR)) {
      if (textOf(card.get(required)) == null) {
        return CardRead.refusedWith(
            StripeErrors.missingRequiredParam(cardParam + "[" + required + "]"));
      }
    }
    String digits = theDigitsOf(Objects.requireNonNull(textOf(card.get(NUMBER))));
    if (digits == null) {
      return CardRead.refusedWith(StripeErrors.cardNumberInvalid());
    }
    if (!passesLuhn(digits)) {
      return CardRead.refusedWith(StripeErrors.cardNumberIncorrect());
    }
    String month = Objects.requireNonNull(textOf(card.get(EXP_MONTH)));
    if (!AN_INTEGER.matcher(month).matches()) {
      return CardRead.refusedWith(
          StripeErrors.invalidInteger(cardParam + "[" + EXP_MONTH + "]", month));
    }
    String year = Objects.requireNonNull(textOf(card.get(EXP_YEAR)));
    if (!AN_INTEGER.matcher(year).matches()) {
      return CardRead.refusedWith(
          StripeErrors.invalidInteger(cardParam + "[" + EXP_YEAR + "]", year));
    }
    Integer expMonth = aMonth(month);
    if (expMonth == null) {
      return CardRead.refusedWith(StripeErrors.expiryMonthInvalid());
    }
    ZonedDateTime today = now.atZone(ZoneOffset.UTC);
    Integer expYear = aYear(year, today.getYear());
    if (expYear == null || expYear < today.getYear()) {
      return CardRead.refusedWith(StripeErrors.expiryYearInvalid());
    }
    if (expYear == today.getYear() && expMonth < today.getMonthValue()) {
      return CardRead.refusedWith(StripeErrors.expiryMonthInvalid());
    }
    String cvc = textOf(card.get(CVC));
    if (cvc != null && !A_SECURITY_CODE.matcher(cvc).matches()) {
      return CardRead.refusedWith(StripeErrors.securityCodeInvalid());
    }
    Optional<StripeTestCards.TestCard> published = StripeTestCards.byNumber(digits);
    return new CardRead(
        published
            .map(known -> StripeTestCards.theCardOf(known, expMonth, expYear))
            .orElseGet(
                () ->
                    StripeTestCards.theCardOfANumberStripePublishesNothingFor(
                        digits, expMonth, expYear)),
        null);
  }

  static @Nullable String theDigitsOf(String number) {
    String digits = number.replace(" ", "");
    if (!DIGITS.matcher(digits).matches()
        || digits.length() < FEWEST_DIGITS
        || digits.length() > MOST_DIGITS) {
      return null;
    }
    return digits;
  }

  static boolean passesLuhn(String digits) {
    int last = digits.length() - 1;
    int sum = 0;
    for (int offset = 1; offset <= last; offset++) {
      int digit = digits.charAt(last - offset) - '0';
      sum += offset % 2 == 1 ? digit * 2 / 10 + digit * 2 % 10 : digit;
    }
    return (10 - sum % 10) % 10 == digits.charAt(last) - '0';
  }

  private static @Nullable Integer aMonth(String month) {
    if (month.length() > 2 || month.startsWith("-")) {
      return null;
    }
    int value = Integer.parseInt(month);
    return value >= 1 && value <= 12 ? value : null;
  }

  static @Nullable Integer aYear(String year, int thisYear) {
    if (year.startsWith("-")) {
      return null;
    }
    if (year.length() == 2) {
      int candidate = thisYear / 100 * 100 + Integer.parseInt(year);
      if (candidate >= thisYear + YEARS_A_TWO_DIGIT_YEAR_REACHES) {
        return candidate - 100;
      }
      if (candidate < thisYear - YEARS_A_TWO_DIGIT_YEAR_REACHES) {
        return candidate + 100;
      }
      return candidate;
    }
    return year.length() == 4 ? Integer.parseInt(year) : null;
  }

  private record BillingRead(
      @Nullable StripeBillingDetails details, @Nullable StripeError refusal) {}

  private static BillingRead billingOf(@Nullable StripeParameter sent, String param) {
    if (sent == null || isEmptyText(sent)) {
      return new BillingRead(StripeBillingDetails.NONE, null);
    }
    if (!(sent instanceof StripeParameter.Hashed hashed)) {
      return new BillingRead(
          null, StripeErrors.unreadableParameter(param, THE_SHAPE_OF_BILLING_DETAILS));
    }
    Map<String, String> read = new LinkedHashMap<>();
    StripeParameter address = hashed.entries().get(StripeBillingDetails.ADDRESS);
    if (address != null && !isEmptyText(address)) {
      if (!(address instanceof StripeParameter.Hashed lines)) {
        return new BillingRead(
            null,
            StripeErrors.unreadableParameter(
                param + "[" + StripeBillingDetails.ADDRESS + "]", THE_SHAPE_OF_BILLING_DETAILS));
      }
      StripeError refused =
          readInto(
              read,
              lines.entries(),
              StripeBillingDetails.THE_ADDRESS_MEMBERS,
              param + "[" + StripeBillingDetails.ADDRESS + "]");
      if (refused != null) {
        return new BillingRead(null, refused);
      }
    }
    StripeError refused =
        readInto(read, hashed.entries(), StripeBillingDetails.THE_PERSON_MEMBERS, param);
    if (refused != null) {
      return new BillingRead(null, refused);
    }
    return new BillingRead(StripeBillingDetails.of(read), null);
  }

  private static @Nullable StripeError readInto(
      Map<String, String> read,
      Map<String, StripeParameter> sent,
      List<String> members,
      String param) {
    for (String member : members) {
      StripeParameter value = sent.get(member);
      if (value == null) {
        continue;
      }
      if (!(value instanceof StripeParameter.Scalar scalar)
          || scalar.value().length() > StripeBillingDetails.LONGEST_VALUE) {
        return StripeErrors.unreadableParameter(
            param + "[" + member + "]", THE_SHAPE_OF_BILLING_DETAILS);
      }
      if (!scalar.value().isEmpty()) {
        read.put(member, scalar.value());
      }
    }
    return null;
  }

  private record MetadataRead(Map<String, String> metadata, @Nullable StripeError refusal) {}

  private static MetadataRead metadataOf(@Nullable StripeParameter sent, String param) {
    if (sent == null || isEmptyText(sent)) {
      return new MetadataRead(Map.of(), null);
    }
    if (!(sent instanceof StripeParameter.Hashed hashed)) {
      return new MetadataRead(
          Map.of(), StripeErrors.unreadableParameter(param, THE_SHAPE_OF_METADATA));
    }
    SequencedMap<String, String> kept = new LinkedHashMap<>();
    for (Map.Entry<String, StripeParameter> entry : hashed.entries().entrySet()) {
      String key = entry.getKey();
      String keyParam = param + "[" + key + "]";
      if (!(entry.getValue() instanceof StripeParameter.Scalar scalar)
          || key.length() > LONGEST_METADATA_KEY
          || scalar.value().length() > LONGEST_METADATA_VALUE) {
        return new MetadataRead(
            Map.of(), StripeErrors.unreadableParameter(keyParam, THE_SHAPE_OF_METADATA));
      }
      if (!scalar.value().isEmpty()) {
        kept.put(key, scalar.value());
      }
    }
    if (kept.size() > MOST_METADATA_KEYS) {
      return new MetadataRead(
          Map.of(), StripeErrors.unreadableParameter(param, THE_SHAPE_OF_METADATA));
    }
    return new MetadataRead(kept, null);
  }

  private static String named(String prefix, String name) {
    return prefix.isEmpty() ? name : prefix + "[" + name + "]";
  }

  private static @Nullable String textOf(@Nullable StripeParameter sent) {
    if (!(sent instanceof StripeParameter.Scalar scalar) || scalar.value().isBlank()) {
      return null;
    }
    return scalar.value();
  }

  private static boolean isEmptyText(StripeParameter sent) {
    return sent instanceof StripeParameter.Scalar scalar && scalar.value().isEmpty();
  }

  private static Outcome refused(StripeError error) {
    return new Outcome.Refused(error);
  }
}
