package dev.marcinromanowski.pspsim.provider.autra;

import static dev.marcinromanowski.pspsim.provider.autra.AutraPreAuthorizationTest.theCodeIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraPaymentListingTest {

  private static final TestRunId RUN = TestRunId.of("autra-listing");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final int EIGHT_PAYMENTS = 8;

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments stored = new AutraDoubles.Payments();
  private final Map<String, Integer> paymentCalls = new LinkedHashMap<>();
  private final AutraPayments payments =
      AutraDoubles.counting(AutraPayments.class, stored, paymentCalls);
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final AutraApi api = apiFor();

  private AutraApi apiFor() {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, Map.of()));
    return AutraWiring.api(merchants, sessions, payments, audit, RUN, NOW, scenarios);
  }

  private AutraApiResponse list(String... query) {
    List<String> asked = new ArrayList<>(List.of(query));
    asked.add(AutraFields.DOCUMENT_ID);
    asked.add(DOCUMENT);
    return api.payments(
        AutraWire.get(
            AutraApiController.PAYMENTS,
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            AutraWire.query(asked.toArray(new String[0]))));
  }

  private AutraJson.JsonObject bodyOf(AutraApiResponse response) {
    return AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
  }

  private List<AutraJson> itemsOf(AutraApiResponse response) {
    return ((AutraJson.JsonArray) bodyOf(response).member(AutraFields.ITEMS)).elements();
  }

  private static List<String> theIdentifiersIn(List<AutraJson> items) {
    List<String> named = new ArrayList<>();
    for (AutraJson item : items) {
      named.add(((AutraJson.JsonObject) item).text("paymentID").orElseThrow());
    }
    return named;
  }

  private String aSaleOf(String amount) {
    return AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null).providerObjectId();
  }

  private String aHoldOf(String amount) {
    return AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null)
        .providerObjectId();
  }

  private String capture(String paymentId) {
    api.capture(
        AutraWire.call(
            AutraApiController.PAYMENTS + "/" + paymentId + "/capture",
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            "{\"transactionalToken\":\""
                + AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT)
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\"}"),
        paymentId);
    return paymentId;
  }

  @Test
  void the_envelope_carries_the_six_members_the_vendor_declares_in_that_order() {
    aSaleOf("500.00");

    AutraJson.JsonObject body = bodyOf(list());

    assertThat(body.members().keySet())
        .containsExactly("items", "page", "limit", "hasMore", "from", "to");
  }

  @Test
  void the_envelope_names_no_total_and_no_cursor_because_this_listing_is_page_based() {
    AutraJson.JsonObject body = bodyOf(list());

    assertThat(body.has("total")).isFalse();
    assertThat(body.has("nextCursor")).isFalse();
    assertThat(body.has("cursor")).isFalse();
  }

  @Test
  void an_item_is_what_a_lookup_answers_with_its_timeline_taken_off() {
    String paymentId =
        AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, "500.00", "pedido-abc-123")
            .providerObjectId();
    AutraJson.JsonObject lookedUp =
        AutraJsonReader.objectIn(
                AutraWire.bodyOf(
                    api.payment(
                        AutraWire.get(
                            AutraApiController.PAYMENTS + "/" + paymentId,
                            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
                            AutraWire.query(AutraFields.DOCUMENT_ID, DOCUMENT)),
                        paymentId)))
            .orElseThrow();

    AutraJson.JsonObject item = (AutraJson.JsonObject) itemsOf(list()).getFirst();

    List<String> withoutTheTimeline = new ArrayList<>(lookedUp.members().keySet());
    withoutTheTimeline.remove(AutraFields.EVENTS);
    assertThat(item.members().keySet())
        .as(
            "provider autra publishes an item as \"o mesmo formato de Get payment, sem events\","
                + " so the two shapes are one shape less one member and never two lists that"
                + " happen to agree")
        .containsExactlyElementsOf(withoutTheTimeline);
    assertThat(item.members().keySet())
        .containsExactly(
            "paymentID",
            "orderId",
            "type",
            "status",
            "voidRetryable",
            "amount",
            "refundedAmount",
            "currency",
            "documentId",
            "transactionType",
            "transactionMethod",
            "installments",
            "authorizationCode",
            "rrn",
            "trackingNumber",
            "createdAt",
            "updatedAt");
    assertThat(item.has("events")).isFalse();
    assertThat(item.has("brandReferenceId")).isFalse();
  }

  @Test
  void the_listing_answers_the_newest_payment_first() {
    String first = aSaleOf("100.00");
    String second = aSaleOf("200.00");
    String third = aSaleOf("300.00");

    assertThat(theIdentifiersIn(itemsOf(list()))).containsExactly(third, second, first);
  }

  @Test
  void a_call_that_names_no_window_is_answered_the_last_seven_days_and_told_which_they_were() {
    AutraJson.JsonObject body = bodyOf(list());

    assertThat(body.text("from")).contains("2026-09-15T10:00:01Z");
    assertThat(body.text("to")).contains("2026-09-22T10:00:01Z");
    assertThat(
            Duration.between(
                Instant.parse(body.text("from").orElseThrow()),
                Instant.parse(body.text("to").orElseThrow())))
        .isEqualTo(Duration.ofDays(7));
  }

  @Test
  void a_payment_made_at_the_frozen_moment_of_the_run_is_inside_the_window_a_call_gets() {
    String paymentId = aSaleOf("500.00");

    assertThat(theIdentifiersIn(itemsOf(list()))).containsExactly(paymentId);
  }

  @Test
  void the_window_a_call_names_is_echoed_back_as_the_one_that_was_applied() {
    AutraJson.JsonObject body =
        bodyOf(list("from", "2026-09-01T00:00:00Z", "to", "2026-09-10T00:00:00Z"));

    assertThat(body.text("from")).contains("2026-09-01T00:00:00Z");
    assertThat(body.text("to")).contains("2026-09-10T00:00:00Z");
  }

  @Test
  void a_bare_date_covers_the_whole_of_that_day_at_each_end_of_the_window() {
    String paymentId = aSaleOf("500.00");

    assertThat(theIdentifiersIn(itemsOf(list("from", "2026-09-22", "to", "2026-09-22"))))
        .containsExactly(paymentId);
    AutraJson.JsonObject body = bodyOf(list("from", "2026-09-22", "to", "2026-09-22"));
    assertThat(body.text("from")).contains("2026-09-22T00:00:00Z");
    assertThat(body.text("to")).contains("2026-09-23T00:00:00Z");
  }

  @Test
  void the_to_boundary_is_exclusive_and_the_from_boundary_is_not() {
    aSaleOf("500.00");

    assertThat(itemsOf(list("from", "2026-09-22T10:00:00Z", "to", "2026-09-22T10:00:00Z")))
        .isEmpty();
    assertThat(itemsOf(list("from", "2026-09-22T10:00:00Z", "to", "2026-09-22T10:00:01Z")))
        .hasSize(1);
    assertThat(itemsOf(list("from", "2026-09-22T10:00:01Z", "to", "2026-09-22T11:00:00Z")))
        .isEmpty();
  }

  @Test
  void a_payment_outside_the_window_is_left_out() {
    aSaleOf("500.00");

    assertThat(itemsOf(list("from", "2026-08-01", "to", "2026-08-02"))).isEmpty();
  }

  @Test
  void a_from_that_is_no_moment_at_all_is_answered_400_invalid_from() {
    AutraApiResponse response = list("from", "last tuesday");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_FROM);
  }

  @Test
  void a_to_that_is_no_moment_at_all_is_answered_400_invalid_to() {
    AutraApiResponse response = list("to", "2026-13-45");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_TO);
  }

  @Test
  void a_window_that_runs_backwards_is_answered_400_invalid_range() {
    AutraApiResponse response = list("from", "2026-09-20T00:00:00Z", "to", "2026-09-10T00:00:00Z");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_RANGE);
  }

  @Test
  void a_window_of_exactly_thirty_one_days_is_taken_and_one_longer_is_answered_range_too_large() {
    assertThat(list("from", "2026-08-22T10:00:00Z", "to", "2026-09-22T10:00:00Z").httpStatus())
        .isEqualTo(200);

    AutraApiResponse longer = list("from", "2026-08-22T09:59:59Z", "to", "2026-09-22T10:00:00Z");

    assertThat(longer.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(longer)).isEqualTo(AutraErrors.RANGE_TOO_LARGE);
  }

  @Test
  void the_thirty_one_day_maximum_is_the_number_the_vendor_publishes() {
    assertThat(AutraPaymentWindow.LONGEST_WINDOW_IN_DAYS).isEqualTo(31);
    assertThat(AutraPaymentWindow.THE_WINDOW_A_CALL_THAT_NAMES_NONE_GETS).isEqualTo(7);
  }

  @Test
  void a_status_filter_selects_the_payments_holding_one_of_the_statuses_it_names() {
    aSaleOf("100.00");
    aHoldOf("200.00");
    String taken = capture(aHoldOf("300.00"));

    assertThat(theIdentifiersIn(itemsOf(list("status", "CAPTURED")))).containsExactly(taken);
  }

  @Test
  void a_status_filter_is_a_comma_separated_list_and_selects_every_status_in_it() {
    String sale = aSaleOf("100.00");
    String hold = aHoldOf("200.00");
    String taken = capture(aHoldOf("300.00"));

    assertThat(theIdentifiersIn(itemsOf(list("status", "ACCEPTED,CAPTURED"))))
        .containsExactly(taken, hold, sale);
  }

  @Test
  void the_query_autra_publishes_for_finding_open_reservations_finds_exactly_those() {
    String sale = aSaleOf("100.00");
    String hold = aHoldOf("200.00");
    String taken = capture(aHoldOf("300.00"));

    assertThat(theIdentifiersIn(itemsOf(list("status", "ACCEPTED", "type", "PREAUTH"))))
        .as(
            "autra names this query as the purpose of the route: \"descobrir quais"
                + " pre-autorizacoes ainda estao abertas (?status=ACCEPTED&type=PREAUTH)\"")
        .containsExactly(hold);
    assertThat(theIdentifiersIn(itemsOf(list("status", "ACCEPTED"))))
        .as("a sale answers the same word, so the status alone cannot separate the two")
        .containsExactly(hold, sale);
    assertThat(theIdentifiersIn(itemsOf(list("status", "ACCEPTED", "type", "SALE"))))
        .containsExactly(sale);
    assertThat(theIdentifiersIn(itemsOf(list("type", "PREAUTH")))).containsExactly(taken, hold);
  }

  @Test
  void every_one_of_the_seventeen_the_vendor_names_is_taken_and_an_eighteenth_is_not() {
    List<String> named =
        List.of(
            "PENDING",
            "ACCEPTED",
            "AUTHORIZED",
            "APPROVED",
            "CAPTURED",
            "VOIDED",
            "PARTIALLY_VOIDED",
            "REFUND_PENDING",
            "PARTIAL_REFUND_PENDING",
            "REFUNDED",
            "SETTLED",
            "PAID",
            "NOT_FOUND",
            "FAILED",
            "ERROR",
            "EXPIRED",
            "DECLINED");

    assertThat(named).hasSize(17);
    for (String status : named) {
      assertThat(list("status", status).httpStatus()).as(status).isEqualTo(200);
    }
    assertThat(list("status", "PRE_AUTHORIZED").httpStatus())
        .as(
            "the vendor's guide names PRE_AUTHORIZED but this route does not, and autra publishes"
                + " \"Valor desconhecido -> 400 INVALID_STATUS\" for a value it does not name")
        .isEqualTo(400);
    assertThat(list("status", String.join(",", named) + ",PRE_AUTHORIZED").httpStatus())
        .isEqualTo(400);
  }

  @Test
  void every_status_a_payment_of_this_simulator_can_hold_is_one_this_filter_takes() {
    List<String> held =
        List.of(
            AutraStatus.ACCEPTED,
            AutraStatus.CAPTURED,
            AutraStatus.VOIDED,
            AutraStatus.PARTIALLY_VOIDED,
            AutraStatus.DECLINED);

    for (String status : held) {
      assertThat(list("status", status).httpStatus()).as(status).isEqualTo(200);
    }
  }

  @Test
  void a_status_the_vendor_publishes_nowhere_is_answered_400_invalid_status() {
    AutraApiResponse response = list("status", "ACCEPTED,SOMETIMES");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_STATUS);
  }

  @Test
  void a_type_filter_selects_sales_or_holds_and_nothing_else_is_taken() {
    String sale = aSaleOf("100.00");
    String hold = aHoldOf("200.00");

    assertThat(theIdentifiersIn(itemsOf(list("type", "SALE")))).containsExactly(sale);
    assertThat(theIdentifiersIn(itemsOf(list("type", "PREAUTH")))).containsExactly(hold);
    AutraApiResponse refused = list("type", "CAPTURE");
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(refused)).isEqualTo(AutraErrors.INVALID_TYPE);
  }

  @Test
  void a_type_filter_is_one_value_and_a_comma_separated_pair_is_answered_400_invalid_type() {
    AutraApiResponse response = list("type", "SALE,PREAUTH");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_TYPE);
  }

  @Test
  void a_page_is_fifty_payments_long_until_a_call_names_another_limit() {
    AutraJson.JsonObject body = bodyOf(list());

    assertThat(body.number("page")).contains("1");
    assertThat(body.number("limit")).contains("50");
  }

  @Test
  void paging_walks_the_matches_in_slices_of_the_limit_and_says_when_more_remain() {
    String first = aSaleOf("100.00");
    String second = aSaleOf("200.00");
    String third = aSaleOf("300.00");

    assertThat(theIdentifiersIn(itemsOf(list("limit", "2", "page", "1"))))
        .containsExactly(third, second);
    assertThat(bodyOf(list("limit", "2", "page", "1")).member("hasMore"))
        .isEqualTo(new AutraJson.JsonFlag(true));
    assertThat(theIdentifiersIn(itemsOf(list("limit", "2", "page", "2")))).containsExactly(first);
    assertThat(bodyOf(list("limit", "2", "page", "2")).member("hasMore"))
        .isEqualTo(new AutraJson.JsonFlag(false));
  }

  @Test
  void a_last_page_that_is_exactly_full_says_nothing_more_remains() {
    aSaleOf("100.00");
    aSaleOf("200.00");

    AutraJson.JsonObject body = bodyOf(list("limit", "2", "page", "1"));

    assertThat(((AutraJson.JsonArray) body.member(AutraFields.ITEMS)).elements()).hasSize(2);
    assertThat(body.member("hasMore"))
        .as(
            "two matches read in a page of two leave nothing behind, and a reader told otherwise"
                + " asks for a second page that is always empty")
        .isEqualTo(new AutraJson.JsonFlag(false));
  }

  @Test
  void the_highest_page_a_client_may_ask_for_answers_an_empty_listing_and_never_a_failure() {
    aSaleOf("100.00");

    for (String page :
        List.of("42949674", "42949675", "85899346", String.valueOf(Integer.MAX_VALUE))) {
      AutraApiResponse response = list("page", page);
      assertThat(response.httpStatus())
          .as(
              "page is published as an integer from 1 with no maximum, so this is a well-formed"
                  + " call: the page offset is counted in a width it cannot overflow and the"
                  + " listing answers empty rather than a 500 of plain text that is not autra's"
                  + " envelope at all")
          .isEqualTo(200);
      AutraJson.JsonObject body = bodyOf(response);
      assertThat(((AutraJson.JsonArray) body.member(AutraFields.ITEMS)).elements()).isEmpty();
      assertThat(body.member("hasMore")).isEqualTo(new AutraJson.JsonFlag(false));
      assertThat(body.number("page")).contains(page);
    }
  }

  @Test
  void the_highest_page_at_the_largest_limit_answers_an_empty_listing_too() {
    aSaleOf("100.00");

    AutraApiResponse response = list("page", String.valueOf(Integer.MAX_VALUE), "limit", "200");

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(((AutraJson.JsonArray) bodyOf(response).member(AutraFields.ITEMS)).elements())
        .isEmpty();
  }

  @Test
  void the_highest_page_answers_an_empty_listing_when_the_run_holds_no_payment_at_all() {
    AutraApiResponse response = list("page", String.valueOf(Integer.MAX_VALUE));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(((AutraJson.JsonArray) bodyOf(response).member(AutraFields.ITEMS)).elements())
        .isEmpty();
  }

  @Test
  void a_page_past_the_end_answers_no_items_and_says_nothing_more_remains() {
    aSaleOf("100.00");

    AutraJson.JsonObject body = bodyOf(list("page", "9"));

    assertThat(((AutraJson.JsonArray) body.member(AutraFields.ITEMS)).elements()).isEmpty();
    assertThat(body.member("hasMore")).isEqualTo(new AutraJson.JsonFlag(false));
    assertThat(body.number("page")).contains("9");
  }

  @Test
  void a_page_below_one_or_no_number_at_all_is_answered_400_invalid_page() {
    assertThat(theCodeIn(list("page", "0"))).isEqualTo(AutraErrors.INVALID_PAGE);
    assertThat(theCodeIn(list("page", "-1"))).isEqualTo(AutraErrors.INVALID_PAGE);
    assertThat(theCodeIn(list("page", "two"))).isEqualTo(AutraErrors.INVALID_PAGE);
    assertThat(list("page", "0").httpStatus()).isEqualTo(400);
  }

  @Test
  void a_limit_outside_one_to_two_hundred_or_no_number_at_all_is_answered_400_invalid_limit() {
    assertThat(theCodeIn(list("limit", "0"))).isEqualTo(AutraErrors.INVALID_LIMIT);
    assertThat(theCodeIn(list("limit", "201"))).isEqualTo(AutraErrors.INVALID_LIMIT);
    assertThat(theCodeIn(list("limit", "fifty"))).isEqualTo(AutraErrors.INVALID_LIMIT);
    assertThat(list("limit", "201").httpStatus()).isEqualTo(400);
  }

  @Test
  void the_two_hundred_this_route_accepts_is_answered_as_two_hundred_and_not_a_framework_default() {
    AutraJson.JsonObject body = bodyOf(list("limit", "200", "page", "1"));

    assertThat(body.number("limit")).contains("200");
    assertThat(body.number("page")).contains("1");
    assertThat(list("limit", "1").httpStatus()).isEqualTo(200);
    assertThat(bodyOf(list("limit", "1")).number("limit")).contains("1");
  }

  @Test
  void a_call_that_names_the_merchant_nowhere_is_answered_400_missing_document_id() {
    AutraApiResponse response =
        api.payments(
            AutraWire.get(
                AutraApiController.PAYMENTS,
                AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
                Map.of()));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_document_no_establishment_of_this_token_answers_to_is_answered_404_merchant_not_found() {
    AutraApiResponse response =
        api.payments(
            AutraWire.get(
                AutraApiController.PAYMENTS,
                AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
                AutraWire.query(AutraFields.DOCUMENT_ID, "11111111111")));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void a_document_that_is_no_document_at_all_is_answered_422_invalid_document() {
    AutraApiResponse response =
        api.payments(
            AutraWire.get(
                AutraApiController.PAYMENTS,
                AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
                AutraWire.query(AutraFields.DOCUMENT_ID, "25.055.662/0001-90")));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_DOCUMENT);
  }

  @Test
  void a_payment_of_another_establishment_of_the_same_run_is_not_in_this_listing() {
    String mine = aSaleOf("100.00");
    merchants.with(AutraDoubles.merchant(RUN, "merchant-2", "11222333000181", Map.of()));
    AutraFlow.sell(api, RUN, "merchant-2", "11222333000181", sessions, "200.00", null);

    assertThat(theIdentifiersIn(itemsOf(list()))).containsExactly(mine);
  }

  @Test
  void a_call_carrying_no_access_token_is_answered_401() {
    AutraApiResponse response =
        api.payments(
            AutraWire.get(
                AutraApiController.PAYMENTS,
                Map.of(),
                AutraWire.query(AutraFields.DOCUMENT_ID, DOCUMENT)));

    assertThat(response.httpStatus()).isEqualTo(AutraApi.UNAUTHORIZED_STATUS);
  }

  @Test
  void a_page_asks_once_for_the_timelines_of_everything_on_it_however_many_it_carries() {
    StringBuilder measured = new StringBuilder("\nMEASURED autra payment listing\n");
    for (int held = 1; held <= EIGHT_PAYMENTS; held++) {
      aSaleOf(held + "00.00");
      paymentCalls.clear();
      List<AutraJson> items = itemsOf(list());
      measured
          .append(items.size())
          .append(" items listed: payments ")
          .append(paymentCalls)
          .append('\n');
      assertThat(items).hasSize(held);
      assertThat(paymentCalls)
          .as(
              "the listing provider autra publishes reads the refusals of every item off that"
                  + " item's own timeline, and a timeline read per item made the route cost a"
                  + " query per payment it answered with: %s items listed",
              held)
          .containsExactly(entry("ofMerchant", 1), entry("everyEventOf", 1));
    }
    System.out.print(measured);
  }

  @Test
  void every_item_carries_the_refusals_of_its_own_steps_and_none_of_another_payments() {
    String accepted = aSaleOf("100.00");
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.SALE),
            new ScenarioAction.TerminalFailure(AutraErrors.INSUFFICIENT_FUNDS),
            null));
    AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, "200.00", null);
    String declined = stored.all().getLast().paymentId();

    List<AutraJson> items = itemsOf(list());

    assertThat(theIdentifiersIn(items)).containsExactly(declined, accepted);
    AutraJson.JsonObject refused = (AutraJson.JsonObject) items.get(0);
    AutraJson.JsonObject taken = (AutraJson.JsonObject) items.get(1);
    assertThat(((AutraJson.JsonArray) refused.member(AutraFields.ERRORS)).elements())
        .as(
            "one answer carrying the timelines of a whole page must place each payment's own"
                + " steps against that payment, or a tester reads another payment's refusal"
                + " under this one")
        .hasSize(1);
    assertThat(taken.has(AutraFields.ERRORS)).isFalse();
  }

  @Test
  void the_listing_changes_nothing_and_records_no_event_of_its_own() {
    aSaleOf("500.00");
    int recorded = stored.everyEvent().size();

    list();
    list("limit", "200");

    assertThat(stored.everyEvent()).hasSize(recorded);
  }
}
