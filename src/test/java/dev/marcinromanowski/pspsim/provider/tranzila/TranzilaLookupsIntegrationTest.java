package dev.marcinromanowski.pspsim.provider.tranzila;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class TranzilaLookupsIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-lookups-it");

  private static final Instant DAY_ONE = Instant.parse("2026-10-14T09:00:00Z");

  private static final Instant DAY_TWO = Instant.parse("2026-10-15T23:59:59Z");

  private static final String ACCOUNT = "account-1";

  private static final String TERMINAL = "lookupterminal";

  private static final String APP_KEY = "tranzila-lookups-it-app-key";

  private static final String APP_SECRET = "tranzila-lookups-it-app-secret";

  private static final String A_VISA = "4580210000004245";

  private static final String THE_PUBLISHED_CARD = "4907639999909022";

  private static final String CREATE = "/v1/transaction/credit_card/create";

  private static final String TRANSACTIONS = "/v1/transactions";

  private static final String REPORT = "/tranzila/report/v1/transaction";

  private static final String THE_FIRST_DAY =
      ",\"transaction_start_date\":\"2026-10-14\",\"transaction_end_date\":\"2026-10-14\"";

  private static final String BOTH_DAYS =
      ",\"transaction_start_date\":\"2026-10-14\",\"transaction_end_date\":\"2026-10-15\"";

  private static final AtomicInteger NONCES = new AtomicInteger();

  private static final Pattern A_HIDDEN_FIELD =
      Pattern.compile("<input type=\"hidden\" name=\"([^\"]*)\" value=\"([^\"]*)\">");

  private static final Pattern THE_PAYER_FORM =
      Pattern.compile(
          "action=\"(/tranzila/directng/" + TERMINAL + "/payer/directng_[0-9a-f]{24})\"");

  @LocalServerPort private int port;

  private RestClient http;
  private Instant runClock = DAY_ONE;
  private final HttpClient wire = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void configureTheTerminal() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configureAt(DAY_ONE);
  }

  @AfterEach
  void unconfigureTheTerminal() {
    reset();
    http.put()
        .uri("/__simulator/tranzila/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "accounts", List.of(), "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  void
      every_transaction_of_the_api_the_page_and_the_hosted_fields_is_found_on_the_day_it_was_made() {
    HttpResponse<String> debit =
        signed(
            CREATE,
            aDebitOf(
                String.join(
                    ",",
                    "\"items\":[{\"name\":\"shirt\",\"units_number\":2,\"unit_price\":10.5}]",
                    "\"user_defined_fields\":[{\"name\":\"orderid\",\"value\":\"ORD-1\"}",
                    "{\"name\":\"DCdisable\",\"value\":\"dc-1\"}]")));
    HttpResponse<String> held = signed(CREATE, aJ5Of("100"));
    String token = textIn(held.body(), "token");
    HttpResponse<String> forced =
        signed(
            CREATE,
            "{\"terminal_name\":\""
                + TERMINAL
                + "\",\"txn_type\":\"force\",\"reference_txn_id\":"
                + numberIn(held.body(), "transaction_id")
                + ",\"card_number\":\""
                + token
                + "\",\"expire_month\":7,\"expire_year\":2030,"
                + "\"items\":[{\"name\":\"Capture\",\"units_number\":1,\"unit_price\":60}]}");
    HttpResponse<String> credited =
        signed(
            CREATE,
            "{\"terminal_name\":\""
                + TERMINAL
                + "\",\"txn_type\":\"credit\",\"reference_txn_id\":"
                + numberIn(forced.body(), "transaction_id")
                + ",\"authorization_number\":\""
                + textIn(forced.body(), "auth_number")
                + "\",\"card_number\":\""
                + token
                + "\",\"expire_month\":7,\"expire_year\":2030,"
                + "\"items\":[{\"name\":\"refund\",\"units_number\":1,\"unit_price\":20}]}");
    HttpResponse<String> charged =
        signed(
            CREATE,
            aDebitOf("\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":5.25}]"));
    HttpResponse<String> cancelled =
        signed(
            CREATE,
            "{\"terminal_name\":\""
                + TERMINAL
                + "\",\"txn_type\":\"cancel\",\"reference_txn_id\":"
                + numberIn(charged.body(), "transaction_id")
                + ",\"authorization_number\":\""
                + textIn(charged.body(), "auth_number")
                + "\"}");
    HttpResponse<String> heldAgain = signed(CREATE, aJ5Of("30"));
    HttpResponse<String> reversed =
        signed(
            CREATE,
            "{\"terminal_name\":\""
                + TERMINAL
                + "\",\"txn_type\":\"reversal\",\"reference_txn_id\":"
                + numberIn(heldAgain.body(), "transaction_id")
                + ",\"authorization_number\":\""
                + textIn(heldAgain.body(), "auth_number")
                + "\"}");
    String paid = aPagePayment();
    HttpResponse<String> hostedFields = aHostedFieldsCharge();
    configureAt(DAY_TWO);
    HttpResponse<String> nextDay =
        signed(
            CREATE, aDebitOf("\"items\":[{\"name\":\"b\",\"units_number\":1,\"unit_price\":1}]"));

    JsonNode firstDay = read(signed(TRANSACTIONS, aLookup(THE_FIRST_DAY)));
    JsonNode bothDays = read(signed(REPORT, aLookup(BOTH_DAYS + ",\"detailed\":\"Y\"")));

    assertThat(firstDay.at("/total").asString()).isEqualTo("10");
    assertThat(firstDay.at("/rows").asInt()).isEqualTo(10);
    assertThat(column(firstDay, "index"))
        .containsExactly(
            numberIn(debit.body(), "transaction_id"),
            numberIn(held.body(), "transaction_id"),
            numberIn(forced.body(), "transaction_id"),
            numberIn(credited.body(), "transaction_id"),
            numberIn(charged.body(), "transaction_id"),
            numberIn(cancelled.body(), "transaction_id"),
            numberIn(heldAgain.body(), "transaction_id"),
            numberIn(reversed.body(), "transaction_id"),
            paid,
            textIn(hostedFields.body(), "transaction_id"));
    assertThat(column(firstDay, "tranmode"))
        .containsExactly("A", "V", "F", "C", "A", "D", "V", "", "A", "A");
    assertThat(column(firstDay, "txn_type"))
        .containsExactly(
            "debit",
            "verify",
            "force",
            "credit",
            "debit",
            "cancel",
            "verify",
            "reversal",
            "debit",
            "debit");
    assertThat(column(firstDay, "amount"))
        .containsExactly(
            "2100", "10000", "6000", "2000", "525", "525", "3000", "3000", "1050", "4200");
    assertThat(column(firstDay, "processor_response_code"))
        .containsExactly("000", "777", "000", "000", "000", "000", "777", "000", "000", "000");
    assertThat(column(firstDay, "transtatus")).containsOnly("1");
    assertThat(column(firstDay, "transaction_date")).containsOnly("2026-10-14");
    assertThat(column(firstDay, "transaction_time")).containsOnly("09:00:00");
    assertThat(column(firstDay, "currency")).containsOnly("1");
    assertThat(column(firstDay, "child_terminal")).containsOnly(TERMINAL);
    assertThat(column(firstDay, "user_defined_1"))
        .containsExactly("ORD-1", "", "", "", "", "", "", "", "SHOP-77", "");
    assertThat(column(firstDay, "user_defined_2"))
        .containsExactly("dc-1", "", "", "", "", "", "", "", "", "");
    assertThat(column(firstDay, "user_defined_3"))
        .containsExactly("", "", "", "", "", "", "", "", "Dana Levi", "Noa Cohen");
    JsonNode first = firstDay.at("/transactions/0");
    assertThat(first.at("/credit_card_token").asString()).isEqualTo(textIn(debit.body(), "token"));
    assertThat(first.at("/expiration_month").asString()).isEqualTo("07");
    assertThat(first.at("/expiration_year").asString()).isEqualTo("30");
    assertThat(first.at("/authorization_number").asString())
        .isEqualTo(textIn(debit.body(), "auth_number"));
    assertThat(first.at("/card_brand").asString()).isEqualTo("2");
    assertThat(first.at("/user_defined_20").asString()).isEmpty();
    assertThat(first.has("user_defined_21")).isFalse();
    assertThat(first.has("items")).isFalse();
    assertThat(firstDay.at("/user_defined").toString())
        .isEqualTo(
            "[{\"TERMINAL_ID\":\""
                + TERMINAL
                + "\",\"FIELD_ID\":\"3\",\"API_PARAMETER_NAME\":\"contact\",\"CAPTION\":\"contact\","
                + "\"DEFAULT_VALUE\":null,\"DISPLAY_ORDER\":null,\"MANDATORY\":\"N\","
                + "\"DATA_TYPE\":\"string\",\"UPDATE_DATE\":\"2026-10-14 09:00:00\"},"
                + "{\"TERMINAL_ID\":\""
                + TERMINAL
                + "\",\"FIELD_ID\":\"2\",\"API_PARAMETER_NAME\":\"DCdisable\",\"CAPTION\":"
                + "\"DCdisable\",\"DEFAULT_VALUE\":null,\"DISPLAY_ORDER\":null,\"MANDATORY\":\"N\","
                + "\"DATA_TYPE\":\"string\",\"UPDATE_DATE\":\"2026-10-14 09:00:00\"},"
                + "{\"TERMINAL_ID\":\""
                + TERMINAL
                + "\",\"FIELD_ID\":\"1\",\"API_PARAMETER_NAME\":\"orderid\",\"CAPTION\":\"orderid\","
                + "\"DEFAULT_VALUE\":null,\"DISPLAY_ORDER\":null,\"MANDATORY\":\"N\","
                + "\"DATA_TYPE\":\"string\",\"UPDATE_DATE\":\"2026-10-14 09:00:00\"}]");

    assertThat(bothDays.at("/total").asString()).isEqualTo("11");
    JsonNode newest = bothDays.at("/transactions/0");
    assertThat(newest.at("/index").isNumber()).isTrue();
    assertThat(newest.at("/index").asString())
        .isEqualTo(numberIn(nextDay.body(), "transaction_id"));
    assertThat(newest.at("/transaction_date").asString()).isEqualTo("2026-10-15");
    assertThat(newest.at("/transaction_time").asString()).isEqualTo("23:59:59");
    assertThat(newest.at("/amount").isNumber()).isTrue();
    assertThat(newest.at("/amount").asInt()).isEqualTo(100);
    assertThat(newest.at("/transtatus").asInt()).isEqualTo(1);
    assertThat(newest.at("/bank").isNull()).isTrue();
    assertThat(newest.at("/user_defined_25").asString()).isEmpty();
    assertThat(column(bothDays, "txn_type"))
        .containsExactly(
            "DEBIT",
            "DEBIT",
            "DEBIT",
            "REVERSAL",
            "VERIFY",
            "CANCEL",
            "DEBIT",
            "CREDIT",
            "FORCE",
            "VERIFY",
            "DEBIT");
    JsonNode oldest = bothDays.at("/transactions/10");
    assertThat(oldest.at("/items").toString())
        .matches(
            "\\[\\{\"item_id\":[0-9]+,\"item_name\":\"shirt\",\"quantity\":\"2.00\","
                + "\"price\":1050,\"item_type\":\"I\"}]");
    assertThat(bothDays.at("/transactions/1/items").toString())
        .matches(
            "\\[\\{\"item_id\":[0-9]+,\"item_name\":\"simulator\",\"quantity\":\"1.00\","
                + "\"price\":4200,\"item_type\":\"I\"}]");
    assertThat(bothDays.at("/transactions/2/items/0/price").asInt()).isEqualTo(1050);
    assertThat(bothDays.at("/transactions/3/items").toString()).isEqualTo("[]");
    assertThat(bothDays.at("/user_defined/0/FIELD_ID").isNumber()).isTrue();
  }

  @Test
  void a_lookup_by_index_answers_that_one_transaction_and_an_index_no_transaction_holds_none() {
    signed(CREATE, aDebitOf("\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":1}]"));
    HttpResponse<String> held = signed(CREATE, aJ5Of("7.5"));
    String index = numberIn(held.body(), "transaction_id");

    JsonNode byIndex = read(signed(TRANSACTIONS, aLookup(",\"transaction_index\":" + index)));
    JsonNode reported = read(signed(REPORT, aLookup(",\"transaction_index\":" + index)));
    HttpResponse<String> nothing = signed(TRANSACTIONS, aLookup(",\"transaction_index\":1"));
    HttpResponse<String> nothingReported = signed(REPORT, aLookup(",\"transaction_index\":1"));

    assertThat(column(byIndex, "index")).containsExactly(index);
    assertThat(byIndex.at("/total").asString()).isEqualTo("1");
    assertThat(byIndex.at("/transactions/0/amount").asString()).isEqualTo("750");
    assertThat(reported.at("/transactions/0/txn_type").asString()).isEqualTo("VERIFY");
    assertThat(reported.at("/transactions/0/tranmode").asString()).isEqualTo("V");
    assertThat(nothing.statusCode()).isEqualTo(200);
    assertThat(nothing.body())
        .isEqualTo("{\"transactions\":[],\"user_defined\":[],\"total\":\"0\",\"rows\":0}");
    assertThat(nothingReported.body())
        .isEqualTo("{\"transactions\":[],\"user_defined\":[],\"total\":\"0\",\"rows\":0}");
  }

  @Test
  void a_report_pages_by_page_results_both_ways_and_the_api_pages_by_a_thousand() {
    List<String> made = new ArrayList<>();
    for (int at = 1; at <= 3; at++) {
      made.add(
          numberIn(
              signed(
                      CREATE,
                      aDebitOf(
                          "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":"
                              + at
                              + "}]"))
                  .body(),
              "transaction_id"));
    }

    JsonNode pageOne =
        read(signed(REPORT, aLookup(THE_FIRST_DAY + ",\"page_results\":2,\"page\":1")));
    JsonNode pageTwo =
        read(signed(REPORT, aLookup(THE_FIRST_DAY + ",\"page_results\":2,\"page\":2")));
    JsonNode oldestFirst =
        read(
            signed(
                REPORT,
                aLookup(THE_FIRST_DAY + ",\"page_results\":2,\"order_direction\":\"asc\"")));
    JsonNode apiPageOne = read(signed(TRANSACTIONS, aLookup(THE_FIRST_DAY)));
    JsonNode apiPageTwo = read(signed(TRANSACTIONS, aLookup(THE_FIRST_DAY + ",\"page\":2")));

    assertThat(column(pageOne, "index")).containsExactly(made.get(2), made.get(1));
    assertThat(pageOne.at("/total").asString()).isEqualTo("3");
    assertThat(pageOne.at("/rows").asInt()).isEqualTo(2);
    assertThat(column(pageTwo, "index")).containsExactly(made.get(0));
    assertThat(pageTwo.at("/rows").asInt()).isEqualTo(1);
    assertThat(column(oldestFirst, "index")).containsExactly(made.get(0), made.get(1));
    assertThat(column(apiPageOne, "index")).containsExactlyElementsOf(made);
    assertThat(column(apiPageTwo, "index")).isEmpty();
    assertThat(apiPageTwo.at("/total").asString()).isEqualTo("3");
    assertThat(apiPageTwo.at("/rows").asInt()).isZero();
  }

  @Test
  void a_range_takes_both_of_its_days_whole_and_a_range_ending_before_it_starts_finds_nothing() {
    signed(CREATE, aDebitOf("\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":1}]"));
    configureAt(DAY_TWO);
    signed(CREATE, aDebitOf("\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":2}]"));

    JsonNode both = read(signed(TRANSACTIONS, aLookup(BOTH_DAYS)));
    JsonNode secondDay =
        read(
            signed(
                TRANSACTIONS,
                aLookup(
                    ",\"transaction_start_date\":\"2026-10-15\","
                        + "\"transaction_end_date\":\"2026-10-15\"")));
    JsonNode reversed =
        read(
            signed(
                TRANSACTIONS,
                aLookup(
                    ",\"transaction_start_date\":\"2026-10-15\","
                        + "\"transaction_end_date\":\"2026-10-14\"")));

    assertThat(column(both, "amount")).containsExactly("100", "200");
    assertThat(column(secondDay, "amount")).containsExactly("200");
    assertThat(reversed.at("/total").asString()).isEqualTo("0");
  }

  @Test
  void
      the_api_refuses_a_wrong_token_and_a_reused_nonce_and_the_report_host_lets_a_nonce_be_reused() {
    String requestTime = Long.toString(DAY_ONE.getEpochSecond());
    String nonce = "reused-" + NONCES.incrementAndGet();
    String lookup = aLookup(THE_FIRST_DAY);

    HttpResponse<String> apiFirst =
        send(TRANSACTIONS, lookup, requestTime, nonce, tokenFor(requestTime, nonce));
    HttpResponse<String> apiAgain =
        send(TRANSACTIONS, lookup, requestTime, nonce, tokenFor(requestTime, nonce));
    String reportNonce = "report-" + NONCES.incrementAndGet();
    HttpResponse<String> reportFirst =
        send(REPORT, lookup, requestTime, reportNonce, tokenFor(requestTime, reportNonce));
    HttpResponse<String> reportAgain =
        send(REPORT, lookup, requestTime, reportNonce, tokenFor(requestTime, reportNonce));
    HttpResponse<String> apiWrong = send(TRANSACTIONS, lookup, requestTime, "w-1", "00");
    HttpResponse<String> reportWrong = send(REPORT, lookup, requestTime, "w-2", "00");
    HttpResponse<String> reportNoTerminal = signed(REPORT, "{\"transaction_index\":1}");
    HttpResponse<String> apiNoTerminal = signed(TRANSACTIONS, "{\"transaction_index\":1}");
    HttpResponse<String> reportFiltered =
        signed(
            REPORT,
            aLookup(
                THE_FIRST_DAY
                    + ",\"ufields\":[{\"name\":\"contact\",\"operator\":\"equals\","
                    + "\"value\":\"x\"}]"));

    assertThat(apiFirst.statusCode()).isEqualTo(200);
    assertThat(apiAgain.statusCode()).isEqualTo(401);
    assertThat(apiAgain.body())
        .isEqualTo(
            "{\"error_code\":\"20000\",\"message\":\"Authentication failed: "
                + TranzilaApi.THE_NONCE_WAS_USED.replace("\"", "\\\"")
                + "\"}");
    assertThat(reportFirst.statusCode()).isEqualTo(200);
    assertThat(reportAgain.statusCode()).isEqualTo(200);
    assertThat(apiWrong.statusCode()).isEqualTo(401);
    assertThat(apiWrong.body())
        .isEqualTo(
            "{\"error_code\":\"20000\",\"message\":\"Authentication failed: "
                + TranzilaApi.THE_ACCESS_TOKEN_DOES_NOT_MATCH
                + "tranzila.access-token\"}");
    assertThat(reportWrong.statusCode()).isEqualTo(401);
    assertThat(reportWrong.body())
        .isEqualTo(
            "{\"error\":\"Unauthorized\",\"message\":\"Invalid access token. Verify your HMAC"
                + " signature and that your timestamp is current. "
                + TranzilaApi.THE_ACCESS_TOKEN_DOES_NOT_MATCH
                + "tranzila.access-token.\"}");
    assertThat(reportNoTerminal.statusCode()).isEqualTo(400);
    assertThat(reportNoTerminal.body())
        .isEqualTo("{\"error\":\"Bad Request\",\"message\":\"terminal_name is required.\"}");
    assertThat(apiNoTerminal.statusCode()).isEqualTo(400);
    assertThat(apiNoTerminal.body())
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: terminal_name is"
                + " required.\"}");
    assertThat(reportFiltered.statusCode()).isEqualTo(400);
    assertThat(reportFiltered.body())
        .isEqualTo(
            "{\"error\":\"Bad Request\",\"message\":\"ufields" + TranzilaLookup.NOT_SERVED + "\"}");
  }

  @Test
  void a_client_that_lost_the_answer_to_a_charge_finds_the_charge_before_it_retries() {
    http.post()
        .uri("/__simulator/tranzila/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "match",
                Map.of("operation", "debit"),
                "action",
                Map.of("type", "TRANSPORT_FAULT", "fault", "SERVER_ERROR", "httpStatus", 503)))
        .retrieve()
        .toBodilessEntity();

    HttpResponse<String> lost =
        signed(
            CREATE,
            aDebitOf(
                String.join(
                    ",",
                    "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":99.9}]",
                    "\"user_defined_fields\":[{\"name\":\"orderid\",\"value\":\"ORD-LOST\"}]")));
    JsonNode found = read(signed(REPORT, aLookup(THE_FIRST_DAY)));

    assertThat(lost.statusCode()).isEqualTo(503);
    assertThat(lost.body()).doesNotContain("transaction_id");
    assertThat(found.at("/total").asString()).isEqualTo("1");
    assertThat(found.at("/transactions/0/user_defined_1").asString()).isEqualTo("ORD-LOST");
    assertThat(found.at("/transactions/0/processor_response_code").asString()).isEqualTo("000");
    assertThat(found.at("/transactions/0/amount").asInt()).isEqualTo(9990);
  }

  @Test
  void tranzila_answers_both_lookups_beside_its_create_and_the_neighbours_on_v1_answer_theirs() {
    HttpResponse<String> lookup = signed(TRANSACTIONS, aLookup(THE_FIRST_DAY));
    HttpResponse<String> underIt = post(TRANSACTIONS + "/more", "{}", Map.of());
    HttpResponse<String> wrongMethod = get(TRANSACTIONS);
    HttpResponse<String> create = post(CREATE, "{}", Map.of());
    HttpResponse<String> reportUnknown = post("/tranzila/report/v1/transactions", "{}", Map.of());
    HttpResponse<String> reportWrongMethod = get(REPORT);
    HttpResponse<String> stripe = post("/v1/payment_intents", "", Map.of());
    HttpResponse<String> autra = post("/v1/oauth/token", "", Map.of());

    assertThat(lookup.statusCode()).isEqualTo(200);
    assertThat(underIt.statusCode()).isEqualTo(404);
    assertThat(underIt.body())
        .isEqualTo("{\"error_code\":\"404\",\"message\":\"The specified resource was not found\"}");
    assertThat(wrongMethod.statusCode()).isEqualTo(405);
    assertThat(wrongMethod.body())
        .isEqualTo(
            "{\"error_code\":\"405\",\"message\":\"This address does not answer this HTTP method\"}");
    assertThat(create.statusCode()).isEqualTo(401);
    assertThat(create.body()).contains("\"error_code\":\"20000\"");
    assertThat(reportUnknown.statusCode()).isEqualTo(404);
    assertThat(reportUnknown.body())
        .isEqualTo(
            "{\"error\":\"Not Found\",\"message\":\"The specified resource was not found\"}");
    assertThat(reportWrongMethod.statusCode()).isEqualTo(405);
    assertThat(reportWrongMethod.body())
        .isEqualTo(
            "{\"error\":\"Method Not Allowed\",\"message\":\"This address does not answer this"
                + " HTTP method\"}");
    assertThat(stripe.statusCode()).isEqualTo(401);
    assertThat(stripe.body()).contains("\"error\"").doesNotContain("error_code");
    assertThat(autra.statusCode()).isNotEqualTo(404);
    assertThat(autra.body()).doesNotContain("error_code");
  }

  @Test
  void two_transactions_bringing_the_same_new_names_at_once_keep_one_numbering() throws Exception {
    for (int round = 0; round < 5; round++) {
      reset();
      configureAt(DAY_ONE);
      List<String> bodies = new ArrayList<>();
      for (int at = 0; at < 8; at++) {
        String alpha = "{\"name\":\"alpha\",\"value\":\"alpha-" + at + "\"}";
        String beta = "{\"name\":\"beta\",\"value\":\"beta-" + at + "\"}";
        bodies.add(
            aDebitOf(
                String.join(
                    ",",
                    "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":1}]",
                    "\"user_defined_fields\":["
                        + (at % 2 == 0 ? alpha + "," + beta : beta + "," + alpha)
                        + "]")));
      }

      List<HttpResponse<String>> answers = atOnce(bodies);
      JsonNode found = read(signed(TRANSACTIONS, aLookup(THE_FIRST_DAY)));

      assertThat(answers).allSatisfy(answer -> assertThat(answer.statusCode()).isEqualTo(200));
      Map<String, String> slotOf = new LinkedHashMap<>();
      for (JsonNode slot : found.at("/user_defined")) {
        slotOf.put(slot.at("/API_PARAMETER_NAME").asString(), slot.at("/FIELD_ID").asString());
      }
      assertThat(slotOf).as("round %s", round).containsOnlyKeys("alpha", "beta");
      assertThat(slotOf.values()).containsExactlyInAnyOrder("1", "2");
      assertThat(column(found, "user_defined_" + slotOf.get("alpha")))
          .hasSize(8)
          .allSatisfy(value -> assertThat(value).startsWith("alpha-"));
      assertThat(column(found, "user_defined_" + slotOf.get("beta")))
          .hasSize(8)
          .allSatisfy(value -> assertThat(value).startsWith("beta-"));
    }
  }

  @Test
  void an_identity_sent_under_any_case_reaches_no_slot_no_lookup_and_no_trail_row() {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("sum", "3");
    parameters.put("currency", "1");
    parameters.put("tranmode", "A");
    parameters.put("success_url_address", "https://shop.example/paid");
    parameters.put("orderid", "ORD-ID");
    parameters.put("MyID", "111111118");
    parameters.put("Card_Holder_Id", "222222226");
    parameters.put("identity_number", "333333334");
    parameters.put("CVV", "7x7x");
    HttpResponse<String> page =
        get("/tranzila/directng/" + TERMINAL + "/iframenew.php?" + encoded(parameters));
    Matcher form = THE_PAYER_FORM.matcher(page.body());
    assertThat(form.find()).as(page.body()).isTrue();
    HttpResponse<String> returned =
        postForm(form.group(1), Map.of("card", "published-9022", "outcome", "PAID"));
    String thtk =
        textIn(
            signed("/v2/handshake/create", "{\"terminal_name\":\"" + TERMINAL + "\",\"sum\":4}")
                .body(),
            "thtk");
    Map<String, String> charge = new LinkedHashMap<>();
    charge.put("terminal_name", TERMINAL);
    charge.put("thtk", thtk);
    charge.put("amount", "4");
    charge.put("credit_card_number", THE_PUBLISHED_CARD);
    charge.put("expiry_month", "12");
    charge.put("expiry_year", "2030");
    charge.put("orderid", "ORD-HF");
    charge.put("IDENTITY_NUMBER", "555555552");
    charge.put("MyId", "666666660");
    charge.put("card_holder_id", "777777779");
    charge.put("Card_Number", "4580210000004245");
    HttpResponse<String> charged =
        postForm("/tranzila/hf/process/ajax_process_transaction", charge);

    String api = signed(TRANSACTIONS, aLookup(THE_FIRST_DAY)).body();
    String report = signed(REPORT, aLookup(THE_FIRST_DAY)).body();
    String trail =
        http.get()
            .uri("/__simulator/tranzila/audit?testRunId=" + RUN.value())
            .retrieve()
            .body(String.class);

    assertThat(returned.body()).contains("111111118");
    assertThat(charged.body()).contains("555555552");
    assertThat(api).contains("ORD-ID").contains("ORD-HF");
    for (String identity :
        List.of(
            "111111118",
            "222222226",
            "333333334",
            "7x7x",
            "555555552",
            "666666660",
            "777777779",
            "4580210000004245")) {
      assertThat(api).as(identity).doesNotContain(identity);
      assertThat(report).as(identity).doesNotContain(identity);
      assertThat(trail).as(identity).doesNotContain(identity);
    }
    assertThat(api)
        .doesNotContainIgnoringCase("\"API_PARAMETER_NAME\":\"myid\"")
        .doesNotContainIgnoringCase("\"API_PARAMETER_NAME\":\"identity_number\"");
  }

  @Test
  void an_identity_named_user_defined_field_keeps_its_value_out_of_every_trail_and_exchange() {
    HttpResponse<String> charged =
        signed(
            CREATE,
            aDebitOf(
                String.join(
                    ",",
                    "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":1}]",
                    "\"user_defined_fields\":[{\"name\":\"MyID\",\"value\":\"123456782\"}",
                    "{\"name\":\"orderid\",\"value\":\"ORD-VISIBLE\"}",
                    "{\"name\":\"DCdisable\",\"value\":\"dc-identity\"}]")));

    String api = signed(TRANSACTIONS, aLookup(THE_FIRST_DAY)).body();
    String report = signed(REPORT, aLookup(THE_FIRST_DAY)).body();
    String trail =
        http.get()
            .uri("/__simulator/tranzila/audit?testRunId=" + RUN.value())
            .retrieve()
            .body(String.class);
    String exchanges =
        http.get()
            .uri("/__simulator/tranzila/idempotency?testRunId=" + RUN.value())
            .retrieve()
            .body(String.class);

    assertThat(charged.body()).contains("123456782");
    assertThat(trail).doesNotContain("123456782").contains("ORD-VISIBLE").contains("[redacted]");
    assertThat(exchanges)
        .doesNotContain("123456782")
        .contains("ORD-VISIBLE")
        .contains("dc-identity");
    assertThat(api).doesNotContain("123456782").contains("ORD-VISIBLE");
    assertThat(report).doesNotContain("123456782").contains("ORD-VISIBLE");
  }

  private List<HttpResponse<String>> atOnce(List<String> bodies) throws Exception {
    ExecutorService senders = Executors.newFixedThreadPool(bodies.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<HttpResponse<String>>> sent = new ArrayList<>();
      for (String body : bodies) {
        sent.add(
            senders.submit(
                () -> {
                  start.await();
                  return signed(CREATE, body);
                }));
      }
      start.countDown();
      List<HttpResponse<String>> answers = new ArrayList<>();
      for (Future<HttpResponse<String>> answer : sent) {
        answers.add(answer.get(30, TimeUnit.SECONDS));
      }
      return answers;
    } finally {
      senders.shutdownNow();
    }
  }

  @Test
  void the_trail_records_both_lookups_and_withholds_every_token_they_answer() {
    HttpResponse<String> debit =
        signed(
            CREATE, aDebitOf("\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":1}]"));
    String token = textIn(debit.body(), "token");

    HttpResponse<String> api = signed(TRANSACTIONS, aLookup(THE_FIRST_DAY));
    HttpResponse<String> report = signed(REPORT, aLookup(THE_FIRST_DAY));
    String trail =
        http.get()
            .uri("/__simulator/tranzila/audit?testRunId=" + RUN.value())
            .retrieve()
            .body(String.class);
    String exchanges =
        http.get()
            .uri("/__simulator/tranzila/idempotency?testRunId=" + RUN.value())
            .retrieve()
            .body(String.class);

    assertThat(api.body()).contains(token);
    assertThat(report.body()).contains(token);
    assertThat(trail)
        .contains(TRANSACTIONS)
        .contains(REPORT)
        .contains("transactions.credit_card_token")
        .doesNotContain(token);
    assertThat(exchanges).doesNotContain(TRANSACTIONS).doesNotContain(REPORT);
  }

  private String aPagePayment() {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("sum", "10.5");
    parameters.put("currency", "1");
    parameters.put("tranmode", "A");
    parameters.put("success_url_address", "https://shop.example/paid");
    parameters.put("orderid", "SHOP-77");
    parameters.put("contact", "Dana Levi");
    parameters.put("myid", "123456789");
    HttpResponse<String> page =
        get("/tranzila/directng/" + TERMINAL + "/iframenew.php?" + encoded(parameters));
    Matcher form = THE_PAYER_FORM.matcher(page.body());
    assertThat(form.find()).as(page.body()).isTrue();
    HttpResponse<String> returned =
        postForm(form.group(1), Map.of("card", "published-9022", "outcome", "PAID"));
    Matcher field = A_HIDDEN_FIELD.matcher(returned.body());
    Map<String, String> fields = new LinkedHashMap<>();
    while (field.find()) {
      fields.put(field.group(1), field.group(2));
    }
    return String.valueOf(fields.get("transaction_id"));
  }

  private HttpResponse<String> aHostedFieldsCharge() {
    String thtk =
        textIn(
            signed("/v2/handshake/create", "{\"terminal_name\":\"" + TERMINAL + "\",\"sum\":42}")
                .body(),
            "thtk");
    Map<String, String> charge = new LinkedHashMap<>();
    charge.put("terminal_name", TERMINAL);
    charge.put("thtk", thtk);
    charge.put("amount", "42");
    charge.put("credit_card_number", THE_PUBLISHED_CARD);
    charge.put("expiry_month", "12");
    charge.put("expiry_year", "2030");
    charge.put("contact", "Noa Cohen");
    return postForm("/tranzila/hf/process/ajax_process_transaction", charge);
  }

  private static String aDebitOf(String rest) {
    return "{\"terminal_name\":\""
        + TERMINAL
        + "\",\"card_number\":\""
        + A_VISA
        + "\",\"expire_month\":7,\"expire_year\":2030,"
        + rest
        + "}";
  }

  private static String aJ5Of(String amount) {
    return "{\"terminal_name\":\""
        + TERMINAL
        + "\",\"txn_type\":\"verify\",\"verify_mode\":5,\"card_number\":\""
        + A_VISA
        + "\",\"expire_month\":7,\"expire_year\":2030,"
        + "\"items\":[{\"name\":\"hold\",\"units_number\":1,\"unit_price\":"
        + amount
        + "}]}";
  }

  private static String aLookup(String criteria) {
    return "{\"terminal_name\":\"" + TERMINAL + "\"" + criteria + "}";
  }

  private JsonNode read(HttpResponse<String> answered) {
    assertThat(answered.statusCode()).as(answered.body()).isEqualTo(200);
    return json.readTree(answered.body());
  }

  private static List<String> column(JsonNode answer, String field) {
    List<String> values = new ArrayList<>();
    for (JsonNode row : answer.at("/transactions")) {
      values.add(row.at("/" + field).asString());
    }
    return values;
  }

  private HttpResponse<String> signed(String path, String body) {
    String nonce = "nonce-" + NONCES.incrementAndGet();
    String requestTime = Long.toString(runClock.getEpochSecond());
    return send(path, body, requestTime, nonce, tokenFor(requestTime, nonce));
  }

  private static String tokenFor(String requestTime, String nonce) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec((APP_SECRET + requestTime + nonce).getBytes(UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(APP_KEY.getBytes(UTF_8)));
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private HttpResponse<String> send(
      String path, String body, String requestTime, String nonce, String accessToken) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-tranzila-api-app-key", APP_KEY);
    headers.put("X-tranzila-api-request-time", requestTime);
    headers.put("X-tranzila-api-nonce", nonce);
    headers.put("X-tranzila-api-access-token", accessToken);
    return post(path, body, headers);
  }

  private HttpResponse<String> post(String path, String body, Map<String, String> headers) {
    HttpRequest.Builder built =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    for (Map.Entry<String, String> header : headers.entrySet()) {
      built = built.header(header.getKey(), header.getValue());
    }
    return exchange(built.build());
  }

  private HttpResponse<String> postForm(String path, Map<String, String> form) {
    return exchange(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<String> get(String path) {
    return exchange(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build());
  }

  private HttpResponse<String> exchange(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private void configureAt(Instant now) {
    runClock = now;
    http.put()
        .uri("/__simulator/tranzila/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", now.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        ACCOUNT,
                        "configuration",
                        Map.of(
                            TranzilaTerminal.TERMINAL_NAME_PROPERTY, TERMINAL,
                            TranzilaTerminal.APP_KEY_PROPERTY, APP_KEY,
                            TranzilaTerminal.APP_SECRET_PROPERTY, APP_SECRET))),
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/tranzila/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private static String numberIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":([0-9]+)").matcher(json);
    if (!found.find()) {
      throw new AssertionError("no number " + field + " in " + json);
    }
    return found.group(1);
  }

  private static String textIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
    if (!found.find()) {
      throw new AssertionError("no text " + field + " in " + json);
    }
    return found.group(1);
  }
}
