package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.clock.ClockChange;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ConfigurationAdmission {

  public static final String THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD =
      "the configuration this request asked this provider to hold";

  public static final String THE_ACCOUNT_IT_ASKED_THIS_PROVIDER_TO_FORGET =
      "the account this request asked this provider to forget";

  public static final String THE_ROUTE_ALIAS_IT_ASKED_THIS_PROVIDER_TO_RELEASE =
      "the route alias this request asked this provider to release";

  public sealed interface Verdict {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD));
      }
    }

    record Refused(ConfigurationRefusal refusal) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD));
      }
    }

    record Conflicting(ConfigurationRefusal refusal) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.conflicting(
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD));
      }
    }

    record Applied(
        TestRunId testRunId,
        List<String> accountIds,
        int webhookTargets,
        int routeAliases,
        Instant now)
        implements Verdict {

      public Applied {
        accountIds = List.copyOf(accountIds);
      }

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  public sealed interface Removal {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Removal {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_ACCOUNT_IT_ASKED_THIS_PROVIDER_TO_FORGET));
      }
    }

    record NotConfigured(String message) implements Removal {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.notFound(
            RefusalSubject.noObjectWasNamed(THE_ACCOUNT_IT_ASKED_THIS_PROVIDER_TO_FORGET));
      }
    }

    record Removed(TestRunId testRunId, String accountId, List<String> accountIds)
        implements Removal {

      public Removed {
        accountIds = List.copyOf(accountIds);
      }

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  public sealed interface Release {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Release {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_ROUTE_ALIAS_IT_ASKED_THIS_PROVIDER_TO_RELEASE));
      }
    }

    record NotBound(String message) implements Release {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.notFound(
            RefusalSubject.noObjectWasNamed(THE_ROUTE_ALIAS_IT_ASKED_THIS_PROVIDER_TO_RELEASE));
      }
    }

    record Released(TestRunId testRunId, String from, List<String> routeAliases)
        implements Release {

      public Released {
        routeAliases = List.copyOf(routeAliases);
      }

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  public static final String A_ROUTE_ALIAS_IS_RELEASED_BY_THE_URL_IT_BINDS =
      "from must name a URL the named test run of this provider answers on; a configuration that"
          + " leaves a route alias out no longer releases it, and no test run releases a URL"
          + " another one holds";

  static final String FROM = "from";

  public static final String AN_ACCOUNT_IS_REMOVED_BY_ITS_IDENTITY =
      "accountId must name an account configured for this provider and test run; a configuration"
          + " that leaves an account out no longer removes it";

  private final ProviderConfigurations configurations;
  private final FixedTimes fixedTimes;
  private final SimulatorClock clock;
  private final TestRuns testRuns;
  private final UnitOfWork unitOfWork;
  private final TestRunId whenNoTestRunIsNamed;

  public ConfigurationAdmission(
      ProviderConfigurations configurations,
      FixedTimes fixedTimes,
      SimulatorClock clock,
      TestRuns testRuns,
      UnitOfWork unitOfWork,
      TestRunId whenNoTestRunIsNamed) {
    this.configurations = configurations;
    this.fixedTimes = fixedTimes;
    this.clock = clock;
    this.testRuns = testRuns;
    this.unitOfWork = unitOfWork;
    this.whenNoTestRunIsNamed = whenNoTestRunIsNamed;
  }

  public Removal forget(
      SimulatorProvider provider, @Nullable String declaredTestRunId, @Nullable String accountId) {
    TestRunId testRunId;
    try {
      testRunId = TestRunId.declaredOr(declaredTestRunId, whenNoTestRunIsNamed);
    } catch (IllegalArgumentException rejected) {
      return new Removal.Rejected(String.valueOf(rejected.getMessage()));
    }
    if (accountId == null || accountId.isBlank()) {
      return new Removal.Rejected(AN_ACCOUNT_IS_REMOVED_BY_ITS_IDENTITY);
    }
    String named = accountId.strip();
    if (!configurations.remove(provider, testRunId, named)) {
      return new Removal.NotConfigured(AN_ACCOUNT_IS_REMOVED_BY_ITS_IDENTITY);
    }
    return new Removal.Removed(testRunId, named, configurations.identitiesOf(provider, testRunId));
  }

  public Release releaseRouteAlias(
      SimulatorProvider provider, @Nullable String declaredTestRunId, @Nullable String from) {
    TestRunId testRunId;
    RouteAliasKey key;
    try {
      testRunId = TestRunId.declaredOr(declaredTestRunId, whenNoTestRunIsNamed);
    } catch (IllegalArgumentException rejected) {
      return new Release.Rejected(String.valueOf(rejected.getMessage()));
    }
    if (from == null || from.isBlank()) {
      return new Release.Rejected(A_ROUTE_ALIAS_IS_RELEASED_BY_THE_URL_IT_BINDS);
    }
    String named = from.strip();
    try {
      key = RouteAliasGrammar.keyOf(FROM, named);
    } catch (IllegalArgumentException rejected) {
      return new Release.Rejected(String.valueOf(rejected.getMessage()));
    }
    if (!configurations.releaseRouteAlias(provider, testRunId, key)) {
      return new Release.NotBound(A_ROUTE_ALIAS_IS_RELEASED_BY_THE_URL_IT_BINDS);
    }
    return new Release.Released(testRunId, named, theUrlsStillBound(provider, testRunId));
  }

  private List<String> theUrlsStillBound(SimulatorProvider provider, TestRunId testRunId) {
    List<String> bound = new ArrayList<>();
    for (RouteAlias alias : configurations.routeAliasesOf(provider, testRunId)) {
      bound.add(alias.from());
    }
    return List.copyOf(bound);
  }

  public Verdict apply(SimulatorProvider provider, DeclaredConfiguration declared) {
    TestRunId testRunId;
    ClockChange clockChange;
    ProviderConfiguration configuration;
    try {
      testRunId = TestRunId.declaredOr(declared.testRunId(), whenNoTestRunIsNamed);
      clockChange = ClockChange.of(declared.clock());
      configuration = ProviderConfigurationGrammar.configurationOf(testRunId, declared);
    } catch (IllegalArgumentException rejected) {
      return new Verdict.Rejected(String.valueOf(rejected.getMessage()));
    }
    ProviderConfigurations.Storable storable;
    try {
      storable = configurations.theWriteItWouldMake(provider, configuration);
    } catch (ProviderConfigurationRejectedException refused) {
      return new Verdict.Refused(ConfigurationRefusal.of(String.valueOf(refused.getMessage())));
    }
    try {
      return unitOfWork.keepingOnly(
          applied -> true,
          () -> {
            clockChange.applyTo(fixedTimes, testRunId);
            testRuns.register(testRunId);
            List<String> configured = configurations.store(provider, configuration, storable);
            return new Verdict.Applied(
                testRunId,
                configured,
                configuration.webhookTargets().size(),
                configuration.routeAliases().size(),
                clock.now(testRunId));
          });
    } catch (ClaimedMeanwhileException claimed) {
      return new Verdict.Conflicting(ConfigurationRefusal.of(String.valueOf(claimed.getMessage())));
    }
  }
}
