package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.Set;
import java.util.function.UnaryOperator;

record StripeVersionChange(
    StripeApiVersion version,
    String changelog,
    Set<String> operations,
    UnaryOperator<StripeCoreRequest> upgrade) {

  static final String A_CHANGE_SHIPS_IN_A_RELEASE_THIS_SIMULATOR_ANSWERS =
      "a version change is walked to from the release a request is answered at, so a change"
          + " declared at a release this simulator does not answer could never be walked to";

  static final String A_CHANGE_TOUCHES_AN_OPERATION =
      "a version change transforms the requests of the operations it touches, so a change that"
          + " touches none would transform nothing";

  StripeVersionChange {
    operations = Set.copyOf(operations);
    if (StripeApiVersions.answeredNamed(version.wireValue()).isEmpty()) {
      throw new IllegalArgumentException(A_CHANGE_SHIPS_IN_A_RELEASE_THIS_SIMULATOR_ANSWERS);
    }
    if (operations.isEmpty()) {
      throw new IllegalArgumentException(A_CHANGE_TOUCHES_AN_OPERATION);
    }
  }

  boolean touches(String operation) {
    return operations.contains(operation);
  }
}
