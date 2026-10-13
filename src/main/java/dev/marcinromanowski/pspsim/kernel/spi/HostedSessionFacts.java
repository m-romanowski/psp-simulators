package dev.marcinromanowski.pspsim.kernel.spi;

import java.time.Instant;

public sealed interface HostedSessionFacts {

  sealed interface Party {

    record NamedBy(String named) implements Party {

      public NamedBy {
        if (named.isBlank()) {
          throw new IllegalArgumentException(
              "a provider module that names the party an object a payer finishes is for must carry"
                  + " the value it publishes for them, because a screen drawing a named party from"
                  + " this would print an empty cell where a tester reads a correlation");
        }
      }
    }

    record NoPartyThisProviderPublishes(String whatThisProviderHasInstead) implements Party {

      public NoPartyThisProviderPublishes {
        if (whatThisProviderHasInstead.isBlank()) {
          throw new IllegalArgumentException(
              "a provider module whose contract names no party on the object a payer finishes must"
                  + " say what it has instead, because a screen reporting that absence in this"
                  + " simulator's words would be reporting a decision this simulator did not make");
        }
      }
    }
  }

  sealed interface Deadline {

    record ItExpiresAt(Instant at) implements Deadline {}

    record NoDeadlineThisProviderPublishes(String whatThisProviderHasInstead) implements Deadline {

      public NoDeadlineThisProviderPublishes {
        if (whatThisProviderHasInstead.isBlank()) {
          throw new IllegalArgumentException(
              "a provider module that publishes no deadline on the object a payer finishes must say"
                  + " what it has instead, because a screen reporting that absence in this"
                  + " simulator's words would be reporting a decision this simulator did not make");
        }
      }
    }
  }

  record AnObjectAPayerFinishes(
      String theAccountItBelongsTo,
      Party theParty,
      String theOperationItWasStartedFor,
      Instant createdAt,
      Deadline deadline)
      implements HostedSessionFacts {

    public AnObjectAPayerFinishes {
      if (theAccountItBelongsTo.isBlank()) {
        throw new IllegalArgumentException(
            "an object a payer finishes belongs to one provider account, and a screen listing it"
                + " without one would offer a tester no way to tell which account's client traffic"
                + " started it");
      }
      if (theOperationItWasStartedFor.isBlank()) {
        throw new IllegalArgumentException(
            "an object a payer finishes was started for one operation, named as this provider"
                + " spells it, and a screen listing it without one could not say what finishing the"
                + " page would do");
      }
    }

    public boolean aPartyIsNamed() {
      return theParty instanceof Party.NamedBy;
    }

    public String thePartyAsItReads() {
      return switch (theParty) {
        case Party.NamedBy named -> named.named();
        case Party.NoPartyThisProviderPublishes none -> none.whatThisProviderHasInstead();
      };
    }

    public boolean itExpiresAtAKnownTime() {
      return deadline instanceof Deadline.ItExpiresAt;
    }

    public String theDeadlineAsItReads() {
      return switch (deadline) {
        case Deadline.ItExpiresAt when -> when.at().toString();
        case Deadline.NoDeadlineThisProviderPublishes none -> none.whatThisProviderHasInstead();
      };
    }

    public boolean itsDeadlineHasPassedBy(Instant now) {
      return deadline instanceof Deadline.ItExpiresAt when && !now.isBefore(when.at());
    }
  }

  record NotAnObjectAPayerFinishes() implements HostedSessionFacts {}
}
