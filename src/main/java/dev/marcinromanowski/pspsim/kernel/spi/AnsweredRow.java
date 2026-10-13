package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record AnsweredRow(@Nullable String providerObjectId, List<AnsweredRow.Fact> facts) {

  public sealed interface AnsweredValue {

    record TheValueItAnswered(String asTheProviderAnsweredIt) implements AnsweredValue {}

    record NoValueAtAll() implements AnsweredValue {}

    record TheMembersUnderIt(List<Fact> members) implements AnsweredValue {

      public TheMembersUnderIt {
        members = List.copyOf(members);
      }
    }

    record TheElementsUnderIt(List<AnsweredValue> elements) implements AnsweredValue {

      public TheElementsUnderIt {
        elements = List.copyOf(elements);
      }
    }
  }

  public record Fact(String asTheProviderNamesIt, AnsweredValue value) {

    public Fact {
      if (asTheProviderNamesIt.isBlank()) {
        throw new IllegalArgumentException(
            "a fact a read answered must carry the name its provider answered it under, because a"
                + " screen prints that name beside the value and a tester matches it against the"
                + " field their own application reads");
      }
    }

    public Fact(String asTheProviderNamesIt, String asTheProviderAnsweredIt) {
      this(asTheProviderNamesIt, new AnsweredValue.TheValueItAnswered(asTheProviderAnsweredIt));
    }
  }

  public AnsweredRow {
    if (providerObjectId == null && facts.isEmpty()) {
      throw new IllegalArgumentException(
          "a row a read answered names the object it is about or carries the facts the provider"
              + " answered about it, and a row that does neither prints as a blank block on a"
              + " screen: a read that found nothing answers no row at all rather than an empty"
              + " one");
    }
    facts = List.copyOf(facts);
  }
}
