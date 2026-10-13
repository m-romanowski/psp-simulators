package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ProviderReads(
    ProviderId provider, List<DeclaredRead> reads, String whatItPublishesInstead) {

  public ProviderReads {
    if (reads.isEmpty() && whatItPublishesInstead.isBlank()) {
      throw new IllegalArgumentException(
          "provider "
              + provider.value()
              + " publishes no read a tester asks with values of their own, and must say in its"
              + " own words what its contract has in place of one, because a screen reporting"
              + " that absence in this simulator's words would be reporting a decision this"
              + " simulator did not make");
    }
    if (!reads.isEmpty() && !whatItPublishesInstead.isBlank()) {
      throw new IllegalArgumentException(
          "provider "
              + provider.value()
              + " publishes "
              + reads.size()
              + " read(s) a tester asks and at the same time says what it has in place of one,"
              + " and a screen reading both would offer the questions and tell a tester there"
              + " are none");
    }
    Map<String, DeclaredRead> byName = new LinkedHashMap<>();
    for (DeclaredRead read : reads) {
      if (byName.put(read.name(), read) != null) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " declares "
                + read.name()
                + " twice as a read, so one answer from a tester would address two of them");
      }
    }
    reads = List.copyOf(reads);
  }

  public static ProviderReads noneOfThem(ProviderId provider, String whatItPublishesInstead) {
    return new ProviderReads(provider, List.of(), whatItPublishesInstead);
  }

  public static ProviderReads theOnesItPublishes(ProviderId provider, List<DeclaredRead> reads) {
    return new ProviderReads(provider, reads, "");
  }

  public boolean publishesAny() {
    return !reads.isEmpty();
  }

  public Optional<DeclaredRead> readNamed(String name) {
    for (DeclaredRead read : reads) {
      if (read.name().equals(name)) {
        return Optional.of(read);
      }
    }
    return Optional.empty();
  }

  public List<String> names() {
    List<String> names = new ArrayList<>();
    for (DeclaredRead read : reads) {
      names.add(read.name());
    }
    return List.copyOf(names);
  }

  public String theReadsItOffers() {
    List<String> offered = new ArrayList<>();
    for (DeclaredRead read : reads) {
      offered.add(read.label() + " (" + read.name() + ")");
    }
    return String.join(", ", offered);
  }

  public String theRefusalOfAQuestionThisProviderDoesNotAnswer(String readNamed) {
    if (reads.isEmpty()) {
      return "provider "
          + provider.value()
          + " answers no question a tester asks with identifiers of their own, so there is no "
          + readNamed
          + " to ask of it and no other question of it either: "
          + whatItPublishesInstead;
    }
    return "provider "
        + provider.value()
        + " answers one of these questions and no other: "
        + theReadsItOffers()
        + ". Go back a step and pick one of them.";
  }

  public ReadOutcome theOutcomeOfAModuleThatPerformsNoRead(String readNamed) {
    if (reads.isEmpty()) {
      return new ReadOutcome.NotOffered(whatItPublishesInstead);
    }
    throw new IllegalStateException(
        "provider "
            + provider.value()
            + " publishes the read "
            + readNamed
            + " and its module performs none, so a tester who asked the question this provider"
            + " declares would be told the provider publishes no read at all. A module that"
            + " declares a read performs it: the reads it declares are "
            + theReadsItOffers());
  }
}
