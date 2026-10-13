package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldlineDeliveryTest {

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
    return open(Map.of());
  }

  private WorldlineApiResponse open(Map<String, List<String>> headers) {
    return api()
        .createHostedTokenization(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                headers,
                "{}"),
            WorldlineWire.MERCHANT);
  }

  private void aRuleThat(ScenarioAction action, String operation) {
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(operation),
            action,
            null));
  }

  @Test
  void an_answer_this_module_delivers_carries_the_run_the_status_the_type_and_the_length() {
    WorldlineApiResponse answered = open();

    assertThat(answered.testRunId()).isEqualTo(RUN);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.OK);
    assertThat(answered.contentType()).isEqualTo(WorldlineFields.JSON);
    assertThat(answered.declaredContentLength()).isEqualTo(answered.body().length);
    assertThat(answered.delay()).isEqualTo(Duration.ZERO);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.headers()).containsEntry("Content-Type", List.of(WorldlineFields.JSON));
  }

  @Test
  void a_rule_that_holds_the_answer_back_delays_the_delivery_and_not_the_operation() {
    aRuleThat(
        new ScenarioAction.TransportFaultAlways(
            new TransportFault.DelayedResponse(Duration.ofMillis(350))),
        WorldlineOperations.CREATE_HOSTED_TOKENIZATION);

    WorldlineApiResponse answered = open();

    assertThat(answered.delay()).isEqualTo(Duration.ofMillis(350));
    assertThat(answered.providerObjectId()).isNotNull();
  }

  @Test
  void a_rule_that_answers_instead_of_the_operation_delivers_its_own_bytes_and_creates_nothing() {
    aRuleThat(
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)),
        WorldlineOperations.CREATE_HOSTED_TOKENIZATION);

    WorldlineApiResponse answered = open();

    assertThat(answered.httpStatus()).isEqualTo(502);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(answered.providerObjectId()).isNull();
    assertThat(sessions.newestFirst(RUN, null, 10)).isEmpty();
  }

  @Test
  void a_rule_that_answers_a_payment_instead_of_running_it_releases_the_key_it_claimed() {
    aRuleThat(
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)),
        WorldlineOperations.CREATE_PAYMENT);
    String token = aStoredToken();
    Map<String, List<String>> key =
        Map.of(
            WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
            List.of("k-released"));

    WorldlineApiResponse answeredInstead = create(token, key);
    rules.clear(WorldlineApi.WORLDLINE, RUN);
    WorldlineApiResponse retried = create(token, key);

    assertThat(answeredInstead.httpStatus()).isEqualTo(502);
    assertThat(retried.httpStatus())
        .as("the key was released, so the retry is a first request rather than a replayed 502")
        .isEqualTo(WorldlineAnswers.CREATED);
  }

  @Test
  void a_call_whose_operation_raised_releases_the_key_rather_than_leaving_it_held_for_ever() {
    payments.refusingToStoreAnything();
    String token = aStoredToken();
    Map<String, List<String>> key =
        Map.of(
            WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT,
            List.of("k-raised"));

    Assertions.assertThrows(IllegalStateException.class, () -> create(token, key));
    payments.storingAgain();

    assertThat(create(token, key).httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
  }

  @Test
  void a_read_of_a_session_of_another_account_of_the_same_run_finds_nothing() {
    merchants.with(WorldlineDoubles.merchant(RUN, "account-2", "merchant-2"));
    String sessionId = String.valueOf(open().providerObjectId());

    WorldlineApiResponse read =
        api()
            .getHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.GET,
                    WorldlineApiController.theHostedTokenizationOf("merchant-2", sessionId),
                    Map.of(
                        WorldlineFields.AUTHORIZATION,
                        List.of(WorldlineWire.permissively("account-2"))),
                    ""),
                "merchant-2",
                sessionId);

    assertThat(read.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
  }

  @Test
  void a_read_of_a_session_past_its_published_life_span_moves_it_to_expired_and_finds_nothing() {
    String sessionId = String.valueOf(open().providerObjectId());
    WorldlineApi later =
        WorldlineWiring.api(
            merchants,
            sessions,
            payments,
            keys,
            audit,
            RUN,
            WHEN.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN).plusSeconds(1),
            new ScenarioEngine(rules),
            ids,
            fixtures);

    WorldlineApiResponse read =
        later.getHostedTokenization(
            WorldlineWire.call(
                WorldlineWire.GET,
                WorldlineApiController.theHostedTokenizationOf(WorldlineWire.MERCHANT, sessionId),
                ""),
            WorldlineWire.MERCHANT,
            sessionId);

    assertThat(read.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
    assertThat(sessions.findById(RUN, sessionId).orElseThrow().nativeStatus())
        .isEqualTo(WorldlineStatus.EXPIRED);
  }

  @Test
  void a_read_of_a_session_already_marked_expired_finds_nothing_and_moves_it_no_further() {
    String sessionId = String.valueOf(open().providerObjectId());
    sessions.moveFromAwaitingThePayerTo(
        sessions.findById(RUN, sessionId).orElseThrow().expiredAt(WHEN));

    WorldlineApiResponse read =
        api()
            .getHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.GET,
                    WorldlineApiController.theHostedTokenizationOf(
                        WorldlineWire.MERCHANT, sessionId),
                    ""),
                WorldlineWire.MERCHANT,
                sessionId);

    assertThat(read.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
  }

  @Test
  void a_payer_who_finished_a_page_that_had_already_timed_out_is_told_the_session_expired_by_now() {
    String sessionId = String.valueOf(open().providerObjectId());
    Instant after = WHEN.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN).plusSeconds(1);

    HostedCompletionOutcome refused =
        new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids)
            .complete(
                new HostedCompletionRequest(
                    RUN,
                    sessionId,
                    PayerOutcome.PAID,
                    WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS,
                    ObjectOrigin.CONTROL_API),
                sessions.findById(RUN, sessionId).orElseThrow(),
                after);

    assertThat(refused)
        .asInstanceOf(
            InstanceOfAssertFactories.type(HostedCompletionOutcome.RefusedByTheProvider.class))
        .extracting(HostedCompletionOutcome.RefusedByTheProvider::inItsOwnWords)
        .asString()
        .contains("past the three hours");
    assertThat(sessions.findById(RUN, sessionId).orElseThrow().statusAt(after))
        .isEqualTo("EXPIRED");
    assertThat(sessions.findById(RUN, sessionId).orElseThrow().nativeStatus())
        .isEqualTo("AWAITING_THE_PAYER");
  }

  @Test
  void a_payer_who_finished_a_page_another_payer_had_finished_is_told_what_it_holds_now() {
    String sessionId = String.valueOf(open().providerObjectId());
    TokenizeOnTheHostedPage page =
        new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids);
    page.complete(aPayerWhoPaid(sessionId), sessions.findById(RUN, sessionId).orElseThrow(), WHEN);

    HostedCompletionOutcome second =
        page.complete(
            aPayerWhoPaid(sessionId), sessions.findById(RUN, sessionId).orElseThrow(), WHEN);

    assertThat(second).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(page.offerOn(sessions.findById(RUN, sessionId).orElseThrow(), WHEN))
        .isInstanceOf(HostedCompletionOffer.NotOffered.class);
  }

  @Test
  void a_signed_call_presenting_a_key_and_no_signature_at_all_is_refused_on_the_scheme() {
    WorldlineMerchant verifying = aVerifyingAccount();

    WorldlineApiResponse refused =
        open(
            Map.of(
                WorldlineFields.AUTHORIZATION,
                List.of(WorldlineSignature.SCHEME + verifying.apiKey() + ":")));

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains("the Authorization header is not of the published form");
  }

  @Test
  void a_signed_call_whose_date_is_not_rfc_1123_is_refused_on_the_date_rather_than_the_signature() {
    WorldlineMerchant verifying = aVerifyingAccount();

    WorldlineApiResponse refused =
        open(
            Map.of(
                WorldlineFields.AUTHORIZATION,
                List.of(WorldlineSignature.SCHEME + verifying.apiKey() + ":a-signature"),
                WorldlineFields.DATE,
                List.of("2026-09-22T10:00:00Z")));

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains("a signed request carries a Date header")
        .contains("RFC 1123 format");
  }

  @Test
  void a_second_account_may_not_take_a_merchant_an_account_this_call_leaves_in_place_holds() {
    WorldlineProvider provider = aProvider();
    provider
        .parse(
            new ProviderConfiguration(
                RUN,
                List.of(anAccount("account-a", "merchant-a", "key-a")),
                List.of(),
                List.of(),
                Map.of()))
        .store();

    ProviderConfiguration takingTheMerchantOfAnotherAccount =
        new ProviderConfiguration(
            RUN,
            List.of(anAccount("account-b", "merchant-a", "key-b")),
            List.of(),
            List.of(),
            Map.of());

    Assertions.assertThrows(
        ProviderConfigurationRejectedException.class,
        () -> provider.parse(takingTheMerchantOfAnotherAccount));
  }

  @Test
  void a_second_account_may_not_take_an_api_key_an_account_this_call_leaves_in_place_holds() {
    WorldlineProvider provider = aProvider();
    provider.parse(configurationOf(RUN, anAccount("account-a", "merchant-a", "key-a"))).store();

    ProviderConfiguration takingTheKeyOfAnotherAccount =
        configurationOf(RUN, anAccount("account-b", "merchant-b", "key-a"));

    assertThatThrownBy(() -> provider.parse(takingTheKeyOfAnotherAccount))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "accounts[0].configuration."
                + WorldlineMerchant.API_KEY_PROPERTY
                + " is already configured for another account in this test run");
  }

  @Test
  void an_api_key_an_account_of_another_run_holds_is_free_for_this_run_to_take() {
    WorldlineProvider provider = aProvider();
    provider
        .parse(
            configurationOf(
                TestRunId.of("run-worldline-elsewhere"),
                anAccount("account-a", "merchant-a", "key-a")))
        .store();

    provider.parse(configurationOf(RUN, anAccount("account-b", "merchant-b", "key-a"))).store();

    assertThat(merchants.find(RUN, "account-b"))
        .get()
        .extracting(WorldlineMerchant::apiKey)
        .isEqualTo("key-a");
  }

  @Test
  void an_account_may_keep_the_api_key_it_already_holds_when_the_same_call_rewrites_it() {
    WorldlineProvider provider = aProvider();
    provider.parse(configurationOf(RUN, anAccount("account-a", "merchant-a", "key-a"))).store();

    provider.parse(configurationOf(RUN, anAccount("account-a", "merchant-a2", "key-a"))).store();

    assertThat(merchants.find(RUN, "account-a"))
        .get()
        .extracting(WorldlineMerchant::merchantId)
        .isEqualTo("merchant-a2");
  }

  private static ProviderConfiguration configurationOf(
      TestRunId run, ConfiguredAccount... accounts) {
    return new ProviderConfiguration(run, List.of(accounts), List.of(), List.of(), Map.of());
  }

  @Test
  void an_account_may_keep_the_merchant_it_already_holds_when_the_same_call_rewrites_it() {
    WorldlineProvider provider = aProvider();
    provider
        .parse(
            new ProviderConfiguration(
                RUN,
                List.of(anAccount("account-a", "merchant-a", "key-a")),
                List.of(),
                List.of(),
                Map.of()))
        .store();

    provider
        .parse(
            new ProviderConfiguration(
                RUN,
                List.of(anAccount("account-a", "merchant-a", "key-a2")),
                List.of(),
                List.of(),
                Map.of()))
        .store();

    assertThat(merchants.find(RUN, "account-a"))
        .get()
        .extracting(WorldlineMerchant::apiKey)
        .isEqualTo("key-a2");
  }

  @Test
  void an_origination_the_provider_refused_reports_the_words_the_envelope_carried() {
    WorldlineProvider provider = aProvider();
    provider
        .parse(
            new ProviderConfiguration(
                RUN,
                List.of(anAccount(WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT, "key-x")),
                List.of(),
                List.of(),
                Map.of()))
        .store();

    assertThat(
            provider.originate(
                OriginationRequest.of(
                    RUN,
                    WorldlineWire.ACCOUNT,
                    WorldlineOrigination.A_PAYMENT,
                    Map.of(
                        WorldlineFields.AMOUNT, "5000",
                        WorldlineFields.CURRENCY_CODE, "PLN",
                        WorldlineFields.AUTHORIZATION_MODE, "SALE",
                        WorldlineFields.TOKEN, "token-nobody-minted"),
                    ObjectOrigin.UI)))
        .asInstanceOf(InstanceOfAssertFactories.type(OriginationOutcome.RefusedByTheProvider.class))
        .extracting(OriginationOutcome.RefusedByTheProvider::inItsOwnWords)
        .asString()
        .contains(WorldlineErrors.INVALID_VALUE);
  }

  @Test
  void a_create_payment_body_this_endpoint_cannot_read_is_refused_before_the_key_is_completed() {
    WorldlineApiResponse refused =
        api()
            .createPayment(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.thePaymentsOf(WorldlineWire.MERCHANT),
                    "not json"),
                WorldlineWire.MERCHANT);

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(CreatePayment.A_BODY_THIS_ENDPOINT_CANNOT_READ);
  }

  @Test
  void a_capture_amount_outside_the_published_bounds_is_refused_before_the_payment_is_touched() {
    String paymentId = aHold();

    WorldlineApiResponse refused =
        capture(paymentId, "{\"amount\":" + (WorldlineAmounts.LARGEST + 1) + "}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("smallest currency unit");
  }

  @Test
  void a_cancel_amount_outside_the_published_bounds_is_refused_before_the_payment_is_touched() {
    String paymentId = aHold();

    WorldlineApiResponse refused =
        cancel(
            paymentId,
            "{\"amountOfMoney\":{\"amount\":"
                + (WorldlineAmounts.LARGEST + 1)
                + ",\"currencyCode\":\"PLN\"}}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains("smallest currency unit");
  }

  @Test
  void a_cancel_of_an_identifier_no_payment_of_this_merchant_answers_to_is_404() {
    WorldlineApiResponse refused = cancel("0000000000_0", "{}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
    assertThat(WorldlineWire.bodyOf(refused)).contains(WorldlineErrors.UNKNOWN_PAYMENT_ID);
  }

  @Test
  void
      a_refused_capture_that_lost_the_claim_while_writing_the_status_says_what_the_payment_holds() {
    String paymentId = aHold();
    aRuleThat(
        new ScenarioAction.TerminalFailure(Integer.toString(WorldlineStatus.CODE_REJECTED_CAPTURE)),
        WorldlineOperations.CAPTURE_PAYMENT);
    payments.theClaimIsTaken();

    WorldlineApiResponse refused = capture(paymentId, "{\"amount\":5000}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains(WorldlineErrors.ACTION_NOT_ALLOWED_ON_TRANSACTION);
  }

  @Test
  void a_refused_cancel_that_lost_the_claim_while_writing_the_status_says_the_payment_is_closed() {
    String paymentId = aHold();
    aRuleThat(
        new ScenarioAction.TerminalFailure(
            Integer.toString(WorldlineStatus.CODE_CANCELLATION_REJECTED)),
        WorldlineOperations.CANCEL_PAYMENT);
    payments.theClaimIsTaken();

    WorldlineApiResponse refused = cancel(paymentId, "{}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.CONFLICT);
  }

  @Test
  void a_rule_that_declines_the_read_of_a_session_answers_the_code_it_named() {
    String sessionId = String.valueOf(open().providerObjectId());
    aRuleThat(
        new ScenarioAction.TerminalFailure(WorldlineErrors.THE_CARD_WAS_DECLINED),
        WorldlineOperations.GET_HOSTED_TOKENIZATION);

    WorldlineApiResponse read =
        api()
            .getHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.GET,
                    WorldlineApiController.theHostedTokenizationOf(
                        WorldlineWire.MERCHANT, sessionId),
                    ""),
                WorldlineWire.MERCHANT,
                sessionId);

    assertThat(read.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(read)).contains(WorldlineErrors.THE_CARD_WAS_DECLINED);
  }

  private String aHold() {
    String token = aStoredToken();
    return String.valueOf(
        api()
            .createPayment(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.thePaymentsOf(WorldlineWire.MERCHANT),
                    WorldlineWire.aPaymentBody(5000L, "PLN", token, "PRE_AUTHORIZATION")),
                WorldlineWire.MERCHANT)
            .providerObjectId());
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

  private WorldlineProvider aProvider() {
    ScenarioEngine scenarios = new ScenarioEngine(rules);
    WorldlineAnswers answers = new WorldlineAnswers(ids);
    WorldlinePayerCards cards = new WorldlinePayerCards(fixtures);
    return new WorldlineProvider(
        merchants,
        sessions,
        payments,
        keys,
        audit,
        fixtures,
        rules,
        new WorldlineDoubles.HeldResponses(),
        WorldlineDoubles.clockAt(WHEN),
        new CreateHostedTokenization(
            sessions, answers, ids, scenarios, WorldlineWire.PUBLIC_BASE_URL),
        new CreatePayment(payments, sessions, cards, keys, answers, ids, scenarios),
        new CapturePayment(payments, answers, keys, scenarios),
        new CancelPayment(payments, answers, keys, scenarios),
        new TokenizeOnTheHostedPage(sessions, cards, ids),
        ids);
  }

  private static ConfiguredAccount anAccount(String accountId, String merchantId, String apiKey) {
    return new ConfiguredAccount(
        accountId,
        Map.of(
            WorldlineMerchant.MERCHANT_ID_PROPERTY, merchantId,
            WorldlineMerchant.API_KEY_PROPERTY, apiKey,
            WorldlineMerchant.API_SECRET_PROPERTY, "a-secret"));
  }

  private WorldlineMerchant aVerifyingAccount() {
    WorldlineMerchant verifying =
        WorldlineDoubles.merchant(
            RUN,
            WorldlineWire.ACCOUNT,
            WorldlineWire.MERCHANT,
            Map.of(
                WorldlineAuthentication.SETTING,
                WorldlineAuthentication.THE_SIGNATURE_IS_VERIFIED.name()));
    merchants.with(verifying);
    return verifying;
  }

  private static HostedCompletionRequest aPayerWhoPaid(String sessionId) {
    return new HostedCompletionRequest(
        RUN,
        sessionId,
        PayerOutcome.PAID,
        WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS,
        ObjectOrigin.CONTROL_API);
  }

  private String aStoredToken() {
    String sessionId = String.valueOf(open().providerObjectId());
    new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids)
        .complete(aPayerWhoPaid(sessionId), sessions.findById(RUN, sessionId).orElseThrow(), WHEN);
    return String.valueOf(sessions.findById(RUN, sessionId).orElseThrow().token());
  }

  private WorldlineApiResponse create(String token, Map<String, List<String>> headers) {
    return api()
        .createPayment(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.thePaymentsOf(WorldlineWire.MERCHANT),
                headers,
                WorldlineWire.aPaymentBody(5000L, "PLN", token, "SALE")),
            WorldlineWire.MERCHANT);
  }
}
