package dev.marcinromanowski.pspsim.provider.autra;

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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AutraCompatibilityChecks {

  static final ProviderId AUTRA = AutraApi.AUTRA;

  static final String THE_TOKEN_IS_MINTED = "POST /v1/oauth/token";

  static final String THE_CREDENTIAL_IS_REFUSED = "POST /v1/oauth/token with a wrong clientSecret";

  static final String THE_SALE_IS_APPROVED = "POST /v1/acquiring/payments";

  static final String THE_SALE_IS_DECLINED = "POST /v1/acquiring/payments declined";

  static final String THE_CALLBACK_CARRIES = "POST acquiring webhook";

  static final String THE_SMOKE_SLUG = "slug-token-smoke";

  static final String THE_TRAIL_RECORDS = "GET /__simulator/autra/audit";

  private static final String ACCOUNT = "merchant-1";

  private static final String DOCUMENT = "25055662000190";

  private static final String THE_TOKEN_ROUTE =
      "/v1/oauth/token?grant_type=" + AutraFields.CLIENT_CREDENTIALS;

  private static final String THE_SALE_AMOUNT = "150.00";

  private static final String THE_PIX_AMOUNT = "99.90";

  private static final String THE_CURRENCY = "BRL";

  private static final int SECRET_LENGTH = 24;

  private static final String SIGNATURE = "X-Autra-Signature";

  private static final String USER_AGENT = "User-Agent";

  private static final String THE_IDENTIFIER_IN_ANY_CASE = "paymentid";

  private final CheckJson json;
  private final IdGenerator ids;
  private final VendorSamples autra;
  private final VendorSamples simulator;

  public AutraCompatibilityChecks(CheckJson json, IdGenerator ids) {
    this.json = json;
    this.ids = ids;
    this.autra = VendorSamples.of("autra");
    this.simulator = VendorSamples.of("simulator");
  }

  public List<ContractCheck> all() {
    return List.of(
        accessToken(),
        refusedCredential(),
        approvedSale(),
        declinedSale(),
        callbackSignature(),
        auditTrail());
  }

  private ContractCheck accessToken() {
    VendorSample thePublishedToken = autra.sample("oauth-token-ok");
    return DeclaredCheck.applying(
        AUTRA,
        THE_TOKEN_IS_MINTED,
        thePublishedToken.provenance(),
        List.of(
            new ContractExpectation("tokenType", thePublishedToken.json("/tokenType")),
            new ContractExpectation("expiresIn", thePublishedToken.json("/expiresIn"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse minted = transport.send(aTokenRequest(credentials.basic()));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "tokenType", json.at(minted.body(), "/" + AutraFields.TOKEN_TYPE));
          Observed.put(read, "expiresIn", json.at(minted.body(), "/" + AutraFields.EXPIRES_IN));
          return read;
        });
  }

  private ContractCheck refusedCredential() {
    VendorSample thePublishedRefusal = autra.sample("oauth-invalid-client-secret");
    return DeclaredCheck.applying(
        AUTRA,
        THE_CREDENTIAL_IS_REFUSED,
        thePublishedRefusal.provenance(),
        List.of(
            new ContractExpectation("httpStatus", thePublishedRefusal.rule("httpStatus")),
            new ContractExpectation("error", thePublishedRefusal.rule("error"))),
        transport -> {
          Credentials credentials = prepare(transport);
          CheckResponse refused =
              transport.send(
                  aTokenRequest(basicOf(credentials.clientId(), "not-the-secret-it-configured")));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(refused.status()));
          Observed.put(read, "error", json.at(refused.body(), "/" + AutraFields.OAUTH_ERROR));
          return read;
        });
  }

  private ContractCheck approvedSale() {
    VendorSample thePublishedSale = autra.sample("sale-approved");
    return DeclaredCheck.applying(
        AUTRA,
        THE_SALE_IS_APPROVED,
        thePublishedSale.provenance(),
        List.of(
            new ContractExpectation("status", thePublishedSale.json("/status")),
            new ContractExpectation("currency", thePublishedSale.json("/currency")),
            new ContractExpectation(
                "identifier", thePublishedSale.body().map(this::theIdentifierSpelling))),
        transport -> {
          Credentials credentials = prepare(transport);
          String bearer = aBearerToken(transport, credentials);
          CheckResponse sold = transport.send(aSale(transport, bearer));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "status", json.at(sold.body(), "/" + AutraFields.STATUS));
          Observed.put(read, "currency", json.at(sold.body(), "/" + AutraFields.CURRENCY));
          Observed.put(read, "identifier", theIdentifierSpelling(sold.body()));
          return read;
        });
  }

  private ContractCheck declinedSale() {
    VendorSample thePublishedDecline = autra.sample("sale-declined");
    return DeclaredCheck.applying(
        AUTRA,
        THE_SALE_IS_DECLINED,
        thePublishedDecline.provenance(),
        List.of(
            new ContractExpectation("httpStatus", thePublishedDecline.status()),
            new ContractExpectation("errors.code", thePublishedDecline.json("/errors/0/code")),
            new ContractExpectation("errors.msg", thePublishedDecline.json("/errors/0/msg"))),
        transport -> {
          Credentials credentials = prepare(transport);
          String bearer = aBearerToken(transport, credentials);
          transport.send(
              CheckRequest.json(
                  "POST",
                  "/__simulator/autra/scenarios",
                  "{\"priority\":0,\"match\":{\"operation\":\""
                      + AutraOperations.SALE
                      + "\"},\"action\":{\"type\":\"TERMINAL_FAILURE\",\"providerCode\":\""
                      + AutraErrors.INSUFFICIENT_FUNDS
                      + "\"}}"));
          CheckResponse declined = transport.send(aSale(transport, bearer));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(declined.status()));
          Observed.put(
              read,
              "errors.code",
              json.at(declined.body(), "/" + AutraFields.ERRORS + "/0/" + AutraFields.CODE));
          Observed.put(
              read,
              "errors.msg",
              json.at(declined.body(), "/" + AutraFields.ERRORS + "/0/" + AutraFields.MSG));
          return read;
        });
  }

  private ContractCheck callbackSignature() {
    VendorSample sent = autra.sample("acquiring-webhook-headers");
    return DeclaredCheck.applying(
        AUTRA,
        THE_CALLBACK_CARRIES,
        sent.provenance(),
        List.of(
            new ContractExpectation(
                "signature.prefix", sent.rule(SIGNATURE).map(AutraCompatibilityChecks::prefixOf)),
            new ContractExpectation("userAgent", sent.rule(USER_AGENT))),
        transport -> {
          Credentials credentials = prepare(transport);
          String bearer = aBearerToken(transport, credentials);
          Map<String, String> read = new LinkedHashMap<>();
          String pixId = aPixAwaitingItsPayer(transport, bearer);
          if (pixId != null) {
            transport.send(
                CheckRequest.json(
                    "POST",
                    "/__simulator/autra/hosted/" + pixId + "/complete",
                    "{\"outcome\":\"PAID\"}"));
            CheckResponse deliveries =
                transport.send(CheckRequest.get("/__simulator/autra/webhooks"));
            String signature =
                json.at(deliveries.body(), "/0/attempts/0/sentHeaders/" + SIGNATURE + "/0");
            Observed.put(read, "signature.prefix", prefixOf(signature));
            Observed.put(
                read,
                "userAgent",
                json.at(deliveries.body(), "/0/attempts/0/sentHeaders/" + USER_AGENT + "/0"));
          }
          return read;
        });
  }

  private ContractCheck auditTrail() {
    VendorSample auditRecord = simulator.sample("audit-record");
    return DeclaredCheck.applying(
        AUTRA,
        THE_TRAIL_RECORDS,
        auditRecord.provenance(),
        List.of(
            new ContractExpectation("audit.direction", auditRecord.json("/direction")),
            new ContractExpectation("audit.outcome", auditRecord.json("/outcome"))),
        transport -> {
          Credentials credentials = prepare(transport);
          transport.send(aTokenRequest(credentials.basic()));
          CheckResponse trail =
              transport.send(CheckRequest.get("/__simulator/autra/audit?origin=PROVIDER_API"));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "audit.direction", json.at(trail.body(), "/0/direction"));
          Observed.put(read, "audit.outcome", json.at(trail.body(), "/1/outcome"));
          return read;
        });
  }

  record Credentials(String clientId, String clientSecret) {

    String basic() {
      return basicOf(clientId, clientSecret);
    }
  }

  private Credentials prepare(CheckTransport transport) {
    Credentials credentials =
        new Credentials(
            "smoke-client-" + ids.hex(SECRET_LENGTH), "smoke-secret-" + ids.hex(SECRET_LENGTH));
    transport.send(
        CheckRequest.json("POST", "/__simulator/autra/reset", "{\"scope\":\"TEST_RUN\"}"));
    transport.send(
        CheckRequest.json(
            "PUT",
            "/__simulator/autra/config",
            "{\"accounts\":[{\"accountId\":\""
                + ACCOUNT
                + "\",\"configuration\":{\""
                + AutraMerchant.CLIENT_ID_PROPERTY
                + "\":\""
                + credentials.clientId()
                + "\",\""
                + AutraMerchant.CLIENT_SECRET_PROPERTY
                + "\":\""
                + credentials.clientSecret()
                + "\",\""
                + AutraMerchant.DOCUMENT_ID_PROPERTY
                + "\":\""
                + DOCUMENT
                + "\",\""
                + AutraWebhookSecret.SETTING
                + "\":\"smoke-webhook-secret-"
                + ids.hex(SECRET_LENGTH)
                + "\"}}],\"webhooks\":[{\"accountId\":\""
                + ACCOUNT
                + "\",\"url\":\"http://127.0.0.1:1/smoke\"}]}"));
    transport.send(
        CheckRequest.json(
            "POST",
            "/__simulator/autra/fixtures/payment-methods",
            "{\"merchantId\":\""
                + ACCOUNT
                + "\",\"ownerId\":\"smoke-owner\",\"type\":\"card\",\"alias\":\"smoke-card\","
                + "\"card\":{\"brand\":\"visa\",\"last4\":\"1111\",\"expMonth\":12,\"expYear\":"
                + "2030,\"funding\":\"credit\"},\"providerIds\":{\"token\":\""
                + THE_SMOKE_SLUG
                + "\"}}"));
    return credentials;
  }

  private String aBearerToken(CheckTransport transport, Credentials credentials) {
    CheckResponse minted = transport.send(aTokenRequest(credentials.basic()));
    return String.valueOf(json.at(minted.body(), "/" + AutraFields.ACCESS_TOKEN));
  }

  private CheckRequest aSale(CheckTransport transport, String bearer) {
    String transactionalToken = aSessionToken(transport, bearer, AutraApiController.INITIALIZE);
    return bearing(
        CheckRequest.json(
            "POST",
            AutraApiController.PAYMENTS,
            "{\"transactionalToken\":\""
                + transactionalToken
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\",\"transactionType\":\""
                + AutraFields.CREDIT
                + "\",\"amount\":"
                + THE_SALE_AMOUNT
                + ",\"currency\":\""
                + THE_CURRENCY
                + "\",\"installments\":1,\"tokenData\":{\""
                + AutraFields.SLUG_TOKEN
                + "\":\""
                + THE_SMOKE_SLUG
                + "\"}}"),
        bearer);
  }

  private @Nullable String aPixAwaitingItsPayer(CheckTransport transport, String bearer) {
    String transactionalToken = aSessionToken(transport, bearer, AutraApiController.PIX_INITIALIZE);
    CheckResponse created =
        transport.send(
            bearing(
                CheckRequest.json(
                    "POST",
                    AutraApiController.PIX_CHECKOUT,
                    "{\"transactionalToken\":\""
                        + transactionalToken
                        + "\",\"documentId\":\""
                        + DOCUMENT
                        + "\",\"amount\":"
                        + THE_PIX_AMOUNT
                        + ",\"currency\":\""
                        + THE_CURRENCY
                        + "\",\"description\":\"contract compatibility smoke check\"}"),
                bearer));
    return json.at(created.body(), "/" + AutraFields.PIX_ID);
  }

  private String aSessionToken(CheckTransport transport, String bearer, String route) {
    CheckResponse opened =
        transport.send(
            bearing(
                CheckRequest.json("POST", route, "{\"documentId\":\"" + DOCUMENT + "\"}"), bearer));
    return String.valueOf(json.at(opened.body(), "/" + AutraFields.TRANSACTIONAL_TOKEN));
  }

  private @Nullable String theIdentifierSpelling(String body) {
    if (!(json.read(body) instanceof Map<?, ?> members)) {
      return null;
    }
    for (Object member : members.keySet()) {
      String name = String.valueOf(member);
      if (THE_IDENTIFIER_IN_ANY_CASE.equals(name.toLowerCase(Locale.ROOT))) {
        return name;
      }
    }
    return null;
  }

  private static @Nullable String prefixOf(@Nullable String signature) {
    if (signature == null) {
      return null;
    }
    int separator = signature.indexOf('=');
    return separator < 0 ? null : signature.substring(0, separator + 1);
  }

  private static CheckRequest aTokenRequest(String basic) {
    return CheckRequest.json("POST", THE_TOKEN_ROUTE, "")
        .carrying(AutraFields.AUTHORIZATION, basic);
  }

  private static CheckRequest bearing(CheckRequest request, String bearer) {
    return request.carrying(AutraFields.AUTHORIZATION, AutraFields.BEARER + bearer);
  }

  private static String basicOf(String clientId, String clientSecret) {
    return AutraFields.BASIC
        + Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
  }
}
