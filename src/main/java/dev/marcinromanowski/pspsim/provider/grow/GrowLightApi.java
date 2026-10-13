package dev.marcinromanowski.pspsim.provider.grow;

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

public final class GrowLightApi {

  public static final ProviderId GROW = ProviderId.of("grow");

  static final int GROW_HTTP_STATUS = 200;

  static final String WIRE_METHOD = "POST";

  private static final int AUDIT_ID_LENGTH = 24;

  private final GrowAccounts accounts;
  private final TestRuns testRuns;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final TestRunId defaultTestRun;
  private final CreatePaymentProcess createPaymentProcess;
  private final CreateTransactionWithToken createTransactionWithToken;
  private final ApproveTransaction approveTransaction;
  private final SettleSuspendedTransaction settleSuspendedTransaction;
  private final RefundTransaction refundTransaction;
  private final GetTransactionInfo getTransactionInfo;
  private final GetPaymentProcessInfo getPaymentProcessInfo;
  private final GetTokenTransactionsByExternalIdentifiers getTokenTransactions;
  private final CancelBitTransaction cancelBitTransaction;
  private final UpdateMyUrl updateMyUrl;

  GrowLightApi(
      GrowAccounts accounts,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      TestRunId defaultTestRun,
      CreatePaymentProcess createPaymentProcess,
      CreateTransactionWithToken createTransactionWithToken,
      ApproveTransaction approveTransaction,
      SettleSuspendedTransaction settleSuspendedTransaction,
      RefundTransaction refundTransaction,
      GetTransactionInfo getTransactionInfo,
      GetPaymentProcessInfo getPaymentProcessInfo,
      GetTokenTransactionsByExternalIdentifiers getTokenTransactions,
      CancelBitTransaction cancelBitTransaction,
      UpdateMyUrl updateMyUrl) {
    this.accounts = accounts;
    this.testRuns = testRuns;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.defaultTestRun = defaultTestRun;
    this.createPaymentProcess = createPaymentProcess;
    this.createTransactionWithToken = createTransactionWithToken;
    this.approveTransaction = approveTransaction;
    this.settleSuspendedTransaction = settleSuspendedTransaction;
    this.refundTransaction = refundTransaction;
    this.getTransactionInfo = getTransactionInfo;
    this.getPaymentProcessInfo = getPaymentProcessInfo;
    this.getTokenTransactions = getTokenTransactions;
    this.cancelBitTransaction = cancelBitTransaction;
    this.updateMyUrl = updateMyUrl;
  }

  @FunctionalInterface
  private interface GrowOperation {
    GrowOperationResult handle(
        GrowForm form,
        Map<String, List<String>> headers,
        TestRunId testRunId,
        List<GrowAccount> credentialMatches,
        Instant now);
  }

  public GrowApiResponse createPaymentProcess(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(
        call,
        form,
        accountsByUserIdAndPageCode(form),
        (fields, headers, testRunId, matches, now) ->
            createPaymentProcess.handle(
                fields, headers, testRunId, matches, now, ObjectOrigin.PROVIDER_API));
  }

  public GrowApiResponse createTransactionWithToken(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(
        call,
        form,
        accountsByUserId(form),
        (fields, headers, testRunId, matches, now) ->
            createTransactionWithToken.handle(
                fields, headers, testRunId, matches, now, ObjectOrigin.PROVIDER_API));
  }

  public GrowApiResponse approveTransaction(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(
        call,
        form,
        accountsByPageCode(form),
        (fields, headers, testRunId, matches, now) ->
            approveTransaction.handle(fields, headers, testRunId, matches));
  }

  public GrowApiResponse settleSuspendedTransaction(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(call, form, accountsByUserId(form), settleSuspendedTransaction::handle);
  }

  public GrowApiResponse refundTransaction(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(
        call,
        form,
        accountsByEitherCredential(form),
        (fields, headers, testRunId, matches, now) ->
            refundTransaction.handle(
                fields, headers, testRunId, matches, now, ObjectOrigin.PROVIDER_API));
  }

  public GrowApiResponse getTransactionInfo(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(call, form, accountsByPageCode(form), getTransactionInfo::handle);
  }

  public GrowApiResponse getPaymentProcessInfo(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(
        call,
        form,
        accountsByPageCode(form),
        (fields, headers, testRunId, matches, now) ->
            getPaymentProcessInfo.handle(fields, headers, testRunId, matches));
  }

  public GrowApiResponse getTokenTransactionsByExternalIdentifiers(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(
        call,
        form,
        accountsByUserId(form),
        (fields, headers, testRunId, matches, now) ->
            getTokenTransactions.handle(fields, headers, testRunId, matches));
  }

  public GrowApiResponse updateMyUrl(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(
        call,
        form,
        List.of(),
        (fields, headers, testRunId, matches, now) -> updateMyUrl.handle(fields, testRunId));
  }

  public GrowApiResponse cancelBitTransaction(GrowApiCall call) {
    GrowForm form = GrowForm.of(call.form());
    return answer(
        call,
        form,
        accountsByPageCode(form),
        (fields, headers, testRunId, matches, now) -> cancelBitTransaction.handle(fields));
  }

  private GrowApiResponse answer(
      GrowApiCall call, GrowForm form, List<GrowAccount> matches, GrowOperation operation) {
    TestRunResolution resolution = resolve(call, matches);
    Instant now = clock.now(resolution.testRunId());
    List<GrowAccount> within = within(resolution, matches);
    String account =
        TheAccountARowNames.amongTheAccountsTheCredentialsMatched(
            within.stream().map(GrowAccount::accountId).toList());

    recordInbound(call, resolution, account, now, form);
    GrowOperationResult result =
        operation.handle(form, call.headers(), resolution.testRunId(), within, now);
    FaultedDelivery delivery = deliveryOf(result, wireFormOf(within));
    recordOutbound(call, resolution, account, now, result, delivery);

    return new GrowApiResponse(
        resolution.testRunId(), delivery, result.outcome(), result.providerObjectId());
  }

  private static FaultedDelivery deliveryOf(GrowOperationResult result, GrowWireForm wireForm) {
    FaultedDelivery insteadOfTheOperation = result.insteadOfTheOperation();
    if (insteadOfTheOperation != null) {
      return insteadOfTheOperation;
    }
    RenderedResponse rendered =
        new RenderedResponse(
            GROW_HTTP_STATUS,
            wireForm.contentType(),
            GrowJsonWriter.toBytes(result.body(), wireForm));
    TransportFault fault = result.fault();
    return fault == null ? rendered.asDelivered() : fault.applyTo(rendered);
  }

  private static GrowWireForm wireFormOf(List<GrowAccount> within) {
    return within.size() == 1 ? within.get(0).wireForm() : GrowWireForm.of(null);
  }

  private TestRunResolution resolve(GrowApiCall call, List<GrowAccount> matches) {
    List<TestRunCandidates> evaluated = new ArrayList<>();
    evaluated.add(TestRunHeader.in(call.headers(), testRuns::exists));
    evaluated.add(call.route().candidatesFor(GROW));
    evaluated.add(
        TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, testRunsOf(matches)));
    return TestRunResolution.of(evaluated, defaultTestRun);
  }

  private List<GrowAccount> accountsByUserIdAndPageCode(GrowForm form) {
    String userId = form.first(GrowFields.USER_ID);
    String pageCode = form.first(GrowFields.PAGE_CODE);
    if (userId == null || pageCode == null) {
      return List.of();
    }
    return accounts.byUserIdAndPageCode(userId, pageCode);
  }

  private List<GrowAccount> accountsByUserId(GrowForm form) {
    String userId = form.first(GrowFields.USER_ID);
    if (userId == null) {
      return List.of();
    }
    return accounts.byUserId(userId);
  }

  private List<GrowAccount> accountsByPageCode(GrowForm form) {
    String pageCode = form.first(GrowFields.PAGE_CODE);
    if (pageCode == null) {
      return List.of();
    }
    return accounts.byPageCode(pageCode);
  }

  private List<GrowAccount> accountsByEitherCredential(GrowForm form) {
    return accounts.byEitherCredential(
        form.first(GrowFields.USER_ID), form.first(GrowFields.PAGE_CODE));
  }

  private static List<GrowAccount> within(TestRunResolution resolution, List<GrowAccount> matches) {
    return resolution
        .resolvedTestRun()
        .map(testRunId -> accountsOf(testRunId, matches))
        .orElseGet(List::of);
  }

  private static List<GrowAccount> accountsOf(TestRunId testRunId, List<GrowAccount> matches) {
    return matches.stream().filter(account -> account.testRunId().equals(testRunId)).toList();
  }

  private static List<TestRunId> testRunsOf(List<GrowAccount> matches) {
    return matches.stream().map(GrowAccount::testRunId).toList();
  }

  private void recordInbound(
      GrowApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      GrowForm form) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            GROW,
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
            sanitizedHeaders(call),
            Redaction.redact(form.forAudit(), GrowFields.SENSITIVE_REQUEST_FIELDS),
            null));
  }

  private void recordOutbound(
      GrowApiCall call,
      TestRunResolution resolution,
      @Nullable String accountId,
      Instant now,
      GrowOperationResult result,
      FaultedDelivery delivery) {
    audit.record(
        new AuditRecord(
            auditId(),
            resolution.testRunId(),
            resolution.rule(),
            GROW,
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
            Redaction.redact(result.body().flatten(), GrowFields.SENSITIVE_RESPONSE_FIELDS),
            new DeliveredResponse(
                delivery.fault(),
                delivery.declaredContentLength(),
                delivery.writtenBodyBytes(),
                delivery.delay())));
  }

  private static Map<String, List<String>> sanitizedHeaders(GrowApiCall call) {
    return Redaction.redact(call.headers(), GrowFields.SENSITIVE_HEADERS);
  }

  private String auditId() {
    return "aud_" + ids.hex(AUDIT_ID_LENGTH);
  }
}
