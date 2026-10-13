package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldlinePaymentTest {

  private static final TestRunId RUN = TestRunId.of("run-worldline");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private static final long FIFTY = 5000L;

  private final WorldlineDoubles.Merchants merchants = new WorldlineDoubles.Merchants();
  private final WorldlineDoubles.Tokenizations sessions = new WorldlineDoubles.Tokenizations();
  private final WorldlineDoubles.Payments payments = new WorldlineDoubles.Payments();
  private final WorldlineDoubles.Keys keys = new WorldlineDoubles.Keys();
  private final WorldlineDoubles.Audit audit = new WorldlineDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final IdGenerator ids = new WorldlineDoubles.CountingIds();

  private WorldlineMerchant configure(Map<String, String> configuration) {
    WorldlineMerchant merchant =
        WorldlineDoubles.merchant(
            RUN, WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT, configuration);
    merchants.with(merchant);
    return merchant;
  }

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

  private WorldlineApiResponse create(String body) {
    return create(body, Map.of());
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

  private WorldlineApiResponse capture(String paymentId, String body) {
    return api()
        .capturePayment(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.thePaymentResourceOf(WorldlineWire.MERCHANT, paymentId)
                    + WorldlineApiController.CAPTURE,
                body),
            WorldlineWire.MERCHANT,
            paymentId);
  }

  private WorldlineApiResponse cancel(String paymentId, String body) {
    return api()
        .cancelPayment(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.thePaymentResourceOf(WorldlineWire.MERCHANT, paymentId)
                    + WorldlineApiController.CANCEL,
                body),
            WorldlineWire.MERCHANT,
            paymentId);
  }

  private List<String> theBalancesOf(String paymentId) {
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    DerivedBalances balances = payment.ledger().balances(payment.authorizationOpen()).orElseThrow();
    return List.of(
        balances.capturedAmount().value(),
        balances.refundedAmount().value(),
        balances.capturableRemainder().value(),
        balances.refundableRemainder().value());
  }

  private String aHold() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));
    return String.valueOf(created.providerObjectId());
  }

  @Test
  void a_create_whose_amount_is_no_number_names_the_property_and_not_the_body_it_could_not_read() {
    configure(Map.of());
    String token = aStoredToken();

    WorldlineApiResponse refused =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":\"abc\",\"currencyCode\":\"PLN\"}},"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\""
                + token
                + "\",\"authorizationMode\":\"SALE\"}}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .as(
            "the body parsed and one property held a value worldline does not type as a number,"
                + " so the refusal names that property rather than the whole body")
        .contains(
            "\"propertyName\":\""
                + WorldlineFields.ORDER
                + "."
                + WorldlineFields.AMOUNT_OF_MONEY
                + "\"")
        .contains("\"id\":\"" + WorldlineErrors.INVALID_VALUE + "\"");
  }

  @Test
  void a_created_payment_is_answered_201_because_a_payment_object_was_created() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));
    assertThat(created.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
  }

  @Test
  void a_created_payment_carries_the_address_of_the_resource_in_the_location_header() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));
    assertThat(WorldlineWire.headerOf(created, WorldlineFields.LOCATION))
        .isEqualTo(
            WorldlineApiController.thePaymentResourceOf(
                WorldlineWire.MERCHANT, String.valueOf(created.providerObjectId())));
  }

  @Test
  void the_identifier_a_create_draws_is_the_published_payid_payidsub_grammar_at_sub_zero() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));
    assertThat(String.valueOf(created.providerObjectId())).matches("[0-9]{10}_0");
  }

  @Test
  void a_pre_authorization_lands_at_pending_capture_with_the_published_status_code_five() {
    String paymentId = aHold();
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(payment.nativeStatus()).isEqualTo(WorldlineStatus.PENDING_CAPTURE);
    assertThat(payment.statusCode()).isEqualTo(WorldlineStatus.CODE_PENDING_CAPTURE);
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_sale_blocks_and_captures_in_one_go_and_leaves_nothing_to_capture() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.SALE.name()));
    WorldlinePayment payment =
        payments.findById(RUN, String.valueOf(created.providerObjectId())).orElseThrow();
    assertThat(payment.nativeStatus()).isEqualTo(WorldlineStatus.CAPTURED);
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.capturableRemainder().longValue()).isZero();
  }

  @Test
  void an_omitted_authorization_mode_behaves_as_the_unspecified_case_the_vendor_publishes() {
    configure(Map.of());
    String token = aStoredToken();
    WorldlineApiResponse created =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\""
                + token
                + "\"}}");
    assertThat(
            payments
                .findById(RUN, String.valueOf(created.providerObjectId()))
                .orElseThrow()
                .authorizationMode())
        .isEqualTo(WorldlineAuthorizationMode.FINAL_AUTHORIZATION);
  }

  @Test
  void an_authorization_mode_this_contract_does_not_publish_is_refused_as_an_invalid_value() {
    configure(Map.of());
    WorldlineApiResponse refused =
        create(WorldlineWire.aPaymentBody(FIFTY, "PLN", aStoredToken(), "UNSPECIFIED"));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains(WorldlineErrors.INVALID_VALUE);
  }

  @Test
  void an_amount_above_the_published_maximum_is_refused_before_any_payment_is_stored() {
    configure(Map.of());
    WorldlineApiResponse refused =
        create(
            WorldlineWire.aPaymentBody(
                WorldlineAmounts.LARGEST + 1,
                "PLN",
                aStoredToken(),
                WorldlineAuthorizationMode.SALE.name()));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(WorldlineErrors.INVALID_VALUE_ERROR_CODE)
        .contains(WorldlineErrors.INVALID_VALUE_DEPRECATED_CODE)
        .contains("smallest currency unit");
  }

  @Test
  void an_amount_at_the_published_maximum_is_inside_the_bounds_and_is_charged() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                WorldlineAmounts.LARGEST,
                "PLN",
                aStoredToken(),
                WorldlineAuthorizationMode.SALE.name()));
    assertThat(created.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
  }

  @Test
  void a_currency_code_that_is_not_three_characters_is_refused() {
    configure(Map.of());
    WorldlineApiResponse refused =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLNX", aStoredToken(), WorldlineAuthorizationMode.SALE.name()));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains(CreatePayment.THE_CURRENCY_IS_REQUIRED);
  }

  @Test
  void a_merchant_reference_longer_than_the_published_maximum_is_refused() {
    configure(Map.of());
    String token = aStoredToken();
    WorldlineApiResponse refused =
        create(
            aPaymentReferencing("r".repeat(CreatePayment.LONGEST_MERCHANT_REFERENCE + 1), token));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains(CreatePayment.THE_REFERENCE_IS_TOO_LONG);
  }

  @Test
  void a_merchant_reference_of_exactly_the_published_maximum_is_taken() {
    configure(Map.of());
    String token = aStoredToken();
    WorldlineApiResponse created =
        create(aPaymentReferencing("r".repeat(CreatePayment.LONGEST_MERCHANT_REFERENCE), token));
    assertThat(created.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
  }

  @Test
  void a_hosted_tokenization_id_this_merchant_holds_no_session_under_charges_nothing() {
    configure(Map.of());
    WorldlineApiResponse refused =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"hostedTokenizationId\":\"00000000000000000000000000000000\","
                + "\"cardPaymentMethodSpecificInput\":{\"authorizationMode\":\"SALE\"}}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains("hostedTokenizationId names no hosted tokenization session of this merchant");
  }

  @Test
  void a_token_a_payment_method_fixture_of_this_run_seeded_is_one_a_payment_may_charge() {
    configure(Map.of());
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

    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", "token-seeded", WorldlineAuthorizationMode.SALE.name()));

    assertThat(created.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
    assertThat(WorldlineWire.bodyOf(created)).contains("************4444").contains("0131");
  }

  private static String aPaymentReferencing(String reference, String token) {
    return "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"},"
        + "\"references\":{\"merchantReference\":\""
        + reference
        + "\"}},\"cardPaymentMethodSpecificInput\":{\"token\":\""
        + token
        + "\",\"authorizationMode\":\"SALE\"}}";
  }

  @Test
  void a_request_naming_neither_a_token_nor_a_hosted_tokenization_id_is_refused() {
    configure(Map.of());
    WorldlineApiResponse refused =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"cardPaymentMethodSpecificInput\":{\"authorizationMode\":\"SALE\"}}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("one of the two is required");
  }

  @Test
  void a_request_naming_both_a_token_and_a_hosted_tokenization_id_is_refused() {
    configure(Map.of());
    WorldlineApiResponse refused =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"hostedTokenizationId\":\"abc\","
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\"t\","
                + "\"authorizationMode\":\"SALE\"}}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
  }

  @Test
  void a_token_this_test_run_holds_no_card_under_is_refused_before_any_payment_is_stored() {
    configure(Map.of());
    WorldlineApiResponse refused =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", "token-nobody-minted", WorldlineAuthorizationMode.SALE.name()));
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(CreatePayment.NO_TOKEN_ANSWERS_TO_THAT_VALUE);
  }

  @Test
  void a_payment_may_name_the_hosted_tokenization_session_instead_of_the_token_it_produced() {
    configure(Map.of());
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
    WorldlineApiResponse created =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"hostedTokenizationId\":\""
                + sessionId
                + "\",\"cardPaymentMethodSpecificInput\":{\"authorizationMode\":\"SALE\"}}");
    assertThat(created.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
    assertThat(
            payments
                .findById(RUN, String.valueOf(created.providerObjectId()))
                .orElseThrow()
                .hostedTokenizationId())
        .isEqualTo(sessionId);
  }

  @Test
  void a_hosted_tokenization_session_no_payer_has_finished_charges_nothing() {
    configure(Map.of());
    WorldlineApiResponse opened =
        api()
            .createHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                    "{}"),
                WorldlineWire.MERCHANT);
    WorldlineApiResponse refused =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"hostedTokenizationId\":\""
                + opened.providerObjectId()
                + "\",\"cardPaymentMethodSpecificInput\":{\"authorizationMode\":\"SALE\"}}");
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(CreatePayment.THE_TOKENIZATION_IS_NOT_FINISHED);
  }

  @Test
  void a_declined_create_is_402_and_the_envelope_names_the_payment_it_declined() {
    configure(Map.of());
    String token = aStoredToken();
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CREATE_PAYMENT),
            new ScenarioAction.TerminalFailure(WorldlineErrors.THE_CARD_WAS_DECLINED),
            null));
    WorldlineApiResponse declined =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", token, WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));
    assertThat(declined.httpStatus()).isEqualTo(WorldlineAnswers.PAYMENT_REJECTED);
    assertThat(WorldlineWire.bodyOf(declined))
        .contains(WorldlineFields.PAYMENT_RESULT)
        .contains(WorldlineStatus.REJECTED)
        .contains(WorldlineErrors.THE_CARD_WAS_DECLINED);
  }

  @Test
  void a_declined_payment_is_kept_as_an_object_a_tester_can_read_and_normalizes_to_failed() {
    configure(Map.of());
    String token = aStoredToken();
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CREATE_PAYMENT),
            new ScenarioAction.TerminalFailure(WorldlineErrors.THE_CARD_WAS_DECLINED),
            null));
    WorldlineApiResponse declined =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", token, WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));
    assertThat(
            payments
                .findById(RUN, String.valueOf(declined.providerObjectId()))
                .orElseThrow()
                .normalizedStatus())
        .isEqualTo(NormalizedStatus.FAILED);
  }

  @Test
  void a_created_hold_reports_the_amount_it_holds_and_the_category_a_hold_waits_in() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));

    assertThat(WorldlineWire.bodyOf(created))
        .contains("\"amount\":" + FIFTY)
        .contains("\"currencyCode\":\"PLN\"")
        .contains("\"statusCategory\":\"" + WorldlineStatus.CATEGORY_PENDING_MERCHANT + "\"")
        .contains("\"isAuthorized\":true")
        .contains("\"isRefundable\":false")
        .contains("\"isCancellable\":true");
  }

  @Test
  void a_sale_reports_the_category_a_captured_payment_rests_in_and_is_refundable() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.SALE.name()));

    assertThat(WorldlineWire.bodyOf(created))
        .contains("\"statusCategory\":\"" + WorldlineStatus.CATEGORY_COMPLETED + "\"")
        .contains("\"isRefundable\":true")
        .contains("\"isCancellable\":false");
  }

  @Test
  void a_declined_payment_is_reported_as_one_no_acquirer_authorized() {
    configure(Map.of());
    String token = aStoredToken();
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CREATE_PAYMENT),
            new ScenarioAction.TerminalFailure(WorldlineErrors.THE_CARD_WAS_DECLINED),
            null));

    WorldlineApiResponse declined =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", token, WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));

    assertThat(WorldlineWire.bodyOf(declined))
        .contains("\"isAuthorized\":false")
        .contains("\"statusCategory\":\"" + WorldlineStatus.CATEGORY_UNSUCCESSFUL + "\"");
  }

  @Test
  void a_capture_leaves_the_amount_it_took_on_the_ledger_and_the_rest_capturable() {
    String paymentId = aHold();

    capture(paymentId, "{\"amount\":2000,\"isFinal\":false}");

    assertThat(theBalancesOf(paymentId))
        .as("captured, refunded, still capturable, still refundable")
        .containsExactly("2000", "0", "3000", "2000");
  }

  @Test
  void a_capture_amount_that_is_no_whole_number_is_refused_on_the_property_and_takes_nothing() {
    String paymentId = aHold();

    WorldlineApiResponse refused = capture(paymentId, "{\"amount\":\"abc\"}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .as(
            "reading the amount only in its number shape left a string amount absent, and the"
                + " capture then took the whole authorized amount without saying anything")
        .contains("\"propertyName\":\"" + WorldlineFields.AMOUNT + "\"")
        .contains("\"id\":\"" + WorldlineErrors.INVALID_VALUE + "\"")
        .contains("amount is a whole number of minor units at the root of a capture request");
    assertThat(theBalancesOf(paymentId))
        .as("captured, refunded, still capturable, still refundable")
        .containsExactly("0", "0", "5000", "0");
  }

  @Test
  void a_capture_that_closes_the_payment_leaves_nothing_capturable_and_the_whole_amount_captured() {
    String paymentId = aHold();

    capture(paymentId, "{}");

    assertThat(theBalancesOf(paymentId))
        .as("captured, refunded, still capturable, still refundable")
        .containsExactly("5000", "0", "0", "5000");
  }

  @Test
  void a_cancelled_hold_leaves_nothing_captured_and_nothing_capturable() {
    String paymentId = aHold();

    cancel(paymentId, "{}");

    assertThat(theBalancesOf(paymentId))
        .as("captured, refunded, still capturable, still refundable")
        .containsExactly("0", "0", "0", "0");
  }

  @Test
  void a_payment_of_an_account_a_cancel_does_not_belong_to_is_not_reachable_by_it() {
    String paymentId = aHold();
    merchants.with(
        WorldlineDoubles.merchant(RUN, "account-2", "merchant-2", new LinkedHashMap<>()));

    WorldlineApiResponse refused =
        api()
            .cancelPayment(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    WorldlineApiController.thePaymentResourceOf("merchant-2", paymentId)
                        + WorldlineApiController.CANCEL,
                    Map.of(
                        WorldlineFields.AUTHORIZATION,
                        List.of(WorldlineWire.permissively("account-2"))),
                    Map.of(),
                    "{}"),
                "merchant-2",
                paymentId);

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
  }

  @Test
  void a_capture_mints_a_second_identifier_the_payment_then_also_answers_to() {
    String paymentId = aHold();
    WorldlineApiResponse captured = capture(paymentId, "{\"amount\":5000,\"isFinal\":true}");
    String minted = String.valueOf(captured.providerObjectId());
    assertThat(minted).isNotEqualTo(paymentId).matches("[0-9]{10}_1");
    WorldlinePayment payment = payments.findById(RUN, minted).orElseThrow();
    assertThat(payment.paymentId()).isEqualTo(paymentId);
    assertThat(payment.identity().members()).containsExactly(paymentId, minted);
    assertThat(payment.identity().canonical()).isEqualTo(paymentId);
    assertThat(payment.identity().keepsOneIdentifierForLife()).isFalse();
  }

  @Test
  void the_capture_response_names_the_minted_identifier_and_not_the_original_one() {
    String paymentId = aHold();
    WorldlineApiResponse captured = capture(paymentId, "{\"amount\":5000,\"isFinal\":true}");
    assertThat(WorldlineWire.bodyOf(captured))
        .contains("\"id\":\"" + captured.providerObjectId() + "\"")
        .doesNotContain("\"id\":\"" + paymentId + "\"");
  }

  @Test
  void a_capture_is_answered_201_with_the_capture_output_shape_and_no_wrapped_payment() {
    String paymentId = aHold();
    WorldlineApiResponse captured = capture(paymentId, "{\"amount\":5000,\"isFinal\":true}");
    assertThat(captured.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
    assertThat(WorldlineWire.bodyOf(captured))
        .contains(WorldlineFields.CAPTURE_OUTPUT)
        .doesNotContain("\"payment\":");
  }

  @Test
  void the_capture_status_output_publishes_a_status_code_and_nothing_else() {
    String paymentId = aHold();
    WorldlineApiResponse captured = capture(paymentId, "{\"amount\":5000,\"isFinal\":true}");
    assertThat(WorldlineWire.bodyOf(captured))
        .contains("\"statusOutput\":{\"statusCode\":9}")
        .doesNotContain("isCancellable");
  }

  @Test
  void the_default_landing_of_a_successful_capture_is_the_one_two_publications_agree_on() {
    String paymentId = aHold();
    capture(paymentId, "{\"amount\":5000,\"isFinal\":true}");
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(payment.nativeStatus()).isEqualTo(WorldlineStatus.CAPTURED);
    assertThat(payment.statusCode()).isEqualTo(WorldlineStatus.CODE_CAPTURED);
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void an_account_configured_for_the_capture_resource_table_lands_at_capture_requested_91() {
    configure(
        Map.of(
            WorldlineCaptureLanding.SETTING, WorldlineCaptureLanding.CAPTURE_REQUESTED_91.name()));
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));
    String paymentId = String.valueOf(created.providerObjectId());
    WorldlineApiResponse captured = capture(paymentId, "{\"amount\":5000,\"isFinal\":true}");
    assertThat(WorldlineWire.bodyOf(captured)).contains(WorldlineStatus.CAPTURE_REQUESTED);
    assertThat(payments.findById(RUN, paymentId).orElseThrow().normalizedStatus())
        .isEqualTo(NormalizedStatus.PROCESSING);
  }

  @Test
  void an_account_configured_for_the_direct_sale_page_lands_at_pending_capture_at_code_nine() {
    configure(
        Map.of(WorldlineCaptureLanding.SETTING, WorldlineCaptureLanding.PENDING_CAPTURE_9.name()));
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY, "PLN", aStoredToken(), WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()));
    String paymentId = String.valueOf(created.providerObjectId());
    WorldlineApiResponse captured = capture(paymentId, "{\"amount\":5000,\"isFinal\":true}");
    assertThat(WorldlineWire.bodyOf(captured))
        .contains(WorldlineStatus.PENDING_CAPTURE)
        .contains("\"statusCode\":9");
    assertThat(payments.findById(RUN, paymentId).orElseThrow().normalizedStatus())
        .as("a closed payment has nothing left to capture, so it is not partially captured")
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_capture_naming_no_amount_takes_the_whole_original_and_closes_the_transaction() {
    String paymentId = aHold();
    capture(paymentId, "{}");
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(payment.authorizationClosed()).isTrue();
    assertThat(payment.capturableRemainder().longValue()).isZero();
  }

  @Test
  void a_partial_capture_that_is_not_final_leaves_the_rest_capturable_and_reports_it_as_partial() {
    String paymentId = aHold();
    capture(paymentId, "{\"amount\":2000,\"isFinal\":false}");
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(payment.capturableRemainder().longValue()).isEqualTo(3000L);
    assertThat(payment.nativeStatus()).isEqualTo(WorldlineStatus.PENDING_CAPTURE);
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_CAPTURED);
  }

  @Test
  void a_second_capture_that_takes_the_whole_remainder_is_final_even_where_isFinal_says_false() {
    String paymentId = aHold();
    capture(paymentId, "{\"amount\":2000,\"isFinal\":false}");
    capture(paymentId, "{\"amount\":3000,\"isFinal\":false}");
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(payment.authorizationClosed()).isTrue();
    assertThat(payment.nativeStatus()).isEqualTo(WorldlineStatus.CAPTURED);
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void a_partial_capture_that_leaves_a_remainder_keeps_the_authorization_open() {
    String paymentId = aHold();
    capture(paymentId, "{\"amount\":2000,\"isFinal\":false}");
    assertThat(payments.findById(RUN, paymentId).orElseThrow().authorizationClosed()).isFalse();
  }

  @Test
  void a_partial_capture_that_names_itself_final_closes_what_it_did_not_take() {
    String paymentId = aHold();
    capture(paymentId, "{\"amount\":2000,\"isFinal\":true}");
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(payment.authorizationClosed()).isTrue();
    assertThat(payment.capturableRemainder().longValue()).isZero();
  }

  @Test
  void two_partial_captures_mint_two_identifiers_and_the_payment_answers_to_all_three() {
    String paymentId = aHold();
    String first = String.valueOf(capture(paymentId, "{\"amount\":2000}").providerObjectId());
    String second = String.valueOf(capture(first, "{\"amount\":3000}").providerObjectId());
    assertThat(payments.findById(RUN, second).orElseThrow().identity().members())
        .containsExactly(paymentId, first, second);
  }

  @Test
  void a_capture_addressed_by_an_identifier_an_earlier_capture_minted_reaches_the_same_payment() {
    String paymentId = aHold();
    String minted = String.valueOf(capture(paymentId, "{\"amount\":2000}").providerObjectId());
    WorldlineApiResponse second = capture(minted, "{\"amount\":3000}");
    assertThat(second.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
    assertThat(payments.findById(RUN, minted).orElseThrow().paymentId()).isEqualTo(paymentId);
  }

  @Test
  void a_capture_above_what_the_authorization_still_has_available_is_refused() {
    String paymentId = aHold();
    WorldlineApiResponse refused = capture(paymentId, "{\"amount\":6000}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("higher than what this authorization");
  }

  @Test
  void a_partial_capture_of_a_final_authorization_is_refused_with_the_published_errors_id() {
    configure(Map.of());
    WorldlineApiResponse created =
        create(
            WorldlineWire.aPaymentBody(
                FIFTY,
                "PLN",
                aStoredToken(),
                WorldlineAuthorizationMode.FINAL_AUTHORIZATION.name()));
    WorldlineApiResponse refused =
        capture(String.valueOf(created.providerObjectId()), "{\"amount\":2000}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(WorldlineErrors.ACTION_NOT_ALLOWED_ON_TRANSACTION);
  }

  @Test
  void a_capture_of_a_payment_that_is_already_closed_is_refused_rather_than_repeated() {
    String paymentId = aHold();
    capture(paymentId, "{}");
    WorldlineApiResponse refused = capture(paymentId, "{}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("this payment is closed");
  }

  @Test
  void a_capture_of_an_identifier_no_payment_of_this_merchant_answers_to_is_404() {
    configure(Map.of());
    WorldlineApiResponse refused = capture("0000000000_0", "{}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
    assertThat(WorldlineWire.bodyOf(refused)).contains(WorldlineErrors.UNKNOWN_PAYMENT_ID);
  }

  @Test
  void a_rule_that_refuses_a_capture_answers_the_published_status_and_leaves_it_capturable() {
    String paymentId = aHold();
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CAPTURE_PAYMENT),
            new ScenarioAction.TerminalFailure(
                Integer.toString(WorldlineStatus.CODE_REJECTED_CAPTURE)),
            null));
    WorldlineApiResponse refused = capture(paymentId, "{\"amount\":5000}");
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(WorldlineStatus.REJECTED_CAPTURE)
        .contains("\"statusCode\":93");
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(payment.nativeStatus()).isEqualTo(WorldlineStatus.PENDING_CAPTURE);
    assertThat(payment.capturableRemainder().longValue()).isEqualTo(FIFTY);
  }

  @Test
  void a_rule_naming_an_error_code_on_a_capture_answers_an_error_body_rather_than_a_status() {
    String paymentId = aHold();
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CAPTURE_PAYMENT),
            new ScenarioAction.TerminalFailure(WorldlineErrors.THE_CARD_WAS_DECLINED),
            null));
    WorldlineApiResponse refused = capture(paymentId, "{\"amount\":5000}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains(WorldlineErrors.THE_CARD_WAS_DECLINED);
  }

  @Test
  void a_cancel_of_a_hold_mints_an_identifier_and_answers_200_wrapping_a_payment() {
    String paymentId = aHold();
    WorldlineApiResponse cancelled = cancel(paymentId, "{}");
    assertThat(cancelled.httpStatus()).isEqualTo(WorldlineAnswers.OK);
    assertThat(WorldlineWire.bodyOf(cancelled))
        .contains("\"payment\":")
        .contains(WorldlineStatus.CANCELLED)
        .contains("\"statusCode\":6");
    assertThat(String.valueOf(cancelled.providerObjectId())).isNotEqualTo(paymentId);
  }

  @Test
  void a_cancelled_hold_normalizes_to_cancelled_and_answers_to_both_of_its_identifiers() {
    String paymentId = aHold();
    String minted = String.valueOf(cancel(paymentId, "{}").providerObjectId());
    WorldlinePayment payment = payments.findById(RUN, minted).orElseThrow();
    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(payment.identity().answersTo(paymentId)).isTrue();
    assertThat(payment.identity().answersTo(minted)).isTrue();
  }

  @Test
  void a_cancel_of_a_closed_payment_is_the_409_the_vendor_publishes_for_this_route() {
    String paymentId = aHold();
    capture(paymentId, "{}");
    WorldlineApiResponse refused = cancel(paymentId, "{}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.CONFLICT);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains("Cancellation is not allowed because payment is closed");
  }

  @Test
  void a_cancel_reads_the_amount_under_order_as_well_as_at_the_body_root() {
    String paymentId = aHold();
    WorldlineApiResponse cancelled =
        cancel(
            paymentId,
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}}}");
    assertThat(cancelled.httpStatus()).isEqualTo(WorldlineAnswers.OK);
  }

  @Test
  void a_cancel_that_names_the_amount_in_both_published_places_and_disagrees_is_refused() {
    String paymentId = aHold();
    WorldlineApiResponse refused =
        cancel(
            paymentId,
            "{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"},"
                + "\"order\":{\"amountOfMoney\":{\"amount\":2000,\"currencyCode\":\"PLN\"}}}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("both with different values");
  }

  @Test
  void a_partial_cancellation_is_refused_in_this_slice_and_says_whose_decision_that_is() {
    String paymentId = aHold();
    WorldlineApiResponse refused =
        cancel(paymentId, "{\"amountOfMoney\":{\"amount\":2000,\"currencyCode\":\"PLN\"}}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains("The reason is this simulator's and not the vendor's");
  }

  @Test
  void a_rule_that_refuses_a_cancellation_answers_the_published_status_and_keeps_it_authorised() {
    String paymentId = aHold();
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CANCEL_PAYMENT),
            new ScenarioAction.TerminalFailure(
                Integer.toString(WorldlineStatus.CODE_CANCELLATION_REJECTED)),
            null));
    WorldlineApiResponse refused = cancel(paymentId, "{}");
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(WorldlineStatus.CANCELLATION_REJECTED)
        .contains("\"statusCode\":63");
    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(payment.isAnAuthorizationWaitingToBeCaptured()).isTrue();
  }

  @Test
  void a_rule_naming_an_error_code_on_a_cancel_answers_an_error_body_rather_than_a_status() {
    String paymentId = aHold();
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CANCEL_PAYMENT),
            new ScenarioAction.TerminalFailure(WorldlineErrors.THE_BRAND_DOES_NOT_MATCH_THE_PAN),
            null));
    WorldlineApiResponse refused = cancel(paymentId, "{}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(WorldlineErrors.THE_BRAND_DOES_NOT_MATCH_THE_PAN);
  }

  @Test
  void the_audit_trail_files_a_capture_under_the_identifier_the_capture_minted() {
    String paymentId = aHold();
    String minted = String.valueOf(capture(paymentId, "{}").providerObjectId());
    assertThat(audit.all())
        .filteredOn(record -> minted.equals(record.providerObjectId()))
        .isNotEmpty();
  }

  @Test
  void a_capture_whose_ledger_claim_was_taken_by_another_request_is_refused_rather_than_applied() {
    String paymentId = aHold();
    payments.theClaimIsTaken();
    WorldlineApiResponse refused = capture(paymentId, "{}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
  }

  @Test
  void a_cancel_whose_object_vanished_between_the_read_and_the_write_is_refused() {
    String paymentId = aHold();
    payments.theObjectIsGone();
    WorldlineApiResponse refused = cancel(paymentId, "{}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.CONFLICT);
  }

  @Test
  void a_capture_body_this_endpoint_cannot_read_is_refused_before_the_payment_is_looked_for() {
    configure(Map.of());
    WorldlineApiResponse refused = capture("0000000000_0", "not json");
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(CapturePayment.A_BODY_THIS_ENDPOINT_CANNOT_READ);
  }

  @Test
  void a_cancel_body_this_endpoint_cannot_read_is_refused_before_the_payment_is_looked_for() {
    configure(Map.of());
    WorldlineApiResponse refused = cancel("0000000000_0", "not json");
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(CancelPayment.A_BODY_THIS_ENDPOINT_CANNOT_READ);
  }

  @Test
  void the_amount_of_a_capture_is_a_bare_integer_at_the_request_root_and_not_an_amount_of_money() {
    String paymentId = aHold();
    WorldlineApiResponse refused =
        capture(paymentId, "{\"amountOfMoney\":{\"amount\":2000,\"currencyCode\":\"PLN\"}}");
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
    assertThat(payments.findById(RUN, paymentId).orElseThrow().capturableRemainder().longValue())
        .isZero();
  }

  @Test
  void a_payment_of_an_account_this_call_does_not_belong_to_is_not_reachable_by_it() {
    String paymentId = aHold();
    merchants.with(
        WorldlineDoubles.merchant(RUN, "account-2", "merchant-2", new LinkedHashMap<>()));
    WorldlineApiResponse refused =
        api()
            .capturePayment(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    WorldlineApiController.thePaymentResourceOf("merchant-2", paymentId)
                        + WorldlineApiController.CAPTURE,
                    Map.of(
                        WorldlineFields.AUTHORIZATION,
                        List.of(WorldlineWire.permissively("account-2"))),
                    Map.of(),
                    "{}"),
                "merchant-2",
                paymentId);
    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
  }
}
