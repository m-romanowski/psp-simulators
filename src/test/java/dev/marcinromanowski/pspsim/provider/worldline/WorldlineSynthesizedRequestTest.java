package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.RenderedResponse;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class WorldlineSynthesizedRequestTest {

  private static final TestRunId RUN = TestRunId.of("run-worldline");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private final WorldlineMerchant merchant =
      WorldlineDoubles.merchant(RUN, WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT);

  @Test
  void a_tokenization_started_on_a_screen_posts_to_the_merchants_own_hosted_tokenization_route() {
    WorldlineApiCall call =
        WorldlineOrigination.tokenizationCallOf(
            OriginationRequest.of(
                RUN,
                WorldlineWire.ACCOUNT,
                WorldlineOrigination.A_TOKENIZATION,
                Map.of(),
                ObjectOrigin.UI),
            merchant);

    assertThat(call.method()).isEqualTo(WorldlineOrigination.WIRE_METHOD);
    assertThat(call.path())
        .isEqualTo(WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT));
    assertThat(call.rawBody()).isEqualTo("{}");
  }

  @Test
  void the_tokens_a_tester_typed_are_sent_as_the_array_the_published_response_reports_on() {
    WorldlineApiCall call =
        WorldlineOrigination.tokenizationCallOf(
            OriginationRequest.of(
                RUN,
                WorldlineWire.ACCOUNT,
                WorldlineOrigination.A_TOKENIZATION,
                Map.of(WorldlineFields.TOKENS, "one, two , "),
                ObjectOrigin.UI),
            merchant);

    assertThat(call.rawBody()).isEqualTo("{\"tokens\":[\"one\",\"two\"]}");
  }

  @Test
  void a_payment_started_on_a_screen_carries_the_order_the_card_input_and_nothing_else() {
    WorldlineApiCall call =
        WorldlineOrigination.paymentCallOf(
            OriginationRequest.of(
                RUN,
                WorldlineWire.ACCOUNT,
                WorldlineOrigination.A_PAYMENT,
                Map.of(
                    WorldlineFields.AMOUNT, "5000",
                    WorldlineFields.CURRENCY_CODE, "PLN",
                    WorldlineFields.AUTHORIZATION_MODE, "PRE_AUTHORIZATION",
                    WorldlineFields.TOKEN, "token-1",
                    WorldlineFields.MERCHANT_REFERENCE, "order-123",
                    WorldlineFields.MERCHANT_CUSTOMER_ID, "customer-1"),
                ObjectOrigin.UI),
            merchant);

    assertThat(call.path()).isEqualTo(WorldlineApiController.thePaymentsOf(WorldlineWire.MERCHANT));
    assertThat(call.rawBody())
        .contains("\"amount\":5000")
        .contains("\"currencyCode\":\"PLN\"")
        .contains("\"merchantReference\":\"order-123\"")
        .contains("\"merchantCustomerId\":\"customer-1\"")
        .contains("\"token\":\"token-1\"")
        .contains("\"authorizationMode\":\"PRE_AUTHORIZATION\"")
        .doesNotContain(WorldlineFields.HOSTED_TOKENIZATION_ID);
  }

  @Test
  void a_payment_started_against_a_session_sends_the_hosted_tokenization_id_and_no_token() {
    WorldlineApiCall call =
        WorldlineOrigination.paymentCallOf(
            OriginationRequest.of(
                RUN,
                WorldlineWire.ACCOUNT,
                WorldlineOrigination.A_PAYMENT,
                Map.of(
                    WorldlineFields.AMOUNT, "5000",
                    WorldlineFields.CURRENCY_CODE, "PLN",
                    WorldlineFields.AUTHORIZATION_MODE, "SALE",
                    WorldlineFields.HOSTED_TOKENIZATION_ID, "session-1"),
                ObjectOrigin.UI),
            merchant);

    assertThat(call.rawBody())
        .contains("\"hostedTokenizationId\":\"session-1\"")
        .doesNotContain("\"token\"");
  }

  @Test
  void a_capture_asked_for_on_a_screen_posts_the_amount_at_the_root_of_the_capture_route() {
    WorldlineApiCall call =
        WorldlineObjectActions.callOf(
            ObjectActionRequest.of(
                RUN, "3066019730_0", ObjectAction.CAPTURE, "2000", ObjectOrigin.UI),
            merchant);

    assertThat(call.path())
        .isEqualTo(
            WorldlineApiController.thePaymentResourceOf(WorldlineWire.MERCHANT, "3066019730_0")
                + WorldlineApiController.CAPTURE);
    assertThat(call.rawBody()).isEqualTo("{\"amount\":2000,\"isFinal\":false}");
  }

  @Test
  void a_cancel_asked_for_on_a_screen_posts_no_amount_at_all_to_the_cancel_route() {
    WorldlineApiCall call =
        WorldlineObjectActions.callOf(
            ObjectActionRequest.of(RUN, "3066019730_1", ObjectAction.CANCEL, null, ObjectOrigin.UI),
            merchant);

    assertThat(call.path())
        .isEqualTo(
            WorldlineApiController.thePaymentResourceOf(WorldlineWire.MERCHANT, "3066019730_1")
                + WorldlineApiController.CANCEL);
    assertThat(call.rawBody()).isEqualTo("{}");
  }

  @Test
  void an_action_a_scenario_answered_instead_of_the_operation_names_the_rule_that_did_it() {
    ObjectActionOutcome answered =
        WorldlineObjectActions.outcomeOf(
            WorldlineOperationResult.answeredInsteadOf(
                "scn_1", new RenderedResponse(500, "text/plain", new byte[0]).asDelivered()),
            aPayment());

    assertThat(answered)
        .asInstanceOf(
            InstanceOfAssertFactories.type(ObjectActionOutcome.AnsweredByAScenarioInstead.class))
        .extracting(ObjectActionOutcome.AnsweredByAScenarioInstead::scenarioId)
        .isEqualTo("scn_1");
  }

  @Test
  void an_action_the_provider_refused_reports_the_words_the_error_envelope_carried() {
    ObjectActionOutcome refused =
        WorldlineObjectActions.outcomeOf(
            new WorldlineAnswers(new WorldlineDoubles.CountingIds())
                .actionNotAllowedOn("3066019730_0", "this payment is closed"),
            aPayment());

    assertThat(refused)
        .asInstanceOf(
            InstanceOfAssertFactories.type(ObjectActionOutcome.RefusedByTheProvider.class))
        .extracting(ObjectActionOutcome.RefusedByTheProvider::inItsOwnWords)
        .asString()
        .contains(WorldlineErrors.ACTION_NOT_ALLOWED_ON_TRANSACTION)
        .contains("400");
  }

  @Test
  void an_origination_a_scenario_answered_instead_of_the_operation_names_the_rule_that_did_it() {
    OriginationOutcome answered =
        WorldlineOrigination.outcomeOf(
            WorldlineOperationResult.answeredInsteadOf(
                "scn_2", new RenderedResponse(502, "text/plain", new byte[0]).asDelivered()),
            OriginationRequest.of(
                RUN,
                WorldlineWire.ACCOUNT,
                WorldlineOrigination.A_TOKENIZATION,
                Map.of(),
                ObjectOrigin.UI));

    assertThat(answered).isInstanceOf(OriginationOutcome.AnsweredByAScenarioInstead.class);
  }

  @Test
  void an_origination_that_succeeded_says_what_the_way_it_was_started_leaves_behind() {
    OriginationOutcome started =
        WorldlineOrigination.outcomeOf(
            WorldlineOperationResult.succeeded(
                "abc", WorldlineBody.of(WorldlineAnswers.OK, WorldlineJson.empty())),
            OriginationRequest.of(
                RUN,
                WorldlineWire.ACCOUNT,
                WorldlineOrigination.A_TOKENIZATION,
                Map.of(),
                ObjectOrigin.UI));

    assertThat(started)
        .asInstanceOf(InstanceOfAssertFactories.type(OriginationOutcome.Originated.class))
        .satisfies(
            originated -> {
              assertThat(originated.providerObjectId()).isEqualTo("abc");
              assertThat(originated.whatExistsNow()).contains("three hours");
            });
  }

  @Test
  void a_result_a_rule_let_through_carries_the_rule_that_decided_it_on_a_success_only() {
    WorldlineOperationResult succeeded =
        WorldlineOperationResult.succeeded(
            "abc", WorldlineBody.of(WorldlineAnswers.OK, WorldlineJson.empty()));
    WorldlineOperationResult failed =
        WorldlineOperationResult.failed(
            WorldlineRefusals.theBodyWasRefused(),
            WorldlineBody.of(WorldlineAnswers.BAD_REQUEST, WorldlineJson.empty()));

    assertThat(succeeded.proceededUnder("scn_3").scenarioId()).isEqualTo("scn_3");
    assertThat(failed.proceededUnder("scn_3").scenarioId()).isNull();
    assertThat(failed.declinedBy("scn_4").scenarioId()).isEqualTo("scn_4");
    assertThat(succeeded.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void a_body_that_rendered_nothing_carries_no_content_type_and_no_fields() {
    WorldlineBody nothing = WorldlineBody.nothingRendered();

    assertThat(nothing.contentType()).isNull();
    assertThat(nothing.bytes()).isEmpty();
    assertThat(nothing.text()).isEmpty();
    assertThat(nothing.fields()).isEmpty();
  }

  @Test
  void a_body_that_rendered_json_says_so_and_flattens_the_members_it_carries() {
    WorldlineBody body =
        WorldlineBody.of(
            WorldlineAnswers.OK,
            WorldlineJson.object(Map.of(WorldlineFields.ID, WorldlineJson.text("abc"))));

    assertThat(body.contentType()).isEqualTo(WorldlineFields.JSON);
    assertThat(body.fields()).containsEntry(WorldlineFields.ID, List.of("abc"));
    assertThat(body.alsoCarrying("X-Extra", "v").headers()).containsKey("X-Extra");
  }

  @Test
  void the_published_amount_bounds_admit_their_own_edges_and_nothing_outside_them() {
    assertThat(WorldlineAmounts.isWithinThePublishedBounds(WorldlineAmounts.SMALLEST)).isTrue();
    assertThat(WorldlineAmounts.isWithinThePublishedBounds(WorldlineAmounts.LARGEST)).isTrue();
    assertThat(WorldlineAmounts.isWithinThePublishedBounds(WorldlineAmounts.SMALLEST - 1))
        .isFalse();
    assertThat(WorldlineAmounts.isWithinThePublishedBounds(WorldlineAmounts.LARGEST + 1)).isFalse();
  }

  @Test
  void an_amount_that_is_not_a_whole_number_of_minor_units_reads_as_no_amount_at_all() {
    assertThat(WorldlineAmounts.wholeMinorUnits("50.00")).isEmpty();
    assertThat(WorldlineAmounts.wholeMinorUnits("5000")).contains(Long.valueOf(5000L));
    assertThat(WorldlineAmounts.minorUnitsOf(Amount.of("not a number"))).isZero();
  }

  @Test
  void a_currency_code_is_three_characters_and_a_code_of_any_other_length_is_not() {
    assertThat(WorldlineAmounts.isAThreeLetterCode("PLN")).isTrue();
    assertThat(WorldlineAmounts.isAThreeLetterCode("PL")).isFalse();
    assertThat(WorldlineAmounts.isAThreeLetterCode("PLNX")).isFalse();
  }

  @Test
  void an_authorization_header_of_the_published_scheme_yields_its_key_and_its_signature() {
    String header = WorldlineSignature.SCHEME + "the-key:the-signature";

    assertThat(WorldlineSignature.apiKeyIn(header)).contains("the-key");
    assertThat(WorldlineSignature.signatureIn(header)).contains("the-signature");
  }

  @Test
  void the_two_halves_of_the_published_scheme_are_read_apart_from_one_another() {
    assertThat(WorldlineSignature.signatureIn(WorldlineSignature.SCHEME + ":only-a-signature"))
        .contains("only-a-signature");
    assertThat(WorldlineSignature.apiKeyIn(WorldlineSignature.SCHEME + ":only-a-signature"))
        .isEmpty();
  }

  @Test
  void an_authorization_header_missing_a_half_of_the_published_scheme_yields_neither() {
    assertThat(WorldlineSignature.apiKeyIn("Bearer x")).isEmpty();
    assertThat(WorldlineSignature.signatureIn("Bearer x")).isEmpty();
    assertThat(WorldlineSignature.apiKeyIn(WorldlineSignature.SCHEME + ":only-a-signature"))
        .isEmpty();
    assertThat(WorldlineSignature.signatureIn(WorldlineSignature.SCHEME + "only-a-key")).isEmpty();
    assertThat(WorldlineSignature.signatureIn(WorldlineSignature.SCHEME + "only-a-key:")).isEmpty();
  }

  @Test
  void an_identifier_that_opens_with_the_separator_has_an_empty_payid_half_rather_than_all_of_it() {
    assertThat(WorldlineIdentifiers.thePayIdIn("_1")).isEmpty();
  }

  @Test
  void a_card_with_no_brand_is_described_by_the_word_card_and_a_branded_one_by_its_brand() {
    assertThat(WorldlinePayerCards.theCardWorldlinePrints().describedForAPayer())
        .startsWith("card ending");
    assertThat(
            new WorldlinePayerCard("alias", "MASTERCARD", "4444", 1, 2031, null)
                .describedForAPayer())
        .startsWith("MASTERCARD ending");
  }

  @Test
  void a_payment_whose_ledger_holds_nothing_has_no_authorization_open_to_capture_against() {
    assertThat(aPayment().authorizationOpen()).isFalse();
  }

  @Test
  void each_published_authorization_mode_carries_the_sentence_the_vendor_publishes_for_it() {
    assertThat(WorldlineAuthorizationMode.PRE_AUTHORIZATION.whatItDoes())
        .isEqualTo(
            "Only blocks the order.amountOfMoney.amount up to 30 days. You can cancel the"
                + " authorisation any time, both fully and partially. Supports both full and"
                + " partial captures.");
    assertThat(WorldlineAuthorizationMode.FINAL_AUTHORIZATION.whatItDoes())
        .isEqualTo(
            "Only blocks the order.amountOfMoney.amount for 7 days. You can cancel the"
                + " authorisation any time, but only fully. Supports only full captures.");
    assertThat(WorldlineAuthorizationMode.SALE.whatItDoes())
        .isEqualTo("Blocks and captures the order.amountOfMoney.amount in one go.");
  }

  @Test
  void every_way_of_starting_a_payment_prints_what_the_vendor_publishes_each_mode_does() {
    DeclaredOrigination way =
        WorldlineOrigination.DECLARED.wayNamed(WorldlineOrigination.A_PAYMENT).orElseThrow();

    assertThat(way.field(WorldlineFields.AUTHORIZATION_MODE).orElseThrow().theValuesItAccepts())
        .contains("Supports both full and partial captures.")
        .contains("Supports only full captures.")
        .contains("Blocks and captures the order.amountOfMoney.amount in one go.");
  }

  @Test
  void a_preset_that_expands_into_rules_expands_into_rules_that_let_the_operation_through() {
    PresetExpansion expanded =
        WorldlinePresets.of(
            new PresetRequest(
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
                RUN,
                1,
                ScenarioMatch.anyRequest()));

    assertThat(((PresetExpansion.Rules) expanded).rules())
        .extracting(NewScenarioRule::action)
        .hasSize(2)
        .allSatisfy(action -> assertThat(action).isInstanceOf(ScenarioAction.FixedSuccess.class));
  }

  @Test
  void the_published_identifier_grammar_reads_back_the_payid_half_of_a_maintenance_identifier() {
    assertThat(WorldlineIdentifiers.thePayIdIn("3066019730_1")).isEqualTo("3066019730");
    assertThat(WorldlineIdentifiers.thePayIdIn("nothing-underscored"))
        .isEqualTo("nothing-underscored");
    assertThat(WorldlineIdentifiers.paymentIdOf("3066019730", 2)).isEqualTo("3066019730_2");
    assertThat("3066019730_2").matches(WorldlineIdentifiers.A_PAYMENT_ID.pattern());
  }

  @Test
  void every_landing_this_account_may_be_configured_for_names_its_own_status_and_code() {
    assertThat(WorldlineCaptureLanding.of(WorldlineCaptureLanding.SETTING, null))
        .isEqualTo(WorldlineCaptureLanding.CAPTURED_9);
    assertThat(WorldlineCaptureLanding.CAPTURE_REQUESTED_91.statusCode())
        .isEqualTo(WorldlineStatus.CODE_CAPTURE_REQUESTED);
    assertThat(WorldlineCaptureLanding.PENDING_CAPTURE_9.statusCategory())
        .isEqualTo(WorldlineStatus.CATEGORY_PENDING_MERCHANT);
    assertThatThrownBy(
            () -> WorldlineCaptureLanding.of(WorldlineCaptureLanding.SETTING, "CAPTURED_LATER"))
        .hasMessageContaining(WorldlineCaptureLanding.SETTING);
  }

  @Test
  void an_authentication_mode_this_simulator_does_not_offer_is_refused_where_it_is_configured() {
    assertThat(WorldlineAuthentication.of(WorldlineAuthentication.SETTING, ""))
        .isEqualTo(WorldlineAuthentication.THE_SIGNATURE_IS_VERIFIED);
    assertThatThrownBy(
            () -> WorldlineAuthentication.of(WorldlineAuthentication.SETTING, "SOMETIMES"))
        .hasMessageContaining(WorldlineAuthentication.SETTING);
  }

  @Test
  void the_three_published_authorization_modes_differ_in_what_they_let_a_maintenance_call_do() {
    assertThat(WorldlineAuthorizationMode.PRE_AUTHORIZATION.allowsAPartialCapture()).isTrue();
    assertThat(WorldlineAuthorizationMode.PRE_AUTHORIZATION.allowsAPartialCancellation()).isTrue();
    assertThat(WorldlineAuthorizationMode.FINAL_AUTHORIZATION.allowsAPartialCapture()).isFalse();
    assertThat(WorldlineAuthorizationMode.FINAL_AUTHORIZATION.allowsAPartialCancellation())
        .isFalse();
    assertThat(WorldlineAuthorizationMode.SALE.capturesAtOnce()).isTrue();
    assertThat(WorldlineAuthorizationMode.PRE_AUTHORIZATION.capturesAtOnce()).isFalse();
    assertThat(WorldlineAuthorizationMode.of("UNSPECIFIED")).isEmpty();
    assertThat(WorldlineAuthorizationMode.thePublishedValues())
        .isEqualTo("PRE_AUTHORIZATION, FINAL_AUTHORIZATION, SALE");
  }

  @Test
  void a_seeded_fixture_offers_a_second_card_beside_the_one_the_published_samples_print() {
    InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
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
    WorldlinePayerCards cards = new WorldlinePayerCards(fixtures);

    assertThat(cards.aliasesOffered(RUN))
        .contains(WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS)
        .contains("seeded-card");
    assertThat(cards.holdingTheToken(RUN, "token-seeded"))
        .get()
        .extracting(WorldlinePayerCard::alias)
        .isEqualTo("seeded-card");
    assertThat(cards.holdingTheToken(RUN, "nobody-seeded-this")).isEmpty();
    assertThat(cards.resolve(RUN, "seeded-card"))
        .get()
        .extracting(WorldlinePayerCard::theCardNumberAsWorldlineWritesIt)
        .isEqualTo("************4444");
    assertThat(cards.resolve(RUN, "seeded-card"))
        .get()
        .extracting(WorldlinePayerCard::theExpiryDateAsWorldlineWritesIt)
        .isEqualTo("0131");
  }

  @Test
  void a_card_a_fixture_seeds_without_a_token_is_offered_and_mints_a_token_of_this_simulator() {
    WorldlinePayerCard published = WorldlinePayerCards.theCardWorldlinePrints();

    assertThat(published.namesATokenOfItsOwn()).isFalse();
    assertThat(published.describedForAPayer()).contains("1111").contains("1230");
  }

  @Test
  void a_payment_says_why_no_maintenance_operation_is_allowed_on_it_and_which_case_it_is() {
    assertThat(aPayment().whyNoMaintenanceOperationIsAllowed()).contains("PENDING_CAPTURE");
    assertThat(aClosedPayment().whyNoMaintenanceOperationIsAllowed())
        .contains("this payment is closed");
  }

  @Test
  void a_payment_that_answers_to_one_identifier_is_answering_under_it_and_names_the_next_one() {
    WorldlinePayment payment = aPayment();

    assertThat(payment.theIdentifierItIsAnsweringUnder()).isEqualTo(payment.paymentId());
    assertThat(payment.theNextIdentifierAMaintenanceOperationMints()).isEqualTo("3066019730_1");
    assertThat(payment.identity().keepsOneIdentifierForLife()).isTrue();
    assertThat(payment.refundsAccepted()).isFalse();
  }

  @Test
  void a_payment_that_has_answered_to_two_identifiers_is_answering_under_the_later_one() {
    WorldlinePayment payment = aPaymentAnsweringAlsoTo("3066019730_1");

    assertThat(payment.theIdentifierItIsAnsweringUnder()).isEqualTo("3066019730_1");
    assertThat(payment.theNextIdentifierAMaintenanceOperationMints()).isEqualTo("3066019730_2");
    assertThat(payment.identity().members()).containsExactly("3066019730_0", "3066019730_1");
  }

  @Test
  void a_status_this_contract_publishes_nowhere_normalizes_to_unknown_on_either_object_kind() {
    assertThat(WorldlineStatus.normalizedPaymentStatus("SOMETHING_ELSE"))
        .isEqualTo(NormalizedStatus.UNKNOWN);
    assertThat(WorldlineStatus.normalizedTokenizationStatus("SOMETHING_ELSE"))
        .isEqualTo(NormalizedStatus.UNKNOWN);
  }

  @Test
  void every_published_payment_status_of_this_contract_normalizes_to_the_state_it_maps_to() {
    assertThat(WorldlineStatus.normalizedPaymentStatus(WorldlineStatus.CREATED))
        .isEqualTo(NormalizedStatus.PROCESSING);
    assertThat(WorldlineStatus.normalizedPaymentStatus(WorldlineStatus.REJECTED))
        .isEqualTo(NormalizedStatus.FAILED);
    assertThat(WorldlineStatus.normalizedPaymentStatus(WorldlineStatus.REJECTED_CAPTURE))
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(WorldlineStatus.normalizedPaymentStatus(WorldlineStatus.CANCELLATION_REJECTED))
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(WorldlineStatus.normalizedPaymentStatus(WorldlineStatus.CANCELLED))
        .isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void a_tokenization_says_why_it_is_no_longer_waiting_and_which_of_the_two_cases_it_is() {
    WorldlineTokenization awaiting = aTokenization(WorldlineStatus.AWAITING_THE_PAYER);

    assertThat(aTokenization(WorldlineStatus.TOKEN_CREATED).whyItIsNoLongerWaitingAt(WHEN))
        .contains("already tokenized a card");
    assertThat(aTokenization(WorldlineStatus.EXPIRED).whyItIsNoLongerWaitingAt(WHEN))
        .contains("three hours");
    assertThat(aTokenization("SOMETHING_ELSE").whyItIsNoLongerWaitingAt(WHEN))
        .contains(WorldlineStatus.NO_STATUS_IS_PUBLISHED_FOR_A_TOKENIZATION_SESSION);
    assertThat(awaiting.holdsAToken()).isFalse();
    assertThat(awaiting.hasExpiredBy(WHEN.plusSeconds(1))).isFalse();
    assertThat(awaiting.hasExpiredBy(awaiting.expiresAt())).isTrue();
  }

  @Test
  void the_life_span_expires_a_session_still_awaiting_a_payer_and_no_session_in_another_state() {
    Instant past = WHEN.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN).plusSeconds(1);

    assertThat(aTokenization(WorldlineStatus.AWAITING_THE_PAYER).statusAt(past))
        .isEqualTo("EXPIRED");
    assertThat(aTokenization(WorldlineStatus.TOKEN_CREATED).statusAt(past)).isEqualTo("CREATED");
    assertThat(aTokenization("SOMETHING_ELSE").statusAt(past)).isEqualTo("SOMETHING_ELSE");
    assertThat(aTokenization(WorldlineStatus.TOKEN_CREATED).normalizedStatusAt(past))
        .isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(aTokenization(WorldlineStatus.TOKEN_CREATED).whyItIsNoLongerWaitingAt(past))
        .contains("already tokenized a card")
        .doesNotContain("three hours");
    assertThat(aTokenization(WorldlineStatus.AWAITING_THE_PAYER).isAwaitingThePayerAt(past))
        .isFalse();
    assertThat(aTokenization(WorldlineStatus.TOKEN_CREATED).isAwaitingThePayerAt(past)).isFalse();
  }

  @Test
  void every_preset_this_provider_meets_either_expands_into_rules_or_says_what_it_lacks() {
    for (ScenarioPreset preset : ScenarioPreset.values()) {
      PresetExpansion expanded =
          WorldlinePresets.of(new PresetRequest(preset, RUN, 1, ScenarioMatch.anyRequest()));
      assertThat(expanded)
          .isInstanceOfAny(PresetExpansion.Rules.class, PresetExpansion.NotExpressible.class);
    }
  }

  @Test
  void the_preset_that_answers_without_the_operation_writes_two_steps_per_operation() {
    PresetExpansion expanded =
        WorldlinePresets.of(
            new PresetRequest(
                ScenarioPreset.PSP_5XX_THEN_SUCCESS, RUN, 1, ScenarioMatch.anyRequest()));

    assertThat(expanded)
        .asInstanceOf(InstanceOfAssertFactories.type(PresetExpansion.Rules.class))
        .extracting(PresetExpansion.Rules::rules)
        .asInstanceOf(InstanceOfAssertFactories.list(Object.class))
        .hasSize(2 * WorldlineOperations.VOCABULARY.operationNames().size());
  }

  @Test
  void the_decline_codes_a_rule_may_name_are_the_published_ones_and_the_two_refusal_statuses() {
    assertThat(WorldlineErrors.theCodesAScenarioMayName())
        .isEqualTo("30921001|40001134|40001137|50001054|93|63");
    assertThat(WorldlineErrors.isAMaintenanceRefusalCode("93")).isTrue();
    assertThat(WorldlineErrors.isAMaintenanceRefusalCode("63")).isTrue();
    assertThat(WorldlineErrors.isAMaintenanceRefusalCode("30921001")).isFalse();
  }

  @Test
  void a_key_header_named_in_any_casing_reaches_the_mechanism_and_a_blank_one_does_not() {
    assertThat(WorldlineIdempotence.keyIn(Map.of("x-gcs-idempotence-key", List.of("k"))))
        .get()
        .extracting(WorldlineIdempotence.Sent::value)
        .isEqualTo("k");
    assertThat(
            WorldlineIdempotence.keyIn(
                Map.of(
                    WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
                    List.of("  "))))
        .isEmpty();
    assertThat(WorldlineIdempotence.keyIn(Map.of())).isEmpty();
    assertThat(WorldlineIdempotence.carriesTwoSpellingsThatDisagree(Map.of())).isFalse();
  }

  private WorldlinePayment aPayment() {
    return aPaymentWith(WorldlineStatus.PENDING_CAPTURE, false, List.of());
  }

  private WorldlinePayment aClosedPayment() {
    return aPaymentWith(WorldlineStatus.CAPTURED, true, List.of());
  }

  private WorldlinePayment aPaymentAnsweringAlsoTo(String minted) {
    return aPaymentWith(WorldlineStatus.PENDING_CAPTURE, false, List.of(minted));
  }

  private WorldlinePayment aPaymentWith(
      String nativeStatus, boolean closed, List<String> maintenanceIds) {
    return new WorldlinePayment(
        RUN,
        "3066019730_0",
        maintenanceIds,
        WorldlineWire.ACCOUNT,
        WorldlineWire.MERCHANT,
        null,
        null,
        "token-1",
        null,
        "************1111",
        "1230",
        WorldlineAmounts.asAnAmount(5000L),
        "PLN",
        WorldlineAuthorizationMode.PRE_AUTHORIZATION,
        nativeStatus,
        WorldlineStatus.CATEGORY_PENDING_MERCHANT,
        WorldlineStatus.CODE_PENDING_CAPTURE,
        closed,
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        WHEN,
        WHEN);
  }

  private WorldlineTokenization aTokenization(String nativeStatus) {
    return new WorldlineTokenization(
        RUN,
        "a".repeat(32),
        WorldlineWire.ACCOUNT,
        WorldlineWire.MERCHANT,
        nativeStatus,
        null,
        null,
        null,
        WHEN.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN),
        ObjectOrigin.PROVIDER_API,
        WHEN,
        WHEN);
  }

  @Test
  void an_amount_a_tester_typed_that_is_no_number_still_reaches_worldline_as_a_readable_body() {
    WorldlineApiCall payment =
        WorldlineOrigination.paymentCallOf(
            OriginationRequest.of(
                RUN,
                WorldlineWire.ACCOUNT,
                WorldlineOrigination.A_PAYMENT,
                Map.of(
                    WorldlineFields.AMOUNT, "abc",
                    WorldlineFields.CURRENCY_CODE, "PLN",
                    WorldlineFields.AUTHORIZATION_MODE, "SALE",
                    WorldlineFields.TOKEN, "token-1"),
                ObjectOrigin.UI),
            merchant);
    WorldlineApiCall capture =
        WorldlineObjectActions.callOf(
            ObjectActionRequest.of(
                RUN, "3066019730_0", ObjectAction.CAPTURE, "abc", ObjectOrigin.UI),
            merchant);

    assertThat(payment.rawBody())
        .as(
            "a value written into a JSON number position unquoted malformed the whole body, and"
                + " worldline then answered about a body this simulator broke rather than naming"
                + " the property that is wrong")
        .contains("\"amount\":\"abc\"");
    assertThat(payment.body()).isPresent();
    assertThat(capture.rawBody()).isEqualTo("{\"amount\":\"abc\",\"isFinal\":false}");
    assertThat(capture.body()).isPresent();
    assertThat(CreatePayment.theAmountIn(payment.body().orElseThrow())).contains("abc");
    assertThat(WorldlineAmounts.wholeMinorUnits("abc")).isEmpty();
  }
}
