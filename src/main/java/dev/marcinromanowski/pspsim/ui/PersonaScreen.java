package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.SeededPersona;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class PersonaScreen {

  public static final String THE_MASK_AN_INSTRUMENT_CARRIES = "**** ";

  public static final String A_SEEDING_IS_CONFIRMED = "yes";

  public static final String THE_OBJECTS_ARE_SHOWN_BEFORE_THEY_ARE_CREATED =
      "a persona is a shorthand for a set of fixtures and scenario rules and nothing more, and"
          + " which rules those are is this provider's answer and not the persona's, so this form"
          + " shows them before it creates any: read them below, tick confirm and press the same"
          + " persona again to create them";

  public static final String NOTHING_IS_HIDDEN_BEHIND_A_PERSONA =
      "Everything a persona creates is an ordinary object of this test run. The payment method it"
          + " seeds is on the Payment methods screen, where you can read it and remove it on its"
          + " own; the rules it writes are on the Scenarios screen, where you can read each one and"
          + " delete it on its own. Nothing here holds a copy of them and nothing behaves"
          + " differently because a persona put it there.";

  public static final String A_PERSONA_IS_NAMED_BY_THE_FORM =
      "persona must name one this provider publishes, and this form named none";

  public static final String THE_RULES_ARE_THE_RUNS_RULES =
      "The rules a persona writes are this test run's rules, not this instrument's: they narrow on"
          + " the operation they name and on nothing else, exactly as the same preset does on"
          + " the Scenarios screen. Two personas that disagree cannot share one test run, so give"
          + " each one a run of its own.";

  public static final String IT_GIVES_NOTHING_OF_ITS_OWN =
      "nothing of its own; everything it gives is repeated below";

  private static final String THE_END_OF_A_SENTENCE = ". ";

  private static final char A_QUOTATION_MARK = '"';

  private PersonaScreen() {}

  public record Instrument(
      String alias,
      String ownerId,
      String type,
      @Nullable String brand,
      @Nullable String maskedCard,
      @Nullable String expiry,
      @Nullable String funding,
      List<String> providerNativeIds,
      String howThoseAreMinted,
      @Nullable Integer triggersTheRuleAt) {

    public Instrument {
      providerNativeIds = List.copyOf(providerNativeIds);
    }
  }

  public record Row(
      String name,
      String intent,
      String whatATesterGets,
      String whatItGives,
      List<String> presets,
      List<Instrument> instruments,
      boolean theRunAlreadyHoldsIt) {

    public Row {
      presets = List.copyOf(presets);
      instruments = List.copyOf(instruments);
    }
  }

  public record Uncovered(String intent, String whatATesterGets, String instead) {}

  public record SharedFact(List<String> personas, String fact) {

    public SharedFact {
      personas = List.copyOf(personas);
    }
  }

  public record View(
      String provider,
      String testRunId,
      List<Row> personas,
      List<Uncovered> uncovered,
      List<SharedFact> theFactsMoreThanOnePersonaRepeats) {

    public View {
      personas = List.copyOf(personas);
      uncovered = List.copyOf(uncovered);
      theFactsMoreThanOnePersonaRepeats = List.copyOf(theFactsMoreThanOnePersonaRepeats);
    }
  }

  public record Proposal(
      String persona,
      String intent,
      String presets,
      List<ScenarioScreen.Proposed> rules,
      List<Instrument> instruments,
      @Nullable String theRefusalThisPreviewCannotForesee) {

    public Proposal {
      rules = List.copyOf(rules);
      instruments = List.copyOf(instruments);
    }
  }

  public record CreatedRule(String scenarioId, @Nullable String operation, String action) {}

  public record CreatedInstrument(
      String fixtureId,
      String alias,
      List<String> providerNativeIds,
      @Nullable String defaultScenario) {

    public CreatedInstrument {
      providerNativeIds = List.copyOf(providerNativeIds);
    }
  }

  public record Created(
      String persona,
      String intent,
      String testRunId,
      List<CreatedRule> rules,
      List<CreatedInstrument> instruments) {

    public Created {
      rules = List.copyOf(rules);
      instruments = List.copyOf(instruments);
    }
  }

  public static View of(
      String provider,
      String testRunId,
      ProviderPersonas published,
      List<PaymentMethodFixture> held) {
    List<List<String>> whatEachOfThemGives = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (DeclaredPersona persona : published.personas()) {
      whatEachOfThemGives.add(theSentencesOf(persona.whatItGives().strip()));
      names.add(persona.name());
    }
    List<Row> rows = new ArrayList<>();
    int at = 0;
    for (DeclaredPersona persona : published.personas()) {
      List<String> sentences = whatEachOfThemGives.get(at);
      int shared = theTrailingSentencesAnotherOfThemAlsoSays(whatEachOfThemGives, at);
      String itsOwn = theTextOf(sentences.subList(0, sentences.size() - shared));
      rows.add(
          new Row(
              persona.name(),
              persona.intent().publishedName(),
              persona.intent().whatATesterGets(),
              itsOwn.isEmpty() ? IT_GIVES_NOTHING_OF_ITS_OWN : itsOwn,
              persona.thePresetNamesItWrites(),
              instrumentsOf(persona.fixtures()),
              theRunAlreadyHolds(persona, held)));
      at++;
    }
    List<SharedFact> facts = theFactsMoreThanOneOfThemRepeats(whatEachOfThemGives, names);
    List<Uncovered> uncovered = new ArrayList<>();
    for (PersonaIntent intent : PersonaIntent.values()) {
      String instead = published.intentsItPublishesNoneFor().get(intent);
      if (instead != null) {
        uncovered.add(new Uncovered(intent.publishedName(), intent.whatATesterGets(), instead));
      }
    }
    return new View(provider, testRunId, rows, uncovered, facts);
  }

  public static Proposal proposalOf(
      DeclaredPersona persona,
      List<NewScenarioRule> rules,
      @Nullable String theRefusalThisPreviewCannotForesee) {
    List<ScenarioScreen.Proposed> proposed = new ArrayList<>();
    for (NewScenarioRule rule : rules) {
      proposed.add(ScenarioScreen.proposalOf(rule));
    }
    return new Proposal(
        persona.name(),
        persona.intent().publishedName(),
        String.join(", ", persona.thePresetNamesItWrites()),
        proposed,
        instrumentsOf(persona.fixtures()),
        theRefusalThisPreviewCannotForesee);
  }

  public static Created createdOf(SeededPersona seeded) {
    List<CreatedRule> rules = new ArrayList<>();
    for (ScenarioRule rule : seeded.rules()) {
      rules.add(
          new CreatedRule(
              rule.scenarioId(),
              rule.match().operation(),
              ScenarioScreen.proposalOf(rule.asWrittenAgain()).action()));
    }
    List<CreatedInstrument> instruments = new ArrayList<>();
    for (PaymentMethodFixture fixture : seeded.fixtures()) {
      List<String> ids = new ArrayList<>();
      for (var id : fixture.providerIds().entrySet()) {
        ids.add(id.getKey() + " " + id.getValue());
      }
      instruments.add(
          new CreatedInstrument(
              fixture.fixtureId(), fixture.fixtureAlias(), ids, fixture.defaultScenario()));
    }
    return new Created(
        seeded.name(),
        seeded.intent().publishedName(),
        seeded.testRunId().value(),
        rules,
        instruments);
  }

  public static ActionVerdict.Refused theRefusalOfObjectsItDidNotCreate() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            "the persona this form showed and did not create, because a persona is confirmed"
                + " before it writes"));
  }

  private static List<String> theSentencesOf(String text) {
    List<String> sentences = new ArrayList<>();
    int from = 0;
    boolean quoted = false;
    for (int at = 0; at < text.length(); at++) {
      if (text.charAt(at) == A_QUOTATION_MARK) {
        quoted = !quoted;
      } else if (!quoted && text.startsWith(THE_END_OF_A_SENTENCE, at)) {
        sentences.add(text.substring(from, at + THE_END_OF_A_SENTENCE.length()));
        from = at + THE_END_OF_A_SENTENCE.length();
      }
    }
    sentences.add(text.substring(from));
    return sentences;
  }

  private static List<SharedFact> theFactsMoreThanOneOfThemRepeats(
      List<List<String>> whatEachOfThemGives, List<String> names) {
    List<SharedFact> facts = new ArrayList<>();
    for (int at = 0; at < whatEachOfThemGives.size(); at++) {
      List<String> sentences = whatEachOfThemGives.get(at);
      int depth = theTrailingSentencesAnotherOfThemAlsoSays(whatEachOfThemGives, at);
      while (depth > 0) {
        List<String> who = theOnesEndingOnTheLast(whatEachOfThemGives, names, at, depth);
        int shallowest = depth;
        while (shallowest > 1
            && theOnesEndingOnTheLast(whatEachOfThemGives, names, at, shallowest - 1).equals(who)) {
          shallowest--;
        }
        SharedFact fact =
            new SharedFact(
                who,
                theTextOf(
                    sentences.subList(
                        sentences.size() - depth, sentences.size() - shallowest + 1)));
        if (!facts.contains(fact)) {
          facts.add(fact);
        }
        depth = shallowest - 1;
      }
    }
    return facts;
  }

  private static List<String> theOnesEndingOnTheLast(
      List<List<String>> whatEachOfThemGives, List<String> names, int at, int sentences) {
    List<String> who = new ArrayList<>();
    for (int other = 0; other < whatEachOfThemGives.size(); other++) {
      if (theTrailingSentencesTwoOfThemShare(
              whatEachOfThemGives.get(at), whatEachOfThemGives.get(other))
          >= sentences) {
        who.add(names.get(other));
      }
    }
    return who;
  }

  private static int theTrailingSentencesAnotherOfThemAlsoSays(
      List<List<String>> whatEachOfThemGives, int at) {
    int longest = 0;
    for (int other = 0; other < whatEachOfThemGives.size(); other++) {
      if (other != at) {
        longest =
            Math.max(
                longest,
                theTrailingSentencesTwoOfThemShare(
                    whatEachOfThemGives.get(at), whatEachOfThemGives.get(other)));
      }
    }
    return longest;
  }

  private static int theTrailingSentencesTwoOfThemShare(List<String> one, List<String> other) {
    int shared = 0;
    while (shared < one.size()
        && shared < other.size()
        && one.get(one.size() - 1 - shared).equals(other.get(other.size() - 1 - shared))) {
      shared++;
    }
    return shared;
  }

  private static String theTextOf(List<String> sentences) {
    return String.join("", sentences).strip();
  }

  private static boolean theRunAlreadyHolds(
      DeclaredPersona persona, List<PaymentMethodFixture> held) {
    for (PersonaFixture instrument : persona.fixtures()) {
      for (PaymentMethodFixture already : held) {
        if (already.fixtureAlias().equals(instrument.alias())) {
          return true;
        }
      }
    }
    return false;
  }

  private static List<Instrument> instrumentsOf(List<PersonaFixture> instruments) {
    List<Instrument> views = new ArrayList<>();
    for (PersonaFixture instrument : instruments) {
      FixtureCard card = instrument.card();
      views.add(
          new Instrument(
              instrument.alias(),
              instrument.ownerId(),
              instrument.type().publishedName(),
              card == null ? null : card.brand(),
              card == null ? null : THE_MASK_AN_INSTRUMENT_CARRIES + card.last4(),
              card == null ? null : card.expMonth() + "/" + card.expYear(),
              card == null ? null : card.funding(),
              instrument.theProviderNativeKeysItCarries(),
              instrument.howThoseAreMinted(),
              instrument.triggersTheRuleAt()));
    }
    return views;
  }
}
