package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ScenarioDocument(
    @Nullable String testRunId,
    @Nullable String provider,
    @Nullable String preset,
    @Nullable List<PortableScenario> rules) {

  public static final String A_HEADER_VALUE_LEAVES_NOTHING =
      "a header narrowing is the one part of a rule whose value a caller supplies verbatim and a"
          + " request is expected to arrive carrying, so it is the one part that can hold a"
          + " credential. A document is copied into files, pasted into screens and posted back, so"
          + " this one carries no header at all: expand the preset without the narrowing, or write"
          + " the rules that need it by hand";

  public static final String A_RULE_OF_ONE_PROVIDER_MEANS_NOTHING_TO_ANOTHER =
      "the same preset expands into different operations for different providers, so a rule of one"
          + " provider means nothing to another";

  public static final String THE_DOCUMENT_NAMES_NO_PROVIDER = "names no provider at all";

  public static String theProviderThatExpandedThem(ProviderId addressed, @Nullable String carried) {
    String expandedThem =
        carried == null || carried.isBlank()
            ? THE_DOCUMENT_NAMES_NO_PROVIDER
            : "was expanded for " + carried;
    return "provider must name the provider this request addresses: this document "
        + expandedThem
        + " and this request addresses "
        + addressed.value()
        + ", and "
        + A_RULE_OF_ONE_PROVIDER_MEANS_NOTHING_TO_ANOTHER;
  }

  private static final String A_DOCUMENT_OF_NO_RULE =
      "rules must carry at least one rule, because a document that carries none would be taken"
          + " back as nothing written and reported as something written";

  public ScenarioDocument {
    rules = rules == null ? null : List.copyOf(rules);
  }

  public static ScenarioDocument of(
      TestRunId testRunId,
      ProviderId provider,
      ScenarioPreset preset,
      List<NewScenarioRule> expanded) {
    return of(testRunId, provider, preset.publishedName(), expanded);
  }

  public static ScenarioDocument of(
      TestRunId testRunId, ProviderId provider, List<NewScenarioRule> held) {
    return of(testRunId, provider, (String) null, held);
  }

  private static ScenarioDocument of(
      TestRunId testRunId,
      ProviderId provider,
      @Nullable String preset,
      List<NewScenarioRule> expanded) {
    if (!theNarrowingsNoDocumentCarries(expanded).isEmpty()) {
      throw new IllegalArgumentException(A_HEADER_VALUE_LEAVES_NOTHING);
    }
    List<PortableScenario> carried = new ArrayList<>();
    for (NewScenarioRule rule : expanded) {
      carried.add(PortableScenario.of(rule));
    }
    return new ScenarioDocument(testRunId.value(), provider.value(), preset, List.copyOf(carried));
  }

  public ScenarioDocument rewrittenFor(TestRunId testRunId) {
    return new ScenarioDocument(testRunId.value(), provider, preset, carriedRules());
  }

  public static List<ScenarioRefusal> theNarrowingsNoDocumentCarries(
      List<NewScenarioRule> expanded) {
    List<ScenarioRefusal> refused = new ArrayList<>();
    for (NewScenarioRule rule : expanded) {
      for (String name : rule.match().header().keySet()) {
        ScenarioRefusal refusal =
            new ScenarioRefusal("match.header." + name, A_HEADER_VALUE_LEAVES_NOTHING);
        if (!refused.contains(refusal)) {
          refused.add(refusal);
        }
      }
    }
    return List.copyOf(refused);
  }

  public List<ScenarioRefusal> theReasonsItCannotBeTakenBackBy(ProviderId addressed) {
    List<ScenarioRefusal> refused = new ArrayList<>();
    if (!addressed.value().equals(provider)) {
      refused.add(
          new ScenarioRefusal("provider", theProviderThatExpandedThem(addressed, provider)));
    }
    if (carriedRules().isEmpty()) {
      refused.add(new ScenarioRefusal("rules", A_DOCUMENT_OF_NO_RULE));
    }
    return List.copyOf(refused);
  }

  public List<PortableScenario> carriedRules() {
    List<PortableScenario> carried = rules;
    return carried == null ? List.of() : carried;
  }
}
