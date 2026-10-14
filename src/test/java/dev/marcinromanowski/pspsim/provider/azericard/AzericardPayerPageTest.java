package dev.marcinromanowski.pspsim.provider.azericard;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.InMemoryKeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.RequestFingerprint;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheObjectsADeclarationPromises;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptWithheldException;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryHasNoTargetException;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.net.URLDecoder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

class AzericardPayerPageTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId RUN = TestRunId.of("azericard-page");
  private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
  private static final String TERMINAL_ID = "17205353";
  private static final String MERCHANT = "merchant-1";
  private static final String ANOTHER_MERCHANT = "merchant-2";
  private static final String ANOTHER_ACCOUNTS_TARGET = "https://merchant-2.example/callback";
  private static final String ORDER = "20260929120001";
  private static final String NONCE = "F2B2DD7E603A7ADA";
  private static final String BACKREF = "https://merchant.example/azericard/callback";
  private static final String MERCH_URL = "https://merchant.example/shop";
  private static final String TOKEN_ROUTE = "/token/cgi_link";
  private static final String CGI_ROUTE = "/cgi-bin/cgi_link";
  private static final String A_PUBLISHED_PAN = "4127208104942601";
  private static final String ITS_CVC = "796";
  private static final String AN_EXPIRY = "05/31";
  private static final String AN_UNKNOWN_PAN = "4000000000000002";
  private static final String NINE_DIGITS_NO_TEST_CARD_HAS = "999888777";
  private static final String FIRST_SESSION = "00000000000000000000000000000001";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();
  private final AzericardDoubles.PayerSessions sessions = new AzericardDoubles.PayerSessions();
  private final List<String> postedCallbacks = new ArrayList<>();
  private final AzericardDoubles.CountingIds ids = new AzericardDoubles.CountingIds();
  private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
  private final SimulatorClock clock = testRunId -> now.get();

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> {
            postedCallbacks.add(new String(payload.body(), UTF_8));
            return new MerchantResponse.Answered(200, "");
          },
          audit,
          clock,
          new AzericardDoubles.CountingIds(),
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new AzericardCallbackAcknowledgement()),
          List.of(new AzericardCallbackRendering(accounts)),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final AzericardCallbacks callbacks =
      new AzericardCallbacks(payments, accounts, accounts, dispatcher, clock, ids);

  private final Authorize authorize = new Authorize(payments, cards, ids, scenarioEngine);

  private final AzericardPayerCheckout checkout =
      AzericardDoubles.checkoutOf(
          sessions, payments, accounts, authorize, callbacks, audit, clock, ids);

  private final AzericardGateway gateway =
      new AzericardGateway(
          accounts,
          testRuns,
          audit,
          clock,
          ids,
          DEFAULT_RUN,
          authorize,
          new CompleteOrReverse(payments, scenarioEngine),
          new LookUpStatus(payments, scenarioEngine),
          checkout);

  private final SimulatorProvider provider =
      new AzericardProvider(
          accounts,
          audit,
          payments,
          cards,
          new InMemoryPaymentMethodFixtures(),
          scenarioRules,
          AzericardDoubles.heldResponses(),
          callbacks,
          dispatcher,
          clock,
          ids,
          authorize,
          new CompleteOrReverse(payments, scenarioEngine),
          sessions,
          checkout);

  @BeforeEach
  void configureTheTerminalAndItsCallbackTarget() {
    accounts.with(AzericardDoubles.terminal(RUN, MERCHANT, TERMINAL_ID));
    testRuns.register(RUN);
    accounts.merge(
        AzericardGateway.AZERICARD,
        RUN,
        List.of(MERCHANT),
        List.of(new WebhookTarget(MERCHANT, BACKREF)),
        new ProviderConfigurationPlan(Set.of(), List.of(), () -> {}));
  }

  @Test
  void a_card_save_the_browser_hands_over_opens_a_page_and_changes_nothing_yet() {
    AzericardApiResponse handedOver = gateway.tokenLink(tokenCall(signed(cardSave())));

    assertThat(handedOver.httpStatus()).isEqualTo(200);
    assertThat(handedOver.contentType()).isEqualTo(AzericardPayerPage.CONTENT_TYPE);
    assertThat(sessions.all()).singleElement().satisfies(s -> assertThat(s.isSettled()).isFalse());
    assertThat(payments.all()).isEmpty();
    assertThat(cards.all()).isEmpty();
    assertThat(
            payments.isClaimedByAPayment(
                RUN, TERMINAL_ID, AzericardOrderTrace.dayOf(NOW), "120001"))
        .isFalse();
    assertThat(
            payments.theSessionHoldingOrderTrace(
                RUN, TERMINAL_ID, AzericardOrderTrace.dayOf(NOW), "120001"))
        .contains(theSession());
    assertThat(postedCallbacks).isEmpty();
    assertThat(bodyOf(handedOver))
        .contains("The Shop")
        .contains("0.01 AZN")
        .contains("name=\"session\" value=\"" + theSession() + "\"")
        .contains("name=\"cardNumber\"")
        .contains("name=\"expiry\"")
        .contains("name=\"cvc\"")
        .contains("value=\"cancel\"");
  }

  @Test
  void a_hold_the_browser_hands_over_on_cgi_link_opens_the_same_page() {
    Map<String, String> hold = cardSave();
    hold.remove(AzericardFields.TOKEN_ACTION);
    hold.remove(AzericardFields.MERCH_TRAN_STATE);
    hold.put(AzericardFields.TRTYPE, "0");

    AzericardApiResponse handedOver = gateway.cgiLink(cgiCall(signed(hold)));

    assertThat(bodyOf(handedOver)).contains("name=\"cardNumber\"");
    assertThat(payments.all()).isEmpty();
    assertThat(sessions.all()).hasSize(1);
  }

  @Test
  void a_request_carrying_the_s2s_card_fields_is_still_answered_at_once() {
    Map<String, String> s2s = cardSave();
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);

    AzericardApiResponse answered = gateway.tokenLink(tokenCall(signed(s2s)));

    assertThat(bodyOf(answered)).contains("<action>0</action>").doesNotContain("cardNumber");
    assertThat(sessions.all()).isEmpty();
    assertThat(cards.all()).hasSize(1);
  }

  @Test
  void a_handoff_with_a_signature_no_key_produced_is_refused_as_today_and_opens_no_page() {
    Map<String, String> forged = signed(cardSave());
    forged.put(AzericardFields.P_SIGN, "00");

    AzericardApiResponse refused = gateway.tokenLink(tokenCall(forged));

    assertThat(bodyOf(refused))
        .contains("<action>3</action>")
        .contains("<rc>" + AzericardOutcomes.RC_ACCESS_DENIED + "</rc>");
    assertThat(sessions.all()).isEmpty();
  }

  @Test
  void a_handoff_whose_order_trace_was_already_claimed_today_is_refused_as_a_duplicate() {
    payments.claimOrderTrace(RUN, TERMINAL_ID, AzericardOrderTrace.dayOf(NOW), "120001");

    AzericardApiResponse refused = gateway.tokenLink(tokenCall(signed(cardSave())));

    assertThat(bodyOf(refused)).contains("<action>1</action>");
    assertThat(sessions.all()).isEmpty();
  }

  @Test
  void a_published_test_card_saves_the_card_and_queues_a_signed_callback_with_a_masked_card() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    AzericardApiResponse result = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(bodyOf(result))
        .contains(AzericardPayerPage.SUCCESSFUL)
        .contains("href=\"" + MERCH_URL + "\"");
    AzericardPayment payment = onlyPayment();
    assertThat(payment.wasApproved()).isTrue();
    assertThat(payment.cardLast4()).isEqualTo("2601");
    String token = cards.all().get(0).token();
    Map<String, String> callback = theOnlyCallback();
    assertThat(callback)
        .containsEntry(AzericardFields.CARD, "************2601")
        .containsEntry(AzericardFields.TOKEN, token)
        .containsEntry(AzericardFields.ACTION, AzericardOutcomes.ACTION_SUCCEEDED)
        .containsEntry(AzericardFields.INT_REF, payment.intRef())
        .containsEntry(AzericardFields.RRN, payment.rrn());
    assertThat(
            AzericardKeyFixture.verifiedByTheGateway(
                AzericardMac.sourceOf(
                    callback.get(AzericardFields.AMOUNT),
                    callback.get(AzericardFields.TERMINAL),
                    callback.get(AzericardFields.APPROVAL),
                    callback.get(AzericardFields.RRN),
                    callback.get(AzericardFields.INT_REF)),
                callback.get(AzericardFields.P_SIGN)))
        .isTrue();
    assertThat(sessions.all().get(0).outcome()).isEqualTo(PayerOutcome.PAID);
  }

  @Test
  void an_approval_goes_through_the_scenario_rules_like_any_other_authorization() {
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=1 TOKEN_ACTION=REGISTER"),
            new ScenarioAction.TerminalFailure("05"),
            null));
    gateway.tokenLink(tokenCall(signed(cardSave())));

    AzericardApiResponse result = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(bodyOf(result)).contains(AzericardPayerPage.FAILED);
    assertThat(theOnlyCallback()).containsEntry(AzericardFields.RC, "05");
    assertThat(cards.all()).isEmpty();
  }

  @ParameterizedTest(name = "{0} ends failed with its callback")
  @ValueSource(strings = {"unknown card", "expired card", "malformed cvc", "cancel"})
  void a_card_the_gateway_does_not_accept_and_a_cancel_each_end_failed_with_their_callback(
      String what) {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    AzericardApiResponse result =
        submit(
            switch (what) {
              case "unknown card" -> pay(AN_UNKNOWN_PAN, "05/31", ITS_CVC);
              case "expired card" -> pay(A_PUBLISHED_PAN, "08/26", ITS_CVC);
              case "malformed cvc" -> pay(A_PUBLISHED_PAN, "05/31", "79");
              default -> cancel();
            });

    assertThat(bodyOf(result)).contains(AzericardPayerPage.FAILED);
    assertThat(onlyPayment().wasApproved()).isFalse();
    assertThat(cards.all()).isEmpty();
    assertThat(theOnlyCallback())
        .containsEntry(AzericardFields.ACTION, AzericardOutcomes.ACTION_DECLINED)
        .containsEntry(AzericardFields.RC, AzericardOutcomes.RC_CARD_NOT_ACCEPTED)
        .doesNotContainKey(AzericardFields.TOKEN);
    assertThat(sessions.all().get(0).outcome())
        .isEqualTo("cancel".equals(what) ? PayerOutcome.ABANDONED : PayerOutcome.DECLINED);
  }

  @Test
  void a_second_submission_shows_the_outcome_already_reached_and_authorizes_nothing_more() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    AzericardApiResponse again = submit(pay(AN_UNKNOWN_PAN, "05/31", ITS_CVC));

    assertThat(bodyOf(again)).contains(AzericardPayerPage.SUCCESSFUL);
    assertThat(payments.all()).hasSize(1);
    assertThat(postedCallbacks).hasSize(1);
  }

  @Test
  void a_page_whose_callback_target_was_removed_after_it_opened_refuses_and_stays_completable() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    accounts.merge(
        AzericardGateway.AZERICARD,
        RUN,
        List.of(MERCHANT),
        List.of(),
        new ProviderConfigurationPlan(Set.of(), List.of(), () -> {}));

    AzericardApiResponse refused = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(refused.httpStatus()).isEqualTo(AzericardPayerCheckout.REFUSED);
    assertThat(payments.all()).isEmpty();
    assertThat(sessions.all().get(0).isSettled()).isFalse();
  }

  @Test
  void a_handoff_whose_backref_is_no_configured_target_is_refused_before_any_page_opens() {
    Map<String, String> elsewhere = cardSave();
    elsewhere.put(AzericardFields.BACKREF, "https://nobody.example/callback");

    AzericardApiResponse refused = gateway.tokenLink(tokenCall(signed(elsewhere)));

    assertThat(bodyOf(refused))
        .contains("Error in merchant URL for posting authorization result field")
        .doesNotContain("cardNumber");
    assertThat(refused.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(sessions.all()).isEmpty();
    assertThat(payments.all()).isEmpty();
    assertThat(audit.all())
        .anySatisfy(
            record ->
                assertThat(String.valueOf(record.refusal()))
                    .contains(AzericardCallbacks.BACKREF_IS_NOT_A_CONFIGURED_TARGET));
  }

  @ParameterizedTest(name = "BACKREF {0}")
  @ValueSource(strings = {"https://nobody.example/callback", ANOTHER_ACCOUNTS_TARGET})
  void
      a_merchant_initiated_payment_whose_backref_is_no_target_of_its_account_is_authorized_and_its_callback_undeliverable(
          String backref) {
    accounts.with(AzericardDoubles.terminal(RUN, ANOTHER_MERCHANT, "17205355"));
    accounts.merge(
        AzericardGateway.AZERICARD,
        RUN,
        List.of(ANOTHER_MERCHANT),
        List.of(new WebhookTarget(ANOTHER_MERCHANT, ANOTHER_ACCOUNTS_TARGET)),
        new ProviderConfigurationPlan(Set.of(), List.of(), () -> {}));
    Map<String, String> save = cardSave();
    save.put(AzericardFields.MIT_AGREEMENT, "N");
    save.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");
    gateway.tokenLink(tokenCall(signed(save)));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    Map<String, String> saved = theOnlyCallback();
    Map<String, String> charge =
        merchantInitiated(saved.get(AzericardFields.TOKEN), saved.get(AzericardFields.EXT_NET_REF));
    charge.put(AzericardFields.BACKREF, backref);

    AzericardApiResponse paid = gateway.tokenLink(tokenCall(signed(charge)));

    assertThat(paid.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(paid)).startsWith("<response>").contains("<action>0</action>");
    assertThat(payments.all()).hasSize(2);
    String intRef = payments.all().get(1).intRef();
    dispatcher.deliverDue(AzericardGateway.AZERICARD, RUN);
    assertThat(postedCallbacks).hasSize(1);
    assertThat(audit.all())
        .filteredOn(record -> record.direction() == AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.providerObjectId()).isEqualTo(intRef);
              assertThat(record.path()).isEqualTo(backref);
              assertThat(record.refusal()).isNull();
              assertThat(record.origin()).isEqualTo(ObjectOrigin.SCHEDULED_JOB);
            });
    assertThat(dispatcher.of(AzericardGateway.AZERICARD, RUN))
        .filteredOn(delivery -> intRef.equals(delivery.providerObjectId()))
        .singleElement()
        .satisfies(
            delivery -> {
              assertThat(delivery.state()).isEqualTo(DeliveryState.UNDELIVERABLE);
              assertThat(delivery.targetUrl()).isEqualTo(backref);
              assertThat(delivery.notSentBecause())
                  .isEqualTo(AzericardCallbacks.NOT_A_TARGET_OF_THE_ACCOUNT);
            });
  }

  @Test
  void
      a_merchant_initiated_payment_without_backref_for_an_account_with_no_single_target_is_undeliverable() {
    Map<String, String> save = cardSave();
    save.put(AzericardFields.MIT_AGREEMENT, "N");
    save.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");
    gateway.tokenLink(tokenCall(signed(save)));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    Map<String, String> saved = theOnlyCallback();
    Map<String, String> charge =
        merchantInitiated(saved.get(AzericardFields.TOKEN), saved.get(AzericardFields.EXT_NET_REF));
    charge.remove(AzericardFields.BACKREF);
    accounts.merge(
        AzericardGateway.AZERICARD,
        RUN,
        List.of(MERCHANT),
        List.of(),
        new ProviderConfigurationPlan(Set.of(), List.of(), () -> {}));

    AzericardApiResponse paid = gateway.tokenLink(tokenCall(signed(charge)));

    assertThat(paid.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(postedCallbacks).hasSize(1);
    assertThat(dispatcher.of(AzericardGateway.AZERICARD, RUN))
        .filteredOn(delivery -> delivery.state() == DeliveryState.UNDELIVERABLE)
        .singleElement()
        .satisfies(
            delivery -> {
              assertThat(delivery.targetUrl()).isNull();
              assertThat(delivery.attemptsMade()).isZero();
              assertThat(delivery.notSentBecause()).isEqualTo(AzericardCallbacks.NO_SINGLE_TARGET);
            });
    String job =
        dispatcher.of(AzericardGateway.AZERICARD, RUN).stream()
            .filter(delivery -> delivery.state() == DeliveryState.UNDELIVERABLE)
            .findFirst()
            .orElseThrow()
            .deliveryId();
    assertThatThrownBy(() -> dispatcher.retry(AzericardGateway.AZERICARD, RUN, job, BACKREF))
        .isInstanceOf(DeliveryHasNoTargetException.class);
    dispatcher.deliverDue(AzericardGateway.AZERICARD, RUN);
    assertThat(postedCallbacks).hasSize(1);
    assertThat(dispatcher.attemptsOf(job)).isEmpty();
  }

  @Test
  void
      a_retry_that_names_another_accounts_target_is_refused_and_the_delivered_callback_stays_delivered() {
    accounts.merge(
        AzericardGateway.AZERICARD,
        RUN,
        List.of(ANOTHER_MERCHANT),
        List.of(new WebhookTarget(ANOTHER_MERCHANT, ANOTHER_ACCOUNTS_TARGET)),
        new ProviderConfigurationPlan(Set.of(), List.of(), () -> {}));
    gateway.tokenLink(tokenCall(signed(cardSave())));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    String job = dispatcher.of(AzericardGateway.AZERICARD, RUN).get(0).deliveryId();

    assertThatThrownBy(
            () -> dispatcher.retry(AzericardGateway.AZERICARD, RUN, job, ANOTHER_ACCOUNTS_TARGET))
        .isInstanceOf(AttemptWithheldException.class)
        .hasMessage(
            AttemptWithheldException.THE_PROVIDER_WITHHELD_IT
                + AzericardCallbacks.NOT_A_TARGET_OF_THE_ACCOUNT);

    assertThat(postedCallbacks).hasSize(1);
    assertThat(dispatcher.find(AzericardGateway.AZERICARD, RUN, job))
        .hasValueSatisfying(
            delivery -> {
              assertThat(delivery.state()).isEqualTo(DeliveryState.DELIVERED);
              assertThat(delivery.attemptsMade()).isEqualTo(1);
            });
  }

  @Test
  void a_retry_of_a_callback_withheld_at_its_backref_that_names_the_accounts_own_target_is_sent() {
    Map<String, String> save = cardSave();
    save.put(AzericardFields.MIT_AGREEMENT, "N");
    save.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");
    gateway.tokenLink(tokenCall(signed(save)));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    Map<String, String> saved = theOnlyCallback();
    Map<String, String> charge =
        merchantInitiated(saved.get(AzericardFields.TOKEN), saved.get(AzericardFields.EXT_NET_REF));
    charge.put(AzericardFields.BACKREF, "https://nobody.example/callback");
    gateway.tokenLink(tokenCall(signed(charge)));
    String job =
        dispatcher.of(AzericardGateway.AZERICARD, RUN).stream()
            .filter(delivery -> delivery.state() == DeliveryState.UNDELIVERABLE)
            .findFirst()
            .orElseThrow()
            .deliveryId();

    dispatcher.retry(AzericardGateway.AZERICARD, RUN, job, BACKREF);

    assertThat(postedCallbacks).hasSize(2);
    assertThat(postedCallbacks.get(1)).contains("ORDER=20260929120009");
  }

  @Test
  void a_merchant_initiated_payment_runs_in_a_unit_that_is_kept_and_calls_its_account_back() {
    Map<String, String> save = cardSave();
    save.put(AzericardFields.MIT_AGREEMENT, "N");
    save.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");
    gateway.tokenLink(tokenCall(signed(save)));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    Map<String, String> saved = theOnlyCallback();
    int unitsBefore = sessions.units().endings().size();

    gateway.tokenLink(
        tokenCall(
            signed(
                merchantInitiated(
                    saved.get(AzericardFields.TOKEN), saved.get(AzericardFields.EXT_NET_REF)))));

    assertThat(sessions.units().endings().subList(unitsBefore, sessions.units().endings().size()))
        .containsExactly(RecordedUnitsOfWork.Ending.KEPT);
    assertThat(postedCallbacks).hasSize(2);
    assertThat(postedCallbacks.get(1)).contains("ORDER=20260929120009");
  }

  @Test
  void an_unknown_session_is_answered_404_and_touches_nothing() {
    AzericardApiResponse unknown =
        checkout.submit(
            new AzericardApiCall(
                "POST",
                AzericardPayerPage.PATH,
                Map.of(),
                Map.of(AzericardPayerPage.SESSION, List.of("nope"))));

    assertThat(unknown.httpStatus()).isEqualTo(AzericardPayerCheckout.NO_SUCH_PAGE);
  }

  @Test
  void the_pan_expiry_and_cvc_appear_in_no_response_row_audit_record_or_callback() {
    String page = bodyOf(gateway.tokenLink(tokenCall(signed(cardSave()))));
    String result = bodyOf(submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC)));
    Map<String, String> s2s = cardSave();
    s2s.put(AzericardFields.ORDER, "20260929120002");
    s2s.put(AzericardFields.CARD, NINE_DIGITS_NO_TEST_CARD_HAS);
    s2s.put(AzericardFields.EXP, "05");
    s2s.put(AzericardFields.EXP_YEAR, "31");
    s2s.put(AzericardFields.CVC2, ITS_CVC);
    String s2sAnswer = bodyOf(gateway.tokenLink(tokenCall(signed(s2s))));
    dispatcher.deliverDue(AzericardGateway.AZERICARD, RUN);

    List<String> everywhere = new ArrayList<>();
    everywhere.add(page);
    everywhere.add(result);
    everywhere.add(s2sAnswer);
    everywhere.add(payments.all().toString());
    everywhere.add(cards.all().toString());
    everywhere.add(sessions.all().toString());
    everywhere.add(audit.all().toString());
    everywhere.add(postedCallbacks.toString());
    everywhere.add(
        payments
            .exchanges(
                new KeyedExchangeQuery(
                    RUN, KeyedExchangeQuery.FROM_THE_NEWEST, KeyedExchangeQuery.DEFAULT_LIMIT))
            .toString());

    assertThat(String.join("\n", everywhere))
        .doesNotContain(A_PUBLISHED_PAN)
        .doesNotContain(NINE_DIGITS_NO_TEST_CARD_HAS)
        .doesNotContain("05/31");
    Set<String> cardKeys =
        Set.of(
            AzericardPayerPage.CARD_NUMBER,
            AzericardPayerPage.EXPIRY,
            AzericardPayerPage.CVC,
            AzericardFields.CARD,
            AzericardFields.EXP,
            AzericardFields.EXP_YEAR,
            AzericardFields.CVC2);
    assertThat(audit.all())
        .allSatisfy(
            record ->
                cardKeys.forEach(
                    key -> {
                      List<String> held = record.sanitizedBody().get(key);
                      assertThat(held == null ? List.of("[redacted]") : held)
                          .as(key + " in the audit record of " + record.path())
                          .allMatch(value -> value.matches("\\[redacted]|\\*{12}[0-9]{4}"));
                    }));
    assertThat(audit.all())
        .anySatisfy(
            record ->
                assertThat(record.sanitizedBody())
                    .containsEntry(AzericardPayerPage.CVC, List.of("[redacted]")))
        .anySatisfy(
            record ->
                assertThat(record.sanitizedBody())
                    .containsEntry(AzericardFields.CVC2, List.of("[redacted]")));
    assertThat(sessions.all())
        .allSatisfy(
            session ->
                assertThat(session.form())
                    .doesNotContainKeys(
                        AzericardFields.CVC2, AzericardFields.EXP, AzericardFields.EXP_YEAR));
    for (String callback : postedCallbacks) {
      assertThat(callback).doesNotContain(AzericardFields.CVC2 + "=").doesNotContain("EXP=");
    }
  }

  @Test
  void a_second_tap_while_the_first_is_being_authorized_authorizes_nothing_and_the_first_wins() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    List<AzericardApiResponse> secondTap = new ArrayList<>();
    payments.runningBeforeTheNextTraceClaim(
        () -> secondTap.add(submit(pay(AN_UNKNOWN_PAN, "05/31", ITS_CVC))));

    AzericardApiResponse firstTap = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(secondTap)
        .singleElement()
        .satisfies(
            answer -> {
              assertThat(answer.httpStatus()).isEqualTo(AzericardPayerCheckout.BUSY);
              assertThat(bodyOf(answer))
                  .contains(AzericardPayerPage.IN_PROGRESS)
                  .doesNotContain(AzericardPayerPage.FAILED);
            });
    assertThat(bodyOf(firstTap)).contains(AzericardPayerPage.SUCCESSFUL);
    assertThat(payments.all()).singleElement().satisfies(p -> assertThat(p.wasApproved()).isTrue());
    assertThat(sessions.all().get(0).outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(theOnlyCallback()).containsEntry(AzericardFields.ACTION, "0");
  }

  @Test
  void a_page_a_scenario_answered_in_place_of_is_released_and_its_payer_can_pay_again() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=1 TOKEN_ACTION=REGISTER"),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    int unitsBefore = sessions.units().endings().size();
    AzericardApiResponse unreached = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    assertThat(sessions.units().endings().subList(unitsBefore, sessions.units().endings().size()))
        .containsExactly(RecordedUnitsOfWork.Ending.KEPT);
    assertThat(sessions.all().get(0).isSettled()).isFalse();
    scenarioRules.clear(AzericardGateway.AZERICARD, RUN);
    AzericardApiResponse paid = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(unreached.httpStatus()).isEqualTo(502);
    assertThat(bodyOf(paid)).contains(AzericardPayerPage.SUCCESSFUL);
    assertThat(sessions.all().get(0).outcome()).isEqualTo(PayerOutcome.PAID);
  }

  @Test
  void a_second_page_for_an_order_another_open_page_holds_is_refused_as_a_duplicate() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    Map<String, String> another = cardSave();
    another.put(AzericardFields.NONCE, "0A0B0C0D0E0F1011");

    AzericardApiResponse second = gateway.tokenLink(tokenCall(signed(another)));

    assertThat(bodyOf(second)).contains("<action>1</action>");
    assertThat(sessions.all()).hasSize(1);
  }

  @Test
  void the_same_signed_form_posted_again_while_its_page_is_open_is_served_that_same_page() {
    Map<String, String> form = signed(cardSave());
    String first = bodyOf(gateway.tokenLink(tokenCall(form)));

    AzericardApiResponse again = gateway.tokenLink(tokenCall(form));

    assertThat(again.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(again))
        .isEqualTo(first)
        .contains("name=\"session\" value=\"" + theSession() + "\"");
    assertThat(again.providerObjectId()).isEqualTo(theSession());
    assertThat(sessions.all()).hasSize(1);
    assertThat(bodyOf(submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC))))
        .contains(AzericardPayerPage.SUCCESSFUL);
  }

  @Test
  void the_same_signed_form_posted_after_its_page_reached_an_outcome_is_a_duplicate() {
    Map<String, String> form = signed(cardSave());
    gateway.tokenLink(tokenCall(form));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    AzericardApiResponse again = gateway.tokenLink(tokenCall(form));

    assertThat(bodyOf(again)).contains("<action>1</action>");
    assertThat(sessions.all()).hasSize(1);
  }

  @Test
  void a_page_paid_the_day_after_it_opened_is_recorded_under_the_day_its_trace_was_held() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    now.set(NOW.plus(Duration.ofDays(1)));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    Map<String, String> theNextDay = cardSave();
    theNextDay.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    theNextDay.put(AzericardFields.NONCE, "0A0B0C0D0E0F1011");
    theNextDay.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(now.get()));

    gateway.tokenLink(tokenCall(signed(theNextDay)));

    assertThat(
            payments
                .exchanges(
                    new KeyedExchangeQuery(
                        RUN, KeyedExchangeQuery.FROM_THE_NEWEST, KeyedExchangeQuery.DEFAULT_LIMIT))
                .exchanges())
        .filteredOn(exchange -> exchange.keyName().equals(AzericardKeyMechanisms.ORDER))
        .extracting(KeyedExchange::keyValue, exchange -> exchange.requests().get(0).verdict())
        .containsExactlyInAnyOrder(
            tuple("2026-09-29 120001", ExchangeVerdict.FIRST_REQUEST),
            tuple("2026-09-30 120001", ExchangeVerdict.FIRST_REQUEST));
  }

  @Test
  void an_s2s_card_authorization_for_an_order_an_open_page_holds_is_refused_as_a_duplicate() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    Map<String, String> s2s = cardSave();
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    s2s.put(AzericardFields.NONCE, "0A0B0C0D0E0F1011");

    AzericardApiResponse refused = gateway.tokenLink(tokenCall(signed(s2s)));

    assertThat(bodyOf(refused)).contains("<action>1</action>");
    assertThat(payments.all()).isEmpty();
    assertThat(bodyOf(submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC))))
        .contains(AzericardPayerPage.SUCCESSFUL);
    assertThat(onlyPayment().rrn()).isNotEqualTo(AzericardOutcomes.ABSENT);
    assertThat(theOnlyCallback()).containsEntry(AzericardFields.ACTION, "0");
  }

  @Test
  void a_page_reset_away_while_its_submission_was_on_its_way_is_answered_as_unknown() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    String session = theSession();
    sessions.runningBeforeTheNextLock(() -> provider.reset(RUN, ResetScope.TEST_RUN));

    AzericardApiResponse answer = submit(session, pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(answer.httpStatus()).isEqualTo(AzericardPayerCheckout.NO_SUCH_PAGE);
    assertThat(bodyOf(answer))
        .contains(AzericardPayerPage.escaped(AzericardPayerPage.NO_SUCH_PAGE))
        .doesNotContain(AzericardPayerPage.IN_PROGRESS);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void a_completion_of_a_page_reset_away_on_its_way_names_no_such_object() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    String session = theSession();
    sessions.runningBeforeTheNextLock(() -> provider.reset(RUN, ResetScope.TEST_RUN));

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, session, PayerOutcome.PAID, "azericard-2601", ObjectOrigin.CONTROL_API));

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.NoSuchObject.class);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void a_completion_of_a_page_another_submission_settled_on_its_way_is_refused_as_completed() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    String session = theSession();
    sessions.runningBeforeTheNextLock(
        () -> submit(session, pay(A_PUBLISHED_PAN, "05/31", ITS_CVC)));

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, session, PayerOutcome.PAID, "azericard-2601", ObjectOrigin.CONTROL_API));

    assertThat(outcome)
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(
                AzericardPayerCheckout.ALREADY_COMPLETED));
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void a_submission_that_finds_its_page_settled_on_its_way_shows_the_outcome_reached() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    String session = theSession();
    sessions.runningBeforeTheNextLock(
        () -> submit(session, pay(A_PUBLISHED_PAN, "05/31", ITS_CVC)));

    AzericardApiResponse late = submit(session, pay(AN_UNKNOWN_PAN, "05/31", ITS_CVC));

    assertThat(late.httpStatus()).isEqualTo(AzericardPayerCheckout.SHOWN);
    assertThat(bodyOf(late)).contains(AzericardPayerPage.SUCCESSFUL);
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void every_page_answer_carries_the_headers_that_keep_a_card_page_out_of_caches_and_frames() {
    AzericardApiResponse page = gateway.tokenLink(tokenCall(signed(cardSave())));
    AzericardApiResponse result = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    AzericardApiResponse unknown =
        checkout.submit(
            new AzericardApiCall(
                "POST",
                AzericardPayerPage.PATH,
                Map.of(),
                Map.of(AzericardPayerPage.SESSION, List.of("nope"))));

    AzericardApiResponse instead = aScenarioAnswerInPlaceOfTheAuthorization();
    for (AzericardApiResponse answer : List.of(page, result, unknown, instead)) {
      assertThat(answer.headers().keySet())
          .as(
              "a header order drawn from a salted map varies per process, and a client cannot"
                  + " tell this simulator from the gateway by a difference that moves")
          .containsExactly(
              "Cache-Control",
              "X-Content-Type-Options",
              "Referrer-Policy",
              "Content-Security-Policy",
              "Content-Type");
      assertThat(answer.headers())
          .containsEntry("Cache-Control", List.of("no-store"))
          .containsEntry("X-Content-Type-Options", List.of("nosniff"))
          .containsEntry("Referrer-Policy", List.of("no-referrer"))
          .containsEntry(
              "Content-Security-Policy",
              List.of(
                  "default-src 'none'; style-src 'unsafe-inline'; form-action 'self';"
                      + " frame-ancestors 'none'; base-uri 'none'"));
    }
    Map<String, String> s2s = cardSave();
    s2s.put(AzericardFields.ORDER, "20260929120003");
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    assertThat(gateway.tokenLink(tokenCall(signed(s2s))).headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Content-Security-Policy"))
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Cache-Control"));
  }

  private AzericardApiResponse aScenarioAnswerInPlaceOfTheAuthorization() {
    Map<String, String> another = cardSave();
    another.put(AzericardFields.ORDER, "20260929120004");
    gateway.tokenLink(tokenCall(signed(another)));
    String session = sessions.all().get(sessions.all().size() - 1).sessionId();
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=1 TOKEN_ACTION=REGISTER"),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));
    AzericardApiResponse instead = submit(session, pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    assertThat(instead.httpStatus()).isEqualTo(502);
    return instead;
  }

  @Test
  void a_session_built_with_m_info_is_refused_as_browser_data_that_would_be_kept() {
    Map<String, List<String>> withMInfo = new LinkedHashMap<>();
    withMInfo.put(AzericardFields.ORDER, List.of(ORDER));
    withMInfo.put(AzericardFields.M_INFO, List.of("eyJtb2JpbGVQaG9uZSI6IjEifQ=="));

    assertThatThrownBy(
            () ->
                new AzericardPayerSession(
                    "s",
                    RUN,
                    AzericardRoute.TOKEN_CGI_LINK,
                    TERMINAL_ID,
                    MERCHANT,
                    withMInfo,
                    Map.of(),
                    null,
                    null,
                    ObjectOrigin.PROVIDER_API,
                    NOW,
                    null))
        .isInstanceOf(BrowserDataWouldBeKeptException.class);
  }

  @Test
  void a_handoff_carrying_m_info_opens_a_page_that_keeps_none_and_is_served_again_on_a_repost() {
    Map<String, String> withMInfo = cardSave();
    withMInfo.put(AzericardFields.M_INFO, "eyJtb2JpbGVQaG9uZSI6Ijk5NDUwMDAwMDAwIn0=");
    Map<String, String> form = signed(withMInfo);
    gateway.tokenLink(tokenCall(form));

    AzericardApiResponse again = gateway.tokenLink(tokenCall(form));

    assertThat(sessions.all())
        .singleElement()
        .satisfies(session -> assertThat(session.form()).doesNotContainKey(AzericardFields.M_INFO));
    assertThat(bodyOf(again)).contains("name=\"session\" value=\"" + theSession() + "\"");
  }

  @Test
  void a_session_built_with_a_card_field_is_refused_as_a_card_that_would_be_kept() {
    Map<String, List<String>> withACard = new LinkedHashMap<>();
    withACard.put(AzericardFields.ORDER, List.of(ORDER));
    withACard.put(AzericardFields.CARD, List.of(A_PUBLISHED_PAN));

    assertThatThrownBy(
            () ->
                new AzericardPayerSession(
                    "s",
                    RUN,
                    AzericardRoute.TOKEN_CGI_LINK,
                    TERMINAL_ID,
                    MERCHANT,
                    withACard,
                    Map.of(),
                    null,
                    null,
                    ObjectOrigin.PROVIDER_API,
                    NOW,
                    null))
        .isInstanceOf(ACardWouldBeKeptException.class);
  }

  @Test
  void an_s2s_card_whose_cvc2_the_merchant_reports_as_not_provided_is_not_declined_for_it() {
    Map<String, String> s2s = cardSave();
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    s2s.remove(AzericardFields.CVC2);
    s2s.put(AzericardFields.CVC2_RC, "0");

    AzericardApiResponse answered = gateway.tokenLink(tokenCall(signed(s2s)));

    assertThat(bodyOf(answered)).contains("<action>0</action>");
  }

  @ParameterizedTest(name = "a CARD request without {0} is refused naming it")
  @ValueSource(strings = {"EXP", "EXP_YEAR", "CVC2"})
  void an_s2s_card_missing_a_field_it_needs_is_refused_naming_it_and_stores_nothing(
      String missing) {
    Map<String, String> s2s = cardSave();
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    s2s.remove(missing);

    AzericardApiResponse refused = gateway.tokenLink(tokenCall(signed(s2s)));

    assertThat(bodyOf(refused))
        .contains(
            switch (missing) {
              case "EXP" -> "Error in card expiration month field";
              case "EXP_YEAR" -> "Error in card expiration year field";
              default -> "Error in card verification code field";
            });
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void a_completion_carrying_card_fields_is_fingerprinted_as_if_it_carried_none() {
    Map<String, String> hold = cardSave();
    hold.remove(AzericardFields.TOKEN_ACTION);
    hold.remove(AzericardFields.MERCH_TRAN_STATE);
    hold.put(AzericardFields.TRTYPE, "0");
    hold.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    gateway.cgiLink(cgiCall(signed(hold)));
    AzericardPayment held = onlyPayment();
    Map<String, List<String>> completion = new LinkedHashMap<>();
    completion.put(AzericardFields.TERMINAL, List.of(TERMINAL_ID));
    completion.put(AzericardFields.TRTYPE, List.of("21"));
    completion.put(AzericardFields.ORDER, List.of(ORDER));
    completion.put(AzericardFields.AMOUNT, List.of("0.01"));
    completion.put(AzericardFields.CURRENCY, List.of("AZN"));
    completion.put(AzericardFields.RRN, List.of(held.rrn()));
    completion.put(AzericardFields.INT_REF, List.of(held.intRef()));
    completion.put(AzericardFields.TIMESTAMP, List.of(AzericardTimestamp.write(NOW)));
    completion.put(AzericardFields.NONCE, List.of(NONCE));
    AzericardMessage unsigned = AzericardMessage.of(AzericardRoute.CGI_LINK, completion);
    completion.put(
        AzericardFields.P_SIGN,
        List.of(AzericardKeyFixture.signedByTheMerchant(unsigned.macSource())));
    String withoutTheCard =
        RequestFingerprint.of(
            InMemoryKeyedExchanges.THE_KEY_OF_EVERY_TEST_RUN,
            AzericardMessage.of(AzericardRoute.CGI_LINK, completion).fingerprinted());
    AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S.forEach(
        (name, value) -> completion.put(name, List.of(value)));

    AzericardApiResponse completed =
        gateway.cgiLink(new AzericardApiCall("POST", CGI_ROUTE, Map.of(), completion));

    assertThat(bodyOf(completed)).contains("<action>0</action>");
    assertThat(
            payments
                .exchanges(
                    new KeyedExchangeQuery(
                        RUN, KeyedExchangeQuery.FROM_THE_NEWEST, KeyedExchangeQuery.DEFAULT_LIMIT))
                .toString())
        .contains(withoutTheCard);
  }

  @Test
  void the_tester_finishes_an_open_page_by_naming_a_published_test_card_by_its_alias() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    String session = theSession();

    HostedCompletionOffer offer = provider.hostedCompletion(RUN, session);
    assertThat(offer).isInstanceOf(HostedCompletionOffer.Offered.class);
    assertThat(((HostedCompletionOffer.Offered) offer).card("azericard-2601")).isPresent();

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, session, PayerOutcome.PAID, "azericard-2601", ObjectOrigin.CONTROL_API));

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.Completed.class);
    assertThat(((HostedCompletionOutcome.Completed) outcome).createdObjectId())
        .isEqualTo(onlyPayment().intRef());
    assertThat(onlyPayment().origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(theOnlyCallback()).containsEntry(AzericardFields.CARD, "************2601");
    assertThat(provider.hostedCompletion(RUN, session))
        .isInstanceOf(HostedCompletionOffer.NotOffered.class);
  }

  @Test
  void a_completion_naming_a_card_that_is_no_published_test_card_is_refused_and_changes_nothing() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, theSession(), PayerOutcome.PAID, "azericard-0000", ObjectOrigin.UI));

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void an_open_page_is_listed_as_an_object_awaiting_its_payer() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    ProviderObjectState state = provider.state(RUN, theSession()).orElseThrow();

    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
    assertThat(state.hostedSession().toString()).contains(MERCHANT);
  }

  @Test
  void a_card_saved_for_merchant_initiated_payments_is_paid_with_its_token_and_ext_net_ref() {
    Map<String, String> save = cardSave();
    save.put(AzericardFields.MIT_AGREEMENT, "N");
    save.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");
    gateway.tokenLink(tokenCall(signed(save)));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    Map<String, String> saved = theOnlyCallback();
    String extNetRef = saved.get(AzericardFields.EXT_NET_REF);
    assertThat(extNetRef).isNotBlank();

    AzericardApiResponse paid =
        gateway.tokenLink(
            tokenCall(signed(merchantInitiated(saved.get(AzericardFields.TOKEN), extNetRef))));

    assertThat(paid.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(paid)).contains("<action>0</action>");
  }

  @Test
  void a_merchant_initiated_payment_naming_another_ext_net_ref_is_refused_as_an_unusable_token() {
    Map<String, String> save = cardSave();
    save.put(AzericardFields.MIT_AGREEMENT, "N");
    save.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");
    gateway.tokenLink(tokenCall(signed(save)));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    AzericardApiResponse refused =
        gateway.tokenLink(
            tokenCall(
                signed(
                    merchantInitiated(
                        theOnlyCallback().get(AzericardFields.TOKEN), "WRONGREF000000"))));

    assertThat(bodyOf(refused)).contains("<rc>" + AzericardOutcomes.RC_NO_SUCH_TOKEN + "</rc>");
  }

  @Test
  void a_merchant_initiated_payment_without_merch_rn_id_is_refused_with_the_error_page() {
    Map<String, String> form = merchantInitiated("A".repeat(28), "EXTREF");
    form.remove(AzericardFields.MERCH_RN_ID);

    AzericardApiResponse refused = gateway.tokenLink(tokenCall(signed(form)));

    assertThat(bodyOf(refused)).contains("Error in merchant recurring nonce field");
  }

  @Test
  void the_card_page_shows_the_description_the_merchant_gave_the_order() {
    assertThat(bodyOf(gateway.tokenLink(tokenCall(signed(cardSave())))))
        .contains("<dt>Description</dt><dd>Card registration</dd>");
  }

  @Test
  void the_result_page_names_the_rrn_of_the_authorization_the_payer_made() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    assertThat(bodyOf(submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC))))
        .contains("<dt>RRN</dt><dd>100000000001</dd>");
  }

  @Test
  void a_page_opened_without_a_merchant_web_site_offers_the_payer_no_way_back() {
    Map<String, String> noSite = cardSave();
    noSite.remove(AzericardFields.MERCH_URL);
    gateway.tokenLink(tokenCall(signed(noSite)));

    assertThat(bodyOf(submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC))))
        .contains("The merchant sent no web site address to return to.")
        .doesNotContain("Return to the merchant")
        .doesNotContain("href=");
  }

  @ParameterizedTest(name = "MERCH_URL {0} is offered as no way back")
  @ValueSource(strings = {"www.sample.com", "javascript:alert(1)", "ftp://merchant.example/"})
  void a_merchant_web_site_a_browser_cannot_follow_is_not_offered_as_the_way_back(String site) {
    Map<String, String> unfollowable = cardSave();
    unfollowable.put(AzericardFields.MERCH_URL, site);
    gateway.tokenLink(tokenCall(signed(unfollowable)));

    assertThat(bodyOf(submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC))))
        .contains("The merchant sent no web site address to return to.")
        .doesNotContain("Return to the merchant")
        .doesNotContain(site);
  }

  @Test
  void a_merchant_web_site_on_plain_http_is_offered_as_the_way_back() {
    Map<String, String> plain = cardSave();
    plain.put(AzericardFields.MERCH_URL, "http://merchant.example/shop");
    gateway.tokenLink(tokenCall(signed(plain)));

    assertThat(bodyOf(submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC))))
        .contains("href=\"http://merchant.example/shop\"");
  }

  @Test
  void a_paid_submission_is_audited_with_the_answer_the_browser_got_naming_the_payment() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(pageAnswers())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.httpStatus()).isEqualTo(200);
              assertThat(row.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
              assertThat(row.providerObjectId()).isEqualTo("0000000000000001");
              assertThat(row.refusal()).isNull();
              assertThat(row.sanitizedBody())
                  .containsEntry("result", List.of("Payment successful"));
            });
  }

  @Test
  void a_declined_submission_is_audited_as_a_refusal_of_the_payment_it_declined() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    submit(pay(AN_UNKNOWN_PAN, "05/31", ITS_CVC));

    assertThat(pageAnswers())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.httpStatus()).isEqualTo(200);
              assertThat(row.outcome()).isEqualTo(ProviderOutcome.FAILURE);
              assertThat(row.providerObjectId()).isEqualTo("0000000000000001");
              assertThat(row.refusal()).isEqualTo(new RefusalSubject.TheObject("0000000000000001"));
            });
  }

  @Test
  void a_scenario_answer_in_place_of_the_authorization_is_audited_as_the_answer_the_browser_got() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=1 TOKEN_ACTION=REGISTER"),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(pageAnswers())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.httpStatus()).isEqualTo(502);
              assertThat(row.providerObjectId()).isEqualTo(FIRST_SESSION);
              assertThat(row.sanitizedBody()).isEmpty();
            });
  }

  @Test
  void a_page_submitted_again_after_it_was_paid_answers_as_a_success() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    AzericardApiResponse again = submit(pay(AN_UNKNOWN_PAN, "05/31", ITS_CVC));

    assertThat(again.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(pageAnswers())
        .extracting(AuditRecord::outcome)
        .containsExactly(ProviderOutcome.SUCCESS, ProviderOutcome.SUCCESS);
  }

  @Test
  void a_page_submitted_again_after_it_was_declined_answers_as_a_failure() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    submit(pay(AN_UNKNOWN_PAN, "05/31", ITS_CVC));

    AzericardApiResponse again = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(again.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(again)).contains(AzericardPayerPage.FAILED);
  }

  @Test
  void a_submission_that_settles_its_page_keeps_what_it_wrote() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    int unitsBefore = sessions.units().endings().size();

    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(sessions.units().endings().subList(unitsBefore, sessions.units().endings().size()))
        .containsExactly(RecordedUnitsOfWork.Ending.KEPT);
  }

  @Test
  void a_submission_that_finds_its_page_settled_on_its_way_keeps_nothing_it_wrote() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    String session = theSession();
    sessions.runningBeforeTheNextLock(
        () -> submit(session, pay(A_PUBLISHED_PAN, "05/31", ITS_CVC)));
    int unitsBefore = sessions.units().endings().size();

    submit(session, pay(AN_UNKNOWN_PAN, "05/31", ITS_CVC));

    assertThat(sessions.units().endings().subList(unitsBefore, sessions.units().endings().size()))
        .containsExactly(RecordedUnitsOfWork.Ending.KEPT, RecordedUnitsOfWork.Ending.DISCARDED);
  }

  @Test
  void a_page_whose_order_trace_is_no_longer_held_when_it_is_paid_authorizes_nothing() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    payments.runningBeforeTheNextTraceClaim(() -> payments.clear(RUN));

    AzericardApiResponse answer = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(bodyOf(answer)).contains(AzericardPayerPage.FAILED);
    assertThat(payments.all()).isEmpty();
    assertThat(cards.all()).isEmpty();
    dispatcher.deliverDue(AzericardGateway.AZERICARD, RUN);
    assertThat(postedCallbacks).isEmpty();
  }

  @Test
  void a_page_whose_terminal_was_removed_after_it_opened_refuses_and_stays_completable() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    accounts.remove(RUN, MERCHANT);

    AzericardApiResponse refused = submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(refused.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(refused)).contains("is no longer configured in this test run");
    assertThat(payments.all()).isEmpty();
    assertThat(sessions.all().get(0).isSettled()).isFalse();
  }

  @Test
  void a_completion_names_the_callback_job_it_queued() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, theSession(), PayerOutcome.PAID, "azericard-2601", ObjectOrigin.CONTROL_API));

    assertThat(outcome)
        .isEqualTo(
            new HostedCompletionOutcome.Completed(
                FIRST_SESSION,
                "0000000000000001",
                PayerOutcome.PAID,
                List.of("whjob_000000000000000000000002")));
  }

  @ParameterizedTest(name = "{0} completes the page paid")
  @ValueSource(strings = {"azericard-2601", "azericard-0780", "azericard-0724"})
  void each_published_test_card_completes_an_open_page_paid(String alias) {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, theSession(), PayerOutcome.PAID, alias, ObjectOrigin.CONTROL_API));

    assertThat(outcome)
        .isInstanceOfSatisfying(
            HostedCompletionOutcome.Completed.class,
            completed -> assertThat(completed.outcome()).isEqualTo(PayerOutcome.PAID));
    assertThat(onlyPayment().wasApproved()).isTrue();
  }

  @Test
  void a_completion_naming_no_published_card_lists_the_cards_it_may_name() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, theSession(), PayerOutcome.PAID, "azericard-0000", ObjectOrigin.UI));

    assertThat(outcome)
        .isInstanceOfSatisfying(
            HostedCompletionOutcome.RefusedByTheProvider.class,
            refused ->
                assertThat(refused.inItsOwnWords())
                    .endsWith(
                        "The cards a payer may name here are: azericard-2601, azericard-0780,"
                            + " azericard-0724"));
  }

  @Test
  void a_page_a_payer_already_paid_is_neither_offered_nor_completed_again() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    HostedCompletionOffer offer = provider.hostedCompletion(RUN, theSession());
    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, theSession(), PayerOutcome.PAID, "azericard-0000", ObjectOrigin.CONTROL_API));

    assertThat(offer)
        .isInstanceOfSatisfying(
            HostedCompletionOffer.NotOffered.class,
            notOffered ->
                assertThat(notOffered.whatThisProviderHasInstead())
                    .startsWith("this payment page has already reached an outcome"));
    assertThat(outcome)
        .isInstanceOfSatisfying(
            HostedCompletionOutcome.RefusedByTheProvider.class,
            refused ->
                assertThat(refused.inItsOwnWords())
                    .startsWith("this payment page has already reached an outcome"));
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void a_paid_page_is_described_by_the_authorization_it_created() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    ProviderObjectState state = provider.state(RUN, FIRST_SESSION).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo("0/00");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(state.relatedObjects())
        .containsOnly(
            Map.entry("merchantId", MERCHANT),
            Map.entry("terminal", TERMINAL_ID),
            Map.entry("order", ORDER),
            Map.entry("intRef", "0000000000000001"));
  }

  @Test
  void one_answer_for_many_identifiers_describes_each_page_by_the_single_read_names() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));
    List<String> asked =
        List.of(FIRST_SESSION, "0000000000000001", "nothing-of-this-run", FIRST_SESSION);

    List<ProviderObjectState> batched = provider.states(RUN, asked);

    assertThat(batched)
        .as(
            "a screen labels every identifier an object names from this answer, and one answer"
                + " carrying the payments a whole list of payer pages sent must place each"
                + " payment against the page that sent it. %s",
            TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactlyElementsOf(
            TheSequenceOneAnswerIsIn.askingOneAtATimeInTheOrderAsked(provider, RUN, asked));
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(batched, asked))
        .as(TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(provider, RUN, asked));
    assertThat(batched)
        .extracting(ProviderObjectState::providerObjectId)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(FIRST_SESSION, "0000000000000001");
  }

  @Test
  void a_listing_describes_a_paid_page_by_the_authorization_the_single_read_names() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(page)
        .isInstanceOfSatisfying(
            ProviderObjectPage.Listed.class,
            listed ->
                assertThat(listed.objects())
                    .as(
                        "one answer carrying the payments a whole page of payer pages sent must"
                            + " place each payment against the page that sent it, or a listing"
                            + " describes one page by another page's authorization")
                    .contains(provider.state(RUN, FIRST_SESSION).orElseThrow()));
  }

  @Test
  void an_open_page_is_described_as_awaiting_its_payer_since_it_opened() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    ProviderObjectState state = provider.state(RUN, FIRST_SESSION).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo("awaiting the payer");
    assertThat(state.updatedAt()).isEqualTo(NOW);
    assertThat(state.relatedObjects())
        .containsOnly(
            Map.entry("merchantId", MERCHANT),
            Map.entry("terminal", TERMINAL_ID),
            Map.entry("order", ORDER));
  }

  @Test
  void a_page_that_ended_without_a_payment_is_described_by_the_outcome_it_reached_when_it_did() {
    Instant opened = Instant.parse("2026-09-29T10:00:00Z");
    Instant ended = Instant.parse("2026-09-29T10:05:00Z");
    Map<String, List<String>> form = new LinkedHashMap<>();
    form.put(AzericardFields.ORDER, List.of(ORDER));
    form.put(AzericardFields.AMOUNT, List.of("0.01"));
    form.put(AzericardFields.CURRENCY, List.of("AZN"));
    form.put(AzericardFields.TRTYPE, List.of("1"));
    AzericardPayerSession open =
        new AzericardPayerSession(
            FIRST_SESSION,
            RUN,
            AzericardRoute.TOKEN_CGI_LINK,
            TERMINAL_ID,
            MERCHANT,
            form,
            Map.of(),
            null,
            null,
            ObjectOrigin.PROVIDER_API,
            opened,
            null);

    ProviderObjectState awaiting = AzericardPayerSessionObject.stateOf(open, null);
    ProviderObjectState abandoned =
        AzericardPayerSessionObject.stateOf(
            open.settledAs(PayerOutcome.ABANDONED, null, ended), null);

    assertThat(awaiting.updatedAt()).isEqualTo(opened);
    assertThat(awaiting.normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
    assertThat(abandoned.updatedAt()).isEqualTo(ended);
    assertThat(abandoned.nativeStatus()).isEqualTo("ABANDONED");
    assertThat(abandoned.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
  }

  @Test
  void a_listing_resumes_after_a_page_named_as_its_cursor() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    Map<String, String> another = cardSave();
    another.put(AzericardFields.ORDER, "20260929120002");
    gateway.tokenLink(tokenCall(signed(another)));

    ProviderObjectPage page =
        provider.objects(new ProviderObjectQuery(RUN, "00000000000000000000000000000002", 10));

    assertThat(page)
        .isInstanceOfSatisfying(
            ProviderObjectPage.Listed.class,
            listed ->
                assertThat(listed.objects())
                    .extracting(ProviderObjectState::providerObjectId)
                    .containsExactly(FIRST_SESSION));
  }

  @Test
  void the_page_a_browser_opens_is_the_object_this_declaration_promises_a_payer_finishes() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    assertThat(provider.hostedPages())
        .isEqualTo(
            new HostedPageContract.ObjectsAPayerFinishes(
                List.of(
                    new HostedPageContract.AnObjectItKeepsWaiting(
                        AzericardProvider.A_PAYMENT_PAGE, true))));
    assertThat(TheObjectsADeclarationPromises.of(provider, RUN, List.of(FIRST_SESSION)))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }

  @Test
  void a_reversal_asked_of_a_page_is_refused_because_a_page_moves_no_money() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    ObjectActionOutcome outcome =
        provider.act(
            new ObjectActionRequest(
                RUN, FIRST_SESSION, ObjectAction.CANCEL, null, ObjectOrigin.CONTROL_API));

    assertThat(outcome)
        .isInstanceOfSatisfying(
            ObjectActionOutcome.RefusedByTheProvider.class,
            refused ->
                assertThat(refused.inItsOwnWords())
                    .startsWith("provider azericard moves no money on a payment page."));
  }

  @Test
  void a_page_opens_under_the_first_session_id_no_other_page_holds() {
    for (int held = 1; held <= 4; held++) {
      sessions.open(aPageHolding("0".repeat(31) + held));
    }

    gateway.tokenLink(tokenCall(signed(cardSave())));

    assertThat(sessions.all())
        .hasSize(5)
        .last()
        .satisfies(
            opened -> assertThat(opened.sessionId()).isEqualTo("00000000000000000000000000000005"));
  }

  @Test
  void gives_up_opening_a_page_rather_than_taking_a_session_id_another_page_holds() {
    for (int held = 1; held <= 5; held++) {
      sessions.open(aPageHolding("0".repeat(31) + held));
    }
    AzericardApiCall handOff = tokenCall(signed(cardSave()));

    assertThatThrownBy(() -> gateway.tokenLink(handOff))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("no Azericard payer session id could be claimed in 5 attempts");
    assertThat(sessions.all()).hasSize(5);
  }

  private static AzericardPayerSession aPageHolding(String sessionId) {
    return new AzericardPayerSession(
        sessionId,
        RUN,
        AzericardRoute.TOKEN_CGI_LINK,
        TERMINAL_ID,
        MERCHANT,
        Map.of(AzericardFields.ORDER, List.of("20260929000000")),
        Map.of(),
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        null);
  }

  @Test
  void
      two_submissions_naming_the_same_five_fields_in_two_orders_are_each_recorded_in_the_order_its_own_submission_named_them() {
    gateway.tokenLink(tokenCall(signed(cardSave())));
    String session = theSession();
    Map<String, List<String>> asThePageRenders = new LinkedHashMap<>();
    asThePageRenders.put(AzericardPayerPage.SESSION, List.of(session));
    asThePageRenders.put(AzericardPayerPage.CARD_NUMBER, List.of(A_PUBLISHED_PAN));
    asThePageRenders.put(AzericardPayerPage.EXPIRY, List.of(AN_EXPIRY));
    asThePageRenders.put(AzericardPayerPage.CVC, List.of(ITS_CVC));
    asThePageRenders.put(AzericardPayerPage.ACTION, List.of(AzericardPayerPage.PAY));
    Map<String, List<String>> reversed = new LinkedHashMap<>();
    reversed.put(AzericardPayerPage.ACTION, List.of(AzericardPayerPage.PAY));
    reversed.put(AzericardPayerPage.CVC, List.of(ITS_CVC));
    reversed.put(AzericardPayerPage.EXPIRY, List.of(AN_EXPIRY));
    reversed.put(AzericardPayerPage.CARD_NUMBER, List.of(A_PUBLISHED_PAN));
    reversed.put(AzericardPayerPage.SESSION, List.of(session));

    checkout.submit(aSubmissionOf(asThePageRenders));
    checkout.submit(aSubmissionOf(reversed));

    assertThat(pageRequests())
        .as(
            "a copy carrying no order of its own derives one order from these five names and gives"
                + " both rows that same order, so no run of this simulator can answer two"
                + " different orders with both of them")
        .extracting(row -> List.copyOf(row.sanitizedBody().keySet()))
        .containsExactly(List.copyOf(asThePageRenders.keySet()), List.copyOf(reversed.keySet()));
  }

  @Test
  void a_payer_page_submission_hands_its_fields_over_as_a_sequenced_map_no_caller_can_write_to() {
    AzericardApiCall submitted =
        aSubmissionOf(Map.of(AzericardPayerPage.ACTION, List.of(AzericardPayerPage.CANCEL)));

    assertThat(submitted.form()).isInstanceOf(SequencedMap.class);
    assertThatThrownBy(() -> submitted.form().put("extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static AzericardApiCall aSubmissionOf(Map<String, List<String>> form) {
    return new AzericardApiCall("POST", AzericardPayerPage.PATH, Map.of(), form);
  }

  private List<AuditRecord> pageRequests() {
    return audit.all().stream()
        .filter(row -> AzericardPayerPage.PATH.equals(row.path()))
        .filter(row -> row.direction() == AuditDirection.INBOUND_REQUEST)
        .toList();
  }

  private List<AuditRecord> pageAnswers() {
    return audit.all().stream()
        .filter(row -> AzericardPayerPage.PATH.equals(row.path()))
        .filter(row -> row.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .toList();
  }

  private Map<String, String> merchantInitiated(String token, String extNetRef) {
    Map<String, String> form = cardSave();
    form.remove(AzericardFields.TOKEN_ACTION);
    form.put(AzericardFields.ORDER, "20260929120009");
    form.put(AzericardFields.AMOUNT, "5.00");
    form.put(AzericardFields.MERCH_TRAN_STATE, AzericardTokenFlow.MERCHANT_INITIATED);
    form.put(AzericardFields.TOKEN, token);
    form.put(AzericardFields.EXT_NET_REF, extNetRef);
    form.put(AzericardFields.MERCH_RN_ID, "FEDCBA9876543210");
    return form;
  }

  private String theSession() {
    return sessions.all().get(0).sessionId();
  }

  private AzericardApiResponse submit(Map<String, String> fields) {
    return submit(theSession(), fields);
  }

  private AzericardApiResponse submit(String session, Map<String, String> fields) {
    Map<String, List<String>> form = new LinkedHashMap<>();
    form.put(AzericardPayerPage.SESSION, List.of(session));
    fields.forEach((name, value) -> form.put(name, List.of(value)));
    return checkout.submit(new AzericardApiCall("POST", AzericardPayerPage.PATH, Map.of(), form));
  }

  private static Map<String, String> pay(String pan, String expiry, String cvc) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(AzericardPayerPage.CARD_NUMBER, pan);
    fields.put(AzericardPayerPage.EXPIRY, expiry);
    fields.put(AzericardPayerPage.CVC, cvc);
    fields.put(AzericardPayerPage.ACTION, AzericardPayerPage.PAY);
    return fields;
  }

  private static Map<String, String> cancel() {
    return Map.of(AzericardPayerPage.ACTION, AzericardPayerPage.CANCEL);
  }

  private Map<String, String> theOnlyCallback() {
    dispatcher.deliverDue(AzericardGateway.AZERICARD, RUN);
    assertThat(postedCallbacks).hasSize(1);
    Map<String, String> fields = new LinkedHashMap<>();
    for (String pair : postedCallbacks.get(0).split("&", -1)) {
      int separator = pair.indexOf('=');
      fields.put(
          URLDecoder.decode(pair.substring(0, separator), UTF_8),
          URLDecoder.decode(pair.substring(separator + 1), UTF_8));
    }
    return fields;
  }

  private AzericardPayment onlyPayment() {
    assertThat(payments.all()).hasSize(1);
    return payments.all().get(0);
  }

  private static Map<String, String> cardSave() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "1");
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, "0.01");
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.DESC, "Card registration");
    form.put(AzericardFields.MERCH_NAME, "The Shop");
    form.put(AzericardFields.MERCH_URL, MERCH_URL);
    form.put(AzericardFields.BACKREF, BACKREF);
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(NOW));
    form.put(AzericardFields.NONCE, NONCE);
    form.put(AzericardFields.TOKEN_ACTION, AzericardTokenFlow.REGISTER);
    form.put(AzericardFields.MERCH_TRAN_STATE, AzericardTokenFlow.STORING_A_CARD);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    Map<String, List<String>> fields = new LinkedHashMap<>();
    signed.forEach((name, value) -> fields.put(name, List.of(value)));
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.TOKEN_CGI_LINK, fields).macSource()));
    return signed;
  }

  private static AzericardApiCall tokenCall(Map<String, String> form) {
    return call(TOKEN_ROUTE, form);
  }

  private static AzericardApiCall cgiCall(Map<String, String> form) {
    return call(CGI_ROUTE, form);
  }

  private static AzericardApiCall call(String route, Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return new AzericardApiCall("POST", route, Map.of(), fields);
  }

  @Test
  void the_defences_this_page_owes_a_browser_are_the_ones_every_answer_of_it_carries() {
    MockHttpServletResponse answered = new MockHttpServletResponse();

    AzericardPayerPage.defencesOnto(answered);

    assertThat(answered.getHeader(AzericardPayerPage.CACHE_CONTROL))
        .as(
            "the answer to a payer page that could not be built is written by the error dispatch,"
                + " which knows nothing of this page, so a page whose defences lived only in the"
                + " answer value lost every one of them on a 500: a browser could cache it and"
                + " frame it")
        .isEqualTo(AzericardPayerPage.NOTHING_IS_STORED);
    assertThat(answered.getHeader(AzericardPayerPage.CONTENT_TYPE_OPTIONS))
        .isEqualTo(AzericardPayerPage.NOTHING_IS_SNIFFED);
    assertThat(answered.getHeader(AzericardPayerPage.REFERRER_POLICY))
        .isEqualTo(AzericardPayerPage.NOTHING_IS_REFERRED);
    assertThat(answered.getHeader(AzericardPayerPage.CONTENT_SECURITY_POLICY))
        .isEqualTo(AzericardPayerPage.WHAT_THIS_PAGE_PERMITS);
    assertThat(AzericardPayerPage.WHAT_THIS_PAGE_PERMITS).contains("frame-ancestors 'none'");
  }

  @Test
  void stamping_the_defences_twice_leaves_one_of_each_rather_than_two() {
    MockHttpServletResponse answered = new MockHttpServletResponse();

    AzericardPayerPage.defencesOnto(answered);
    AzericardPayerPage.defencesOnto(answered);

    assertThat(answered.getHeaders(AzericardPayerPage.CACHE_CONTROL))
        .as("a duplicated header is a difference on the wire a real gateway does not have")
        .containsExactly(AzericardPayerPage.NOTHING_IS_STORED);
  }

  @Test
  void the_headers_an_answer_carries_are_the_same_four_the_defences_stamp() {
    assertThat(AzericardPayerPage.HEADERS)
        .containsEntry(
            AzericardPayerPage.CACHE_CONTROL, List.of(AzericardPayerPage.NOTHING_IS_STORED))
        .containsEntry(
            AzericardPayerPage.CONTENT_TYPE_OPTIONS, List.of(AzericardPayerPage.NOTHING_IS_SNIFFED))
        .containsEntry(
            AzericardPayerPage.REFERRER_POLICY, List.of(AzericardPayerPage.NOTHING_IS_REFERRED))
        .containsEntry(
            AzericardPayerPage.CONTENT_SECURITY_POLICY,
            List.of(AzericardPayerPage.WHAT_THIS_PAGE_PERMITS));
  }

  private static String bodyOf(AzericardApiResponse response) {
    return new String(response.body(), UTF_8);
  }

  @Test
  void the_row_this_page_answers_with_carries_the_outcome_it_rendered_and_nothing_else() {
    gateway.tokenLink(tokenCall(signed(cardSave())));

    submit(pay(A_PUBLISHED_PAN, "05/31", ITS_CVC));

    assertThat(AzericardFields.SENSITIVE_RESPONSE_FIELDS)
        .as(
            "azericard declares no response field, so its declaration is applied to this body and"
                + " can match nothing in it")
        .isEmpty();
    assertThat(pageAnswers())
        .singleElement()
        .satisfies(
            row ->
                assertThat(row.sanitizedBody())
                    .containsExactly(entry("result", List.of("Payment successful"))));
  }
}
