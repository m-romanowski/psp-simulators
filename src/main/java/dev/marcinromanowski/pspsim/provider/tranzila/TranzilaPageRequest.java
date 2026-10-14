package dev.marcinromanowski.pspsim.provider.tranzila;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record TranzilaPageRequest(
    String sum,
    TranzilaPageCurrency currency,
    TranzilaTranmode tranmode,
    @Nullable String successUrl,
    @Nullable String failUrl,
    @Nullable String notifyUrl,
    @Nullable String dcDisable,
    @Nullable String thtk,
    SequencedMap<String, String> echoed) {

  static final String A_DECIMAL = "[0-9]+(\\.[0-9]+)?";

  static final String ONE_PAYMENT = "1";

  static final Set<String> THE_SCHEMES_A_RETURN_ADDRESS_TAKES = Set.of("http", "https");

  static final String THE_SUM_IS_REQUIRED =
      "sum is required, a positive decimal number, as the iframe parameters table publishes it";

  static final String THE_CURRENCY_IS_REQUIRED =
      "currency is required and is one of 1 (NIS), 2 (US dollar), 978 (Euro) and 826 (Pound"
          + " Sterling), as the iframe parameters table publishes it";

  static final String THE_TRANMODE_IS_REQUIRED =
      "tranmode is required and is one of A, V, N and K, or AK, VK and NK for the same with a"
          + " token, as the iframe parameters tables publish them";

  static final String A_STANDING_ORDER_IS_NOT_SERVED =
      "tranmode J starts a standing order, which needs the My Billing module and is not served by"
          + " this simulator; no page was opened and nothing was charged";

  static final String INSTALMENTS_ARE_NOT_SERVED =
      "cred_type other than 1 asks for instalments, which this simulator does not serve; no page"
          + " was opened and nothing was charged";

  static final String NOT_AN_ADDRESS =
      " is not an absolute http or https address a browser could be sent to or a notify posted"
          + " to";

  static final String THE_DC_DISABLE_IS_TOO_LONG =
      "Invalid DCdisable: DCdisable is limited to 254 characters, as the DCdisable section"
          + " publishes it";

  TranzilaPageRequest {
    echoed = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(echoed));
  }

  sealed interface Read {

    record Admitted(TranzilaPageRequest request) implements Read {}

    record Refused(String why) implements Read {}
  }

  static Read read(Map<String, List<String>> sent) {
    String sum = first(sent, TranzilaFields.SUM);
    if (sum == null || !isAPositiveDecimal(sum)) {
      return new Read.Refused(THE_SUM_IS_REQUIRED);
    }
    Optional<TranzilaPageCurrency> currency =
        Optional.ofNullable(first(sent, TranzilaFields.CURRENCY))
            .flatMap(TranzilaPageCurrency::numbered);
    if (currency.isEmpty()) {
      return new Read.Refused(THE_CURRENCY_IS_REQUIRED);
    }
    String tranmode = first(sent, TranzilaFields.TRANMODE);
    if (TranzilaTranmode.STANDING_ORDER.equals(tranmode)) {
      return new Read.Refused(A_STANDING_ORDER_IS_NOT_SERVED);
    }
    Optional<TranzilaTranmode> served =
        Optional.ofNullable(tranmode).flatMap(TranzilaTranmode::named);
    if (served.isEmpty()) {
      return new Read.Refused(THE_TRANMODE_IS_REQUIRED);
    }
    String credType = first(sent, TranzilaFields.CRED_TYPE);
    if (credType != null && !ONE_PAYMENT.equals(credType)) {
      return new Read.Refused(INSTALMENTS_ARE_NOT_SERVED);
    }
    for (String address :
        List.of(
            TranzilaFields.SUCCESS_URL_ADDRESS,
            TranzilaFields.FAIL_URL_ADDRESS,
            TranzilaFields.NOTIFY_URL_ADDRESS)) {
      String named = first(sent, address);
      if (named != null && !isAnAddress(named)) {
        return new Read.Refused(address + NOT_AN_ADDRESS);
      }
    }
    String dcDisable = first(sent, TranzilaFields.DC_DISABLE);
    if (dcDisable != null && dcDisable.length() > CreateTransaction.LONGEST_DC_DISABLE) {
      return new Read.Refused(THE_DC_DISABLE_IS_TOO_LONG);
    }
    return new Read.Admitted(
        new TranzilaPageRequest(
            sum,
            currency.get(),
            served.get(),
            first(sent, TranzilaFields.SUCCESS_URL_ADDRESS),
            first(sent, TranzilaFields.FAIL_URL_ADDRESS),
            first(sent, TranzilaFields.NOTIFY_URL_ADDRESS),
            dcDisable,
            first(sent, TranzilaFields.THTK),
            theEchoedFieldsOf(sent)));
  }

  static SequencedMap<String, String> theEchoedFieldsOf(Map<String, List<String>> sent) {
    SequencedMap<String, String> echoed = new LinkedHashMap<>();
    for (String field : TranzilaFields.THE_PREDETERMINED_FIELDS) {
      String value = first(sent, field);
      if (value != null) {
        echoed.put(field, value);
      }
    }
    return echoed;
  }

  static boolean isAPositiveDecimal(String sum) {
    return sum.matches(A_DECIMAL) && new BigDecimal(sum).signum() > 0;
  }

  static boolean isAnAddress(String named) {
    try {
      URI address = new URI(named);
      String scheme = address.getScheme();
      return scheme != null
          && THE_SCHEMES_A_RETURN_ADDRESS_TAKES.contains(scheme.toLowerCase(Locale.ROOT))
          && address.getHost() != null;
    } catch (URISyntaxException notAnAddress) {
      return false;
    }
  }

  static @Nullable String first(Map<String, List<String>> sent, String field) {
    List<String> values = sent.get(field);
    if (values == null || values.isEmpty()) {
      return null;
    }
    String value = values.get(0);
    return value.isEmpty() ? null : value;
  }

  boolean answersOnTheSum(BigDecimal locked) {
    return new BigDecimal(sum).compareTo(locked) == 0;
  }
}
