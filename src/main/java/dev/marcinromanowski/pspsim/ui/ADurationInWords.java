package dev.marcinromanowski.pspsim.ui;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record ADurationInWords(long amount, ADurationInWords.Unit unit) {

  public enum Unit {
    SECONDS(1, "seconds", "second"),
    MINUTES(60, "minutes", "minute"),
    HOURS(60 * 60, "hours", "hour"),
    DAYS(24 * 60 * 60, "days", "day");

    private final long secondsEach;

    private final String many;

    private final String one;

    Unit(long secondsEach, String many, String one) {
      this.secondsEach = secondsEach;
      this.many = many;
      this.one = one;
    }

    public long secondsEach() {
      return secondsEach;
    }

    public String inThePlural() {
      return many;
    }

    public String inTheSingular() {
      return one;
    }
  }

  public record Offered(String value, String whatItMeans, boolean chosen) {}

  public static final Unit THE_UNIT_A_FORM_FALLS_BACK_ON = Unit.DAYS;

  private static final List<Unit> LARGEST_FIRST = List.of(Unit.DAYS, Unit.HOURS, Unit.MINUTES);

  public static List<Unit> theUnitsATesterPicksFrom() {
    return List.of(Unit.values());
  }

  public static List<Offered> theUnitsOffered(Unit counted) {
    List<Offered> offered = new ArrayList<>();
    for (Unit unit : theUnitsATesterPicksFrom()) {
      offered.add(new Offered(unit.name(), unit.inThePlural(), unit == counted));
    }
    return List.copyOf(offered);
  }

  public static Optional<Unit> theUnitNamed(@Nullable String name) {
    for (Unit unit : Unit.values()) {
      if (unit.name().equals(name)) {
        return Optional.of(unit);
      }
    }
    return Optional.empty();
  }

  public static Optional<ADurationInWords> of(Duration span) {
    if (span.isNegative() || span.getNano() != 0) {
      return Optional.empty();
    }
    long seconds = span.getSeconds();
    Unit unit = theLargestUnitThatDividesIt(seconds);
    return Optional.of(new ADurationInWords(seconds / unit.secondsEach(), unit));
  }

  public static Optional<ADurationInWords> read(@Nullable String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return of(Duration.parse(value.strip()));
    } catch (DateTimeParseException noSpanAnythingCanRead) {
      return Optional.empty();
    }
  }

  public static @Nullable String theIsoFormOf(@Nullable String amount, @Nullable String unit) {
    if (amount == null) {
      return null;
    }
    if (amount.isBlank()) {
      return "";
    }
    String counted = amount.strip();
    Optional<Unit> stated = theUnitNamed(unit);
    if (stated.isEmpty()) {
      return counted;
    }
    long howMany;
    try {
      howMany = Long.parseLong(counted);
    } catch (NumberFormatException noNumberATesterCouldHaveTyped) {
      return counted;
    }
    try {
      return new ADurationInWords(howMany, stated.get()).theIsoForm();
    } catch (ArithmeticException longerThanAnySpanJavaHolds) {
      return counted;
    }
  }

  public String theIsoForm() {
    return Duration.ofSeconds(Math.multiplyExact(amount, unit.secondsEach())).toString();
  }

  public String inWords() {
    return amount + " " + (amount == 1 ? unit.inTheSingular() : unit.inThePlural());
  }

  private static Unit theLargestUnitThatDividesIt(long seconds) {
    for (Unit unit : LARGEST_FIRST) {
      if (seconds != 0 && seconds % unit.secondsEach() == 0) {
        return unit;
      }
    }
    return Unit.SECONDS;
  }
}
