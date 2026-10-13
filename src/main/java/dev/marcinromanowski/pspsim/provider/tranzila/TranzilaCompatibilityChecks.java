package dev.marcinromanowski.pspsim.provider.tranzila;

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
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeNamespace;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSample;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSamples;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TranzilaCompatibilityChecks {

  static final ProviderId TRANZILA = TranzilaApi.TRANZILA;

  static final String THE_API_CHARGES = "POST /v1/transaction/credit_card/create debit";

  static final String THE_API_FINDS_NO_ORIGINAL =
      "POST /v1/transaction/credit_card/create credit of an unknown reference_txn_id";

  static final String THE_API_REFUSES_A_TOKEN =
      "POST /v1/transaction/credit_card/create with a wrong access token";

  static final String THE_TRAIL_RECORDS = "GET /__simulator/tranzila/audit";

  private static final String ACCOUNT = "account-1";

  private static final String TERMINAL = "smoketerminal";

  private static final String A_CARD = "4580210000004245";

  private static final String A_TOKEN_NO_SECRET_PRODUCED = "not-the-access-token";

  private static final int SECRET_LENGTH = 24;

  private static final int NONCE_LENGTH = 80;

  private static final String AN_UNKNOWN_ORIGINAL = "99999999";

  private final CheckJson json;
  private final IdGenerator ids;
  private final SimulatorClock clock;
  private final VendorSamples tranzila;
  private final VendorSamples simulator;

  public TranzilaCompatibilityChecks(CheckJson json, IdGenerator ids, SimulatorClock clock) {
    this.json = json;
    this.ids = ids;
    this.clock = clock;
    this.tranzila = VendorSamples.of("tranzila");
    this.simulator = VendorSamples.of("simulator");
  }

  public List<ContractCheck> all() {
    return List.of(charge(), noOriginal(), refusedToken(), auditTrail());
  }

  private ContractCheck charge() {
    VendorSample published = tranzila.sample("create-success");
    return DeclaredCheck.applying(
        TRANZILA,
        THE_API_CHARGES,
        published.provenance(),
        List.of(
            new ContractExpectation("httpStatus", published.status()),
            new ContractExpectation("error_code", published.json("/error_code")),
            new ContractExpectation(
                "transaction_result.processor_response_code",
                published.json("/transaction_result/processor_response_code")),
            new ContractExpectation(
                "transaction_result.txn_type", published.json("/transaction_result/txn_type")),
            new ContractExpectation(
                "transaction_result.tranmode", published.json("/transaction_result/tranmode"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse charged = transport.send(signed(aDebit(), credentials));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(charged.status()));
          Observed.put(read, "error_code", json.at(charged.body(), "/error_code"));
          Observed.put(
              read,
              "transaction_result.processor_response_code",
              json.at(charged.body(), "/transaction_result/processor_response_code"));
          Observed.put(
              read,
              "transaction_result.txn_type",
              json.at(charged.body(), "/transaction_result/txn_type"));
          Observed.put(
              read,
              "transaction_result.tranmode",
              json.at(charged.body(), "/transaction_result/tranmode"));
          return read;
        });
  }

  private ContractCheck noOriginal() {
    VendorSample published = tranzila.sample("original-transaction-not-found");
    return DeclaredCheck.applying(
        TRANZILA,
        THE_API_FINDS_NO_ORIGINAL,
        published.provenance(),
        List.of(
            new ContractExpectation("httpStatus", published.rule("httpStatus")),
            new ContractExpectation("error_code", published.rule("error_code"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse refused = transport.send(signed(aCreditOfNothing(), credentials));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(refused.status()));
          Observed.put(read, "error_code", json.at(refused.body(), "/error_code"));
          return read;
        });
  }

  private ContractCheck refusedToken() {
    VendorSample published = tranzila.sample("authentication-failed");
    return DeclaredCheck.applying(
        TRANZILA,
        THE_API_REFUSES_A_TOKEN,
        published.provenance(),
        List.of(
            new ContractExpectation("httpStatus", published.rule("httpStatus")),
            new ContractExpectation("error_code", published.rule("error_code"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse refused =
              transport.send(
                  aDebit()
                      .carrying(TranzilaFields.APP_KEY, credentials.appKey())
                      .carrying(TranzilaFields.REQUEST_TIME, aRequestTime())
                      .carrying(TranzilaFields.NONCE, ids.hex(NONCE_LENGTH))
                      .carrying(TranzilaFields.ACCESS_TOKEN, A_TOKEN_NO_SECRET_PRODUCED));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(refused.status()));
          Observed.put(read, "error_code", json.at(refused.body(), "/error_code"));
          return read;
        });
  }

  private ContractCheck auditTrail() {
    VendorSample published = simulator.sample("audit-record");
    return DeclaredCheck.applying(
        TRANZILA,
        THE_TRAIL_RECORDS,
        published.provenance(),
        List.of(
            new ContractExpectation("audit.direction", published.json("/direction")),
            new ContractExpectation("audit.outcome", published.json("/outcome"))),
        transport -> {
          Credentials credentials = prepare(transport);
          transport.send(signed(aDebit(), credentials));
          CheckResponse trail =
              transport.send(CheckRequest.get("/__simulator/tranzila/audit?origin=PROVIDER_API"));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "audit.direction", json.at(trail.body(), "/0/direction"));
          Observed.put(read, "audit.outcome", json.at(trail.body(), "/1/outcome"));
          return read;
        });
  }

  record Credentials(String appKey, String appSecret) {}

  private Credentials prepare(CheckTransport transport) {
    Credentials credentials =
        new Credentials(
            "smoke-app-key-" + ids.hex(SECRET_LENGTH),
            "smoke-app-secret-" + ids.hex(SECRET_LENGTH));
    transport.send(
        CheckRequest.json("POST", "/__simulator/tranzila/reset", "{\"scope\":\"TEST_RUN\"}"));
    transport.send(
        CheckRequest.json(
            "PUT",
            "/__simulator/tranzila/config",
            "{\"accounts\":[{\"accountId\":\""
                + ACCOUNT
                + "\",\"configuration\":{\""
                + TranzilaTerminal.TERMINAL_NAME_PROPERTY
                + "\":\""
                + TERMINAL
                + "\",\""
                + TranzilaTerminal.APP_KEY_PROPERTY
                + "\":\""
                + credentials.appKey()
                + "\",\""
                + TranzilaTerminal.APP_SECRET_PROPERTY
                + "\":\""
                + credentials.appSecret()
                + "\"}}],\"webhooks\":[]}"));
    return credentials;
  }

  private static CheckRequest aDebit() {
    return CheckRequest.json(
        "POST",
        TranzilaApiController.CREATE,
        "{\"terminal_name\":\""
            + TERMINAL
            + "\",\"txn_type\":\"debit\",\"card_number\":\""
            + A_CARD
            + "\",\"expire_month\":7,\"expire_year\":2030,\"items\":[{\"name\":\"smoke\","
            + "\"units_number\":1,\"unit_price\":10.53}]}");
  }

  private static CheckRequest aCreditOfNothing() {
    return CheckRequest.json(
        "POST",
        TranzilaApiController.CREATE,
        "{\"terminal_name\":\""
            + TERMINAL
            + "\",\"txn_type\":\"credit\",\"card_number\":\""
            + A_CARD
            + "\",\"expire_month\":7,\"expire_year\":2030,\"reference_txn_id\":"
            + AN_UNKNOWN_ORIGINAL
            + ",\"authorization_number\":\"0000000\",\"items\":[{\"name\":\"smoke\","
            + "\"units_number\":1,\"unit_price\":10.53}]}");
  }

  private CheckRequest signed(CheckRequest request, Credentials credentials) {
    String requestTime = aRequestTime();
    String nonce = ids.hex(NONCE_LENGTH);
    return request
        .carrying(TranzilaFields.APP_KEY, credentials.appKey())
        .carrying(TranzilaFields.REQUEST_TIME, requestTime)
        .carrying(TranzilaFields.NONCE, nonce)
        .carrying(
            TranzilaFields.ACCESS_TOKEN,
            TranzilaAccessToken.AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT.expectedFor(
                credentials.appKey(), credentials.appSecret(), requestTime, nonce));
  }

  private String aRequestTime() {
    return Long.toString(clock.now(SmokeNamespace.RUN).getEpochSecond());
  }
}
