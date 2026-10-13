package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WhereARefreshLandsTest {

  private static final String THE_PAYER_STEP = "/ui/objects/complete";

  private static final String A_PROVIDER = "grow";

  private static final String A_RUN = "tester-run";

  @Test
  void a_refresh_of_a_post_only_route_lands_on_the_screen_that_route_acts_for() {
    assertThat(WhereARefreshLands.theAddressARefreshIsSentTo(THE_PAYER_STEP, A_PROVIDER, A_RUN))
        .as(
            "a tester pressing F5 on the address a form posted to is not asking to see a status"
                + " code: they are asking for the screen they were working on, in the run they"
                + " were working in")
        .isEqualTo("/ui/objects?provider=grow&testRunId=tester-run");
    assertThat(
            WhereARefreshLands.theAddressARefreshIsSentTo(
                "/ui/objects/follow-up", A_PROVIDER, A_RUN))
        .isEqualTo("/ui/objects?provider=grow&testRunId=tester-run");
    assertThat(
            WhereARefreshLands.theAddressARefreshIsSentTo(
                "/ui/configuration/account/remove", A_PROVIDER, A_RUN))
        .isEqualTo("/ui/configuration?provider=grow&testRunId=tester-run");
    assertThat(
            WhereARefreshLands.theAddressARefreshIsSentTo(
                "/ui/dashboard/forget", A_PROVIDER, A_RUN))
        .isEqualTo("/ui/dashboard?provider=grow&testRunId=tester-run");
  }

  @Test
  void the_screen_behind_an_address_is_the_longest_screen_that_address_falls_under() {
    assertThat(WhereARefreshLands.theScreenBehind("/ui/scenarios/diff/copy"))
        .as(
            "the first screen that happens to prefix an address is not the screen that address"
                + " belongs to: /ui/scenarios also prefixes this one and would drop a tester a"
                + " screen short of where they were")
        .isEqualTo("/ui/scenarios/diff");
    assertThat(WhereARefreshLands.theScreenBehind("/ui/scenarios/write"))
        .isEqualTo("/ui/scenarios");
    assertThat(WhereARefreshLands.theScreenBehind("/ui/dashboard")).isEqualTo("/ui/dashboard");
    assertThat(WhereARefreshLands.theScreenBehind("/ui")).isEqualTo("/ui");
  }

  @Test
  void the_screens_are_read_longest_first_so_the_longest_match_is_the_first_one_found() {
    assertThat(WhereARefreshLands.theScreensLongestFirst())
        .isNotEmpty()
        .containsAll(WhereARefreshLands.theScreensLongestFirst())
        .isSortedAccordingTo((one, other) -> other.length() - one.length());
  }

  @Test
  void an_address_under_no_deeper_screen_lands_on_the_screen_a_run_is_read_on() {
    assertThat(WhereARefreshLands.theScreenBehind("/ui/x/autra/pix"))
        .isEqualTo(WhereARefreshLands.THE_SCREEN_A_RUN_IS_READ_ON);
    assertThat(WhereARefreshLands.theScreenBehind("/ui/nothing-of-ours"))
        .isEqualTo(WhereARefreshLands.THE_SCREEN_A_RUN_IS_READ_ON);
  }

  @Test
  void an_address_under_no_screen_at_all_is_refused_rather_than_sent_to_a_screen_it_never_asked() {
    assertThatCode(() -> WhereARefreshLands.theScreenBehind("/health"))
        .as(
            "the rail carries "
                + WhereARefreshLands.THE_SCREEN_A_RUN_IS_READ_ON
                + " as a screen of its own, and both callers ask only about an address under it,"
                + " so an address that falls under nothing is a caller that lost its guard")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("/health")
        .hasMessageContaining("falls under no screen this rail carries");
    assertThatThrownBy(() -> WhereARefreshLands.theScreenBehind("/uix"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_screen_whose_name_only_starts_like_another_is_not_the_screen_behind_it() {
    assertThat(WhereARefreshLands.fallsUnder("/ui/objects/new", "/ui/objects")).isTrue();
    assertThat(WhereARefreshLands.fallsUnder("/ui/objects", "/ui/objects")).isTrue();
    assertThat(WhereARefreshLands.fallsUnder("/ui/objectsmith", "/ui/objects"))
        .as(
            "a prefix match with no separator reads one screen's name as the start of another's,"
                + " and lands a tester on a screen that was never theirs")
        .isFalse();
  }

  @Test
  void an_address_naming_no_run_and_no_provider_lands_on_the_screen_and_asks_for_nothing() {
    assertThat(WhereARefreshLands.theAddressARefreshIsSentTo(THE_PAYER_STEP, null, null))
        .as(
            "provider= and testRunId= with nothing after them is the address the error page"
                + " offered, and the reason a tester landed on Choose a provider")
        .isEqualTo("/ui/objects");
    assertThat(WhereARefreshLands.theAddressARefreshIsSentTo(THE_PAYER_STEP, "  ", ""))
        .isEqualTo("/ui/objects");
  }

  @Test
  void whichever_of_the_two_the_address_named_is_the_one_that_travels_back() {
    assertThat(WhereARefreshLands.theAddressARefreshIsSentTo(THE_PAYER_STEP, A_PROVIDER, null))
        .isEqualTo("/ui/objects?provider=grow");
    assertThat(WhereARefreshLands.theAddressARefreshIsSentTo(THE_PAYER_STEP, null, A_RUN))
        .as("the run travels on its own, and first in the address rather than after an empty one")
        .isEqualTo("/ui/objects?testRunId=tester-run");
  }

  @Test
  void a_run_named_with_characters_a_query_string_escapes_comes_back_escaped() {
    assertThat(WhereARefreshLands.theAddressARefreshIsSentTo(THE_PAYER_STEP, null, "a run/1"))
        .as(
            "a run id handed back unescaped would cut the address short at the first & or # and"
                + " send the tester to a run that is not theirs")
        .isEqualTo("/ui/objects?testRunId=a+run%2F1");
  }
}
