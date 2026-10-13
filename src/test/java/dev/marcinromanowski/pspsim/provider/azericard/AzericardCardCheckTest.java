package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AzericardCardCheckTest {

  private static final Instant LATE_SEPTEMBER_2026 = Instant.parse("2026-09-29T12:00:00Z");
  private static final String A_PUBLISHED_PAN = "4127208104942601";
  private static final String ITS_CVC = "796";

  @ParameterizedTest(name = "expiry {0} is accepted in September 2026")
  @ValueSource(strings = {"09/26", "12/26", "01/27", "05/31", " 05 / 31 "})
  void a_published_card_is_accepted_through_the_month_its_expiry_names(String expiry) {
    assertThat(AzericardCardCheck.ofThePage(A_PUBLISHED_PAN, expiry, ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Accepted("2601"));
  }

  @Test
  void a_card_expiring_in_the_last_month_of_a_year_is_accepted_until_that_month_ends() {
    assertThat(
            AzericardCardCheck.ofThePage(
                A_PUBLISHED_PAN, "12/26", ITS_CVC, Instant.parse("2026-12-31T23:59:59Z")))
        .isEqualTo(new AzericardPresentedCard.Accepted("2601"));
    assertThat(
            AzericardCardCheck.ofThePage(
                A_PUBLISHED_PAN, "12/26", ITS_CVC, Instant.parse("2027-01-01T00:00:00Z")))
        .isEqualTo(new AzericardPresentedCard.Declined("2601"));
  }

  @Test
  void a_card_expiring_in_january_is_accepted_through_january() {
    assertThat(
            AzericardCardCheck.ofThePage(
                A_PUBLISHED_PAN, "01/27", ITS_CVC, Instant.parse("2027-01-31T23:59:59Z")))
        .isEqualTo(new AzericardPresentedCard.Accepted("2601"));
    assertThat(
            AzericardCardCheck.ofThePage(
                A_PUBLISHED_PAN, "01/27", ITS_CVC, Instant.parse("2027-02-01T00:00:00Z")))
        .isEqualTo(new AzericardPresentedCard.Declined("2601"));
  }

  @ParameterizedTest(name = "expiry \"{0}\" is declined")
  @ValueSource(
      strings = {
        "08/26",
        "00/31",
        "13/31",
        "5/31",
        "05/2031",
        "0531",
        "",
        "/31",
        "05/",
        "05/31/31",
        "ab/cd"
      })
  void a_published_card_with_a_past_or_malformed_expiry_is_declined_keeping_its_last_four(
      String expiry) {
    assertThat(AzericardCardCheck.ofThePage(A_PUBLISHED_PAN, expiry, ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Declined("2601"));
  }

  @Test
  void a_published_card_the_payer_gave_no_expiry_for_is_declined() {
    assertThat(AzericardCardCheck.ofThePage(A_PUBLISHED_PAN, null, ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Declined("2601"));
  }

  @Test
  void a_card_number_the_payer_typed_in_groups_is_read_without_its_spaces() {
    assertThat(
            AzericardCardCheck.ofThePage(
                "4127 2081 0494 2601", "05/31", ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Accepted("2601"));
    assertThat(
            AzericardCardCheck.ofThePage(
                "4000 0000 0000 0002", "05/31", ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Declined("0002"));
  }

  @Test
  void an_unpublished_card_number_is_declined_keeping_its_last_four_digits() {
    assertThat(
            AzericardCardCheck.ofThePage("4000000000000002", "05/31", ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Declined("0002"));
    assertThat(AzericardCardCheck.ofThePage("1234", "05/31", ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Declined("1234"));
  }

  @ParameterizedTest(name = "card number \"{0}\" is declined keeping no digits")
  @ValueSource(strings = {"123", "", "4127-2081-0494-2601", "abcdefgh"})
  void a_card_number_too_short_or_not_all_digits_is_declined_keeping_no_digits(String pan) {
    assertThat(AzericardCardCheck.ofThePage(pan, "05/31", ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Declined(null));
  }

  @Test
  void a_payer_who_typed_no_card_number_is_declined_keeping_no_digits() {
    assertThat(AzericardCardCheck.ofThePage(null, "05/31", ITS_CVC, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Declined(null));
  }

  @ParameterizedTest(name = "CVC \"{0}\" is declined")
  @ValueSource(strings = {"79", "7960", "7a6", ""})
  void a_published_card_whose_cvc_is_not_three_digits_is_declined(String cvc) {
    assertThat(AzericardCardCheck.ofThePage(A_PUBLISHED_PAN, "05/31", cvc, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Declined("2601"));
  }

  @Test
  void each_published_test_card_passes_the_check_with_its_published_expiry_and_cvc() {
    assertThat(AzericardCardCheck.ofATestCard(AzericardTestCard.FIRST, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Accepted("2601"));
    assertThat(AzericardCardCheck.ofATestCard(AzericardTestCard.SECOND, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Accepted("0780"));
    assertThat(AzericardCardCheck.ofATestCard(AzericardTestCard.THIRD, LATE_SEPTEMBER_2026))
        .isEqualTo(new AzericardPresentedCard.Accepted("0724"));
    assertThat(
            AzericardCardCheck.ofATestCard(
                AzericardTestCard.THIRD, Instant.parse("2031-05-01T00:00:00Z")))
        .isEqualTo(new AzericardPresentedCard.Declined("0724"));
  }
}
