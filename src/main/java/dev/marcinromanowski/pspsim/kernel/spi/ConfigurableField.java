package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record ConfigurableField(
    String key,
    String label,
    String whatItIsFor,
    boolean required,
    boolean secret,
    List<AcceptedValue> acceptedValues,
    @Nullable String example,
    FieldShape shape,
    List<String> theLinesABlockMayEndWith) {

  public static final int LONGEST_LABEL_A_TESTER_SCANS = 32;

  private static final String SENTENCE_PUNCTUATION = ".,;:";

  public ConfigurableField(
      String key, String label, String whatItIsFor, boolean required, boolean secret) {
    this(
        key,
        label,
        whatItIsFor,
        required,
        secret,
        List.of(),
        null,
        FieldShape.FREE_TEXT,
        List.of());
  }

  public ConfigurableField(
      String key,
      String label,
      String whatItIsFor,
      boolean required,
      boolean secret,
      List<AcceptedValue> acceptedValues) {
    this(
        key,
        label,
        whatItIsFor,
        required,
        secret,
        acceptedValues,
        null,
        FieldShape.FREE_TEXT,
        List.of());
  }

  public ConfigurableField(
      String key,
      String label,
      String whatItIsFor,
      boolean required,
      boolean secret,
      List<AcceptedValue> acceptedValues,
      @Nullable String example) {
    this(
        key,
        label,
        whatItIsFor,
        required,
        secret,
        acceptedValues,
        example,
        FieldShape.FREE_TEXT,
        List.of());
  }

  public static ConfigurableField aDuration(
      String key, String label, String whatItIsFor, boolean required, String example) {
    return new ConfigurableField(
        key,
        label,
        whatItIsFor,
        required,
        false,
        List.of(),
        example,
        FieldShape.A_DURATION,
        List.of());
  }

  public static ConfigurableField aBlockOfManyLines(
      String key,
      String label,
      String whatItIsFor,
      boolean required,
      boolean secret,
      String theLineTheBlockBeginsWith,
      List<String> theLinesItMayEndWith) {
    return new ConfigurableField(
        key,
        label,
        whatItIsFor,
        required,
        secret,
        List.of(),
        theLineTheBlockBeginsWith,
        FieldShape.A_BLOCK_OF_MANY_LINES,
        theLinesItMayEndWith);
  }

  public static final String A_BLOCK_NAMES_THE_LINES_IT_MAY_END_WITH =
      " holds a block of many lines and names no line it may end with. A box that lets a value"
          + " run on over the lines under it has to know where that value stops, or the next line"
          + " a tester mistyped is welded onto the one above it and nothing is refused";

  public ConfigurableField {
    theLinesABlockMayEndWith = List.copyOf(theLinesABlockMayEndWith);
    if (key.isBlank()) {
      throw new IllegalArgumentException(
          "a configurable field with no key names nothing the provider reads, so a form could"
              + " never send its answer anywhere");
    }
    if (label.isBlank()) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " must carry a label, because the form a tester fills in prints the label and"
              + " never the key");
    }
    if (label.codePointCount(0, label.length()) > LONGEST_LABEL_A_TESTER_SCANS) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " carries a label of "
              + label.codePointCount(0, label.length())
              + " characters, and a label is at most "
              + LONGEST_LABEL_A_TESTER_SCANS
              + ". The bound is a length chosen to keep a label short enough to scan in a list of"
              + " eleven, and not a width: what a column of a form holds on one line depends on"
              + " the glyphs and on the badges beside them, which no count of characters knows."
              + " Put the rest in whatItIsFor, which the form shows beside the field for whoever"
              + " asks");
    }
    for (int i = 0; i < label.length(); i++) {
      if (SENTENCE_PUNCTUATION.indexOf(label.charAt(i)) >= 0) {
        throw new IllegalArgumentException(
            "configurable field "
                + key
                + " carries "
                + label.charAt(i)
                + " in its label, which makes the label a sentence rather than a name. A form"
                + " prints the label in a list beside ten others: put the sentence in whatItIsFor");
      }
    }
    if (whatItIsFor.isBlank()) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " must say, in this provider's own words, what the provider does with the value:"
              + " a form leads a tester who has read no provider contract, and a field with"
              + " nothing but a name beside it leaves that tester guessing");
    }
    if (whatItIsFor.strip().equals(label.strip())) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " repeats its label as its explanation, so a tester who opens the explanation"
              + " reads the name they had already read and learns nothing");
    }
    if (secret && !acceptedValues.isEmpty()) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " is a secret and declares the values it accepts, so a form drawn from it would"
              + " print a credential of this account on a screen as something to pick from. A"
              + " secret is free text by nature: the account holds whichever one the provider"
              + " issued it");
    }
    if (shape == FieldShape.A_DURATION && !acceptedValues.isEmpty()) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " holds a duration and declares the values it accepts, and a duration is not a"
              + " closed set: a form states one as a number and a unit, so the list would offer a"
              + " tester entries the control it draws has nowhere to put");
    }
    if (shape == FieldShape.A_BLOCK_OF_MANY_LINES && theLinesABlockMayEndWith.isEmpty()) {
      throw new IllegalArgumentException(
          "configurable field " + key + A_BLOCK_NAMES_THE_LINES_IT_MAY_END_WITH);
    }
    if (shape != FieldShape.A_BLOCK_OF_MANY_LINES && !theLinesABlockMayEndWith.isEmpty()) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " names a line a block of many lines may end with and is drawn as a box of one"
              + " line, so nothing would ever read that line");
    }
    for (String ending : theLinesABlockMayEndWith) {
      if (ending.isBlank() || ending.lines().count() != 1 || ending.equals(example)) {
        throw new IllegalArgumentException(
            "configurable field "
                + key
                + " may end with "
                + ending
                + ", which is blank, more than one line, or the very line it begins with, and a"
                + " box reading a value to its end needs one line it can recognise as that end");
      }
    }
    if (shape == FieldShape.A_BLOCK_OF_MANY_LINES && !acceptedValues.isEmpty()) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " holds a block of many lines and declares the values it accepts, and a block of"
              + " many lines is not a closed set: a form draws it as a box a tester pastes a"
              + " whole block into, so the list would offer entries that control has nowhere to"
              + " put");
    }
    if (shape == FieldShape.A_BLOCK_OF_MANY_LINES
        && example != null
        && example.lines().count() > 1) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " holds a block of many lines and hints more than one line of it. What a form"
              + " prints into a box as its hint is one line, and a whole block printed there"
              + " reads as a value the field was left holding: hint the line the block begins"
              + " with and put the rest of the shape in whatItIsFor");
    }
    if (shape == FieldShape.A_DURATION && secret) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " holds a duration and is a secret, and the two cannot both be true of one field:"
              + " a secret is whichever credential the provider issued this account, which is"
              + " never a span of time, and a form that drew a duration beside a secret badge"
              + " would promise to keep back a value it prints on the screen");
    }
    Set<String> values = new LinkedHashSet<>();
    Set<String> sentences = new LinkedHashSet<>();
    for (AcceptedValue accepted : acceptedValues) {
      if (!values.add(accepted.value())) {
        throw new IllegalArgumentException(
            "configurable field "
                + key
                + " accepts the value "
                + accepted.value()
                + " twice, so a form would offer one value under two sentences and a tester could"
                + " not tell which of them they had picked");
      }
      if (!sentences.add(accepted.whatItMeans())) {
        throw new IllegalArgumentException(
            "configurable field "
                + key
                + " says the same thing about two of the values it accepts, so a form would offer"
                + " a tester two entries they read as one and the pick between them would be a"
                + " coin flip");
      }
    }
    if (example != null && example.isBlank()) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " carries a blank example, which a form prints into the box as a hint and a"
              + " tester reads as a value the field was left holding. Leave it out, or write the"
              + " shape the provider accepts");
    }
    if (example != null && !acceptedValues.isEmpty()) {
      throw new IllegalArgumentException(
          "configurable field "
              + key
              + " declares the values it accepts and an example beside them, and a form that"
              + " offers a list prints no hint into a box it never draws, so the example would"
              + " reach no tester");
    }
    acceptedValues = List.copyOf(acceptedValues);
  }

  public boolean holdsADuration() {
    return shape == FieldShape.A_DURATION;
  }

  public boolean holdsABlockOfManyLines() {
    return shape == FieldShape.A_BLOCK_OF_MANY_LINES;
  }

  public Optional<AcceptedValue> accepted(String value) {
    for (AcceptedValue offered : acceptedValues) {
      if (offered.value().equals(value)) {
        return Optional.of(offered);
      }
    }
    return Optional.empty();
  }
}
