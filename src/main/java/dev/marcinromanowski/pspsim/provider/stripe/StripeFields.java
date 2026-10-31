package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

final class StripeFields {

  private StripeFields() {}

  static final String AMOUNT = "amount";
  static final String CURRENCY = "currency";
  static final String CAPTURE_METHOD = "capture_method";
  static final String CONFIRM = "confirm";
  static final String PAYMENT_METHOD = "payment_method";
  static final String CUSTOMER = "customer";
  static final String USAGE = "usage";
  static final String PAYMENT_METHOD_TYPES = "payment_method_types";
  static final String AUTOMATIC_PAYMENT_METHODS = "automatic_payment_methods";
  static final String ALLOWED_PAYMENT_METHOD_TYPES = "allowed_payment_method_types";
  static final String EXCLUDED_PAYMENT_METHOD_TYPES = "excluded_payment_method_types";
  static final String PAYMENT_METHOD_CONFIGURATION = "payment_method_configuration";
  static final String PAYMENT_METHOD_CONFIGURATION_DETAILS = "payment_method_configuration_details";
  static final String TYPE = "type";
  static final String RECEIPT_EMAIL = "receipt_email";
  static final String EMAIL = "email";
  static final String OFF_SESSION = "off_session";
  static final String RETURN_URL = "return_url";
  static final String AMOUNT_TO_CAPTURE = "amount_to_capture";
  static final String FINAL_CAPTURE = "final_capture";
  static final String PAYMENT_INTENT = "payment_intent";
  static final String CHARGE = "charge";
  static final String REASON = "reason";
  static final String CANCELLATION_REASON = "cancellation_reason";
  static final String ASSOCIATED_OBJECTS = "associated_objects";
  static final String EXPIRES = "expires";

  static final String AUTHORIZATION_HEADER = "Authorization";

  static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";

  static final String THE_BASIC_CHALLENGE = "Basic realm=\"Stripe\"";

  static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

  static final String REQUEST_ID_HEADER = "Request-Id";

  static final String STRIPE_VERSION_HEADER = "Stripe-Version";

  static final String REQUEST_ID_PREFIX = "req_sim_";

  static final String CLIENT_SECRET = "client_secret";

  static final Set<String> SENSITIVE_REQUEST_FIELDS = Set.of(PAYMENT_METHOD, CLIENT_SECRET);

  static final String SECRET = "secret";

  static final String WITHHELD = Redaction.REDACTED;

  static final String CARD = "card";

  static final Set<String> SENSITIVE_CARD_MEMBERS = Set.of("number", "cvc");

  private static final Pattern BETWEEN_KEYS = Pattern.compile("[\\[\\]]+");

  private static final Pattern A_RUN_OF_DIGITS = Pattern.compile("[0-9](?:[0-9 -]*[0-9])?");

  private static final Pattern DIGITS = Pattern.compile("[0-9]+");

  static final Set<String> SENSITIVE_RESPONSE_FIELDS = Set.of(CLIENT_SECRET, SECRET);

  static final Set<String> SENSITIVE_DELIVERY_FIELDS = Set.of("data.object." + CLIENT_SECRET);

  static String theLeafOf(String key) {
    return key.substring(key.lastIndexOf('.') + 1);
  }

  static SequencedMap<String, List<String>> withholding(
      Map<String, List<String>> body, Set<String> sensitive) {
    LinkedHashMap<String, List<String>> withheld = new LinkedHashMap<>();
    body.forEach(
        (key, values) -> {
          String leaf = theLeafOf(key);
          withheld.put(key, Redaction.redact(Map.of(leaf, values), sensitive).get(leaf));
        });
    return Collections.unmodifiableSequencedMap(withheld);
  }

  static Map<String, List<String>> withholdingTheCard(Map<String, List<String>> fields) {
    LinkedHashMap<String, List<String>> withheld = new LinkedHashMap<>();
    fields.forEach(
        (name, values) -> {
          boolean aCardMember = namesASensitiveCardMember(name);
          List<String> kept =
              new ArrayList<>(withheld.getOrDefault(withholdingCardNumbersIn(name), List.of()));
          for (String value : values) {
            kept.add(aCardMember ? WITHHELD : withholdingCardNumbersIn(value));
          }
          withheld.put(withholdingCardNumbersIn(name), List.copyOf(kept));
        });
    return Collections.unmodifiableMap(withheld);
  }

  static String withholdingCardNumbersIn(String text) {
    boolean[] withheld = new boolean[text.length()];
    Matcher runs = A_RUN_OF_DIGITS.matcher(text);
    while (runs.find()) {
      List<int[]> groups = new ArrayList<>();
      Matcher digits = DIGITS.matcher(runs.group());
      while (digits.find()) {
        groups.add(new int[] {runs.start() + digits.start(), runs.start() + digits.end()});
      }
      IntStream.range(0, groups.size())
          .forEach(
              first ->
                  IntStream.range(first, groups.size())
                      .forEach(
                          last -> {
                            int from = groups.get(first)[0];
                            int to = groups.get(last)[1];
                            if (readsAsACardNumber(text.substring(from, to))) {
                              Arrays.fill(withheld, from, to, true);
                            }
                          }));
    }
    StringBuilder kept = new StringBuilder();
    for (int at = 0; at < text.length(); at++) {
      if (!withheld[at]) {
        kept.append(text.charAt(at));
      } else if (at == 0 || !withheld[at - 1]) {
        kept.append(WITHHELD);
      }
    }
    return kept.toString();
  }

  static boolean namesASensitiveCardMember(String name) {
    List<String> keys = new ArrayList<>();
    for (String key : BETWEEN_KEYS.split(name.toLowerCase(Locale.ROOT), -1)) {
      if (!key.isEmpty()) {
        keys.add(key);
      }
    }
    int card = keys.indexOf(CARD);
    if (card < 0) {
      return false;
    }
    for (String after : keys.subList(card + 1, keys.size())) {
      if (SENSITIVE_CARD_MEMBERS.contains(after)) {
        return true;
      }
    }
    return false;
  }

  static boolean readsAsACardNumber(String value) {
    String digits = value.replace(" ", "").replace("-", "");
    return StripeNewPaymentMethod.theDigitsOf(digits) != null
        && StripeNewPaymentMethod.passesLuhn(digits);
  }

  static final Set<String> SENSITIVE_HEADERS =
      Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");
}
