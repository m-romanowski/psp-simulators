package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeyedExchangesTest {

  private static final ProviderId GROW = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("inspector-run");
  private static final Instant NOON = Instant.parse("2026-09-15T12:00:00Z");
  private static final String UNIQUE = "transactionUniqueIdentifier";
  private static final String TRANSACTION_ID = "transactionId";
  private static final String MERCHANT = "merchant-1";
  private static final String OTHER_MERCHANT = "merchant-2";
  private static final String CHARGE = "createTransactionWithToken";

  private static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          GROW,
          List.of(
              new DeclaredKey(
                  UNIQUE,
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  "every submission of this business",
                  TestKeyClaims.NEVER_HELD),
              new DeclaredKey(
                  TRANSACTION_ID,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  "the transaction it names",
                  TestKeyClaims.NEVER_HELD)),
          Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, "provider grow replays nothing"),
          answered -> answered);

  @Test
  void every_request_under_one_key_value_is_gathered_into_the_one_row_a_tester_reads() {
    KeyedExchangePage page =
        KeyedExchanges.pageOf(
            MECHANISMS,
            RUN,
            List.of(
                row(MERCHANT, UNIQUE, "9001", 1L, 0, ExchangeVerdict.FIRST_REQUEST),
                row(MERCHANT, UNIQUE, "9001", 3L, 1, ExchangeVerdict.DUPLICATE_KEY_REFUSAL),
                row(MERCHANT, UNIQUE, "9002", 2L, 0, ExchangeVerdict.FIRST_REQUEST)));

    assertThat(page.exchanges()).hasSize(2);
    assertThat(page.exchanges().get(0).keyValue()).isEqualTo("9002");
    assertThat(page.exchanges().get(0).requests()).hasSize(1);
    KeyedExchange repeated = page.exchanges().get(1);
    assertThat(repeated.keyValue()).isEqualTo("9001");
    assertThat(repeated.requests()).hasSize(2);
    assertThat(repeated.role()).isEqualTo(KeyRole.DUPLICATE_DETECTION_KEY);
  }

  @Test
  void one_key_value_sent_by_two_accounts_is_two_rows_and_never_one() {
    KeyedExchangePage page =
        KeyedExchanges.pageOf(
            MECHANISMS,
            RUN,
            List.of(
                row(MERCHANT, UNIQUE, "9001", 1L, 0, ExchangeVerdict.FIRST_REQUEST),
                row(OTHER_MERCHANT, UNIQUE, "9001", 2L, 0, ExchangeVerdict.FIRST_REQUEST)));

    assertThat(page.exchanges()).hasSize(2);
    assertThat(page.exchanges())
        .extracting(KeyedExchange::accountId)
        .containsExactly(OTHER_MERCHANT, MERCHANT);
  }

  @Test
  void the_same_value_under_two_different_keys_is_read_under_the_role_each_key_declares() {
    KeyedExchangePage page =
        KeyedExchanges.pageOf(
            MECHANISMS,
            RUN,
            List.of(
                row(MERCHANT, UNIQUE, "9001", 1L, 0, ExchangeVerdict.FIRST_REQUEST),
                row(MERCHANT, TRANSACTION_ID, "9001", 2L, 0, ExchangeVerdict.FIRST_REQUEST),
                row(MERCHANT, TRANSACTION_ID, "9001", 3L, 1, ExchangeVerdict.CORRELATED_RETRY)));

    assertThat(page.exchanges())
        .extracting(KeyedExchange::role)
        .containsExactly(KeyRole.CORRELATION_KEY, KeyRole.DUPLICATE_DETECTION_KEY);
  }

  @Test
  void requests_stored_out_of_order_are_read_back_in_the_order_they_were_answered_in() {
    KeyedExchangePage page =
        KeyedExchanges.pageOf(
            MECHANISMS,
            RUN,
            List.of(
                row(MERCHANT, UNIQUE, "9001", 3L, 1, ExchangeVerdict.DUPLICATE_KEY_REFUSAL),
                row(MERCHANT, UNIQUE, "9001", 1L, 0, ExchangeVerdict.FIRST_REQUEST)));

    assertThat(page.exchanges().get(0).firstRequest().verdict())
        .isEqualTo(ExchangeVerdict.FIRST_REQUEST);
    assertThat(page.exchanges().get(0).arrival()).isEqualTo(1L);
  }

  @Test
  void a_full_page_hands_back_the_cursor_of_its_last_row_and_a_short_one_hands_back_none() {
    KeyedExchangePage page =
        KeyedExchanges.pageOf(
            MECHANISMS,
            RUN,
            List.of(
                row(MERCHANT, UNIQUE, "9001", 1L, 0, ExchangeVerdict.FIRST_REQUEST),
                row(MERCHANT, UNIQUE, "9002", 2L, 0, ExchangeVerdict.FIRST_REQUEST)));

    assertThat(page.cursorToCarryOnFrom(2)).isEqualTo("1");
    assertThat(page.cursorToCarryOnFrom(3)).isNull();
  }

  @Test
  void a_page_whose_rows_do_not_run_newest_first_is_refused_because_paging_it_would_loop() {
    KeyedExchange older = one("9001", 1L);
    KeyedExchange newer = one("9002", 2L);

    List<KeyedExchange> oldestFirst = List.of(older, newer);

    assertThatThrownBy(() -> new KeyedExchangePage(oldestFirst))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("either loop on it or skip");
  }

  @Test
  void two_rows_that_arrived_at_the_same_moment_are_refused_because_one_of_them_would_be_skipped() {
    KeyedExchange first = one("9001", 4L);
    KeyedExchange second = one("9002", 4L);

    List<KeyedExchange> arrivedTogether = List.of(first, second);

    assertThatThrownBy(() -> new KeyedExchangePage(arrivedTogether))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("did not arrive before");
  }

  @Test
  void a_page_of_one_row_is_a_page_and_never_a_row_out_of_order() {
    assertThat(new KeyedExchangePage(List.of(one("9001", 4L))).exchanges()).hasSize(1);
  }

  @Test
  void a_page_of_one_exchange_and_a_cursor_of_the_first_row_are_both_admitted() {
    KeyedExchangeQuery narrowest = KeyedExchangeQuery.of(null, "1", "1", RUN);
    KeyedExchangeQuery widest =
        KeyedExchangeQuery.of(null, null, String.valueOf(KeyedExchangeQuery.LARGEST_LIMIT), RUN);

    assertThat(narrowest.limit()).isEqualTo(1);
    assertThat(narrowest.after()).isEqualTo(1L);
    assertThat(widest.limit()).isEqualTo(KeyedExchangeQuery.LARGEST_LIMIT);
  }

  @Test
  void a_cursor_before_the_first_row_this_simulator_ever_recorded_is_refused() {
    assertThatThrownBy(() -> KeyedExchangeQuery.of(null, "0", null, RUN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(KeyedExchangeQuery.AFTER_NAMES_A_ROW_ALREADY_SEEN);
    assertThatThrownBy(() -> KeyedExchangeQuery.of(null, null, "0", RUN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and");
  }

  @Test
  void a_test_run_that_has_sent_nothing_under_a_key_pages_to_nothing() {
    assertThat(KeyedExchanges.pageOf(MECHANISMS, RUN, List.of()).exchanges()).isEmpty();
    assertThat(KeyedExchangePage.empty().exchanges()).isEmpty();
    assertThat(KeyedExchangePage.empty().cursorToCarryOnFrom(1)).isNull();
  }

  @Test
  void a_cursor_that_is_not_a_whole_number_is_refused_rather_than_read_as_the_newest_page() {
    assertThatThrownBy(() -> KeyedExchangeQuery.of(null, "not-a-number", null, RUN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(KeyedExchangeQuery.AFTER_NAMES_A_ROW_ALREADY_SEEN);
    assertThatThrownBy(() -> KeyedExchangeQuery.of(null, null, "nine", RUN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit must be a whole number");
    String pastTheCeiling = String.valueOf(1 + KeyedExchangeQuery.LARGEST_LIMIT);

    assertThatThrownBy(() -> KeyedExchangeQuery.of(null, null, pastTheCeiling, RUN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 1 and");
  }

  @Test
  void a_query_that_names_neither_a_test_run_nor_a_page_reads_the_newest_of_the_default_run() {
    KeyedExchangeQuery query = KeyedExchangeQuery.of(null, null, null, RUN);

    assertThat(query.testRunId()).isEqualTo(RUN);
    assertThat(query.after()).isEqualTo(KeyedExchangeQuery.FROM_THE_NEWEST);
    assertThat(query.limit()).isEqualTo(KeyedExchangeQuery.DEFAULT_LIMIT);
  }

  @Test
  void a_query_that_names_a_test_run_and_a_page_reads_that_run_from_that_cursor() {
    KeyedExchangeQuery query = KeyedExchangeQuery.of("another-run", "12", "5", RUN);

    assertThat(query.testRunId().value()).isEqualTo("another-run");
    assertThat(query.after()).isEqualTo(12L);
    assertThat(query.limit()).isEqualTo(5);
  }

  @Test
  void a_query_naming_a_malformed_test_run_says_which_property_was_wrong() {
    assertThatThrownBy(() -> KeyedExchangeQuery.of("not a namespace", null, null, RUN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("testRunId");
  }

  private static final String CLAIMED_KEY = "Idempotency-Key";

  private static final ProviderKeyMechanisms MECHANISMS_THAT_CLAIM =
      new ProviderKeyMechanisms(
          GROW,
          List.of(
              new DeclaredKey(
                  CLAIMED_KEY,
                  KeyRole.TRUE_IDEMPOTENCY_KEY,
                  KeyCarriage.HEADER,
                  "the account for twenty-four hours",
                  TestKeyClaims.CLAIMED)),
          Map.of(
              KeyRole.DUPLICATE_DETECTION_KEY,
              "no duplicate check",
              KeyRole.CORRELATION_KEY,
              "no correlation key"),
          answered -> answered);

  @Test
  void a_key_its_provider_declares_claimed_reads_back_a_request_refused_while_it_was_held() {
    KeyedExchangePage page =
        KeyedExchanges.pageOf(
            MECHANISMS_THAT_CLAIM,
            RUN,
            List.of(
                row(
                    MERCHANT,
                    CLAIMED_KEY,
                    "k-1",
                    1L,
                    0,
                    ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD),
                row(MERCHANT, CLAIMED_KEY, "k-1", 2L, 1, ExchangeVerdict.FIRST_REQUEST)));

    assertThat(page.exchanges()).hasSize(1);
    assertThat(page.exchanges().get(0).requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(
            ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD, ExchangeVerdict.FIRST_REQUEST);
  }

  @Test
  void a_key_its_provider_declares_never_held_cannot_read_back_a_request_refused_for_holding_it() {
    List<RecordedKeyedRequest> rows =
        List.of(
            row(MERCHANT, UNIQUE, "9001", 1L, 0, ExchangeVerdict.FIRST_REQUEST),
            row(MERCHANT, UNIQUE, "9001", 2L, 1, ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD));

    assertThatThrownBy(() -> KeyedExchanges.pageOf(MECHANISMS, RUN, rows))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares that no request can ever find this key held")
        .hasMessageContaining(UNIQUE);
  }

  @Test
  void a_key_the_provider_declares_nowhere_is_reported_rather_than_read_as_never_claimed() {
    assertThatThrownBy(() -> MECHANISMS.claimOf("a-key-nobody-declared"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("whether a request can ever find this key held");
  }

  @Test
  void a_provider_that_claims_a_key_must_say_what_a_request_meeting_the_claim_is_answered() {
    assertThatThrownBy(() -> new KeyClaim.ClaimedBeforeTheOperationRuns(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say in its own words what that is");
  }

  @Test
  void a_provider_whose_key_is_never_held_must_say_why_there_is_nothing_to_hold_it() {
    assertThatThrownBy(() -> new KeyClaim.NeverHeldByARequest(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("why there is nothing to hold it");
  }

  private static KeyedExchange one(String keyValue, long arrival) {
    return KeyedExchanges.pageOf(
            MECHANISMS,
            RUN,
            List.of(row(MERCHANT, UNIQUE, keyValue, arrival, 0, ExchangeVerdict.FIRST_REQUEST)))
        .exchanges()
        .get(0);
  }

  private static RecordedKeyedRequest row(
      String accountId,
      String keyName,
      String keyValue,
      long arrival,
      int sequence,
      ExchangeVerdict verdict) {
    return new RecordedKeyedRequest(
        accountId,
        keyName,
        keyValue,
        arrival,
        sequence,
        verdict,
        "fingerprint-" + keyValue,
        CHARGE,
        200,
        "application/json",
        "{}",
        null,
        false,
        null,
        NOON.plus(Duration.ofSeconds(arrival)));
  }
}
