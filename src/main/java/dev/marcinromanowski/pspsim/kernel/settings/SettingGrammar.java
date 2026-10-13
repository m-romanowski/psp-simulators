package dev.marcinromanowski.pspsim.kernel.settings;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class SettingGrammar {

  public static final int MOST_ATTEMPTS_A_RETRY_PLAN_MAY_HOLD = 20;

  private SettingGrammar() {}

  public static void rejectAValueTheSettingCannotCarry(
      SettingKey key, String property, String value) {
    switch (key) {
      case WEBHOOK_RETRY_PLAN -> delaysOf(property, value);
    }
  }

  public static List<Duration> delaysOf(String property, String value) {
    String[] tokens = value.split(",", -1);
    if (tokens.length > MOST_ATTEMPTS_A_RETRY_PLAN_MAY_HOLD) {
      throw new IllegalArgumentException(
          property
              + " names more attempts than one delivery may make, which is at most "
              + MOST_ATTEMPTS_A_RETRY_PLAN_MAY_HOLD);
    }
    List<Duration> delays = new ArrayList<>();
    for (int index = 0; index < tokens.length; index++) {
      delays.add(delayOf(property + "[" + index + "]", tokens[index].strip()));
    }
    if (!delays.get(0).isZero()) {
      throw new IllegalArgumentException(
          property
              + "[0] must be zero, because the first delivery attempt is made immediately and a"
              + " plan that holds it back leaves a delivery that is never due");
    }
    for (int index = 1; index < delays.size(); index++) {
      if (delays.get(index).compareTo(delays.get(index - 1)) < 0) {
        throw new IllegalArgumentException(
            property
                + "["
                + index
                + "] is shorter than the delay before it, and a retry plan that hurries as it goes"
                + " describes no provider this simulator impersonates");
      }
    }
    return List.copyOf(delays);
  }

  private static Duration delayOf(String property, String token) {
    if (token.isBlank()) {
      throw new IllegalArgumentException(property + " is required");
    }
    Duration delay;
    try {
      delay = Duration.parse(token);
    } catch (DateTimeParseException refused) {
      throw new IllegalArgumentException(property + " must be an ISO-8601 duration");
    }
    if (delay.isNegative()) {
      throw new IllegalArgumentException(property + " must not be a negative duration");
    }
    return delay;
  }
}
