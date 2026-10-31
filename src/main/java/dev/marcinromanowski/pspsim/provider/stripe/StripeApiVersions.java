package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class StripeApiVersions {

  static final List<String> RELEASED_BEFORE_THE_FIRST_MAJOR =
      List.of(
          "2011-06-21",
          "2011-06-28",
          "2011-08-01",
          "2011-09-15",
          "2012-02-23",
          "2012-03-25",
          "2012-06-18",
          "2012-07-09",
          "2012-09-24",
          "2012-10-26",
          "2012-11-07",
          "2013-02-11",
          "2013-02-13",
          "2013-07-05",
          "2013-08-12",
          "2013-08-13",
          "2013-10-29",
          "2013-12-03",
          "2014-01-31",
          "2014-03-13",
          "2014-03-28",
          "2014-05-19",
          "2014-06-13",
          "2014-06-17",
          "2014-07-22",
          "2014-07-26",
          "2014-08-04",
          "2014-08-20",
          "2014-09-08",
          "2014-10-07",
          "2014-11-05",
          "2014-11-20",
          "2014-12-08",
          "2014-12-17",
          "2014-12-22",
          "2015-01-11",
          "2015-01-26",
          "2015-02-10",
          "2015-02-16",
          "2015-02-18",
          "2015-03-24",
          "2015-04-07",
          "2015-06-15",
          "2015-07-07",
          "2015-07-13",
          "2015-07-28",
          "2015-08-07",
          "2015-08-19",
          "2015-09-03",
          "2015-09-08",
          "2015-09-23",
          "2015-10-01",
          "2015-10-12",
          "2015-10-16",
          "2016-02-03",
          "2016-02-19",
          "2016-02-22",
          "2016-02-23",
          "2016-02-29",
          "2016-03-07",
          "2016-06-15",
          "2016-07-06",
          "2016-10-19",
          "2017-01-27",
          "2017-02-14",
          "2017-04-06",
          "2017-05-25",
          "2017-06-05",
          "2017-08-15",
          "2017-12-14",
          "2018-01-23",
          "2018-02-05",
          "2018-02-06",
          "2018-02-28",
          "2018-05-21",
          "2018-07-27",
          "2018-08-23",
          "2018-09-06",
          "2018-09-24",
          "2018-10-31",
          "2018-11-08",
          "2019-02-11",
          "2019-02-19",
          "2019-03-14",
          "2019-05-16",
          "2019-08-14",
          "2019-09-09",
          "2019-10-08",
          "2019-10-17",
          "2019-11-05",
          "2019-12-03",
          "2020-03-02",
          "2020-08-27",
          "2022-08-01",
          "2022-11-15",
          "2023-08-16",
          "2023-10-16",
          "2024-04-10",
          "2024-06-20");

  static final String PREVIEW_CODENAME = "preview";

  static final List<String> PREVIEW_RELEASE_DATES =
      List.of(
          "2025-04-30",
          "2025-05-28",
          "2025-06-30",
          "2025-07-30",
          "2025-08-27",
          "2025-09-30",
          "2025-10-29",
          "2025-11-17",
          "2025-12-15",
          "2026-01-28",
          "2026-02-25",
          "2026-03-25",
          "2026-04-22",
          "2026-05-27",
          "2026-06-24",
          "2026-07-29",
          "2026-08-26",
          "2026-09-30",
          "2026-10-28");

  static final StripeApiVersion PINNED_BY_THE_ANDROID_SDK = new StripeApiVersion("2020-03-02");

  static final StripeApiVersion PINNED_BY_THE_IOS_SDK = new StripeApiVersion("2020-08-27");

  static final List<StripeApiVersion> PINNED_BY_THE_MOBILE_SDKS =
      List.of(PINNED_BY_THE_ANDROID_SDK, PINNED_BY_THE_IOS_SDK);

  static final String BETA_SEPARATOR = ";";

  static final Pattern A_BETA_AND_WHAT_FOLLOWS =
      Pattern.compile(BETA_SEPARATOR + ".*", Pattern.DOTALL);

  private static final Pattern A_DATED_RELEASE =
      Pattern.compile("([0-9]{4}-[0-9]{2}-[0-9]{2})\\.[a-z]+");

  private StripeApiVersions() {}

  static List<StripeApiVersion> answered() {
    List<StripeApiVersion> answered = new ArrayList<>();
    for (StripeApiMajor major : StripeApiMajor.values()) {
      if (major.answeredHere()) {
        answered.addAll(major.releases());
      }
    }
    return List.copyOf(answered);
  }

  static List<StripeApiMajor> majorsAnswered() {
    List<StripeApiMajor> majors = new ArrayList<>();
    for (StripeApiMajor major : StripeApiMajor.values()) {
      if (major.answeredHere()) {
        majors.add(major);
      }
    }
    return List.copyOf(majors);
  }

  static StripeApiVersion oldestAnswered() {
    return answered().getFirst();
  }

  static StripeApiVersion newest() {
    return answered().getLast();
  }

  static String theRangeAnswered() {
    return oldestAnswered().wireValue() + " through " + newest().wireValue();
  }

  static Optional<StripeApiVersion> answeredNamed(String wireValue) {
    for (StripeApiVersion version : answered()) {
      if (version.wireValue().equals(wireValue)) {
        return Optional.of(version);
      }
    }
    return Optional.empty();
  }

  static boolean isLaterThanTheCatalogue(String wireValue) {
    Matcher release = A_DATED_RELEASE.matcher(wireValue);
    if (!release.matches()) {
      return false;
    }
    try {
      return LocalDate.parse(release.group(1)).isAfter(LocalDate.parse(theDateOf(newest())));
    } catch (DateTimeParseException notADay) {
      return false;
    }
  }

  private static String theDateOf(StripeApiVersion version) {
    String wireValue = version.wireValue();
    return wireValue.substring(0, wireValue.indexOf(StripeApiVersion.CODENAME_SEPARATOR));
  }

  static boolean isPublished(String wireValue) {
    if (RELEASED_BEFORE_THE_FIRST_MAJOR.contains(wireValue)) {
      return true;
    }
    for (String date : PREVIEW_RELEASE_DATES) {
      if (wireValue.equals(date + StripeApiVersion.CODENAME_SEPARATOR + PREVIEW_CODENAME)) {
        return true;
      }
    }
    for (StripeApiMajor major : StripeApiMajor.values()) {
      if (major.releases().contains(new StripeApiVersion(wireValue))) {
        return true;
      }
    }
    return false;
  }

  static List<AcceptedValue> acceptedValues() {
    List<AcceptedValue> offered = new ArrayList<>();
    for (StripeApiMajor major : majorsAnswered()) {
      for (StripeApiVersion release : major.releases()) {
        offered.add(new AcceptedValue(release.wireValue(), whatAnsweringAt(major, release)));
      }
    }
    return List.copyOf(offered);
  }

  private static String whatAnsweringAt(StripeApiMajor major, StripeApiVersion release) {
    String answeredAt =
        "a request sending no Stripe-Version, and every event, is answered at "
            + release.wireValue();
    if (release.equals(newest())) {
      return answeredAt + ", the newest release, which an account leaving this empty is given";
    }
    if (release.equals(major.opening())) {
      return answeredAt + ", the release that opened " + major.codename() + " and its breaks";
    }
    return answeredAt
        + ", a monthly "
        + major.codename()
        + " release that adds to "
        + major.opening().wireValue()
        + " and breaks nothing";
  }
}
