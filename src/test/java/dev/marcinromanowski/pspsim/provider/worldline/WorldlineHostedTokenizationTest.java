package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class WorldlineHostedTokenizationTest {

  private static final TestRunId RUN = TestRunId.of("run-worldline");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private final WorldlineDoubles.Merchants merchants =
      new WorldlineDoubles.Merchants()
          .with(WorldlineDoubles.merchant(RUN, WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT));
  private final WorldlineDoubles.Tokenizations sessions = new WorldlineDoubles.Tokenizations();
  private final WorldlineDoubles.Payments payments = new WorldlineDoubles.Payments();
  private final WorldlineDoubles.Keys keys = new WorldlineDoubles.Keys();
  private final WorldlineDoubles.Audit audit = new WorldlineDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
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
        new ScenarioEngine(rules),
        ids,
        fixtures);
  }

  private WorldlineApiResponse open() {
    return api()
        .createHostedTokenization(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                "{}"),
            WorldlineWire.MERCHANT);
  }

  @Test
  void the_create_answers_the_three_fields_the_published_response_carries() {
    String body = WorldlineWire.bodyOf(open());
    assertThat(body)
        .contains(WorldlineFields.HOSTED_TOKENIZATION_ID)
        .contains(WorldlineFields.HOSTED_TOKENIZATION_URL)
        .contains(WorldlineFields.PARTIAL_REDIRECT_URL);
  }

  @Test
  void the_identifier_it_draws_is_thirty_two_lowercase_hexadecimal_digits() {
    assertThat(String.valueOf(open().providerObjectId())).matches("[0-9a-f]{32}");
  }

  @Test
  void the_partial_redirect_url_reproduces_the_double_slash_the_published_example_carries() {
    assertThat(
            CreateHostedTokenization.thePartialRedirectUrlOf("https://simulator.internal", "abc"))
        .isEqualTo("simulator.internal//hostedtokenization/tokenization/form/abc");
  }

  @Test
  void the_partial_redirect_url_keeps_the_port_and_the_path_of_the_public_base_url() {
    assertThat(CreateHostedTokenization.thePartialRedirectUrlOf("http://sim.local:8080/psp", "abc"))
        .isEqualTo("sim.local:8080/psp//hostedtokenization/tokenization/form/abc");
  }

  @Test
  void the_hosted_tokenization_url_carries_a_protocol_where_the_partial_one_carries_none() {
    assertThat(
            CreateHostedTokenization.theHostedTokenizationUrlOf(
                "https://simulator.internal", "abc"))
        .isEqualTo("https://simulator.internal/hostedtokenization/tokenization/form/abc");
    assertThat(
            CreateHostedTokenization.thePartialRedirectUrlOf("https://simulator.internal", "abc"))
        .doesNotContain("https://");
  }

  @Test
  void the_create_answers_the_page_this_simulator_serves_under_its_own_public_base_url() {
    WorldlineApiResponse answered = open();
    String id = String.valueOf(answered.providerObjectId());
    String body = WorldlineWire.bodyOf(answered);

    assertThat(body)
        .contains(
            "\""
                + WorldlineFields.HOSTED_TOKENIZATION_URL
                + "\":\""
                + WorldlineWire.PUBLIC_BASE_URL
                + WorldlineHostedTokenizationPage.pathOf(id)
                + "\"")
        .contains(
            "\""
                + WorldlineFields.PARTIAL_REDIRECT_URL
                + "\":\"simulator.internal//hostedtokenization/tokenization/form/"
                + id
                + "\"");
  }

  @Test
  void a_session_expires_exactly_three_hours_after_it_was_opened() {
    String id = String.valueOf(open().providerObjectId());
    assertThat(sessions.findById(RUN, id).orElseThrow().expiresAt())
        .isEqualTo(WHEN.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN));
  }

  @Test
  void a_session_no_payer_has_finished_is_read_back_as_an_empty_json_object() {
    String id = String.valueOf(open().providerObjectId());
    WorldlineApiResponse read = read(id);
    assertThat(read.httpStatus()).isEqualTo(WorldlineAnswers.OK);
    assertThat(WorldlineWire.bodyOf(read)).isEqualTo("{}");
  }

  @Test
  void a_tokens_array_the_run_holds_no_card_under_comes_back_as_invalid_tokens() {
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                    "{\"tokens\":[\"token-nobody-minted\"]}"),
                WorldlineWire.MERCHANT);
    assertThat(WorldlineWire.bodyOf(answered))
        .contains(WorldlineFields.INVALID_TOKENS)
        .contains("token-nobody-minted");
  }

  @Test
  void a_create_carrying_no_tokens_answers_no_invalid_tokens_member_at_all() {
    assertThat(WorldlineWire.bodyOf(open())).doesNotContain(WorldlineFields.INVALID_TOKENS);
  }

  @Test
  void a_body_this_endpoint_cannot_read_is_refused_before_any_session_is_opened() {
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                    "not json"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(answered))
        .contains(CreateHostedTokenization.A_BODY_THIS_ENDPOINT_CANNOT_READ);
  }

  @Test
  void a_read_of_an_identifier_this_merchant_holds_no_session_under_is_answered_404() {
    WorldlineApiResponse read = read("00000000000000000000000000000000");
    assertThat(read.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
    assertThat(WorldlineWire.bodyOf(read))
        .contains("hostedTokenizationId names no hosted tokenization session of this merchant");
  }

  @Test
  void the_404_of_an_unknown_session_carries_no_errors_id_because_worldline_publishes_none() {
    assertThat(WorldlineWire.bodyOf(read("00000000000000000000000000000000")))
        .doesNotContain("\"id\":");
  }

  @Test
  void a_payer_who_paid_leaves_a_token_and_the_published_token_status_on_the_session() {
    String id = String.valueOf(open().providerObjectId());
    HostedCompletionOutcome completed = complete(id, PayerOutcome.PAID);
    assertThat(completed).isInstanceOf(HostedCompletionOutcome.Completed.class);
    WorldlineTokenization session = sessions.findById(RUN, id).orElseThrow();
    assertThat(session.nativeStatus()).isEqualTo(WorldlineStatus.TOKEN_CREATED);
    assertThat(session.token())
        .as("the token left on the session is not the session identifier of the same shape")
        .isNotNull()
        .isNotEqualTo(id);
    assertThat(session.normalizedStatusAt(WHEN)).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void the_read_of_a_finished_session_carries_the_token_the_card_and_the_token_status() {
    String id = String.valueOf(open().providerObjectId());
    complete(id, PayerOutcome.PAID);
    String body = WorldlineWire.bodyOf(read(id));
    assertThat(body)
        .contains(WorldlineFields.TOKEN_STATUS)
        .contains(WorldlineStatus.TOKEN_CREATED)
        .contains(WorldlineFields.CARD_WITHOUT_CVV)
        .contains("************1111")
        .contains("\"expiryDate\":\"1230\"");
  }

  @Test
  void a_payer_who_named_a_seeded_card_stores_the_token_that_fixture_was_seeded_with() {
    fixtures.seed(
        new NewPaymentMethodFixture(
            WorldlineApi.WORLDLINE,
            RUN,
            "merchant-1",
            "owner-1",
            PaymentMethodKind.CARD,
            "seeded-card",
            new FixtureCard("MASTERCARD", "4444", 1, 2031, "credit"),
            Map.of(ProviderPaymentMethodFields.TOKEN, "token-seeded"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
    String id = String.valueOf(open().providerObjectId());

    new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids)
        .complete(
            new HostedCompletionRequest(
                RUN, id, PayerOutcome.PAID, "seeded-card", ObjectOrigin.CONTROL_API),
            sessions.findById(RUN, id).orElseThrow(),
            WHEN);

    WorldlineTokenization session = sessions.findById(RUN, id).orElseThrow();
    assertThat(session.token()).isEqualTo("token-seeded");
    assertThat(session.cardNumber()).isEqualTo("************4444");
  }

  @Test
  void a_payer_who_named_the_card_the_samples_print_stores_a_token_this_simulator_minted() {
    String id = String.valueOf(open().providerObjectId());

    complete(id, PayerOutcome.PAID);

    assertThat(sessions.findById(RUN, id).orElseThrow().token())
        .matches("[0-9a-f]{32}")
        .as("the token minted on the page is not the session identifier of the same shape")
        .isNotEqualTo(id);
  }

  @Test
  void a_token_this_simulator_mints_is_never_temporary() {
    String id = String.valueOf(open().providerObjectId());
    complete(id, PayerOutcome.PAID);
    assertThat(WorldlineWire.bodyOf(read(id))).contains("\"isTemporary\":false");
  }

  @Test
  void a_payer_who_left_the_page_moves_the_session_to_the_expired_status_this_simulator_owns() {
    String id = String.valueOf(open().providerObjectId());
    complete(id, PayerOutcome.ABANDONED);
    assertThat(sessions.findById(RUN, id).orElseThrow().nativeStatus())
        .isEqualTo(WorldlineStatus.EXPIRED);
  }

  @Test
  void no_payer_is_declined_on_a_page_that_charges_nothing() {
    String id = String.valueOf(open().providerObjectId());
    assertThat(complete(id, PayerOutcome.DECLINED))
        .isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class)
        .asInstanceOf(
            InstanceOfAssertFactories.type(HostedCompletionOutcome.RefusedByTheProvider.class))
        .extracting(HostedCompletionOutcome.RefusedByTheProvider::inItsOwnWords)
        .isEqualTo(TokenizeOnTheHostedPage.NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE);
    assertThat(TokenizeOnTheHostedPage.THE_PAYER_STEPS.theStepsAPayerMayTake())
        .containsExactly(PayerOutcome.PAID, PayerOutcome.ABANDONED);
    assertThat(TokenizeOnTheHostedPage.THE_PAYER_STEPS.whatItHasInsteadOf(PayerOutcome.DECLINED))
        .isEqualTo(TokenizeOnTheHostedPage.NO_PAYER_IS_DECLINED_ON_A_TOKENIZATION_PAGE);
  }

  @Test
  void a_session_a_payer_already_finished_offers_no_second_page() {
    String id = String.valueOf(open().providerObjectId());
    complete(id, PayerOutcome.PAID);
    assertThat(offer(id)).isInstanceOf(HostedCompletionOffer.NotOffered.class);
  }

  @Test
  void a_session_past_the_published_three_hours_offers_no_page_and_reads_back_as_unknown() {
    String id = String.valueOf(open().providerObjectId());
    TokenizeOnTheHostedPage later =
        new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids);
    Instant after = WHEN.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN).plusSeconds(1);
    assertThat(later.offerOn(sessions.findById(RUN, id).orElseThrow(), after))
        .isInstanceOf(HostedCompletionOffer.NotOffered.class);
  }

  @Test
  void a_rule_that_declines_the_create_answers_the_code_it_named_with_the_published_errors_id() {
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CREATE_HOSTED_TOKENIZATION),
            new ScenarioAction.TerminalFailure(WorldlineErrors.THE_CARD_WAS_DECLINED),
            null));
    WorldlineApiResponse answered = open();
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(answered))
        .contains(WorldlineErrors.THE_CARD_WAS_DECLINED)
        .contains(WorldlineErrors.PAYMENT_PRODUCT_NOT_AVAILABLE);
  }

  @Test
  void the_partial_redirect_url_is_withheld_from_the_audit_trail_as_the_full_one_is() {
    open();

    assertThat(audit.all())
        .filteredOn(
            record -> record.sanitizedBody().containsKey(WorldlineFields.PARTIAL_REDIRECT_URL))
        .as(
            "the partial address names the same page under the same identifier and carries"
                + " everything the full one does but the protocol")
        .isNotEmpty()
        .allSatisfy(
            record ->
                assertThat(record.sanitizedBody().get(WorldlineFields.PARTIAL_REDIRECT_URL))
                    .containsExactly(Redaction.REDACTED));
  }

  @Test
  void neither_address_of_the_page_is_anywhere_in_the_trail_while_both_are_on_the_wire() {
    WorldlineApiResponse answered = open();
    String id = String.valueOf(answered.providerObjectId());
    String full = "https://simulator.internal/hostedtokenization/tokenization/form/" + id;
    String partial = "simulator.internal//hostedtokenization/tokenization/form/" + id;

    assertThat(WorldlineWire.bodyOf(answered)).contains(full).contains(partial);
    assertThat(String.valueOf(audit.all()))
        .as("no row of the trail carries either address under any key")
        .doesNotContain(full)
        .doesNotContain(partial);
  }

  @Test
  void the_hosted_tokenization_url_is_withheld_from_the_audit_trail() {
    String id = String.valueOf(open().providerObjectId());

    assertThat(audit.all())
        .filteredOn(
            record -> record.sanitizedBody().containsKey(WorldlineFields.HOSTED_TOKENIZATION_URL))
        .isNotEmpty()
        .allSatisfy(
            record ->
                assertThat(record.sanitizedBody().get(WorldlineFields.HOSTED_TOKENIZATION_URL))
                    .containsExactly(Redaction.REDACTED)
                    .doesNotContain(
                        CreateHostedTokenization.theHostedTokenizationUrlOf(
                            WorldlineWire.PUBLIC_BASE_URL, id)));
  }

  private WorldlineApiResponse read(String hostedTokenizationId) {
    return api()
        .getHostedTokenization(
            WorldlineWire.call(
                WorldlineWire.GET,
                WorldlineApiController.theHostedTokenizationOf(
                    WorldlineWire.MERCHANT, hostedTokenizationId),
                ""),
            WorldlineWire.MERCHANT,
            hostedTokenizationId);
  }

  private HostedCompletionOffer offer(String hostedTokenizationId) {
    return new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids)
        .offerOn(sessions.findById(RUN, hostedTokenizationId).orElseThrow(), WHEN);
  }

  private HostedCompletionOutcome complete(String hostedTokenizationId, PayerOutcome outcome) {
    return new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids)
        .complete(
            new HostedCompletionRequest(
                RUN,
                hostedTokenizationId,
                outcome,
                outcome == PayerOutcome.PAID
                    ? WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS
                    : null,
                ObjectOrigin.CONTROL_API),
            sessions.findById(RUN, hostedTokenizationId).orElseThrow(),
            WHEN);
  }

  @Test
  void an_unnamed_alias_is_refused_with_the_aliases_this_run_does_offer() {
    String id = String.valueOf(open().providerObjectId());
    HostedCompletionOutcome refused =
        new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids)
            .complete(
                new HostedCompletionRequest(
                    RUN, id, PayerOutcome.PAID, "no-such-card", ObjectOrigin.CONTROL_API),
                sessions.findById(RUN, id).orElseThrow(),
                WHEN);
    assertThat(refused)
        .asInstanceOf(
            InstanceOfAssertFactories.type(HostedCompletionOutcome.RefusedByTheProvider.class))
        .extracting(HostedCompletionOutcome.RefusedByTheProvider::inItsOwnWords)
        .asString()
        .contains(WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS);
  }

  @Test
  void the_card_the_published_samples_print_carries_the_masked_pan_and_expiry_they_print() {
    WorldlinePayerCard card = WorldlinePayerCards.theCardWorldlinePrints();
    assertThat(card.theCardNumberAsWorldlineWritesIt()).isEqualTo("************1111");
    assertThat(card.theExpiryDateAsWorldlineWritesIt()).isEqualTo("1230");
  }

  @Test
  void a_completion_that_lost_the_race_to_another_request_reports_what_the_session_holds_now() {
    String id = String.valueOf(open().providerObjectId());
    sessions.movedByAnotherRequestFirst(id);
    assertThat(complete(id, PayerOutcome.PAID))
        .isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
  }

  @Test
  void a_map_of_no_tokens_reads_as_an_empty_list_rather_than_as_a_missing_member() {
    assertThat(CreateHostedTokenization.theTokensIn(WorldlineJson.empty())).isEmpty();
  }

  @Test
  void a_tokens_array_carrying_a_blank_string_names_no_token_at_all() {
    assertThat(
            CreateHostedTokenization.theTokensIn(
                WorldlineJsonReader.objectIn("{\"tokens\":[\"\",\"a\"]}").orElseThrow()))
        .isEqualTo(List.of("a"));
  }
}
