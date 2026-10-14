package dev.marcinromanowski.pspsim.provider.tranzila;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    @Nullable String dcDisable,
    @Nullable TranzilaThreeDSecure threeDSecure,
    @Nullable String sentCurrency,
    List<TranzilaUserField> userDefinedFields,
    List<TranzilaItem> items) {

  TranzilaCreateRequest {
    userDefinedFields = List.copyOf(userDefinedFields);
    items = List.copyOf(items);
  }

  static final String THE_DEFAULT_CURRENCY = "ILS";

  static final Set<String> THE_CURRENCIES = Set.of("ILS", "USD", "EUR", "GBP");

  static final Set<Long> THE_PAN_ENTRY_MODES = Set.of(50L, 51L, 52L);

  static final Set<String> THE_ITEM_FIELDS_THE_SCHEMA_PUBLISHES =
      Set.of(
          "code",
          TranzilaFields.NAME,
          TranzilaFields.TYPE,
          TranzilaFields.UNIT_PRICE,
          "unit_type",
          TranzilaFields.PRICE_TYPE,
          TranzilaFields.UNITS_NUMBER,
          TranzilaFields.CURRENCY_CODE,
          "to_txn_currency_exchange_rate",
          "discount_type",
          "discount",
          TranzilaFields.VAT_PERCENT,
          "attributes");

  static final Set<String> THE_ITEM_FIELDS_WHOSE_ARITHMETIC_IS_UNPUBLISHED =
      Set.of("to_txn_currency_exchange_rate", "discount_type", "discount");

  static final String WHOSE_ARITHMETIC_IS_UNPUBLISHED =
      " changes the amount by arithmetic Tranzila does not publish, so it is refused rather than"
          + " charged at an amount this simulator would have to guess";

  static final String A_COUPON = "C";

  static final String NET = "N";

  static final Set<String> THE_PRICE_TYPES = Set.of("G", NET);

  static final int SHORTEST_CARD_NUMBER = 8;

  static final int LONGEST_CARD_NUMBER = 19;

  static final int CARD_HOLDER_ID_LENGTH = 9;

  static final String NOT_SERVED =
      " is published and not served by this simulator, so it is refused rather than answered"
          + " with a success that performed something else";

  static TranzilaCreateRequest read(TranzilaJson.JsonObject body) {
    for (String sent : body.members().keySet()) {
      if (!TranzilaFields.THE_REQUEST_FIELDS_THE_SCHEMA_PUBLISHES.contains(sent)
          && !TranzilaThreeDSecure.THE_TOP_LEVEL_MEMBERS_THE_COMPLETE_EXAMPLE_ECHOES.contains(
              sent)) {
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
    String currencyCode = currencyIn(body);
    String amount = amountIn(body, type, verifyMode, currencyCode);
    TranzilaThreeDSecure.refuseWhatTheSchemaDoesNotAdmit(body);
    boolean asksFor3ds = body.text(TranzilaFields.ACTIVATE_3DS).filter("Y"::equals).isPresent();
    if (asksFor3ds && !type.carriesACardholder()) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.ACTIVATE_3DS
              + " Y authenticates the card holder of a debit or a verify, and a "
              + type.wire()
              + " acts on a transaction already made");
    }
    List<TranzilaUserField> userDefinedFields = userDefinedFieldsIn(body);
    return new TranzilaCreateRequest(
        terminalName,
        type,
        verifyMode,
        cardNumber,
        expireMonth,
        expireYear,
        cvvIn(body),
        cardHolderIdIn(body),
        currencyCode,
        amount,
        integer(body, TranzilaFields.REFERENCE_TXN_ID).orElse(null),
        text(body, TranzilaFields.AUTHORIZATION_NUMBER).orElse(null),
        dcDisableIn(userDefinedFields),
        asksFor3ds ? TranzilaThreeDSecure.read(body) : null,
        body.has(TranzilaFields.TXN_CURRENCY_CODE) ? currencyCode : null,
        userDefinedFields,
        type.carriesAnAmount() ? itemsIn(body) : List.of());
  }

  static TranzilaCreateRequest ofTheChallenge(
      TranzilaChallenge challenge, TranzilaJson.JsonObject originalRequest) {
    return new TranzilaCreateRequest(
        challenge.terminalName(),
        challenge.type(),
        challenge.verifyMode(),
        challenge.token(),
        Integer.valueOf(challenge.card().expiryMonth()),
        Integer.valueOf(challenge.card().expiryYear()),
        CreateTransaction.ENTERED_AND_VALID.equals(challenge.responseCvv())
            ? TranzilaHostedFieldsCharge.AN_ENTERED_CVV_NOT_CARRIED
            : null,
        CreateTransaction.ENTERED_AND_VALID.equals(challenge.responseId())
            ? TranzilaHostedFieldsCharge.AN_ENTERED_ID_NOT_CARRIED
            : null,
        challenge.currencyCode(),
        challenge.amount(),
        null,
        null,
        challenge.dcDisable(),
        null,
        challenge.currencyCode(),
        userDefinedFieldsIn(originalRequest),
        challenge.type().carriesAnAmount() ? itemsIn(originalRequest) : List.of());
  }

  boolean isInTheCurrencyOf(TranzilaTransaction original) {
    String sent = sentCurrency;
    return sent == null || sent.equals(original.currencyCode());
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
      if (!"Y".equals(asked) && !"N".equals(asked)) {
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
      TranzilaJson.JsonObject body,
      TranzilaTxnType type,
      @Nullable Integer verifyMode,
      String currencyCode) {
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
      sum = sum.add(theAmountOf(item, currencyCode));
    }
    if (required && sum.signum() <= 0) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.ITEMS + " must add up to more than nothing for a " + type.wire());
    }
    return TranzilaAmounts.lexicalOf(sum);
  }

  private static BigDecimal theAmountOf(TranzilaJson item, String currencyCode) {
    if (!(item instanceof TranzilaJson.JsonObject described)) {
      throw new TranzilaSchemaMismatch("every item of " + TranzilaFields.ITEMS + " is an object");
    }
    for (String sent : described.members().keySet()) {
      if (!THE_ITEM_FIELDS_THE_SCHEMA_PUBLISHES.contains(sent)) {
        throw new TranzilaSchemaMismatch(
            TranzilaFields.ITEMS + "." + sent + " is not a field of the Transactions API schema");
      }
      if (THE_ITEM_FIELDS_WHOSE_ARITHMETIC_IS_UNPUBLISHED.contains(sent)) {
        throw new TranzilaSchemaMismatch(
            TranzilaFields.ITEMS + "." + sent + WHOSE_ARITHMETIC_IS_UNPUBLISHED);
      }
    }
    if (text(described, TranzilaFields.NAME).isEmpty()) {
      throw required(TranzilaFields.ITEMS + "." + TranzilaFields.NAME);
    }
    if (described.text(TranzilaFields.TYPE).filter(A_COUPON::equals).isPresent()) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.ITEMS
              + "."
              + TranzilaFields.TYPE
              + " C"
              + WHOSE_ARITHMETIC_IS_UNPUBLISHED);
    }
    if (described.has(TranzilaFields.CURRENCY_CODE)
        && described.text(TranzilaFields.CURRENCY_CODE).filter(currencyCode::equals).isEmpty()) {
      throw new TranzilaSchemaMismatch(
          TranzilaFields.ITEMS
              + "."
              + TranzilaFields.CURRENCY_CODE
              + " other than txn_currency_code needs an exchange rate"
              + WHOSE_ARITHMETIC_IS_UNPUBLISHED);
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
    BigDecimal asSent = TranzilaAmounts.ofAnItem(price, units);
    return isNet(described) ? TranzilaAmounts.withVat(asSent, vatPercentOf(described)) : asSent;
  }

  private static boolean isNet(TranzilaJson.JsonObject item) {
    if (!item.has(TranzilaFields.PRICE_TYPE)) {
      return false;
    }
    String priceType =
        item.text(TranzilaFields.PRICE_TYPE)
            .filter(THE_PRICE_TYPES::contains)
            .orElseThrow(
                () ->
                    new TranzilaSchemaMismatch(
                        TranzilaFields.ITEMS
                            + "."
                            + TranzilaFields.PRICE_TYPE
                            + " is neither G nor N"));
    return NET.equals(priceType);
  }

  private static String vatPercentOf(TranzilaJson.JsonObject item) {
    if (!item.has(TranzilaFields.VAT_PERCENT)) {
      return TranzilaAmounts.ISRAELI_VAT_PERCENT;
    }
    return item.number(TranzilaFields.VAT_PERCENT)
        .filter(percent -> !percent.startsWith("-"))
        .orElseThrow(
            () ->
                new TranzilaSchemaMismatch(
                    TranzilaFields.ITEMS
                        + "."
                        + TranzilaFields.VAT_PERCENT
                        + " must be a number that is not negative"));
  }

  private static List<TranzilaUserField> userDefinedFieldsIn(TranzilaJson.JsonObject body) {
    if (!body.has(TranzilaFields.USER_DEFINED_FIELDS)) {
      return List.of();
    }
    List<TranzilaJson> fields =
        body.array(TranzilaFields.USER_DEFINED_FIELDS)
            .map(TranzilaJson.JsonArray::elements)
            .orElseThrow(
                () ->
                    new TranzilaSchemaMismatch(
                        TranzilaFields.USER_DEFINED_FIELDS + " must be an array"));
    List<TranzilaUserField> read = new ArrayList<>();
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
      read.add(new TranzilaUserField(name, value));
    }
    return List.copyOf(read);
  }

  static @Nullable String dcDisableIn(List<TranzilaUserField> fields) {
    String dcDisable = null;
    for (TranzilaUserField field : fields) {
      if (TranzilaFields.DC_DISABLE.equals(field.name())) {
        dcDisable = field.value();
      }
    }
    return dcDisable;
  }

  private static List<TranzilaItem> itemsIn(TranzilaJson.JsonObject body) {
    List<TranzilaItem> read = new ArrayList<>();
    for (TranzilaJson item :
        body.array(TranzilaFields.ITEMS).map(TranzilaJson.JsonArray::elements).orElse(List.of())) {
      if (item instanceof TranzilaJson.JsonObject described) {
        read.add(anItem(described));
      }
    }
    return List.copyOf(read);
  }

  static TranzilaItem anItem(TranzilaJson.JsonObject item) {
    BigDecimal price = new BigDecimal(item.number(TranzilaFields.UNIT_PRICE).orElseThrow());
    return new TranzilaItem(
        item.text(TranzilaFields.NAME).orElseThrow(),
        integer(item, TranzilaFields.UNITS_NUMBER).orElseThrow().longValue(),
        TranzilaAmounts.lexicalOf(
            isNet(item) ? TranzilaAmounts.withVat(price, vatPercentOf(item)) : price),
        item.text(TranzilaFields.TYPE).orElse(TranzilaItem.A_PRODUCT));
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
