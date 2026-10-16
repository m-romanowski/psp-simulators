package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TranzilaLookupsTest {

  private static final String APP_KEY = "key-account-1";

  private static final String SECRET = "secret-account-1";

  private static final String SECONDS = Long.toString(TranzilaWiring.NOW.getEpochSecond());

  private static final String THE_DAY =
      ",\"transaction_start_date\":\"2026-10-13\",\"transaction_end_date\":\"2026-10-13\"";

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void a_create_keeps_every_user_defined_field_in_order_and_every_item_as_charged() {
    wired.answered(
        "{\"terminal_name\":\"myterminal\",\"card_number\":\""
            + TranzilaWire.A_VISA
            + "\",\"expire_month\":7,\"expire_year\":2030,\"items\":["
            + "{\"name\":\"shirt\",\"units_number\":2,\"unit_price\":1E1},"
            + "{\"name\":\"net\",\"units_number\":1,\"unit_price\":10,\"price_type\":\"N\","
            + "\"vat_percent\":17,\"type\":\"S\"},"
            + "{\"name\":\"gross\",\"units_number\":1,\"unit_price\":2.5,\"price_type\":\"G\"}],"
            + "\"user_defined_fields\":[{\"name\":\"orderid\",\"value\":\"ORD-1\"},"
            + "{\"name\":\"DCdisable\",\"value\":\"dc-1\"},{\"name\":\"qty\",\"value\":3}]}");

    TranzilaRecorded recorded = wired.transactions.recordedOf(wired.theLastStored());

    assertThat(recorded.userDefinedFields())
        .containsExactly(
            new TranzilaUserField("orderid", "ORD-1"),
            new TranzilaUserField("DCdisable", "dc-1"),
            new TranzilaUserField("qty", "3"));
    assertThat(recorded.items())
        .containsExactly(
            new TranzilaRecorded.Item(1, new TranzilaItem("shirt", 2, "10", "I")),
            new TranzilaRecorded.Item(2, new TranzilaItem("net", 1, "11.70", "S")),
            new TranzilaRecorded.Item(3, new TranzilaItem("gross", 1, "2.5", "I")));
    assertThat(recorded.transaction().dcDisable()).isEqualTo("dc-1");
    assertThat(recorded.transaction().amount()).isEqualTo("34.20");
  }

  @Test
  void a_follow_on_without_items_keeps_none_and_a_duplicate_keeps_nothing_new() {
    String underTheKey = ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"k\"}]";
    String debit = TranzilaWire.aDebit().replace("}]}", "}]" + underTheKey + "}");
    wired.answered(debit);
    TranzilaTransaction charged = wired.theLastStored();
    wired.answered(debit);
    wired.answered(
        TranzilaWire.aCancel(charged.transactionId(), String.valueOf(charged.authNumber())));

    assertThat(wired.stored()).hasSize(2);
    assertThat(wired.transactions.recordedOf(charged).userDefinedFields())
        .containsExactly(new TranzilaUserField("DCdisable", "k"));
    assertThat(wired.transactions.recordedOf(wired.theLastStored()))
        .extracting(TranzilaRecorded::userDefinedFields, TranzilaRecorded::items)
        .containsExactly(List.of(), List.of());
  }

  @Test
  void a_lookup_of_the_api_answers_every_transaction_of_the_day_oldest_first() {
    wired.answered(TranzilaWire.aDebit());
    wired.answered(TranzilaWire.aJ5("7"));

    TranzilaApiResponse answered = lookup(TranzilaApiController.TRANSACTIONS, THE_DAY);

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.providerObjectId()).isNull();
    assertThat(bodyOf(answered))
        .startsWith("{\"transactions\":[{\"index\":\"10000001\",")
        .contains("\"amount\":\"1053\"")
        .contains("{\"index\":\"10000002\",")
        .contains("\"amount\":\"700\"")
        .endsWith("],\"user_defined\":[],\"total\":\"2\",\"rows\":2}");
  }

  @Test
  void a_report_answers_newest_first_with_items_when_detailed() {
    wired.answered(TranzilaWire.aDebit());
    wired.answered(TranzilaWire.aJ5("7"));

    String answered =
        bodyOf(lookup(TranzilaReportController.TRANSACTION, THE_DAY + ",\"detailed\":\"Y\""));

    assertThat(answered)
        .startsWith("{\"transactions\":[{\"index\":10000002,")
        .contains(
            "\"items\":[{\"item_id\":2,\"item_name\":\"a\",\"quantity\":\"1.00\",\"price\":700,"
                + "\"item_type\":\"I\"}]")
        .contains("{\"index\":10000001,")
        .endsWith("],\"user_defined\":[],\"total\":\"2\",\"rows\":2}");
  }

  @Test
  void a_lookup_by_index_answers_that_transaction_on_its_first_page_only() {
    wired.answered(TranzilaWire.aDebit());
    wired.answered(TranzilaWire.aJ5("7"));

    String first =
        bodyOf(lookup(TranzilaApiController.TRANSACTIONS, ",\"transaction_index\":10000002"));
    String second =
        bodyOf(
            lookup(
                TranzilaApiController.TRANSACTIONS, ",\"transaction_index\":10000002,\"page\":2"));
    String unknown =
        bodyOf(lookup(TranzilaReportController.TRANSACTION, ",\"transaction_index\":10000009"));

    assertThat(first)
        .startsWith("{\"transactions\":[{\"index\":\"10000002\",")
        .endsWith("\"total\":\"1\",\"rows\":1}");
    assertThat(second)
        .isEqualTo("{\"transactions\":[],\"user_defined\":[],\"total\":\"1\",\"rows\":0}");
    assertThat(unknown)
        .isEqualTo("{\"transactions\":[],\"user_defined\":[],\"total\":\"0\",\"rows\":0}");
  }

  @Test
  void the_slots_are_the_terminals_names_in_the_order_first_recorded() {
    wired.answered(
        TranzilaWire.aDebit()
            .replace(
                "}]}", "}],\"user_defined_fields\":[{\"name\":\"orderid\",\"value\":\"O-1\"}]}"));
    wired.clock.moveTo(TranzilaWiring.NOW.plusSeconds(61));
    wired.answered(
        TranzilaWire.aDebit()
            .replace(
                "}]}",
                "}],\"user_defined_fields\":[{\"name\":\"contact\",\"value\":\"Dana\"},"
                    + "{\"name\":\"orderid\",\"value\":\"O-2\"}]}"));

    String answered = bodyOf(lookup(TranzilaApiController.TRANSACTIONS, THE_DAY));

    assertThat(answered)
        .contains("\"user_defined_1\":\"O-1\",\"user_defined_2\":\"\",")
        .contains("\"user_defined_1\":\"O-2\",\"user_defined_2\":\"Dana\",")
        .contains(
            "\"user_defined\":[{\"TERMINAL_ID\":\"myterminal\",\"FIELD_ID\":\"2\","
                + "\"API_PARAMETER_NAME\":\"contact\",\"CAPTION\":\"contact\","
                + "\"DEFAULT_VALUE\":null,\"DISPLAY_ORDER\":null,\"MANDATORY\":\"N\","
                + "\"DATA_TYPE\":\"string\",\"UPDATE_DATE\":\"2026-10-13 12:01:01\"},"
                + "{\"TERMINAL_ID\":\"myterminal\",\"FIELD_ID\":\"1\","
                + "\"API_PARAMETER_NAME\":\"orderid\",");
  }

  @Test
  void a_body_that_is_no_json_object_is_refused_in_each_hosts_shape() {
    TranzilaApiResponse api = call(TranzilaApiController.TRANSACTIONS, Map.of(), "[]");
    TranzilaApiResponse report = call(TranzilaReportController.TRANSACTION, Map.of(), "nope");

    assertThat(api.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(api))
        .isEqualTo(
            "{\"error_code\":\"20001\",\"message\":\"Invalid JSON Format: the request body is not a"
                + " JSON object\"}");
    assertThat(report.httpStatus()).isEqualTo(400);
    assertThat(report.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(report))
        .isEqualTo(
            "{\"error\":\"Bad Request\",\"message\":\"The request body is not a JSON object.\"}");
  }

  @Test
  void a_body_outside_the_schema_is_refused_in_each_hosts_shape() {
    TranzilaApiResponse api =
        call(TranzilaApiController.TRANSACTIONS, Map.of(), "{\"terminal_name\":\"myterminal\"}");
    TranzilaApiResponse report =
        call(TranzilaReportController.TRANSACTION, Map.of(), "{\"terminal_name\":\"myterminal\"}");

    assertThat(api.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(api))
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: "
                + TranzilaLookup.ONE_OF_THEM
                + "\"}");
    assertThat(bodyOf(report))
        .isEqualTo(
            "{\"error\":\"Bad Request\",\"message\":\"" + TranzilaLookup.ONE_OF_THEM + "\"}");
  }

  @Test
  void a_lookup_naming_another_terminal_is_refused_as_the_api_and_the_report_host_refuse() {
    String another = "{\"terminal_name\":\"otherterminal\",\"transaction_index\":1}";

    TranzilaApiResponse api = call(TranzilaApiController.TRANSACTIONS, Map.of(), another);
    TranzilaApiResponse report = call(TranzilaReportController.TRANSACTION, Map.of(), another);

    assertThat(api.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(api))
        .isEqualTo(
            "{\"error_code\":\"20002\",\"message\":\"Authorization failed: terminal_name names a"
                + " terminal other than the one the X-tranzila-api-app-key belongs to\"}");
    assertThat(report.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(report))
        .isEqualTo(
            "{\"error\":\"Unauthorized\",\"message\":\"Invalid access token. Verify your HMAC"
                + " signature and that your timestamp is current. terminal_name names a terminal"
                + " other than the one the X-tranzila-api-app-key belongs to.\"}");
  }

  @Test
  void an_app_key_no_account_holds_is_refused_as_each_host_refuses() {
    Map<String, List<String>> unknown = Map.of(TranzilaFields.APP_KEY, List.of("nobody"));

    TranzilaApiResponse api =
        wired.api.transactions(
            TranzilaApiCall.of("POST", TranzilaApiController.TRANSACTIONS, unknown, "{}"));
    TranzilaApiResponse report =
        wired.api.report(
            TranzilaApiCall.of("POST", TranzilaReportController.TRANSACTION, unknown, "{}"));

    assertThat(bodyOf(api))
        .isEqualTo(
            "{\"error_code\":\"20000\",\"message\":\"Authentication failed: "
                + TranzilaApi.NO_ACCOUNT_ANSWERS_TO_THAT_APP_KEY
                + "\"}");
    assertThat(report.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(report))
        .isEqualTo(
            "{\"error\":\"Unauthorized\",\"message\":\""
                + TranzilaLookups.INVALID_ACCESS_TOKEN
                + " "
                + TranzilaApi.NO_ACCOUNT_ANSWERS_TO_THAT_APP_KEY
                + ".\"}");
  }

  @Test
  void the_report_host_accepts_a_nonce_again_and_the_api_spends_it() {
    wired.configuring(Map.of());
    String lookup = "{\"terminal_name\":\"myterminal\",\"transaction_index\":1}";

    TranzilaApiResponse reportFirst =
        call(TranzilaReportController.TRANSACTION, signed("n-1"), lookup);
    TranzilaApiResponse reportAgain =
        call(TranzilaReportController.TRANSACTION, signed("n-1"), lookup);
    TranzilaApiResponse apiFirst = call(TranzilaApiController.TRANSACTIONS, signed("n-2"), lookup);
    TranzilaApiResponse apiAgain = call(TranzilaApiController.TRANSACTIONS, signed("n-2"), lookup);
    TranzilaApiResponse reportUnsigned =
        call(TranzilaReportController.TRANSACTION, Map.of(), lookup);

    assertThat(reportFirst.httpStatus()).isEqualTo(200);
    assertThat(reportAgain.httpStatus()).isEqualTo(200);
    assertThat(apiFirst.httpStatus()).isEqualTo(200);
    assertThat(apiAgain.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(reportUnsigned))
        .isEqualTo(
            "{\"error\":\"Unauthorized\",\"message\":\""
                + TranzilaLookups.INVALID_ACCESS_TOKEN
                + " X-tranzila-api-request-time"
                + TranzilaApi.A_HEADER_IS_MISSING
                + ".\"}");
  }

  @Test
  void the_trail_records_a_lookup_both_ways_and_withholds_the_tokens_it_answered() {
    wired.answered(TranzilaWire.aDebit());
    String token = String.valueOf(wired.theLastStored().token());

    lookup(TranzilaReportController.TRANSACTION, THE_DAY);

    List<AuditRecord> lookedUp =
        wired.audit.of(TranzilaApi.TRANZILA, TranzilaWiring.RUN).stream()
            .filter(row -> TranzilaReportController.TRANSACTION.equals(row.path()))
            .toList();
    assertThat(lookedUp)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    assertThat(lookedUp.get(1).sanitizedBody())
        .containsEntry("transactions.credit_card_token", List.of("[redacted]"))
        .containsEntry("transactions.credit_card_owner_id", List.of("[redacted]"))
        .doesNotContainValue(List.of(token));
  }

  @Test
  void the_report_host_answers_a_fault_beside_its_route_in_its_own_shape() {
    ProviderWireAnswer notFound =
        wired.provider.theAnswerOffItsRoutes(
            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/tranzila/report/v2/transaction");
    ProviderWireAnswer wrongMethod =
        wired.provider.theAnswerOffItsRoutes(
            WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER, TranzilaReportController.TRANSACTION);
    ProviderWireAnswer failure =
        wired.provider.theAnswerOffItsRoutes(
            WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, TranzilaReportController.TRANSACTION);
    ProviderWireAnswer underTheApi =
        wired.provider.theAnswerOffItsRoutes(
            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/v1/transactions/x");

    ProviderWireAnswer.InThisProvidersOwnWords notFoundBody =
        (ProviderWireAnswer.InThisProvidersOwnWords) notFound;
    ProviderWireAnswer.InThisProvidersOwnWords wrongMethodBody =
        (ProviderWireAnswer.InThisProvidersOwnWords) wrongMethod;
    assertThat(notFoundBody.httpStatus()).isEqualTo(404);
    assertThat(notFoundBody.contentType()).isEqualTo("application/json");
    assertThat(new String(notFoundBody.body(), StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"error\":\"Not Found\",\"message\":\"The specified resource was not found\"}");
    assertThat(wrongMethodBody.httpStatus()).isEqualTo(405);
    assertThat(new String(wrongMethodBody.body(), StandardCharsets.UTF_8))
        .isEqualTo(
            "{\"error\":\"Method Not Allowed\",\"message\":\"This address does not answer this"
                + " HTTP method\"}");
    assertThat(failure)
        .isEqualTo(
            new ProviderWireAnswer.NothingThisContractPublishes(
                TranzilaWireFaults.WHAT_THE_REPORT_HOST_PUBLISHES_FOR_A_FAILURE_INSIDE_IT));
    assertThat(
            new String(
                ((ProviderWireAnswer.InThisProvidersOwnWords) underTheApi).body(),
                StandardCharsets.UTF_8))
        .isEqualTo("{\"error_code\":\"404\",\"message\":\"The specified resource was not found\"}");
  }

  @Test
  void pairing_publishes_the_report_host_beside_the_others() {
    assertThat(TranzilaPairing.PAIRING.settings())
        .extracting(setting -> setting.name() + " " + setting.value())
        .contains("TRANZILA_REPORT_BASE_URL AnAddressOfThisSimulator[path=/tranzila/report]");
  }

  @Test
  void a_page_charges_its_own_parameters_as_user_defined_fields_after_its_dc_disable() {
    LinkedHashMap<String, String> echoed = new LinkedHashMap<>();
    echoed.put("success_url_address", "https://shop.example/ok");
    echoed.put("orderid", "SHOP-77");
    echoed.put("DCdisable", "dc-page");
    echoed.put("MyID", "123456789");
    echoed.put("myid", "987654321");
    echoed.put("Card_Holder_Id", "111111111");
    echoed.put("contact", "Dana");
    echoed.put("pdesc", "shoes");
    echoed.put("lang", "il");
    echoed.put("nologo", "1");
    echoed.put("cred_type", "1");
    echoed.put("remarks", "gift");
    TranzilaPage page = TranzilaBrowserReturnTest.aPage(TranzilaTranmode.A, echoed, null);

    assertThat(
            TranzilaPageCharge.theUserDefinedFieldsOf(
                "dc-page", echoed, TranzilaPageCharge.THE_PARAMETERS_NO_SLOT_KEEPS))
        .containsExactly(
            new TranzilaUserField("DCdisable", "dc-page"),
            new TranzilaUserField("orderid", "SHOP-77"),
            new TranzilaUserField("contact", "Dana"),
            new TranzilaUserField("remarks", "gift"));
    assertThat(TranzilaPageCharge.callOf(page, TranzilaPageCards.thePublishedCard()).rawBody())
        .endsWith(
            ",\"user_defined_fields\":[{\"name\":\"orderid\",\"value\":\"SHOP-77\"},"
                + "{\"name\":\"contact\",\"value\":\"Dana\"},"
                + "{\"name\":\"remarks\",\"value\":\"gift\"}]}");
    assertThat(TranzilaPageCharge.THE_PARAMETERS_NO_SLOT_KEEPS)
        .containsAll(TranzilaPageRequest.NEVER_ECHOED)
        .containsAll(TranzilaPageCharge.THE_PARAMETERS_THE_PAGE_READS_OR_DISPLAYS)
        .doesNotContain("orderid", "contact", "email", "remarks");
    assertThat(TranzilaPageCharge.THE_PARAMETERS_THE_PAGE_READS_OR_DISPLAYS)
        .containsExactlyInAnyOrder(
            "sum",
            "currency",
            "tranmode",
            "cred_type",
            "success_url_address",
            "fail_url_address",
            "notify_url_address",
            "DCdisable",
            "thtk",
            "myid",
            "pdesc",
            "lang",
            "accessibility",
            "opensum",
            "trBgColor",
            "trTextColor",
            "trButtonColor",
            "buttonLabel",
            "hidesum",
            "nologo",
            "hide_cc",
            "apple_pay",
            "google_pay",
            "bit_pay",
            "ppnewwin",
            "recur_payments",
            "recur_start_date",
            "recur_sum",
            "recur_transaction",
            "npay",
            "fpay",
            "spay",
            "maxpay",
            "Ilang",
            "IMaam",
            "Z_field",
            "business_id",
            "json_purchase_data");
  }

  @Test
  void a_hosted_fields_charge_keeps_its_own_keys_as_user_defined_fields_but_no_setting_or_id() {
    LinkedHashMap<String, String> sent = new LinkedHashMap<>();
    sent.put("contact", "Noa");
    sent.put("notify_url", "https://shop.example/notify");
    sent.put("IDENTITY_NUMBER", "123456789");
    sent.put("myid", "123456789");
    sent.put("DCdisable", "dc-hf");
    sent.put("pdesc", "shoes");
    sent.put("force_challenge", "1");
    sent.put("orderid", "o-1");

    assertThat(
            TranzilaPageCharge.theUserDefinedFieldsOf(
                "dc-hf", sent, TranzilaHostedFieldsCharge.THE_KEYS_NO_SLOT_KEEPS))
        .containsExactly(
            new TranzilaUserField("DCdisable", "dc-hf"),
            new TranzilaUserField("contact", "Noa"),
            new TranzilaUserField("orderid", "o-1"));
    assertThat(TranzilaHostedFieldsCharge.THE_KEYS_NO_SLOT_KEEPS)
        .containsExactlyInAnyOrder(
            "DCdisable",
            "notify_url",
            "pdesc",
            "json_purchase_data",
            "force_txn_on_3ds_fail",
            "force_challenge");
    assertThat(
            TranzilaJsonWriter.toText(
                TranzilaPageCharge.asSent(List.of(new TranzilaUserField("a", "1")))))
        .isEqualTo("[{\"name\":\"a\",\"value\":\"1\"}]");
  }

  @Test
  void a_cancel_and_a_reversal_keep_no_items_even_when_sent_with_them() {
    wired.answered(TranzilaWire.aDebit());
    TranzilaTransaction charged = wired.theLastStored();
    wired.answered(
        TranzilaWire.aCancel(charged.transactionId(), String.valueOf(charged.authNumber()))
            .replace("}", "," + TranzilaWire.items("1") + "}"));
    TranzilaTransaction cancelled = wired.theLastStored();
    wired.answered(TranzilaWire.aJ5("5"));
    TranzilaTransaction held = wired.theLastStored();
    wired.answered(
        TranzilaWire.aReversal(held.transactionId(), String.valueOf(held.authNumber()))
            .replace("}", "," + TranzilaWire.items("5") + "}"));
    TranzilaTransaction reversed = wired.theLastStored();

    assertThat(cancelled.type()).isEqualTo(TranzilaTxnType.CANCEL);
    assertThat(reversed.type()).isEqualTo(TranzilaTxnType.REVERSAL);
    assertThat(wired.transactions.recordedOf(cancelled).items()).isEmpty();
    assertThat(wired.transactions.recordedOf(reversed).items()).isEmpty();
    assertThat(wired.transactions.recordedOf(charged).items()).hasSize(1);
  }

  @Test
  void an_api_create_keeps_no_identity_named_field_and_no_name_past_the_twenty_fifth() {
    StringBuilder fields = new StringBuilder("{\"name\":\"CVV\",\"value\":\"123\"}");
    for (int name = 1; name <= 26; name++) {
      fields
          .append(",{\"name\":\"n")
          .append(name)
          .append("\",\"value\":\"v")
          .append(name)
          .append("\"}");
    }
    wired.answered(
        TranzilaWire.aDebit().replace("}]}", "}],\"user_defined_fields\":[" + fields + "]}"));

    TranzilaRecorded recorded = wired.transactions.recordedOf(wired.theLastStored());

    assertThat(recorded.userDefinedFields()).hasSize(25);
    assertThat(recorded.userDefinedFields().get(0)).isEqualTo(new TranzilaUserField("n1", "v1"));
    assertThat(recorded.userDefinedFields())
        .doesNotContain(new TranzilaUserField("n26", "v26"), new TranzilaUserField("CVV", "123"));
    assertThat(wired.records.slotsOf(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT))
        .hasSize(25)
        .last()
        .isEqualTo(new TranzilaUserSlot(25, "n25", TranzilaWiring.NOW));
  }

  private TranzilaApiResponse lookup(String path, String criteria) {
    return call(path, Map.of(), "{\"terminal_name\":\"myterminal\"" + criteria + "}");
  }

  private TranzilaApiResponse call(String path, Map<String, List<String>> signed, String body) {
    Map<String, List<String>> headers = new LinkedHashMap<>(signed);
    headers.putIfAbsent(TranzilaFields.APP_KEY, List.of(APP_KEY));
    TranzilaApiCall call = TranzilaApiCall.of("POST", path, headers, body);
    return path.equals(TranzilaReportController.TRANSACTION)
        ? wired.api.report(call)
        : wired.api.transactions(call);
  }

  private static Map<String, List<String>> signed(String nonce) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(TranzilaFields.APP_KEY, List.of(APP_KEY));
    headers.put(TranzilaFields.REQUEST_TIME, List.of(SECONDS));
    headers.put(TranzilaFields.NONCE, List.of(nonce));
    headers.put(TranzilaFields.ACCESS_TOKEN, List.of(hmac(SECRET + SECONDS + nonce, APP_KEY)));
    return headers;
  }

  private static String hmac(String key, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException unavailable) {
      throw new AssertionError(unavailable);
    }
  }

  private static String bodyOf(TranzilaApiResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }
}
