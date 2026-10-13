package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ReadControl;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAReadLeaves;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
public final class ReadPage {

  public static final String THE_FORM = "read";

  static final String VIEW = "reads";

  static final String PATH = "/ui/reads";

  static final String ASK = PATH + "/ask";

  private static final boolean THE_TESTER_ONLY_OPENED_THE_SCREEN = false;

  private static final boolean THE_TESTER_ASKED_THE_QUESTION = true;

  private final ProviderRegistry providers;
  private final ProviderAccounts accounts;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;

  public ReadPage(
      ProviderRegistry providers,
      ProviderAccounts accounts,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.accounts = accounts;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  public String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String accountId,
      @Nullable String readNamed,
      Map<String, String> parameters,
      Model model) {
    return draw(
        provider,
        testRunId,
        accountId,
        readNamed,
        parameters,
        THE_TESTER_ONLY_OPENED_THE_SCREEN,
        model);
  }

  public String answer(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String accountId,
      @Nullable String readNamed,
      Map<String, String> parameters,
      Model model) {
    return draw(
        provider,
        testRunId,
        accountId,
        readNamed,
        parameters,
        THE_TESTER_ASKED_THE_QUESTION,
        model);
  }

  private String draw(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String accountId,
      @Nullable String readNamed,
      Map<String, String> parameters,
      boolean itWasAsked,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused unusable -> model.addAttribute("refusal", unusable.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen ->
          describe(chosen, accountId, readNamed, parameters, itWasAsked, model);
    }
    return VIEW;
  }

  private void describe(
      Selection.Chosen chosen,
      @Nullable String accountId,
      @Nullable String readNamed,
      Map<String, String> parameters,
      boolean itWasAsked,
      Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    ProviderReads declared = module.reads();
    Map<String, String> submitted =
        TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(parameters);
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute(
        THE_FORM,
        ReadForm.of(
            chosen.provider(),
            run.value(),
            accountId,
            readNamed,
            declared,
            accounts.identitiesOf(module.id(), run),
            ReadControl.theValuesThisTestRunHasAlreadyProduced(
                declared,
                accountId,
                readNamed,
                (account, key) ->
                    module.theValuesThisTestRunHasAlreadyProducedFor(run, account, key)),
            submitted,
            itWasAsked
                ? theAnswerItRecorded(chosen, declared, accountId, readNamed, submitted)
                : null,
            aRuleOfThisRunCouldDecide(module, readNamed)));
  }

  private static boolean aRuleOfThisRunCouldDecide(
      SimulatorProvider module, @Nullable String readNamed) {
    return readNamed != null && module.vocabulary().operationNames().contains(readNamed.strip());
  }

  private ReadForm.Answer theAnswerItRecorded(
      Selection.Chosen chosen,
      ProviderReads declared,
      @Nullable String accountId,
      @Nullable String readNamed,
      Map<String, String> submitted) {
    ReadRequest request;
    try {
      request =
          ReadControl.asked(
              declared,
              chosen.testRunId(),
              accountId == null ? "" : accountId,
              readNamed == null ? "" : readNamed,
              submitted,
              ObjectOrigin.UI);
    } catch (IllegalArgumentException theQuestionCouldNotBeRead) {
      return theQuestionTurnedAwayBeforeTheProviderWasReached(
          chosen, accountId, readNamed, String.valueOf(theQuestionCouldNotBeRead.getMessage()));
    }
    DeclaredRead asked = declared.readNamed(request.readNamed()).orElseThrow();
    ReadOutcome outcome = chosen.module().read(request);
    actions.record(TheTrailRowAReadLeaves.of(chosen.module().id(), ASK, request, outcome));
    return ReadForm.answerOf(asked, outcome);
  }

  private ReadForm.Answer theQuestionTurnedAwayBeforeTheProviderWasReached(
      Selection.Chosen chosen,
      @Nullable String accountId,
      @Nullable String readNamed,
      String inItsOwnWords) {
    actions.record(
        TheTrailRowAReadLeaves.ofAQuestionTurnedAwayBeforeTheProviderWasReached(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            ASK,
            readNamed,
            accountId,
            inItsOwnWords));
    return ReadForm.theRefusal(inItsOwnWords, null);
  }
}
