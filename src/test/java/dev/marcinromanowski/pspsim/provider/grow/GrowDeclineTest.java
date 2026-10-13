package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrowDeclineTest {

  @ParameterizedTest(name = "{0} is err.id {1} with the reason provider grow publishes")
  @CsvSource(
      delimiter = '~',
      value = {
        "expiredCard~103~כרטיס פג תוקף.",
        "stolenCard~103~כרטיס גנוב - עסקה לא מאושרת",
        "blockedCard~103~כרטיס חסום - עסקה לא מאושרת",
        "creditCompanyDidNotApprove~103~חברת האשראי לא אשרה את העסקה",
        "cardInactiveOrExpired~451~כרטיס האשראי אינו פעיל/פג תוקף, נא לפנות ללקוח לקבלת כרטיס חדש",
        "cardBlockedByTheCreditCompany~452~חברת האשראי חסמה את הכרטיס, נא לפנות ללקוח לבדיקה",
        "systemBlock~453~חסימת מערכת"
      })
  void carries_the_id_and_the_hebrew_reason_grow_publishes(String named, int id, String message) {
    assertThat(GrowDecline.of(named)).isPresent();
    assertThat(GrowDecline.of(named).orElseThrow().error()).isEqualTo(new GrowError(id, message));
  }

  @Test
  void four_reasons_share_one_error_id_so_the_reason_is_in_the_message_and_not_the_code() {
    assertThat(GrowDecline.of("expiredCard").orElseThrow().error().id()).isEqualTo(103);
    assertThat(GrowDecline.of("stolenCard").orElseThrow().error().id()).isEqualTo(103);
    assertThat(GrowDecline.of("expiredCard").orElseThrow().error().message())
        .isNotEqualTo(GrowDecline.of("stolenCard").orElseThrow().error().message());
  }

  @Test
  void names_no_reason_for_a_word_grow_does_not_publish() {
    assertThat(GrowDecline.of("insufficient_funds")).isEmpty();
    assertThat(GrowDecline.of("103")).isEmpty();
  }

  @Test
  void offers_a_scenario_every_reason_and_no_other() {
    assertThat(GrowDecline.theReasonsAScenarioMayName())
        .isEqualTo(
            "expiredCard|stolenCard|blockedCard|creditCompanyDidNotApprove|cardInactiveOrExpired"
                + "|cardBlockedByTheCreditCompany|systemBlock");
  }

  @Test
  void tells_a_tester_which_reasons_grow_calls_final_and_which_it_does_not() {
    assertThat(GrowDecline.theReasonsDescribed())
        .isEqualTo(
            "expiredCard (סופי), stolenCard (סופי), blockedCard (סופי),"
                + " creditCompanyDidNotApprove (לא סופי), cardInactiveOrExpired (סופי),"
                + " cardBlockedByTheCreditCompany (סופי), systemBlock (סופי)");
  }

  @Test
  void publishes_the_two_class_words_grow_uses_and_no_english_gloss() {
    assertThat(GrowDeclineFinality.FINAL.publishedAs()).isEqualTo("סופי");
    assertThat(GrowDeclineFinality.NOT_FINAL.publishedAs()).isEqualTo("לא סופי");
  }
}
