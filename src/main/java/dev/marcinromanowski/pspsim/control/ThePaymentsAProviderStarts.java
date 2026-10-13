package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationControl;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAStartedPaymentLeaves;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
final class ThePaymentsAProviderStarts {

  static final String THE_ROUTE_A_PAYMENT_IS_STARTED_ON = "/originations";

  static final TheFieldParameterBothRoutesRead.TheWordsARouteTakesItsValuesIn
      THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN =
          new TheFieldParameterBothRoutesRead.TheWordsARouteTakesItsValuesIn(
              "every value a payment is started with",
              "one way of starting a payment",
              "originations");

  static final String A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT =
      TheFieldParameterBothRoutesRead.aFieldCarriesTheKeyItIsSentForAndTheValueAfterIt(
          THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN);

  static final String ONE_WAY_OF_STARTING_A_PAYMENT_ASKS_EACH_OF_ITS_FIELDS_ONCE =
      TheFieldParameterBothRoutesRead.itAsksEachOfItsFieldsOnce(
          THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN);

  static final String A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD =
      TheTrailRowAStartedPaymentLeaves.A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD;

  static final String A_CALLBACK_TARGET_THIS_RUN_CONFIGURES =
      "a callback target this test run configures for this provider";

  private final ProviderRegistry providers;
  private final ProviderAccounts accounts;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;

  ThePaymentsAProviderStarts(
      ProviderRegistry providers,
      ProviderAccounts accounts,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.accounts = accounts;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  record Answer(int status, Object body) {}

  Answer theWaysThisProviderDeclares(
      String provider,
      @Nullable String testRunId,
      @Nullable String accountId,
      @Nullable String startedAs) {
    SimulatorProvider module = providers.find(provider).orElse(null);
    if (module == null) {
      return theRefusalOfAnUnknownProvider();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return theRequestCouldNotBeRead(rejected);
    }
    ProviderOriginationFields declared = module.originationFields();
    return new Answer(
        HttpStatus.OK.value(),
        ControlViews.originationsOf(
            run,
            provider,
            accountId,
            accounts.identitiesOf(module.id(), run),
            declared,
            theValuesEachFieldAlsoOffers(module, declared, run, accountId, startedAs)));
  }

  Answer thePaymentItStarts(
      String provider,
      @Nullable String testRunId,
      @Nullable String accountId,
      @Nullable String startedAs,
      @Nullable List<String> field) {
    SimulatorProvider module = providers.find(provider).orElse(null);
    if (module == null) {
      return theRefusalOfAnUnknownProvider();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException theRunItNamedCouldNotBeRead) {
      return theRefusalOfARequestTheCoreCouldNotRead(
          module,
          provider,
          TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
          accountId,
          startedAs,
          theRunItNamedCouldNotBeRead);
    }
    OriginationRequest request;
    try {
      request =
          OriginationControl.asked(
              module.originationFields(),
              run,
              accountId == null ? "" : accountId,
              startedAs == null ? "" : startedAs,
              TheFieldParameterBothRoutesRead.theValuesItWasSent(
                  field, THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN),
              theCallbackTargetsThisRunConfigures(module, run),
              ObjectOrigin.CONTROL_API);
    } catch (IllegalArgumentException refused) {
      return theRefusalOfARequestTheCoreCouldNotRead(
          module, provider, run, accountId, startedAs, refused);
    }
    return theAnswerOf(module, provider, run, request, module.originate(request));
  }

  private Answer theAnswerOf(
      SimulatorProvider module,
      String provider,
      TestRunId run,
      OriginationRequest request,
      OriginationOutcome outcome) {
    actions.record(
        TheTrailRowAStartedPaymentLeaves.of(
            module.id(),
            theRouteAPaymentIsStartedOn(provider),
            request,
            outcome,
            HttpStatus.CREATED.value()));
    return switch (outcome) {
      case OriginationOutcome.Originated started ->
          new Answer(
              HttpStatus.CREATED.value(), ControlViews.startedOf(run, provider, request, started));
      case OriginationOutcome.RefusedByTheProvider refused ->
          new Answer(
              HttpStatus.UNPROCESSABLE_CONTENT.value(), new ControlError(refused.inItsOwnWords()));
      case OriginationOutcome.AnsweredByAScenarioInstead answered ->
          new Answer(
              HttpStatus.UNPROCESSABLE_CONTENT.value(),
              new ControlError(A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD + answered.scenarioId()));
      case OriginationOutcome.NotOffered notOffered ->
          new Answer(
              HttpStatus.BAD_REQUEST.value(),
              new ControlError(notOffered.whatThisProviderHasInstead()));
      case OriginationOutcome.NoSuchAccount _ ->
          new Answer(
              HttpStatus.NOT_FOUND.value(),
              new ControlError(ControlRefusals.noAccountOfThisProvider(provider, run)));
    };
  }

  private Answer theRefusalOfARequestTheCoreCouldNotRead(
      SimulatorProvider module,
      String provider,
      TestRunId run,
      @Nullable String accountId,
      @Nullable String startedAs,
      IllegalArgumentException refused) {
    String unreadable = String.valueOf(refused.getMessage());
    actions.record(
        TheTrailRowAStartedPaymentLeaves.ofARequestTurnedAwayBeforeTheProviderWasReached(
            module.id(),
            run,
            ObjectOrigin.CONTROL_API,
            theRouteAPaymentIsStartedOn(provider),
            startedAs,
            accountId,
            unreadable));
    return new Answer(HttpStatus.BAD_REQUEST.value(), new ControlError(unreadable));
  }

  static String theRouteAPaymentIsStartedOn(String provider) {
    return "/__simulator/" + provider + THE_ROUTE_A_PAYMENT_IS_STARTED_ON;
  }

  private Map<String, List<AValueAFieldOffers>> theValuesEachFieldAlsoOffers(
      SimulatorProvider module,
      ProviderOriginationFields declared,
      TestRunId run,
      @Nullable String accountId,
      @Nullable String startedAs) {
    Map<String, List<AValueAFieldOffers>> offered =
        new LinkedHashMap<>(
            OriginationControl.theValuesThisTestRunHasAlreadyProduced(
                declared,
                accountId,
                startedAs,
                (account, key) ->
                    module.theValuesThisTestRunHasAlreadyProducedFor(run, account, key)));
    List<AValueAFieldOffers> configured = new ArrayList<>();
    for (String url : theCallbackTargetsThisRunConfigures(module, run)) {
      configured.add(new AValueAFieldOffers(url, A_CALLBACK_TARGET_THIS_RUN_CONFIGURES));
    }
    for (DeclaredOrigination way : declared.waysToStartAPayment()) {
      for (AFieldATesterFillsIn field : way.fields()) {
        if (field.namesTheCallbackTarget()) {
          offered.put(field.key(), List.copyOf(configured));
        }
      }
    }
    return Map.copyOf(offered);
  }

  private List<String> theCallbackTargetsThisRunConfigures(
      SimulatorProvider module, TestRunId run) {
    List<String> urls = new ArrayList<>();
    for (WebhookTarget target : accounts.webhookTargetsOf(module.id(), run)) {
      if (!urls.contains(target.url())) {
        urls.add(target.url());
      }
    }
    return List.copyOf(urls);
  }

  private Answer theRefusalOfAnUnknownProvider() {
    return new Answer(
        HttpStatus.NOT_FOUND.value(), new ControlError(providers.theRefusalOfAnUnknownProvider()));
  }

  private static Answer theRequestCouldNotBeRead(IllegalArgumentException rejected) {
    return new Answer(
        HttpStatus.BAD_REQUEST.value(), new ControlError(String.valueOf(rejected.getMessage())));
  }
}
