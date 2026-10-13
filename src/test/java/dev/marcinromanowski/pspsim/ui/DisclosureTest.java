package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DisclosureTest {

  private static final String PAYER_EMAIL = "pageField[email]";
  private static final String AUTHORIZATION = "Authorization";
  private static final String EMAIL = "payer@example.test";

  @Test
  void a_payers_email_stays_off_the_page_until_the_page_is_asked_for_values() {
    List<Disclosure.Field> fields = Disclosure.of(anExchangeCarryingAPayer(), false);

    assertThat(valuesOf(fields)).isEmpty();
    assertThat(fields).extracting(Disclosure.Field::name).contains(PAYER_EMAIL);
    assertThat(visibilityOf(fields, PAYER_EMAIL)).isEqualTo(Disclosure.Visibility.WITHHELD);
  }

  @Test
  void a_payers_email_is_handed_over_verbatim_once_the_page_has_been_asked_for_values() {
    List<Disclosure.Field> fields = Disclosure.of(anExchangeCarryingAPayer(), true);

    assertThat(valuesOf(fields)).contains(EMAIL);
    assertThat(visibilityOf(fields, PAYER_EMAIL)).isEqualTo(Disclosure.Visibility.SHOWN);
  }

  @Test
  void a_credential_the_simulator_hid_when_it_stored_the_row_stays_hidden_when_values_are_asked() {
    List<Disclosure.Field> fields = Disclosure.of(anExchangeCarryingAPayer(), true);

    assertThat(visibilityOf(fields, AUTHORIZATION))
        .isEqualTo(Disclosure.Visibility.REDACTED_ON_WRITE);
    assertThat(valuesOf(fields)).doesNotContain(Redaction.REDACTED);
  }

  @Test
  void a_field_that_was_sent_and_hidden_is_told_apart_from_one_that_was_never_sent() {
    List<Disclosure.Field> fields = Disclosure.of(anExchangeCarryingAPayer(), false);

    assertThat(fields).extracting(Disclosure.Field::name).contains(AUTHORIZATION);
    assertThat(fields).extracting(Disclosure.Field::name).doesNotContain("cvv");
  }

  @Test
  void a_field_sent_twice_reads_as_sent_twice_even_while_its_values_are_withheld() {
    Map<String, List<String>> sentTwice =
        Map.of("pageField[phone]", List.of("+972500000001", "+972500000002"));

    List<Disclosure.Field> fields = Disclosure.of(sentTwice, false);

    assertThat(fields.get(0).occurrences()).isEqualTo(2);
    assertThat(fields.get(0).values()).isEmpty();
  }

  @Test
  void a_field_that_arrived_carrying_no_value_is_not_reported_as_one_the_simulator_hid() {
    Map<String, List<String>> empty = Map.of("cField2", List.of());

    List<Disclosure.Field> fields = Disclosure.of(empty, false);

    assertThat(fields.get(0).visibility()).isEqualTo(Disclosure.Visibility.WITHHELD);
  }

  @Test
  void a_page_shows_values_only_when_it_was_asked_for_them_by_name() {
    assertThat(Disclosure.valuesAsked("show")).isTrue();
    assertThat(Disclosure.valuesAsked(null)).isFalse();
    assertThat(Disclosure.valuesAsked("")).isFalse();
    assertThat(Disclosure.valuesAsked("true")).isFalse();
  }

  private static Map<String, List<String>> anExchangeCarryingAPayer() {
    Map<String, List<String>> body = new LinkedHashMap<>();
    body.put(AUTHORIZATION, List.of(Redaction.REDACTED));
    body.put("pageField[fullName]", List.of("A Payer"));
    body.put(PAYER_EMAIL, List.of(EMAIL));
    return body;
  }

  private static List<String> valuesOf(List<Disclosure.Field> fields) {
    return fields.stream().flatMap(field -> field.values().stream()).toList();
  }

  private static Disclosure.Visibility visibilityOf(List<Disclosure.Field> fields, String name) {
    return fields.stream()
        .filter(field -> field.name().equals(name))
        .findFirst()
        .orElseThrow()
        .visibility();
  }
}
