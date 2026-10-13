package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ARefusedProperty;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.Seeding;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

public final class PaymentMethodFixtureAdmission {

  public static final String THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED =
      "the payment-method fixture this request asked this provider to seed";

  public static final String THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_FORGET =
      "the payment-method fixture this request asked this provider to forget";

  public sealed interface Verdict {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED));
      }
    }

    record Refused(String property, String message) implements Verdict {

      public String inItsOwnWords() {
        return ARefusedProperty.theWordsOf(property, message);
      }

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED));
      }
    }

    record Conflicting(@Nullable String property, String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.conflicting(
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED));
      }
    }

    record Seeded(PaymentMethodFixture fixture) implements Verdict {

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
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_FORGET));
      }
    }

    record NotSeeded(String message) implements Removal {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.notFound(
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_FORGET));
      }
    }

    record Removed(PaymentMethodFixture fixture) implements Removal {

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  public static final String DEFAULT_SCENARIO = "defaultScenario";

  public static final String A_FIXTURE_IS_REMOVED_BY_ITS_IDENTITY =
      "fixtureId must name a payment-method fixture seeded for this provider and test run";

  private final PaymentMethodFixtures fixtures;
  private final ScenarioRules scenarios;
  private final TestRuns testRuns;
  private final UnitOfWork unitOfWork;
  private final TestRunId whenNoTestRunIsNamed;

  public PaymentMethodFixtureAdmission(
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      TestRuns testRuns,
      UnitOfWork unitOfWork,
      TestRunId whenNoTestRunIsNamed) {
    this.fixtures = fixtures;
    this.scenarios = scenarios;
    this.testRuns = testRuns;
    this.unitOfWork = unitOfWork;
    this.whenNoTestRunIsNamed = whenNoTestRunIsNamed;
  }

  public Verdict seed(
      SimulatorProvider provider, DeclaredPaymentMethodFixture declared, ObjectOrigin askedBy) {
    TestRunId testRunId;
    NewPaymentMethodFixture asked;
    try {
      testRunId = TestRunId.declaredOr(declared.testRunId(), whenNoTestRunIsNamed);
      asked = PaymentMethodFixtureGrammar.fixtureOf(provider.id(), testRunId, declared, askedBy);
    } catch (IllegalArgumentException rejected) {
      return new Verdict.Rejected(String.valueOf(rejected.getMessage()));
    }

    Verdict unreadable = theProviderNativeIdThisProviderReadsNothingOf(provider, asked);
    if (unreadable != null) {
      return unreadable;
    }
    Verdict dangling = theScenarioThisTestRunHasNotWritten(provider, asked);
    if (dangling != null) {
      return dangling;
    }

    PaymentMethodFixturePlan plan;
    try {
      plan = provider.seed(asked);
    } catch (ProviderFixtureRejectedException refused) {
      return new Verdict.Refused(refused.property(), String.valueOf(refused.getMessage()));
    }

    try {
      return unitOfWork.keepingOnly(
          Verdict.Seeded.class::isInstance,
          () -> theSeedItWrites(provider, testRunId, asked, plan));
    } catch (ProviderFixtureRejectedException refused) {
      return new Verdict.Refused(refused.property(), String.valueOf(refused.getMessage()));
    } catch (ClaimedMeanwhileException claimed) {
      return new Verdict.Conflicting(null, String.valueOf(claimed.getMessage()));
    }
  }

  private Verdict theSeedItWrites(
      SimulatorProvider provider,
      TestRunId testRunId,
      NewPaymentMethodFixture asked,
      PaymentMethodFixturePlan plan) {
    return switch (fixtures.seed(asked, plan, theKeysManyFixturesMayShare(provider, asked))) {
      case Seeding.Claimed claimed ->
          new Verdict.Conflicting(claimed.property(), claimed.message());
      case Seeding.Seeded seeded -> {
        testRuns.register(testRunId);
        yield new Verdict.Seeded(seeded.fixture());
      }
    };
  }

  public @Nullable Verdict theReasonsItWouldNotSeed(
      SimulatorProvider provider, DeclaredPaymentMethodFixture declared, ObjectOrigin askedBy) {
    NewPaymentMethodFixture asked;
    try {
      asked =
          PaymentMethodFixtureGrammar.fixtureOf(
              provider.id(),
              TestRunId.declaredOr(declared.testRunId(), whenNoTestRunIsNamed),
              declared,
              askedBy);
    } catch (IllegalArgumentException rejected) {
      return new Verdict.Rejected(String.valueOf(rejected.getMessage()));
    }
    return theProviderNativeIdThisProviderReadsNothingOf(provider, asked);
  }

  public Removal forget(
      SimulatorProvider provider, @Nullable String declaredTestRunId, @Nullable String fixtureId) {
    TestRunId testRunId;
    try {
      testRunId = TestRunId.declaredOr(declaredTestRunId, whenNoTestRunIsNamed);
    } catch (IllegalArgumentException rejected) {
      return new Removal.Rejected(String.valueOf(rejected.getMessage()));
    }
    if (fixtureId == null || fixtureId.isBlank()) {
      return new Removal.Rejected(A_FIXTURE_IS_REMOVED_BY_ITS_IDENTITY);
    }
    String named = fixtureId.strip();
    Optional<PaymentMethodFixture> doomed = fixtures.find(provider.id(), testRunId, named);
    if (doomed.isEmpty()) {
      return new Removal.NotSeeded(A_FIXTURE_IS_REMOVED_BY_ITS_IDENTITY);
    }
    if (!fixtures.remove(provider.id(), testRunId, named, provider.forgetFixture(doomed.get()))) {
      return new Removal.NotSeeded(A_FIXTURE_IS_REMOVED_BY_ITS_IDENTITY);
    }
    return new Removal.Removed(doomed.get());
  }

  private static Set<String> theKeysManyFixturesMayShare(
      SimulatorProvider provider, NewPaymentMethodFixture asked) {
    ProviderPaymentMethodFields declared = provider.paymentMethodFields();
    Set<String> shared = new LinkedHashSet<>();
    for (String key : asked.providerIds().keySet()) {
      if (declared.namesAnOwner(key)) {
        shared.add(key);
      }
    }
    return Set.copyOf(shared);
  }

  private static @Nullable Verdict theProviderNativeIdThisProviderReadsNothingOf(
      SimulatorProvider provider, NewPaymentMethodFixture asked) {
    ProviderPaymentMethodFields declared = provider.paymentMethodFields();
    for (Map.Entry<String, String> named : asked.providerIds().entrySet()) {
      if (declared.field(named.getKey()).isEmpty()) {
        return new Verdict.Refused(
            "providerIds." + named.getKey(), declared.theRefusalOf(named.getKey()));
      }
    }
    for (ConfigurableField field : declared.fields()) {
      if (field.required() && !asked.providerIds().containsKey(field.key())) {
        return new Verdict.Refused(
            "providerIds." + field.key(),
            "provider "
                + provider.id().value()
                + " drives a seeded payment method by "
                + field.label()
                + ", so a fixture that leaves providerIds."
                + field.key()
                + " out could never be used; this provider reads "
                + new TreeSet<>(declared.keys()));
      }
    }
    return null;
  }

  private @Nullable Verdict theScenarioThisTestRunHasNotWritten(
      SimulatorProvider provider, NewPaymentMethodFixture asked) {
    String named = asked.defaultScenario();
    if (named == null) {
      return null;
    }
    if (scenarios.find(provider.id(), asked.testRunId(), named).isEmpty()) {
      return new Verdict.Refused(
          DEFAULT_SCENARIO,
          "defaultScenario must name a scenario rule this provider and test run already hold,"
              + " because a fixture names the scenario it is meant to trigger and writes none of"
              + " its own: the rule that fires is the one written with match.paymentMethodId equal"
              + " to the provider-native id this fixture carries");
    }
    return null;
  }
}
