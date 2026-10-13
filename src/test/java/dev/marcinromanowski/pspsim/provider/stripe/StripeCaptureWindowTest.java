package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StripeCaptureWindowTest {

  private static final String PREFIX = "accounts[0].configuration.";

  private static final Instant THE_MOMENT_THE_HOLD_WAS_TAKEN =
      Instant.parse("2026-09-11T12:00:00Z");

  @ParameterizedTest
  @CsvSource({
    "P3D, 2026-09-14T12:00:00Z",
    "PT168H, 2026-09-18T12:00:00Z",
    "PT1.5S, 2026-09-11T12:00:01.5Z",
    "PT0.0009S, 2026-09-11T12:00:00.0009Z",
    "PT1.0000015S, 2026-09-11T12:00:01.0000015Z",
    "PT0.000001S, 2026-09-11T12:00:00.000001Z"
  })
  void a_deadline_is_the_whole_window_after_the_moment_the_hold_was_taken(
      String configured, String deadline) {
    StripeCaptureWindow window = StripeCaptureWindow.of(PREFIX, configured);

    assertThat(window.deadlineFrom(THE_MOMENT_THE_HOLD_WAS_TAKEN))
        .as(
            "the deadline was counted in whole milliseconds of the window, so every window"
                + " shorter than a millisecond fixed the very moment the hold was taken and"
                + " every window with a finer tail lost it")
        .isEqualTo(Instant.parse(deadline));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0.0009S", "PT0.000001S", "PT1.0000015S", "P3D"})
  void a_deadline_sits_after_the_moment_the_hold_was_taken_for_every_window_taken(
      String configured) {
    StripeCaptureWindow window = StripeCaptureWindow.of(PREFIX, configured);

    assertThat(window.deadlineFrom(THE_MOMENT_THE_HOLD_WAS_TAKEN))
        .as(
            "StripePaymentIntent.theAuthorizationHasExpired reads a deadline at or before the"
                + " creation moment as expired, so a deadline equal to it is a hold nothing can"
                + " capture, whatever the account asked for")
        .isAfter(THE_MOMENT_THE_HOLD_WAS_TAKEN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0.000000999S", "PT0.000000001S", "PT0.0000005S"})
  void refuses_a_window_finer_than_the_deadline_it_fixes_is_kept(String finer) {
    assertThatCode(() -> StripeCaptureWindow.of(PREFIX, finer))
        .as(
            "capture_expires_at is a timestamptz, which keeps a microsecond at best, so a"
                + " window finer than that is stored as the creation moment itself and the"
                + " account's PaymentIntents are expired before anything can capture them")
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(PREFIX + StripeCaptureWindow.MUST_BE_KEEPABLE);
  }

  @Test
  void one_microsecond_is_the_shortest_window_this_provider_takes() {
    StripeCaptureWindow finest = StripeCaptureWindow.of(PREFIX, "PT0.000001S");

    assertThat(finest.window()).isEqualTo(Duration.ofNanos(1000));
  }

  @Test
  void the_longest_window_this_provider_takes_is_still_taken() {
    StripeCaptureWindow longest = StripeCaptureWindow.of(PREFIX, "PT8760H");

    assertThat(longest.window()).isEqualTo(Duration.ofDays(365));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0.000000001S", "PT0.0000005S", "PT0S", "PT-120H"})
  void refuses_a_stored_window_no_hold_could_outlive(String refused) {
    assertThatCode(() -> StripeCaptureWindow.stored(refused))
        .as(
            "JdbcStripeAccounts reads capture_window straight through this factory, so of() was"
                + " the only guarded door and a row an older build stored would have come back"
                + " as a window every hold of the account is born past")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(StripeCaptureWindow.NO_WINDOW_A_HOLD_CAN_OUTLIVE);
  }

  @Test
  void reads_back_the_longest_window_the_column_admits_rather_than_judging_the_year_again() {
    assertThatCode(() -> StripeCaptureWindow.stored("PT8760H0.0000004S"))
        .as(
            "stripe_account bounds the year as capture_window::interval <= interval '365 days'"
                + " and a Postgres interval rounds to the microsecond, so a text over the year"
                + " by half a microsecond or less is one the column admits and of() refuses;"
                + " judging the ceiling here as well would stop the account loading over a row"
                + " the store was allowed to keep, while the floor is exact at every resolution"
                + " on both sides")
        .doesNotThrowAnyException();

    assertThatThrownBy(() -> StripeCaptureWindow.of(PREFIX, "PT8760H0.0000004S"))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeCaptureWindow.MUST_BE_STORABLE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0.000001S", "PT0.0009S", "PT168H", "PT8760H"})
  void reads_back_a_stored_window_a_hold_can_outlive(String stored) {
    assertThatCode(() -> StripeCaptureWindow.stored(stored)).doesNotThrowAnyException();

    assertThat(StripeCaptureWindow.stored(stored).storedForm()).isEqualTo(stored);
  }
}
