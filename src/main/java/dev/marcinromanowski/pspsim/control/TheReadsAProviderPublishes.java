package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ReadControl;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAReadLeaves;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
final class TheReadsAProviderPublishes {

  static final TheFieldParameterBothRoutesRead.TheWordsARouteTakesItsValuesIn
      THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN =
          new TheFieldParameterBothRoutesRead.TheWordsARouteTakesItsValuesIn(
              "every value of a read", "one read", "reads");

  static final String A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT =
      TheFieldParameterBothRoutesRead.aFieldCarriesTheKeyItIsSentForAndTheValueAfterIt(
          THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN);

  static final String ONE_READ_ASKS_EACH_OF_ITS_FIELDS_ONCE =
      TheFieldParameterBothRoutesRead.itAsksEachOfItsFieldsOnce(
          THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN);

  static final String A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD =
      TheTrailRowAReadLeaves.A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD;

  static final String AND_THIS_ROUTE_ANSWERS_THE_ROWS_A_PROVIDER_ANSWERED =
      ". This route answers the rows a provider answered and no answer of a rule.";

  static final String THE_ROUTE_AN_ANSWER_IS_ASKED_FOR_ON = "/reads/answer";

  private final ProviderRegistry providers;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;

  TheReadsAProviderPublishes(
      ProviderRegistry providers,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  record Answer(int status, Object body) {}

  Answer whatThisProviderPublishes(
      String provider,
      @Nullable String testRunId,
      @Nullable String accountId,
      @Nullable String readNamed) {
    SimulatorProvider module = providers.find(provider).orElse(null);
    if (module == null) {
      return theRefusalOfAnUnknownProvider();
    }
    TestRunId run;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return theQuestionCouldNotBeRead(rejected);
    }
    ProviderReads declared = module.reads();
    return new Answer(
        HttpStatus.OK.value(),
        ControlViews.readsOf(
            run,
            provider,
            accountId,
            declared,
            ReadControl.theValuesThisTestRunHasAlreadyProduced(
                declared,
                accountId,
                readNamed,
                (account, key) ->
                    module.theValuesThisTestRunHasAlreadyProducedFor(run, account, key))));
  }

  Answer theAnswerTo(
      String provider,
      @Nullable String testRunId,
      @Nullable String accountId,
      @Nullable String readNamed,
      @Nullable List<String> field) {
    SimulatorProvider module = providers.find(provider).orElse(null);
    if (module == null) {
      return theRefusalOfAnUnknownProvider();
    }
    String path = theRouteAnAnswerIsAskedForOn(provider);
    ProviderReads declared = module.reads();
    if (!declared.publishesAny()) {
      return theQuestionTurnedAwayBeforeTheProviderWasReached(
          module, path, testRunId, accountId, readNamed, declared.whatItPublishesInstead());
    }
    TestRunId run;
    ReadRequest request;
    try {
      run = TestRunId.declaredOr(testRunId, defaultTestRun);
      request =
          ReadControl.asked(
              declared,
              run,
              accountId == null ? "" : accountId,
              readNamed == null ? "" : readNamed,
              TheFieldParameterBothRoutesRead.theValuesItWasSent(
                  field, THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN),
              ObjectOrigin.CONTROL_API);
    } catch (IllegalArgumentException rejected) {
      return theQuestionTurnedAwayBeforeTheProviderWasReached(
          module, path, testRunId, accountId, readNamed, String.valueOf(rejected.getMessage()));
    }
    DeclaredRead asked = declared.readNamed(request.readNamed()).orElseThrow();
    ReadOutcome outcome = module.read(request);
    actions.record(TheTrailRowAReadLeaves.of(module.id(), path, request, outcome));
    return theAnswerOf(provider, run, request, asked, outcome);
  }

  private Answer theQuestionTurnedAwayBeforeTheProviderWasReached(
      SimulatorProvider module,
      String path,
      @Nullable String testRunId,
      @Nullable String accountId,
      @Nullable String readNamed,
      String inItsOwnWords) {
    actions.record(
        TheTrailRowAReadLeaves.ofAQuestionTurnedAwayBeforeTheProviderWasReached(
            module.id(),
            TestRunId.theRunARowAboutThisRequestBelongsTo(testRunId, defaultTestRun),
            ObjectOrigin.CONTROL_API,
            path,
            readNamed,
            accountId,
            inItsOwnWords));
    return new Answer(HttpStatus.BAD_REQUEST.value(), new ControlError(inItsOwnWords));
  }

  static String theRouteAnAnswerIsAskedForOn(String provider) {
    return "/__simulator/" + provider + THE_ROUTE_AN_ANSWER_IS_ASKED_FOR_ON;
  }

  private static Answer theAnswerOf(
      String provider,
      TestRunId run,
      ReadRequest request,
      DeclaredRead asked,
      ReadOutcome outcome) {
    return switch (outcome) {
      case ReadOutcome.Answered answered ->
          new Answer(
              HttpStatus.OK.value(),
              ControlViews.answerOf(run, provider, request, asked, answered));
      case ReadOutcome.RefusedByTheProvider refused ->
          new Answer(
              HttpStatus.UNPROCESSABLE_CONTENT.value(), new ControlError(refused.inItsOwnWords()));
      case ReadOutcome.AnsweredByAScenarioInstead instead ->
          new Answer(
              HttpStatus.UNPROCESSABLE_CONTENT.value(),
              new ControlError(
                  A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD
                      + instead.scenarioId()
                      + AND_THIS_ROUTE_ANSWERS_THE_ROWS_A_PROVIDER_ANSWERED));
      case ReadOutcome.NotOffered notOffered ->
          new Answer(
              HttpStatus.BAD_REQUEST.value(),
              new ControlError(notOffered.whatThisProviderHasInstead()));
      case ReadOutcome.NoSuchAccount _ ->
          new Answer(
              HttpStatus.NOT_FOUND.value(),
              new ControlError(ControlRefusals.noAccountOfThisProvider(provider, run)));
    };
  }

  private Answer theRefusalOfAnUnknownProvider() {
    return new Answer(
        HttpStatus.NOT_FOUND.value(), new ControlError(providers.theRefusalOfAnUnknownProvider()));
  }

  private static Answer theQuestionCouldNotBeRead(IllegalArgumentException rejected) {
    return new Answer(
        HttpStatus.BAD_REQUEST.value(), new ControlError(String.valueOf(rejected.getMessage())));
  }
}
