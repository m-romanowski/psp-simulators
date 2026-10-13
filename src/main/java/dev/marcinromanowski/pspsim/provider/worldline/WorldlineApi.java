package dev.marcinromanowski.pspsim.provider.worldline;

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

public final class WorldlineApi {

  public static final ProviderId WORLDLINE = ProviderId.of("worldline");

  static final String NO_ACCOUNT_ANSWERS_TO_THAT_KEY_AND_MERCHANT =
      "the Authorization header names no apiKey this test run configures, or names one configured"
          + " for a different merchantId from the one in the path";

  static final String THE_AUTHORIZATION_HEADER_IS_NOT_THE_PUBLISHED_SCHEME =
      "the Authorization header is not of the published form \"GCS v1HMAC:<your-api-key>:"
          + "<base64-encoded-signature>\"";

  static final String THE_DATE_HEADER_IS_REQUIRED =
      "a signed request carries a Date header, which \"Must be identical for both request header"
          + " and the string-to-hash to compute the signature\" and is a UTC timestamp in RFC"
          + " 1123 format";

  static final String THE_DATE_IS_OLDER_THAN_THE_PLATFORM_ACCEPTS =
      "Our platform will reject requests with a timestamp older than 5 minutes. "
          + WorldlineSignature.THE_SKEW_IS_THE_VENDORS_AND_NOT_A_KNOB;

  static final String THE_SIGNATURE_DOES_NOT_MATCH =
      "the signature in the Authorization header is not the one this simulator computes over the"
          + " published canonical string for this request";

  private static final int AUDIT_ID_LENGTH = 24;

  private final WorldlineMerchants merchants;
  private final TestRuns testRuns;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final WorldlineAnswers answers;
  private final TestRunId defaultTestRun;
  private final CreateHostedTokenization createHostedTokenization;
  private final GetHostedTokenization getHostedTokenization;
  private final CreatePayment createPayment;
  private final CapturePayment capturePayment;
  private final CancelPayment cancelPayment;

  WorldlineApi(
      WorldlineMerchants merchants,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      WorldlineAnswers answers,
      TestRunId defaultTestRun,
      CreateHostedTokenization createHostedTokenization,
      GetHostedTokenization getHostedTokenization,
      CreatePayment createPayment,
      CapturePayment capturePayment,
      CancelPayment cancelPayment) {
    this.merchants = merchants;
    this.testRuns = testRuns;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.answers = answers;
    this.defaultTestRun = defaultTestRun;
    this.createHostedTokenization = createHostedTokenization;
    this.getHostedTokenization = getHostedTokenization;
    this.createPayment = createPayment;
    this.capturePayment = capturePayment;
    this.cancelPayment = cancelPayment;
  }

  public WorldlineApiResponse createHostedTokenization(WorldlineApiCall call, String merchantId) {
    return authenticated(
        call,
        merchantId,
        (merchant, at) ->
            createHostedTokenization.handle(
                call,
                call.headers(),
                at.testRunId(),
                merchant,
                at.now(),
                ObjectOrigin.PROVIDER_API));
  }

  public WorldlineApiResponse getHostedTokenization(
      WorldlineApiCall call, String merchantId, String hostedTokenizationId) {
    return authenticated(
        call,
        merchantId,
        (merchant, at) ->
            getHostedTokenization.handle(
                call.headers(), at.testRunId(), merchant, hostedTokenizationId, at.now()));
  }

  public WorldlineApiResponse createPayment(WorldlineApiCall call, String merchantId) {
    return authenticated(
        call,
        merchantId,
        (merchant, at) ->
            createPayment.handle(
                call,
                call.headers(),
                at.testRunId(),
                merchant,
                at.now(),
                ObjectOrigin.PROVIDER_API));
  }

  public WorldlineApiResponse capturePayment(
      WorldlineApiCall call, String merchantId, String paymentId) {
    return authenticated(
        call,
        merchantId,
        (merchant, at) ->
            capturePayment.handle(
                call, call.headers(), at.testRunId(), merchant, paymentId, at.now()));
  }

  public WorldlineApiResponse cancelPayment(
      WorldlineApiCall call, String merchantId, String paymentId) {
    return authenticated(
        call,
        merchantId,
        (merchant, at) ->
            cancelPayment.handle(
                call, call.headers(), at.testRunId(), merchant, paymentId, at.now()));
  }

  private WorldlineApiResponse authenticated(
      WorldlineApiCall call,
      String merchantId,
      BiFunction<WorldlineMerchant, AtThisMoment, WorldlineOperationResult> operation) {
    Optional<String> apiKey =
        call.header(WorldlineFields.AUTHORIZATION).flatMap(WorldlineSignature::apiKeyIn);
    List<WorldlineMerchant> byCredential = apiKey.map(merchants::byApiKey).orElseGet(List::of);
    TestRunResolution resolution = resolve(call, testRunsOf(byCredential));
    Instant now = clock.now(resolution.testRunId());
    Optional<WorldlineMerchant> merchant =
        merchants
            .ofTheRunHolding(resolution.testRunId(), merchantId)
            .filter(held -> apiKey.filter(sent -> sent.equals(held.apiKey())).isPresent());
    String account = merchant.map(WorldlineMerchant::accountId).orElse(null);
    recordInbound(call, resolution, account, now);
    WorldlineOperationResult result =
        merchant.isEmpty()
            ? answers.refusedCredential(NO_ACCOUNT_ANSWERS_TO_THAT_KEY_AND_MERCHANT)
            : theRefusalOfTheSignature(call, merchant.get(), now)
                .orElseGet(
                    () ->
                        operation.apply(
                            merchant.get(), new AtThisMoment(resolution.testRunId(), now)));
    return delivered(call, resolution, account, now, result);
  }

  Optional<WorldlineOperationResult> theRefusalOfTheSignature(
      WorldlineApiCall call, WorldlineMerchant merchant, Instant now) {
    if (!merchant.authentication().verifiesTheSignature()) {
      return Optional.empty();
    }
    Optional<String> authorization = call.header(WorldlineFields.AUTHORIZATION);
    Optional<String> presented = authorization.flatMap(WorldlineSignature::signatureIn);
    if (presented.isEmpty()) {
      return Optional.of(
          answers.refusedCredential(THE_AUTHORIZATION_HEADER_IS_NOT_THE_PUBLISHED_SCHEME));
    }
    Optional<String> date = call.header(WorldlineFields.DATE);
    if (date.isEmpty()) {
      return Optional.of(answers.refusedCredential(THE_DATE_HEADER_IS_REQUIRED));
    }
    Optional<Instant> sent = WorldlineSignature.dateIn(date.get());
    if (sent.isEmpty()) {
      return Optional.of(answers.refusedCredential(THE_DATE_HEADER_IS_REQUIRED));
    }
    if (WorldlineSignature.isOlderThanThePlatformAccepts(sent.get(), now)) {
      return Optional.of(answers.refusedCredential(THE_DATE_IS_OLDER_THAN_THE_PLATFORM_ACCEPTS));
    }
    String expected =
        WorldlineSignature.signed(merchant.apiSecret(), theSubjectOf(call, merchant, date.get()));
    return expected.equals(presented.get())
        ? Optional.empty()
        : Optional.of(answers.refusedCredential(THE_SIGNATURE_DOES_NOT_MATCH));
  }

  static String theSubjectOf(WorldlineApiCall call, WorldlineMerchant merchant, String date) {
    return WorldlineSignature.subjectOf(
        call.method(),
        call.header(WorldlineFields.CONTENT_TYPE).orElse(WorldlineSignature.THE_EMPTY_CONTENT_TYPE),
        date,
        WorldlineSignature.canonicalHeaderLinesOf(call.headers()),
        merchant.signedPathVersion().signedFormOf(call.path()));
  }

  private WorldlineApiResponse delivered(
      WorldlineApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      WorldlineOperationResult result) {
    FaultedDelivery delivery = deliveryOf(result);
    recordOutbound(call, resolution, accountId, now, result, delivery);
    return new WorldlineApiResponse(
        resolution.testRunId(),
        delivery,
        result.outcome(),
        result.providerObjectId(),
        result.body().headers());
  }

  private static FaultedDelivery deliveryOf(WorldlineOperationResult result) {
    FaultedDelivery insteadOfTheOperation = result.insteadOfTheOperation();
    if (insteadOfTheOperation != null) {
      return insteadOfTheOperation;
    }
    WorldlineBody body = result.body();
    RenderedResponse rendered =
        new RenderedResponse(body.httpStatus(), String.valueOf(body.contentType()), body.bytes());
    TransportFault fault = result.fault();
    return fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
  }

  private TestRunResolution resolve(WorldlineApiCall call, List<TestRunId> byCredential) {
    List<TestRunCandidates> evaluated = new ArrayList<>();
    evaluated.add(TestRunHeader.in(call.headers(), testRuns::exists));
    evaluated.add(call.route().candidatesFor(WORLDLINE));
    evaluated.add(TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, byCredential));
    return TestRunResolution.of(evaluated, defaultTestRun);
  }

  private static List<TestRunId> testRunsOf(List<WorldlineMerchant> matches) {
    return matches.stream().map(WorldlineMerchant::testRunId).toList();
  }

  private void recordInbound(
      WorldlineApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            WORLDLINE,
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
            Redaction.redact(call.headers(), WorldlineFields.SENSITIVE_HEADERS),
            Redaction.redact(theRequestFieldsOf(call), WorldlineFields.SENSITIVE_REQUEST_FIELDS),
            null));
  }

  static Map<String, List<String>> theRequestFieldsOf(WorldlineApiCall call) {
    return call.body().map(WorldlineJson::flatten).orElseGet(Map::of);
  }

  private void recordOutbound(
      WorldlineApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      WorldlineOperationResult result,
      FaultedDelivery delivery) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            WORLDLINE,
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
            DeliveredHeaders.of(delivery, result.body().headers()),
            Redaction.redact(result.body().fields(), WorldlineFields.SENSITIVE_RESPONSE_FIELDS),
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
