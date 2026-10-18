package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationFacts;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class WorkbenchScreen {

  public static final int THE_NEWEST_OBJECTS_IT_SHOWS = 5;

  public static String theHostedSessionsAmongTheseObjects(HostedPages.Split hosted) {
    return "An object waiting for a payer is an ordinary object of this test run, and the objects"
        + " panel above lists each one beside every other object of this run. The providers here"
        + " keep "
        + hosted.theObjectsAsACount()
        + " a payer finishes: "
        + hosted.theObjectsTheyKeep()
        + ". What this screen does not do is narrow that list to the ones still waiting or"
        + " press the payer's own step; the Payments screen does both.";
  }

  public static final String THE_PAYMENT_METHODS_THIS_WORKBENCH_DOES_NOT_LIST =
      "A payment-method fixture is an ordinary object of this test run: the Payment methods screen"
          + " lists what this provider holds, seeds one and removes one, the control API answers"
          + " for the same fixtures, and a persona seeds one in one press. This screen does not"
          + " read them, so this panel is not built rather than unbuildable.";

  public static final String A_SECRET_IS_NEVER_A_SUMMARY =
      "This summary names every account field this provider declares and prints the value of only"
          + " those it declares are not secret. A withheld value is not hidden behind a button"
          + " here: this screen has no way to ask for one, so nothing on it can print one.";

  public static final String NO_CALL_RECORDS_THE_OPERATION_IT_ASKED_FOR =
      "A tick for an operation this run has already exercised is absent, and it is absent rather"
          + " than empty: the trail records the method and the path a call arrived on and never the"
          + " operation it asked for, and the operations of this build's providers are spelled three"
          + " different ways - a bare endpoint name, a method and a path template, and a wire field"
          + " assignment several operations share one path under - so no reading of a recorded"
          + " method and path maps back to an operation for every provider. The column below counts"
          + " the scenario rules of this run that name each operation, which is exact, and it counts"
          + " nothing about traffic.";

  public static final String THE_OBJECT_READ_REFUSED_THE_CURSOR =
      "The object read answered that the cursor it was sent names no object of this provider and"
          + " test run. This screen sends no cursor at all, so this run may well hold objects that"
          + " are not listed here: read them on the Payments screen rather than as none.";

  public record Scenario(
      String scenarioId,
      int priority,
      int specificity,
      @Nullable String operation,
      @Nullable String story,
      @Nullable String keyedOn,
      @Nullable String whenState,
      @Nullable String thenState,
      Instant createdAt) {}

  public record ConfiguredAccount(
      String accountId, boolean heldByTheProvider, List<Disclosure.Field> configuration) {

    public ConfiguredAccount {
      configuration = List.copyOf(configuration);
    }
  }

  public record Addresses(
      String publicBaseUrl,
      String providerBaseUrl,
      String testRunHeader,
      String howARequestNamesItsAccount,
      List<PairingScreen.Alias> routeAliases) {

    public Addresses {
      routeAliases = List.copyOf(routeAliases);
    }
  }

  public record Operation(
      String name,
      String storiesKeyedOn,
      boolean reportsAProcessingState,
      boolean itsFlowKeyMayBeReplaced,
      List<String> carries,
      String failsWith,
      int rulesOfThisRunNamingIt) {

    public Operation {
      carries = List.copyOf(carries);
    }

    public boolean anyRuleOfThisRunNamesIt() {
      return rulesOfThisRunNamingIt > 0;
    }
  }

  public record CurrentObjects(
      List<ObjectScreen.Row> newest, boolean moreThanTheseExist, @Nullable String whyItListsNone) {

    public CurrentObjects {
      newest = List.copyOf(newest);
    }
  }

  public record View(
      String provider,
      List<String> providers,
      String testRunId,
      List<ConfiguredAccount> accounts,
      Addresses addresses,
      List<Operation> operations,
      int rulesOfThisRunNamingNoOperation,
      CurrentObjects objects,
      List<Scenario> scenarios,
      DeliveryScreen.Queue queue,
      String whyItListsNoPaymentMethod,
      String whereTheHostedSessionsAre) {

    public View {
      providers = List.copyOf(providers);
      accounts = List.copyOf(accounts);
      operations = List.copyOf(operations);
      scenarios = List.copyOf(scenarios);
    }
  }

  private WorkbenchScreen() {}

  public static int theLimitItAsksTheObjectReadFor() {
    return THE_NEWEST_OBJECTS_IT_SHOWS + 1;
  }

  public static View of(
      String provider,
      List<String> providers,
      String testRunId,
      ProviderAccountFields accountFields,
      List<String> accountIds,
      Function<String, Optional<DisclosedAccount>> disclosureOf,
      String publicBaseUrl,
      ProviderPairing pairing,
      List<RouteAlias> routeAliases,
      ProviderVocabulary vocabulary,
      CurrentObjects objects,
      List<ScenarioRule> rules,
      DeliveryScreen.Queue queue,
      HostedPages.Split hosted) {
    return new View(
        provider,
        providers,
        testRunId,
        accountsOf(accountFields, accountIds, disclosureOf),
        addressesOf(publicBaseUrl, pairing, routeAliases),
        operationsOf(vocabulary, rules),
        theRulesNamingNoOperation(rules),
        objects,
        scenariosOf(rules),
        queue,
        THE_PAYMENT_METHODS_THIS_WORKBENCH_DOES_NOT_LIST,
        theHostedSessionsAmongTheseObjects(hosted));
  }

  public static List<ConfiguredAccount> accountsOf(
      ProviderAccountFields accountFields,
      List<String> accountIds,
      Function<String, Optional<DisclosedAccount>> disclosureOf) {
    List<ConfiguredAccount> accounts = new ArrayList<>();
    for (String accountId : accountIds) {
      accounts.add(accountOf(accountFields, accountId, disclosureOf.apply(accountId)));
    }
    return List.copyOf(accounts);
  }

  private static ConfiguredAccount accountOf(
      ProviderAccountFields accountFields, String accountId, Optional<DisclosedAccount> disclosed) {
    if (disclosed.isEmpty()) {
      return new ConfiguredAccount(accountId, false, List.of());
    }
    return new ConfiguredAccount(
        accountId,
        true,
        theFieldsItNamesWithoutTheValuesItWithholds(accountFields, disclosed.get().disclosed()));
  }

  public static List<Disclosure.Field> theFieldsItNamesWithoutTheValuesItWithholds(
      ProviderAccountFields accountFields, Map<String, List<String>> disclosed) {
    return Disclosure.of(
        Redaction.redact(
            disclosed,
            ConfigurationFacts.everythingButTheDeclaredPlainFields(
                accountFields, disclosed.keySet())),
        true);
  }

  public static Addresses addressesOf(
      String publicBaseUrl, ProviderPairing pairing, List<RouteAlias> routeAliases) {
    List<PairingScreen.Alias> aliases = new ArrayList<>();
    for (RouteAlias alias : routeAliases) {
      aliases.add(new PairingScreen.Alias(alias.from(), alias.to()));
    }
    return new Addresses(
        publicBaseUrl,
        pairing.providerBaseUrlWithin(publicBaseUrl),
        TestRunHeader.NAME,
        pairing.howARequestNamesItsAccount(),
        List.copyOf(aliases));
  }

  public static List<Operation> operationsOf(
      ProviderVocabulary vocabulary, List<ScenarioRule> rules) {
    List<Operation> operations = new ArrayList<>();
    for (ProviderOperation exposed : vocabulary.operations()) {
      operations.add(
          new Operation(
              exposed.name(),
              exposed.storiesKeyedOn(),
              vocabulary.processingStates().reachedBy(exposed.name()),
              exposed.itsFlowKeyMayBeReplaced(),
              exposed.theFactsItCarries(),
              vocabulary.theCodesOf(exposed.name()).describedAs(),
              theRulesNaming(exposed.name(), rules)));
    }
    return List.copyOf(operations);
  }

  public static int theRulesNamingNoOperation(List<ScenarioRule> rules) {
    int naming = 0;
    for (ScenarioRule rule : rules) {
      if (rule.match().operation() == null) {
        naming++;
      }
    }
    return naming;
  }

  private static int theRulesNaming(String operation, List<ScenarioRule> rules) {
    int naming = 0;
    for (ScenarioRule rule : rules) {
      if (operation.equals(rule.match().operation())) {
        naming++;
      }
    }
    return naming;
  }

  public static CurrentObjects theNewestObjectsOf(ProviderObjectPage page, Instant now) {
    return switch (page) {
      case ProviderObjectPage.NoSuchCursor _ ->
          new CurrentObjects(List.of(), false, THE_OBJECT_READ_REFUSED_THE_CURSOR);
      case ProviderObjectPage.Listed listed -> theNewestOf(listed.objects(), now);
    };
  }

  private static CurrentObjects theNewestOf(List<ProviderObjectState> objects, Instant now) {
    boolean moreThanTheseExist = objects.size() > THE_NEWEST_OBJECTS_IT_SHOWS;
    List<ProviderObjectState> newest =
        moreThanTheseExist ? objects.subList(0, THE_NEWEST_OBJECTS_IT_SHOWS) : objects;
    return new CurrentObjects(
        ObjectScreen.pageOf(newest, THE_NEWEST_OBJECTS_IT_SHOWS, now).rows(),
        moreThanTheseExist,
        null);
  }

  public static List<Scenario> scenariosOf(List<ScenarioRule> rules) {
    List<Scenario> scenarios = new ArrayList<>();
    for (ScenarioRule rule : rules) {
      ScenarioStory story = rule.story();
      scenarios.add(
          new Scenario(
              rule.scenarioId(),
              rule.priority(),
              rule.specificity(),
              rule.match().operation(),
              story == null ? null : story.name(),
              story == null ? null : story.keyedOn().name(),
              story == null ? null : story.whenState(),
              story == null ? null : story.thenState(),
              rule.createdAt()));
    }
    return List.copyOf(scenarios);
  }
}
