package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;

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
  static final String TYPE = "type";
  static final String RECEIPT_EMAIL = "receipt_email";
  static final String EMAIL = "email";
  static final String OFF_SESSION = "off_session";
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

  static final String REQUEST_ID_PREFIX = "req_sim_";

  static final Set<String> SENSITIVE_REQUEST_FIELDS = Set.of(PAYMENT_METHOD);

  static final String CLIENT_SECRET = "client_secret";

  static final String SECRET = "secret";

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

  static final Set<String> SENSITIVE_HEADERS =
      Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");
}
