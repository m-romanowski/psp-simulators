package com.example.tester.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ACardNumber {

  private static final Pattern DIGITS_IN_GROUPS =
      Pattern.compile("(?<![0-9A-Za-z])[0-9]+(?:[ -][0-9]+)*(?![0-9A-Za-z])");

  private static final Pattern A_GROUP = Pattern.compile("[0-9]+");

  private static final String A_SEPARATOR = "[ -]";

  private static final String A_MASK = "[*xX#.\\u2022]{1,9}";

  private static final int SHORTEST = 13;

  private static final int LONGEST = 19;

  private ACardNumber() {}

  public static List<String> leakedIn(
      String where, String text, Collection<String> numbersThePayerHolds) {
    List<String> leaks = new ArrayList<>();
    for (String number : wholeNumbersIn(text)) {
      if (numbersThePayerHolds.contains(number) || looksIssued(number)) {
        leaks.add(where + " carries a whole card number ending " + lastFourOf(number));
      }
    }
    String unseparated = text.replaceAll(A_SEPARATOR, "");
    for (String number : numbersThePayerHolds) {
      Pattern firstSixBesideLastFour =
          Pattern.compile(
              Pattern.quote(number.substring(0, 6)) + A_MASK + Pattern.quote(lastFourOf(number)));
      if (firstSixBesideLastFour.matcher(unseparated).find()) {
        leaks.add(
            where
                + " carries the first six digits of the card ending "
                + lastFourOf(number)
                + " beside its last four");
      }
    }
    return leaks;
  }

  static List<String> wholeNumbersIn(String text) {
    List<String> numbers = new ArrayList<>();
    Matcher run = DIGITS_IN_GROUPS.matcher(text);
    while (run.find()) {
      List<String> groups = new ArrayList<>();
      Matcher group = A_GROUP.matcher(run.group());
      while (group.find()) {
        groups.add(group.group());
      }
      for (int first = 0; first < groups.size(); first++) {
        StringBuilder joined = new StringBuilder();
        for (int last = first; last < groups.size() && joined.length() < LONGEST; last++) {
          joined.append(groups.get(last));
          if (joined.length() >= SHORTEST && joined.length() <= LONGEST) {
            numbers.add(joined.toString());
          }
        }
      }
    }
    return numbers;
  }

  static boolean looksIssued(String number) {
    return anIssuerPrefix(number) && passesLuhn(number);
  }

  static boolean passesLuhn(String number) {
    int sum = 0;
    boolean doubled = false;
    for (int at = number.length() - 1; at >= 0; at--) {
      int digit = number.charAt(at) - '0';
      if (doubled) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubled = !doubled;
    }
    return sum % 10 == 0;
  }

  private static boolean anIssuerPrefix(String number) {
    int two = Integer.parseInt(number.substring(0, 2));
    int three = Integer.parseInt(number.substring(0, 3));
    int four = Integer.parseInt(number.substring(0, 4));
    return number.charAt(0) == '4'
        || (two >= 51 && two <= 55)
        || (four >= 2221 && four <= 2720)
        || two == 34
        || two == 37
        || two == 35
        || two == 36
        || two == 38
        || two == 39
        || (three >= 300 && three <= 305)
        || four == 6011
        || two == 62
        || two == 65
        || (three >= 644 && three <= 649);
  }

  private static String lastFourOf(String number) {
    return number.substring(number.length() - 4);
  }
}
