package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DoneTest {

  @Test
  void the_notice_of_a_reset_names_what_it_cleared_and_how_many_providers_it_reached() {
    assertThat(Done.theTestRunIsReset(3, 7, 42))
        .isEqualTo(
            "This test run is reset in every provider. It cleared 7 object(s) and 42 audit"
                + " record(s) across 3 provider(s).");
  }

  @Test
  void the_notice_of_a_started_payment_names_the_provider_and_the_id_it_gave_it() {
    assertThat(Done.thePaymentExistsNow("autra", "pix_0ec1"))
        .isEqualTo(
            "This payment exists now: autra gave it the id pix_0ec1, and the audit trail records"
                + " it as started from a screen rather than by a call from a client application.");
  }

  @Test
  void the_notice_of_a_removal_names_the_account_it_removed() {
    assertThat(Done.theAccountIsRemoved("merchant-2"))
        .isEqualTo(
            "Account merchant-2 is removed. Its provider row, its callback targets and its"
                + " identity are gone; the test run and every other account of it are"
                + " untouched, and a callback still waiting on the removed account is now"
                + " acknowledged by the merchant's 2xx like any other.");
  }

  @Test
  void the_notice_of_a_forgotten_run_says_its_registration_was_dropped_and_what_emptying_cleared() {
    assertThat(
            Done.theTestRunIsForgotten("ghost-194902", 1, new DashboardScreen.Emptied(5, 12, 340)))
        .isEqualTo(
            "Test run ghost-194902 is forgotten. Its registration is dropped, and emptying it"
                + " across 5 provider(s) cleared 12 object(s) and 340 audit record(s). Every other"
                + " test run is untouched.");
  }

  @Test
  void the_notice_of_a_run_nothing_had_registered_says_there_was_no_registration_to_drop() {
    assertThat(Done.theTestRunIsForgotten("ghost-194902", 0, new DashboardScreen.Emptied(5, 0, 0)))
        .isEqualTo(
            "Test run ghost-194902 is forgotten. Nothing was registered under that name, so there"
                + " was no registration to drop, and emptying it across 5 provider(s) cleared 0"
                + " object(s) and 0 audit record(s). Every other test run is untouched.");
  }
}
