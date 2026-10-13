package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AutraDeclineTest {

  private static final TestRunId RUN = TestRunId.of("autra-decline");

  private static final AutraMerchant AN_ACCOUNT_CONFIGURING_NO_ISSUER_MESSAGE =
      AutraDoubles.merchant(RUN, "merchant-1", "25055662000190");

  private static final AutraMerchant AN_ACCOUNT_CONFIGURING_AN_ISSUER_MESSAGE =
      AutraDoubles.merchant(
          RUN,
          "merchant-2",
          "11111111111",
          Map.of(AutraAuthorizerMessage.SETTING, "59 - Suspected fraud"));

  private static final String A_CODE_SPELLED_LIKE_THE_REVERSAL_CYCLE_ONE_AND_CARRIED_BY_NO_DECLINE =
      "ORIGINAL_CYCLE_NOT_ACCEPTED_YET";

  private static final String THE_CODES_THIS_CONTRACT_PUBLISHES_IN_THE_ORDER_IT_PUBLISHES_THEM =
      "AUTHORIZER_REJECTED|INSUFFICIENT_FUNDS|CARD_DECLINED|TOKEN_NOT_AVAILABLE"
          + "|INVALID_CARD_BRAND|MSG_GENERAL_ERROR|UNAUTHORIZED"
          + "|AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE|CANT_REFUND_TRANSACTION_FROM_TODAY"
          + "|ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD|CARD_BRAND_NOT_SUPPORTED"
          + "|ORIGINAL_CYCLE_NOT_ACCEPTED";

  static List<Arguments> theCodeAndTheMsgTheWireCarriesBesideIt() {
    return List.of(
        Arguments.of("AUTHORIZER_REJECTED", "05 - Do not honor"),
        Arguments.of("INSUFFICIENT_FUNDS", "insufficient funds"),
        Arguments.of(
            "CARD_DECLINED",
            "the card was declined, and provider autra publishes no msg for CARD_DECLINED"
                + " anywhere: it glosses the code as \"Recusa generica. Pode ser fraude, dados"
                + " errados, banco bloqueando\" in its own error table and prints no wire example"
                + " of the string, so this sentence is the simulator's and not a quotation"),
        Arguments.of("TOKEN_NOT_AVAILABLE", "the card token this call named is no longer eligible"),
        Arguments.of("INVALID_CARD_BRAND", "the card brand is not supported"),
        Arguments.of("MSG_GENERAL_ERROR", "502 : badGateway"),
        Arguments.of(
            "UNAUTHORIZED",
            "authentication failed while processing, and the reservation is untouched"),
        Arguments.of(
            "AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE",
            "the amount this call names is higher than the remaining balance of the payment"),
        Arguments.of(
            "CANT_REFUND_TRANSACTION_FROM_TODAY",
            "the original transaction is from today and cannot be refunded"),
        Arguments.of(
            "ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD",
            "the original transaction date is too old to be reversed"),
        Arguments.of("CARD_BRAND_NOT_SUPPORTED", "the card brand is not supported"),
        Arguments.of(
            "ORIGINAL_CYCLE_NOT_ACCEPTED", "the original cycle is not accepted for reversal"));
  }

  @ParameterizedTest
  @MethodSource("theCodeAndTheMsgTheWireCarriesBesideIt")
  void a_decline_naming_a_code_answers_the_msg_spelled_out_here_and_read_off_no_source_under_test(
      String code, String msg) {
    assertThat(
            AutraDecline.of(code)
                .orElseThrow()
                .theMsgNoDeclineCanBeDeclaredWithout(AN_ACCOUNT_CONFIGURING_NO_ISSUER_MESSAGE))
        .as(
            "the msg a decline carrying %s goes to the wire beside, spelled out in this test rather"
                + " than read off the declaration under test, so a declaration that respelled it"
                + " fails here",
            code)
        .isEqualTo(msg);
  }

  @Test
  void the_published_codes_a_scenario_may_name_are_spelled_and_ordered_as_this_test_spells_them() {
    assertThat(AutraErrors.theCodesAScenarioMayName())
        .as(
            "the FailureCodes a scenario picks a decline code from, whose order a tester reads and"
                + " whose spelling the wire carries")
        .isEqualTo(THE_CODES_THIS_CONTRACT_PUBLISHES_IN_THE_ORDER_IT_PUBLISHES_THEM);
    assertThat(AutraErrors.DECLINE_CODES)
        .as("the published sentence naming the same codes in the same order, comma separated")
        .contains(
            THE_CODES_THIS_CONTRACT_PUBLISHES_IN_THE_ORDER_IT_PUBLISHES_THEM.replace("|", ", "));
  }

  @Test
  void the_msg_an_issuer_refusal_carries_is_the_account_setting_and_not_a_value_of_this_build() {
    assertThat(
            AutraDecline.AUTHORIZER_REJECTED.theMsgNoDeclineCanBeDeclaredWithout(
                AN_ACCOUNT_CONFIGURING_AN_ISSUER_MESSAGE))
        .as("the msg the wire carries beside AUTHORIZER_REJECTED for an account configuring one")
        .isEqualTo("59 - Suspected fraud");
    assertThat(
            AutraDecline.AUTHORIZER_REJECTED.theMsgNoDeclineCanBeDeclaredWithout(
                AN_ACCOUNT_CONFIGURING_NO_ISSUER_MESSAGE))
        .as("the msg the wire carries beside AUTHORIZER_REJECTED for an account configuring none")
        .isEqualTo("05 - Do not honor");
  }

  @Test
  void the_whole_wire_answer_to_a_declined_acquiring_call_is_errors_with_one_code_and_one_msg() {
    assertThat(
            AutraJsonWriter.toText(
                AutraErrors.envelope(
                    "INSUFFICIENT_FUNDS",
                    AutraDeclineMessages.of(
                        "INSUFFICIENT_FUNDS", AN_ACCOUNT_CONFIGURING_NO_ISSUER_MESSAGE))))
        .as("the bytes provider autra answers a refused acquiring call with")
        .isEqualTo(
            "{\"errors\":[{\"code\":\"INSUFFICIENT_FUNDS\",\"msg\":\"insufficient funds\"}]}");
  }

  @ParameterizedTest
  @EnumSource(AutraDecline.class)
  void no_decline_of_this_contract_can_be_declared_without_the_msg_its_code_carries(
      AutraDecline decline) {
    assertThat(
            decline.theMsgNoDeclineCanBeDeclaredWithout(AN_ACCOUNT_CONFIGURING_NO_ISSUER_MESSAGE))
        .as(
            "the msg %s carries for an account configuring no issuer message, which the"
                + " declaration of %s had to supply for it to exist at all",
            decline, decline)
        .isNotBlank();
    assertThat(
            decline.theMsgNoDeclineCanBeDeclaredWithout(AN_ACCOUNT_CONFIGURING_AN_ISSUER_MESSAGE))
        .as("the msg %s carries for an account configuring an issuer message", decline)
        .isNotBlank();
  }

  @ParameterizedTest
  @EnumSource(AutraDecline.class)
  void the_code_a_decline_carries_is_the_one_the_register_publishes_under_that_name(
      AutraDecline decline) {
    assertThat(decline.code())
        .as(
            "the code %s puts on the wire, which is the constant of that spelling and not another"
                + " one of this contract",
            decline)
        .isEqualTo(decline.name());
    assertThat(AutraErrors.THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES)
        .as("the register a scenario picks from, which publishes the code %s carries", decline)
        .contains(decline.code());
  }

  @ParameterizedTest
  @EnumSource(AutraDecline.class)
  void a_published_code_is_matched_by_the_one_decline_carrying_it_and_by_no_other(
      AutraDecline decline) {
    assertThat(AutraDecline.of(decline.code()))
        .as("the decline the code %s names, looked up the way a scenario code reaches it", decline)
        .contains(decline);
  }

  @Test
  void a_code_no_decline_of_this_contract_carries_is_matched_by_none_of_them() {
    assertThat(AutraErrors.THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES)
        .as(
            "the code this test looks up is published by none, and is spelled close enough to the"
                + " reversal-cycle one that an inexact match would return that decline")
        .doesNotContain(A_CODE_SPELLED_LIKE_THE_REVERSAL_CYCLE_ONE_AND_CARRIED_BY_NO_DECLINE);

    assertThat(
            AutraDecline.of(A_CODE_SPELLED_LIKE_THE_REVERSAL_CYCLE_ONE_AND_CARRIED_BY_NO_DECLINE))
        .as("the decline a code this contract publishes for no refusal names")
        .isEmpty();
  }

  @Test
  void
      the_register_is_read_from_the_method_that_builds_it_and_not_only_from_the_field_storing_it() {
    assertThat(AutraDecline.theCodesADeclineOfThisContractCarries())
        .as(
            "every other reader of this register reads the field AutraErrors stores it in, which a"
                + " run builds once, so the line that builds it is covered by whichever test"
                + " happened to load that class first and a register emptied there is a change no"
                + " reader can be shown to notice; this calls the method itself, in the order this"
                + " contract publishes the codes in")
        .containsExactly(
            THE_CODES_THIS_CONTRACT_PUBLISHES_IN_THE_ORDER_IT_PUBLISHES_THEM.split("\\|"));
  }

  @Test
  void the_register_a_scenario_picks_from_holds_one_code_for_each_decline_and_nothing_besides() {
    assertThat(AutraErrors.THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES)
        .as(
            "the register, which is read off the declarations rather than written beside them, so"
                + " a code cannot be published without the decline that carries its msg")
        .hasSize(AutraDecline.values().length)
        .doesNotHaveDuplicates();
  }
}
