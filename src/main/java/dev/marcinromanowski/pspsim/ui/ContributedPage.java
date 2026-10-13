package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
public final class ContributedPage {

  public static final String THE_WHOLE_RUN_WAS_READ = "theWholeRunWasRead";

  public static final String THE_SCREEN_IS_ONE_PROVIDERS_OWN = "theScreenIsOneProvidersOwn";

  public static final String A_MOMENT_IN_WORDS = "inWords";

  static final int AS_MANY_AS_ONE_READ_CARRIES = 100;

  static final int ONE_MORE_THAN_A_READ_CARRIES = AS_MANY_AS_ONE_READ_CARRIES + 1;

  private final ContributedScreens screens;
  private final ProviderRegistry providers;
  private final ProviderAccounts accounts;
  private final SimulatorClock clock;
  private final TestRunId defaultTestRun;

  public ContributedPage(
      ContributedScreens screens,
      ProviderRegistry providers,
      ProviderAccounts accounts,
      SimulatorClock clock,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.screens = screens;
    this.providers = providers;
    this.accounts = accounts;
    this.clock = clock;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  record ARead(List<ContributedObject> rows, boolean theWholeRun) {

    ARead {
      rows = List.copyOf(rows);
    }
  }

  public String render(String provider, String screen, @Nullable String testRunId, Model model) {
    ContributedScreen served =
        screens
            .serving(provider, screen)
            .orElseThrow(
                () ->
                    new UnknownContributedScreenException(
                        screens.theRefusalOfAScreenThisBuildDoesNotServe(provider, screen)));
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", served.path());
    model.addAttribute("screen", served.asAScreenNamesItself());
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute(THE_SCREEN_IS_ONE_PROVIDERS_OWN, true);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused unusable -> model.addAttribute("refusal", unusable.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen -> describe(chosen, model);
    }
    return served.view();
  }

  private void describe(Selection.Chosen chosen, Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    Instant now = clock.now(run);
    WhatWasTyped typed = whatTheTesterTyped(model);
    model.addAttribute(WhatWasTyped.ATTRIBUTE, typed);
    ARead read = theObjectsOfTheRun(chosen, module, run, now, typed);
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute("accounts", accounts.identitiesOf(module.id(), run));
    model.addAttribute("now", now);
    model.addAttribute(A_MOMENT_IN_WORDS, new AMomentInWords(now));
    model.addAttribute("objects", read.rows());
    model.addAttribute(THE_WHOLE_RUN_WAS_READ, read.theWholeRun());
  }

  static WhatWasTyped whatTheTesterTyped(Model model) {
    return model.getAttribute(WhatWasTyped.ATTRIBUTE) instanceof WhatWasTyped carried
        ? carried
        : WhatWasTyped.nothing();
  }

  static ARead theObjectsOfTheRun(
      Selection.Chosen chosen,
      SimulatorProvider module,
      TestRunId run,
      Instant now,
      WhatWasTyped typed) {
    if (!(module.objects(new ProviderObjectQuery(run, null, ONE_MORE_THAN_A_READ_CARRIES))
        instanceof ProviderObjectPage.Listed listed)) {
      return new ARead(List.of(), false);
    }
    List<ProviderObjectState> found = listed.objects();
    int asManyAsThisReadCarries = Math.min(found.size(), AS_MANY_AS_ONE_READ_CARRIES);
    List<ContributedObject> rows = new ArrayList<>();
    for (int row = 0; row < asManyAsThisReadCarries; row++) {
      rows.add(rowOf(chosen, module, run, now, found.get(row), typed));
    }
    return new ARead(rows, found.size() <= AS_MANY_AS_ONE_READ_CARRIES);
  }

  private static ContributedObject rowOf(
      Selection.Chosen chosen,
      SimulatorProvider module,
      TestRunId run,
      Instant now,
      ProviderObjectState object,
      WhatWasTyped typed) {
    String id = object.providerObjectId();
    return new ContributedObject(
        object,
        FollowUpForm.of(module.followUps(run, id, now)),
        HostedCompletionForm.of(
            chosen.provider(),
            run.value(),
            id,
            module.hostedCompletion(run, id),
            typed.theStepChosenFor(id),
            typed.theCardChosenFor(id)));
  }
}
