package dev.marcinromanowski.pspsim.kernel.testrun;

import java.util.List;
import java.util.Optional;

public enum TestRunResolutionRule {
  TEST_HEADER,
  ROUTE_ALIAS,
  ACCOUNT_CREDENTIALS,
  OBJECT_CORRELATION,
  DEFAULT,
  AMBIGUOUS,
  TEST_HEADER_UNMATCHED;

  public sealed interface WhenNothingMatched {

    record TheNextRuleDecides() implements WhenNothingMatched {}

    record ResolutionFails(TestRunResolutionRule verdict) implements WhenNothingMatched {}

    static WhenNothingMatched theNextRuleDecides() {
      return new TheNextRuleDecides();
    }

    static WhenNothingMatched resolutionFailsAs(TestRunResolutionRule verdict) {
      return new ResolutionFails(verdict);
    }
  }

  private static final List<TestRunResolutionRule> PRECEDENCE =
      List.of(TEST_HEADER, ROUTE_ALIAS, ACCOUNT_CREDENTIALS, OBJECT_CORRELATION);

  static {
    for (TestRunResolutionRule rule : values()) {
      if (rule.producesCandidates() != PRECEDENCE.contains(rule)) {
        throw new IllegalStateException(
            "every candidate-producing rule needs a place in the published precedence: "
                + rule.name());
      }
    }
  }

  public static List<TestRunResolutionRule> byPrecedence() {
    return PRECEDENCE;
  }

  public boolean producesCandidates() {
    return answerWhenNothingMatched().isPresent();
  }

  public boolean namesATestRun() {
    return switch (this) {
      case TEST_HEADER, ROUTE_ALIAS, ACCOUNT_CREDENTIALS, OBJECT_CORRELATION, DEFAULT -> true;
      case AMBIGUOUS, TEST_HEADER_UNMATCHED -> false;
    };
  }

  public WhenNothingMatched whenNothingMatched() {
    return answerWhenNothingMatched()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    name() + " is a verdict of resolution, not a rule that can match nothing"));
  }

  private Optional<WhenNothingMatched> answerWhenNothingMatched() {
    return switch (this) {
      case TEST_HEADER -> Optional.of(WhenNothingMatched.resolutionFailsAs(TEST_HEADER_UNMATCHED));
      case ROUTE_ALIAS -> Optional.of(WhenNothingMatched.theNextRuleDecides());
      case ACCOUNT_CREDENTIALS -> Optional.of(WhenNothingMatched.theNextRuleDecides());
      case OBJECT_CORRELATION -> Optional.of(WhenNothingMatched.theNextRuleDecides());
      case DEFAULT, AMBIGUOUS, TEST_HEADER_UNMATCHED -> Optional.empty();
    };
  }
}
