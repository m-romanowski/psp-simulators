package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class PersonaAdmission {

  public static final String THE_PERSONA_IT_ASKED_THIS_PROVIDER_TO_SEED =
      "the persona this request asked this provider to seed";

  public static final String PERSONA = "persona";

  public static final String A_PERSONA_IS_NAMED_BY_THE_REQUEST =
      "persona must name one this provider publishes, and this request named none";

  public static final String A_PROVIDER_MINTS_ITS_IDS_WHEN_THE_PERSONA_IS_CREATED =
      "this plan mints none of the provider-native ids it names: the provider mints them at the"
          + " moment the persona is created. A test run gives such an id to one instrument, so"
          + " creating this persona is refused, and nothing written, if the id minted then is one"
          + " this run already claims. This plan cannot foresee that refusal, because an id it"
          + " minted in order to check it would not be the id the creation uses. The ids compared"
          + " that way are ";

  public static final String A_PERSONA_IS_SEEDED_INTO_AN_ACCOUNT =
      "merchantId must name the account this persona's instruments belong to, because a provider"
          + " answers for a stored instrument only through the key that account is addressed with,"
          + " and an instrument filed under no account would sit on the object list looking"
          + " readable and be answered by nothing";

  public sealed interface Verdict {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_PERSONA_IT_ASKED_THIS_PROVIDER_TO_SEED));
      }
    }

    record Unknown(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.notFound(RefusalSubject.noObjectAnsweredTo(PERSONA));
      }
    }

    record Refused(List<ScenarioRefusal> refusals) implements Verdict {

      public Refused {
        refusals = List.copyOf(refusals);
      }

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(THE_PERSONA_IT_ASKED_THIS_PROVIDER_TO_SEED));
      }
    }

    record Conflicting(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.conflicting(
            RefusalSubject.noObjectWasNamed(THE_PERSONA_IT_ASKED_THIS_PROVIDER_TO_SEED));
      }
    }

    record Seeded(SeededPersona seeded) implements Verdict {

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  public record HeldInstrument(
      String theAliasTheRunHolds,
      String theAliasThisPersonaWouldSeed,
      @Nullable String underTheKey,
      @Nullable String andTheValue) {}

  public sealed interface Plan {

    record Rejected(String message) implements Plan {}

    record Unknown(String message) implements Plan {}

    record Refused(List<ScenarioRefusal> refusals) implements Plan {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }

    record Planned(
        DeclaredPersona persona,
        TestRunId testRunId,
        List<NewScenarioRule> rules,
        List<HeldInstrument> theInstrumentsThisRunAlreadyHolds,
        @Nullable String theRefusalThisPlanCannotForesee)
        implements Plan {

      public Planned {
        rules = List.copyOf(rules);
        theInstrumentsThisRunAlreadyHolds = List.copyOf(theInstrumentsThisRunAlreadyHolds);
      }

      public boolean theRunAlreadyHoldsIt() {
        return !theInstrumentsThisRunAlreadyHolds.isEmpty();
      }

      public List<String> theAliasesThisRunAlreadyHolds() {
        List<String> aliases = new ArrayList<>();
        for (HeldInstrument held : theInstrumentsThisRunAlreadyHolds) {
          aliases.add(held.theAliasTheRunHolds());
        }
        return List.copyOf(aliases);
      }
    }
  }

  private final ScenarioAdmission scenarios;
  private final PaymentMethodFixtureAdmission fixtureAdmission;
  private final PaymentMethodFixtures fixtures;
  private final IdGenerator ids;
  private final UnitOfWork unitOfWork;
  private final TestRunId whenNoTestRunIsNamed;

  public PersonaAdmission(
      ScenarioAdmission scenarios,
      PaymentMethodFixtureAdmission fixtureAdmission,
      PaymentMethodFixtures fixtures,
      IdGenerator ids,
      UnitOfWork unitOfWork,
      TestRunId whenNoTestRunIsNamed) {
    this.scenarios = scenarios;
    this.fixtureAdmission = fixtureAdmission;
    this.fixtures = fixtures;
    this.ids = ids;
    this.unitOfWork = unitOfWork;
    this.whenNoTestRunIsNamed = whenNoTestRunIsNamed;
  }

  public ProviderPersonas publishedBy(SimulatorProvider module) {
    return module.personas(ids);
  }

  public Plan whatItWouldCreate(SimulatorProvider module, DeclaredPersonaSeeding declared) {
    TestRunId run;
    try {
      run = TestRunId.declaredOr(declared.testRunId(), whenNoTestRunIsNamed);
    } catch (IllegalArgumentException rejected) {
      return new Plan.Rejected(String.valueOf(rejected.getMessage()));
    }
    String named = declared.persona() == null ? "" : declared.persona().strip();
    if (named.isEmpty()) {
      return new Plan.Rejected(A_PERSONA_IS_NAMED_BY_THE_REQUEST);
    }
    ProviderPersonas published = publishedBy(module);
    Optional<DeclaredPersona> found = published.named(named);
    if (found.isEmpty()) {
      return new Plan.Unknown(published.theRefusalOfAnUnknownPersona());
    }
    DeclaredPersona persona = found.get();

    List<NewScenarioRule> rules = new ArrayList<>();
    for (ScenarioPreset preset : persona.presets()) {
      ScenarioAdmission.Expansion expansion = scenarios.preview(module, presetOf(run, preset));
      if (expansion instanceof ScenarioAdmission.Expansion.Rejected rejected) {
        throw new IllegalStateException(
            A_PRESET_REQUEST_THIS_CLASS_BUILT_ITSELF
                + preset.publishedName()
                + ": "
                + rejected.message());
      }
      if (expansion instanceof ScenarioAdmission.Expansion.Refused refused) {
        return new Plan.Refused(refused.refusals());
      }
      rules.addAll(((ScenarioAdmission.Expansion.Expanded) expansion).rules());
    }

    List<ScenarioRefusal> unplaceable =
        thePlacesItNamesThatItWritesNoRuleFor(persona, rules.size());
    if (!unplaceable.isEmpty()) {
      return new Plan.Refused(unplaceable);
    }
    return new Plan.Planned(
        persona,
        run,
        rules,
        theInstrumentsAlreadyHeld(module, run, persona),
        theRefusalAPlanCannotForesee(persona, module.paymentMethodFields()));
  }

  public static List<String> theProviderNativeKeysAPlanCannotCompare(
      DeclaredPersona persona, ProviderPaymentMethodFields fields) {
    List<String> compared = new ArrayList<>();
    for (PersonaFixture instrument : persona.fixtures()) {
      for (String key : instrument.theProviderNativeKeysItCarries()) {
        if (!fields.namesAnOwner(key)
            && !instrument.theIdsItPublishes().containsKey(key)
            && !compared.contains(key)) {
          compared.add(key);
        }
      }
    }
    return List.copyOf(compared);
  }

  public static @Nullable String theRefusalAPlanCannotForesee(
      DeclaredPersona persona, ProviderPaymentMethodFields fields) {
    List<String> compared = theProviderNativeKeysAPlanCannotCompare(persona, fields);
    if (compared.isEmpty()) {
      return null;
    }
    List<String> named = new ArrayList<>();
    for (String key : compared) {
      named.add("providerIds." + key);
    }
    return A_PROVIDER_MINTS_ITS_IDS_WHEN_THE_PERSONA_IS_CREATED + String.join(", ", named) + ".";
  }

  public Verdict seed(
      SimulatorProvider module, DeclaredPersonaSeeding declared, ObjectOrigin askedBy) {
    Plan plan = whatItWouldCreate(module, declared);
    if (plan instanceof Plan.Rejected rejected) {
      return new Verdict.Rejected(rejected.message());
    }
    if (plan instanceof Plan.Unknown unknown) {
      return new Verdict.Unknown(unknown.message());
    }
    if (plan instanceof Plan.Refused refused) {
      return new Verdict.Refused(refused.refusals());
    }
    Plan.Planned planned = (Plan.Planned) plan;
    String merchantId = declared.merchantId() == null ? "" : declared.merchantId().strip();
    if (merchantId.isEmpty()) {
      return new Verdict.Rejected(A_PERSONA_IS_SEEDED_INTO_AN_ACCOUNT);
    }
    if (planned.theRunAlreadyHoldsIt()) {
      return new Verdict.Conflicting(theRunAlreadyHoldsIt(planned));
    }

    DeclaredPersona persona = planned.persona();
    TestRunId run = planned.testRunId();
    List<MintedInstrument> minted = new ArrayList<>();
    for (PersonaFixture instrument : persona.fixtures()) {
      minted.add(new MintedInstrument(instrument, instrument.mint()));
    }

    String claimed = theProviderNativeIdThisRunAlreadyClaims(module, run, minted);
    if (claimed != null) {
      return new Verdict.Conflicting(claimed);
    }

    for (MintedInstrument instrument : minted) {
      PaymentMethodFixtureAdmission.Verdict unseedable =
          fixtureAdmission.theReasonsItWouldNotSeed(
              module,
              theFixtureItWouldSeed(
                  run, merchantId, instrument.instrument(), instrument.ids(), null),
              askedBy);
      if (unseedable != null) {
        return theSameRefusalAboutThePersona(unseedable);
      }
    }

    try {
      return unitOfWork.keepingOnly(
          Verdict.Seeded.class::isInstance,
          () -> everythingThePersonaWrites(module, persona, run, merchantId, minted, askedBy));
    } catch (ClaimedMeanwhileException claimedMeanwhile) {
      return new Verdict.Conflicting(String.valueOf(claimedMeanwhile.getMessage()));
    }
  }

  private Verdict everythingThePersonaWrites(
      SimulatorProvider module,
      DeclaredPersona persona,
      TestRunId run,
      String merchantId,
      List<MintedInstrument> minted,
      ObjectOrigin askedBy) {
    List<ScenarioRule> written = new ArrayList<>();
    for (ScenarioPreset preset : persona.presets()) {
      ScenarioAdmission.Verdict expanded = scenarios.expand(module, presetOf(run, preset));
      if (expanded instanceof ScenarioAdmission.Verdict.Rejected rejected) {
        throw new IllegalStateException(
            A_PRESET_REQUEST_THIS_CLASS_BUILT_ITSELF
                + preset.publishedName()
                + ": "
                + rejected.message());
      }
      if (expanded instanceof ScenarioAdmission.Verdict.Refused refused) {
        return new Verdict.Refused(refused.refusals());
      }
      written.addAll(((ScenarioAdmission.Verdict.Written) expanded).rules());
    }

    List<PaymentMethodFixture> seeded = new ArrayList<>();
    for (MintedInstrument instrument : minted) {
      PaymentMethodFixtureAdmission.Verdict verdict =
          fixtureAdmission.seed(
              module,
              theFixtureItWouldSeed(
                  run,
                  merchantId,
                  instrument.instrument(),
                  instrument.ids(),
                  theRuleItTriggers(instrument.instrument(), written)),
              askedBy);
      if (!(verdict instanceof PaymentMethodFixtureAdmission.Verdict.Seeded wasSeeded)) {
        return theSameRefusalAboutThePersona(verdict);
      }
      seeded.add(wasSeeded.fixture());
    }
    return new Verdict.Seeded(
        new SeededPersona(persona.name(), persona.intent(), run, written, seeded));
  }

  public static DeclaredPaymentMethodFixture theFixtureItWouldSeed(
      TestRunId run,
      String merchantId,
      PersonaFixture instrument,
      Map<String, String> minted,
      @Nullable String defaultScenario) {
    FixtureCard card = instrument.card();
    return new DeclaredPaymentMethodFixture(
        run.value(),
        merchantId,
        instrument.ownerId(),
        instrument.type().publishedName(),
        instrument.alias(),
        card == null
            ? null
            : new DeclaredCard(
                card.brand(), card.last4(), card.expMonth(), card.expYear(), card.funding()),
        minted,
        defaultScenario);
  }

  public static List<ScenarioRefusal> thePlacesItNamesThatItWritesNoRuleFor(
      DeclaredPersona persona, int rulesItWrites) {
    List<ScenarioRefusal> refusals = new ArrayList<>();
    List<PersonaFixture> instruments = persona.fixtures();
    for (int place = 0; place < instruments.size(); place++) {
      Integer names = instruments.get(place).triggersTheRuleAt();
      if (names != null && names >= rulesItWrites) {
        refusals.add(
            new ScenarioRefusal(
                "fixtures[" + place + "].triggersTheRuleAt",
                "persona "
                    + persona.name()
                    + " names the rule at place "
                    + names
                    + " as the default of instrument "
                    + instruments.get(place).alias()
                    + ", and its presets expand to "
                    + rulesItWrites
                    + " rules for this provider, so the instrument would be seeded against a rule"
                    + " this run does not hold"));
      }
    }
    return List.copyOf(refusals);
  }

  private static @Nullable String theRuleItTriggers(
      PersonaFixture instrument, List<ScenarioRule> written) {
    Integer place = instrument.triggersTheRuleAt();
    return place == null ? null : written.get(place).scenarioId();
  }

  public static final String A_PRESET_REQUEST_THIS_CLASS_BUILT_ITSELF =
      "a persona names a preset of this simulator's own published list and is seeded into a test"
          + " run this class resolved before it asked for anything, so a rejected expansion names"
          + " a request no caller could have sent: ";

  private static DeclaredPreset presetOf(TestRunId run, ScenarioPreset preset) {
    return new DeclaredPreset(run.value(), preset.publishedName(), 0, null);
  }

  private List<HeldInstrument> theInstrumentsAlreadyHeld(
      SimulatorProvider module, TestRunId run, DeclaredPersona persona) {
    ProviderPaymentMethodFields fields = module.paymentMethodFields();
    List<HeldInstrument> held = new ArrayList<>();
    List<PaymentMethodFixture> seeded = fixtures.of(module.id(), run);
    for (PersonaFixture instrument : persona.fixtures()) {
      for (PaymentMethodFixture already : seeded) {
        if (already.fixtureAlias().equals(instrument.alias())) {
          held.add(new HeldInstrument(already.fixtureAlias(), instrument.alias(), null, null));
        }
      }
      for (Map.Entry<String, String> published : instrument.theIdsItPublishes().entrySet()) {
        if (fields.namesAnOwner(published.getKey())) {
          continue;
        }
        for (PaymentMethodFixture already : seeded) {
          if (published.getValue().equals(already.providerIds().get(published.getKey()))
              && !already.fixtureAlias().equals(instrument.alias())) {
            held.add(
                new HeldInstrument(
                    already.fixtureAlias(),
                    instrument.alias(),
                    published.getKey(),
                    published.getValue()));
          }
        }
      }
    }
    return List.copyOf(held);
  }

  private record MintedInstrument(PersonaFixture instrument, Map<String, String> ids) {}

  private @Nullable String theProviderNativeIdThisRunAlreadyClaims(
      SimulatorProvider module, TestRunId run, List<MintedInstrument> minted) {
    ProviderPaymentMethodFields fields = module.paymentMethodFields();
    List<PaymentMethodFixture> already = fixtures.of(module.id(), run);
    for (MintedInstrument instrument : minted) {
      for (Map.Entry<String, String> id : instrument.ids().entrySet()) {
        if (fields.namesAnOwner(id.getKey())) {
          continue;
        }
        for (PaymentMethodFixture held : already) {
          if (id.getValue().equals(held.providerIds().get(id.getKey()))) {
            return theIdTwoInstrumentsWouldAnswerTo(
                run,
                new HeldInstrument(
                    held.fixtureAlias(),
                    instrument.instrument().alias(),
                    id.getKey(),
                    id.getValue()));
          }
        }
      }
    }
    return null;
  }

  private static String theRunAlreadyHoldsIt(Plan.Planned planned) {
    for (HeldInstrument held : planned.theInstrumentsThisRunAlreadyHolds()) {
      if (held.underTheKey() != null) {
        return theIdTwoInstrumentsWouldAnswerTo(planned.testRunId(), held);
      }
    }
    return "test run "
        + planned.testRunId().value()
        + " already holds the instrument "
        + String.join(", ", planned.theAliasesThisRunAlreadyHolds())
        + " that persona "
        + planned.persona().name()
        + " seeds, so seeding it again would either duplicate what is there or refuse halfway"
        + " through. Remove that instrument on the Payment methods screen, or seed this persona"
        + " into another test run.";
  }

  private static String theIdTwoInstrumentsWouldAnswerTo(TestRunId run, HeldInstrument held) {
    return "test run "
        + run.value()
        + " already holds the instrument "
        + held.theAliasTheRunHolds()
        + " under providerIds."
        + held.underTheKey()
        + " "
        + held.andTheValue()
        + ", and a run gives that id to one instrument, so the instrument "
        + held.theAliasThisPersonaWouldSeed()
        + " this persona seeds could not be told apart from it on any read route. Remove"
        + " that instrument on the Payment methods screen, or seed this persona into"
        + " another test run.";
  }

  private static Verdict theSameRefusalAboutThePersona(
      PaymentMethodFixtureAdmission.Verdict verdict) {
    return switch (verdict) {
      case PaymentMethodFixtureAdmission.Verdict.Rejected rejected ->
          new Verdict.Rejected(rejected.message());
      case PaymentMethodFixtureAdmission.Verdict.Refused refused ->
          new Verdict.Refused(List.of(new ScenarioRefusal(refused.property(), refused.message())));
      case PaymentMethodFixtureAdmission.Verdict.Conflicting conflicting ->
          new Verdict.Conflicting(conflicting.message());
      case PaymentMethodFixtureAdmission.Verdict.Seeded _ ->
          throw new IllegalStateException(
              "a fixture this persona seeded was handed to the branch that reports why one was"
                  + " not seeded, and the trail would carry a refusal about an instrument that"
                  + " exists");
    };
  }
}
