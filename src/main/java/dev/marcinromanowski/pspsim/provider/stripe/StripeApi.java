package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.audit.TheAccountARowNames;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.RenderedResponse;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeLog;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunCandidates;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolution;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class StripeApi {

  public static final ProviderId STRIPE = ProviderId.of("stripe");

  static final String CONTENT_TYPE = "application/json";

  static final String POST = "POST";

  private static final int AUDIT_ID_LENGTH = 24;

  private static final int REQUEST_ID_LENGTH = 24;

  private final StripeAccounts accounts;
  private final StripeIdempotencyKeys keys;
  private final StripeEphemeralKeys ephemeralKeys;
  private final TestRuns testRuns;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final TestRunId defaultTestRun;
  private final CreatePaymentIntent createPaymentIntent;
  private final ConfirmPaymentIntent confirmPaymentIntent;
  private final CapturePaymentIntent capturePaymentIntent;
  private final CancelPaymentIntent cancelPaymentIntent;
  private final RetrievePaymentIntent retrievePaymentIntent;
  private final RetrieveCharge retrieveCharge;
  private final CreateRefund createRefund;
  private final CreateCustomer createCustomer;
  private final RetrieveCustomer retrieveCustomer;
  private final UpdateCustomer updateCustomer;
  private final DeleteCustomer deleteCustomer;
  private final ListPaymentMethods listPaymentMethods;
  private final RetrievePaymentMethod retrievePaymentMethod;
  private final DetachPaymentMethod detachPaymentMethod;
  private final CreateSetupIntent createSetupIntent;
  private final ConfirmSetupIntent confirmSetupIntent;
  private final CreateEphemeralKey createEphemeralKey;

  StripeApi(
      StripeAccounts accounts,
      StripeIdempotencyKeys keys,
      StripeEphemeralKeys ephemeralKeys,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      TestRunId defaultTestRun,
      CreatePaymentIntent createPaymentIntent,
      ConfirmPaymentIntent confirmPaymentIntent,
      CapturePaymentIntent capturePaymentIntent,
      CancelPaymentIntent cancelPaymentIntent,
      RetrievePaymentIntent retrievePaymentIntent,
      RetrieveCharge retrieveCharge,
      CreateRefund createRefund,
      StripeCustomers customers,
      StripePaymentMethods paymentMethods,
      CreateSetupIntent createSetupIntent,
      ConfirmSetupIntent confirmSetupIntent) {
    this.accounts = accounts;
    this.keys = keys;
    this.ephemeralKeys = ephemeralKeys;
    this.testRuns = testRuns;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.defaultTestRun = defaultTestRun;
    this.createPaymentIntent = createPaymentIntent;
    this.confirmPaymentIntent = confirmPaymentIntent;
    this.capturePaymentIntent = capturePaymentIntent;
    this.cancelPaymentIntent = cancelPaymentIntent;
    this.retrievePaymentIntent = retrievePaymentIntent;
    this.retrieveCharge = retrieveCharge;
    this.createRefund = createRefund;
    this.createCustomer = new CreateCustomer(customers, ids);
    this.retrieveCustomer = new RetrieveCustomer(customers);
    this.updateCustomer = new UpdateCustomer(customers);
    this.deleteCustomer = new DeleteCustomer(customers, paymentMethods);
    this.listPaymentMethods = new ListPaymentMethods(paymentMethods, customers);
    this.retrievePaymentMethod = new RetrievePaymentMethod(paymentMethods, customers);
    this.detachPaymentMethod = new DetachPaymentMethod(paymentMethods);
    this.createSetupIntent = createSetupIntent;
    this.confirmSetupIntent = confirmSetupIntent;
    this.createEphemeralKey = new CreateEphemeralKey(ephemeralKeys, customers, ids);
  }

  @FunctionalInterface
  private interface StripeOperation {
    StripeOperationResult handle(
        StripeForm form,
        Map<String, List<String>> headers,
        TestRunId testRunId,
        StripeAccount account,
        Instant now);
  }

  public StripeApiResponse createPaymentIntent(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE,
        (form, headers, testRunId, account, now) ->
            createPaymentIntent.handle(
                form, headers, testRunId, account, now, ObjectOrigin.PROVIDER_API));
  }

  public StripeApiResponse confirmPaymentIntent(StripeApiCall call, String intentId) {
    return answer(
        call,
        StripeOperations.CONFIRM,
        (form, headers, testRunId, account, now) ->
            confirmPaymentIntent.handle(form, headers, testRunId, account, intentId, now));
  }

  public StripeApiResponse capturePaymentIntent(StripeApiCall call, String intentId) {
    return answer(
        call,
        StripeOperations.CAPTURE,
        (form, headers, testRunId, account, now) ->
            capturePaymentIntent.handle(form, headers, testRunId, account, intentId, now));
  }

  public StripeApiResponse cancelPaymentIntent(StripeApiCall call, String intentId) {
    return answer(
        call,
        StripeOperations.CANCEL,
        (form, headers, testRunId, account, now) ->
            cancelPaymentIntent.handle(form, headers, testRunId, account, intentId, now));
  }

  public StripeApiResponse retrievePaymentIntent(StripeApiCall call, String intentId) {
    return answer(
        call,
        StripeOperations.RETRIEVE,
        (form, headers, testRunId, account, now) ->
            retrievePaymentIntent.handle(testRunId, account, intentId, now));
  }

  public StripeApiResponse retrieveCharge(StripeApiCall call, String chargeId) {
    return answer(
        call,
        StripeOperations.RETRIEVE_CHARGE,
        (form, headers, testRunId, account, now) ->
            retrieveCharge.handle(testRunId, account, chargeId, now));
  }

  public StripeApiResponse createRefund(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.REFUND,
        (form, headers, testRunId, account, now) ->
            createRefund.handle(form, headers, testRunId, account, now, ObjectOrigin.PROVIDER_API));
  }

  public StripeApiResponse createCustomer(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE_CUSTOMER,
        (form, headers, testRunId, account, now) ->
            createCustomer.handle(form, testRunId, account, now, ObjectOrigin.PROVIDER_API));
  }

  public StripeApiResponse retrieveCustomer(StripeApiCall call, String customerId) {
    return answer(
        call,
        StripeOperations.RETRIEVE_CUSTOMER,
        new StripeShortLivedAccess.TheCustomerTheCallNames(customerId),
        (form, headers, testRunId, account, now) ->
            retrieveCustomer.handle(testRunId, account, customerId));
  }

  public StripeApiResponse updateCustomer(StripeApiCall call, String customerId) {
    return answer(
        call,
        StripeOperations.UPDATE_CUSTOMER,
        (form, headers, testRunId, account, now) ->
            updateCustomer.handle(form, testRunId, account, customerId, now));
  }

  public StripeApiResponse deleteCustomer(StripeApiCall call, String customerId) {
    return answer(
        call,
        StripeOperations.DELETE_CUSTOMER,
        (form, headers, testRunId, account, now) ->
            deleteCustomer.handle(testRunId, account, customerId, now));
  }

  public StripeApiResponse listPaymentMethods(StripeApiCall call) {
    StripeForm query = StripeForm.of(call.query());
    return answer(
        call,
        StripeOperations.LIST_PAYMENT_METHODS,
        (form, headers, testRunId, account, now) ->
            listPaymentMethods.everyMethod(query, testRunId, account));
  }

  public StripeApiResponse listCustomerPaymentMethods(StripeApiCall call, String customerId) {
    StripeForm query = StripeForm.of(call.query());
    return answer(
        call,
        StripeOperations.LIST_CUSTOMER_PAYMENT_METHODS,
        new StripeShortLivedAccess.TheCustomerTheCallNames(customerId),
        (form, headers, testRunId, account, now) ->
            listPaymentMethods.theMethodsOf(query, testRunId, account, customerId));
  }

  public StripeApiResponse retrievePaymentMethod(StripeApiCall call, String paymentMethodId) {
    StripeForm query = StripeForm.of(call.query());
    return answer(
        call,
        StripeOperations.RETRIEVE_PAYMENT_METHOD,
        (form, headers, testRunId, account, now) ->
            retrievePaymentMethod.handle(query, testRunId, account, paymentMethodId));
  }

  public StripeApiResponse detachPaymentMethod(StripeApiCall call, String paymentMethodId) {
    return answer(
        call,
        StripeOperations.DETACH_PAYMENT_METHOD,
        (form, headers, testRunId, account, now) ->
            detachPaymentMethod.handle(testRunId, account, paymentMethodId, now));
  }

  public StripeApiResponse createSetupIntent(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE_SETUP_INTENT,
        (form, headers, testRunId, account, now) ->
            createSetupIntent.handle(form, testRunId, account, now, ObjectOrigin.PROVIDER_API));
  }

  public StripeApiResponse confirmSetupIntent(StripeApiCall call, String setupIntentId) {
    return answer(
        call,
        StripeOperations.CONFIRM_SETUP_INTENT,
        (form, headers, testRunId, account, now) ->
            confirmSetupIntent.handle(form, headers, testRunId, account, setupIntentId, now));
  }

  public StripeApiResponse createEphemeralKey(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE_EPHEMERAL_KEY,
        (form, headers, testRunId, account, now) ->
            createEphemeralKey.handle(form, testRunId, account, now));
  }

  private StripeApiResponse answer(
      StripeApiCall call, String operationName, StripeOperation operation) {
    return answer(
        call,
        operationName,
        new StripeShortLivedAccess.NoShortLivedKeyReachesThisRoute(),
        operation);
  }

  private StripeApiResponse answer(
      StripeApiCall call,
      String operationName,
      StripeShortLivedAccess access,
      StripeOperation operation) {
    StripeForm form = StripeForm.of(call.form());
    String secretKey = StripeAuthentication.secretKeyIn(call.headers());
    List<StripeAccount> matches = accountsBySecretKey(secretKey);
    List<StripeEphemeralKey> shortLived = shortLivedKeysBySecret(secretKey, matches);
    TestRunResolution resolution = resolve(call, matches, shortLived);
    Instant now = clock.now(resolution.testRunId());

    List<StripeAccount> within = within(resolution, matches);
    String account =
        TheAccountARowNames.amongTheAccountsTheCredentialsMatched(
            within.stream().map(StripeAccount::accountId).toList());

    recordInbound(call, resolution, account, now, form);
    Answered answered =
        identifiedBy(
            secretKey == null
                ? noKeyWasPresented()
                : answered(
                    call,
                    form,
                    resolution.testRunId(),
                    within,
                    withinTheRun(resolution, shortLived),
                    access,
                    now,
                    operationName,
                    operation),
            requestId());
    recordOutbound(call, resolution, account, now, answered);

    return new StripeApiResponse(
        resolution.testRunId(),
        answered.delivery(),
        answered.outcome(),
        answered.providerObjectId(),
        answered.headers());
  }

  private Answered answered(
      StripeApiCall call,
      StripeForm form,
      TestRunId testRunId,
      List<StripeAccount> matches,
      List<StripeEphemeralKey> shortLived,
      StripeShortLivedAccess access,
      Instant now,
      String operationName,
      StripeOperation operation) {
    if (matches.size() == 1) {
      return answeredForTheAccount(
          call, form, testRunId, matches.get(0), now, operationName, operation);
    }
    if (matches.isEmpty() && shortLived.size() == 1) {
      return answeredForAShortLivedKey(
          call, form, testRunId, shortLived.get(0), access, now, operation);
    }
    return rendered(
        StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theKeyReachesNoAccount(), StripeErrors.noValidApiKey()),
        Map.of());
  }

  private static Answered noKeyWasPresented() {
    return rendered(
        StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theKeyReachesNoAccount(), StripeErrors.noApiKey()),
        Map.of(StripeFields.WWW_AUTHENTICATE_HEADER, List.of(StripeFields.THE_BASIC_CHALLENGE)));
  }

  private Answered answeredForAShortLivedKey(
      StripeApiCall call,
      StripeForm form,
      TestRunId testRunId,
      StripeEphemeralKey shortLived,
      StripeShortLivedAccess access,
      Instant now,
      StripeOperation operation) {
    if (shortLived.hasExpiredAt(now)) {
      return rendered(
          StripeOperationResult.refusedBeforeExecuting(
              StripeRefusals.theShortLivedKeyIsRefused(),
              StripeErrors.theShortLivedKeyHasExpired()),
          Map.of());
    }
    if (!(access instanceof StripeShortLivedAccess.TheCustomerTheCallNames named)) {
      return rendered(
          StripeOperationResult.refusedBeforeExecuting(
              StripeRefusals.theShortLivedKeyIsRefused(),
              StripeErrors.theShortLivedKeyReachesNoRouteOfThisKind()),
          Map.of());
    }
    if (!shortLived.reaches(named.customerId())) {
      return rendered(
          StripeOperationResult.refusedBeforeExecuting(
              StripeRefusals.theShortLivedKeyIsRefused(),
              StripeErrors.theShortLivedKeyReachesAnotherResource()),
          Map.of());
    }
    return accounts
        .find(testRunId, shortLived.accountId())
        .map(
            account ->
                rendered(operation.handle(form, call.headers(), testRunId, account, now), Map.of()))
        .orElseGet(
            () ->
                rendered(
                    StripeOperationResult.refusedBeforeExecuting(
                        StripeRefusals.theKeyReachesNoAccount(), StripeErrors.noValidApiKey()),
                    Map.of()));
  }

  private Answered answeredForTheAccount(
      StripeApiCall call,
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String operationName,
      StripeOperation operation) {
    String key = StripeIdempotency.keyIn(call.headers());
    if (key == null || !POST.equals(call.method())) {
      return rendered(operation.handle(form, call.headers(), testRunId, account, now), Map.of());
    }
    if (StripeIdempotency.isTooLong(key)) {
      return rendered(
          StripeOperationResult.refusedBeforeExecuting(
              StripeRefusals.theRequestWasRefused(),
              StripeErrors.headerRefused(StripeIdempotency.KEY_IS_TOO_LONG)),
          Map.of());
    }
    return underTheKey(call, form, testRunId, account, now, operationName, operation, key);
  }

  private Answered underTheKey(
      StripeApiCall call,
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String operationName,
      StripeOperation operation,
      String key) {
    FingerprintedRequest request =
        new FingerprintedRequest(call.method(), call.path(), call.form());
    StripeIdempotencyClaim claim =
        keys.claim(
            testRunId,
            account.accountId(),
            key,
            keys.fingerprintOf(testRunId, request),
            now,
            now.minus(StripeIdempotency.WINDOW));
    return switch (claim) {
      case StripeIdempotencyClaim.Fresh _ ->
          answeredUnderTheKeyItClaimed(
              testRunId,
              account,
              key,
              operationName,
              request,
              operation,
              form,
              call.headers(),
              now);
      case StripeIdempotencyClaim.Replayed replayed ->
          observedReplay(testRunId, account, key, operationName, request, replayed.answer(), now);
      case StripeIdempotencyClaim.InProgressElsewhere _ ->
          observedARefusal(
              testRunId,
              account,
              key,
              operationName,
              request,
              KeyedOutcome.REFUSED_WHILE_THE_KEY_WAS_HELD,
              StripeErrors.idempotencyKeyInUse(),
              now);
      case StripeIdempotencyClaim.ReusedWithOtherParameters _ ->
          observedARefusal(
              testRunId,
              account,
              key,
              operationName,
              request,
              KeyedOutcome.REFUSED_AS_A_CONFLICT,
              StripeErrors.idempotencyKeyReused(),
              now);
    };
  }

  private Answered observedReplay(
      TestRunId testRunId,
      StripeAccount account,
      String key,
      String operationName,
      FingerprintedRequest request,
      StripeStoredAnswer answer,
      Instant now) {
    keys.observed(
        new KeyedObservation(
            testRunId,
            account.accountId(),
            StripeKeyMechanisms.IDEMPOTENCY_KEY,
            key,
            operationName,
            KeyedOutcome.REPLAYED,
            request,
            new KeyedAnswer(answer.httpStatus(), StripeJsonReader.read(answer.body()).flatten()),
            answer.providerObjectId(),
            false,
            null,
            now));
    return replayed(answer);
  }

  private Answered observedARefusal(
      TestRunId testRunId,
      StripeAccount account,
      String key,
      String operationName,
      FingerprintedRequest request,
      KeyedOutcome outcome,
      StripeError error,
      Instant now) {
    StripeOperationResult refusal =
        StripeOperationResult.refusedBeforeExecuting(
            RefusalSubject.anObjectItWillNotName(
                StripeRefusals.THE_IDEMPOTENCY_KEY_IS_ALREADY_CLAIMED),
            error);
    keys.observed(
        new KeyedObservation(
            testRunId,
            account.accountId(),
            StripeKeyMechanisms.IDEMPOTENCY_KEY,
            key,
            operationName,
            outcome,
            request,
            new KeyedAnswer(refusal.httpStatus(), refusal.body().flatten()),
            null,
            false,
            null,
            now));
    return rendered(refusal, Map.of());
  }

  private Answered answeredUnderTheKeyItClaimed(
      TestRunId testRunId,
      StripeAccount account,
      String key,
      String operationName,
      FingerprintedRequest request,
      StripeOperation operation,
      StripeForm form,
      Map<String, List<String>> headers,
      Instant now) {
    StripeOperationResult result;
    try {
      result = operation.handle(form, headers, testRunId, account, now);
    } catch (RuntimeException unanswerable) {
      keys.released(testRunId, account.accountId(), key);
      throw unanswerable;
    }
    return storedUnder(testRunId, account, key, operationName, request, result, now);
  }

  private Answered storedUnder(
      TestRunId testRunId,
      StripeAccount account,
      String key,
      String operationName,
      FingerprintedRequest request,
      StripeOperationResult result,
      Instant now) {
    Answered answered = rendered(result, Map.of());
    if (!result.theEndpointBeganExecuting()) {
      keys.released(testRunId, account.accountId(), key);
      return answered;
    }
    KeyedExchangeLog.recordedThenCompleted(
        () ->
            keys.observed(
                new KeyedObservation(
                    testRunId,
                    account.accountId(),
                    StripeKeyMechanisms.IDEMPOTENCY_KEY,
                    key,
                    operationName,
                    KeyedOutcome.RAN_THE_OPERATION,
                    request,
                    new KeyedAnswer(result.httpStatus(), result.body().flatten()),
                    result.providerObjectId(),
                    result.stateChanged(),
                    answered.delivery().fault(),
                    now)),
        () ->
            keys.completed(
                testRunId,
                account.accountId(),
                key,
                new StripeStoredAnswer(
                    result.httpStatus(),
                    CONTENT_TYPE,
                    StripeJsonWriter.toText(result.body()),
                    result.outcome(),
                    result.providerObjectId())));
    return answered;
  }

  private static Answered replayed(StripeStoredAnswer answer) {
    RenderedResponse rendered =
        new RenderedResponse(
            answer.httpStatus(),
            answer.contentType(),
            answer.body().getBytes(StandardCharsets.UTF_8));
    return new Answered(
        rendered.asDelivered(),
        answer.outcome(),
        answer.providerObjectId(),
        answer.outcome() == ProviderOutcome.FAILURE
            ? StripeRefusals.theAnswerItStoredFor(answer.providerObjectId())
            : null,
        Map.of(StripeFields.IDEMPOTENT_REPLAYED_HEADER, List.of(StripeFlag.TRUE)),
        null,
        Map.of());
  }

  private static Answered rendered(
      StripeOperationResult result, Map<String, List<String>> headers) {
    return new Answered(
        deliveryOf(result),
        result.outcome(),
        result.providerObjectId(),
        result.refusal(),
        headers,
        result.scenarioId(),
        result.body().flatten());
  }

  private static FaultedDelivery deliveryOf(StripeOperationResult result) {
    FaultedDelivery insteadOfTheOperation = result.insteadOfTheOperation();
    if (insteadOfTheOperation != null) {
      return insteadOfTheOperation;
    }
    RenderedResponse rendered =
        new RenderedResponse(
            result.httpStatus(), CONTENT_TYPE, StripeJsonWriter.toBytes(result.body()));
    TransportFault fault = result.fault();
    return fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
  }

  private List<StripeAccount> accountsBySecretKey(@Nullable String secretKey) {
    return secretKey == null ? List.of() : accounts.bySecretKey(secretKey);
  }

  private List<StripeEphemeralKey> shortLivedKeysBySecret(
      @Nullable String secretKey, List<StripeAccount> matches) {
    if (secretKey == null || !matches.isEmpty()) {
      return List.of();
    }
    return ephemeralKeys.bySecret(secretKey);
  }

  private TestRunResolution resolve(
      StripeApiCall call, List<StripeAccount> matches, List<StripeEphemeralKey> shortLived) {
    List<TestRunCandidates> evaluated = new ArrayList<>();
    evaluated.add(TestRunHeader.in(call.headers(), testRuns::exists));
    evaluated.add(call.route().candidatesFor(STRIPE));
    evaluated.add(
        TestRunCandidates.of(
            TestRunResolutionRule.ACCOUNT_CREDENTIALS,
            theRunsTheseCredentialsName(matches, shortLived)));
    return TestRunResolution.of(evaluated, defaultTestRun);
  }

  private static List<TestRunId> theRunsTheseCredentialsName(
      List<StripeAccount> matches, List<StripeEphemeralKey> shortLived) {
    List<TestRunId> named = new ArrayList<>(testRunsOf(matches));
    for (StripeEphemeralKey key : shortLived) {
      named.add(key.testRunId());
    }
    return List.copyOf(named);
  }

  private static List<StripeEphemeralKey> withinTheRun(
      TestRunResolution resolution, List<StripeEphemeralKey> shortLived) {
    return resolution
        .resolvedTestRun()
        .map(
            testRunId ->
                shortLived.stream().filter(key -> key.testRunId().equals(testRunId)).toList())
        .orElseGet(List::of);
  }

  private static List<StripeAccount> within(
      TestRunResolution resolution, List<StripeAccount> matches) {
    return resolution
        .resolvedTestRun()
        .map(testRunId -> accountsOf(testRunId, matches))
        .orElseGet(List::of);
  }

  private static List<StripeAccount> accountsOf(TestRunId testRunId, List<StripeAccount> matches) {
    return matches.stream().filter(account -> account.testRunId().equals(testRunId)).toList();
  }

  private static List<TestRunId> testRunsOf(List<StripeAccount> matches) {
    return matches.stream().map(StripeAccount::testRunId).toList();
  }

  private static Map<String, List<String>> theParametersItCarried(
      StripeApiCall call, StripeForm form) {
    Map<String, List<String>> carried = new LinkedHashMap<>(form.forAudit());
    call.query().forEach((name, sent) -> carried.merge(name, sent, StripeApi::bothOf));
    return Collections.unmodifiableMap(new LinkedHashMap<>(carried));
  }

  private static List<String> bothOf(List<String> inTheBody, List<String> inTheQuery) {
    List<String> both = new ArrayList<>(inTheBody);
    both.addAll(inTheQuery);
    return List.copyOf(both);
  }

  private void recordInbound(
      StripeApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      StripeForm form) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            STRIPE,
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
            Redaction.redact(call.headers(), StripeFields.SENSITIVE_HEADERS),
            Redaction.redact(
                theParametersItCarried(call, form), StripeFields.SENSITIVE_REQUEST_FIELDS),
            null));
  }

  private void recordOutbound(
      StripeApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      Answered answered) {
    FaultedDelivery delivery = answered.delivery();
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            STRIPE,
            accountId,
            ObjectOrigin.PROVIDER_API,
            AuditDirection.OUTBOUND_RESPONSE,
            answered.outcome(),
            call.method(),
            call.path(),
            delivery.httpStatus(),
            answered.providerObjectId(),
            answered.refusal(),
            answered.scenarioId(),
            1,
            now,
            DeliveredHeaders.of(delivery, answered.headers()),
            StripeFields.withholding(
                answered.bodyForAudit(), StripeFields.SENSITIVE_RESPONSE_FIELDS),
            new DeliveredResponse(
                delivery.fault(),
                delivery.declaredContentLength(),
                delivery.writtenBodyBytes(),
                delivery.delay())));
  }

  private String auditId() {
    return "aud_" + ids.hex(AUDIT_ID_LENGTH);
  }

  private String requestId() {
    return StripeFields.REQUEST_ID_PREFIX + ids.hex(REQUEST_ID_LENGTH);
  }

  private static Answered identifiedBy(Answered answered, String requestId) {
    Map<String, List<String>> headers = new LinkedHashMap<>(answered.headers());
    headers.put(StripeFields.REQUEST_ID_HEADER, List.of(requestId));
    return new Answered(
        answered.delivery(),
        answered.outcome(),
        answered.providerObjectId(),
        answered.refusal(),
        Collections.unmodifiableMap(new LinkedHashMap<>(headers)),
        answered.scenarioId(),
        answered.bodyForAudit());
  }

  private record Answered(
      FaultedDelivery delivery,
      ProviderOutcome outcome,
      @Nullable String providerObjectId,
      @Nullable RefusalSubject refusal,
      Map<String, List<String>> headers,
      @Nullable String scenarioId,
      Map<String, List<String>> bodyForAudit) {}
}
