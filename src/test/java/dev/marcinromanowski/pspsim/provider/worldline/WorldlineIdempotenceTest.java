package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldlineIdempotenceTest {

  private static final TestRunId RUN = TestRunId.of("run-worldline");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private static final String KEY = "a-key-the-caller-chose";

  private final WorldlineDoubles.Merchants merchants =
      new WorldlineDoubles.Merchants()
          .with(WorldlineDoubles.merchant(RUN, WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT));
  private final WorldlineDoubles.Tokenizations sessions = new WorldlineDoubles.Tokenizations();
  private final WorldlineDoubles.Payments payments = new WorldlineDoubles.Payments();
  private final WorldlineDoubles.Keys keys = new WorldlineDoubles.Keys();
  private final WorldlineDoubles.Audit audit = new WorldlineDoubles.Audit();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final IdGenerator ids = new WorldlineDoubles.CountingIds();

  private WorldlineApi api() {
    return WorldlineWiring.api(
        merchants,
        sessions,
        payments,
        keys,
        audit,
        RUN,
        WHEN,
        new ScenarioEngine(new InMemoryScenarioRules()),
        ids,
        fixtures);
  }

  private String aStoredToken() {
    WorldlineApiResponse opened =
        api()
            .createHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                    "{}"),
                WorldlineWire.MERCHANT);
    String sessionId = String.valueOf(opened.providerObjectId());
    new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids)
        .complete(
            new HostedCompletionRequest(
                RUN,
                sessionId,
                PayerOutcome.PAID,
                WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS,
                ObjectOrigin.CONTROL_API),
            sessions.findById(RUN, sessionId).orElseThrow(),
            WHEN);
    return String.valueOf(sessions.findById(RUN, sessionId).orElseThrow().token());
  }

  private WorldlineApiResponse create(String body, Map<String, List<String>> headers) {
    return api()
        .createPayment(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.thePaymentsOf(WorldlineWire.MERCHANT),
                headers,
                body),
            WorldlineWire.MERCHANT);
  }

  private static Map<String, List<String>> under(String header, String value) {
    return Map.of(header, List.of(value));
  }

  @Test
  void the_spelling_the_idempotent_requests_page_publishes_reaches_the_mechanism() {
    String token = aStoredToken();
    String body = WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE");
    WorldlineApiResponse first =
        create(
            body,
            under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    WorldlineApiResponse repeat =
        create(
            body,
            under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    assertThat(repeat.providerObjectId()).isEqualTo(first.providerObjectId());
  }

  @Test
  void the_spelling_the_manual_authentication_page_publishes_reaches_the_same_mechanism() {
    String token = aStoredToken();
    String body = WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE");
    WorldlineApiResponse first =
        create(
            body,
            under(
                WorldlineFields.IDEMPOTENCY_KEY_AS_THE_MANUAL_AUTHENTICATION_PAGE_SPELLS_IT, KEY));
    WorldlineApiResponse repeat =
        create(
            body,
            under(
                WorldlineFields.IDEMPOTENCY_KEY_AS_THE_MANUAL_AUTHENTICATION_PAGE_SPELLS_IT, KEY));
    assertThat(repeat.providerObjectId()).isEqualTo(first.providerObjectId());
  }

  @Test
  void a_repeat_with_a_different_payload_replays_the_first_answer_rather_than_conflicting() {
    String token = aStoredToken();
    WorldlineApiResponse first =
        create(
            WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
            under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    WorldlineApiResponse repeat =
        create(
            WorldlineWire.aPaymentBody(9999L, "EUR", token, "PRE_AUTHORIZATION"),
            under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    assertThat(repeat.httpStatus()).isEqualTo(first.httpStatus());
    assertThat(WorldlineWire.bodyOf(repeat)).isEqualTo(WorldlineWire.bodyOf(first));
    assertThat(keys.everythingItObserved())
        .extracting(RecordedKeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY);
  }

  @Test
  void a_replay_carries_the_published_timestamp_header_of_the_request_it_replays() {
    String token = aStoredToken();
    String body = WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE");
    create(
        body,
        under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    WorldlineApiResponse repeat =
        create(
            body,
            under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    assertThat(WorldlineWire.headerOf(repeat, WorldlineFields.IDEMPOTENCE_REQUEST_TIMESTAMP))
        .isEqualTo(Long.toString(WHEN.toEpochMilli()));
  }

  @Test
  void a_replay_of_a_created_payment_carries_the_location_header_the_first_answer_carried() {
    String token = aStoredToken();
    String body = WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE");
    WorldlineApiResponse first =
        create(
            body,
            under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    WorldlineApiResponse repeat =
        create(
            body,
            under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    assertThat(WorldlineWire.headerOf(repeat, WorldlineFields.LOCATION))
        .isEqualTo(WorldlineWire.headerOf(first, WorldlineFields.LOCATION));
  }

  @Test
  void a_request_meeting_a_key_another_request_has_not_finished_under_is_the_published_409() {
    String token = aStoredToken();
    keys.stillRunningUnder(RUN, WorldlineWire.ACCOUNT, KEY, WHEN);
    WorldlineApiResponse refused =
        create(
            WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
            under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.CONFLICT);
    assertThat(keys.everythingItObserved())
        .extracting(RecordedKeyedRequest::verdict)
        .contains(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD);
  }

  @Test
  void a_key_longer_than_the_published_forty_characters_is_refused_as_an_invalid_value() {
    String token = aStoredToken();
    WorldlineApiResponse refused =
        create(
            WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
            under(
                WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
                "k".repeat(WorldlineIdempotence.LONGEST_KEY + 1)));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("maximum length of 40 characters");
  }

  @Test
  void a_key_of_exactly_forty_characters_is_inside_the_published_maximum() {
    String token = aStoredToken();
    WorldlineApiResponse answered =
        create(
            WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
            under(
                WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
                "k".repeat(WorldlineIdempotence.LONGEST_KEY)));
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
  }

  @Test
  void the_last_character_of_ascii_is_inside_the_set_the_vendor_publishes_as_ascii_compliant() {
    String token = aStoredToken();

    WorldlineApiResponse answered =
        create(
            WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
            under(
                WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
                "key-\u007f"));

    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
  }

  @Test
  void the_fingerprint_recorded_under_a_key_is_the_one_of_the_payload_that_request_sent() {
    String token = aStoredToken();
    create(
        WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
        under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));
    create(
        WorldlineWire.aPaymentBody(9999L, "EUR", token, "PRE_AUTHORIZATION"),
        under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));

    List<RecordedKeyedRequest> recorded = keys.everythingItObserved();

    assertThat(recorded).hasSize(2);
    assertThat(recorded.get(0).fingerprint()).isNotBlank();
    assertThat(recorded.get(1).fingerprint())
        .as("the two payloads differ, so the two fingerprints differ and the answer is replayed")
        .isNotEqualTo(recorded.get(0).fingerprint());
  }

  @Test
  void a_first_request_that_created_a_payment_is_recorded_as_having_changed_state() {
    String token = aStoredToken();

    create(
        WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
        under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));

    assertThat(keys.everythingItObserved())
        .singleElement()
        .extracting(RecordedKeyedRequest::stateChanged)
        .isEqualTo(true);
  }

  @Test
  void a_first_request_the_provider_refused_is_recorded_as_having_changed_nothing() {
    create(
        WorldlineWire.aPaymentBody(5000L, "PLN", "token-nobody-minted", "SALE"),
        under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY));

    assertThat(keys.everythingItObserved())
        .singleElement()
        .extracting(RecordedKeyedRequest::stateChanged)
        .isEqualTo(false);
  }

  @Test
  void a_key_carrying_a_character_outside_ascii_is_refused_as_the_vendor_publishes() {
    String token = aStoredToken();
    WorldlineApiResponse refused =
        create(
            WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
            under(
                WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
                "klucz-ż"));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("ASCII compliant");
  }

  @Test
  void
      a_request_carrying_both_published_spellings_with_two_values_is_refused_rather_than_resolved() {
    String token = aStoredToken();
    WorldlineApiResponse refused =
        create(
            WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
            Map.of(
                WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
                List.of("one"),
                WorldlineFields.IDEMPOTENCY_KEY_AS_THE_MANUAL_AUTHENTICATION_PAGE_SPELLS_IT,
                List.of("another")));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("no precedence between them");
  }

  @Test
  void a_request_carrying_both_published_spellings_with_one_value_is_one_key_and_not_two() {
    String token = aStoredToken();
    WorldlineApiResponse answered =
        create(
            WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
            Map.of(
                WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
                List.of(KEY),
                WorldlineFields.IDEMPOTENCY_KEY_AS_THE_MANUAL_AUTHENTICATION_PAGE_SPELLS_IT,
                List.of(KEY)));
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
    assertThat(keys.everythingItObserved()).hasSize(1);
  }

  @Test
  void a_key_on_a_hosted_tokenization_route_is_ignored_and_recorded_as_no_exchange_at_all() {
    api()
        .createHostedTokenization(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                under(
                    WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY),
                "{}"),
            WorldlineWire.MERCHANT);
    assertThat(keys.everythingItObserved()).isEmpty();
  }

  @Test
  void a_create_carrying_no_key_at_all_is_recorded_as_no_keyed_exchange() {
    String token = aStoredToken();
    create(WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"), Map.of());
    assertThat(keys.everythingItObserved()).isEmpty();
  }

  @Test
  void a_key_whose_value_is_blank_names_no_key_and_the_request_is_a_first_request() {
    String token = aStoredToken();
    create(
        WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE"),
        under(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, "  "));
    assertThat(keys.everythingItObserved()).isEmpty();
  }

  @Test
  void a_capture_honours_the_key_because_the_vendor_lists_capture_among_the_operations_it_serves() {
    String token = aStoredToken();
    WorldlineApiResponse created =
        create(WorldlineWire.aPaymentBody(5000L, "PLN", token, "PRE_AUTHORIZATION"), Map.of());
    String paymentId = String.valueOf(created.providerObjectId());
    WorldlineApiResponse first = capture(paymentId, KEY);
    WorldlineApiResponse repeat = capture(paymentId, KEY);
    assertThat(WorldlineWire.bodyOf(repeat)).isEqualTo(WorldlineWire.bodyOf(first));
  }

  private WorldlineApiResponse capture(String paymentId, String key) {
    return api()
        .capturePayment(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.thePaymentResourceOf(WorldlineWire.MERCHANT, paymentId)
                    + WorldlineApiController.CAPTURE,
                under(
                    WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, key),
                "{}"),
            WorldlineWire.MERCHANT,
            paymentId);
  }

  @Test
  void this_provider_declares_a_true_idempotency_key_and_says_it_has_no_duplicate_detection_key() {
    assertThat(WorldlineKeyMechanisms.MECHANISMS.has(KeyRole.TRUE_IDEMPOTENCY_KEY)).isTrue();
    assertThat(WorldlineKeyMechanisms.MECHANISMS.has(KeyRole.CORRELATION_KEY)).isTrue();
    assertThat(WorldlineKeyMechanisms.MECHANISMS.has(KeyRole.DUPLICATE_DETECTION_KEY)).isFalse();
    assertThat(WorldlineKeyMechanisms.MECHANISMS.theAbsenceOf(KeyRole.DUPLICATE_DETECTION_KEY))
        .isEqualTo(WorldlineKeyMechanisms.NO_DUPLICATE_DETECTION_KEY);
  }

  @Test
  void the_key_this_provider_claims_before_the_operation_runs_says_what_meets_it() {
    assertThat(
            WorldlineKeyMechanisms.MECHANISMS
                .claimOf(WorldlineKeyMechanisms.IDEMPOTENCE_KEY)
                .canBeHeld())
        .isTrue();
    assertThat(
            WorldlineKeyMechanisms.MECHANISMS
                .claimOf(WorldlineKeyMechanisms.PAYMENT_ID)
                .canBeHeld())
        .isFalse();
  }

  @Test
  void every_verdict_this_provider_records_is_one_the_true_idempotency_role_admits() {
    assertThat(ExchangeVerdict.FIRST_REQUEST.isAdmittedUnder(KeyRole.TRUE_IDEMPOTENCY_KEY))
        .isTrue();
    assertThat(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY.isAdmittedUnder(KeyRole.TRUE_IDEMPOTENCY_KEY))
        .isTrue();
    assertThat(
            ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD.isAdmittedUnder(
                KeyRole.TRUE_IDEMPOTENCY_KEY))
        .isTrue();
  }
}
