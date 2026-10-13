package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRouteAlias;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.CallbackRetryPlans;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class ConfigurationScreen {

  public record Value(String value, @Nullable String readsAs) {}

  public record Field(
      String name, Disclosure.Visibility visibility, int occurrences, List<Value> values) {

    public Field {
      values = List.copyOf(values);
    }
  }

  public record Account(
      String accountId, boolean open, boolean heldByTheProvider, List<Field> configuration) {

    public Account {
      configuration = List.copyOf(configuration);
    }
  }

  public record Target(String accountId, String url) {}

  public record Alias(String from, String to) {}

  public record Clock(
      @Nullable String pinnedTo,
      String now,
      String theNumberAnAdvanceHints,
      List<ADurationInWords.Offered> units) {

    public Clock {
      units = List.copyOf(units);
    }
  }

  public record Deployment(
      String publicBaseUrl,
      String defaultTestRunId,
      String testRunHeader,
      int webhookAttemptCap,
      List<String> webhookRetryDelays) {

    public Deployment {
      webhookRetryDelays = List.copyOf(webhookRetryDelays);
    }
  }

  public record View(
      String provider,
      String testRunId,
      List<Account> accounts,
      List<Target> webhookTargets,
      List<Alias> routeAliases,
      Clock clock,
      Deployment deployment,
      boolean valuesShown) {

    public View {
      accounts = List.copyOf(accounts);
      webhookTargets = List.copyOf(webhookTargets);
      routeAliases = List.copyOf(routeAliases);
    }
  }

  public static final String REMOVAL_IS_CONFIRMED = "yes";

  public static final String THE_UNIT_AN_ADVANCE_IS_STATED_IN =
      AccountForm.SUBMITTED_UNIT_PREFIX + "advanceBy";

  static ADurationInWords anAdvanceAFormHints() {
    return new ADurationInWords(1, ADurationInWords.Unit.HOURS);
  }

  public static @Nullable String theSpanAnAdvanceNames(
      @Nullable String advanceBy, @Nullable String unitPicked) {
    return ADurationInWords.theIsoFormOf(advanceBy, unitPicked);
  }

  public static final String A_RELEASE_IS_CONFIRMED =
      "releasing a URL leaves every request that arrives on it to be resolved by the credentials"
          + " it carries or by the default test run, and nothing else releases one, so this form"
          + " asks for it twice: tick the box beside the URL and submit again";

  public static final String A_REMOVAL_IS_CONFIRMED =
      "removing an account is not undone by anything, and nothing else removes one, so this form"
          + " asks for it twice: tick the box beside the account and submit again";

  private ConfigurationScreen() {}

  public static ActionVerdict.Refused theRefusalOfAnUnconfirmedRelease() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            ConfigurationAdmission.THE_ROUTE_ALIAS_IT_ASKED_THIS_PROVIDER_TO_RELEASE));
  }

  public static ActionVerdict.Refused theRefusalOfAnUnconfirmedRemoval() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            ConfigurationAdmission.THE_ACCOUNT_IT_ASKED_THIS_PROVIDER_TO_FORGET));
  }

  public static Deployment deploymentOf(
      String publicBaseUrl,
      String defaultTestRunId,
      CallbackRetryPlans retryPlans,
      ProviderId provider,
      TestRunId testRunId) {
    RetryPlan webhookRetries = retryPlans.of(provider, testRunId);
    List<String> delays = new ArrayList<>();
    for (Duration delay : webhookRetries.delays()) {
      delays.add(
          ADurationInWords.of(delay).map(ADurationInWords::inWords).orElseGet(delay::toString));
    }
    return new Deployment(
        publicBaseUrl,
        defaultTestRunId,
        TestRunHeader.NAME,
        webhookRetries.attemptCap(),
        List.copyOf(delays));
  }

  public static View of(
      String provider,
      String testRunId,
      List<String> accountIds,
      ProviderAccountFields declared,
      Function<String, Optional<DisclosedAccount>> disclosureOf,
      @Nullable String open,
      boolean valuesAsked,
      List<WebhookTarget> webhookTargets,
      List<RouteAlias> routeAliases,
      @Nullable Instant pinnedTo,
      Instant now,
      Deployment deployment) {
    List<Account> accounts = new ArrayList<>();
    for (String accountId : accountIds) {
      accounts.add(
          accountOf(accountId, declared, disclosureOf.apply(accountId), open, valuesAsked));
    }
    List<Target> targets = new ArrayList<>();
    for (WebhookTarget target : webhookTargets) {
      targets.add(new Target(target.accountId(), target.url()));
    }
    List<Alias> aliases = new ArrayList<>();
    for (RouteAlias alias : routeAliases) {
      aliases.add(new Alias(alias.from(), alias.to()));
    }
    return new View(
        provider,
        testRunId,
        List.copyOf(accounts),
        List.copyOf(targets),
        List.copyOf(aliases),
        theClockOf(pinnedTo, now),
        deployment,
        valuesAsked);
  }

  private static Clock theClockOf(@Nullable Instant pinnedTo, Instant now) {
    ADurationInWords hinted = anAdvanceAFormHints();
    return new Clock(
        pinnedTo == null ? null : pinnedTo.toString(),
        now.toString(),
        Long.toString(hinted.amount()),
        ADurationInWords.theUnitsOffered(hinted.unit()));
  }

  public static DeclaredConfiguration clockChangeOf(String testRunId, @Nullable String fixedNow) {
    return new DeclaredConfiguration(
        testRunId,
        new DeclaredClock(theInstantAsked(fixedNow), null, null),
        null,
        null,
        null,
        null);
  }

  public static DeclaredConfiguration routeAliasOf(
      String testRunId, @Nullable String from, @Nullable String to) {
    return new DeclaredConfiguration(
        testRunId, null, null, null, List.of(new DeclaredRouteAlias(from, to)), null);
  }

  private static Account accountOf(
      String accountId,
      ProviderAccountFields declared,
      Optional<DisclosedAccount> disclosed,
      @Nullable String open,
      boolean valuesAsked) {
    boolean opened = accountId.equals(open);
    if (disclosed.isEmpty()) {
      return new Account(accountId, opened, false, List.of());
    }
    return new Account(
        accountId,
        opened,
        true,
        opened
            ? theFieldsItShows(declared, Disclosure.of(disclosed.get().disclosed(), valuesAsked))
            : List.of());
  }

  private static List<Field> theFieldsItShows(
      ProviderAccountFields declared, List<Disclosure.Field> disclosed) {
    List<Field> shown = new ArrayList<>();
    for (Disclosure.Field field : disclosed) {
      shown.add(
          new Field(
              field.name(),
              field.visibility(),
              field.occurrences(),
              theValuesItReadsBack(declared, field)));
    }
    return List.copyOf(shown);
  }

  private static List<Value> theValuesItReadsBack(
      ProviderAccountFields declared, Disclosure.Field field) {
    Optional<ConfigurableField> configurable = declared.field(field.name());
    List<Value> values = new ArrayList<>();
    for (String value : field.values()) {
      values.add(new Value(value, whatItMeans(configurable, value)));
    }
    return List.copyOf(values);
  }

  private static @Nullable String whatItMeans(
      Optional<ConfigurableField> configurable, String value) {
    if (configurable.isEmpty()) {
      return null;
    }
    if (configurable.get().holdsADuration()) {
      return ADurationInWords.read(value).map(ADurationInWords::inWords).orElse(null);
    }
    Optional<AcceptedValue> accepted = configurable.get().accepted(value);
    return accepted.isEmpty() ? null : accepted.get().whatItMeans();
  }

  private static @Nullable String theInstantAsked(@Nullable String fixedNow) {
    if (fixedNow == null || fixedNow.isBlank()) {
      return null;
    }
    return fixedNow.strip();
  }
}
