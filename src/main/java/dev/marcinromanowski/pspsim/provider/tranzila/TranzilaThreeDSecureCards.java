package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Map;
import java.util.Optional;

final class TranzilaThreeDSecureCards {

  static final String FRICTIONLESS_SUCCESS = "4907639999909022";

  static final String FRICTIONLESS_FAIL = "4907639999990022";

  static final String CHALLENGE_REQUIRED = "4918914107195005";

  static final String THE_CODE_THAT_PASSES = "555";

  static final Map<String, TranzilaThreeDSecureOutcome> PUBLISHED =
      Map.of(
          FRICTIONLESS_SUCCESS, TranzilaThreeDSecureOutcome.AUTHENTICATED,
          FRICTIONLESS_FAIL, TranzilaThreeDSecureOutcome.FAILED,
          CHALLENGE_REQUIRED, TranzilaThreeDSecureOutcome.CHALLENGED);

  private TranzilaThreeDSecureCards() {}

  static Optional<TranzilaThreeDSecureOutcome> ofThePan(String pan) {
    return Optional.ofNullable(PUBLISHED.get(pan));
  }

  static boolean passes(String code) {
    return THE_CODE_THAT_PASSES.equals(code.strip());
  }
}
