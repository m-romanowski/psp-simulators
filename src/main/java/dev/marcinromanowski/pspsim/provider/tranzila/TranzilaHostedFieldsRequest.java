package dev.marcinromanowski.pspsim.provider.tranzila;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record TranzilaHostedFieldsRequest(
    String terminalName,
    String thtk,
    String amount,
    TranzilaPageCurrency currency,
    TranzilaTranmode tranmode,
    boolean tokenize,
    TranzilaHostedCard card,
    int expiryMonth,
    int expiryYear,
    @Nullable String dcDisable,
    SequencedMap<String, String> userFormData) {

  static final String EXPIRY_MONTH = "expiry_month";

  static final String EXPIRY_YEAR = "expiry_year";

  static final String CURRENCY_CODE = "currency_code";

  static final String THE_ONE_PAYMENT_PLAN_SERVED = "1";

  static final String TOKENIZE_YES = "true";

  static final List<String> THE_MANDATORY_KEYS =
      List.of(
          TranzilaFields.TERMINAL_NAME,
          TranzilaFields.THTK,
          TranzilaFields.AMOUNT,
          TranzilaFields.CREDIT_CARD_NUMBER,
          EXPIRY_MONTH,
          EXPIRY_YEAR);

  static final Set<String> THE_KEYS_THE_CHARGE_READS =
      Set.of(
          TranzilaFields.TERMINAL_NAME,
          TranzilaFields.THTK,
          TranzilaFields.AMOUNT,
          CURRENCY_CODE,
          TranzilaFields.TRAN_MODE,
          TranzilaFields.TOKENIZE,
          TranzilaFields.PAYMENT_PLAN,
          TranzilaFields.CREDIT_CARD_NUMBER,
          TranzilaFields.CVV,
          EXPIRY_MONTH,
          EXPIRY_YEAR,
          TranzilaFields.CARD_HOLDER_ID_NUMBER);

  static final List<TranzilaPageCurrency> THE_CURRENCIES_SERVED =
      List.of(TranzilaPageCurrency.NIS, TranzilaPageCurrency.US_DOLLAR, TranzilaPageCurrency.EURO);

  static final List<TranzilaTranmode> THE_TRAN_MODES_SERVED =
      List.of(TranzilaTranmode.A, TranzilaTranmode.V, TranzilaTranmode.N);

  static final String AN_AMOUNT = "(0|[1-9][0-9]{0,12})(\\.[0-9]{1,2})?";

  static final String A_CARD_NUMBER = "[0-9]{8,19}";

  static final String A_CVV = "[0-9]{3,4}";

  static final String A_MONTH = "[0-9]{1,2}";

  static final String A_YEAR = "[0-9]{4}";

  static final String AN_ID_NUMBER = "[0-9]{1,9}";

  static final String WHITESPACE = "\\s";

  static final String NOTHING = "";

  static final int LAST_MONTH = 12;

  record Refusal(int code, String param, String message) {}

  sealed interface Read {

    record Admitted(TranzilaHostedFieldsRequest request) implements Read {}

    record Refused(List<Refusal> refusals) implements Read {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }
  }

  TranzilaHostedFieldsRequest {
    userFormData = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(userFormData));
  }

  static Read read(Map<String, List<String>> sent) {
    List<Refusal> refusals = new ArrayList<>();
    for (String key : THE_MANDATORY_KEYS) {
      if (first(sent, key) == null) {
        refusals.add(
            TranzilaHostedFieldsErrors.of(
                TranzilaHostedFieldsErrors.MANDATORY_KEY_IS_MISSING, key));
      }
    }
    String amount = first(sent, TranzilaFields.AMOUNT);
    if (amount != null && !isAnAmount(amount)) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_AMOUNT, TranzilaFields.AMOUNT));
    }
    Optional<TranzilaPageCurrency> currency = currencyIn(first(sent, CURRENCY_CODE));
    if (currency.isEmpty()) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_CURRENCY, CURRENCY_CODE));
    }
    Optional<TranzilaTranmode> tranmode = tranmodeIn(first(sent, TranzilaFields.TRAN_MODE));
    if (tranmode.isEmpty()) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_TRAN_MODE, TranzilaFields.TRAN_MODE));
    }
    String plan = first(sent, TranzilaFields.PAYMENT_PLAN);
    if (plan != null && !THE_ONE_PAYMENT_PLAN_SERVED.equals(plan)) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_PAYMENT_PLAN, TranzilaFields.PAYMENT_PLAN));
    }
    String typed = first(sent, TranzilaFields.CREDIT_CARD_NUMBER);
    String cardNumber = typed == null ? null : typed.replaceAll(WHITESPACE, NOTHING);
    if (cardNumber != null && !isACardNumber(cardNumber)) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_CREDIT_CARD_NUMBER,
              TranzilaFields.CREDIT_CARD_NUMBER));
    }
    String cvv = first(sent, TranzilaFields.CVV);
    if (cvv != null && !cvv.matches(A_CVV)) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_CVV, TranzilaFields.CVV));
    }
    String month = first(sent, EXPIRY_MONTH);
    if (month != null && !isAMonth(month)) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_EXPIRATION_MONTH, EXPIRY_MONTH));
    }
    String year = first(sent, EXPIRY_YEAR);
    if (year != null && !year.matches(A_YEAR)) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_EXPIRATION_YEAR, EXPIRY_YEAR));
    }
    String idNumber = first(sent, TranzilaFields.CARD_HOLDER_ID_NUMBER);
    if (idNumber != null && !idNumber.matches(AN_ID_NUMBER)) {
      refusals.add(
          TranzilaHostedFieldsErrors.of(
              TranzilaHostedFieldsErrors.INVALID_CARD_HOLDER_ID_NUMBER,
              TranzilaFields.CARD_HOLDER_ID_NUMBER));
    }
    String dcDisable = first(sent, TranzilaFields.DC_DISABLE);
    if (dcDisable != null && dcDisable.length() > CreateTransaction.LONGEST_DC_DISABLE) {
      refusals.add(
          new Refusal(
              TranzilaErrors.INVALID_DC_DISABLE,
              TranzilaFields.DC_DISABLE,
              TranzilaErrors.theTextOf(TranzilaErrors.INVALID_DC_DISABLE)));
    }
    if (!refusals.isEmpty()) {
      return new Read.Refused(refusals);
    }
    return new Read.Admitted(
        new TranzilaHostedFieldsRequest(
            String.valueOf(first(sent, TranzilaFields.TERMINAL_NAME)),
            String.valueOf(first(sent, TranzilaFields.THTK)),
            String.valueOf(amount),
            currency.orElseThrow(),
            tranmode.orElseThrow(),
            TOKENIZE_YES.equalsIgnoreCase(first(sent, TranzilaFields.TOKENIZE)),
            new TranzilaHostedCard(String.valueOf(cardNumber), cvv != null, idNumber != null),
            Integer.parseInt(String.valueOf(month)),
            Integer.parseInt(String.valueOf(year)),
            dcDisable,
            theUserFormDataIn(sent)));
  }

  BigDecimal theAmount() {
    return new BigDecimal(amount);
  }

  static boolean isAnAmount(String sent) {
    return sent.matches(AN_AMOUNT) && new BigDecimal(sent).signum() > 0;
  }

  static boolean isACardNumber(String sent) {
    return sent.matches(A_CARD_NUMBER) && passesLuhn(sent);
  }

  static boolean isAMonth(String sent) {
    if (!sent.matches(A_MONTH)) {
      return false;
    }
    int month = Integer.parseInt(sent);
    return month >= 1 && month <= LAST_MONTH;
  }

  static boolean passesLuhn(String digits) {
    int sum = 0;
    boolean doubled = false;
    for (int at = digits.length() - 1; at >= 0; at--) {
      int digit = digits.charAt(at) - '0';
      if (doubled) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubled = !doubled;
    }
    return sum % 10 == 0;
  }

  static Optional<TranzilaPageCurrency> currencyIn(@Nullable String sent) {
    if (sent == null) {
      return Optional.of(TranzilaPageCurrency.NIS);
    }
    return THE_CURRENCIES_SERVED.stream().filter(served -> served.alpha().equals(sent)).findFirst();
  }

  static Optional<TranzilaTranmode> tranmodeIn(@Nullable String sent) {
    if (sent == null) {
      return Optional.of(TranzilaTranmode.A);
    }
    return THE_TRAN_MODES_SERVED.stream().filter(served -> served.wire().equals(sent)).findFirst();
  }

  static SequencedMap<String, String> theUserFormDataIn(Map<String, List<String>> sent) {
    SequencedMap<String, String> echoed = new LinkedHashMap<>();
    sent.forEach(
        (name, values) -> {
          if (!THE_KEYS_THE_CHARGE_READS.contains(name) && !values.isEmpty()) {
            echoed.put(name, values.get(0));
          }
        });
    return echoed;
  }

  static @Nullable String first(Map<String, List<String>> sent, String key) {
    return TranzilaPageRequest.first(sent, key);
  }
}
