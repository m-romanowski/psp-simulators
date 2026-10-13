package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.smoke.DeclaredCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.Observed;
import dev.marcinromanowski.pspsim.kernel.smoke.Published;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeNamespace;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSample;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSamples;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class WorldlineCompatibilityChecks {

  static final ProviderId WORLDLINE = WorldlineApi.WORLDLINE;

  static final String THE_GATEWAY_HOLDS = "POST /v2/{merchantId}/payments";

  static final String THE_MAINTENANCE_MINTS_AN_ID =
      "POST /v2/{merchantId}/payments/{paymentId}/capture";

  static final String THE_GATEWAY_REFUSES_A_SIGNATURE =
      "POST /v2/{merchantId}/payments with a wrong signature";

  static final String THE_GATEWAY_KNOWS_NO_SUCH_PAYMENT =
      "POST /v2/{merchantId}/payments/{unknown}/capture";

  static final String THE_GATEWAY_REPLAYS_A_KEY =
      "POST /v2/{merchantId}/payments under one idempotence key";

  static final String THE_TRAIL_RECORDS = "GET /__simulator/worldline/audit";

  private static final String ACCOUNT = "account-1";

  private static final String MERCHANT = "pspid-1";

  private static final String A_SIGNATURE_NO_SECRET_PRODUCED = "not-the-signature";

  private static final String THE_HELD_AMOUNT = "5000";

  private static final String THE_CURRENCY = "PLN";

  private static final int SECRET_LENGTH = 24;

  private static final int A_PAYIDSUB_NO_PAYMENT_HOLDS = 9;

  private static final String THE_PAYID = "PAYID";

  private static final String THE_PAYIDSUB = "PAYIDSUB";

  private final CheckJson json;
  private final IdGenerator ids;
  private final SimulatorClock clock;
  private final VendorSamples worldline;
  private final VendorSamples simulator;
  private final Pattern aPayIdSub;

  public WorldlineCompatibilityChecks(CheckJson json, IdGenerator ids, SimulatorClock clock) {
    this.json = json;
    this.ids = ids;
    this.clock = clock;
    this.worldline = VendorSamples.of("worldline");
    this.simulator = VendorSamples.of("simulator");
    this.aPayIdSub =
        Pattern.compile(
            Pattern.quote(
                    worldline
                        .sample("payment-id-grammar")
                        .rule("formula")
                        .map(WorldlineCompatibilityChecks::theSeparatorIn)
                        .value())
                + "[0-9]+$");
  }

  public List<ContractCheck> all() {
    return List.of(
        hold(), mintedIdentifier(), refusedSignature(), unknownPayment(), replay(), auditTrail());
  }

  private ContractCheck hold() {
    VendorSample thePublishedHold = worldline.sample("create-payment-pending-capture");
    return DeclaredCheck.applying(
        WORLDLINE,
        THE_GATEWAY_HOLDS,
        thePublishedHold.provenance(),
        List.of(
            new ContractExpectation("payment.status", thePublishedHold.json("/payment/status")),
            new ContractExpectation(
                "payment.statusOutput.statusCategory",
                thePublishedHold.json("/payment/statusOutput/statusCategory")),
            new ContractExpectation(
                "payment.statusOutput.statusCode",
                thePublishedHold.json("/payment/statusOutput/statusCode"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse held = transport.send(aHold(credentials));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "payment.status", json.at(held.body(), "/payment/status"));
          Observed.put(
              read,
              "payment.statusOutput.statusCategory",
              json.at(held.body(), "/payment/statusOutput/statusCategory"));
          Observed.put(
              read,
              "payment.statusOutput.statusCode",
              json.at(held.body(), "/payment/statusOutput/statusCode"));
          return read;
        });
  }

  private ContractCheck mintedIdentifier() {
    VendorSample thePublishedHold = worldline.sample("create-payment-pending-capture");
    return DeclaredCheck.applying(
        WORLDLINE,
        THE_MAINTENANCE_MINTS_AN_ID,
        thePublishedHold.provenance(),
        List.of(
            new ContractExpectation(
                "payment.payIdSub", thePublishedHold.json("/payment/id").map(this::payIdSubOf)),
            new ContractExpectation(
                "capture.payIdSub",
                worldline.sample("capture-response").json("/id").map(this::payIdSubOf))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse held = transport.send(aHold(credentials));
          String paymentId = json.at(held.body(), "/payment/id");
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "payment.payIdSub", payIdSubOf(paymentId));
          if (paymentId != null) {
            CheckResponse captured =
                transport.send(
                    signed(
                        CheckRequest.json(
                            "POST",
                            WorldlineApiController.thePaymentResourceOf(MERCHANT, paymentId)
                                + WorldlineApiController.CAPTURE,
                            "{\"amount\":" + THE_HELD_AMOUNT + "}"),
                        credentials));
            Observed.put(read, "capture.payIdSub", payIdSubOf(json.at(captured.body(), "/id")));
          }
          return read;
        });
  }

  private ContractCheck refusedSignature() {
    VendorSample thePublishedRefusal = worldline.sample("access-to-merchant-not-allowed");
    return DeclaredCheck.applying(
        WORLDLINE,
        THE_GATEWAY_REFUSES_A_SIGNATURE,
        thePublishedRefusal.provenance(),
        List.of(
            new ContractExpectation("httpStatus", thePublishedRefusal.rule("httpStatus")),
            new ContractExpectation("errors.id", thePublishedRefusal.rule("errors.id"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse refused =
              transport.send(
                  theHoldBody(credentials)
                      .carrying(WorldlineFields.DATE, aDate())
                      .carrying(
                          WorldlineFields.AUTHORIZATION,
                          WorldlineSignature.SCHEME
                              + credentials.apiKey()
                              + WorldlineSignature.FIELD_SEPARATOR
                              + A_SIGNATURE_NO_SECRET_PRODUCED));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(refused.status()));
          Observed.put(read, "errors.id", json.at(refused.body(), "/errors/0/id"));
          return read;
        });
  }

  private ContractCheck unknownPayment() {
    VendorSample thePublishedRefusal = worldline.sample("unknown-payment-id");
    return DeclaredCheck.applying(
        WORLDLINE,
        THE_GATEWAY_KNOWS_NO_SUCH_PAYMENT,
        thePublishedRefusal.provenance(),
        List.of(
            new ContractExpectation("httpStatus", thePublishedRefusal.status()),
            new ContractExpectation("errors.id", thePublishedRefusal.json("/errors/0/id")),
            new ContractExpectation(
                "errors.category", thePublishedRefusal.json("/errors/0/category")),
            new ContractExpectation(
                "errors.errorCode", thePublishedRefusal.json("/errors/0/errorCode"))),
        transport -> {
          Credentials credentials = prepare(transport);
          String nothingAnswersToIt =
              WorldlineIdentifiers.paymentIdOf(
                  WorldlineIdentifiers.aPayId(ids), A_PAYIDSUB_NO_PAYMENT_HOLDS);
          CheckResponse refused =
              transport.send(
                  signed(
                      CheckRequest.json(
                          "POST",
                          WorldlineApiController.thePaymentResourceOf(MERCHANT, nothingAnswersToIt)
                              + WorldlineApiController.CAPTURE,
                          "{\"amount\":" + THE_HELD_AMOUNT + "}"),
                      credentials));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(refused.status()));
          Observed.put(read, "errors.id", json.at(refused.body(), "/errors/0/id"));
          Observed.put(read, "errors.category", json.at(refused.body(), "/errors/0/category"));
          Observed.put(read, "errors.errorCode", json.at(refused.body(), "/errors/0/errorCode"));
          return read;
        });
  }

  private ContractCheck replay() {
    VendorSample thePublishedReplay = worldline.sample("idempotent-replay");
    Published theReplayHeader = thePublishedReplay.rule("replayHeader");
    return DeclaredCheck.applying(
        WORLDLINE,
        THE_GATEWAY_REPLAYS_A_KEY,
        thePublishedReplay.provenance(),
        List.of(
            new ContractExpectation(
                "replay.body", thePublishedReplay.rule("sameOutcomeAsTheOriginal")),
            new ContractExpectation("replay.header", theReplayHeader)),
        transport -> {
          Credentials credentials = prepare(transport);
          String key = ids.hex(SECRET_LENGTH);
          CheckResponse first = transport.send(aHoldUnder(credentials, key));
          CheckResponse repeat = transport.send(aHoldUnder(credentials, key));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("replay.body", String.valueOf(first.body().equals(repeat.body())));
          String header = theReplayHeader.value();
          Observed.put(read, "replay.header", repeat.header(header) == null ? null : header);
          return read;
        });
  }

  private ContractCheck auditTrail() {
    VendorSample thePublishedRecord = simulator.sample("audit-record");
    return DeclaredCheck.applying(
        WORLDLINE,
        THE_TRAIL_RECORDS,
        thePublishedRecord.provenance(),
        List.of(
            new ContractExpectation("audit.direction", thePublishedRecord.json("/direction")),
            new ContractExpectation("audit.outcome", thePublishedRecord.json("/outcome"))),
        transport -> {
          Credentials credentials = prepare(transport);
          transport.send(aHold(credentials));
          CheckResponse trail =
              transport.send(CheckRequest.get("/__simulator/worldline/audit?origin=PROVIDER_API"));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "audit.direction", json.at(trail.body(), "/0/direction"));
          Observed.put(read, "audit.outcome", json.at(trail.body(), "/1/outcome"));
          return read;
        });
  }

  record Credentials(String apiKey, String apiSecret, String token) {}

  private Credentials prepare(CheckTransport transport) {
    Credentials credentials =
        new Credentials(
            "smoke-api-key-" + ids.hex(SECRET_LENGTH),
            "smoke-api-secret-" + ids.hex(SECRET_LENGTH),
            WorldlineIdentifiers.aToken(ids));
    transport.send(
        CheckRequest.json("POST", "/__simulator/worldline/reset", "{\"scope\":\"TEST_RUN\"}"));
    transport.send(
        CheckRequest.json(
            "PUT",
            "/__simulator/worldline/config",
            "{\"accounts\":[{\"accountId\":\""
                + ACCOUNT
                + "\",\"configuration\":{\""
                + WorldlineMerchant.MERCHANT_ID_PROPERTY
                + "\":\""
                + MERCHANT
                + "\",\""
                + WorldlineMerchant.API_KEY_PROPERTY
                + "\":\""
                + credentials.apiKey()
                + "\",\""
                + WorldlineMerchant.API_SECRET_PROPERTY
                + "\":\""
                + credentials.apiSecret()
                + "\"}}],\"webhooks\":[]}"));
    transport.send(
        CheckRequest.json(
            "POST",
            "/__simulator/worldline/fixtures/payment-methods",
            "{\"merchantId\":\""
                + ACCOUNT
                + "\",\"ownerId\":\"smoke-owner\",\"type\":\"card\",\"alias\":\"smoke-token\","
                + "\"card\":{\"brand\":\"visa\",\"last4\":\"1111\",\"expMonth\":12,\"expYear\":"
                + "2030,\"funding\":\"credit\"},\"providerIds\":{\"token\":\""
                + credentials.token()
                + "\"}}"));
    return credentials;
  }

  private CheckRequest aHold(Credentials credentials) {
    return signed(theHoldBody(credentials), credentials);
  }

  private CheckRequest aHoldUnder(Credentials credentials, String idempotenceKey) {
    return signed(
        theHoldBody(credentials)
            .carrying(
                WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
                idempotenceKey),
        credentials);
  }

  private static CheckRequest theHoldBody(Credentials credentials) {
    return CheckRequest.json(
        "POST",
        WorldlineApiController.thePaymentsOf(MERCHANT),
        "{\"order\":{\"amountOfMoney\":{\"amount\":"
            + THE_HELD_AMOUNT
            + ",\"currencyCode\":\""
            + THE_CURRENCY
            + "\"},\"references\":{\"merchantReference\":\"order-smoke\"}},"
            + "\"cardPaymentMethodSpecificInput\":{\"token\":\""
            + credentials.token()
            + "\",\"authorizationMode\":\"PRE_AUTHORIZATION\"}}");
  }

  private CheckRequest signed(CheckRequest request, Credentials credentials) {
    String date = aDate();
    String subject =
        WorldlineSignature.subjectOf(
            request.method(),
            request
                .headers()
                .getOrDefault(
                    WorldlineFields.CONTENT_TYPE, WorldlineSignature.THE_EMPTY_CONTENT_TYPE),
            date,
            WorldlineSignature.canonicalHeaderLinesOf(asSentHeaders(request)),
            request.path());
    return request
        .carrying(WorldlineFields.DATE, date)
        .carrying(
            WorldlineFields.AUTHORIZATION,
            WorldlineSignature.SCHEME
                + credentials.apiKey()
                + WorldlineSignature.FIELD_SEPARATOR
                + WorldlineSignature.signed(credentials.apiSecret(), subject));
  }

  private static Map<String, List<String>> asSentHeaders(CheckRequest request) {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    request.headers().forEach((name, value) -> sent.put(name, List.of(value)));
    return sent;
  }

  private String aDate() {
    return WorldlineSignature.asWorldlineWritesIt(clock.now(SmokeNamespace.RUN));
  }

  private static String theSeparatorIn(String formula) {
    return formula.substring(THE_PAYID.length(), formula.length() - THE_PAYIDSUB.length());
  }

  private @Nullable String payIdSubOf(@Nullable String paymentId) {
    if (paymentId == null) {
      return null;
    }
    Matcher payIdSub = aPayIdSub.matcher(paymentId);
    return payIdSub.find() ? payIdSub.group() : null;
  }
}
