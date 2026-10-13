package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AutraErrorsTest {

  @Test
  void the_codes_a_scenario_may_name_are_the_register_pipe_separated_in_the_order_it_holds_them() {
    String named = AutraErrors.theCodesAScenarioMayName();

    assertThat(named.split("\\|"))
        .containsExactlyElementsOf(AutraErrors.THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES);
    assertThat(named)
        .contains(AutraErrors.AUTHORIZER_REJECTED)
        .contains(AutraErrors.MSG_GENERAL_ERROR);
  }

  @Test
  void an_error_item_carries_the_code_and_the_msg_autra_publishes_and_no_other_member() {
    AutraJson.JsonObject item = AutraErrors.item("A_CODE", "a message");

    assertThat(item.members().keySet()).containsExactly(AutraFields.CODE, AutraFields.MSG);
    assertThat(item.text(AutraFields.CODE)).contains("A_CODE");
    assertThat(item.text(AutraFields.MSG)).contains("a message");
  }

  @Test
  void the_acquiring_envelope_carries_errors_as_its_one_member_and_holds_every_item_given_it() {
    AutraJson.JsonObject envelope =
        AutraErrors.envelope(
            List.of(AutraErrors.item("ONE", "first"), AutraErrors.item("TWO", "second")));

    assertThat(envelope.members().keySet()).containsExactly(AutraFields.ERRORS);
    assertThat(envelope.flatten())
        .containsEntry(AutraFields.ERRORS + "." + AutraFields.CODE, List.of("ONE", "TWO"))
        .containsEntry(AutraFields.ERRORS + "." + AutraFields.MSG, List.of("first", "second"));
  }

  @Test
  void the_oauth_envelope_carries_one_string_under_error_and_nothing_the_acquiring_one_carries() {
    AutraJson.JsonObject envelope = AutraErrors.oauthEnvelope("invalid clientSecret");

    assertThat(envelope.members().keySet()).containsExactly(AutraFields.OAUTH_ERROR);
    assertThat(envelope.text(AutraFields.OAUTH_ERROR)).contains("invalid clientSecret");
    assertThat(AutraJsonWriter.toText(envelope)).isEqualTo("{\"error\":\"invalid clientSecret\"}");
  }

  @Test
  void every_code_this_contract_publishes_for_a_refusal_is_a_screaming_snake_case_identifier() {
    assertThat(AutraErrors.THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES)
        .isNotEmpty()
        .allSatisfy(code -> assertThat(code).matches("[A-Z][A-Z_]+"));
  }

  @Test
  void the_envelope_of_one_code_and_one_message_is_the_envelope_of_one_item_carrying_them() {
    assertThat(AutraJsonWriter.toText(AutraErrors.envelope("A_CODE", "a message")))
        .isEqualTo(
            AutraJsonWriter.toText(
                AutraErrors.envelope(List.of(AutraErrors.item("A_CODE", "a message")))));
    assertThat(AutraErrors.envelope("A_CODE", "a message").flatten())
        .containsEntry(AutraFields.ERRORS + "." + AutraFields.CODE, List.of("A_CODE"));
  }

  @Test
  void an_envelope_of_no_item_at_all_still_carries_errors_as_its_one_member() {
    assertThat(AutraErrors.envelope(List.of()).members()).containsOnlyKeys(AutraFields.ERRORS);
    assertThat(AutraErrors.envelope(List.of()).flatten())
        .containsEntry(AutraFields.ERRORS, List.of());
  }
}
