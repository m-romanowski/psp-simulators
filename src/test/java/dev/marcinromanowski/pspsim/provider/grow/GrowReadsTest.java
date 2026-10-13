package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class GrowReadsTest {

  private static final TestRunId RUN = TestRunId.of("grow-reads");

  private static final String MERCHANT = "merchant-1";

  private static final String USER_ID = "grow-reads-user";

  private static final String A_RULE = "rule-9";

  @Test
  void the_form_the_read_sends_carries_the_user_id_of_the_account_and_what_the_tester_filled_in() {
    GrowForm sent =
        GrowReads.theRetraceFormOf(
            theQuestionAskedWith("tok-1", "1001"),
            SimulatorDoubles.account(RUN, MERCHANT, USER_ID, "grow-reads-page", Map.of()));

    assertThat(sent.first(GrowFields.USER_ID))
        .as(
            "grow names the merchant by the userId of the account, which this simulator sends and"
                + " never asks a tester to type")
        .isEqualTo(USER_ID);
    assertThat(sent.first(GrowFields.CARD_TOKEN)).isEqualTo("tok-1");
    assertThat(sent.first(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER)).isEqualTo("1001");
    assertThat(sent.values().keySet())
        .as("nothing else is sent, because grow reads nothing else on this operation")
        .containsExactly(
            GrowFields.USER_ID, GrowFields.CARD_TOKEN, GrowFields.TRANSACTION_UNIQUE_IDENTIFIER);
  }

  @Test
  void the_retrace_declares_the_user_id_as_the_one_field_the_simulator_fills_in_for_a_tester() {
    DeclaredRead retrace =
        GrowReads.DECLARED.readNamed(GrowReads.A_CHARGE_ON_A_SAVED_CARD).orElseThrow();

    assertThat(retrace.fieldsItFillsInItself())
        .as(
            "the userId is the account's credential, so this read declares it as a value it fills"
                + " in itself and says in grow's own words where the value comes from")
        .containsExactly(entry(GrowFields.USER_ID, GrowReads.THE_MERCHANT_IS_THE_ACCOUNTS_OWN));
    assertThat(retrace.keys())
        .as("a field grow fills in itself is one no tester is asked to type")
        .doesNotContain(GrowFields.USER_ID);
    assertThat(retrace.theQuestionItAnswers())
        .as(
            "the sentence a tester reads to pick between questions says what this one answers,"
                + " and where a value it sends comes from is declared beside that value instead")
        .doesNotContain(GrowFields.USER_ID);
  }

  @Test
  void an_answer_a_scenario_gave_instead_of_grow_is_reported_as_the_rule_that_gave_it() {
    GrowOperationResult preempted =
        new GrowOperationResult(
            ProviderOutcome.UNKNOWN, null, null, A_RULE, null, null, GrowJson.object());

    assertThat(GrowReads.outcomeOf(preempted))
        .isEqualTo(new ReadOutcome.AnsweredByAScenarioInstead(A_RULE));
  }

  @Test
  void a_refusal_grow_answered_is_reported_with_the_err_id_and_message_it_published() {
    GrowOperationResult refused =
        GrowOperationResult.failed(
            RefusalSubject.noObjectAnsweredTo(GrowFields.CARD_TOKEN),
            GrowEnvelope.failure(GrowErrors.unknownCardToken(), GrowScalarType.INTEGER));

    assertThat(GrowReads.outcomeOf(refused))
        .asInstanceOf(InstanceOfAssertFactories.type(ReadOutcome.RefusedByTheProvider.class))
        .satisfies(
            answered ->
                assertThat(answered.inItsOwnWords())
                    .as(
                        "a tester reads the id and the message grow publishes together, because"
                            + " the id alone is a number nobody has read that contract knows")
                    .isEqualTo("140 " + GrowErrors.unknownCardToken().message()));
  }

  @Test
  void the_rule_a_read_proceeded_under_is_carried_onto_the_answer() {
    GrowOperationResult answered =
        GrowOperationResult.succeededAbout(
                null, GrowEnvelope.success(noTransactions(), GrowScalarType.INTEGER))
            .proceededUnder(A_RULE);

    assertThat(GrowReads.outcomeOf(answered))
        .isEqualTo(new ReadOutcome.Answered(List.of(), A_RULE));
    assertThat(((ReadOutcome.Answered) GrowReads.outcomeOf(answered)).whatItAnsweredAboutTheQuery())
        .as(
            "grow answers the transactions list and nothing about the identifiers it was asked"
                + " with, so this read answers no fact about the question itself")
        .isEmpty();
  }

  @Test
  void a_row_grow_answered_is_read_as_facts_in_the_order_grow_answered_them() {
    List<AnsweredRow> rows =
        GrowReads.theRowsOf(
            GrowEnvelope.success(
                GrowJson.object(
                    GrowResponseFields.TRANSACTIONS,
                    GrowJson.array(
                        List.of(
                            GrowJson.object(
                                GrowResponseFields.STATUS,
                                GrowJson.text("paid"),
                                GrowResponseFields.TRANSACTION_ID,
                                GrowJson.number(100003))))),
                GrowScalarType.INTEGER));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).providerObjectId())
        .as("a row names the charge grow answered it under, so a screen can link to it")
        .isEqualTo("100003");
    assertThat(rows.get(0).facts())
        .as(
            "grow answers every member of a retraced charge as one value, so every fact of the"
                + " row carries that one value under the name grow answered it under")
        .containsExactly(
            new AnsweredRow.Fact(GrowResponseFields.STATUS, "paid"),
            new AnsweredRow.Fact(GrowResponseFields.TRANSACTION_ID, "100003"));
    assertThat(rows.get(0).facts().getFirst().value())
        .isEqualTo(new AnsweredRow.AnsweredValue.TheValueItAnswered("paid"));
  }

  @Test
  void a_row_grow_answered_without_a_transaction_id_names_no_object_and_still_prints() {
    List<AnsweredRow> rows =
        GrowReads.theRowsOf(
            GrowEnvelope.success(
                GrowJson.object(
                    GrowResponseFields.TRANSACTIONS,
                    GrowJson.array(
                        List.of(
                            GrowJson.object(GrowResponseFields.STATUS, GrowJson.text("paid"))))),
                GrowScalarType.INTEGER));

    assertThat(rows.get(0).providerObjectId()).isNull();
    assertThat(rows.get(0).facts())
        .containsExactly(new AnsweredRow.Fact(GrowResponseFields.STATUS, "paid"));
  }

  @Test
  void an_envelope_carrying_no_transactions_list_is_refused_rather_than_printed_as_no_rows() {
    GrowJson somethingElse =
        GrowEnvelope.success(
            GrowJson.object("url", GrowJson.text("https://x")), GrowScalarType.INTEGER);

    assertThatThrownBy(() -> GrowReads.theRowsOf(somethingElse))
        .as(
            "an envelope of another shape reaching here is a change to the one place this"
                + " simulator builds this answer in, and no rows is a different fact from no"
                + " answer")
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(GrowReads.THE_ANSWER_OF_THIS_READ_IS_A_LIST_OF_TRANSACTIONS);
  }

  @Test
  void a_failure_envelope_is_never_read_for_rows_because_its_data_is_a_bare_string() {
    GrowJson refused = GrowEnvelope.failure(GrowErrors.unknownCardToken(), GrowScalarType.INTEGER);

    assertThatThrownBy(() -> GrowReads.theRowsOf(refused))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(GrowReads.THE_ANSWER_OF_THIS_READ_IS_A_LIST_OF_TRANSACTIONS);
  }

  @Test
  void a_row_that_is_not_an_object_at_all_is_refused() {
    GrowJson answered =
        GrowEnvelope.success(
            GrowJson.object(
                GrowResponseFields.TRANSACTIONS, GrowJson.array(List.of(GrowJson.text("paid")))),
            GrowScalarType.INTEGER);

    assertThatThrownBy(() -> GrowReads.theRowsOf(answered))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(GrowReads.THE_ANSWER_OF_THIS_READ_IS_A_LIST_OF_TRANSACTIONS);
  }

  @Test
  void a_member_holding_a_shape_of_its_own_is_refused_rather_than_printed_as_one_value() {
    GrowJson answered =
        GrowEnvelope.success(
            GrowJson.object(
                GrowResponseFields.TRANSACTIONS,
                GrowJson.array(
                    List.of(
                        GrowJson.object(
                            GrowResponseFields.CUSTOM_FIELD,
                            GrowJson.object("cField2", GrowJson.text("x")))))),
            GrowScalarType.INTEGER);

    assertThatThrownBy(() -> GrowReads.theRowsOf(answered))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(GrowReads.EVERY_MEMBER_OF_A_RETRACED_CHARGE_IS_A_SINGLE_VALUE);
  }

  @Test
  void a_member_holding_a_list_of_its_own_is_refused_too() {
    GrowJson answered =
        GrowEnvelope.success(
            GrowJson.object(
                GrowResponseFields.TRANSACTIONS,
                GrowJson.array(
                    List.of(
                        GrowJson.object(
                            GrowResponseFields.CUSTOM_FIELD, GrowJson.array(List.of()))))),
            GrowScalarType.INTEGER);

    assertThatThrownBy(() -> GrowReads.theRowsOf(answered))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(GrowReads.EVERY_MEMBER_OF_A_RETRACED_CHARGE_IS_A_SINGLE_VALUE);
  }

  private static GrowJson noTransactions() {
    return GrowJson.object(GrowResponseFields.TRANSACTIONS, GrowJson.array(List.of()));
  }

  private static ReadRequest theQuestionAskedWith(String cardToken, String myOwnIdentifier) {
    Map<String, String> asked = new LinkedHashMap<>();
    asked.put(GrowFields.CARD_TOKEN, cardToken);
    asked.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, myOwnIdentifier);
    return ReadRequest.of(
        RUN, MERCHANT, GrowReads.A_CHARGE_ON_A_SAVED_CARD, asked, ObjectOrigin.UI);
  }
}
