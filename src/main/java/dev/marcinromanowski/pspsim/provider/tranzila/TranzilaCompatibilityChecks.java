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
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TranzilaCompatibilityChecks {

  static final ProviderId TRANZILA = TranzilaApi.TRANZILA;

  static final String THE_API_CHARGES = "POST /v1/transaction/credit_card/create debit";

  static final String THE_API_FINDS_NO_ORIGINAL =
      "POST /v1/transaction/credit_card/create credit of an unknown reference_txn_id";

  static final String THE_API_REFUSES_A_TOKEN =
      "POST /v1/transaction/credit_card/create with a wrong access token";

  static final String THE_TRAIL_RECORDS = "GET /__simulator/tranzila/audit";

  static final String THE_HANDSHAKE_LOCKS_A_SUM = "POST /v2/handshake/create";

  static final String THE_HANDSHAKE_REFUSES_A_TOKEN =
      "POST /v2/handshake/create with a wrong access token";

  static final String THE_PAGE_RETURNS_THE_PAYER =
      "GET /tranzila/directng/{terminal}/iframenew.php tranmode A, paid with the published card";

  static final String THE_HOSTED_FIELDS_CHARGE =
      "POST /tranzila/hf/process/ajax_process_transaction tran_mode A, a published test card";

  static final String THE_HOSTED_FIELDS_REFUSE_A_PLAN =
      "POST /tranzila/hf/process/ajax_process_transaction payment_plan 6";

  static final List<String> THE_RESPONSE_FIELDS_THE_HOSTED_FIELDS_SAMPLE_PUBLISHES =
      List.of(
          "success",
          TranzilaFields.PROCESSOR_RESPONSE_CODE,
          TranzilaFields.CURRENCY_CODE,
          TranzilaFields.CARD_LOCALITY,
          TranzilaFields.TXN_TYPE,
          TranzilaFields.TRANMODE,
          TranzilaFields.PAYMENT_PLAN);

  static final String THE_SHOPS_ADDRESS = "https://shop.invalid/paid";

  static final String THE_PAGE =
      "/tranzila/directng/smoketerminal/iframenew.php?sum=10.53&currency=1&tranmode=A"
          + "&success_url_address="
          + THE_SHOPS_ADDRESS;

  static final List<String> THE_FIELDS_THE_SAMPLE_PUBLISHES =
      List.of(TranzilaFields.RESPONSE, TranzilaFields.TXN_TYPE, TranzilaFields.TRANMODE);

  private static final Pattern THE_PAYER_FORM =
      Pattern.compile("action=\"(/tranzila/directng/[^\"/]+/payer/[^\"/]+)\"");

  private static final Pattern A_RETURNED_FIELD =
      Pattern.compile("<input type=\"hidden\" name=\"([^\"]*)\" value=\"([^\"]*)\">");

  private static final String ACCOUNT = "account-1";

  private static final String TERMINAL = "smoketerminal";

  private static final String A_CARD = "4580210000004245";

  private static final String A_PUBLISHED_TEST_CARD = "4907639999909022";

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
    return List.of(
        charge(),
        noOriginal(),
        refusedToken(),
        auditTrail(),
        handshake(),
        handshakeRefused(),
        pageReturn(),
        hostedFieldsCharge(),
        hostedFieldsRefuseAPlan());
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

  private ContractCheck handshake() {
    VendorSample published = tranzila.sample("handshake-created");
    return DeclaredCheck.applying(
        TRANZILA,
        THE_HANDSHAKE_LOCKS_A_SUM,
        published.provenance(),
        List.of(
            new ContractExpectation("httpStatus", published.status()),
            new ContractExpectation("error_code", published.json("/error_code")),
            new ContractExpectation("message", published.json("/message"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse created = transport.send(signed(aHandshake(), credentials));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(created.status()));
          Observed.put(read, "error_code", json.at(created.body(), "/error_code"));
          Observed.put(read, "message", json.at(created.body(), "/message"));
          return read;
        });
  }

  private ContractCheck handshakeRefused() {
    VendorSample published = tranzila.sample("handshake-unauthorized");
    return DeclaredCheck.applying(
        TRANZILA,
        THE_HANDSHAKE_REFUSES_A_TOKEN,
        published.provenance(),
        List.of(
            new ContractExpectation("httpStatus", published.status()),
            new ContractExpectation("error_code", published.json("/error_code")),
            new ContractExpectation("message", published.json("/message"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse refused =
              transport.send(
                  aHandshake()
                      .carrying(TranzilaFields.APP_KEY, credentials.appKey())
                      .carrying(TranzilaFields.REQUEST_TIME, aRequestTime())
                      .carrying(TranzilaFields.NONCE, ids.hex(NONCE_LENGTH))
                      .carrying(TranzilaFields.ACCESS_TOKEN, A_TOKEN_NO_SECRET_PRODUCED));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(refused.status()));
          Observed.put(read, "error_code", json.at(refused.body(), "/error_code"));
          Observed.put(read, "message", json.at(refused.body(), "/message"));
          return read;
        });
  }

  private ContractCheck pageReturn() {
    VendorSample published = tranzila.sample("directng-return");
    return DeclaredCheck.applying(
        TRANZILA,
        THE_PAGE_RETURNS_THE_PAYER,
        published.provenance(),
        List.of(
            new ContractExpectation(
                TranzilaFields.RESPONSE, published.rule(TranzilaFields.RESPONSE)),
            new ContractExpectation(
                TranzilaFields.TXN_TYPE, published.rule(TranzilaFields.TXN_TYPE)),
            new ContractExpectation(
                TranzilaFields.TRANMODE, published.rule(TranzilaFields.TRANMODE))),
        transport -> {
          prepare(transport);
          return theReturnedFields(transport);
        });
  }

  private ContractCheck hostedFieldsCharge() {
    VendorSample published = tranzila.sample("hosted-fields-charged");
    List<ContractExpectation> expected = new ArrayList<>();
    for (String field : THE_RESPONSE_FIELDS_THE_HOSTED_FIELDS_SAMPLE_PUBLISHES) {
      expected.add(
          new ContractExpectation(
              TranzilaFields.TRANSACTION_RESPONSE + "." + field,
              published.json("/" + TranzilaFields.TRANSACTION_RESPONSE + "/" + field)));
    }
    return DeclaredCheck.applying(
        TRANZILA,
        THE_HOSTED_FIELDS_CHARGE,
        published.provenance(),
        expected,
        transport -> {
          CheckResponse charged = aHostedFieldsCharge(transport, Map.of());
          Map<String, String> read = new LinkedHashMap<>();
          for (String field : THE_RESPONSE_FIELDS_THE_HOSTED_FIELDS_SAMPLE_PUBLISHES) {
            Observed.put(
                read,
                TranzilaFields.TRANSACTION_RESPONSE + "." + field,
                json.at(charged.body(), "/" + TranzilaFields.TRANSACTION_RESPONSE + "/" + field));
          }
          return read;
        });
  }

  private ContractCheck hostedFieldsRefuseAPlan() {
    VendorSample published = tranzila.sample("hosted-fields-payment-plan");
    return DeclaredCheck.applying(
        TRANZILA,
        THE_HOSTED_FIELDS_REFUSE_A_PLAN,
        published.provenance(),
        List.of(
            new ContractExpectation("code", published.rule("code")),
            new ContractExpectation(
                TranzilaFields.MESSAGE, published.rule(TranzilaFields.MESSAGE))),
        transport -> {
          CheckResponse refused =
              aHostedFieldsCharge(transport, Map.of(TranzilaFields.PAYMENT_PLAN, "6"));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "code", json.at(refused.body(), "/errors/0/code"));
          Observed.put(read, TranzilaFields.MESSAGE, json.at(refused.body(), "/errors/0/message"));
          return read;
        });
  }

  private CheckResponse aHostedFieldsCharge(CheckTransport transport, Map<String, String> more) {
    Credentials credentials = prepare(transport);
    CheckResponse created = transport.send(signed(aHandshake(), credentials));
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(TranzilaFields.TERMINAL_NAME, TERMINAL);
    fields.put(TranzilaFields.THTK, String.valueOf(json.at(created.body(), "/thtk")));
    fields.put(TranzilaFields.AMOUNT, "10.53");
    fields.put(TranzilaFields.CREDIT_CARD_NUMBER, A_PUBLISHED_TEST_CARD);
    fields.put(TranzilaHostedFieldsRequest.EXPIRY_MONTH, "12");
    fields.put(TranzilaHostedFieldsRequest.EXPIRY_YEAR, "2030");
    fields.putAll(more);
    return transport.send(
        CheckRequest.form(
            TranzilaObjectActions.WIRE_METHOD, TranzilaHostedFieldsPage.PROCESS_ROUTE, fields));
  }

  private static Map<String, String> theReturnedFields(CheckTransport transport) {
    CheckResponse page = transport.send(CheckRequest.get(THE_PAGE));
    Matcher form = THE_PAYER_FORM.matcher(page.body());
    Map<String, String> read = new LinkedHashMap<>();
    if (form.find()) {
      Map<String, String> paying = new LinkedHashMap<>();
      paying.put(TranzilaDirectNgPage.CARD, TranzilaPageCards.THE_PUBLISHED_CARD);
      paying.put(TranzilaDirectNgPage.OUTCOME, PayerOutcome.PAID.name());
      CheckResponse returned =
          transport.send(
              CheckRequest.form(TranzilaObjectActions.WIRE_METHOD, form.group(1), paying));
      Matcher field = A_RETURNED_FIELD.matcher(returned.body());
      while (field.find()) {
        read.put(field.group(1), field.group(2));
      }
      read.keySet().retainAll(THE_FIELDS_THE_SAMPLE_PUBLISHES);
    }
    return read;
  }

  private static CheckRequest aHandshake() {
    return CheckRequest.json(
        "POST",
        TranzilaApiController.CREATE_HANDSHAKE,
        "{\"terminal_name\":\"" + TERMINAL + "\",\"sum\":10.53}");
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
