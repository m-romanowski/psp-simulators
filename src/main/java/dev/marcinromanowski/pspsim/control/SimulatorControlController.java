package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.control.ControlPayloads.AccountRemovedResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AuditRecordView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ClockResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ConfigurationResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlRefusal;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DeliverDueResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.FixtureRemovedResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.FixtureResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.FollowUpResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.HostedCompletionResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.PersonaPlanResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.PersonaSeededResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ResetResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.RouteAliasReleasedResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ScenarioResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.StateResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.TheWordsARefusalWasAnsweredIn;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookDeliveryView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookJobView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookResponse;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.audit.DeclaredAuditQuery;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleReport;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredAdvance;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionReport;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.BundleAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ClockAdvanceAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ClockAdvanceFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationFacts;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredBundleImport;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredFollowUp;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredHostedCompletion;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersonaSeeding;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredReproductionImport;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredReset;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredScenarioCopy;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredWebhookCall;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpControl;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureFacts;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectAddressing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ReproductionAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ReproductionBundles;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.RunBundles;
import dev.marcinromanowski.pspsim.kernel.spi.RunResets;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioComparison;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioCopy;
import dev.marcinromanowski.pspsim.kernel.spi.SeededPersona;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowACompletedHostedPageLeaves;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookControl;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptRefusedException;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

@RestController
@RequestMapping("/__simulator/{provider}")
class SimulatorControlController {

  private static final String PAYMENT_METHODS = "/fixtures/payment-methods";

  private static final String ACCOUNT_ID = "accountId";

  private static final String FIXTURE_ID = "fixtureId";

  private static final String SCENARIO_ID = "scenarioId";

  private static final String FROM = "from";

  private static final String PROVIDER_OBJECT_ID = "providerObjectId";

  private static final String FOLLOW_UP = "followUp";

  private static final String POST = "POST";

  private static final String PUT = "PUT";

  private static final String DELETE = "DELETE";

  private final ProviderRegistry providers;
  private final ConfigurationAdmission configurations;
  private final ClockAdvanceAdmission clocks;
  private final SimulatorClock clock;
  private final WebhookDispatcher deliveries;
  private final ScenarioRules scenarios;
  private final ScenarioAdmission admission;
  private final PaymentMethodFixtureAdmission fixtures;
  private final PersonaAdmission personas;
  private final PaymentMethodFixtures seeded;
  private final RunBundles exports;
  private final BundleAdmission bundles;
  private final ReproductionBundles captures;
  private final ReproductionAdmission reproductions;
  private final AuditReads audit;
  private final ActionTrail actions;
  private final TheReadsAProviderPublishes theReads;
  private final ThePaymentsAProviderStarts theOriginations;
  private final TestRunId defaultTestRun;
  private final String publicBaseUrl;
  private final RunResets resets;

  SimulatorControlController(
      ProviderRegistry providers,
      ConfigurationAdmission configurations,
      ClockAdvanceAdmission clocks,
      SimulatorClock clock,
      WebhookDispatcher deliveries,
      ScenarioRules scenarios,
      ScenarioAdmission admission,
      PaymentMethodFixtureAdmission fixtures,
      PersonaAdmission personas,
      PaymentMethodFixtures seeded,
      RunBundles exports,
      BundleAdmission bundles,
      ReproductionBundles captures,
      ReproductionAdmission reproductions,
      AuditReads audit,
      ActionTrail actions,
      TheReadsAProviderPublishes theReads,
      ThePaymentsAProviderStarts theOriginations,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId,
      @Value("${simulator.public-base-url}") String publicBaseUrl,
      RunResets resets) {
    this.providers = providers;
    this.configurations = configurations;
    this.clocks = clocks;
    this.clock = clock;
    this.deliveries = deliveries;
    this.scenarios = scenarios;
    this.admission = admission;
    this.fixtures = fixtures;
    this.personas = personas;
    this.seeded = seeded;
    this.exports = exports;
    this.bundles = bundles;
    this.captures = captures;
    this.reproductions = reproductions;
    this.audit = audit;
    this.actions = actions;
    this.theReads = theReads;
    this.theOriginations = theOriginations;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
    this.publicBaseUrl = publicBaseUrl;
    this.resets = resets;
  }

  @PostMapping("/reset")
  ResponseEntity<Object> reset(
      @PathVariable String provider, @RequestBody(required = false) @Nullable DeclaredReset body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    DeclaredReset request = body == null ? new DeclaredReset(null, null) : body;
    String path = pathOf(provider, "/reset");

    TestRunId testRunId;
    ResetScope scope;
    try {
      testRunId = TestRunId.declaredOr(request.testRunId(), defaultTestRun);
      scope = ResetScope.declared(request.scope());
    } catch (IllegalArgumentException rejected) {
      return refusing(
          module.get(),
          TestRunId.theRunARowAboutThisRequestBelongsTo(request.testRunId(), defaultTestRun),
          POST,
          path,
          ActionVerdict.rejected(
              RefusalSubject.noObjectWasNamed(
                  DeclaredReset.THE_RESET_IT_ASKED_THIS_PROVIDER_TO_PERFORM)),
          ActionFacts.none().asRecorded(),
          new ControlError(String.valueOf(rejected.getMessage())));
    }

    ResetOutcome outcome = resets.ofOne(module.get(), testRunId, scope);
    actions.record(
        AuditedAction.applied(
            module.get().id(),
            testRunId,
            ObjectOrigin.CONTROL_API,
            POST,
            path,
            ActionVerdict.applied(HttpStatus.OK.value()),
            ActionFacts.none()
                .and("scope", scope.name())
                .andCount("clearedObjects", outcome.clearedObjects())
                .andCount("clearedAuditRecords", outcome.clearedAuditRecords())
                .asRecorded()));
    return ResponseEntity.ok(
        new ResetResponse(
            scope == ResetScope.ALL ? null : testRunId.value(),
            provider,
            scope.name(),
            outcome.clearedObjects(),
            outcome.clearedAuditRecords()));
  }

  @PutMapping("/config")
  ResponseEntity<Object> configure(
      @PathVariable String provider, @RequestBody DeclaredConfiguration body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/config");
    Map<String, List<String>> facts = ConfigurationFacts.of(module.get().accountFields(), body);

    return switch (configurations.apply(module.get(), body)) {
      case ConfigurationAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun),
              PUT,
              path,
              rejected.theRefusalItReports(),
              facts,
              new ControlError(rejected.message()));
      case ConfigurationAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun),
              PUT,
              path,
              refused.theRefusalItReports(),
              facts,
              new ControlError(refused.refusal().message()));
      case ConfigurationAdmission.Verdict.Conflicting conflicting ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun),
              PUT,
              path,
              conflicting.theRefusalItReports(),
              facts,
              new ControlError(conflicting.refusal().message()));
      case ConfigurationAdmission.Verdict.Applied applied -> {
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                applied.testRunId(),
                ObjectOrigin.CONTROL_API,
                PUT,
                path,
                ActionVerdict.applied(HttpStatus.OK.value()),
                facts));
        yield ResponseEntity.ok(
            new ConfigurationResponse(
                applied.testRunId().value(),
                provider,
                applied.accountIds(),
                applied.webhookTargets(),
                applied.routeAliases(),
                applied.now()));
      }
    };
  }

  @PostMapping("/clock/advance")
  ResponseEntity<Object> advanceTheClock(
      @PathVariable String provider,
      @RequestBody(required = false) @Nullable DeclaredAdvance body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    DeclaredAdvance request = body == null ? new DeclaredAdvance(null, null) : body;
    String path = pathOf(provider, "/clock/advance");
    Map<String, List<String>> asked = ClockAdvanceFacts.asked(request.advanceBy());

    return switch (clocks.advance(request)) {
      case ClockAdvanceAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(request.testRunId(), defaultTestRun),
              POST,
              path,
              rejected.theRefusalItReports(),
              asked,
              new ControlError(rejected.message()));
      case ClockAdvanceAdmission.Verdict.NotPinned following ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(request.testRunId(), defaultTestRun),
              POST,
              path,
              following.theRefusalItReports(),
              asked,
              new ControlError(following.message()));
      case ClockAdvanceAdmission.Verdict.Advanced advanced -> {
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                advanced.testRunId(),
                ObjectOrigin.CONTROL_API,
                POST,
                path,
                ActionVerdict.applied(HttpStatus.OK.value()),
                ClockAdvanceFacts.of(advanced)));
        yield ResponseEntity.ok(
            new ClockResponse(
                advanced.testRunId().value(),
                provider,
                advanced.by().toString(),
                advanced.movedFrom(),
                advanced.now()));
      }
    };
  }

  @DeleteMapping("/config/accounts/{accountId}")
  ResponseEntity<Object> forgetAccount(
      @PathVariable String provider,
      @PathVariable String accountId,
      @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/config/accounts/" + accountId);
    Map<String, List<String>> asked = ActionFacts.none().and(ACCOUNT_ID, accountId).asRecorded();
    return switch (configurations.forget(module.get(), testRunId, accountId)) {
      case ConfigurationAdmission.Removal.Rejected rejected ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
              DELETE,
              path,
              rejected.theRefusalItReports(),
              asked,
              new ControlError(rejected.message()));
      case ConfigurationAdmission.Removal.NotConfigured absent ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
              DELETE,
              path,
              absent.theRefusalItReports(),
              asked,
              new ControlError(absent.message()));
      case ConfigurationAdmission.Removal.Removed removed -> {
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                removed.testRunId(),
                ObjectOrigin.CONTROL_API,
                DELETE,
                pathOf(provider, "/config/accounts/" + removed.accountId()),
                ActionVerdict.applied(HttpStatus.OK.value()),
                ActionFacts.none()
                    .and("removedAccount", removed.accountId())
                    .andEach("configuredAccounts", removed.accountIds())
                    .asRecorded()));
        yield ResponseEntity.ok(
            new AccountRemovedResponse(
                removed.testRunId().value(), provider, removed.accountId(), removed.accountIds()));
      }
    };
  }

  @DeleteMapping("/config/route-aliases")
  ResponseEntity<Object> releaseRouteAlias(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String from) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/config/route-aliases");
    Map<String, List<String>> asked = ActionFacts.none().and(FROM, from).asRecorded();
    return switch (configurations.releaseRouteAlias(module.get(), testRunId, from)) {
      case ConfigurationAdmission.Release.Rejected rejected ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
              DELETE,
              path,
              rejected.theRefusalItReports(),
              asked,
              new ControlError(rejected.message()));
      case ConfigurationAdmission.Release.NotBound absent ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
              DELETE,
              path,
              absent.theRefusalItReports(),
              asked,
              new ControlError(absent.message()));
      case ConfigurationAdmission.Release.Released released -> {
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                released.testRunId(),
                ObjectOrigin.CONTROL_API,
                DELETE,
                path,
                ActionVerdict.applied(HttpStatus.OK.value()),
                ActionFacts.none()
                    .and("releasedRouteAlias", released.from())
                    .andEach("configuredRouteAliases", released.routeAliases())
                    .asRecorded()));
        yield ResponseEntity.ok(
            new RouteAliasReleasedResponse(
                released.testRunId().value(), provider, released.from(), released.routeAliases()));
      }
    };
  }

  @PostMapping("/fixtures/payment-methods")
  ResponseEntity<Object> seedPaymentMethod(
      @PathVariable String provider, @RequestBody DeclaredPaymentMethodFixture body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, PAYMENT_METHODS);
    TestRunId whereItBelongs =
        TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun);
    return switch (fixtures.seed(module.get(), body, ObjectOrigin.CONTROL_API)) {
      case PaymentMethodFixtureAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              rejected.theRefusalItReports(),
              ActionFacts.none().asRecorded(),
              new ControlError(rejected.message()));
      case PaymentMethodFixtureAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              refused.theRefusalItReports(),
              ActionFacts.none()
                  .andEach(ActionFacts.REFUSED_PROPERTIES, List.of(refused.property()))
                  .asRecorded(),
              new ControlRefusal(refused.inItsOwnWords(), List.of(refused.property())));
      case PaymentMethodFixtureAdmission.Verdict.Conflicting conflicting ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              conflicting.theRefusalItReports(),
              ActionFacts.none().asRecorded(),
              new ControlError(conflicting.message()));
      case PaymentMethodFixtureAdmission.Verdict.Seeded wasSeeded -> {
        PaymentMethodFixture fixture = wasSeeded.fixture();
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                fixture.testRunId(),
                ObjectOrigin.CONTROL_API,
                POST,
                path,
                ActionVerdict.applied(HttpStatus.CREATED.value()),
                PaymentMethodFixtureFacts.of(fixture)));
        yield ResponseEntity.status(HttpStatus.CREATED).body(ControlViews.viewOf(fixture));
      }
    };
  }

  @GetMapping("/fixtures/payment-methods")
  ResponseEntity<Object> paymentMethods(
      @PathVariable String provider, @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    List<FixtureResponse> views = new ArrayList<>();
    for (PaymentMethodFixture fixture : seeded.of(module.get().id(), run)) {
      views.add(ControlViews.viewOf(fixture));
    }
    return ResponseEntity.ok(views);
  }

  @DeleteMapping("/fixtures/payment-methods/{fixtureId}")
  ResponseEntity<Object> forgetPaymentMethod(
      @PathVariable String provider,
      @PathVariable String fixtureId,
      @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, PAYMENT_METHODS + "/" + fixtureId);
    Map<String, List<String>> asked = ActionFacts.none().and(FIXTURE_ID, fixtureId).asRecorded();
    return switch (fixtures.forget(module.get(), testRunId, fixtureId)) {
      case PaymentMethodFixtureAdmission.Removal.Rejected rejected ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
              DELETE,
              path,
              rejected.theRefusalItReports(),
              asked,
              new ControlError(rejected.message()));
      case PaymentMethodFixtureAdmission.Removal.NotSeeded absent ->
          refusing(
              module.get(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
              DELETE,
              path,
              absent.theRefusalItReports(),
              asked,
              new ControlError(absent.message()));
      case PaymentMethodFixtureAdmission.Removal.Removed removed -> {
        PaymentMethodFixture fixture = removed.fixture();
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                fixture.testRunId(),
                ObjectOrigin.CONTROL_API,
                DELETE,
                pathOf(provider, PAYMENT_METHODS + "/" + fixture.fixtureId()),
                ActionVerdict.applied(HttpStatus.OK.value()),
                PaymentMethodFixtureFacts.of(fixture)));
        yield ResponseEntity.ok(
            new FixtureRemovedResponse(fixture.testRunId().value(), provider, fixture.fixtureId()));
      }
    };
  }

  @PostMapping("/webhooks")
  ResponseEntity<Object> webhook(
      @PathVariable String provider, @RequestBody DeclaredWebhookCall body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/webhooks");
    TestRunId whereItBelongs =
        TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun);
    Map<String, List<String>> asked =
        ActionFacts.none()
            .and("operation", body.operation())
            .and("eventType", body.eventType())
            .and(PROVIDER_OBJECT_ID, body.providerObjectId())
            .and(DeclaredWebhookCall.WEBHOOK_JOB_ID, body.webhookJobId())
            .asRecorded();

    TestRunId testRunId;
    WebhookControl.Operation operation;
    List<String> deliveryIds;
    try {
      testRunId = TestRunId.declaredOr(body.testRunId(), defaultTestRun);
      operation = WebhookControl.operationOf(body.operation());
      deliveryIds =
          switch (operation) {
            case SEND ->
                WebhookControl.send(module.get(), testRunId, body, ObjectOrigin.CONTROL_API);
            case RETRY -> List.of(WebhookControl.requiredWebhookJobId(body));
          };
    } catch (IllegalArgumentException rejected) {
      return refusing(
          module.get(),
          whereItBelongs,
          POST,
          path,
          ActionVerdict.rejected(
              RefusalSubject.noObjectWasNamed(
                  DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE)),
          asked,
          new ControlError(String.valueOf(rejected.getMessage())));
    } catch (WebhookNotSchedulableException refused) {
      return refusing(
          module.get(),
          whereItBelongs,
          POST,
          path,
          ActionVerdict.refused(
              RefusalSubject.noObjectWasNamed(
                  DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE)),
          asked,
          new ControlError(String.valueOf(refused.getMessage())));
    } catch (UnknownProviderObjectException unknown) {
      return refusing(
          module.get(),
          whereItBelongs,
          POST,
          path,
          ActionVerdict.notFound(RefusalSubject.noObjectAnsweredTo(PROVIDER_OBJECT_ID)),
          asked,
          new ControlError(String.valueOf(unknown.getMessage())));
    }

    if (operation == WebhookControl.Operation.RETRY) {
      try {
        deliveries.retry(module.get().id(), testRunId, deliveryIds.get(0));
      } catch (AttemptRefusedException refused) {
        return refusing(
            module.get(),
            testRunId,
            POST,
            path,
            refused.theRefusalItReports(),
            asked,
            new ControlError(String.valueOf(refused.getMessage())));
      }
    }

    List<WebhookJobView> jobs = new ArrayList<>();
    for (String deliveryId : deliveryIds) {
      deliveries
          .find(module.get().id(), testRunId, deliveryId)
          .map(ControlViews::jobOf)
          .ifPresent(jobs::add);
    }
    if (jobs.isEmpty()) {
      boolean retrying = operation == WebhookControl.Operation.RETRY;
      return refusing(
          module.get(),
          testRunId,
          POST,
          path,
          ActionVerdict.notFound(
              retrying
                  ? RefusalSubject.noObjectAnsweredTo(DeclaredWebhookCall.WEBHOOK_JOB_ID)
                  : RefusalSubject.noObjectWasNamed(
                      DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE)),
          asked,
          new ControlError(
              retrying
                  ? ControlRefusals.noWebhookJob(provider, testRunId)
                  : ControlRefusals.nothingWasScheduled(provider, testRunId)));
    }
    WebhookJobView first = jobs.get(0);
    int answered = operation.successStatusCode();
    List<String> jobIds = new ArrayList<>();
    for (WebhookJobView job : jobs) {
      jobIds.add(job.webhookJobId());
    }
    actions.record(
        AuditedAction.applied(
            module.get().id(),
            testRunId,
            ObjectOrigin.CONTROL_API,
            POST,
            path,
            ActionVerdict.applied(answered),
            body.providerObjectId(),
            ActionFacts.none()
                .and("operation", operation.name())
                .and("eventType", body.eventType())
                .andEach("webhookJobIds", jobIds)
                .asRecorded()));
    return ResponseEntity.status(answered)
        .body(
            new WebhookResponse(
                first.webhookJobId(),
                first.eventId(),
                first.status(),
                first.attemptsMade(),
                first.scheduledAt(),
                jobs));
  }

  @PostMapping("/webhooks/deliver-due")
  ResponseEntity<Object> deliverDue(
      @PathVariable String provider,
      @RequestBody(required = false) @Nullable DeclaredWebhookCall body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/webhooks/deliver-due");
    String declaredTestRun = body == null ? null : body.testRunId();
    TestRunId testRunId;
    try {
      testRunId = TestRunId.declaredOr(declaredTestRun, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return refusing(
          module.get(),
          TestRunId.theRunARowAboutThisRequestBelongsTo(declaredTestRun, defaultTestRun),
          POST,
          path,
          ActionVerdict.rejected(
              RefusalSubject.noObjectWasNamed(
                  DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE)),
          ActionFacts.none().asRecorded(),
          new ControlError(String.valueOf(rejected.getMessage())));
    }
    int attempted = deliveries.deliverDue(module.get().id(), testRunId);
    actions.record(
        AuditedAction.applied(
            module.get().id(),
            testRunId,
            ObjectOrigin.CONTROL_API,
            POST,
            path,
            ActionVerdict.applied(HttpStatus.OK.value()),
            ActionFacts.none().andCount("attempted", attempted).asRecorded()));
    return ResponseEntity.ok(new DeliverDueResponse(testRunId.value(), provider, attempted));
  }

  @GetMapping("/webhooks")
  ResponseEntity<Object> webhooks(
      @PathVariable String provider, @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    List<WebhookDeliveryView> views = new ArrayList<>();
    for (WebhookDelivery delivery : deliveries.of(module.get().id(), run)) {
      views.add(ControlViews.viewOf(delivery, deliveries.attemptsOf(delivery.deliveryId())));
    }
    return ResponseEntity.ok(views);
  }

  @PostMapping("/scenarios")
  ResponseEntity<Object> createScenario(
      @PathVariable String provider, @RequestBody DeclaredScenario body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/scenarios");
    TestRunId whereItBelongs =
        TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun);
    return switch (admission.write(module.get(), body)) {
      case ScenarioAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              rejected.theRefusalItReports(),
              ActionFacts.none().asRecorded(),
              new ControlError(rejected.message()));
      case ScenarioAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              refused.theRefusalItReports(),
              ActionFacts.none()
                  .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(refused.refusals()))
                  .asRecorded(),
              unprocessable(refused.refusals()));
      case ScenarioAdmission.Verdict.Written written -> {
        ScenarioRule rule = written.rules().getFirst();
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                rule.testRunId(),
                ObjectOrigin.CONTROL_API,
                POST,
                path,
                ActionVerdict.applied(HttpStatus.CREATED.value()),
                ActionFacts.none()
                    .andEach("scenarioIds", scenarioIdsOf(written.rules()))
                    .asRecorded()));
        yield ResponseEntity.status(HttpStatus.CREATED).body(ControlViews.viewOf(provider, rule));
      }
    };
  }

  @PostMapping("/scenarios/presets")
  ResponseEntity<Object> selectPreset(
      @PathVariable String provider, @RequestBody DeclaredPreset body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/scenarios/presets");
    TestRunId whereItBelongs =
        TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun);
    return switch (admission.expand(module.get(), body)) {
      case ScenarioAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              rejected.theRefusalItReports(),
              ActionFacts.none().and("preset", body.preset()).asRecorded(),
              new ControlError(rejected.message()));
      case ScenarioAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              refused.theRefusalItReports(),
              ActionFacts.none()
                  .and("preset", body.preset())
                  .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(refused.refusals()))
                  .asRecorded(),
              unprocessable(refused.refusals()));
      case ScenarioAdmission.Verdict.Written written -> {
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                written.rules().getFirst().testRunId(),
                ObjectOrigin.CONTROL_API,
                POST,
                path,
                ActionVerdict.applied(HttpStatus.CREATED.value()),
                ActionFacts.none()
                    .and("preset", body.preset())
                    .andEach("scenarioIds", scenarioIdsOf(written.rules()))
                    .asRecorded()));
        yield ResponseEntity.status(HttpStatus.CREATED).body(viewsOf(provider, written.rules()));
      }
    };
  }

  @PostMapping("/scenarios/presets/preview")
  ResponseEntity<Object> previewPreset(
      @PathVariable String provider, @RequestBody DeclaredPreset body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    return switch (admission.preview(module.get(), body)) {
      case ScenarioAdmission.Expansion.Rejected rejected -> badRequest(rejected.message());
      case ScenarioAdmission.Expansion.Refused refused ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
              .body(unprocessable(refused.refusals()));
      case ScenarioAdmission.Expansion.Expanded expanded ->
          ResponseEntity.ok(expanded.asDocument(module.get().id()));
    };
  }

  @GetMapping("/personas")
  ResponseEntity<Object> personas(@PathVariable String provider) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    return ResponseEntity.ok(ControlViews.viewOf(personas.publishedBy(module.get())));
  }

  @PostMapping("/personas/preview")
  ResponseEntity<Object> previewPersona(
      @PathVariable String provider, @RequestBody DeclaredPersonaSeeding body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    return switch (personas.whatItWouldCreate(module.get(), body)) {
      case PersonaAdmission.Plan.Rejected rejected -> badRequest(rejected.message());
      case PersonaAdmission.Plan.Unknown unknown -> notFound(unknown.message());
      case PersonaAdmission.Plan.Refused refused ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
              .body(unprocessable(refused.refusals()));
      case PersonaAdmission.Plan.Planned planned ->
          ResponseEntity.ok(
              new PersonaPlanResponse(
                  provider,
                  planned.testRunId().value(),
                  planned.persona().name(),
                  planned.persona().intent().publishedName(),
                  planned.rules().isEmpty()
                      ? null
                      : ScenarioDocument.of(
                          planned.testRunId(), module.get().id(), planned.rules()),
                  ControlViews.viewsOf(planned.persona().fixtures()),
                  planned.theRunAlreadyHoldsIt(),
                  planned.theRefusalThisPlanCannotForesee()));
    };
  }

  @PostMapping("/personas")
  ResponseEntity<Object> seedPersona(
      @PathVariable String provider, @RequestBody DeclaredPersonaSeeding body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/personas");
    TestRunId whereItBelongs =
        TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun);
    Map<String, List<String>> asked =
        ActionFacts.none()
            .and(PersonaAdmission.PERSONA, String.valueOf(body.persona()))
            .asRecorded();
    return switch (personas.seed(module.get(), body, ObjectOrigin.CONTROL_API)) {
      case PersonaAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              rejected.theRefusalItReports(),
              asked,
              new ControlError(rejected.message()));
      case PersonaAdmission.Verdict.Unknown unknown ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              unknown.theRefusalItReports(),
              asked,
              new ControlError(unknown.message()));
      case PersonaAdmission.Verdict.Conflicting conflicting ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              conflicting.theRefusalItReports(),
              asked,
              new ControlError(conflicting.message()));
      case PersonaAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              refused.theRefusalItReports(),
              ActionFacts.none()
                  .and(PersonaAdmission.PERSONA, String.valueOf(body.persona()))
                  .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(refused.refusals()))
                  .asRecorded(),
              unprocessable(refused.refusals()));
      case PersonaAdmission.Verdict.Seeded wasSeeded -> {
        SeededPersona persona = wasSeeded.seeded();
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                persona.testRunId(),
                ObjectOrigin.CONTROL_API,
                POST,
                path,
                ActionVerdict.applied(HttpStatus.CREATED.value()),
                ActionFacts.none()
                    .and(PersonaAdmission.PERSONA, persona.name())
                    .andEach("scenarioIds", persona.theScenarioIdsItWrote())
                    .andEach("fixtureIds", persona.theFixtureIdsItSeeded())
                    .asRecorded()));
        yield ResponseEntity.status(HttpStatus.CREATED)
            .body(
                new PersonaSeededResponse(
                    provider,
                    persona.testRunId().value(),
                    persona.name(),
                    persona.intent().publishedName(),
                    viewsOf(provider, persona.rules()),
                    viewsOfFixtures(persona.fixtures())));
      }
    };
  }

  @PostMapping("/scenarios/import")
  ResponseEntity<Object> importScenarios(
      @PathVariable String provider, @RequestBody ScenarioDocument body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/scenarios/import");
    TestRunId whereItBelongs =
        TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun);
    return switch (admission.takeBack(module.get(), body)) {
      case ScenarioAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              rejected.theRefusalItReports(),
              ActionFacts.none().and("preset", body.preset()).asRecorded(),
              new ControlError(rejected.message()));
      case ScenarioAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              refused.theRefusalItReports(),
              ActionFacts.none()
                  .and("preset", body.preset())
                  .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(refused.refusals()))
                  .asRecorded(),
              unprocessable(refused.refusals()));
      case ScenarioAdmission.Verdict.Written written -> {
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                written.rules().getFirst().testRunId(),
                ObjectOrigin.CONTROL_API,
                POST,
                path,
                ActionVerdict.applied(HttpStatus.CREATED.value()),
                ActionFacts.none()
                    .and("preset", body.preset())
                    .andEach("scenarioIds", scenarioIdsOf(written.rules()))
                    .asRecorded()));
        yield ResponseEntity.status(HttpStatus.CREATED).body(viewsOf(provider, written.rules()));
      }
    };
  }

  @GetMapping("/scenarios")
  ResponseEntity<Object> scenarios(
      @PathVariable String provider, @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    return ResponseEntity.ok(viewsOf(provider, scenarios.of(module.get().id(), run)));
  }

  @GetMapping("/scenarios/{scenarioId}/diff")
  ResponseEntity<Object> compareScenarios(
      @PathVariable String provider,
      @PathVariable String scenarioId,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String against,
      @RequestParam(required = false) @Nullable String againstProvider,
      @RequestParam(required = false) @Nullable String againstTestRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    Optional<SimulatorProvider> other =
        againstProvider == null ? module : providers.find(againstProvider);
    if (other.isEmpty()) {
      return notFound();
    }
    if (against == null) {
      return badRequest(ControlRefusals.noSecondScenarioRuleWasNamed());
    }
    TestRunId run;
    TestRunId otherRun;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
      otherRun = TestRunId.declaredOr(againstTestRunId, run);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    Optional<ScenarioRule> onTheLeft = scenarios.find(module.get().id(), run, scenarioId);
    if (onTheLeft.isEmpty()) {
      return notFound(ControlRefusals.noScenarioRuleCalled(provider, run, scenarioId));
    }
    Optional<ScenarioRule> onTheRight = scenarios.find(other.get().id(), otherRun, against);
    if (onTheRight.isEmpty()) {
      return notFound(
          ControlRefusals.noScenarioRuleCalled(other.get().id().value(), otherRun, against));
    }
    return switch (ScenarioComparison.of(
        onTheLeft.get(), onTheRight.get(), module.get().vocabulary())) {
      case ScenarioComparison.Comparison.OfTwoProviders refused ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
              .body(new ControlRefusal(refused.reason(), List.of("provider")));
      case ScenarioComparison.Comparison.Compared compared -> ResponseEntity.ok(compared);
    };
  }

  @PostMapping("/scenarios/{scenarioId}/copy")
  ResponseEntity<Object> copyScenario(
      @PathVariable String provider,
      @PathVariable String scenarioId,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestBody(required = false) @Nullable DeclaredScenarioCopy body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/scenarios/" + scenarioId + "/copy");
    Map<String, List<String>> asked = ActionFacts.none().and("copiedFrom", scenarioId).asRecorded();
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return refusing(
          module.get(),
          TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
          POST,
          path,
          ActionVerdict.rejected(
              RefusalSubject.noObjectWasNamed(
                  ScenarioCopy.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY)),
          asked,
          new ControlError(String.valueOf(rejected.getMessage())));
    }
    Optional<ScenarioRule> source = scenarios.find(module.get().id(), run, scenarioId);
    if (source.isEmpty()) {
      return refusing(
          module.get(),
          run,
          POST,
          path,
          ActionVerdict.notFound(
              RefusalSubject.noObjectWasNamed(
                  ScenarioCopy.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY)),
          asked,
          new ControlError(ControlRefusals.noScenarioRuleCalled(provider, run, scenarioId)));
    }
    DeclaredScenarioCopy declared = body == null ? DeclaredScenarioCopy.asItStands() : body;
    ScenarioCopy.Copy copy = ScenarioCopy.of(source.get(), declared);
    if (copy instanceof ScenarioCopy.Copy.Refused refused) {
      return refusing(
          module.get(),
          run,
          POST,
          path,
          ActionVerdict.refused(
              RefusalSubject.noObjectWasNamed(
                  ScenarioCopy.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY)),
          ActionFacts.none()
              .and("copiedFrom", scenarioId)
              .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(refused.refusals()))
              .asRecorded(),
          unprocessable(refused.refusals()));
    }
    DeclaredScenario written = ((ScenarioCopy.Copy.Declared) copy).scenario();
    return switch (admission.write(module.get(), written)) {
      case ScenarioAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              run,
              POST,
              path,
              rejected.theRefusalItReports(),
              asked,
              new ControlError(rejected.message()));
      case ScenarioAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              run,
              POST,
              path,
              refused.theRefusalItReports(),
              ActionFacts.none()
                  .and("copiedFrom", scenarioId)
                  .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(refused.refusals()))
                  .asRecorded(),
              unprocessable(refused.refusals()));
      case ScenarioAdmission.Verdict.Written applied -> {
        ScenarioRule fresh = applied.rules().getFirst();
        actions.record(
            AuditedAction.applied(
                module.get().id(),
                fresh.testRunId(),
                ObjectOrigin.CONTROL_API,
                POST,
                path,
                ActionVerdict.applied(HttpStatus.CREATED.value()),
                ActionFacts.none()
                    .and("copiedFrom", scenarioId)
                    .andEach("scenarioIds", scenarioIdsOf(applied.rules()))
                    .asRecorded()));
        yield ResponseEntity.status(HttpStatus.CREATED).body(ControlViews.viewOf(provider, fresh));
      }
    };
  }

  @GetMapping("/scenarios/{scenarioId}")
  ResponseEntity<Object> scenario(
      @PathVariable String provider,
      @PathVariable String scenarioId,
      @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    return scenarios
        .find(module.get().id(), run, scenarioId)
        .<ResponseEntity<Object>>map(rule -> ResponseEntity.ok(ControlViews.viewOf(provider, rule)))
        .orElseGet(() -> notFound(ControlRefusals.noScenarioRule(provider, run)));
  }

  @DeleteMapping("/scenarios/{scenarioId}")
  ResponseEntity<Object> deleteScenario(
      @PathVariable String provider,
      @PathVariable String scenarioId,
      @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/scenarios/" + scenarioId);
    Map<String, List<String>> asked = ActionFacts.none().and(SCENARIO_ID, scenarioId).asRecorded();
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return refusing(
          module.get(),
          TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
          DELETE,
          path,
          ActionVerdict.rejected(
              RefusalSubject.noObjectWasNamed(
                  ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_FORGET)),
          asked,
          new ControlError(String.valueOf(rejected.getMessage())));
    }
    Optional<ScenarioRule> doomed = scenarios.find(module.get().id(), run, scenarioId);
    if (doomed.isEmpty() || !scenarios.remove(module.get().id(), run, scenarioId)) {
      return refusing(
          module.get(),
          run,
          DELETE,
          path,
          ActionVerdict.notFound(
              RefusalSubject.noObjectWasNamed(
                  ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_FORGET)),
          asked,
          new ControlError(ControlRefusals.noScenarioRule(provider, run)));
    }
    actions.record(
        AuditedAction.applied(
            module.get().id(),
            run,
            ObjectOrigin.CONTROL_API,
            DELETE,
            path,
            ActionVerdict.applied(HttpStatus.OK.value()),
            ActionFacts.none()
                .andEach("scenarioIds", List.of(doomed.get().scenarioId()))
                .asRecorded()));
    return ResponseEntity.ok(ControlViews.viewOf(provider, doomed.get()));
  }

  @GetMapping("/address")
  ResponseEntity<Object> address(
      @PathVariable String provider, @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    return ResponseEntity.ok(
        ControlViews.addressOf(run, provider, publicBaseUrl, module.get().pairing()));
  }

  @GetMapping("/config/fields")
  ResponseEntity<Object> accountFields(@PathVariable String provider) {
    return providers
        .find(provider)
        .map(module -> accountFields(module, provider))
        .orElseGet(this::notFound);
  }

  private static ResponseEntity<Object> accountFields(SimulatorProvider module, String provider) {
    return ResponseEntity.ok(ControlViews.accountFieldsOf(provider, module.accountFields()));
  }

  @GetMapping("/operations")
  ResponseEntity<Object> operations(
      @PathVariable String provider, @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    return ResponseEntity.ok(ControlViews.operationsOf(run, provider, module.get().vocabulary()));
  }

  @GetMapping("/reads")
  ResponseEntity<Object> reads(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String readNamed) {
    return answering(theReads.whatThisProviderPublishes(provider, testRunId, accountId, readNamed));
  }

  @GetMapping(TheReadsAProviderPublishes.THE_ROUTE_AN_ANSWER_IS_ASKED_FOR_ON)
  ResponseEntity<Object> theAnswerToARead(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String readNamed,
      @RequestParam(required = false) @Nullable List<String> field) {
    return answering(theReads.theAnswerTo(provider, testRunId, accountId, readNamed, field));
  }

  @GetMapping("/originations")
  ResponseEntity<Object> originations(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String startedAs) {
    return answering(
        theOriginations.theWaysThisProviderDeclares(provider, testRunId, accountId, startedAs));
  }

  @PostMapping(ThePaymentsAProviderStarts.THE_ROUTE_A_PAYMENT_IS_STARTED_ON)
  ResponseEntity<Object> startAPayment(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String startedAs,
      @RequestParam(required = false) @Nullable List<String> field) {
    return answering(
        theOriginations.thePaymentItStarts(provider, testRunId, accountId, startedAs, field));
  }

  @GetMapping("/run-bundle")
  ResponseEntity<Object> exportRunBundle(
      @PathVariable String provider, @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    return switch (exports.of(module.get(), testRunId)) {
      case RunBundles.Export.Rejected rejected -> badRequest(rejected.message());
      case RunBundles.Export.Refused refused ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
              .body(bundleRefusalNamingEachProperty(refused.refusals()));
      case RunBundles.Export.Exported exported -> ResponseEntity.ok(exported.bundle());
    };
  }

  @PostMapping("/run-bundle/import")
  ResponseEntity<Object> importRunBundle(
      @PathVariable String provider, @RequestBody DeclaredBundleImport body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/run-bundle/import");
    TestRunId whereItBelongs =
        TestRunId.theRunARowAboutThisRequestBelongsTo(
            theRunABundleImportNames(body), defaultTestRun);
    return switch (bundles.takeBack(module.get(), body)) {
      case BundleAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              rejected.theRefusalItReports(),
              theFactsOfAnImport(body).asRecorded(),
              new ControlError(rejected.message()));
      case BundleAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              refused.theRefusalItReports(),
              theFactsOfAnImport(body)
                  .andEach(ActionFacts.REFUSED_PROPERTIES, bundlePropertiesOf(refused.refusals()))
                  .asRecorded(),
              bundleRefusalNamingEachProperty(refused.refusals()));
      case BundleAdmission.Verdict.Conflicting conflicting ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              conflicting.theRefusalItReports(),
              theFactsOfAnImport(body)
                  .andEach(
                      ActionFacts.REFUSED_PROPERTIES, bundlePropertiesOf(conflicting.refusals()))
                  .asRecorded(),
              bundleRefusalNamingEachProperty(conflicting.refusals()));
      case BundleAdmission.Verdict.Taken taken -> {
        BundleReport report = taken.report();
        if (report.applied()) {
          actions.record(
              AuditedAction.applied(
                  module.get().id(),
                  whereItBelongs,
                  ObjectOrigin.CONTROL_API,
                  POST,
                  path,
                  ActionVerdict.applied(HttpStatus.CREATED.value()),
                  theFactsOfAnImport(body)
                      .andCount("created", report.created())
                      .andCount("updated", report.updated())
                      .andCount("skipped", report.skipped())
                      .asRecorded()));
        }
        yield ResponseEntity.status(report.applied() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(report);
      }
    };
  }

  @GetMapping("/repro-bundle")
  ResponseEntity<Object> captureReproductionBundle(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String key) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    return switch (captures.of(module.get(), testRunId, key)) {
      case ReproductionBundles.Capture.Rejected rejected -> badRequest(rejected.message());
      case ReproductionBundles.Capture.Unknown unknown -> notFound(unknown.message());
      case ReproductionBundles.Capture.Refused refused ->
          ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
              .body(bundleRefusalNamingEachProperty(refused.refusals()));
      case ReproductionBundles.Capture.Captured captured -> ResponseEntity.ok(captured.bundle());
    };
  }

  @PostMapping("/repro-bundle/import")
  ResponseEntity<Object> importReproductionBundle(
      @PathVariable String provider, @RequestBody DeclaredReproductionImport body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/repro-bundle/import");
    TestRunId whereItBelongs =
        TestRunId.theRunARowAboutThisRequestBelongsTo(
            theRunAReproductionImportNames(body), defaultTestRun);
    return switch (reproductions.takeBack(module.get(), body)) {
      case ReproductionAdmission.Verdict.Rejected rejected ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              rejected.theRefusalItReports(),
              theFactsOfAReproductionImport(body).asRecorded(),
              new ControlError(rejected.message()));
      case ReproductionAdmission.Verdict.Refused refused ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              refused.theRefusalItReports(),
              theFactsOfAReproductionImport(body)
                  .andEach(ActionFacts.REFUSED_PROPERTIES, bundlePropertiesOf(refused.refusals()))
                  .asRecorded(),
              bundleRefusalNamingEachProperty(refused.refusals()));
      case ReproductionAdmission.Verdict.Conflicting conflicting ->
          refusing(
              module.get(),
              whereItBelongs,
              POST,
              path,
              conflicting.theRefusalItReports(),
              theFactsOfAReproductionImport(body)
                  .andEach(
                      ActionFacts.REFUSED_PROPERTIES, bundlePropertiesOf(conflicting.refusals()))
                  .asRecorded(),
              bundleRefusalNamingEachProperty(conflicting.refusals()));
      case ReproductionAdmission.Verdict.Taken taken -> {
        ReproductionReport report = taken.report();
        if (report.applied()) {
          actions.record(
              AuditedAction.applied(
                  module.get().id(),
                  whereItBelongs,
                  ObjectOrigin.CONTROL_API,
                  POST,
                  path,
                  ActionVerdict.applied(HttpStatus.CREATED.value()),
                  theFactsOfAReproductionImport(body)
                      .andCount("created", report.setup().created())
                      .andCount("updated", report.setup().updated())
                      .andCount("skipped", report.setup().skipped())
                      .asRecorded()));
        }
        yield ResponseEntity.status(report.applied() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(report);
      }
    };
  }

  @PostMapping("/hosted/{providerObjectId}/complete")
  ResponseEntity<Object> completeHostedPage(
      @PathVariable String provider,
      @PathVariable String providerObjectId,
      @RequestBody DeclaredHostedCompletion body) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    String path = pathOf(provider, "/hosted/" + providerObjectId + "/complete");
    TestRunId testRunId;
    HostedCompletionRequest request;
    try {
      testRunId = TestRunId.declaredOr(body.testRunId(), defaultTestRun);
      request =
          HostedCompletionRequest.of(
              module.get().hostedCompletion(testRunId, providerObjectId.strip()),
              testRunId,
              providerObjectId,
              body.payerOutcome(),
              body.card(),
              ObjectOrigin.CONTROL_API);
    } catch (IllegalArgumentException rejected) {
      String unreadable = String.valueOf(rejected.getMessage());
      actions.record(
          TheTrailRowACompletedHostedPageLeaves.ofACompletionTurnedAwayBeforeTheProviderWasReached(
              module.get().id(),
              TestRunId.theRunARowAboutThisRequestBelongsTo(body.testRunId(), defaultTestRun),
              ObjectOrigin.CONTROL_API,
              path,
              providerObjectId,
              body.outcome(),
              body.card(),
              unreadable));
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ControlError(unreadable));
    }

    HostedCompletionOutcome outcome = module.get().complete(request);
    actions.record(
        TheTrailRowACompletedHostedPageLeaves.of(
            module.get().id(), path, request, outcome, HttpStatus.OK.value()));
    return switch (outcome) {
      case HostedCompletionOutcome.NoSuchObject noSuchObject ->
          ResponseEntity.status(noSuchObject.theRefusalItReports().httpStatus())
              .body(new ControlError(ControlRefusals.noHostedProcess(provider, testRunId)));
      case HostedCompletionOutcome.NotOffered notOffered ->
          ResponseEntity.status(notOffered.theRefusalItReports().httpStatus())
              .body(new ControlError(notOffered.whatThisProviderHasInstead()));
      case HostedCompletionOutcome.RefusedByTheProvider refused ->
          ResponseEntity.status(refused.theRefusalItReports().httpStatus())
              .body(new ControlError(refused.inItsOwnWords()));
      case HostedCompletionOutcome.Completed completed ->
          ResponseEntity.ok(
              new HostedCompletionResponse(
                  testRunId.value(),
                  provider,
                  completed.providerObjectId(),
                  completed.outcome().name(),
                  completed.createdObjectId(),
                  completed.webhookJobIds()));
    };
  }

  @PostMapping("/objects/{providerObjectId}/follow-ups/{name}")
  ResponseEntity<Object> followUp(
      @PathVariable String provider,
      @PathVariable String providerObjectId,
      @PathVariable String name,
      @RequestBody(required = false) @Nullable DeclaredFollowUp body) {
    String testRunId = DeclaredFollowUp.theRunNamedIn(body);
    return providers
        .find(provider)
        .map(module -> followUp(module, provider, providerObjectId, name, testRunId))
        .orElseGet(this::notFound);
  }

  private ResponseEntity<Object> followUp(
      SimulatorProvider module,
      String provider,
      String providerObjectId,
      String name,
      @Nullable String testRunId) {
    String path = pathOf(provider, "/objects/" + providerObjectId + "/follow-ups/" + name);
    Map<String, List<String>> asked =
        ActionFacts.none()
            .and(PROVIDER_OBJECT_ID, providerObjectId)
            .and(FOLLOW_UP, name)
            .asRecorded();
    TestRunId run;
    FollowUpRequest request;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
      request = FollowUpRequest.of(run, providerObjectId, name, ObjectOrigin.CONTROL_API);
    } catch (IllegalArgumentException rejected) {
      return refusing(
          module,
          TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
          POST,
          path,
          ActionVerdict.rejected(
              RefusalSubject.noObjectWasNamed(
                  FollowUpOutcome.THE_FOLLOW_UP_IT_ASKED_THIS_PROVIDER_TO_PERFORM)),
          asked,
          new ControlError(String.valueOf(rejected.getMessage())));
    }
    return switch (FollowUpControl.perform(module, request, clock.now(run))) {
      case FollowUpOutcome.Refused refused ->
          refusing(
              module,
              run,
              POST,
              path,
              refused.verdict(),
              asked,
              new ControlError(refused.inItsOwnWords()));
      case FollowUpOutcome.Performed performed -> {
        actions.record(
            AuditedAction.applied(
                module.id(),
                run,
                ObjectOrigin.CONTROL_API,
                POST,
                path,
                ActionVerdict.applied(HttpStatus.OK.value()),
                performed.after().canonical(),
                ActionFacts.none()
                    .and(FOLLOW_UP, performed.name())
                    .andEach("webhookJobIds", performed.webhookJobIds())
                    .asRecorded()));
        yield ResponseEntity.ok(
            new FollowUpResponse(
                run.value(),
                provider,
                performed.name(),
                ControlViews.viewOf(run, provider, performed.after(), clock.now(run)),
                performed.webhookJobIds()));
      }
    };
  }

  @GetMapping("/state/{providerObjectId}")
  ResponseEntity<Object> state(
      @PathVariable String provider,
      @PathVariable String providerObjectId,
      @RequestParam(required = false) @Nullable String testRunId) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    return AddressedProviderObject.read(module.get(), run, providerObjectId)
        .<ResponseEntity<Object>>map(
            addressed ->
                ResponseEntity.ok(ControlViews.viewOf(run, provider, addressed, clock.now(run))))
        .orElseGet(() -> notFound(ControlRefusals.noProviderObject(provider, run)));
  }

  @GetMapping("/idempotency")
  ResponseEntity<Object> keyedExchanges(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String after,
      @RequestParam(required = false) @Nullable String limit) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    KeyedExchangeQuery query;
    try {
      query = KeyedExchangeQuery.of(testRunId, after, limit, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    ProviderKeyMechanisms mechanisms = module.get().keyMechanisms();
    KeyedExchangePage page = module.get().keyedExchanges(query);
    return ResponseEntity.ok(
        ControlViews.viewOf(query.testRunId(), mechanisms, page, query.limit()));
  }

  @GetMapping("/objects")
  ResponseEntity<Object> objects(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String after,
      @RequestParam(required = false) @Nullable String limit) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    ProviderObjectQuery query;
    try {
      query =
          ProviderObjectQuery.of(new DeclaredObjectQuery(testRunId, after, limit), defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    return switch (module.get().objects(query)) {
      case ProviderObjectPage.NoSuchCursor _ ->
          ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(new ControlError("after must name an object of this provider and test run"));
      case ProviderObjectPage.Listed listed ->
          ResponseEntity.ok(
              stateViewsOf(
                  query.testRunId(), provider, listed.objects(), clock.now(query.testRunId())));
    };
  }

  @GetMapping("/audit")
  ResponseEntity<Object> audit(
      @PathVariable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String auditId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String origin,
      @RequestParam(required = false) @Nullable String direction,
      @RequestParam(required = false) @Nullable String method,
      @RequestParam(required = false) @Nullable String status,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String scenarioId,
      @RequestParam(required = false) @Nullable String from,
      @RequestParam(required = false) @Nullable String until,
      @RequestParam(required = false) @Nullable String after,
      @RequestParam(required = false) @Nullable String limit) {
    Optional<SimulatorProvider> module = providers.find(provider);
    if (module.isEmpty()) {
      return notFound();
    }
    AuditQuery query;
    try {
      query =
          AuditQuery.of(
              new DeclaredAuditQuery(
                  testRunId,
                  auditId,
                  accountId,
                  origin,
                  direction,
                  method,
                  status,
                  providerObjectId,
                  scenarioId,
                  from,
                  until,
                  after,
                  limit),
              defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return badRequest(rejected);
    }
    query = ProviderObjectAddressing.overEveryIdentifierOfTheSameObject(module.get(), query);
    return switch (audit.pageOf(module.get().id(), query)) {
      case AuditPage.NoSuchCursor _ ->
          ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(
                  new ControlError(
                      "after must name an audit record of this provider and test run"));
      case AuditPage.Listed listed -> ResponseEntity.ok(auditViewsOf(listed.records()));
    };
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Object> aBodyThisApiCouldNotRead(
      HttpMessageNotReadableException unreadable,
      HttpServletRequest asked,
      @Nullable HandlerMethod theRouteItWasHeadingTo) {
    ABodyTheControlApiCouldNotRead refused =
        ABodyTheControlApiCouldNotRead.of(unreadable, theRouteItWasHeadingTo);
    return providers
        .find(TheControlApiInItsOwnWords.theSegmentAfterTheNamespace(asked.getRequestURI()))
        .map(
            module ->
                refusing(
                    module,
                    refused.theRunARowAboutItBelongsTo(
                        asked.getParameter(
                            ABodyTheControlApiCouldNotRead.THE_RUN_A_REQUEST_NAMES_IN_ITS_QUERY)),
                    asked.getMethod(),
                    asked.getRequestURI(),
                    refused.theRefusalItReports(),
                    refused.theFactsOfTheRowItLeaves(),
                    refused.theWordsItIsAnsweredIn()))
        .orElseGet(this::notFound);
  }

  private ResponseEntity<Object> refusing(
      SimulatorProvider module,
      TestRunId testRunId,
      String method,
      String path,
      ActionVerdict.Refused verdict,
      Map<String, List<String>> facts,
      TheWordsARefusalWasAnsweredIn body) {
    actions.record(
        AuditedAction.refusing(
            module.id(),
            testRunId,
            ObjectOrigin.CONTROL_API,
            method,
            path,
            verdict,
            TheWordsARefusalWasAnsweredIn.beside(facts, body)));
    return ResponseEntity.status(verdict.httpStatus()).body(body);
  }

  private static String pathOf(String provider, String suffix) {
    return "/__simulator/" + provider + suffix;
  }

  private static List<String> scenarioIdsOf(List<ScenarioRule> rules) {
    List<String> ids = new ArrayList<>();
    for (ScenarioRule rule : rules) {
      ids.add(rule.scenarioId());
    }
    return ids;
  }

  private static List<String> propertiesOf(List<ScenarioRefusal> refusals) {
    List<String> properties = new ArrayList<>();
    for (ScenarioRefusal refusal : refusals) {
      properties.add(refusal.property());
    }
    return properties;
  }

  private static List<StateResponse> stateViewsOf(
      TestRunId testRunId, String provider, List<ProviderObjectState> objects, Instant now) {
    List<StateResponse> views = new ArrayList<>();
    for (ProviderObjectState state : objects) {
      views.add(
          ControlViews.viewOf(
              testRunId, provider, AddressedProviderObject.canonically(state), now));
    }
    return views;
  }

  private static List<AuditRecordView> auditViewsOf(List<AuditRecord> records) {
    List<AuditRecordView> views = new ArrayList<>();
    for (AuditRecord record : records) {
      views.add(ControlViews.viewOf(record));
    }
    return views;
  }

  private static List<FixtureResponse> viewsOfFixtures(List<PaymentMethodFixture> fixtures) {
    List<FixtureResponse> views = new ArrayList<>();
    for (PaymentMethodFixture fixture : fixtures) {
      views.add(ControlViews.viewOf(fixture));
    }
    return views;
  }

  private static List<ScenarioResponse> viewsOf(String provider, List<ScenarioRule> rules) {
    List<ScenarioResponse> views = new ArrayList<>();
    for (ScenarioRule rule : rules) {
      views.add(ControlViews.viewOf(provider, rule));
    }
    return views;
  }

  private ResponseEntity<Object> notFound() {
    return notFound(providers.theRefusalOfAnUnknownProvider());
  }

  private static ResponseEntity<Object> answering(TheReadsAProviderPublishes.Answer answer) {
    return ResponseEntity.status(answer.status()).body(answer.body());
  }

  private static ResponseEntity<Object> answering(ThePaymentsAProviderStarts.Answer answer) {
    return ResponseEntity.status(answer.status()).body(answer.body());
  }

  private static ResponseEntity<Object> notFound(String message) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ControlError(message));
  }

  private static @Nullable String theRunABundleImportNames(DeclaredBundleImport body) {
    String named = body.testRunId();
    if (named != null) {
      return named;
    }
    RunBundle carried = body.bundle();
    return carried == null ? null : carried.testRunId();
  }

  private static @Nullable String theRunAReproductionImportNames(DeclaredReproductionImport body) {
    String named = body.testRunId();
    if (named != null) {
      return named;
    }
    ReproductionBundle carried = body.bundle();
    RunBundle setup = carried == null ? null : carried.setup();
    return setup == null ? null : setup.testRunId();
  }

  private static ActionFacts theFactsOfAReproductionImport(DeclaredReproductionImport body) {
    return ActionFacts.none()
        .and(ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO, theRunAReproductionImportNames(body))
        .and(ActionFacts.AN_IMPORT_WRITES_NOTHING, String.valueOf(body.writesNothing()))
        .and("providerNativeIdsPreserved", String.valueOf(body.preservesProviderNativeIds()))
        .andEach(
            ActionFacts.THE_SECRETS_AN_IMPORT_WAS_GIVEN,
            BundleAdmission.theNamesOfTheSecretsSupplied(body.theSecretsItSupplies()))
        .andCount("providerIdsRemapped", body.theRemappingItAsksFor().size());
  }

  private static ActionFacts theFactsOfAnImport(DeclaredBundleImport body) {
    return ActionFacts.none()
        .and(ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO, theRunABundleImportNames(body))
        .and(ActionFacts.AN_IMPORT_WRITES_NOTHING, String.valueOf(body.writesNothing()))
        .andEach(
            ActionFacts.THE_SECRETS_AN_IMPORT_WAS_GIVEN,
            BundleAdmission.theNamesOfTheSecretsSupplied(body.theSecretsItSupplies()));
  }

  private static List<String> bundlePropertiesOf(List<BundleRefusal> refusals) {
    List<String> properties = new ArrayList<>();
    for (BundleRefusal refusal : refusals) {
      properties.add(refusal.property());
    }
    return properties;
  }

  private static ControlRefusal bundleRefusalNamingEachProperty(List<BundleRefusal> refusals) {
    return new ControlRefusal(
        BundleRefusal.theWordsOf(refusals), List.copyOf(bundlePropertiesOf(refusals)));
  }

  private static ControlRefusal unprocessable(List<ScenarioRefusal> refusals) {
    return new ControlRefusal(
        ScenarioRefusal.theWordsOf(refusals), List.copyOf(propertiesOf(refusals)));
  }

  private static ResponseEntity<Object> badRequest(IllegalArgumentException rejected) {
    return badRequest(String.valueOf(rejected.getMessage()));
  }

  private static ResponseEntity<Object> badRequest(String message) {
    return ResponseEntity.badRequest().body(new ControlError(message));
  }
}
