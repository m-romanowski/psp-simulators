package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.smoke.DeclaredCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.Observed;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSample;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSamples;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class StripeCompatibilityChecks {

  static final ProviderId STRIPE = ProviderId.of("stripe");

  static final String CREATE_PAYMENT_INTENT = "POST /v1/payment_intents";

  static final String THE_CONTROL_API_CREATES = "POST /__simulator/stripe/scenarios";

  static final String THE_TRAIL_RECORDS = "GET /__simulator/stripe/audit";

  static final String THE_WEBHOOK_CARRIES = "POST webhook endpoint";

  private static final String ACCOUNT = "merchant-1";

  private static final String INTENTS = "/v1/payment_intents";

  private static final String SIGNATURE = "Stripe-Signature";

  private static final String REPLAYED = "Idempotent-Replayed";

  private static final String CONTENT_TYPE = "Content-Type";

  private static final String CHALLENGE = "WWW-Authenticate";

  private static final int SECRET_LENGTH = 24;

  private static final int TIMESTAMP_PREFIX = 2;

  private final CheckJson json;
  private final IdGenerator ids;
  private final VendorSamples stripe;
  private final VendorSamples simulator;

  public StripeCompatibilityChecks(CheckJson json, IdGenerator ids) {
    this.json = json;
    this.ids = ids;
    this.stripe = VendorSamples.of("stripe");
    this.simulator = VendorSamples.of("simulator");
  }

  public List<ContractCheck> all() {
    return List.of(
        happyPath(),
        decline(),
        idempotency(),
        webhookSignature(),
        authentication(),
        controlApi(),
        auditTrail());
  }

  private ContractCheck happyPath() {
    VendorSample thePublishedHold = stripe.sample("payment-intent-requires-capture");
    return DeclaredCheck.applying(
        STRIPE,
        CREATE_PAYMENT_INTENT,
        thePublishedHold.provenance(),
        List.of(
            new ContractExpectation("status", thePublishedHold.rule("status")),
            new ContractExpectation("retrieved.status", thePublishedHold.rule("status"))),
        transport -> {
          String key = prepare(transport);
          CheckResponse held = transport.send(aHold(key));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "status", json.at(held.body(), "/status"));
          String intent = json.at(held.body(), "/id");
          if (intent != null) {
            CheckResponse reread =
                transport.send(keyed(CheckRequest.get(INTENTS + "/" + intent), key));
            Observed.put(read, "retrieved.status", json.at(reread.body(), "/status"));
          }
          return read;
        });
  }

  private ContractCheck decline() {
    VendorSample thePublishedDecline = stripe.sample("card-declined-insufficient-funds");
    return DeclaredCheck.applying(
        STRIPE,
        CREATE_PAYMENT_INTENT,
        thePublishedDecline.provenance(),
        List.of(
            new ContractExpectation("error.type", thePublishedDecline.rule("error.type")),
            new ContractExpectation("error.code", thePublishedDecline.rule("error.code")),
            new ContractExpectation(
                "error.decline_code", thePublishedDecline.rule("error.decline_code"))),
        transport -> {
          String key = prepare(transport);
          transport.send(
              CheckRequest.json(
                  "POST",
                  "/__simulator/stripe/scenarios",
                  "{\"priority\":0,\"match\":{\"operation\":\"POST /v1/payment_intents\"},"
                      + "\"action\":{\"type\":\"TERMINAL_FAILURE\",\"providerCode\":"
                      + "\"insufficient_funds\"}}"));
          CheckResponse declined = transport.send(aHold(key));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "error.type", json.at(declined.body(), "/error/type"));
          Observed.put(read, "error.code", json.at(declined.body(), "/error/code"));
          Observed.put(read, "error.decline_code", json.at(declined.body(), "/error/decline_code"));
          return read;
        });
  }

  private ContractCheck idempotency() {
    VendorSample replayed = stripe.sample("idempotent-replay");
    return DeclaredCheck.applying(
        STRIPE,
        CREATE_PAYMENT_INTENT,
        replayed.provenance(),
        List.of(
            new ContractExpectation(REPLAYED, replayed.rule(REPLAYED)),
            new ContractExpectation("replay.body", replayed.rule("sameKeySameResult"))),
        transport -> {
          String key = prepare(transport);
          String idempotencyKey = ids.hex(SECRET_LENGTH);
          CheckResponse first =
              transport.send(aHold(key).carrying("Idempotency-Key", idempotencyKey));
          CheckResponse replay =
              transport.send(aHold(key).carrying("Idempotency-Key", idempotencyKey));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, REPLAYED, replay.header(REPLAYED));
          read.put("replay.body", String.valueOf(first.body().equals(replay.body())));
          return read;
        });
  }

  private ContractCheck webhookSignature() {
    VendorSample signed = stripe.sample("webhook-signature");
    return DeclaredCheck.applying(
        STRIPE,
        THE_WEBHOOK_CARRIES,
        signed.provenance(),
        List.of(
            new ContractExpectation(
                "timestamp.prefix",
                signed.header(SIGNATURE).map(StripeCompatibilityChecks::timestampPrefixOf)),
            new ContractExpectation(
                "signature.scheme",
                signed.header(SIGNATURE).map(StripeCompatibilityChecks::schemeOf))),
        transport -> {
          String key = prepare(transport);
          CheckResponse held = transport.send(aHold(key));
          String intent = json.at(held.body(), "/id");
          Map<String, String> read = new LinkedHashMap<>();
          String sent = intent == null ? null : theSignatureOfTheAttemptFor(transport, intent);
          if (sent != null) {
            read.put("timestamp.prefix", timestampPrefixOf(sent));
            Observed.put(read, "signature.scheme", schemeOf(sent));
          }
          return read;
        });
  }

  private ContractCheck authentication() {
    VendorSample theProbedRefusal = stripe.sample("no-api-key");
    return DeclaredCheck.applying(
        STRIPE,
        CREATE_PAYMENT_INTENT,
        theProbedRefusal.provenance(),
        List.of(
            new ContractExpectation("httpStatus", theProbedRefusal.status()),
            new ContractExpectation("error.type", theProbedRefusal.json("/error/type")),
            new ContractExpectation(CONTENT_TYPE, theProbedRefusal.header(CONTENT_TYPE)),
            new ContractExpectation(CHALLENGE, theProbedRefusal.header(CHALLENGE)),
            new ContractExpectation("error.message", theProbedRefusal.json("/error/message"))),
        transport -> {
          prepare(transport);
          CheckResponse refused = transport.send(CheckRequest.form("POST", INTENTS, theHoldForm()));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(refused.status()));
          Observed.put(read, "error.type", json.at(refused.body(), "/error/type"));
          Observed.put(read, CONTENT_TYPE, refused.header(CONTENT_TYPE));
          Observed.put(read, CHALLENGE, refused.header(CHALLENGE));
          Observed.put(read, "error.message", json.at(refused.body(), "/error/message"));
          return read;
        });
  }

  private ContractCheck controlApi() {
    VendorSample scenarioCreated = simulator.sample("scenario-created");
    return DeclaredCheck.applying(
        STRIPE,
        THE_CONTROL_API_CREATES,
        scenarioCreated.provenance(),
        List.of(
            new ContractExpectation("scenario.httpStatus", scenarioCreated.status()),
            new ContractExpectation(
                "fixture.httpStatus", simulator.sample("fixture-created").status())),
        transport -> {
          prepare(transport);
          CheckResponse scenario =
              transport.send(
                  CheckRequest.json(
                      "POST",
                      "/__simulator/stripe/scenarios",
                      "{\"priority\":50,\"match\":{\"operation\":\"POST /v1/payment_intents\"},"
                          + "\"action\":{\"type\":\"FIXED_SUCCESS\"}}"));
          CheckResponse fixture =
              transport.send(
                  CheckRequest.json(
                      "POST",
                      "/__simulator/stripe/fixtures/payment-methods",
                      "{\"merchantId\":\""
                          + ACCOUNT
                          + "\",\"ownerId\":\"smoke-owner\","
                          + "\"type\":\"card\",\"alias\":\"smoke-visa\",\"card\":{\"brand\":"
                          + "\"visa\",\"last4\":\"1111\",\"expMonth\":12,\"expYear\":2030,"
                          + "\"funding\":\"credit\"},\"providerIds\":{\"paymentMethodId\":"
                          + "\"pm_sim_smokecheck\",\"customerId\":\"cus_sim_smokecheck\"}}"));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("scenario.httpStatus", String.valueOf(scenario.status()));
          read.put("fixture.httpStatus", String.valueOf(fixture.status()));
          return read;
        });
  }

  private ContractCheck auditTrail() {
    VendorSample auditRecord = simulator.sample("audit-record");
    return DeclaredCheck.applying(
        STRIPE,
        THE_TRAIL_RECORDS,
        auditRecord.provenance(),
        List.of(
            new ContractExpectation("audit.direction", auditRecord.json("/direction")),
            new ContractExpectation("audit.outcome", auditRecord.json("/outcome"))),
        transport -> {
          String key = prepare(transport);
          transport.send(aHold(key));
          CheckResponse trail =
              transport.send(CheckRequest.get("/__simulator/stripe/audit?origin=PROVIDER_API"));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "audit.direction", json.at(trail.body(), "/0/direction"));
          Observed.put(read, "audit.outcome", json.at(trail.body(), "/1/outcome"));
          return read;
        });
  }

  private @Nullable String theSignatureOfTheAttemptFor(CheckTransport transport, String intent) {
    transport.send(
        CheckRequest.json(
            "POST",
            "/__simulator/stripe/webhooks",
            "{\"providerObjectId\":\"" + intent + "\",\"merchantId\":\"" + ACCOUNT + "\"}"));
    CheckResponse deliveries = transport.send(CheckRequest.get("/__simulator/stripe/webhooks"));
    return json.at(deliveries.body(), "/0/attempts/0/sentHeaders/" + SIGNATURE + "/0");
  }

  private static String timestampPrefixOf(String signature) {
    String first = signature.split(",", -1)[0];
    return first.substring(0, Math.min(TIMESTAMP_PREFIX, first.length()));
  }

  private static @Nullable String schemeOf(String signature) {
    for (String element : signature.split(",", -1)) {
      int split = element.indexOf('=');
      if (split < 1) {
        continue;
      }
      String scheme = element.substring(0, split);
      if (!"t".equals(scheme)) {
        return scheme;
      }
    }
    return null;
  }

  private String prepare(CheckTransport transport) {
    String secretKey = "sk_test_" + ids.hex(SECRET_LENGTH);
    String signingSecret = "whsec_" + ids.hex(SECRET_LENGTH);
    transport.send(
        CheckRequest.json("POST", "/__simulator/stripe/reset", "{\"scope\":\"TEST_RUN\"}"));
    transport.send(
        CheckRequest.json(
            "PUT",
            "/__simulator/stripe/config",
            "{\"accounts\":[{\"accountId\":\""
                + ACCOUNT
                + "\",\"configuration\":{\"secretKey\":\""
                + secretKey
                + "\",\"captureWindow\":\"P3D\",\"webhookSecret\":\""
                + signingSecret
                + "\"}}],\"webhooks\":"
                + "[{\"accountId\":\""
                + ACCOUNT
                + "\",\"url\":\"http://127.0.0.1:1/smoke\"}]}"));
    return secretKey;
  }

  private static CheckRequest aHold(String secretKey) {
    return keyed(CheckRequest.form("POST", INTENTS, theHoldForm()), secretKey);
  }

  private static CheckRequest keyed(CheckRequest request, String secretKey) {
    return request.carrying("Authorization", "Bearer " + secretKey);
  }

  private static Map<String, String> theHoldForm() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", "pm_card_visa");
    form.put("confirm", "true");
    return form;
  }
}
