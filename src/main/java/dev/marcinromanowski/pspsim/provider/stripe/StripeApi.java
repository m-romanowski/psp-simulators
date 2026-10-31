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
import java.util.Objects;
import java.util.function.Supplier;
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
  private final CreatePaymentMethod createPaymentMethod;
  private final ListPaymentMethods listPaymentMethods;
  private final RetrievePaymentMethod retrievePaymentMethod;
  private final DetachPaymentMethod detachPaymentMethod;
  private final CreateSetupIntent createSetupIntent;
  private final ConfirmSetupIntent confirmSetupIntent;
  private final CreateEphemeralKey createEphemeralKey;
  private final RetrieveSetupIntent retrieveSetupIntent;
  private final StripeClientSecrets clientSecrets;
  private final StripeRelatedObjects related;

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
      StripePaymentIntents intents,
      StripeCharges charges,
      StripeRefunds refunds,
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
    this.createPaymentMethod = new CreatePaymentMethod(paymentMethods, ids);
    this.listPaymentMethods = new ListPaymentMethods(paymentMethods, customers);
    this.retrievePaymentMethod = new RetrievePaymentMethod(paymentMethods);
    this.detachPaymentMethod = new DetachPaymentMethod(paymentMethods);
    this.createSetupIntent = createSetupIntent;
    this.confirmSetupIntent = confirmSetupIntent;
    this.createEphemeralKey = new CreateEphemeralKey(ephemeralKeys, customers, ids);
    this.retrieveSetupIntent = new RetrieveSetupIntent(confirmSetupIntent.store());
    this.clientSecrets = new StripeClientSecrets(intents, confirmSetupIntent.store());
    this.related = new StripeRelatedObjects(intents, charges, refunds, customers, paymentMethods);
  }

  @FunctionalInterface
  private interface StripeOperation {
    StripeOperationResult handle(
        StripeCoreRequest request,
        Map<String, List<String>> headers,
        TestRunId testRunId,
        StripeAccount account,
        Instant now,
        StripeKeyKind presented);
  }

  public StripeApiResponse createPaymentIntent(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.PAYMENT_INTENT),
            (request, headers, testRunId, account, now, _) ->
                createPaymentIntent.handle(
                    request, headers, testRunId, account, now, ObjectOrigin.PROVIDER_API)));
  }

  public StripeApiResponse confirmPaymentIntent(StripeApiCall call, String intentId) {
    return answer(
        call,
        StripeOperations.CONFIRM,
        new StripePublishableAccess.ThePaymentIntentItsClientSecretOpens(intentId),
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.PAYMENT_INTENT),
            (request, headers, testRunId, account, now, _) ->
                confirmPaymentIntent.handle(request, headers, testRunId, account, intentId, now)));
  }

  public StripeApiResponse capturePaymentIntent(StripeApiCall call, String intentId) {
    return answer(
        call,
        StripeOperations.CAPTURE,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.PAYMENT_INTENT),
            (request, headers, testRunId, account, now, _) ->
                capturePaymentIntent.handle(
                    request.form(), headers, testRunId, account, intentId, now)));
  }

  public StripeApiResponse cancelPaymentIntent(StripeApiCall call, String intentId) {
    return answer(
        call,
        StripeOperations.CANCEL,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.PAYMENT_INTENT),
            (request, headers, testRunId, account, now, _) ->
                cancelPaymentIntent.handle(
                    request.form(), headers, testRunId, account, intentId, now)));
  }

  public StripeApiResponse retrievePaymentIntent(StripeApiCall call, String intentId) {
    return answer(
        call,
        StripeOperations.RETRIEVE,
        new StripePublishableAccess.ThePaymentIntentItsClientSecretOpens(intentId),
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.PAYMENT_INTENT),
            (request, headers, testRunId, account, now, _) ->
                retrievePaymentIntent.handle(testRunId, account, intentId, now)));
  }

  public StripeApiResponse retrieveCharge(StripeApiCall call, String chargeId) {
    return answer(
        call,
        StripeOperations.RETRIEVE_CHARGE,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.CHARGE),
            (request, headers, testRunId, account, now, _) ->
                retrieveCharge.handle(testRunId, account, chargeId, now)));
  }

  public StripeApiResponse createRefund(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.REFUND,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.REFUND),
            (request, headers, testRunId, account, now, _) ->
                createRefund.handle(
                    request.form(), headers, testRunId, account, now, ObjectOrigin.PROVIDER_API)));
  }

  public StripeApiResponse createCustomer(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE_CUSTOMER,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.CUSTOMER),
            (request, headers, testRunId, account, now, _) ->
                createCustomer.handle(
                    request.form(), testRunId, account, now, ObjectOrigin.PROVIDER_API)));
  }

  public StripeApiResponse retrieveCustomer(StripeApiCall call, String customerId) {
    return answer(
        call,
        StripeOperations.RETRIEVE_CUSTOMER,
        new StripeShortLivedAccess.TheCustomerTheCallNames(customerId),
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.CUSTOMER),
            (request, headers, testRunId, account, now, _) ->
                retrieveCustomer.handle(testRunId, account, customerId)));
  }

  public StripeApiResponse updateCustomer(StripeApiCall call, String customerId) {
    return answer(
        call,
        StripeOperations.UPDATE_CUSTOMER,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.CUSTOMER),
            (request, headers, testRunId, account, now, _) ->
                updateCustomer.handle(request.form(), testRunId, account, customerId, now)));
  }

  public StripeApiResponse deleteCustomer(StripeApiCall call, String customerId) {
    return answer(
        call,
        StripeOperations.DELETE_CUSTOMER,
        (request, headers, testRunId, account, now, _) ->
            deleteCustomer.handle(testRunId, account, customerId, now));
  }

  public StripeApiResponse createPaymentMethod(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE_PAYMENT_METHOD,
        new StripePublishableAccess.ItsOwnAccountWithoutAClientSecret(),
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.PAYMENT_METHOD),
            (request, headers, testRunId, account, now, _) ->
                createPaymentMethod.handle(
                    request, testRunId, account, now, ObjectOrigin.PROVIDER_API)));
  }

  public StripeApiResponse listPaymentMethods(StripeApiCall call) {
    StripeForm query = StripeForm.of(call.query());
    return answer(
        call,
        StripeOperations.LIST_PAYMENT_METHODS,
        expanding(
            call,
            StripeExpansion.ofAList(StripeExpandable.PAYMENT_METHOD),
            (request, headers, testRunId, account, now, _) ->
                listPaymentMethods.everyMethod(query, testRunId, account)));
  }

  public StripeApiResponse listCustomerPaymentMethods(StripeApiCall call, String customerId) {
    StripeForm query = StripeForm.of(call.query());
    return answer(
        call,
        StripeOperations.LIST_CUSTOMER_PAYMENT_METHODS,
        new StripeShortLivedAccess.TheCustomerTheCallNames(customerId),
        expanding(
            call,
            StripeExpansion.ofAList(StripeExpandable.PAYMENT_METHOD),
            (request, headers, testRunId, account, now, _) ->
                listPaymentMethods.theMethodsOf(query, testRunId, account, customerId)));
  }

  public StripeApiResponse retrievePaymentMethod(StripeApiCall call, String paymentMethodId) {
    return answer(
        call,
        StripeOperations.RETRIEVE_PAYMENT_METHOD,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.PAYMENT_METHOD),
            (request, headers, testRunId, account, now, _) ->
                retrievePaymentMethod.handle(testRunId, account, paymentMethodId)));
  }

  public StripeApiResponse detachPaymentMethod(StripeApiCall call, String paymentMethodId) {
    return answer(
        call,
        StripeOperations.DETACH_PAYMENT_METHOD,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.PAYMENT_METHOD),
            (request, headers, testRunId, account, now, _) ->
                detachPaymentMethod.handle(testRunId, account, paymentMethodId, now)));
  }

  public StripeApiResponse createSetupIntent(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE_SETUP_INTENT,
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.SETUP_INTENT),
            (request, headers, testRunId, account, now, _) ->
                createSetupIntent.handle(
                    request, testRunId, account, now, ObjectOrigin.PROVIDER_API)));
  }

  public StripeApiResponse retrieveSetupIntent(StripeApiCall call, String setupIntentId) {
    return answer(
        call,
        StripeOperations.RETRIEVE_SETUP_INTENT,
        new StripePublishableAccess.TheSetupIntentItsClientSecretOpens(setupIntentId),
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.SETUP_INTENT),
            (request, headers, testRunId, account, now, _) ->
                retrieveSetupIntent.handle(testRunId, account, setupIntentId)));
  }

  public StripeApiResponse confirmSetupIntent(StripeApiCall call, String setupIntentId) {
    return answer(
        call,
        StripeOperations.CONFIRM_SETUP_INTENT,
        new StripePublishableAccess.TheSetupIntentItsClientSecretOpens(setupIntentId),
        expanding(
            call,
            StripeExpansion.ofAnObject(StripeExpandable.SETUP_INTENT),
            (request, headers, testRunId, account, now, _) ->
                confirmSetupIntent.handle(
                    request, headers, testRunId, account, setupIntentId, now)));
  }

  public StripeApiResponse createEphemeralKey(StripeApiCall call) {
    return answer(
        call,
        StripeOperations.CREATE_EPHEMERAL_KEY,
        new StripeShortLivedAccess.NoShortLivedKeyReachesThisRoute(),
        StripeVersionsAdmitted.ALSO_THOSE_THE_MOBILE_SDKS_PIN,
        new StripePublishableAccess.NoPublishableKeyReachesThisRoute(),
        expanding(
            call,
            StripeExpansion.ofAnObjectThatExpandsNothing(),
            (request, headers, testRunId, account, now, _) ->
                createEphemeralKey.handle(request.form(), testRunId, account, now)));
  }

  private StripeOperation expanding(
      StripeApiCall call, StripeExpansion expansion, StripeOperation operation) {
    StripeForm carrying = carrying(call);
    return (request, headers, testRunId, account, now, presented) ->
        expansion.answered(
            carrying,
            presented == StripeKeyKind.PUBLISHABLE_KEY
                ? related.reachedByAPublishableKey(testRunId, account, now)
                : related.reachedBy(testRunId, account, now),
            () -> operation.handle(request, headers, testRunId, account, now, presented));
  }

  private static StripeForm carrying(StripeApiCall call) {
    return StripeForm.of(POST.equals(call.method()) ? call.form() : call.query());
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
      StripePublishableAccess publishable,
      StripeOperation operation) {
    return answer(
        call,
        operationName,
        new StripeShortLivedAccess.NoShortLivedKeyReachesThisRoute(),
        StripeVersionsAdmitted.THE_RANGE_ANSWERED,
        publishable,
        operation);
  }

  private StripeApiResponse answer(
      StripeApiCall call,
      String operationName,
      StripeShortLivedAccess access,
      StripeOperation operation) {
    return answer(
        call,
        operationName,
        access,
        StripeVersionsAdmitted.THE_RANGE_ANSWERED,
        new StripePublishableAccess.NoPublishableKeyReachesThisRoute(),
        operation);
  }

  private StripeApiResponse answer(
      StripeApiCall call,
      String operationName,
      StripeShortLivedAccess access,
      StripeVersionsAdmitted admitted,
      StripePublishableAccess publishable,
      StripeOperation operation) {
    StripeForm form = StripeForm.of(call.form());
    String secretKey = StripeAuthentication.secretKeyIn(call.headers());
    List<StripeAccount> matches = accountsBySecretKey(secretKey);
    List<StripeEphemeralKey> shortLived = shortLivedKeysBySecret(secretKey, matches);
    List<StripeAccount> publishedTo = accountsByPublishableKey(secretKey, matches, shortLived);
    TestRunResolution resolution = resolve(call, matches, shortLived, publishedTo);
    Instant now = clock.now(resolution.testRunId());

    List<StripeAccount> within = within(resolution, matches);
    List<StripeAccount> publishedWithin = within(resolution, publishedTo);
    List<String> named = new ArrayList<>();
    within.forEach(matched -> named.add(matched.accountId()));
    publishedWithin.forEach(matched -> named.add(matched.accountId()));
    String account = TheAccountARowNames.amongTheAccountsTheCredentialsMatched(named);

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
                    publishedWithin,
                    access,
                    admitted,
                    publishable,
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
      List<StripeAccount> publishedTo,
      StripeShortLivedAccess access,
      StripeVersionsAdmitted admitted,
      StripePublishableAccess publishable,
      Instant now,
      String operationName,
      StripeOperation operation) {
    if (matches.size() == 1) {
      return answeredForTheAccount(
          call, form, testRunId, matches.get(0), admitted, now, operationName, operation);
    }
    if (matches.isEmpty() && shortLived.size() == 1) {
      return answeredForAShortLivedKey(
          call, form, testRunId, shortLived.get(0), access, now, operationName, operation);
    }
    if (matches.isEmpty() && shortLived.isEmpty() && publishedTo.size() == 1) {
      return answeredForAPublishableKey(
          call, form, testRunId, publishedTo.get(0), publishable, now, operationName, operation);
    }
    return rendered(
        StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theKeyReachesNoAccount(), StripeErrors.noValidApiKey()),
        Map.of());
  }

  private static @Nullable Answered refusedOnItsEncoding(StripeApiCall call, StripeForm form) {
    StripeError unreadable = form.unreadable();
    if (unreadable == null) {
      unreadable = StripeForm.of(call.query()).unreadable();
    }
    if (unreadable == null) {
      return null;
    }
    return rendered(
        StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theRequestWasRefused(), unreadable),
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
      String operationName,
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
                switch (StripeVersionsAdmitted.ALSO_THOSE_THE_MOBILE_SDKS_PIN.choose(
                    call.headers(), account.apiVersion())) {
                  case StripeVersionChoice.Refused refused -> refusedOnItsVersion(refused);
                  case StripeVersionChoice.AnsweredAt at ->
                      answeredAt(
                          at.version(),
                          readableOrRefused(
                              call,
                              form,
                              () ->
                                  rendered(
                                      handledAt(
                                          operation,
                                          form,
                                          at.version(),
                                          operationName,
                                          call.headers(),
                                          testRunId,
                                          account,
                                          now,
                                          StripeKeyKind.SHORT_LIVED_KEY),
                                      Map.of())));
                })
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
      StripeVersionsAdmitted admitted,
      Instant now,
      String operationName,
      StripeOperation operation) {
    return switch (admitted.choose(call.headers(), account.apiVersion())) {
      case StripeVersionChoice.Refused refused -> refusedOnItsVersion(refused);
      case StripeVersionChoice.AnsweredAt at ->
          answeredAt(
              at.version(),
              readableOrRefused(
                  call,
                  form,
                  () ->
                      answeredAtTheVersion(
                          call,
                          form,
                          testRunId,
                          account,
                          at.version(),
                          now,
                          operationName,
                          operation,
                          StripeKeyKind.SECRET_KEY)));
    };
  }

  private Answered answeredForAPublishableKey(
      StripeApiCall call,
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      StripePublishableAccess publishable,
      Instant now,
      String operationName,
      StripeOperation operation) {
    if (publishable instanceof StripePublishableAccess.NoPublishableKeyReachesThisRoute) {
      return rendered(
          Objects.requireNonNull(
              clientSecrets.theRefusalOf(publishable, carrying(call), testRunId, account)),
          Map.of());
    }
    return switch (StripeVersionsAdmitted.ALSO_THOSE_THE_MOBILE_SDKS_PIN.choose(
        call.headers(), account.apiVersion())) {
      case StripeVersionChoice.Refused refused -> refusedOnItsVersion(refused);
      case StripeVersionChoice.AnsweredAt at ->
          answeredAt(
              at.version(),
              readableOrRefused(
                  call,
                  form,
                  () -> {
                    StripeOperationResult refusal =
                        clientSecrets.theRefusalOf(publishable, carrying(call), testRunId, account);
                    return refusal != null
                        ? rendered(refusal, Map.of())
                        : answeredAtTheVersion(
                            call,
                            form,
                            testRunId,
                            account,
                            at.version(),
                            now,
                            operationName,
                            operation,
                            StripeKeyKind.PUBLISHABLE_KEY);
                  }));
    };
  }

  private static StripeOperationResult handledAt(
      StripeOperation operation,
      StripeForm form,
      StripeApiVersion version,
      String operationName,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      StripeKeyKind presented) {
    StripeOperationResult result =
        operation.handle(
            StripeVersionChanges.atTheCore(form, version, operationName),
            headers,
            testRunId,
            account,
            now,
            presented);
    StripeJson shown =
        StripeVersionChanges.asAnsweredAt(
            presented.asShownTo(result.body()), version, operationName);
    return shown.equals(result.body()) ? result : result.answeringWith(shown);
  }

  private static Answered readableOrRefused(
      StripeApiCall call, StripeForm form, Supplier<Answered> readable) {
    Answered unreadable = refusedOnItsEncoding(call, form);
    return unreadable == null ? readable.get() : unreadable;
  }

  private Answered answeredAtTheVersion(
      StripeApiCall call,
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      StripeApiVersion version,
      Instant now,
      String operationName,
      StripeOperation operation,
      StripeKeyKind presented) {
    String key = StripeIdempotency.keyIn(call.headers());
    if (key == null || !POST.equals(call.method())) {
      return rendered(
          handledAt(
              operation,
              form,
              version,
              operationName,
              call.headers(),
              testRunId,
              account,
              now,
              presented),
          Map.of());
    }
    if (StripeIdempotency.isTooLong(key)) {
      return rendered(
          StripeOperationResult.refusedBeforeExecuting(
              StripeRefusals.theRequestWasRefused(),
              StripeErrors.headerRefused(StripeIdempotency.KEY_IS_TOO_LONG)),
          Map.of());
    }
    return underTheKey(
        call, form, testRunId, account, version, now, operationName, operation, key, presented);
  }

  private static Answered refusedOnItsVersion(StripeVersionChoice.Refused refused) {
    return rendered(
        StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theRequestWasRefused(), refused.error()),
        Map.of());
  }

  private static Answered answeredAt(StripeApiVersion version, Answered answered) {
    Map<String, List<String>> headers = new LinkedHashMap<>(answered.headers());
    headers.put(StripeFields.STRIPE_VERSION_HEADER, List.of(version.wireValue()));
    return new Answered(
        answered.delivery(),
        answered.outcome(),
        answered.providerObjectId(),
        answered.refusal(),
        Collections.unmodifiableMap(new LinkedHashMap<>(headers)),
        answered.scenarioId(),
        answered.bodyForAudit());
  }

  private Answered underTheKey(
      StripeApiCall call,
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      StripeApiVersion version,
      Instant now,
      String operationName,
      StripeOperation operation,
      String key,
      StripeKeyKind presented) {
    FingerprintedRequest request =
        new FingerprintedRequest(call.method(), call.path(), form.compared());
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
              version,
              key,
              operationName,
              request,
              operation,
              form,
              call.headers(),
              now,
              presented);
      case StripeIdempotencyClaim.Replayed replayed ->
          observedReplay(
              testRunId, account, key, operationName, request, replayed.answer(), now, presented);
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
      Instant now,
      StripeKeyKind presented) {
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
    return replayed(answer, presented);
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
      StripeApiVersion version,
      String key,
      String operationName,
      FingerprintedRequest request,
      StripeOperation operation,
      StripeForm form,
      Map<String, List<String>> headers,
      Instant now,
      StripeKeyKind presented) {
    StripeOperationResult result;
    try {
      result =
          handledAt(
              operation, form, version, operationName, headers, testRunId, account, now, presented);
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

  private static Answered replayed(StripeStoredAnswer answer, StripeKeyKind presented) {
    byte[] stored = answer.body().getBytes(StandardCharsets.UTF_8);
    RenderedResponse rendered =
        new RenderedResponse(
            answer.httpStatus(),
            answer.contentType(),
            presented == StripeKeyKind.PUBLISHABLE_KEY
                ? StripeJsonWriter.toBytes(
                    presented.asShownTo(StripeJsonReader.read(answer.body())))
                : stored);
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

  private List<StripeAccount> accountsByPublishableKey(
      @Nullable String presented,
      List<StripeAccount> matches,
      List<StripeEphemeralKey> shortLived) {
    if (presented == null || !matches.isEmpty() || !shortLived.isEmpty()) {
      return List.of();
    }
    return accounts.byPublishableKey(presented);
  }

  private TestRunResolution resolve(
      StripeApiCall call,
      List<StripeAccount> matches,
      List<StripeEphemeralKey> shortLived,
      List<StripeAccount> publishedTo) {
    List<TestRunCandidates> evaluated = new ArrayList<>();
    evaluated.add(TestRunHeader.in(call.headers(), testRuns::exists));
    evaluated.add(call.route().candidatesFor(STRIPE));
    evaluated.add(
        TestRunCandidates.of(
            TestRunResolutionRule.ACCOUNT_CREDENTIALS,
            theRunsTheseCredentialsName(matches, shortLived, publishedTo)));
    return TestRunResolution.of(evaluated, defaultTestRun);
  }

  private static List<TestRunId> theRunsTheseCredentialsName(
      List<StripeAccount> matches,
      List<StripeEphemeralKey> shortLived,
      List<StripeAccount> publishedTo) {
    List<TestRunId> named = new ArrayList<>(testRunsOf(matches));
    for (StripeEphemeralKey key : shortLived) {
      named.add(key.testRunId());
    }
    named.addAll(testRunsOf(publishedTo));
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
            StripeFields.withholdingTheCard(
                Redaction.redact(
                    theParametersItCarried(call, form), StripeFields.SENSITIVE_REQUEST_FIELDS)),
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
