package dev.marcinromanowski.pspsim.provider.tranzila;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record TranzilaCreateRequest(
    String terminalName,
    TranzilaTxnType type,
    @Nullable Integer verifyMode,
    @Nullable String cardNumber,
    @Nullable Integer expireMonth,
    @Nullable Integer expireYear,
    @Nullable String cvv,
    @Nullable String cardHolderId,
    String currencyCode,
    @Nullable String amount,
    @Nullable Long referenceTxnId,
    @Nullable String authorizationNumber,
    @Nullable String dcDisable) {

  static final String THE_DEFAULT_CURRENCY = "ILS";

  static final Set<String> THE_CURRENCIES = Set.of("ILS", "USD", "EUR", "GBP");

  static final Set<Long> THE_PAN_ENTRY_MODES = Set.of(50L, 51L, 52L);

  static final int SHORTEST_CARD_NUMBER = 8;

  static final int LONGEST_CARD_NUMBER = 19;

  static final int CARD_HOLDER_ID_LENGTH = 9;

  static final String NOT_SERVED =
      " is published and not served by this simulator, so it is refused rather than answered"
          + " with a success that performed something else";

  static final String THREE_D_SECURE_ON =
      TranzilaFields.ACTIVATE_3DS
          + " Y asks for 3-D Secure, which this route does not perform here; a request asking for"
          + " it is refused rather than charged without it";

  static TranzilaCreateRequest read(TranzilaJson.JsonObject body) {
    for (String sent : body.members().keySet()) {
      if (!TranzilaFields.THE_REQUEST_FIELDS_THE_SCHEMA_PUBLISHES.contains(sent)) {
        throw new TranzilaSchemaMismatch(sent + " is not a field of the Transactions API schema");
      }
    }
    String terminalName =
        text(body, TranzilaFields.TERMINAL_NAME)
            .filter(sent -> !sent.isBlank())
            .orElseThrow(() -> required(TranzilaFields.TERMINAL_NAME));
    TranzilaTxnType type = typeIn(body);
    Integer verifyMode = verifyModeIn(body, type);
    refuseWhatThisSimulatorDoesNotServe(body);
    String cardNumber = cardNumberIn(body).orElse(null);
    Integer expireMonth =
        integer(body, TranzilaFields.EXPIRE_MONTH).map(Long::intValue).orElse(null);
    Integer expireYear = integer(body, TranzilaFields.EXPIRE_YEAR).map(Long::intValue).orElse(null);
    if (expireMonth != null && (expireMonth < 1 || expireMonth > 12)) {
      throw new TranzilaSchemaMismatch(TranzilaFields.EXPIRE_MONTH + " must be 1 to 12");
    }
    if (expireYear != null && expireYear < 0) {
      throw new TranzilaSchemaMismatch(TranzilaFields.EXPIRE_YEAR + " must not be negative");
    }
    if (type.carriesACard()) {
      if (cardNumber == null) {
        throw required(TranzilaFields.CARD_NUMBER);
      }
      if (expireMonth == null) {
        throw required(TranzilaFields.EXPIRE_MONTH);
      }
      if (expireYear == null) {
        throw required(TranzilaFields.EXPIRE_YEAR);
      }
    }
    String amount = amountIn(body, type, verifyMode);
    return new TranzilaCreateRequest(
        terminalName,
        type,
        verifyMode,
        cardNumber,
        expireMonth,
        expireYear,
        cvvIn(body),
        cardHolderIdIn(body),
        currencyIn(body),
        amount,
        integer(body, TranzilaFields.REFERENCE_TXN_ID).orElse(null),
        text(body, TranzilaFields.AUTHORIZATION_NUMBER).orElse(null),
        dcDisableIn(body));
  }

  private static TranzilaTxnType typeIn(TranzilaJson.JsonObject body) {
    if (!body.has(TranzilaFields.TXN_TYPE)) {
      return TranzilaTxnType.DEBIT;
    }
    String sent =
        text(body, TranzilaFields.TXN_TYPE).orElseThrow(() -> notAString(TranzilaFields.TXN_TYPE));
    if (TranzilaTxnType.STO.equals(sent)) {
      throw new TranzilaSchemaMismatch(TranzilaFields.TXN_TYPE + " sto" + NOT_SERVED);
    }
    return TranzilaTxnType.of(sent)
        .orElseThrow(
            () ->
                new TranzilaSchemaMismatch(
                    TranzilaFields.TXN_TYPE
                        + " is none of debit, credit, verify, force, cancel, reversal and sto"));
  }

  private static @Nullable Integer verifyModeIn(
      TranzilaJson.JsonObject body, TranzilaTxnType type) {
    Optional<Long> sent = integer(body, TranzilaFields.VERIFY_MODE);
    if (sent.isEmpty()) {
      if (type == TranzilaTxnType.VERIFY) {
        throw required(TranzilaFields.VERIFY_MODE);
      }
      return null;
    }
    long mode = sent.get().longValue();
    if (mode == TranzilaTxnType.STANDING_ORDER_VERIFICATION) {
      throw new TranzilaSchemaMismatch(TranzilaFields.VERIFY_MODE + " 6" + NOT_SERVED);
    }
    if (mode != TranzilaTxnType.J2 && mode != TranzilaTxnType.J5) {
      throw new TranzilaSchemaMismatch(TranzilaFields.VERIFY_MODE + " is none of 2, 5 and 6");
    }
    return type == TranzilaTxnType.VERIFY ? Integer.valueOf((int) mode) : null;
  }

  private static void refuseWhatThisSimulatorDoesNotServe(TranzilaJson.JsonObject body) {
    Optional<Long> plan = integer(body, TranzilaFields.PAYMENT_PLAN);
    if (plan.isPresent() && plan.get().longValue() != 1L) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.PAYMENT_PLAN
              + " other than 1 asks for instalments, which are"
              + NOT_SERVED);
    }
    Optional<Long> entry = integer(body, TranzilaFields.PAN_ENTRY_MODE);
    if (entry.isPresent() && !THE_PAN_ENTRY_MODES.contains(entry.get())) {
      throw new TranzilaSchemaMismatch(TranzilaFields.PAN_ENTRY_MODE + " is none of 50, 51 and 52");
    }
    if (body.has(TranzilaFields.ACTIVATE_3DS)) {
      String asked =
          text(body, TranzilaFields.ACTIVATE_3DS)
              .orElseThrow(() -> notAString(TranzilaFields.ACTIVATE_3DS));
      if ("Y".equals(asked)) {
        throw new TranzilaSchemaMismatch(THREE_D_SECURE_ON);
      }
      if (!"N".equals(asked)) {
        throw new TranzilaSchemaMismatch(TranzilaFields.ACTIVATE_3DS + " is neither Y nor N");
      }
    }
  }

  private static Optional<String> cardNumberIn(TranzilaJson.JsonObject body) {
    if (!body.has(TranzilaFields.CARD_NUMBER)) {
      return Optional.empty();
    }
    String sent =
        text(body, TranzilaFields.CARD_NUMBER)
            .orElseThrow(() -> notAString(TranzilaFields.CARD_NUMBER));
    if (sent.length() < SHORTEST_CARD_NUMBER
        || sent.length() > LONGEST_CARD_NUMBER
        || !sent.matches("[0-9A-Za-z]+")) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.CARD_NUMBER
              + " must be 8 to 19 letters and digits: a card number, or a token in its place");
    }
    return Optional.of(sent);
  }

  private static @Nullable String cvvIn(TranzilaJson.JsonObject body) {
    if (!body.has(TranzilaFields.CVV)
        || body.member(TranzilaFields.CVV) instanceof TranzilaJson.JsonNothing) {
      return null;
    }
    String sent = text(body, TranzilaFields.CVV).orElseThrow(() -> notAString(TranzilaFields.CVV));
    if (!sent.matches("[0-9]{3,4}")) {
      throw new TranzilaSchemaMismatch(TranzilaFields.CVV + " must be 3 or 4 digits");
    }
    return sent;
  }

  private static @Nullable String cardHolderIdIn(TranzilaJson.JsonObject body) {
    if (!body.has(TranzilaFields.CARD_HOLDER_ID)) {
      return null;
    }
    String sent =
        text(body, TranzilaFields.CARD_HOLDER_ID)
            .orElseThrow(() -> notAString(TranzilaFields.CARD_HOLDER_ID));
    if (sent.length() != CARD_HOLDER_ID_LENGTH) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.CARD_HOLDER_ID + " must be " + CARD_HOLDER_ID_LENGTH + " characters");
    }
    return sent;
  }

  private static String currencyIn(TranzilaJson.JsonObject body) {
    if (!body.has(TranzilaFields.TXN_CURRENCY_CODE)) {
      return THE_DEFAULT_CURRENCY;
    }
    String sent =
        text(body, TranzilaFields.TXN_CURRENCY_CODE)
            .orElseThrow(() -> notAString(TranzilaFields.TXN_CURRENCY_CODE));
    if (!THE_CURRENCIES.contains(sent)) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.TXN_CURRENCY_CODE + " is none of ILS, USD, EUR and GBP");
    }
    return sent;
  }

  private static @Nullable String amountIn(
      TranzilaJson.JsonObject body, TranzilaTxnType type, @Nullable Integer verifyMode) {
    boolean required =
        type.carriesAnAmount() && !Integer.valueOf(TranzilaTxnType.J2).equals(verifyMode);
    if (!body.has(TranzilaFields.ITEMS)) {
      if (required) {
        throw required(TranzilaFields.ITEMS);
      }
      return null;
    }
    List<TranzilaJson> items =
        body.array(TranzilaFields.ITEMS)
            .map(TranzilaJson.JsonArray::elements)
            .orElseThrow(
                () -> new TranzilaSchemaMismatch(TranzilaFields.ITEMS + " must be an array"));
    if (items.isEmpty()) {
      throw new TranzilaSchemaMismatch(TranzilaFields.ITEMS + " must name at least one item");
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (TranzilaJson item : items) {
      sum = sum.add(theAmountOf(item));
    }
    if (required && sum.signum() <= 0) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.ITEMS + " must add up to more than nothing for a " + type.wire());
    }
    return TranzilaAmounts.lexicalOf(sum);
  }

  private static BigDecimal theAmountOf(TranzilaJson item) {
    if (!(item instanceof TranzilaJson.JsonObject described)) {
      throw new TranzilaSchemaMismatch("every item of " + TranzilaFields.ITEMS + " is an object");
    }
    if (text(described, TranzilaFields.NAME).isEmpty()) {
      throw required(TranzilaFields.ITEMS + "." + TranzilaFields.NAME);
    }
    long units =
        integer(described, TranzilaFields.UNITS_NUMBER)
            .orElseThrow(() -> required(TranzilaFields.ITEMS + "." + TranzilaFields.UNITS_NUMBER))
            .longValue();
    String price =
        described
            .number(TranzilaFields.UNIT_PRICE)
            .orElseThrow(() -> required(TranzilaFields.ITEMS + "." + TranzilaFields.UNIT_PRICE));
    if (units < 1 || price.startsWith("-")) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.ITEMS
              + " carries a negative "
              + TranzilaFields.UNIT_PRICE
              + " or fewer than one "
              + TranzilaFields.UNITS_NUMBER);
    }
    return TranzilaAmounts.ofAnItem(price, units);
  }

  private static @Nullable String dcDisableIn(TranzilaJson.JsonObject body) {
    if (!body.has(TranzilaFields.USER_DEFINED_FIELDS)) {
      return null;
    }
    List<TranzilaJson> fields =
        body.array(TranzilaFields.USER_DEFINED_FIELDS)
            .map(TranzilaJson.JsonArray::elements)
            .orElseThrow(
                () ->
                    new TranzilaSchemaMismatch(
                        TranzilaFields.USER_DEFINED_FIELDS + " must be an array"));
    String dcDisable = null;
    for (TranzilaJson field : fields) {
      if (!(field instanceof TranzilaJson.JsonObject named)) {
        throw new TranzilaSchemaMismatch(
            "every element of " + TranzilaFields.USER_DEFINED_FIELDS + " is an object");
      }
      String name =
          text(named, TranzilaFields.NAME)
              .orElseThrow(
                  () -> required(TranzilaFields.USER_DEFINED_FIELDS + "." + TranzilaFields.NAME));
      String value =
          named
              .scalar(TranzilaFields.VALUE)
              .orElseThrow(
                  () -> required(TranzilaFields.USER_DEFINED_FIELDS + "." + TranzilaFields.VALUE));
      if (TranzilaFields.DC_DISABLE.equals(name)) {
        dcDisable = value;
      }
    }
    return dcDisable;
  }

  private static Optional<String> text(TranzilaJson.JsonObject body, String field) {
    return body.text(field);
  }

  static Optional<Long> integer(TranzilaJson.JsonObject body, String field) {
    if (!body.has(field)) {
      return Optional.empty();
    }
    String sent =
        body.number(field)
            .filter(lexical -> lexical.matches("-?[0-9]{1,18}"))
            .orElseThrow(() -> new TranzilaSchemaMismatch(field + " must be an integer"));
    return Optional.of(Long.valueOf(sent));
  }

  private static TranzilaSchemaMismatch required(String field) {
    return new TranzilaSchemaMismatch(field + " is required");
  }

  private static TranzilaSchemaMismatch notAString(String field) {
    return new TranzilaSchemaMismatch(field + " must be a string");
  }

  boolean namesAToken() {
    String sent = cardNumber;
    return sent != null && !sent.chars().allMatch(Character::isDigit);
  }
}
