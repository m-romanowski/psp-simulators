package dev.marcinromanowski.pspsim.provider.autra;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

record AutraPaymentWindow(Instant from, Instant to) {

  static final int LONGEST_WINDOW_IN_DAYS = 31;

  static final int THE_WINDOW_A_CALL_THAT_NAMES_NONE_GETS = 7;

  private static final Pattern A_BARE_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

  private static final int ONE_SECOND = 1;

  static final String A_MOMENT_IS_RFC3339_OR_A_BARE_DATE =
      "provider autra reads this as \"RFC3339 ou YYYY-MM-DD (UTC)\", so send either a whole"
          + " moment such as 2026-07-27T22:18:42Z or a bare date such as 2026-07-27, which covers"
          + " the whole of that day";

  static final String THE_WINDOW_RUNS_FORWARDS =
      "from names a moment after to, so the window runs backwards and holds nothing. Provider"
          + " autra publishes INVALID_RANGE without saying which range it refuses, and this"
          + " simulator refuses a from after a to and admits a from equal to it, which is an empty"
          + " window rather than an impossible one";

  static final String THE_WINDOW_IS_AT_MOST_A_MONTH =
      "provider autra publishes the date window as \"Janela padrao: ultimos 7 dias; maxima: 31"
          + " dias\" and answers RANGE_TOO_LARGE \"(> 31 dias)\", so the maximum is the vendor's"
          + " own and not this simulator's";

  sealed interface Asked {

    record Between(AutraPaymentWindow window) implements Asked {}

    record Refused(String code, String msg) implements Asked {}
  }

  static Instant theEndACallThatNamesNoneGets(Instant now) {
    return now.plusSeconds(ONE_SECOND);
  }

  static Asked of(AutraApiCall call, Instant now) {
    Optional<String> askedTo = call.queryParameter(AutraFields.TO);
    Optional<Instant> to =
        askedTo.isEmpty()
            ? Optional.of(theEndACallThatNamesNoneGets(now))
            : theEndOf(askedTo.get());
    if (to.isEmpty()) {
      return new Asked.Refused(AutraErrors.INVALID_TO, A_MOMENT_IS_RFC3339_OR_A_BARE_DATE);
    }
    Optional<String> askedFrom = call.queryParameter(AutraFields.FROM);
    Optional<Instant> from =
        askedFrom.isEmpty()
            ? Optional.of(to.get().minus(Duration.ofDays(THE_WINDOW_A_CALL_THAT_NAMES_NONE_GETS)))
            : theStartOf(askedFrom.get());
    if (from.isEmpty()) {
      return new Asked.Refused(AutraErrors.INVALID_FROM, A_MOMENT_IS_RFC3339_OR_A_BARE_DATE);
    }
    if (from.get().isAfter(to.get())) {
      return new Asked.Refused(AutraErrors.INVALID_RANGE, THE_WINDOW_RUNS_FORWARDS);
    }
    if (Duration.between(from.get(), to.get()).compareTo(Duration.ofDays(LONGEST_WINDOW_IN_DAYS))
        > 0) {
      return new Asked.Refused(AutraErrors.RANGE_TOO_LARGE, THE_WINDOW_IS_AT_MOST_A_MONTH);
    }
    return new Asked.Between(new AutraPaymentWindow(from.get(), to.get()));
  }

  private static Optional<Instant> theStartOf(String asked) {
    return A_BARE_DATE.matcher(asked).matches()
        ? aDay(asked).map(day -> day.atStartOfDay(ZoneOffset.UTC).toInstant())
        : aMoment(asked);
  }

  private static Optional<Instant> theEndOf(String asked) {
    return A_BARE_DATE.matcher(asked).matches()
        ? aDay(asked).map(day -> day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
        : aMoment(asked);
  }

  private static Optional<LocalDate> aDay(String asked) {
    try {
      return Optional.of(LocalDate.parse(asked));
    } catch (DateTimeParseException unreadable) {
      return Optional.empty();
    }
  }

  private static Optional<Instant> aMoment(String asked) {
    try {
      return Optional.of(Instant.parse(asked));
    } catch (DateTimeParseException unreadable) {
      return Optional.empty();
    }
  }

  boolean holds(Instant at) {
    return !at.isBefore(from) && at.isBefore(to);
  }

  String fromAsAutraWritesIt() {
    return CreatePixCheckout.asAutraWritesIt(from);
  }

  String toAsAutraWritesIt() {
    return CreatePixCheckout.asAutraWritesIt(to);
  }
}
