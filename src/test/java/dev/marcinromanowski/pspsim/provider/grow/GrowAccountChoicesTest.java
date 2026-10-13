package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GrowAccountChoicesTest {

  @Test
  void the_two_wire_forms_are_offered_as_what_an_answer_of_this_account_would_carry() {
    assertThat(GrowWireForm.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "LIVE_WIRE",
                "answer as the live wire does: content type text/html, and every non-ASCII"
                    + " character escaped"),
            new AcceptedValue(
                "PUBLISHED_EXAMPLE",
                "answer as the published example does: content type application/json, with"
                    + " non-ASCII characters left as they are"));
  }

  @Test
  void the_field_that_asked_a_tester_which_of_the_two_forms_now_names_both_of_them() {
    assertThat(sentencesOf(GrowWireForm.SETTING))
        .as(
            "the help beside this field said \"which of the two forms\" and named neither, which"
                + " left reading a refusal as the only way to learn them")
        .anySatisfy(said -> assertThat(said).contains("text/html"))
        .anySatisfy(said -> assertThat(said).contains("application/json"));
  }

  @ParameterizedTest
  @CsvSource({
    GrowAcknowledgementMode.SETTING,
    GrowSettlementCeiling.SETTING,
    GrowRefundAllowance.SETTING,
    GrowRefundExcessRejection.SETTING,
    GrowMerchantBalance.SETTING,
    GrowProcessLookupRejection.SETTING,
    GrowWireForm.SETTING
  })
  void every_value_a_field_offers_is_one_a_configuration_naming_it_is_taken_for(String key) {
    for (String offered : valuesOf(key)) {
      assertThat(
              GrowAccount.of(
                      "accounts[0].configuration.",
                      TestRunId.of("grow-choices"),
                      new ConfiguredAccount(
                          "merchant-1", Map.of("userId", "u", "pageCode", "p", key, offered)))
                  .accountId())
          .isEqualTo("merchant-1");
    }
  }

  @Test
  void the_values_offered_for_each_field_are_every_value_that_field_has() {
    assertThat(valuesOf(GrowAcknowledgementMode.SETTING))
        .containsExactlyElementsOf(namesOf(GrowAcknowledgementMode.values()));
    assertThat(valuesOf(GrowSettlementCeiling.SETTING))
        .containsExactlyElementsOf(namesOf(GrowSettlementCeiling.values()));
    assertThat(valuesOf(GrowRefundAllowance.SETTING))
        .containsExactlyElementsOf(namesOf(GrowRefundAllowance.values()));
    assertThat(valuesOf(GrowRefundExcessRejection.SETTING))
        .containsExactlyElementsOf(namesOf(GrowRefundExcessRejection.values()));
    assertThat(valuesOf(GrowMerchantBalance.SETTING))
        .containsExactlyElementsOf(namesOf(GrowMerchantBalance.values()));
    assertThat(valuesOf(GrowProcessLookupRejection.SETTING))
        .containsExactlyElementsOf(namesOf(GrowProcessLookupRejection.values()));
    assertThat(valuesOf(GrowWireForm.SETTING))
        .containsExactlyElementsOf(namesOf(GrowWireForm.values()));
  }

  @ParameterizedTest
  @CsvSource({
    GrowAcknowledgementMode.SETTING,
    GrowSettlementCeiling.SETTING,
    GrowRefundAllowance.SETTING,
    GrowRefundExcessRejection.SETTING,
    GrowMerchantBalance.SETTING,
    GrowProcessLookupRejection.SETTING,
    GrowWireForm.SETTING,
    GrowHoldWindow.CAPTURE_WINDOW,
    GrowHoldWindow.AUTOMATIC_VOID_AFTER
  })
  void a_setting_sent_empty_is_read_as_one_nobody_configured_rather_than_refused(String key) {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put(GrowAccount.USER_ID, "u");
    configuration.put(GrowAccount.PAGE_CODE, "p");
    configuration.put(key, "");

    GrowAccount empty =
        GrowAccount.of(
            "accounts[0].configuration.",
            TestRunId.of("grow-blank"),
            new ConfiguredAccount("merchant-1", configuration));

    assertThat(empty)
        .as(
            "the field says \"Leaving it empty behaves as grow's own default does\", and the other"
                + " four providers read an empty value as one nobody configured; grow read it as"
                + " a value and refused it, so the sentence was false of every call that sent one")
        .isEqualTo(
            GrowAccount.of(
                "accounts[0].configuration.",
                TestRunId.of("grow-blank"),
                new ConfiguredAccount(
                    "merchant-1", Map.of(GrowAccount.USER_ID, "u", GrowAccount.PAGE_CODE, "p"))));
  }

  @ParameterizedTest
  @CsvSource({
    GrowAccount.USER_ID,
    GrowAccount.PAGE_CODE,
    GrowHoldWindow.CAPTURE_WINDOW,
    GrowHoldWindow.AUTOMATIC_VOID_AFTER
  })
  void a_field_whose_values_are_not_a_closed_set_offers_none_of_them(String key) {
    assertThat(field(key).acceptedValues())
        .as(
            "two credentials grow issues and two spans of time: none of them is a set this"
                + " provider could write out")
        .isEmpty();
  }

  @ParameterizedTest
  @CsvSource({GrowAccount.USER_ID, GrowAccount.PAGE_CODE})
  void a_credential_grow_issues_is_typed_out_and_is_no_span_of_time(String key) {
    assertThat(field(key).holdsADuration()).isFalse();
  }

  @Test
  void the_two_spans_say_they_hold_one_and_the_shape_a_call_writes_it_in() {
    assertThat(field(GrowHoldWindow.CAPTURE_WINDOW).holdsADuration())
        .as(
            "a box wanting P7D answered PT168H, so the provider declares the span and core draws"
                + " the control a tester states it on")
        .isTrue();
    assertThat(field(GrowHoldWindow.AUTOMATIC_VOID_AFTER).holdsADuration()).isTrue();
    assertThat(field(GrowHoldWindow.CAPTURE_WINDOW).example()).isEqualTo("P7D");
    assertThat(field(GrowHoldWindow.AUTOMATIC_VOID_AFTER).example()).isEqualTo("P10D");
  }

  @Test
  void the_two_spans_say_how_each_reader_states_one() {
    assertThat(field(GrowHoldWindow.CAPTURE_WINDOW).whatItIsFor())
        .as(
            "this sentence is read on the form and answered to a control call asking what this"
                + " provider takes, and the two readers state a span in different forms")
        .contains("A form states it as a number and a unit")
        .contains("an ISO-8601 duration such as P7D");
    assertThat(field(GrowHoldWindow.AUTOMATIC_VOID_AFTER).whatItIsFor())
        .contains("A form states it as a number and a unit")
        .contains("an ISO-8601 duration such as P10D");
  }

  @Test
  void the_two_acknowledgement_modes_are_declared_with_what_each_one_leaves_a_caller_to_do() {
    assertThat(GrowAcknowledgementMode.acceptedValues())
        .as(
            "a form offers the sentence and never the enum name, so what grow counts a callback"
                + " as acknowledged by is written out here as a tester reads it")
        .containsExactly(
            new AcceptedValue(
                "HTTP_2XX",
                "any 2xx answer from your endpoint acknowledges the callback, and nothing more is"
                    + " expected of you"),
            new AcceptedValue(
                "APPROVE_TRANSACTION",
                "a 2xx answer leaves the callback waiting for your separate approveTransaction"
                    + " call, and it stays unacknowledged until that call arrives"));
  }

  @Test
  void the_two_settlement_ceilings_are_declared_with_the_code_a_settlement_beyond_each_is() {
    assertThat(GrowSettlementCeiling.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "FRAME_CAPTURE",
                "a settlement may not exceed the amount held, and one that does is refused 804"),
            new AcceptedValue(
                "THIRTY_PERCENT_ABOVE",
                "a settlement may reach 30% above the amount held, and one beyond that is refused"
                    + " 814"));
  }

  @Test
  void the_two_refund_allowances_are_declared_with_the_refund_each_one_refuses() {
    assertThat(GrowRefundAllowance.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "ONE_REFUND", "one refund per transaction, and a second one is refused"),
            new AcceptedValue(
                "TWO_REFUNDS", "two refunds per transaction, and a third one is refused"));
  }

  @Test
  void the_three_amounts_a_refund_is_measured_on_are_declared_with_the_code_each_refuses_at() {
    assertThat(GrowRefundExcessRejection.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "ORIGINAL_AMOUNT",
                "too large a refund is refused 105, which names the original amount"),
            new AcceptedValue(
                "CHARGE_AMOUNT",
                "too large a refund is refused 190, which names the charge amount"),
            new AcceptedValue(
                "TRANSACTION_AMOUNT",
                "too large a refund is refused 218, which names the transaction amount"));
  }

  @Test
  void the_two_merchant_balances_are_declared_with_what_each_one_does_to_a_refund() {
    assertThat(GrowMerchantBalance.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "AVAILABLE",
                "the balance funds a refund of this account, so no refund is refused for want of"
                    + " funds"),
            new AcceptedValue(
                "EMPTY",
                "the balance funds nothing, so a refund that passes every other check is refused"
                    + " 115 for want of funds"));
  }

  @Test
  void the_two_process_lookup_rejections_are_declared_with_the_grow_page_each_code_is_on() {
    assertThat(GrowProcessLookupRejection.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "PUBLISHED_EXAMPLE",
                "a process lookup with the wrong credentials is refused 716, as grow's own worked"
                    + " example answers it"),
            new AcceptedValue(
                "PUBLISHED_TABLE",
                "a process lookup with the wrong credentials is refused 730, as grow's published"
                    + " error table answers it"));
  }

  private static List<String> namesOf(Enum<?>[] values) {
    List<String> names = new ArrayList<>();
    for (Enum<?> value : values) {
      names.add(value.name());
    }
    return names;
  }

  private static List<String> valuesOf(String key) {
    List<String> values = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      values.add(accepted.value());
    }
    return values;
  }

  private static List<String> sentencesOf(String key) {
    List<String> said = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      said.add(accepted.whatItMeans());
    }
    return said;
  }

  private static ConfigurableField field(String key) {
    return GrowAccount.ACCOUNT_FIELDS.field(key).orElseThrow();
  }
}
