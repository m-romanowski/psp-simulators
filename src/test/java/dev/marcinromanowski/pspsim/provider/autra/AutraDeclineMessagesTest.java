package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AutraDeclineMessagesTest {

  private static final TestRunId RUN = TestRunId.of("autra-decline-messages");

  private static final AutraMerchant MERCHANT =
      AutraDoubles.merchant(RUN, "merchant-1", "25055662000190");

  private static final String A_CODE_SPELLED_LIKE_THE_REVERSAL_CYCLE_ONE_AND_PUBLISHED_BY_NONE =
      "ORIGINAL_CYCLE_NOT_ACCEPTED_YET";

  @ParameterizedTest
  @MethodSource("theCodesADeclineOfThisContractCarries")
  void every_code_a_decline_of_this_contract_carries_is_answered_with_a_msg_and_not_refused(
      String code) {
    assertThat(AutraDeclineMessages.of(code, MERCHANT))
        .as("the msg a decline carrying %s goes to the wire beside", code)
        .isNotBlank();
  }

  static Stream<String> theCodesADeclineOfThisContractCarries() {
    return AutraErrors.THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES.stream();
  }

  @Test
  void a_code_no_decline_of_this_contract_carries_is_refused_and_not_given_the_msg_of_another() {
    assertThat(AutraErrors.THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES)
        .as(
            "the code this test hands the mapping is carried by no decline of this contract, and is"
                + " spelled close enough to the one the reversal-cycle msg belongs to that an"
                + " inexact match would hand it that msg")
        .doesNotContain(A_CODE_SPELLED_LIKE_THE_REVERSAL_CYCLE_ONE_AND_PUBLISHED_BY_NONE);

    assertThatCode(
            () ->
                AutraDeclineMessages.of(
                    A_CODE_SPELLED_LIKE_THE_REVERSAL_CYCLE_ONE_AND_PUBLISHED_BY_NONE, MERCHANT))
        .as(
            "a code no decline of this contract carries is refused where it is read, rather than"
                + " reaching an acquiring response beside the msg of a code that is not the one the"
                + " decline carries")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(A_CODE_SPELLED_LIKE_THE_REVERSAL_CYCLE_ONE_AND_PUBLISHED_BY_NONE)
        .hasMessageContaining("AutraDecline")
        .hasMessageNotContaining(AutraDeclineMessages.CYCLE_NOT_ACCEPTED);
  }

  @Test
  void an_issuer_refusal_carries_the_message_the_account_configures_and_not_one_of_its_own() {
    AutraMerchant configured =
        AutraDoubles.merchant(
            RUN,
            "merchant-2",
            "11111111111",
            Map.of(AutraAuthorizerMessage.SETTING, "59 - Suspected fraud"));

    assertThat(AutraDeclineMessages.of(AutraErrors.AUTHORIZER_REJECTED, configured))
        .isEqualTo("59 - Suspected fraud");
    assertThat(AutraDeclineMessages.of(AutraErrors.AUTHORIZER_REJECTED, MERCHANT))
        .isEqualTo(AutraAuthorizerMessage.DEFAULT);
  }

  @Test
  void each_code_that_is_not_an_issuer_refusal_is_given_the_message_its_own_decline_was_declared() {
    assertThat(AutraDeclineMessages.of(AutraErrors.INSUFFICIENT_FUNDS, MERCHANT))
        .isEqualTo(AutraDeclineMessages.THE_PUBLISHED_INSUFFICIENT_FUNDS_MESSAGE);
    assertThat(AutraDeclineMessages.of(AutraErrors.CARD_DECLINED, MERCHANT))
        .isEqualTo(AutraDeclineMessages.THE_CARD_WAS_DECLINED);
    assertThat(AutraDeclineMessages.of(AutraErrors.TOKEN_NOT_AVAILABLE, MERCHANT))
        .isEqualTo(AutraDeclineMessages.TOKEN_IS_NO_LONGER_ELIGIBLE);
    assertThat(AutraDeclineMessages.of(AutraErrors.INVALID_CARD_BRAND, MERCHANT))
        .isEqualTo(AutraDeclineMessages.BRAND_IS_NOT_SUPPORTED);
    assertThat(AutraDeclineMessages.of(AutraErrors.MSG_GENERAL_ERROR, MERCHANT))
        .isEqualTo(AutraDeclineMessages.TRANSIENT_PROCESSING_ERROR);
    assertThat(AutraDeclineMessages.of(AutraErrors.UNAUTHORIZED, MERCHANT))
        .isEqualTo(AutraDeclineMessages.TRANSIENT_AUTHENTICATION_ERROR);
    assertThat(
            AutraDeclineMessages.of(
                AutraErrors.AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE, MERCHANT))
        .isEqualTo(AutraDeclineMessages.HIGHER_THAN_THE_REMAINING_BALANCE);
    assertThat(AutraDeclineMessages.of(AutraErrors.CANT_REFUND_TRANSACTION_FROM_TODAY, MERCHANT))
        .isEqualTo(AutraDeclineMessages.FROM_TODAY);
    assertThat(AutraDeclineMessages.of(AutraErrors.ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD, MERCHANT))
        .isEqualTo(AutraDeclineMessages.TOO_OLD);
    assertThat(AutraDeclineMessages.of(AutraErrors.CARD_BRAND_NOT_SUPPORTED, MERCHANT))
        .isEqualTo(AutraDeclineMessages.BRAND_IS_NOT_SUPPORTED);
    assertThat(AutraDeclineMessages.of(AutraErrors.ORIGINAL_CYCLE_NOT_ACCEPTED, MERCHANT))
        .isEqualTo(AutraDeclineMessages.CYCLE_NOT_ACCEPTED);
  }
}
