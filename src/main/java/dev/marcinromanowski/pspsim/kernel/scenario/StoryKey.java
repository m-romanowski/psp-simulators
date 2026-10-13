package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record StoryKey(StoryKeying keyedOn, String value) {

  public static final int LONGEST_KEY = 512;

  public static final char NAMES_THE_SPACE = ':';

  private static final Pattern A_NAMED_SPACE = Pattern.compile("[a-z][a-z0-9-]*" + NAMES_THE_SPACE);

  private static final String OF_A_TEST_RUN = "test-run";

  private static final int NO_TEXT_COLUMN_HOLDS_THIS = 0;

  public StoryKey {
    rejectWhatCannotBeStored(value);
  }

  public static StoryKey ofTestRun(TestRunId testRunId) {
    return new StoryKey(StoryKeying.TEST_RUN, inSpace(OF_A_TEST_RUN, testRunId.value()));
  }

  public static String inSpace(String space, String identifier) {
    return space + NAMES_THE_SPACE + identifier;
  }

  public static void rejectWhatNoStoryKeyCanCarry(String property, String longestKeyItSpells) {
    try {
      rejectWhatCannotBeStored(longestKeyItSpells);
    } catch (IllegalArgumentException refused) {
      throw new IllegalArgumentException(
          property
              + " is spelled into the story key of every flow the account it configures reaches,"
              + " and the longest such key this value spells is one the simulator cannot store, so"
              + " a request of that account would raise where the key is built rather than be"
              + " answered. "
              + refused.getMessage(),
          refused);
    }
  }

  static void rejectWhatCannotBeStored(String key) {
    if (key.isBlank()) {
      throw new IllegalArgumentException(
          "storyKey must be text the simulator can store, and a key of nothing but whitespace is"
              + " not: the story table refuses one");
    }
    if (key.codePointCount(0, key.length()) > LONGEST_KEY) {
      throw new IllegalArgumentException(
          "storyKey must be text the simulator can store, and a key longer than "
              + LONGEST_KEY
              + " characters is not: the story key is part of the unique index that keeps one"
              + " story of one flow apart from another, and that index refuses an entry this"
              + " large");
    }
    for (int one : key.codePoints().toArray()) {
      if (one == NO_TEXT_COLUMN_HOLDS_THIS) {
        throw new IllegalArgumentException(
            "storyKey must be text the simulator can store, and a key holding a NUL character"
                + " (U+0000) is not: a story key is kept in a text column, which cannot hold one."
                + " The provider that resolved this key must spell it, separators included,"
                + " without a NUL");
      }
      if (one >= Character.MIN_SURROGATE && one <= Character.MAX_SURROGATE) {
        throw new IllegalArgumentException(
            "storyKey must be text the simulator can store, and a key holding an unpaired"
                + " surrogate is not: it has no UTF-8 encoding, so it can never reach the story"
                + " table. The provider that resolved this key must spell it as whole characters");
      }
    }
    rejectWhatNamesNoSpace(key);
  }

  private static void rejectWhatNamesNoSpace(String key) {
    Matcher space = A_NAMED_SPACE.matcher(key);
    if (!space.lookingAt()) {
      throw new IllegalArgumentException(
          "storyKey must open with the name of the identifier space it was drawn from, followed"
              + " by '"
              + NAMES_THE_SPACE
              + "', and this key opens with no such name: two identifier spaces of one provider"
              + " can draw the same string, and a story two flows reach by that coincidence is"
              + " one they were never meant to share. A space is named in lower-case letters,"
              + " digits and hyphens and begins with a letter, so a count or an identifier"
              + " standing where the name belongs is refused rather than read as a space");
    }
    if (key.substring(space.end()).isBlank()) {
      throw new IllegalArgumentException(
          "storyKey must name a flow within the identifier space it opens with, and this key names"
              + " the space and nothing in it: every flow of that space would resolve to the one"
              + " story instance");
    }
  }
}
