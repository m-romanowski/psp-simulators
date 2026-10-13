package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
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
import java.util.Optional;
import java.util.function.BiFunction;
import org.jspecify.annotations.Nullable;

public final class AutraApi {

  public static final ProviderId AUTRA = ProviderId.of("autra");

  static final int UNAUTHORIZED_STATUS = 401;

  static final String THE_BEARER_NAMES_NO_SESSION =
      "the Authorization bearer names no access token this tenant issued, or the one it names has"
          + " expired";

  private static final int AUDIT_ID_LENGTH = 24;

  private final AutraMerchants merchants;
  private final AutraSessions sessions;
  private final TestRuns testRuns;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final TestRunId defaultTestRun;
  private final IssueAccessToken issueAccessToken;
  private final OpenAcquiringSession openAcquiringSession;
  private final OpenCardSession openCardSession;
  private final ChargeStoredCard chargeStoredCard;
  private final PreAuthorize preAuthorize;
  private final CaptureHold captureHold;
  private final ReverseOrRelease reverseOrRelease;
  private final CreatePixCheckout createPixCheckout;
  private final LookUpPayment lookUpPayment;
  private final ListPayments listPayments;

  AutraApi(
      AutraMerchants merchants,
      AutraSessions sessions,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      TestRunId defaultTestRun,
      IssueAccessToken issueAccessToken,
      OpenAcquiringSession openAcquiringSession,
      OpenCardSession openCardSession,
      ChargeStoredCard chargeStoredCard,
      PreAuthorize preAuthorize,
      CaptureHold captureHold,
      ReverseOrRelease reverseOrRelease,
      CreatePixCheckout createPixCheckout,
      LookUpPayment lookUpPayment,
      ListPayments listPayments) {
    this.merchants = merchants;
    this.sessions = sessions;
    this.testRuns = testRuns;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.defaultTestRun = defaultTestRun;
    this.issueAccessToken = issueAccessToken;
    this.openAcquiringSession = openAcquiringSession;
    this.openCardSession = openCardSession;
    this.chargeStoredCard = chargeStoredCard;
    this.preAuthorize = preAuthorize;
    this.captureHold = captureHold;
    this.reverseOrRelease = reverseOrRelease;
    this.createPixCheckout = createPixCheckout;
    this.lookUpPayment = lookUpPayment;
    this.listPayments = listPayments;
  }

  public AutraApiResponse token(AutraApiCall call) {
    String clientId = IssueAccessToken.clientIdIn(call).orElse("");
    List<AutraMerchant> byCredential = merchants.byClientId(clientId);
    TestRunResolution resolution = resolve(call, testRunsOf(byCredential));
    Instant now = clock.now(resolution.testRunId());
    AutraMerchant candidate = within(resolution, byCredential);
    String account = theAccountOf(candidate);
    recordInbound(call, resolution, account, now);
    AutraOperationResult result =
        issueAccessToken.handle(call, resolution.testRunId(), candidate, now);
    return delivered(call, resolution, account, now, result);
  }

  public AutraApiResponse initialize(AutraApiCall call) {
    return acquiring(
        call,
        (merchant, at) ->
            openAcquiringSession.handle(
                call, call.headers(), at.testRunId(), merchant, at.now(), AutraSessionKind.CARD));
  }

  public AutraApiResponse pixInitialize(AutraApiCall call) {
    return acquiring(
        call,
        (merchant, at) ->
            openAcquiringSession.handle(
                call, call.headers(), at.testRunId(), merchant, at.now(), AutraSessionKind.PIX));
  }

  public AutraApiResponse pixCheckout(AutraApiCall call) {
    return acquiring(
        call,
        (merchant, at) ->
            createPixCheckout.handle(
                call,
                call.headers(),
                at.testRunId(),
                merchant,
                at.now(),
                ObjectOrigin.PROVIDER_API));
  }

  public AutraApiResponse cardSession(AutraApiCall call) {
    return acquiring(
        call,
        (merchant, at) ->
            openCardSession.handle(
                call,
                call.headers(),
                at.testRunId(),
                merchant,
                at.now(),
                ObjectOrigin.PROVIDER_API));
  }

  public AutraApiResponse sale(AutraApiCall call) {
    return acquiring(
        call,
        (merchant, at) ->
            chargeStoredCard.handle(
                call,
                call.headers(),
                at.testRunId(),
                merchant,
                at.now(),
                ObjectOrigin.PROVIDER_API));
  }

  public AutraApiResponse preAuthorization(AutraApiCall call) {
    return acquiring(
        call,
        (merchant, at) ->
            preAuthorize.handle(
                call,
                call.headers(),
                at.testRunId(),
                merchant,
                at.now(),
                ObjectOrigin.PROVIDER_API));
  }

  public AutraApiResponse capture(AutraApiCall call, String paymentId) {
    return acquiring(
        call,
        (merchant, at) ->
            captureHold.handle(
                call, call.headers(), at.testRunId(), merchant, paymentId, at.now()));
  }

  public AutraApiResponse voidPayment(AutraApiCall call, String paymentId) {
    return acquiring(
        call,
        (merchant, at) ->
            reverseOrRelease.handle(
                call, call.headers(), at.testRunId(), merchant, paymentId, at.now()));
  }

  public AutraApiResponse payment(AutraApiCall call, String paymentId) {
    return acquiring(
        call, (merchant, at) -> lookUpPayment.handle(call, at.testRunId(), merchant, paymentId));
  }

  public AutraApiResponse payments(AutraApiCall call) {
    return acquiring(
        call, (merchant, at) -> listPayments.handle(call, at.testRunId(), merchant, at.now()));
  }

  private AutraApiResponse acquiring(
      AutraApiCall call, BiFunction<AutraMerchant, AtThisMoment, AutraOperationResult> operation) {
    Optional<AutraAccessToken> bearer = theBearerOf(call);
    TestRunResolution resolution =
        resolve(call, bearer.map(token -> List.of(token.testRunId())).orElseGet(List::of));
    Instant now = clock.now(resolution.testRunId());
    Optional<AutraMerchant> merchant =
        bearer
            .filter(token -> !token.hasExpiredBy(now))
            .filter(token -> token.testRunId().equals(resolution.testRunId()))
            .flatMap(token -> merchants.find(token.testRunId(), token.accountId()));
    String account = merchant.map(AutraMerchant::accountId).orElse(null);
    recordInbound(call, resolution, account, now);
    AutraOperationResult result =
        merchant.isEmpty()
            ? unauthorized()
            : operation.apply(merchant.get(), new AtThisMoment(resolution.testRunId(), now));
    return delivered(call, resolution, account, now, result);
  }

  private Optional<AutraAccessToken> theBearerOf(AutraApiCall call) {
    return call.header(AutraFields.AUTHORIZATION)
        .filter(sent -> sent.startsWith(AutraFields.BEARER))
        .map(sent -> sent.substring(AutraFields.BEARER.length()))
        .flatMap(sessions::accessToken);
  }

  private static AutraOperationResult unauthorized() {
    return AutraOperationResult.failed(
        AutraRefusals.theCredentialWasRefused(),
        AutraBody.of(
            UNAUTHORIZED_STATUS,
            AutraErrors.envelope(AutraErrors.UNAUTHORIZED, THE_BEARER_NAMES_NO_SESSION)));
  }

  private AutraApiResponse delivered(
      AutraApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      AutraOperationResult result) {
    FaultedDelivery delivery = deliveryOf(result);
    recordOutbound(call, resolution, accountId, now, result, delivery);
    return new AutraApiResponse(
        resolution.testRunId(), delivery, result.outcome(), result.providerObjectId());
  }

  private static FaultedDelivery deliveryOf(AutraOperationResult result) {
    FaultedDelivery insteadOfTheOperation = result.insteadOfTheOperation();
    if (insteadOfTheOperation != null) {
      return insteadOfTheOperation;
    }
    AutraBody body = result.body();
    RenderedResponse rendered =
        new RenderedResponse(body.httpStatus(), String.valueOf(body.contentType()), body.bytes());
    TransportFault fault = result.fault();
    return fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
  }

  private TestRunResolution resolve(AutraApiCall call, List<TestRunId> byCredential) {
    List<TestRunCandidates> evaluated = new ArrayList<>();
    evaluated.add(TestRunHeader.in(call.headers(), testRuns::exists));
    evaluated.add(call.route().candidatesFor(AUTRA));
    evaluated.add(TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, byCredential));
    return TestRunResolution.of(evaluated, defaultTestRun);
  }

  private static List<TestRunId> testRunsOf(List<AutraMerchant> matches) {
    return matches.stream().map(AutraMerchant::testRunId).toList();
  }

  private static @Nullable AutraMerchant within(
      TestRunResolution resolution, List<AutraMerchant> matches) {
    Optional<TestRunId> resolved = resolution.resolvedTestRun();
    if (resolved.isEmpty()) {
      return null;
    }
    return matches.stream()
        .filter(merchant -> merchant.testRunId().equals(resolved.get()))
        .findFirst()
        .orElse(null);
  }

  private static @Nullable String theAccountOf(@Nullable AutraMerchant merchant) {
    return Optional.ofNullable(merchant).map(AutraMerchant::accountId).orElse(null);
  }

  private void recordInbound(
      AutraApiCall call, TestRunResolution resolution, @Nullable String accountId, Instant now) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            AUTRA,
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
            Redaction.redact(call.headers(), AutraFields.SENSITIVE_HEADERS),
            Redaction.redact(theRequestFieldsOf(call), AutraFields.SENSITIVE_REQUEST_FIELDS),
            null));
  }

  static Map<String, List<String>> theRequestFieldsOf(AutraApiCall call) {
    return call.body().map(AutraJson::flatten).orElseGet(Map::of);
  }

  private void recordOutbound(
      AutraApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      AutraOperationResult result,
      FaultedDelivery delivery) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            AUTRA,
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
            Redaction.redact(result.body().fields(), AutraFields.SENSITIVE_RESPONSE_FIELDS),
            new DeliveredResponse(
                delivery.fault(),
                delivery.declaredContentLength(),
                delivery.writtenBodyBytes(),
                delivery.delay())));
  }

  private String auditId() {
    return "aud_" + ids.hex(AUDIT_ID_LENGTH);
  }

  record AtThisMoment(TestRunId testRunId, Instant now) {}
}
