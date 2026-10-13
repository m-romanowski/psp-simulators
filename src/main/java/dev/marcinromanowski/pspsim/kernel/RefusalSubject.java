package dev.marcinromanowski.pspsim.kernel;

import java.util.Optional;

public sealed interface RefusalSubject {

  record TheObject(String providerObjectId) implements RefusalSubject {

    public TheObject {
      if (providerObjectId.isBlank()) {
        throw new IllegalArgumentException(
            "a refusal that says it names an object must carry the id that object answers to, and a"
                + " blank id names nothing, so the trail would carry a refusal whose subject a"
                + " tester could neither open nor rule out");
      }
    }
  }

  record NoObjectAnsweredTo(String theFieldThatNamedIt) implements RefusalSubject {

    public NoObjectAnsweredTo {
      if (theFieldThatNamedIt.isBlank()) {
        throw new IllegalArgumentException(
            "a refusal that says no object of this run answered must name the request field it"
                + " looked under, because a tester told only that nothing was found cannot tell a"
                + " request that named the wrong object from one that named the wrong field");
      }
    }
  }

  record SeveralObjectsAnsweredTo(String theFieldThatNamedThem, int howManyAnswered)
      implements RefusalSubject {

    public SeveralObjectsAnsweredTo {
      if (theFieldThatNamedThem.isBlank()) {
        throw new IllegalArgumentException(
            "a refusal that says several objects answered must name the request field they answered"
                + " under, because a tester told only that the answer was ambiguous cannot tell"
                + " which of the things they seeded made it so");
      }
      if (howManyAnswered < 2) {
        throw new IllegalArgumentException(
            "a refusal that says several objects answered must have counted at least two of them,"
                + " and a count below that describes an answer this arm does not describe, which"
                + " belongs to "
                + TheObject.class.getSimpleName()
                + " or to "
                + NoObjectAnsweredTo.class.getSimpleName());
      }
    }
  }

  record AnObjectItWillNotName(String whyItsContractNamesNone) implements RefusalSubject {

    public AnObjectItWillNotName {
      if (whyItsContractNamesNone.isBlank()) {
        throw new IllegalArgumentException(
            "a provider that knows this run holds the object it refused over and answers without"
                + " naming it must say in its own words why its contract names none, because a"
                + " screen reporting that absence in this simulator's words would be reporting a"
                + " decision this simulator did not make");
      }
    }
  }

  record NoObjectWasNamed(String whatItRefusedInstead) implements RefusalSubject {

    public NoObjectWasNamed {
      if (whatItRefusedInstead.isBlank()) {
        throw new IllegalArgumentException(
            "a refusal that names no object of this run must say in its own words what it refused"
                + " instead, because a tester told only that no object was named cannot tell a"
                + " request the provider never let reach one from a request whose object this"
                + " simulator forgot to record");
      }
    }
  }

  static RefusalSubject theObject(String providerObjectId) {
    return new TheObject(providerObjectId);
  }

  static RefusalSubject noObjectAnsweredTo(String theFieldThatNamedIt) {
    return new NoObjectAnsweredTo(theFieldThatNamedIt);
  }

  static RefusalSubject severalObjectsAnsweredTo(
      String theFieldThatNamedThem, int howManyAnswered) {
    return new SeveralObjectsAnsweredTo(theFieldThatNamedThem, howManyAnswered);
  }

  static RefusalSubject anObjectItWillNotName(String whyItsContractNamesNone) {
    return new AnObjectItWillNotName(whyItsContractNamesNone);
  }

  static RefusalSubject noObjectWasNamed(String whatItRefusedInstead) {
    return new NoObjectWasNamed(whatItRefusedInstead);
  }

  default Optional<String> theObjectItNames() {
    return this instanceof TheObject named
        ? Optional.of(named.providerObjectId())
        : Optional.empty();
  }
}
