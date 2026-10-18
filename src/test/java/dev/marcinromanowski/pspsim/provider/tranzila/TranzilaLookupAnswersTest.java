package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TranzilaLookupAnswersTest {

  private static final Instant MADE = Instant.parse("2026-10-13T07:05:09Z");

  private static final String EMPTY_SLOTS_OF_THE_API = emptySlots(1, 20);

  private static final String THE_ITEMS =
      String.join(
          ",",
          ",\"items\":[{\"item_id\":67099484",
          "\"item_name\":\"Capture\"",
          "\"quantity\":\"3.00\"",
          "\"price\":150",
          "\"item_type\":\"S\"}]}");

  @Test
  void a_row_of_the_api_answers_every_published_field_as_a_string_and_amounts_in_minor_units() {
    TranzilaRecorded row =
        new TranzilaRecorded(
            aTransaction(TranzilaTxnType.DEBIT, null, "000", "0000001", "t0000000000000a4245"),
            List.of(),
            List.of());

    assertThat(TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheApi(row, List.of())))
        .isEqualTo(
            "{\"index\":\"41044\",\"transaction_date\":\"2026-10-13\","
                + "\"transaction_time\":\"07:05:09\",\"amount\":\"1053\","
                + "\"credit_card_token\":\"t0000000000000a4245\",\"expiration_month\":\"07\","
                + "\"expiration_year\":\"30\",\"credit_card_owner_id\":\"\",\"card_type\":\"1\","
                + "\"card_description\":\"Visa\",\"card_brand\":\"2\",\"clearing_processor\":\"2\","
                + "\"stars\":\"0\",\"is_foreign\":\"0\",\"club_number\":\"\","
                + "\"authorization_number\":\"0000001\",\"bank\":\"\",\"bank_branch\":\"\","
                + "\"bank_account\":\"\",\"payment_plan\":\"1\",\"currency\":\"1\","
                + "\"number_of_payments\":\"0\",\"first_payment_amount\":\"0\","
                + "\"other_payment_amount\":\"0\",\"host_ip\":\"\","
                + "\"processor_response_code\":\"000\",\"tranmode\":\"A\",\"refnr\":\"\","
                + "\"tempref\":\"\",\"xid\":\"\",\"cavv\":\"\",\"eci\":\"\","
                + "\"child_terminal\":\"myterminal\",\"uid\":\"\",\"transtatus\":\"1\","
                + "\"telauthability\":\"1\",\"txn_type\":\"debit\",\"txn_payment_method\":\"CC\","
                + "\"processor\":\"SHVA\",\"broker\":\"\",\"broker_product\":\"\","
                + "\"pan_entry_mode\":\"\""
                + EMPTY_SLOTS_OF_THE_API
                + "}");
  }

  @Test
  void a_row_of_the_report_answers_the_published_types_and_its_items_when_detailed() {
    TranzilaRecorded row =
        new TranzilaRecorded(
            aTransaction(TranzilaTxnType.FORCE, null, "000", "0000002", "t0000000000000b4245"),
            List.of(),
            List.of(
                new TranzilaRecorded.Item(67099484, new TranzilaItem("Capture", 3, "1.5", "S"))));

    assertThat(
            TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheReport(row, List.of(), true)))
        .isEqualTo(
            "{\"index\":41044,\"transaction_date\":\"2026-10-13\","
                + "\"transaction_time\":\"07:05:09\",\"amount\":1053,"
                + "\"credit_card_token\":\"t0000000000000b4245\",\"expiration_month\":\"07\","
                + "\"expiration_year\":\"30\",\"credit_card_owner_id\":\"\",\"card_issuer\":1,"
                + "\"card_description\":\"Visa\",\"card_brand\":2,\"clearing_processor\":2,"
                + "\"is_foreign\":0,\"club_number\":\"\",\"authorization_number\":\"0000002\","
                + "\"bank\":null,\"bank_branch\":null,\"bank_account\":null,\"payment_plan\":1,"
                + "\"currency\":\"1\",\"number_of_payments\":0,\"first_payment_amount\":0,"
                + "\"other_payment_amount\":0,\"host_ip\":\"\",\"processor_response_code\":\"000\","
                + "\"tranmode\":\"F\",\"refnr\":\"\",\"tempref\":\"\",\"xid\":\"\",\"cavv\":\"\","
                + "\"eci\":\"\",\"child_terminal\":\"myterminal\",\"uid\":\"\",\"transtatus\":1,"
                + "\"telauthability\":1,\"txnfdid\":null,\"cancelfdid\":null,"
                + "\"txnfdnumber\":null,\"cancelfdnumber\":null,\"txn_payment_method\":\"CC\","
                + "\"txn_type\":\"FORCE\",\"pan_entry_mode\":\"\",\"processor\":\"SHVA\","
                + "\"broker\":\"\",\"broker_product\":\"\""
                + emptySlots(1, 25)
                + THE_ITEMS);
    assertThat(
            TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheReport(row, List.of(), false)))
        .doesNotContain("items")
        .endsWith(",\"user_defined_25\":\"\"}");
  }

  @Test
  void a_declined_transaction_has_no_token_no_authorization_and_a_failed_status() {
    TranzilaRecorded row =
        new TranzilaRecorded(
            aTransaction(TranzilaTxnType.DEBIT, null, "004", null, null), List.of(), List.of());

    String api = TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheApi(row, List.of()));
    String report =
        TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheReport(row, List.of(), true));

    assertThat(api)
        .contains("\"credit_card_token\":\"\"")
        .contains("\"authorization_number\":\"\"")
        .contains("\"processor_response_code\":\"004\"")
        .contains("\"transtatus\":\"0\"");
    assertThat(report).contains("\"transtatus\":0").contains("\"items\":[]");
  }

  @Test
  void each_kind_of_transaction_is_named_by_its_tranmode_and_its_type_as_the_tables_publish_them() {
    assertThat(kindOf(TranzilaTxnType.DEBIT, null)).isEqualTo("A debit DEBIT");
    assertThat(kindOf(TranzilaTxnType.VERIFY, 5)).isEqualTo("V verify VERIFY");
    assertThat(kindOf(TranzilaTxnType.VERIFY, 2)).isEqualTo("N j2 J2");
    assertThat(kindOf(TranzilaTxnType.FORCE, null)).isEqualTo("F force FORCE");
    assertThat(kindOf(TranzilaTxnType.CREDIT, null)).isEqualTo("C credit CREDIT");
    assertThat(kindOf(TranzilaTxnType.CANCEL, null)).isEqualTo("D cancel CANCEL");
    assertThat(kindOf(TranzilaTxnType.REVERSAL, null)).isEqualTo(" reversal REVERSAL");
  }

  @Test
  void the_currency_is_answered_by_its_numeric_code() {
    assertThat(currencyOf("ILS")).isEqualTo("1");
    assertThat(currencyOf("USD")).isEqualTo("2");
    assertThat(currencyOf("EUR")).isEqualTo("978");
    assertThat(currencyOf("GBP")).isEqualTo("826");
    assertThatThrownBy(() -> currencyOf("JPY"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("the store holds JPY as a currency, which no numeric code names");
  }

  @Test
  void an_amount_is_answered_in_minor_units_rounded_half_up() {
    assertThat(TranzilaAmounts.inMinorUnits("10.53")).isEqualTo("1053");
    assertThat(TranzilaAmounts.inMinorUnits("10.535")).isEqualTo("1054");
    assertThat(TranzilaAmounts.inMinorUnits("10.534")).isEqualTo("1053");
    assertThat(TranzilaAmounts.inMinorUnits("7")).isEqualTo("700");
    assertThat(TranzilaAmounts.inMinorUnits("0.5")).isEqualTo("50");
    assertThat(TranzilaLookupAnswers.twoDigits(3)).isEqualTo("03");
    assertThat(TranzilaLookupAnswers.twoDigits(12)).isEqualTo("12");
  }

  @Test
  void each_slot_answers_the_value_its_name_carried_and_a_name_sent_twice_answers_the_last() {
    TranzilaRecorded row =
        new TranzilaRecorded(
            aTransaction(TranzilaTxnType.DEBIT, null, "000", "0000001", null),
            List.of(
                new TranzilaUserField("orderid", "first"),
                new TranzilaUserField("contact", "Dana"),
                new TranzilaUserField("orderid", "last"),
                new TranzilaUserField("unslotted", "x"),
                new TranzilaUserField("late", "L")),
            List.of());
    List<TranzilaUserSlot> slots =
        List.of(
            new TranzilaUserSlot(1, "contact", MADE),
            new TranzilaUserSlot(2, "email", MADE),
            new TranzilaUserSlot(3, "orderid", MADE),
            new TranzilaUserSlot(21, "late", MADE));

    String api = TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheApi(row, slots));
    String report =
        TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheReport(row, slots, false));

    assertThat(api)
        .contains(
            "\"user_defined_1\":\"Dana\",\"user_defined_2\":\"\",\"user_defined_3\":\"last\","
                + "\"user_defined_4\":\"\"")
        .endsWith(",\"user_defined_20\":\"\"}")
        .doesNotContain(":\"x\"")
        .doesNotContain("user_defined_21");
    assertThat(report)
        .contains("\"user_defined_20\":\"\",\"user_defined_21\":\"L\",\"user_defined_22\":\"\"")
        .contains("\"user_defined_3\":\"last\"");
  }

  @Test
  void the_slots_are_described_highest_first_and_the_api_lists_only_the_twenty_it_answers() {
    List<TranzilaUserSlot> slots = new ArrayList<>();
    for (int slot = 1; slot <= 21; slot++) {
      slots.add(new TranzilaUserSlot(slot, "name" + slot, MADE.plusSeconds(slot)));
    }
    TranzilaLookupAnswers.Found found =
        new TranzilaLookupAnswers.Found("myterminal", List.of(), 0, slots, false);

    List<TranzilaJson> api = TranzilaLookupAnswers.theSlotsOf(found, 20, false);
    List<TranzilaJson> report = TranzilaLookupAnswers.theSlotsOf(found, 25, true);

    assertThat(api).hasSize(20);
    assertThat(TranzilaJsonWriter.toText(api.get(0)))
        .isEqualTo(
            "{\"TERMINAL_ID\":\"myterminal\",\"FIELD_ID\":\"20\",\"API_PARAMETER_NAME\":\"name20\","
                + "\"CAPTION\":\"name20\",\"DEFAULT_VALUE\":null,\"DISPLAY_ORDER\":null,"
                + "\"MANDATORY\":\"N\",\"DATA_TYPE\":\"string\","
                + "\"UPDATE_DATE\":\"2026-10-13 07:05:29\"}");
    assertThat(TranzilaJsonWriter.toText(api.get(19))).contains("\"FIELD_ID\":\"1\"");
    assertThat(report).hasSize(21);
    assertThat(TranzilaJsonWriter.toText(report.get(0)))
        .startsWith("{\"TERMINAL_ID\":\"myterminal\",\"FIELD_ID\":21,");
  }

  @Test
  void an_answer_carries_its_rows_the_slots_the_total_as_text_and_the_rows_counted() {
    TranzilaRecorded row =
        new TranzilaRecorded(
            aTransaction(TranzilaTxnType.DEBIT, null, "000", "0000001", null),
            List.of(),
            List.of());
    TranzilaLookupAnswers.Found found =
        new TranzilaLookupAnswers.Found(
            "myterminal", List.of(row), 54, List.of(new TranzilaUserSlot(1, "a", MADE)), false);
    TranzilaLookupAnswers.Found none =
        new TranzilaLookupAnswers.Found("myterminal", List.of(), 0, List.of(), true);

    assertThat(TranzilaJsonWriter.toText(TranzilaLookupAnswers.ofTheApi(found)))
        .startsWith("{\"transactions\":[{\"index\":\"41044\",")
        .contains("],\"user_defined\":[{\"TERMINAL_ID\":\"myterminal\",\"FIELD_ID\":\"1\",")
        .endsWith("}],\"total\":\"54\",\"rows\":1}");
    assertThat(TranzilaJsonWriter.toText(TranzilaLookupAnswers.ofTheReport(found)))
        .startsWith("{\"transactions\":[{\"index\":41044,")
        .contains("],\"user_defined\":[{\"TERMINAL_ID\":\"myterminal\",\"FIELD_ID\":1,")
        .endsWith("}],\"total\":\"54\",\"rows\":1}");
    assertThat(TranzilaJsonWriter.toText(TranzilaLookupAnswers.ofTheApi(none)))
        .isEqualTo("{\"transactions\":[],\"user_defined\":[],\"total\":\"0\",\"rows\":0}");
    assertThat(TranzilaJsonWriter.toText(TranzilaLookupAnswers.ofTheReport(none)))
        .isEqualTo("{\"transactions\":[],\"user_defined\":[],\"total\":\"0\",\"rows\":0}");
  }

  @Test
  void the_fields_and_items_read_back_are_handed_to_the_transaction_that_owns_them() {
    TranzilaTransaction one = aTransaction(TranzilaTxnType.DEBIT, null, "000", "0000001", null);
    TranzilaTransaction other =
        new TranzilaTransaction(
            one.testRunId(),
            41045,
            41045,
            one.accountId(),
            one.terminalName(),
            one.type(),
            null,
            null,
            "000",
            "1",
            "ILS",
            "0000003",
            null,
            one.card(),
            "0",
            "0",
            null,
            null,
            ObjectOrigin.PROVIDER_API,
            MADE,
            null);
    TranzilaItem shirt = new TranzilaItem("shirt", 1, "1", "I");

    List<TranzilaRecorded> assembled =
        TranzilaRecorded.assembled(
            List.of(other, one),
            List.of(
                new TranzilaRecorded.Owned<>(41044, new TranzilaUserField("a", "1")),
                new TranzilaRecorded.Owned<>(41044, new TranzilaUserField("b", "2")),
                new TranzilaRecorded.Owned<>(41045, new TranzilaUserField("c", "3"))),
            List.of(new TranzilaRecorded.Owned<>(41044, new TranzilaRecorded.Item(9, shirt))));

    assertThat(assembled)
        .containsExactly(
            new TranzilaRecorded(other, List.of(new TranzilaUserField("c", "3")), List.of()),
            new TranzilaRecorded(
                one,
                List.of(new TranzilaUserField("a", "1"), new TranzilaUserField("b", "2")),
                List.of(new TranzilaRecorded.Item(9, shirt))));
  }

  @Test
  void no_slot_keeps_a_name_the_trail_withholds_as_an_identity_whatever_its_case() {
    List<TranzilaUserField> sent =
        List.of(
            new TranzilaUserField("MyID", "1"),
            new TranzilaUserField("orderid", "2"),
            new TranzilaUserField("IDENTITY_NUMBER", "3"),
            new TranzilaUserField("Card_Number", "4"),
            new TranzilaUserField("cvv", "5"),
            new TranzilaUserField("card_holder_id", "6"),
            new TranzilaUserField("Credit_Card_Number", "7"),
            new TranzilaUserField("card_holder_id_number", "8"),
            new TranzilaUserField("DCdisable", "9"));

    assertThat(TranzilaUserField.theOnesASlotKeeps(sent))
        .containsExactly(
            new TranzilaUserField("orderid", "2"), new TranzilaUserField("DCdisable", "9"));
  }

  @Test
  void an_identity_named_user_defined_value_is_withheld_at_any_depth_and_every_other_kept() {
    TranzilaJson sent =
        TranzilaJsonReader.objectIn(
                "{\"user_defined_fields\":[{\"name\":\"MyID\",\"value\":\"1\"},"
                    + "{\"name\":\"orderid\",\"value\":\"2\"},{\"name\":\"CVV\"},7,"
                    + "{\"name\":3,\"value\":\"4\"}],\"original_request\":{"
                    + "\"user_defined_fields\":[{\"value\":5,\"name\":\"identity_number\"}],"
                    + "\"items\":[{\"user_defined_fields\":\"x\"}]},\"user_defined\":["
                    + "{\"name\":\"cvv\",\"value\":\"6\"}]}")
            .orElseThrow();

    assertThat(TranzilaJsonWriter.toText(TranzilaUserField.withTheIdentitiesWithheld(sent)))
        .isEqualTo(
            "{\"user_defined_fields\":[{\"name\":\"MyID\",\"value\":\"[redacted]\"},"
                + "{\"name\":\"orderid\",\"value\":\"2\"},{\"name\":\"CVV\"},7,"
                + "{\"name\":3,\"value\":\"4\"}],\"original_request\":{"
                + "\"user_defined_fields\":[{\"value\":\"[redacted]\","
                + "\"name\":\"identity_number\"}],\"items\":[{\"user_defined_fields\":\"x\"}]},"
                + "\"user_defined\":[{\"name\":\"cvv\",\"value\":\"6\"}]}");
    assertThat(TranzilaUserField.namesAnIdentity("Card_Holder_Id_Number")).isTrue();
    assertThat(TranzilaUserField.namesAnIdentity("card_holder")).isFalse();
  }

  @Test
  void the_slots_of_a_terminal_are_claimed_under_one_lock_of_its_run_and_account() {
    assertThat(TranzilaUserSlot.lockOf(TranzilaWiring.RUN, "a-1"))
        .isEqualTo((TranzilaWiring.RUN.value() + "\u0000a-1").hashCode())
        .isNotEqualTo(TranzilaUserSlot.lockOf(TranzilaWiring.RUN, "a-2"));
    assertThat(TranzilaUserSlot.LOCK_SPACE)
        .isEqualTo("tranzila user-defined slots".hashCode())
        .isNotEqualTo(TranzilaDcDisableKey.LOCK_SPACE);
    assertThat(TranzilaUserSlot.THE_MOST_SLOTS).isEqualTo(25);
  }

  private static String kindOf(TranzilaTxnType type, @Nullable Integer verifyMode) {
    TranzilaRecorded row =
        new TranzilaRecorded(
            aTransaction(type, verifyMode, "000", "0000001", null), List.of(), List.of());
    String api = TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheApi(row, List.of()));
    String report =
        TranzilaJsonWriter.toText(TranzilaLookupAnswers.aRowOfTheReport(row, List.of(), false));
    return between(api, "\"tranmode\":\"")
        + " "
        + between(api, "\"txn_type\":\"")
        + " "
        + between(report, "\"txn_type\":\"");
  }

  private static String currencyOf(String alpha) {
    TranzilaTransaction debit = aTransaction(TranzilaTxnType.DEBIT, null, "000", "0000001", null);
    return TranzilaLookupAnswers.theNumericCurrencyOf(
        new TranzilaTransaction(
            debit.testRunId(),
            debit.transactionId(),
            debit.rootTxnId(),
            debit.accountId(),
            debit.terminalName(),
            debit.type(),
            null,
            null,
            "000",
            "1",
            alpha,
            "0000001",
            null,
            debit.card(),
            "0",
            "0",
            null,
            null,
            ObjectOrigin.PROVIDER_API,
            MADE,
            null));
  }

  private static String between(String json, String opening) {
    int from = json.indexOf(opening) + opening.length();
    return json.substring(from, json.indexOf('"', from));
  }

  private static String emptySlots(int from, int to) {
    StringBuilder slots = new StringBuilder();
    for (int slot = from; slot <= to; slot++) {
      slots.append(",\"user_defined_").append(slot).append("\":\"\"");
    }
    return slots.toString();
  }

  private static TranzilaTransaction aTransaction(
      TranzilaTxnType type,
      @Nullable Integer verifyMode,
      String code,
      @Nullable String authNumber,
      @Nullable String token) {
    return new TranzilaTransaction(
        TranzilaWiring.RUN,
        41044,
        41044,
        TranzilaWiring.ACCOUNT,
        TranzilaWiring.TERMINAL,
        type,
        verifyMode,
        null,
        code,
        "10.53",
        "ILS",
        authNumber,
        token,
        new TranzilaCard("458021xxxxxx4245", TranzilaCard.VISA, 7, 30),
        "1",
        "0",
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        MADE,
        null);
  }
}
