package dev.marcinromanowski.pspsim.kernel.spi;

import static java.util.Collections.unmodifiableSequencedMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;

public record DeclaredRead(
    String name,
    String label,
    String theQuestionItAnswers,
    EmptyAnswer whatAnEmptyAnswerWouldMean,
    List<AFieldATesterFillsIn> fields,
    SequencedMap<String, String> fieldsItFillsInItself) {

  public sealed interface EmptyAnswer {

    String inTheProvidersOwnWords();

    record ItMeansThis(String inTheProvidersOwnWords) implements EmptyAnswer {}

    record ItNeverAnswersNothing(String inTheProvidersOwnWords) implements EmptyAnswer {}

    static EmptyAnswer whatItMeans(String inTheProvidersOwnWords) {
      return new ItMeansThis(inTheProvidersOwnWords);
    }

    static EmptyAnswer itNeverAnswersNothingBecause(String inTheProvidersOwnWords) {
      return new ItNeverAnswersNothing(inTheProvidersOwnWords);
    }
  }

  public DeclaredRead(
      String name,
      String label,
      String theQuestionItAnswers,
      EmptyAnswer whatAnEmptyAnswerWouldMean,
      List<AFieldATesterFillsIn> fields) {
    this(
        name,
        label,
        theQuestionItAnswers,
        whatAnEmptyAnswerWouldMean,
        fields,
        new LinkedHashMap<>());
  }

  public DeclaredRead {
    if (name.isBlank()) {
      throw new IllegalArgumentException(
          "a read with no name cannot be asked for, because the name is what a screen sends back"
              + " when a tester picks the question they want answered");
    }
    if (label.isBlank()) {
      throw new IllegalArgumentException(
          "read "
              + name
              + " must carry a label, because a tester picks between these by the question each"
              + " one answers and not by the operation the provider happens to spell it with");
    }
    if (theQuestionItAnswers.isBlank()) {
      throw new IllegalArgumentException(
          "read "
              + name
              + " must say, in this provider's own words, which question a merchant asks it: a"
              + " read changes no object of the run it is asked in, so it is worth pressing only"
              + " for what it tells you, and a tester who cannot see that is pressing a button"
              + " for no reason");
    }
    if (whatAnEmptyAnswerWouldMean.inTheProvidersOwnWords().isBlank()) {
      throw new IllegalArgumentException(
          switch (whatAnEmptyAnswerWouldMean) {
            case EmptyAnswer.ItMeansThis _ ->
                "read "
                    + name
                    + " must say what it means when it answers nothing at all, because that is"
                    + " the answer a merchant asks this read for most often and an empty screen"
                    + " reads as a read that failed rather than as a provider that holds no such"
                    + " object";
            case EmptyAnswer.ItNeverAnswersNothing _ ->
                "read "
                    + name
                    + " says it never answers nothing at all, and must say what it answers in"
                    + " place of nothing, because a tester told only that an empty answer cannot"
                    + " happen is told nothing about what does happen when the object they asked"
                    + " about is not there";
          });
    }
    if (fields.isEmpty()) {
      throw new IllegalArgumentException(
          "read "
              + name
              + " asks a tester for nothing, so every tester would ask it the same question and"
              + " the answer belongs on a screen that already lists what this run holds rather"
              + " than behind a form nobody fills in");
    }
    Map<String, AFieldATesterFillsIn> byKey = new LinkedHashMap<>();
    for (AFieldATesterFillsIn field : fields) {
      if (byKey.put(field.key(), field) != null) {
        throw new IllegalArgumentException(
            "field "
                + field.key()
                + " is declared twice by the read "
                + name
                + ", so a form would draw two inputs writing one value");
      }
      if (fieldsItFillsInItself.containsKey(field.key())) {
        throw new IllegalArgumentException(
            "the read "
                + name
                + " asks a tester to type "
                + field.key()
                + " and at the same time fills it in itself, and a form reading both would ask a"
                + " tester for a value that is overwritten before the provider ever sees it, or"
                + " put a credential of the account on a screen");
      }
      if (field.namesTheCallbackTarget()) {
        throw new IllegalArgumentException(
            "field "
                + field.key()
                + " of the read "
                + name
                + " offers the callback targets this test run configures, and a read posts no"
                + " callback anywhere: it answers the caller and nothing else, so a tester"
                + " picking a target here would be choosing where an answer that never leaves the"
                + " response is delivered");
      }
    }
    for (Map.Entry<String, String> supplied : fieldsItFillsInItself.entrySet()) {
      if (supplied.getValue().isBlank()) {
        throw new IllegalArgumentException(
            "the read "
                + name
                + " fills "
                + supplied.getKey()
                + " in itself and must say, in this provider's own words, where the value comes"
                + " from: a blank sentence reads as a value this simulator failed to record"
                + " rather than as one no tester is ever asked for");
      }
    }
    fields = List.copyOf(fields);
    fieldsItFillsInItself = unmodifiableSequencedMap(new LinkedHashMap<>(fieldsItFillsInItself));
  }

  public String whatItMeansThatThisOneAnsweredNothing() {
    return switch (whatAnEmptyAnswerWouldMean) {
      case EmptyAnswer.ItMeansThis itMeans -> itMeans.inTheProvidersOwnWords();
      case EmptyAnswer.ItNeverAnswersNothing never ->
          throw new IllegalStateException(
              "read "
                  + name
                  + " declares that it never answers nothing at all, and its module answered no"
                  + " row: a screen has no true sentence to print under an answer this read"
                  + " cannot give, because the provider said it answers or refuses and this"
                  + " simulator did neither. What this read answers instead of nothing is "
                  + never.inTheProvidersOwnWords());
    };
  }

  public List<String> keys() {
    List<String> keys = new ArrayList<>();
    for (AFieldATesterFillsIn field : fields) {
      keys.add(field.key());
    }
    return List.copyOf(keys);
  }

  public Optional<AFieldATesterFillsIn> field(String key) {
    for (AFieldATesterFillsIn declared : fields) {
      if (declared.key().equals(key)) {
        return Optional.of(declared);
      }
    }
    return Optional.empty();
  }
}
