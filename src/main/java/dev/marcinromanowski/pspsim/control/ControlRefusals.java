package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

final class ControlRefusals {

  private ControlRefusals() {}

  static String noScenarioRule(String provider, TestRunId searched) {
    return searchedFor("no scenario rule with that scenarioId", provider, searched);
  }

  static String noScenarioRuleCalled(String provider, TestRunId searched, String scenarioId) {
    return searchedFor("no scenario rule " + scenarioId, provider, searched);
  }

  static String noSecondScenarioRuleWasNamed() {
    return "a comparison reads two rules and this request named one: put the other rule's"
        + " scenarioId in the against parameter";
  }

  static String noHostedProcess(String provider, TestRunId searched) {
    return searchedFor("no hosted payment process with that providerObjectId", provider, searched);
  }

  static String noProviderObject(String provider, TestRunId searched) {
    return searchedFor("no provider object with that providerObjectId", provider, searched);
  }

  static String noAccountOfThisProvider(String provider, TestRunId searched) {
    return searchedFor("no account with that accountId", provider, searched);
  }

  static String noWebhookJob(String provider, TestRunId searched) {
    return searchedFor("no webhook job with that webhookJobId", provider, searched);
  }

  static String nothingWasScheduled(String provider, TestRunId searched) {
    return searchedFor("no webhook was scheduled for that providerObjectId", provider, searched);
  }

  private static String searchedFor(String missing, String provider, TestRunId searched) {
    return missing + " in test run " + searched.value() + " of provider " + provider;
  }
}
