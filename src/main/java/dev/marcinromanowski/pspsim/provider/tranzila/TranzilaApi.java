package dev.marcinromanowski.pspsim.provider.tranzila;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class TranzilaApi {

  public static final ProviderId TRANZILA = ProviderId.of("tranzila");

  static final Duration THE_REQUEST_TIME_TOLERANCE = Duration.ofMinutes(5);

  static final Duration A_NONCE_IS_REMEMBERED_FOR = THE_REQUEST_TIME_TOLERANCE.multipliedBy(2);

  static final String NO_ACCOUNT_ANSWERS_TO_THAT_APP_KEY =
      "the X-tranzila-api-app-key header names no app key this test run configures";

  static final String A_HEADER_IS_MISSING =
      " is required on every call, as the authentication page's header table publishes it";

  static final String THE_REQUEST_TIME_IS_NOT_A_NUMBER =
      "X-tranzila-api-request-time is not a whole number of ";

  static final String THE_REQUEST_TIME_IS_OUT_OF_THE_WINDOW =
      "X-tranzila-api-request-time is more than five minutes from this test run's clock; the"
          + " reports authentication page publishes \"Must be within a few minutes of server"
          + " time\" and no figure, so five minutes is this simulator's own";

  static final String THE_ACCESS_TOKEN_DOES_NOT_MATCH =
      "X-tranzila-api-access-token is not the HMAC-SHA256 this simulator computes for this app"
          + " key, request time and nonce under the construction this account names in ";

  static final String THE_NONCE_WAS_USED =
      "X-tranzila-api-nonce was already used by a call of this account within the last ten"
          + " minutes, as long as a request time it came with could still be accepted, and the"
          + " authentication page says the token is there to \"prevent"
          + " processing of the same request more than once\"";

  private static final int AUDIT_ID_LENGTH = 24;

  private final TranzilaTerminals terminals;
  private final TranzilaNonces nonces;
  private final TestRuns testRuns;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final TestRunId defaultTestRun;
  private final CreateTransaction createTransaction;
  private final CreateHandshake createHandshake;
  private final CompleteThreeDSecure completeThreeDSecure;
  private final TranzilaLookups lookups;

  TranzilaApi(
      TranzilaTerminals terminals,
      TranzilaNonces nonces,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      TestRunId defaultTestRun,
      CreateTransaction createTransaction,
      CreateHandshake createHandshake,
      CompleteThreeDSecure completeThreeDSecure,
      TranzilaLookups lookups) {
    this.terminals = terminals;
    this.nonces = nonces;
    this.testRuns = testRuns;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.defaultTestRun = defaultTestRun;
    this.createTransaction = createTransaction;
    this.createHandshake = createHandshake;
    this.completeThreeDSecure = completeThreeDSecure;
    this.lookups = lookups;
  }

  public TranzilaApiResponse create(TranzilaApiCall call) {
    return answered(
        call,
        ANonce.IS_SPENT,
        TranzilaApi::refused,
        (testRunId, terminal, now) ->
            createTransaction.handle(
                call, call.headers(), testRunId, terminal, now, ObjectOrigin.PROVIDER_API));
  }

  public TranzilaApiResponse complete(TranzilaApiCall call) {
    return answered(
        call,
        ANonce.IS_SPENT,
        TranzilaApi::refused,
        (testRunId, terminal, now) -> completeThreeDSecure.handle(call, testRunId, terminal, now));
  }

  public TranzilaApiResponse handshake(TranzilaApiCall call) {
    return answered(
        call,
        ANonce.IS_SPENT,
        _ -> CreateHandshake.refusedCredential(),
        (testRunId, terminal, now) -> createHandshake.handle(call, testRunId, terminal, now));
  }

  public TranzilaApiResponse transactions(TranzilaApiCall call) {
    return answered(
        call,
        ANonce.IS_SPENT,
        TranzilaApi::refused,
        (testRunId, terminal, now) -> lookups.ofTheApi(call, terminal));
  }

  public TranzilaApiResponse report(TranzilaApiCall call) {
    return answered(
        call,
        ANonce.MAY_BE_REUSED,
        TranzilaLookups::unauthorized,
        (testRunId, terminal, now) -> lookups.ofTheReport(call, terminal));
  }

  enum ANonce {
    IS_SPENT,

    MAY_BE_REUSED
  }

  interface AnOperation {

    TranzilaOperationResult handle(TestRunId testRunId, TranzilaTerminal terminal, Instant now);
  }

  private TranzilaApiResponse answered(
      TranzilaApiCall call,
      ANonce nonceRule,
      Function<String, TranzilaOperationResult> asThisRouteRefuses,
      AnOperation operation) {
    Optional<String> appKey = call.header(TranzilaFields.APP_KEY);
    List<TranzilaTerminal> byCredential = appKey.map(terminals::byAppKey).orElseGet(List::of);
    TestRunResolution resolution = resolve(call, byCredential);
    TestRunId testRunId = resolution.testRunId();
    Instant now = clock.now(testRunId);
    Optional<TranzilaTerminal> terminal =
        byCredential.stream().filter(held -> held.testRunId().equals(testRunId)).findFirst();
    String account = terminal.map(TranzilaTerminal::accountId).orElse(null);
    recordInbound(call, resolution, account, now);
    TranzilaOperationResult result =
        terminal.isEmpty()
            ? asThisRouteRefuses.apply(NO_ACCOUNT_ANSWERS_TO_THAT_APP_KEY)
            : theRefusalOfTheAccessToken(call, terminal.get(), now, nonceRule)
                .map(asThisRouteRefuses)
                .orElseGet(() -> operation.handle(testRunId, terminal.get(), now));
    return delivered(call, resolution, account, now, result);
  }

  Optional<String> theRefusalOfTheAccessToken(
      TranzilaApiCall call, TranzilaTerminal terminal, Instant now, ANonce nonceRule) {
    if (!terminal.authentication().verifiesTheAccessToken()) {
      return Optional.empty();
    }
    Optional<String> requestTime = call.header(TranzilaFields.REQUEST_TIME);
    Optional<String> nonce = call.header(TranzilaFields.NONCE);
    Optional<String> presented = call.header(TranzilaFields.ACCESS_TOKEN);
    if (requestTime.isEmpty()) {
      return Optional.of(TranzilaFields.REQUEST_TIME + A_HEADER_IS_MISSING);
    }
    if (nonce.isEmpty()) {
      return Optional.of(TranzilaFields.NONCE + A_HEADER_IS_MISSING);
    }
    if (presented.isEmpty()) {
      return Optional.of(TranzilaFields.ACCESS_TOKEN + A_HEADER_IS_MISSING);
    }
    TranzilaAccessToken construction = terminal.accessToken();
    Optional<Instant> sentAt = construction.requestTimeIn(requestTime.get());
    if (sentAt.isEmpty()) {
      return Optional.of(THE_REQUEST_TIME_IS_NOT_A_NUMBER + construction.theUnitOfTheRequestTime());
    }
    if (Duration.between(sentAt.get(), now).abs().compareTo(THE_REQUEST_TIME_TOLERANCE) > 0) {
      return Optional.of(THE_REQUEST_TIME_IS_OUT_OF_THE_WINDOW);
    }
    String expected =
        construction.expectedFor(
            terminal.appKey(), terminal.appSecret(), requestTime.get(), nonce.get());
    if (!expected.equalsIgnoreCase(presented.get())) {
      return Optional.of(THE_ACCESS_TOKEN_DOES_NOT_MATCH + TranzilaAccessToken.SETTING);
    }
    if (nonceRule == ANonce.IS_SPENT
        && nonces.claim(
                terminal.testRunId(),
                terminal.accountId(),
                nonce.get(),
                now,
                now.minus(A_NONCE_IS_REMEMBERED_FOR))
            != 1) {
      return Optional.of(THE_NONCE_WAS_USED);
    }
    return Optional.empty();
  }

  private static TranzilaOperationResult refused(String why) {
    return TranzilaAnswers.refusedCredential(
        TranzilaErrors.AUTHENTICATION_FAILED, TranzilaErrors.AUTHENTICATION_FAILED_TEXT, why);
  }

  private TranzilaApiResponse delivered(
      TranzilaApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      TranzilaOperationResult result) {
    FaultedDelivery delivery = deliveryOf(result);
    recordOutbound(call, resolution, accountId, now, result, delivery);
    return new TranzilaApiResponse(
        resolution.testRunId(), delivery, result.outcome(), result.providerObjectId());
  }

  static FaultedDelivery deliveryOf(TranzilaOperationResult result) {
    FaultedDelivery insteadOfTheOperation = result.insteadOfTheOperation();
    if (insteadOfTheOperation != null) {
      return insteadOfTheOperation;
    }
    TranzilaBody body = result.body();
    RenderedResponse rendered =
        new RenderedResponse(body.httpStatus(), String.valueOf(body.contentType()), body.bytes());
    TransportFault fault = result.fault();
    return fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
  }

  private TestRunResolution resolve(TranzilaApiCall call, List<TranzilaTerminal> byCredential) {
    List<TestRunCandidates> evaluated = new ArrayList<>();
    evaluated.add(TestRunHeader.in(call.headers(), testRuns::exists));
    evaluated.add(call.route().candidatesFor(TRANZILA));
    evaluated.add(
        TestRunCandidates.of(
            TestRunResolutionRule.ACCOUNT_CREDENTIALS,
            byCredential.stream().map(TranzilaTerminal::testRunId).toList()));
    return TestRunResolution.of(evaluated, defaultTestRun);
  }

  private void recordInbound(
      TranzilaApiCall call, TestRunResolution resolution, @Nullable String accountId, Instant now) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            TRANZILA,
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
            Redaction.redact(call.headers(), TranzilaFields.SENSITIVE_HEADERS),
            Redaction.redact(theRequestFieldsOf(call), TranzilaFields.SENSITIVE_REQUEST_FIELDS),
            null));
  }

  static Map<String, List<String>> theRequestFieldsOf(TranzilaApiCall call) {
    return call.body()
        .map(TranzilaUserField::withTheIdentitiesWithheld)
        .map(TranzilaJson::flatten)
        .orElseGet(Map::of);
  }

  private void recordOutbound(
      TranzilaApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      TranzilaOperationResult result,
      FaultedDelivery delivery) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            TRANZILA,
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
            Redaction.redact(result.body().fields(), TranzilaFields.SENSITIVE_RESPONSE_FIELDS),
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
