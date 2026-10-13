package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
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

public final class GrowCompatibilityChecks {

  static final ProviderId GROW = ProviderId.of("grow");

  static final String CREATE_PAYMENT_PROCESS = "POST /api/light/server/1.0/createPaymentProcess";

  static final String THE_CONTROL_API_CREATES = "POST /__simulator/grow/scenarios";

  static final String THE_TRAIL_RECORDS = "GET /__simulator/grow/audit";

  static final String THE_CALLBACK_CARRIES = "POST notifyUrl";

  static final String THE_CALLBACK_IS_RESENT = "POST notifyUrl, resent";

  private static final String ACCOUNT = "merchant-1";

  private static final String USER_ID = "smoke-user";

  private static final String PAGE_CODE = "smoke-page";

  private static final String THE_CALLBACK_IS_UNSIGNED =
      "Grow signs no server-to-server callback, so a signature has nothing to be checked against";

  private final CheckJson json;
  private final VendorSamples grow;
  private final VendorSamples simulator;

  public GrowCompatibilityChecks(CheckJson json) {
    this.json = json;
    this.grow = VendorSamples.of("grow");
    this.simulator = VendorSamples.of("simulator");
  }

  public List<ContractCheck> all() {
    return List.of(
        happyPath(),
        decline(),
        credentials(),
        callbackSignature(),
        callbackResends(),
        controlApi(),
        auditTrail());
  }

  private ContractCheck callbackResends() {
    VendorSample resends = grow.sample("unacknowledged-update-resends");
    VendorSample schedule = grow.sample("server-update-retry-schedule");
    return DeclaredCheck.applying(
        GROW,
        THE_CALLBACK_IS_RESENT,
        resends.provenance(),
        List.of(
            new ContractExpectation("delivery.attemptCap", schedule.rule("attempts")),
            new ContractExpectation(
                "delivery.resendsAfterTheFirstAttempt", resends.rule("additionalTimes"))),
        transport -> {
          prepare(transport);
          CheckResponse created = transport.send(aHold());
          Map<String, String> read = new LinkedHashMap<>();
          String processId = json.at(created.body(), "/data/processId");
          if (processId != null) {
            transport.send(
                CheckRequest.json(
                    "POST",
                    "/__simulator/grow/webhooks",
                    "{\"providerObjectId\":\"" + processId + "\"}"));
            CheckResponse queued = transport.send(CheckRequest.get("/__simulator/grow/webhooks"));
            String cap = json.at(queued.body(), "/0/attemptCap");
            String made = json.at(queued.body(), "/0/attemptsMade");
            Observed.put(read, "delivery.attemptCap", cap);
            Observed.put(read, "delivery.resendsAfterTheFirstAttempt", theResendsLeftBy(cap, made));
          }
          return read;
        });
  }

  private static @Nullable String theResendsLeftBy(
      @Nullable String attemptCap, @Nullable String attemptsMade) {
    if (attemptCap == null || attemptsMade == null) {
      return null;
    }
    try {
      return String.valueOf(Integer.parseInt(attemptCap) - Integer.parseInt(attemptsMade));
    } catch (NumberFormatException notACount) {
      return null;
    }
  }

  private ContractCheck happyPath() {
    VendorSample thePublishedApproval = grow.sample("create-payment-process-approved");
    return DeclaredCheck.applying(
        GROW,
        CREATE_PAYMENT_PROCESS,
        thePublishedApproval.provenance(),
        List.of(
            new ContractExpectation(
                "httpStatus", grow.sample("every-answer-is-http-200").rule("httpStatus")),
            new ContractExpectation("status", thePublishedApproval.json("/status"))),
        transport -> {
          prepare(transport);
          CheckResponse created = transport.send(aHold());
          return observed(created, "/status");
        });
  }

  private ContractCheck decline() {
    VendorSample thePublishedDecline = grow.sample("token-charge-declined");
    return DeclaredCheck.applying(
        GROW,
        CREATE_PAYMENT_PROCESS,
        thePublishedDecline.provenance(),
        List.of(
            new ContractExpectation("status", thePublishedDecline.json("/status")),
            new ContractExpectation("err.id", thePublishedDecline.json("/err/id"))),
        transport -> {
          prepare(transport);
          transport.send(
              CheckRequest.json(
                  "POST",
                  "/__simulator/grow/scenarios",
                  "{\"priority\":100,\"match\":{\"operation\":\"createPaymentProcess\"},"
                      + "\"action\":{\"type\":\"TERMINAL_FAILURE\",\"providerCode\":"
                      + "\"expiredCard\"}}"));
          CheckResponse declined = transport.send(aHold());
          Map<String, String> read = observed(declined, "/status");
          Observed.put(read, "err.id", json.at(declined.body(), "/err/id"));
          return read;
        });
  }

  private ContractCheck credentials() {
    VendorSample thePublishedRefusal = grow.sample("create-payment-process-unknown-business");
    return DeclaredCheck.applying(
        GROW,
        CREATE_PAYMENT_PROCESS,
        thePublishedRefusal.provenance(),
        List.of(
            new ContractExpectation("httpStatus", thePublishedRefusal.status()),
            new ContractExpectation("err.id", thePublishedRefusal.json("/err/id"))),
        transport -> {
          prepare(transport);
          Map<String, String> form = new LinkedHashMap<>(theHoldForm());
          form.put(GrowFields.USER_ID, "no-account-answers-to-this");
          CheckResponse refused =
              transport.send(
                  CheckRequest.form("POST", "/api/light/server/1.0/createPaymentProcess", form));
          Map<String, String> read = observed(refused, "/status");
          Observed.put(read, "err.id", json.at(refused.body(), "/err/id"));
          return read;
        });
  }

  private ContractCheck callbackSignature() {
    return DeclaredCheck.notApplying(
        GROW,
        THE_CALLBACK_CARRIES,
        grow.sample("unsigned-callback").provenance(),
        THE_CALLBACK_IS_UNSIGNED);
  }

  private ContractCheck controlApi() {
    VendorSample scenarioCreated = simulator.sample("scenario-created");
    return DeclaredCheck.applying(
        GROW,
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
                      "/__simulator/grow/scenarios",
                      "{\"priority\":50,\"match\":{\"operation\":\"createPaymentProcess\"},"
                          + "\"action\":{\"type\":\"FIXED_SUCCESS\"}}"));
          CheckResponse fixture =
              transport.send(
                  CheckRequest.json(
                      "POST",
                      "/__simulator/grow/fixtures/payment-methods",
                      "{\"merchantId\":\""
                          + ACCOUNT
                          + "\",\"ownerId\":\"smoke-payer\","
                          + "\"type\":\"card\",\"alias\":\"smoke-4580\",\"card\":{\"brand\":"
                          + "\"visa\",\"last4\":\"4580\",\"expMonth\":12,\"expYear\":2030,"
                          + "\"funding\":\"credit\"}}"));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("scenario.httpStatus", String.valueOf(scenario.status()));
          read.put("fixture.httpStatus", String.valueOf(fixture.status()));
          return read;
        });
  }

  private ContractCheck auditTrail() {
    VendorSample auditRecord = simulator.sample("audit-record");
    return DeclaredCheck.applying(
        GROW,
        THE_TRAIL_RECORDS,
        auditRecord.provenance(),
        List.of(
            new ContractExpectation("audit.direction", auditRecord.json("/direction")),
            new ContractExpectation("audit.outcome", auditRecord.json("/outcome"))),
        transport -> {
          prepare(transport);
          transport.send(aHold());
          CheckResponse trail =
              transport.send(CheckRequest.get("/__simulator/grow/audit?origin=PROVIDER_API"));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "audit.direction", json.at(trail.body(), "/0/direction"));
          Observed.put(read, "audit.outcome", json.at(trail.body(), "/1/outcome"));
          return read;
        });
  }

  private void prepare(CheckTransport transport) {
    transport.send(
        CheckRequest.json("POST", "/__simulator/grow/reset", "{\"scope\":\"TEST_RUN\"}"));
    transport.send(
        CheckRequest.json(
            "PUT",
            "/__simulator/grow/config",
            "{\"accounts\":[{\"accountId\":\""
                + ACCOUNT
                + "\",\"configuration\":{\"userId\":\""
                + USER_ID
                + "\",\"pageCode\":\""
                + PAGE_CODE
                + "\"}}],\"webhooks\":"
                + "[{\"accountId\":\""
                + ACCOUNT
                + "\",\"url\":\"http://127.0.0.1:1/smoke\"}]}"));
  }

  private static CheckRequest aHold() {
    return CheckRequest.form("POST", "/api/light/server/1.0/createPaymentProcess", theHoldForm());
  }

  private static Map<String, String> theHoldForm() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put("chargeType", "2");
    form.put("sum", "120.50");
    form.put("description", "contract compatibility smoke check");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("pageField[fullName]", "Israel Israeli");
    form.put("pageField[phone]", "0505503144");
    return form;
  }

  private Map<String, String> observed(CheckResponse answer, String statusPointer) {
    Map<String, String> read = new LinkedHashMap<>();
    read.put("httpStatus", String.valueOf(answer.status()));
    String status = json.at(answer.body(), statusPointer);
    if (status != null) {
      read.put("status", status);
    }
    return read;
  }
}
