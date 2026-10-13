package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.TheAccountARowNames;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.RenderedResponse;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunCandidates;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolution;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AzericardGateway {

  public static final ProviderId AZERICARD = ProviderId.of("azericard");

  static final int GATEWAY_HTTP_STATUS = 200;

  static final String WIRE_METHOD = "POST";

  private static final int AUDIT_ID_LENGTH = 24;

  private final AzericardTerminals terminals;
  private final TestRuns testRuns;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final TestRunId defaultTestRun;
  private final Authorize authorize;
  private final CompleteOrReverse completeOrReverse;
  private final LookUpStatus lookUpStatus;
  private final AzericardPayerCheckout checkout;

  AzericardGateway(
      AzericardTerminals terminals,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      TestRunId defaultTestRun,
      Authorize authorize,
      CompleteOrReverse completeOrReverse,
      LookUpStatus lookUpStatus,
      AzericardPayerCheckout checkout) {
    this.terminals = terminals;
    this.testRuns = testRuns;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.defaultTestRun = defaultTestRun;
    this.authorize = authorize;
    this.completeOrReverse = completeOrReverse;
    this.lookUpStatus = lookUpStatus;
    this.checkout = checkout;
  }

  public AzericardApiResponse cgiLink(AzericardApiCall call) {
    return answer(AzericardRoute.CGI_LINK, call);
  }

  public AzericardApiResponse tokenLink(AzericardApiCall call) {
    return answer(AzericardRoute.TOKEN_CGI_LINK, call);
  }

  private AzericardApiResponse answer(AzericardRoute route, AzericardApiCall call) {
    AzericardMessage message = AzericardMessage.of(route, call.form());
    List<AzericardTerminal> matches = accountsByTerminal(message);
    TestRunResolution resolution = resolve(call, matches);
    Instant now = clock.now(resolution.testRunId());

    List<AzericardTerminal> within = within(resolution, matches);
    String account =
        TheAccountARowNames.amongTheAccountsTheCredentialsMatched(
            within.stream().map(AzericardTerminal::accountId).toList());

    recordInbound(call, resolution, account, now, message);
    AzericardOperationResult result =
        answered(message, call.headers(), resolution.testRunId(), within, now);
    FaultedDelivery delivery = deliveryOf(result);
    recordOutbound(call, resolution, account, now, result, delivery);

    return AzericardApiResponse.ofTheGateway(
        resolution.testRunId(),
        delivery,
        result.outcome(),
        result.providerObjectId(),
        result.body());
  }

  private AzericardOperationResult answered(
      AzericardMessage message,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      List<AzericardTerminal> matches,
      Instant now) {
    AzericardRequiredField unusable = message.unusableField();
    if (unusable != null) {
      return AzericardOperationResult.failed(
          AzericardRefusals.theMessageWasRefused(), AzericardErrorPage.naming(unusable));
    }
    if (matches.size() != 1) {
      return AzericardOperationResult.failed(
          AzericardRefusals.theMessageReachesNoTerminal(),
          AzericardErrorPage.naming(AzericardFields.REQUIRED_TERMINAL));
    }
    AzericardTerminal terminal = matches.get(0);
    if (!AzericardSignature.verifies(
        theSourceThisTerminalSignsIn(message, terminal), message.pSign(), terminal.merchantKey())) {
      return accessDenied(message, terminal, now);
    }
    if (!AzericardTimestamp.withinTheWindow(message.sentAt(), now)) {
      return accessDenied(message, terminal, now);
    }
    if (message.transactionType().looksUpThePayment()) {
      return lookUpStatus.handle(message, headers, testRunId, terminal, now);
    }
    if (message.collectsTheCardFromThePayer()) {
      return checkout.handOff(message, headers, testRunId, terminal, now);
    }
    if (message.transactionType().createsThePayment()) {
      return message.route() == AzericardRoute.TOKEN_CGI_LINK
          ? checkout.authorizeCallingBack(message, headers, testRunId, terminal, now)
          : authorize.handle(message, headers, testRunId, terminal, now, ObjectOrigin.PROVIDER_API);
    }
    return completeOrReverse.handle(message, headers, testRunId, terminal, now);
  }

  private static byte[] theSourceThisTerminalSignsIn(
      AzericardMessage message, AzericardTerminal terminal) {
    return terminal.settlementMac() == AzericardSettlementMac.PUBLISHED_EXAMPLE
        ? message.macSourceAsTheManualPublishesIt()
        : message.macSource();
  }

  private static AzericardOperationResult accessDenied(
      AzericardMessage message, AzericardTerminal terminal, Instant now) {
    return AzericardOperationResult.failed(
        AzericardRefusals.theMessageReachesNoTerminal(), accessDeniedBody(message, terminal, now));
  }

  private static AzericardBody accessDeniedBody(
      AzericardMessage message, AzericardTerminal terminal, Instant now) {
    if (message.transactionType().looksUpThePayment()) {
      return AzericardStatusResponse.refusing(
              message,
              AzericardOutcomes.ACTION_ERROR,
              AzericardOutcomes.RC_ACCESS_DENIED,
              AzericardOutcomes.DESC_ACCESS_DENIED,
              AzericardTimestamp.write(now))
          .signedWith(terminal.gatewayKey());
    }
    return new AzericardResponse(
            AzericardOutcomes.DESC_ACCESS_DENIED,
            message.terminal(),
            message.trtype(),
            message.order(),
            message.amount(),
            AzericardOutcomes.ABSENT,
            AzericardOutcomes.ACTION_ERROR,
            AzericardOutcomes.RC_ACCESS_DENIED,
            AzericardOutcomes.ABSENT,
            message.rrnAsSentOrNothing(),
            message.intRefAsSentOrNothing(),
            AzericardTimestamp.write(now),
            message.nonce())
        .signedWith(terminal.gatewayKey());
  }

  private static FaultedDelivery deliveryOf(AzericardOperationResult result) {
    FaultedDelivery insteadOfTheOperation = result.insteadOfTheOperation();
    if (insteadOfTheOperation != null) {
      return insteadOfTheOperation;
    }
    AzericardBody body = result.body();
    RenderedResponse rendered =
        new RenderedResponse(GATEWAY_HTTP_STATUS, body.contentType(), body.bytes());
    TransportFault fault = result.fault();
    return fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
  }

  private TestRunResolution resolve(AzericardApiCall call, List<AzericardTerminal> matches) {
    List<TestRunCandidates> evaluated = new ArrayList<>();
    evaluated.add(TestRunHeader.in(call.headers(), testRuns::exists));
    evaluated.add(call.route().candidatesFor(AZERICARD));
    evaluated.add(
        TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, testRunsOf(matches)));
    return TestRunResolution.of(evaluated, defaultTestRun);
  }

  private List<AzericardTerminal> accountsByTerminal(AzericardMessage message) {
    String terminalId = message.terminalAsSent();
    if (terminalId == null) {
      return List.of();
    }
    return terminals.byTerminalId(terminalId);
  }

  private static List<AzericardTerminal> within(
      TestRunResolution resolution, List<AzericardTerminal> matches) {
    return resolution
        .resolvedTestRun()
        .map(testRunId -> accountsOf(testRunId, matches))
        .orElseGet(List::of);
  }

  private static List<AzericardTerminal> accountsOf(
      TestRunId testRunId, List<AzericardTerminal> matches) {
    return matches.stream().filter(terminal -> terminal.testRunId().equals(testRunId)).toList();
  }

  private static List<TestRunId> testRunsOf(List<AzericardTerminal> matches) {
    return matches.stream().map(AzericardTerminal::testRunId).toList();
  }

  private void recordInbound(
      AzericardApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      AzericardMessage message) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            AZERICARD,
            accountId,
            ObjectOrigin.PROVIDER_API,
            AuditDirection.INBOUND_REQUEST,
            null,
            call.method(),
            call.path(),
            null,
            null,
            null,
            null,
            1,
            now,
            Redaction.redact(call.headers(), AzericardFields.SENSITIVE_HEADERS),
            Redaction.redact(message.form().forAudit(), AzericardFields.SENSITIVE_REQUEST_FIELDS),
            null));
  }

  private void recordOutbound(
      AzericardApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      AzericardOperationResult result,
      FaultedDelivery delivery) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            AZERICARD,
            accountId,
            ObjectOrigin.PROVIDER_API,
            AuditDirection.OUTBOUND_RESPONSE,
            result.outcome(),
            call.method(),
            call.path(),
            delivery.httpStatus(),
            result.providerObjectId(),
            result.refusal(),
            result.scenarioId(),
            1,
            now,
            DeliveredHeaders.of(delivery),
            Redaction.redact(result.body().fields(), AzericardFields.SENSITIVE_RESPONSE_FIELDS),
            new DeliveredResponse(
                delivery.fault(),
                delivery.declaredContentLength(),
                delivery.writtenBodyBytes(),
                delivery.delay())));
  }

  private String auditId() {
    return "aud_" + ids.hex(AUDIT_ID_LENGTH);
  }
}
