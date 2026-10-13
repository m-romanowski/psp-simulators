package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.Fact;
import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface ReadOutcome {

  record Answered(
      List<AnsweredRow> rows, List<Fact> whatItAnsweredAboutTheQuery, @Nullable String scenarioId)
      implements ReadOutcome {

    public Answered {
      rows = List.copyOf(rows);
      whatItAnsweredAboutTheQuery = List.copyOf(whatItAnsweredAboutTheQuery);
    }

    public Answered(List<AnsweredRow> rows) {
      this(rows, List.of(), null);
    }

    public Answered(List<AnsweredRow> rows, @Nullable String scenarioId) {
      this(rows, List.of(), scenarioId);
    }

    public boolean itAnsweredNoRowAtAll() {
      return rows.isEmpty();
    }
  }

  record RefusedByTheProvider(String inItsOwnWords, @Nullable String scenarioId)
      implements ReadOutcome {

    public RefusedByTheProvider {
      if (inItsOwnWords.isBlank()) {
        throw new IllegalArgumentException(
            "a provider that refused to answer a read answers with the refusal its own contract"
                + " publishes, and an empty refusal tells a tester only that something failed"
                + " where the whole point of the read was to be told something");
      }
    }
  }

  record AnsweredByAScenarioInstead(String scenarioId) implements ReadOutcome {}

  record NotOffered(String whatThisProviderHasInstead) implements ReadOutcome {

    public NotOffered {
      if (whatThisProviderHasInstead.isBlank()) {
        throw new IllegalArgumentException(
            "a provider whose module answers no read of this kind must say in its own words what"
                + " its contract has in place of one, because a screen reporting that absence in"
                + " this simulator's words would be reporting a decision this simulator did not"
                + " make");
      }
    }
  }

  record NoSuchAccount() implements ReadOutcome {}

  default @Nullable String theRuleThatDecidedIt() {
    return switch (this) {
      case Answered answered -> answered.scenarioId();
      case RefusedByTheProvider refused -> refused.scenarioId();
      case AnsweredByAScenarioInstead answered -> answered.scenarioId();
      case NotOffered _ -> null;
      case NoSuchAccount _ -> null;
    };
  }
}
