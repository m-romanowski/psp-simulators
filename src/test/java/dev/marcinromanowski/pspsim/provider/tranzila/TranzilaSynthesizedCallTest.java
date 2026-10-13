package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaSynthesizedCallTest {

  private static final Instant AT = TranzilaWiring.NOW;

  private final TranzilaTerminal terminal =
      TranzilaDoubles.terminal(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, "myterminal");

  @Test
  void a_capture_asked_on_a_screen_is_a_force_of_the_j5_with_its_token_and_no_authorization() {
    TranzilaObject held =
        new TranzilaObject(
            new TranzilaPayment(TranzilaPaymentTest.verify(1, 5, AT.plusSeconds(60)), List.of()),
            TranzilaPaymentTest.verify(1, 5, AT.plusSeconds(60)));

    TranzilaApiCall call =
        TranzilaObjectActions.callOf(request(ObjectAction.CAPTURE, "40.5"), held, terminal, AT);

    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo("/v1/transaction/credit_card/create");
    assertThat(call.headers()).isEmpty();
    assertThat(call.rawBody())
        .isEqualTo(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"force\","
                + "\"card_number\":\"t000000000000014245\",\"expire_month\":7,\"expire_year\":30,"
                + "\"items\":[{\"name\":\"simulator\",\"units_number\":1,\"unit_price\":40.5}],"
                + "\"reference_txn_id\":1}");
  }

  @Test
  void a_cancel_asked_on_a_live_j5_is_its_reversal_with_its_authorization_number() {
    TranzilaObject held =
        new TranzilaObject(
            new TranzilaPayment(TranzilaPaymentTest.verify(1, 5, AT.plusSeconds(60)), List.of()),
            TranzilaPaymentTest.verify(1, 5, AT.plusSeconds(60)));

    assertThat(
            TranzilaObjectActions.callOf(request(ObjectAction.CANCEL, null), held, terminal, AT)
                .rawBody())
        .isEqualTo(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"reversal\",\"reference_txn_id\":1,"
                + "\"authorization_number\":\"1000001\"}");
  }

  @Test
  void a_cancel_asked_on_an_expired_j5_nothing_forced_cancels_the_j5_itself() {
    TranzilaTransaction expired = TranzilaPaymentTest.verify(1, 5, AT);
    TranzilaObject held = new TranzilaObject(new TranzilaPayment(expired, List.of()), expired);

    assertThat(
            TranzilaObjectActions.callOf(request(ObjectAction.CANCEL, null), held, terminal, AT)
                .rawBody())
        .contains("\"txn_type\":\"cancel\",\"reference_txn_id\":1,");
  }

  @Test
  void a_refund_asked_on_a_debit_is_a_credit_of_it_with_its_card_and_authorization_number() {
    TranzilaTransaction debit = TranzilaPaymentTest.debit(1, "000");
    TranzilaObject charged = new TranzilaObject(new TranzilaPayment(debit, List.of()), debit);

    assertThat(
            TranzilaObjectActions.callOf(request(ObjectAction.REFUND, "3"), charged, terminal, AT)
                .rawBody())
        .isEqualTo(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"credit\","
                + "\"card_number\":\"t000000000000014245\",\"expire_month\":7,\"expire_year\":30,"
                + "\"items\":[{\"name\":\"simulator\",\"units_number\":1,\"unit_price\":3}],"
                + "\"reference_txn_id\":1,\"authorization_number\":\"1000001\"}");
  }

  @Test
  void an_amount_that_is_no_number_is_sent_as_text_for_the_schema_to_refuse() {
    assertThat(TranzilaJsonWriter.toText(TranzilaObjectActions.itemsOf("abc")))
        .isEqualTo("[{\"name\":\"simulator\",\"units_number\":1,\"unit_price\":\"abc\"}]");
  }

  @Test
  void a_debit_started_on_a_screen_carries_every_value_the_tester_filled_in() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("amount", "10.53");
    values.put("txn_currency_code", "USD");
    values.put("card_number", "4580210000004245");
    values.put("expire_month", "7");
    values.put("expire_year", "2030");

    TranzilaApiCall call =
        TranzilaOrigination.callOf(
            OriginationRequest.of(
                TranzilaWiring.RUN,
                TranzilaWiring.ACCOUNT,
                TranzilaOrigination.A_DEBIT,
                values,
                ObjectOrigin.UI),
            terminal);

    assertThat(call.path()).isEqualTo("/v1/transaction/credit_card/create");
    assertThat(call.rawBody())
        .isEqualTo(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"debit\","
                + "\"card_number\":\"4580210000004245\",\"expire_month\":7,\"expire_year\":2030,"
                + "\"txn_currency_code\":\"USD\",\"items\":[{\"name\":\"simulator\","
                + "\"units_number\":1,\"unit_price\":10.53}]}");
  }

  @Test
  void a_j5_started_on_a_screen_is_a_verify_of_mode_5_and_names_no_currency_it_was_not_given() {
    TranzilaApiCall call =
        TranzilaOrigination.callOf(
            OriginationRequest.of(
                TranzilaWiring.RUN,
                TranzilaWiring.ACCOUNT,
                TranzilaOrigination.A_J5,
                Map.of(),
                ObjectOrigin.UI),
            terminal);

    assertThat(call.rawBody())
        .isEqualTo(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"verify\",\"verify_mode\":5,"
                + "\"card_number\":\"\",\"expire_month\":\"\",\"expire_year\":\"\","
                + "\"items\":[{\"name\":\"simulator\",\"units_number\":1,\"unit_price\":\"\"}]}");
  }

  @Test
  void a_stored_form_keeps_an_instant_in_utc_and_reads_nothing_back_as_nothing() {
    assertThat(TranzilaStoredForm.timestampOf(AT)).isEqualTo(AT.atOffset(ZoneOffset.UTC));
    assertThat(TranzilaStoredForm.timestampOf(null)).isNull();
    assertThat(TranzilaStoredForm.instantOf(OffsetDateTime.parse("2026-10-13T14:00:00+02:00")))
        .isEqualTo(AT);
    assertThat(TranzilaStoredForm.instantOf(null)).isNull();
    assertThatThrownBy(() -> TranzilaStoredForm.typeOf("sto"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sto");
  }

  @Test
  void one_row_written_is_a_stored_transaction_and_none_is_an_id_another_holds() {
    assertThat(TranzilaInsert.ofTheRowsWritten(1, 1)).isEqualTo(TranzilaInsert.STORED);
    assertThat(TranzilaInsert.ofTheRowsWritten(0, 1)).isEqualTo(TranzilaInsert.ID_TAKEN);
    assertThat(TranzilaInsert.ofTheRowsWritten(0, 0))
        .isEqualTo(TranzilaInsert.CLOSED_BY_ANOTHER_REQUEST_FIRST);
  }

  @Test
  void a_withheld_body_redacts_exactly_the_keys_the_trail_redacted_at_any_depth() {
    TranzilaJson body =
        TranzilaJsonReader.objectIn(
                "{\"a\":{\"Token\":\"t1\",\"keep\":1},\"list\":[{\"token\":\"t2\"}],"
                    + "\"empty\":[],\"mixed\":[\"x\",\"y\"]}")
            .orElseThrow();
    Map<String, List<String>> redacted = new LinkedHashMap<>();
    redacted.put("a.Token", List.of("[redacted]"));
    redacted.put("list.token", List.of("[redacted]"));
    redacted.put("empty", List.of());
    redacted.put("mixed", List.of("x", "[redacted]"));
    redacted.put("a.keep", List.of("1"));

    assertThat(TranzilaWithheld.theKeysItRedacted(redacted))
        .containsExactlyInAnyOrder("a.token", "list.token");
    assertThat(TranzilaWithheld.theTextOf(TranzilaBody.of(200, body), redacted))
        .isEqualTo(
            "{\"a\":{\"Token\":\"[redacted]\",\"keep\":1},\"list\":[{\"token\":\"[redacted]\"}],"
                + "\"empty\":[],\"mixed\":[\"x\",\"y\"]}");
    assertThat(TranzilaWithheld.theTextOf(TranzilaBody.nothingRendered(), redacted)).isEmpty();
  }

  @Test
  void a_transaction_id_is_never_drawn_as_zero() {
    IdGenerator zeros =
        new IdGenerator() {
          @Override
          public String hex(int length) {
            return "0".repeat(length);
          }

          @Override
          public String digits(int length) {
            return "0".repeat(length);
          }
        };

    assertThat(TranzilaIdentifiers.aTransactionId(zeros)).isEqualTo(1L);
    assertThat(TranzilaIdentifiers.aTransactionId(new TranzilaDoubles.CountingIds()))
        .isEqualTo(10000001L);
  }

  @Test
  void a_transaction_id_is_a_positive_integer_of_at_most_eighteen_digits() {
    assertThat(TranzilaIdentifiers.transactionIdIn("1")).contains(1L);
    assertThat(TranzilaIdentifiers.transactionIdIn("1".repeat(18))).isPresent();
    assertThat(TranzilaIdentifiers.transactionIdIn("1".repeat(19))).isEmpty();
    assertThat(TranzilaIdentifiers.transactionIdIn("0")).isEmpty();
    assertThat(TranzilaIdentifiers.transactionIdIn("-1")).isEmpty();
  }

  private static ObjectActionRequest request(ObjectAction action, String amount) {
    return ObjectActionRequest.of(TranzilaWiring.RUN, "1", action, amount, ObjectOrigin.UI);
  }
}
