package dev.marcinromanowski.pspsim.kernel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class AShapeSaidInWords {

  private static final int NO_LONGEST_IT_STATES = -1;

  static final int AS_FAR_AS_THE_COUNTS_A_SHAPE_WRITES_MAY_MULTIPLY = 512;

  private static final long PAST_EVERY_COUNT_THIS_MEASUREMENT_READS =
      AS_FAR_AS_THE_COUNTS_A_SHAPE_WRITES_MAY_MULTIPLY + 1;

  static final String CHARACTERS_THESE_WORDS_HAVE_NO_NAME_FOR = "@/*é";

  private static final int AS_LONG_AS_EVERY_VALUE_IS_TRIED = 3;

  private final Pattern pattern;

  private final String aValueItAccepts;

  private final Set<ACharacterAValueIsMadeOf> itBeginsWith;

  private final Set<ACharacterAValueIsMadeOf> theRestIsMadeOf;

  private final int shortest;

  private final int longest;

  private AShapeSaidInWords(
      Pattern pattern,
      String aValueItAccepts,
      Set<ACharacterAValueIsMadeOf> itBeginsWith,
      Set<ACharacterAValueIsMadeOf> theRestIsMadeOf,
      int shortest,
      int longest) {
    this.pattern = pattern;
    this.aValueItAccepts = aValueItAccepts;
    this.itBeginsWith = itBeginsWith;
    this.theRestIsMadeOf = theRestIsMadeOf;
    this.shortest = shortest;
    this.longest = longest;
  }

  public static AShapeSaidInWords of(String shape, String aValueItAccepts) {
    Pattern pattern = Pattern.compile(shape);
    if (aValueItAccepts.isEmpty() || !pattern.matcher(aValueItAccepts).matches()) {
      throw new IllegalArgumentException(
          "the shape "
              + shape
              + " is said in words beside the value "
              + aValueItAccepts
              + ", which that shape does not accept, so the one value these words offer a tester"
              + " to copy would be refused the moment they typed it");
    }
    Set<ACharacterAValueIsMadeOf> begins =
        theKindsAdmittedAt(pattern, aValueItAccepts, 0, "begins with");
    Set<ACharacterAValueIsMadeOf> rest =
        theKindsAdmittedAt(
            pattern, aValueItAccepts, aValueItAccepts.length() - 1, "is otherwise made of");
    char first = begins.iterator().next().oneOfItsOwn();
    char otherwise = rest.iterator().next().oneOfItsOwn();
    long counted = theCountsItWritesMultipliedTogether(shape);
    if (counted > AS_FAR_AS_THE_COUNTS_A_SHAPE_WRITES_MAY_MULTIPLY) {
      throw theShapeIsNotOneTheseWordsCanSay(
          shape,
          "the counts it writes multiply to more than "
              + AS_FAR_AS_THE_COUNTS_A_SHAPE_WRITES_MAY_MULTIPLY
              + ", and the lengths this measurement walks reach past every count a shape writes,"
              + " so not every length it accepts would have been tried");
    }
    int pastEveryCountItWrites = (int) (counted * shape.length() + 1);
    int shortest = NO_LONGEST_IT_STATES;
    int longest = NO_LONGEST_IT_STATES;
    for (int length = 1; length <= pastEveryCountItWrites; length++) {
      if (pattern.matcher(aValueOfLength(length, first, otherwise)).matches()) {
        shortest = shortest == NO_LONGEST_IT_STATES ? length : shortest;
        longest = length;
      }
    }
    if (shortest == NO_LONGEST_IT_STATES) {
      throw theShapeIsNotOneTheseWordsCanSay(
          shape,
          "no value built from the characters it was measured to accept matches it at any length"
              + " up to "
              + pastEveryCountItWrites);
    }
    if (longest == pastEveryCountItWrites) {
      longest = NO_LONGEST_IT_STATES;
    }
    AShapeSaidInWords said =
        new AShapeSaidInWords(pattern, aValueItAccepts, begins, rest, shortest, longest);
    said.refuseWhatTheseWordsWouldMisstate(shape, first, otherwise, pastEveryCountItWrites);
    return said;
  }

  public boolean admits(String value) {
    return pattern.matcher(value).matches();
  }

  public String aValueItAccepts() {
    return aValueItAccepts;
  }

  public String saidInWords() {
    return theLengthAndCharactersInWords() + ", for example " + aValueItAccepts;
  }

  private String saidInWordsWith(String aClauseOfWhoeverPublishedIt) {
    if (aClauseOfWhoeverPublishedIt.isBlank()) {
      throw new IllegalArgumentException(
          "a clause composed into these words says what whoever published this shape calls it,"
              + " and a blank one says nothing, so it would leave a tester reading two commas"
              + " around no words at all");
    }
    return theLengthAndCharactersInWords()
        + ", "
        + aClauseOfWhoeverPublishedIt
        + ", for example "
        + aValueItAccepts;
  }

  public String theLengthAndCharactersInWords() {
    return theLengthInWords() + ", " + theCharactersInWords();
  }

  public String theRefusalOf(String whatTheValueIsCalled) {
    return theValueMustMatchThisShape(whatTheValueIsCalled) + saidInWords();
  }

  public String theRefusalOf(String whatTheValueIsCalled, String aClauseOfWhoeverPublishedIt) {
    return theValueMustMatchThisShape(whatTheValueIsCalled)
        + saidInWordsWith(aClauseOfWhoeverPublishedIt);
  }

  private String theValueMustMatchThisShape(String whatTheValueIsCalled) {
    return whatTheValueIsCalled + " must match " + pattern.pattern() + ", which is ";
  }

  private String theLengthInWords() {
    if (longest == NO_LONGEST_IT_STATES) {
      return shortest == 1 ? "one character or more" : "at least " + shortest + " characters";
    }
    if (shortest == longest) {
      return longest == 1 ? "one character" : "exactly " + longest + " characters";
    }
    if (shortest == 1) {
      return "at most " + longest + " characters";
    }
    return "between " + shortest + " and " + longest + " characters";
  }

  private String theCharactersInWords() {
    if (itBeginsWith.equals(theRestIsMadeOf)) {
      return "each " + ACharacterAValueIsMadeOf.theWordsOfEachOf(itBeginsWith);
    }
    return "the first "
        + ACharacterAValueIsMadeOf.theWordsOfEachOf(itBeginsWith)
        + " and the rest "
        + ACharacterAValueIsMadeOf.theWordsOfEachOf(theRestIsMadeOf);
  }

  private boolean theseWordsAdmit(String value) {
    if (value.length() < shortest) {
      return false;
    }
    if (longest != NO_LONGEST_IT_STATES && value.length() > longest) {
      return false;
    }
    if (!namedBy(itBeginsWith, value.charAt(0))) {
      return false;
    }
    for (int at = 1; at < value.length(); at++) {
      if (!namedBy(theRestIsMadeOf, value.charAt(at))) {
        return false;
      }
    }
    return true;
  }

  private void refuseWhatTheseWordsWouldMisstate(
      String shape, char first, char otherwise, int pastEveryCountItWrites) {
    for (String tried : everyValueTheseWordsAreTriedAgainst()) {
      if (pattern.matcher(tried).matches() != theseWordsAdmit(tried)) {
        throw theShapeIsNotOneTheseWordsCanSay(
            shape,
            "they and it disagree over the value \""
                + tried
                + "\", which the shape "
                + (pattern.matcher(tried).matches() ? "accepts" : "refuses")
                + " and the words "
                + (theseWordsAdmit(tried) ? "accept" : "refuse"));
      }
    }
    int asFarAsALengthIsStated = longest == NO_LONGEST_IT_STATES ? pastEveryCountItWrites : longest;
    for (int length = 1; length <= asFarAsALengthIsStated; length++) {
      String tried = aValueOfLength(length, first, otherwise);
      if (pattern.matcher(tried).matches() != theseWordsAdmit(tried)) {
        throw theShapeIsNotOneTheseWordsCanSay(
            shape,
            "they and it disagree over a value of "
                + length
                + " characters, so the lengths it accepts are not the one run these words state");
      }
    }
  }

  private static long theCountsItWritesMultipliedTogether(String shape) {
    long counted = 1;
    boolean insideACount = false;
    int at = 0;
    while (at < shape.length()) {
      char each = shape.charAt(at);
      if (each == '{') {
        insideACount = true;
        at++;
      } else if (each == '}') {
        insideACount = false;
        at++;
      } else if (insideACount && isOneOfTheTenDigits(each)) {
        int past = at;
        while (past < shape.length() && isOneOfTheTenDigits(shape.charAt(past))) {
          past++;
        }
        counted =
            Math.min(
                PAST_EVERY_COUNT_THIS_MEASUREMENT_READS,
                counted * theCountWrittenBetween(shape, at, past));
        at = past;
      } else {
        at++;
      }
    }
    return counted;
  }

  private static long theCountWrittenBetween(String shape, int at, int past) {
    long written = 0;
    for (int each = at; each < past; each++) {
      written =
          Math.min(
              PAST_EVERY_COUNT_THIS_MEASUREMENT_READS,
              written * 10 + theDigitWrittenAt(shape, each));
    }
    return Math.max(written, 1);
  }

  private static int theDigitWrittenAt(String shape, int at) {
    return ACharacterAValueIsMadeOf.A_DIGIT.everyOneOfItsOwn().indexOf(shape.charAt(at));
  }

  private static boolean isOneOfTheTenDigits(char each) {
    return ACharacterAValueIsMadeOf.A_DIGIT.everyOneOfItsOwn().indexOf(each) >= 0;
  }

  private static List<String> everyValueTheseWordsAreTriedAgainst() {
    List<String> alphabet = new ArrayList<>();
    for (ACharacterAValueIsMadeOf kind : ACharacterAValueIsMadeOf.values()) {
      alphabet.add(String.valueOf(kind.oneOfItsOwn()));
    }
    for (int at = 0; at < CHARACTERS_THESE_WORDS_HAVE_NO_NAME_FOR.length(); at++) {
      alphabet.add(String.valueOf(CHARACTERS_THESE_WORDS_HAVE_NO_NAME_FOR.charAt(at)));
    }
    List<String> tried = new ArrayList<>();
    tried.add("");
    List<String> grown = new ArrayList<>(tried);
    for (int length = 1; length <= AS_LONG_AS_EVERY_VALUE_IS_TRIED; length++) {
      List<String> longer = new ArrayList<>();
      for (String shorter : grown) {
        for (String character : alphabet) {
          longer.add(shorter + character);
        }
      }
      tried.addAll(longer);
      grown = longer;
    }
    return tried;
  }

  private static Set<ACharacterAValueIsMadeOf> theKindsAdmittedAt(
      Pattern pattern, String aValueItAccepts, int at, String whatThePositionSays) {
    Set<ACharacterAValueIsMadeOf> admitted = EnumSet.noneOf(ACharacterAValueIsMadeOf.class);
    for (ACharacterAValueIsMadeOf kind : ACharacterAValueIsMadeOf.values()) {
      int howManyOfItsOwnAreAdmitted = 0;
      for (char each : kind.everyOneOfItsOwn().toCharArray()) {
        if (pattern.matcher(theValueWith(aValueItAccepts, at, each)).matches()) {
          howManyOfItsOwnAreAdmitted++;
        }
      }
      if (howManyOfItsOwnAreAdmitted == kind.everyOneOfItsOwn().length()) {
        admitted.add(kind);
      } else if (howManyOfItsOwnAreAdmitted > 0) {
        throw theShapeIsNotOneTheseWordsCanSay(
            pattern.pattern(),
            "it accepts only some of what these words call "
                + kind.inWords()
                + " in the position a value "
                + whatThePositionSays
                + ", so naming that kind would offer a tester characters it turns away");
      }
    }
    if (admitted.isEmpty()) {
      throw theShapeIsNotOneTheseWordsCanSay(
          pattern.pattern(),
          "no character these words can name was measured in the position a value "
              + whatThePositionSays);
    }
    return admitted;
  }

  private static String theValueWith(String aValueItAccepts, int at, char each) {
    return aValueItAccepts.substring(0, at) + each + aValueItAccepts.substring(at + 1);
  }

  private static String aValueOfLength(int length, char first, char otherwise) {
    StringBuilder value = new StringBuilder().append(first);
    while (value.length() < length) {
      value.append(otherwise);
    }
    return value.toString();
  }

  private static boolean namedBy(Set<ACharacterAValueIsMadeOf> kinds, char character) {
    Optional<ACharacterAValueIsMadeOf> named = ACharacterAValueIsMadeOf.of(character);
    return named.isPresent() && kinds.contains(named.get());
  }

  private static IllegalArgumentException theShapeIsNotOneTheseWordsCanSay(
      String shape, String whatWasMeasured) {
    return new IllegalArgumentException(
        "the shape "
            + shape
            + " is not one this simulator can say in words: "
            + whatWasMeasured
            + ". Say it in the words of whoever published it instead, beside the pattern, the way"
            + " a provider says its own grammar");
  }
}
