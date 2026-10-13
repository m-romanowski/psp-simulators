package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutraCardSessionTest {

  private static final TestRunId RUN = TestRunId.of("autra-card-session");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String A_SEEDED_CARD = "a-seeded-card";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.PixPayments pixPayments = new AutraDoubles.PixPayments();
  private final AutraDoubles.CardSessions cardSessions = new AutraDoubles.CardSessions();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final AutraPayerCards cards = new AutraPayerCards(fixtures);

  private final TokenizeOnTheHostedPage tokenize =
      new TokenizeOnTheHostedPage(cardSessions, cards, new AutraDoubles.CountingIds());

  private AutraApi api() {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT));
    return AutraWiring.api(
        merchants,
        sessions,
        payments,
        pixPayments,
        cardSessions,
        audit,
        RUN,
        NOW,
        scenarios,
        new AutraDoubles.Runs(RUN));
  }

  @Test
  void a_card_session_is_answered_the_three_fields_autra_publishes_and_no_others() {
    AutraApiResponse response = open(api(), "{\"documentId\":\"" + DOCUMENT + "\"}");

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response).members().keySet())
        .containsExactly(
            AutraFields.HOSTED_CARD_SESSION_ID,
            AutraFields.HOSTED_CARD_URL,
            AutraFields.EXPIRES_AT);
  }

  @Test
  void the_session_expires_exactly_fifteen_minutes_after_it_was_opened() {
    AutraApiResponse response = open(api(), "{\"documentId\":\"" + DOCUMENT + "\"}");

    assertThat(bodyOf(response).text(AutraFields.EXPIRES_AT)).contains("2026-09-22T10:15:00Z");
  }

  @Test
  void the_hosted_card_url_names_the_session_it_opens_and_the_token_its_page_is_found_by() {
    AutraApiResponse response = open(api(), "{\"documentId\":\"" + DOCUMENT + "\"}");

    AutraJson.JsonObject body = bodyOf(response);
    String sessionId = body.text(AutraFields.HOSTED_CARD_SESSION_ID).orElseThrow();
    AutraCardSession stored = cardSessions.findById(RUN, sessionId).orElseThrow();
    assertThat(body.text(AutraFields.HOSTED_CARD_URL).orElseThrow())
        .isEqualTo(
            AutraWiring.PUBLIC_BASE_URL
                + "/tokenize/sessions/"
                + sessionId
                + "?token="
                + stored.pageToken());
  }

  @Test
  void the_one_time_token_inside_the_hosted_card_url_is_withheld_from_the_audit_trail() {
    open(api(), "{\"documentId\":\"" + DOCUMENT + "\"}");

    assertThat(AutraFields.SENSITIVE_RESPONSE_FIELDS).contains(AutraFields.HOSTED_CARD_URL);
    assertThat(theOutboundFields().get(AutraFields.HOSTED_CARD_URL))
        .isNotEmpty()
        .noneMatch(recorded -> recorded.contains(AutraCardPage.TOKEN + "="));
  }

  @Test
  void a_card_session_naming_no_document_is_refused_missing_document_id() {
    AutraApiResponse response = open(api(), "{}");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_card_session_whose_body_is_not_a_json_object_is_refused_invalid_json() {
    AutraApiResponse response = open(api(), "not json at all");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_JSON);
  }

  @Test
  void a_card_session_for_a_document_no_merchant_of_this_run_holds_is_refused_as_not_found() {
    AutraApiResponse response = open(api(), "{\"documentId\":\"11111111111\"}");

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void a_rule_that_declines_a_card_session_answers_the_error_envelope_at_the_published_status() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.CARD_SESSION),
            new ScenarioAction.TerminalFailure(AutraErrors.MSG_GENERAL_ERROR),
            null));

    AutraApiResponse response = open(api(), "{\"documentId\":\"" + DOCUMENT + "\"}");

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MSG_GENERAL_ERROR);
  }

  @Test
  void the_nickname_the_cardholder_would_type_on_the_page_is_carried_by_the_session_it_opened() {
    AutraApi api = api();

    String sessionId =
        theSessionIn(
            open(api, "{\"documentId\":\"" + DOCUMENT + "\",\"nickname\":\"Personal Visa\"}"));

    assertThat(cardSessions.findById(RUN, sessionId).orElseThrow().nickname())
        .isEqualTo("Personal Visa");
  }

  @Test
  void a_session_awaiting_its_payer_offers_the_one_card_autra_prints_and_nothing_else() {
    HostedCompletionOffer offer = tokenize.offerOn(aSessionAwaitingItsPayer(), NOW);

    assertThat(offer).isInstanceOf(HostedCompletionOffer.Offered.class);
    assertThat(((HostedCompletionOffer.Offered) offer).cardsAPayerMayName())
        .extracting(PayerCard::alias)
        .containsExactly(AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);
  }

  @Test
  void a_seeded_payment_method_fixture_is_a_second_card_the_page_offers() {
    seedACard(Map.of(ProviderPaymentMethodFields.TOKEN, "seeded-network-token"));

    HostedCompletionOffer offer = tokenize.offerOn(aSessionAwaitingItsPayer(), NOW);

    assertThat(((HostedCompletionOffer.Offered) offer).cardsAPayerMayName())
        .extracting(PayerCard::alias)
        .containsExactly(AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS, A_SEEDED_CARD);
  }

  @Test
  void a_payer_who_typed_the_card_autra_prints_leaves_a_session_naming_both_halves_of_the_pair() {
    AutraCardSession session = aSessionAwaitingItsPayer();

    HostedCompletionOutcome outcome =
        tokenize.complete(
            completion(PayerOutcome.PAID, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS), session, NOW);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.Completed.class);
    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(stored.slugToken()).isNotBlank();
    assertThat(stored.slugStoredCard()).isNotBlank();
    assertThat(stored.slugToken()).isNotEqualTo(stored.slugStoredCard());
  }

  @Test
  void the_tokenized_session_carries_the_card_facts_the_published_result_table_names() {
    AutraCardSession session = aSessionAwaitingItsPayer();

    tokenize.complete(
        completion(PayerOutcome.PAID, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS), session, NOW);

    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(stored.brand()).isEqualTo(AutraPayerCards.THE_PUBLISHED_BRAND);
    assertThat(stored.first4()).isEqualTo(AutraPayerCards.THE_PUBLISHED_FIRST4);
    assertThat(stored.last4()).isEqualTo(AutraPayerCards.THE_PUBLISHED_LAST4);
    assertThat(stored.expirationMonth()).isEqualTo(12);
    assertThat(stored.expirationYear()).isEqualTo(2030);
    assertThat(stored.tokenExpirationDate()).isEqualTo("122030");
  }

  @Test
  void a_session_that_tokenized_a_seeded_card_names_its_last_four_and_no_bin_prefix() {
    seedACard(Map.of(ProviderPaymentMethodFields.TOKEN, "seeded-network-token"));
    AutraCardSession session = aSessionAwaitingItsPayer();

    tokenize.complete(completion(PayerOutcome.PAID, A_SEEDED_CARD), session, NOW);

    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(stored.last4()).isEqualTo("4242");
    assertThat(stored.first4()).isNull();
  }

  @Test
  void a_seeded_pair_is_handed_back_unchanged_so_a_tester_charges_the_slugs_they_chose() {
    seedACard(
        Map.of(
            ProviderPaymentMethodFields.TOKEN,
            "seeded-network-token",
            ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
            "seeded-stored-card"));
    AutraCardSession session = aSessionAwaitingItsPayer();

    tokenize.complete(completion(PayerOutcome.PAID, A_SEEDED_CARD), session, NOW);

    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(stored.slugToken()).isEqualTo("seeded-network-token");
    assertThat(stored.slugStoredCard()).isEqualTo("seeded-stored-card");
  }

  @Test
  void a_seeded_card_naming_only_one_half_hands_that_half_back_and_leaves_the_other_absent() {
    seedACard(Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "seeded-stored-card"));
    AutraCardSession session = aSessionAwaitingItsPayer();

    tokenize.complete(completion(PayerOutcome.PAID, A_SEEDED_CARD), session, NOW);

    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(stored.slugToken()).isNull();
    assertThat(stored.slugStoredCard()).isEqualTo("seeded-stored-card");
  }

  @Test
  void a_payer_who_named_a_card_this_run_does_not_offer_is_refused_and_told_which_it_offers() {
    AutraCardSession session = aSessionAwaitingItsPayer();

    HostedCompletionOutcome outcome =
        tokenize.complete(completion(PayerOutcome.PAID, "no-such-card"), session, NOW);

    assertThat(outcome)
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(
                TokenizeOnTheHostedPage.NO_CARD_OF_THIS_RUN_ANSWERS_TO_THAT_ALIAS
                    + AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS));
  }

  @Test
  void
      a_payer_who_left_the_page_leaves_the_session_expired_because_that_is_the_only_end_published() {
    AutraCardSession session = aSessionAwaitingItsPayer();

    HostedCompletionOutcome outcome =
        tokenize.complete(completion(PayerOutcome.ABANDONED, null), session, NOW);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.Completed.class);
    assertThat(cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.EXPIRED);
  }

  @Test
  void a_declined_payer_is_refused_because_a_tokenization_page_has_no_acquirer_to_decline() {
    AutraCardSession session = aSessionAwaitingItsPayer();

    HostedCompletionOutcome outcome =
        tokenize.complete(completion(PayerOutcome.DECLINED, null), session, NOW);

    assertThat(outcome)
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(
                TokenizeOnTheHostedPage.NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE));
    assertThat(TokenizeOnTheHostedPage.THE_PAYER_STEPS.theStepsAPayerMayTake())
        .containsExactly(PayerOutcome.PAID, PayerOutcome.ABANDONED);
    assertThat(TokenizeOnTheHostedPage.THE_PAYER_STEPS.whatItHasInsteadOf(PayerOutcome.DECLINED))
        .isEqualTo(TokenizeOnTheHostedPage.NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE);
  }

  @Test
  void a_session_a_payer_already_finished_offers_no_second_page_because_the_url_is_single_use() {
    AutraCardSession session = aSessionAwaitingItsPayer();
    tokenize.complete(
        completion(PayerOutcome.PAID, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS), session, NOW);

    HostedCompletionOffer offer =
        tokenize.offerOn(cardSessions.findById(RUN, session.cardSessionId()).orElseThrow(), NOW);

    assertThat(offer).isInstanceOf(HostedCompletionOffer.NotOffered.class);
    assertThat(((HostedCompletionOffer.NotOffered) offer).whatThisProviderHasInstead())
        .contains("uso unico");
  }

  @Test
  void a_second_completion_of_a_session_already_tokenized_is_refused_in_the_vendors_own_words() {
    AutraCardSession session = aSessionAwaitingItsPayer();
    tokenize.complete(
        completion(PayerOutcome.PAID, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS), session, NOW);

    HostedCompletionOutcome outcome =
        tokenize.complete(
            completion(PayerOutcome.PAID, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS),
            cardSessions.findById(RUN, session.cardSessionId()).orElseThrow(),
            NOW);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(((HostedCompletionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains("uso unico");
  }

  @Test
  void a_session_past_its_fifteen_minutes_offers_no_page() {
    AutraCardSession session = aSessionAwaitingItsPayer();

    HostedCompletionOffer offer = tokenize.offerOn(session, NOW.plusSeconds(901));

    assertThat(offer)
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                AutraApi.AUTRA, session.whyItIsNoLongerWaitingAt(NOW.plusSeconds(901))));
    assertThat(((HostedCompletionOffer.NotOffered) offer).whatThisProviderHasInstead())
        .contains("no longer loads");
  }

  @Test
  void a_payer_who_paid_a_session_past_its_fifteen_minutes_is_refused_in_the_words_of_an_expiry() {
    AutraCardSession session = aSessionAwaitingItsPayer();

    HostedCompletionOutcome outcome =
        tokenize.complete(
            completion(PayerOutcome.PAID, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS),
            session,
            NOW.plusSeconds(900));

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(((HostedCompletionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains("no longer loads");
  }

  @Test
  void a_session_the_clock_has_passed_reads_expired_while_the_row_still_carries_what_was_written() {
    AutraCardSession session = aSessionAwaitingItsPayer();
    Instant past = NOW.plusSeconds(900);

    assertThat(session.statusAt(past)).isEqualTo("EXPIRED");
    assertThat(session.normalizedStatusAt(past)).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(session.statusAt(past.minusMillis(1))).isEqualTo("PENDING");
    assertThat(session.normalizedStatusAt(past.minusMillis(1)))
        .isEqualTo(NormalizedStatus.PROCESSING);
    assertThat(cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().nativeStatus())
        .isEqualTo("PENDING");
  }

  @Test
  void the_ttl_expires_a_session_still_waiting_and_leaves_a_tokenized_one_tokenized() {
    AutraCardSession session = aSessionAwaitingItsPayer();
    AutraCardSession tokenized =
        session.tokenizedAs(new AutraStoredCard("t", "s", null, null, null, null, null, null), NOW);
    Instant past = NOW.plusSeconds(900);

    assertThat(tokenized.statusAt(past)).isEqualTo("TOKENIZED");
    assertThat(tokenized.normalizedStatusAt(past)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(tokenized.isAwaitingThePayerAt(past)).isFalse();
    assertThat(tokenized.whyItIsNoLongerWaitingAt(past))
        .contains("uso unico")
        .doesNotContain("no longer loads");
    assertThat(session.isAwaitingThePayerAt(past)).isFalse();
    assertThat(session.isAwaitingThePayerAt(past.minusMillis(1))).isTrue();
  }

  @Test
  void a_payer_losing_the_race_to_another_request_is_told_what_that_request_left_behind() {
    AutraCardSession session = aSessionAwaitingItsPayer();
    cardSessions.movedByAnotherRequestFirst(session.cardSessionId());

    HostedCompletionOutcome outcome =
        tokenize.complete(
            completion(PayerOutcome.PAID, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS), session, NOW);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(((HostedCompletionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains("no longer loads");
  }

  @Test
  void a_payer_who_left_and_lost_the_race_to_another_request_is_told_what_that_request_left() {
    AutraCardSession session = aSessionAwaitingItsPayer();
    cardSessions.movedByAnotherRequestFirst(session.cardSessionId());

    HostedCompletionOutcome outcome =
        tokenize.complete(completion(PayerOutcome.ABANDONED, null), session, NOW);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(((HostedCompletionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains("no longer loads");
  }

  @Test
  void a_session_waiting_is_processing_a_tokenized_one_succeeded_and_an_expired_one_cancelled() {
    AutraCardSession waiting = aSessionAwaitingItsPayer();

    assertThat(waiting.normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.PROCESSING);
    assertThat(
            waiting
                .tokenizedAs(new AutraStoredCard("t", "s", null, null, null, null, null, null), NOW)
                .normalizedStatusAt(NOW))
        .isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(waiting.expiredAt(NOW).normalizedStatusAt(NOW))
        .isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void a_session_holding_a_status_this_simulator_never_writes_is_unknown_and_says_so() {
    AutraCardSession odd = aSessionAwaitingItsPayer();
    AutraCardSession drifted =
        new AutraCardSession(
            RUN,
            odd.cardSessionId(),
            ACCOUNT,
            DOCUMENT,
            null,
            "SOMETHING_ELSE",
            odd.pageToken(),
            null,
            odd.expiresAt(),
            ObjectOrigin.PROVIDER_API,
            NOW,
            NOW);

    assertThat(drifted.normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.UNKNOWN);
    assertThat(drifted.whyItIsNoLongerWaitingAt(NOW))
        .contains("SOMETHING_ELSE")
        .contains(AutraCardSession.NO_STATUS_IS_PUBLISHED_FOR_A_SESSION);
  }

  @Test
  void an_identifier_the_store_will_not_take_is_drawn_again_until_one_is_claimed() {
    cardSessions.refusingTheNextInserts(OpenCardSession.SESSION_ATTEMPTS - 1);

    assertThat(open(api(), "{\"documentId\":\"" + DOCUMENT + "\"}").httpStatus()).isEqualTo(200);
  }

  @Test
  void an_identifier_no_attempt_could_claim_is_a_call_this_simulator_cannot_answer() {
    AutraApi api = api();
    cardSessions.refusingTheNextInserts(OpenCardSession.SESSION_ATTEMPTS);

    assertThatThrownBy(() -> open(api, "{\"documentId\":\"" + DOCUMENT + "\"}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("hostedCardSessionId");
  }

  @Test
  void a_stored_card_naming_neither_half_of_the_pair_names_no_card_and_cannot_be_built() {
    assertThatThrownBy(() -> new AutraStoredCard(null, " ", null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AutraStoredCard.A_STORED_CARD_NAMES_AT_LEAST_ONE_SLUG);
  }

  @Test
  void a_seeded_fixture_that_describes_no_card_is_no_card_a_payer_may_name_on_this_page() {
    fixtures.seed(
        new NewPaymentMethodFixture(
            AutraApi.AUTRA,
            RUN,
            ACCOUNT,
            "owner-1",
            PaymentMethodKind.PSP_TOKEN,
            "a-wallet",
            null,
            Map.of(ProviderPaymentMethodFields.TOKEN, "seeded-network-token"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());

    assertThat(cards.aliasesOffered(RUN)).isEqualTo(AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);
  }

  @Test
  void a_seeded_pair_whose_halves_are_blank_is_a_card_the_page_mints_its_own_slugs_for() {
    Map<String, String> blank = new LinkedHashMap<>();
    blank.put(ProviderPaymentMethodFields.TOKEN, "  ");
    blank.put(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "  ");
    seedACard(blank);
    AutraCardSession session = aSessionAwaitingItsPayer();

    tokenize.complete(completion(PayerOutcome.PAID, A_SEEDED_CARD), session, NOW);

    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(stored.slugToken()).isNotBlank().isNotEqualTo("  ");
  }

  @Test
  void a_card_expiring_in_october_is_written_as_four_digits_and_takes_no_leading_zero() {
    fixtures.seed(
        new NewPaymentMethodFixture(
            AutraApi.AUTRA,
            RUN,
            ACCOUNT,
            "owner-1",
            PaymentMethodKind.CARD,
            A_SEEDED_CARD,
            new FixtureCard("mastercard", "4242", 10, 2029, "debit"),
            Map.of(ProviderPaymentMethodFields.TOKEN, "seeded-network-token"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
    AutraCardSession session = aSessionAwaitingItsPayer();

    tokenize.complete(completion(PayerOutcome.PAID, A_SEEDED_CARD), session, NOW);

    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(stored.tokenExpirationDate()).isEqualTo("102029");
  }

  @Test
  void a_card_whose_month_is_below_october_is_written_with_the_leading_zero_autra_writes() {
    seedACard(Map.of(ProviderPaymentMethodFields.TOKEN, "seeded-network-token"));
    AutraCardSession session = aSessionAwaitingItsPayer();

    tokenize.complete(completion(PayerOutcome.PAID, A_SEEDED_CARD), session, NOW);

    AutraStoredCard stored =
        cardSessions.findById(RUN, session.cardSessionId()).orElseThrow().storedCard();
    assertThat(stored).isNotNull();
    assertThat(stored.tokenExpirationDate()).isEqualTo("062029");
  }

  @Test
  void the_account_setting_names_one_of_the_two_answers_autra_publishes_and_no_third() {
    Map<String, String> namingBothOnOnePage =
        Map.of(AutraSaleDecline.SETTING, "FOUR_HUNDRED_WITH_ERRORS");

    assertThatThrownBy(() -> AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, namingBothOnOnePage))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(AutraSaleDecline.THE_VENDOR_PUBLISHES_BOTH_ON_ONE_PAGE);
  }

  @Test
  void a_setting_left_empty_reads_the_machine_readable_side_autra_declares() {
    assertThat(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT).saleDecline())
        .isEqualTo(AutraSaleDecline.TWO_HUNDRED_WITH_ERRORS);
    assertThat(
            AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, Map.of(AutraSaleDecline.SETTING, " "))
                .saleDecline())
        .isEqualTo(AutraSaleDecline.TWO_HUNDRED_WITH_ERRORS);
  }

  private AutraCardSession aSessionAwaitingItsPayer() {
    AutraApi api = api();
    String sessionId = theSessionIn(open(api, "{\"documentId\":\"" + DOCUMENT + "\"}"));
    return cardSessions.findById(RUN, sessionId).orElseThrow();
  }

  private void seedACard(Map<String, String> providerIds) {
    fixtures.seed(
        new NewPaymentMethodFixture(
            AutraApi.AUTRA,
            RUN,
            ACCOUNT,
            "owner-1",
            PaymentMethodKind.CARD,
            A_SEEDED_CARD,
            new FixtureCard("mastercard", "4242", 6, 2029, "debit"),
            providerIds,
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
  }

  private static HostedCompletionRequest completion(PayerOutcome outcome, String card) {
    return new HostedCompletionRequest(RUN, "a-session", outcome, card, ObjectOrigin.CONTROL_API);
  }

  private static String theSessionIn(AutraApiResponse response) {
    return bodyOf(response).text(AutraFields.HOSTED_CARD_SESSION_ID).orElseThrow();
  }

  private static String theCodeIn(AutraApiResponse response) {
    return bodyOf(response)
        .members()
        .get(AutraFields.ERRORS)
        .flatten()
        .get(AutraFields.CODE)
        .get(0);
  }

  private Map<String, List<String>> theOutboundFields() {
    return audit.all().get(audit.all().size() - 1).sanitizedBody();
  }

  private static AutraApiResponse open(AutraApi api, String body) {
    return api.cardSession(AutraWire.call(AutraApiController.CARD_SESSIONS, bearerOf(api), body));
  }

  private static Map<String, List<String>> bearerOf(AutraApi api) {
    return AutraWire.bearer(
        AutraTokenTest.theTokenIn(
            api.token(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS,
                    AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT)))));
  }

  private static AutraJson.JsonObject bodyOf(AutraApiResponse response) {
    return AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
  }
}
