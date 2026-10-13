package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class WorldlineProviderReadsTest {

  private static final TestRunId RUN = TestRunId.of("run-worldline");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private static final String ACCOUNT = WorldlineWire.ACCOUNT;

  private static final String MERCHANT = WorldlineWire.MERCHANT;

  private final WorldlineDoubles.Merchants merchants = new WorldlineDoubles.Merchants();
  private final WorldlineDoubles.Tokenizations sessions = new WorldlineDoubles.Tokenizations();
  private final WorldlineDoubles.Payments payments = new WorldlineDoubles.Payments();
  private final WorldlineDoubles.Keys keys = new WorldlineDoubles.Keys();
  private final WorldlineDoubles.Audit audit = new WorldlineDoubles.Audit();
  private final WorldlineDoubles.HeldResponses held = new WorldlineDoubles.HeldResponses();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final IdGenerator ids = new WorldlineDoubles.CountingIds();

  private final SimulatorProvider provider = provider();

  private SimulatorProvider provider() {
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
        held,
        WorldlineDoubles.clockAt(WHEN),
        new CreateHostedTokenization(
            sessions, answers, ids, scenarios, WorldlineWire.PUBLIC_BASE_URL),
        new CreatePayment(payments, sessions, cards, keys, answers, ids, scenarios),
        new CapturePayment(payments, answers, keys, scenarios),
        new CancelPayment(payments, answers, keys, scenarios),
        new TokenizeOnTheHostedPage(sessions, cards, ids),
        ids);
  }

  private void configure() {
    provider
        .parse(
            new ProviderConfiguration(
                RUN,
                List.of(
                    new ConfiguredAccount(
                        ACCOUNT,
                        Map.of(
                            WorldlineMerchant.MERCHANT_ID_PROPERTY,
                            MERCHANT,
                            WorldlineMerchant.API_KEY_PROPERTY,
                            "key-" + ACCOUNT,
                            WorldlineMerchant.API_SECRET_PROPERTY,
                            "secret"))),
                List.of(),
                List.of(),
                Map.of()))
        .store();
  }

  private String aTokenization() {
    return ((OriginationOutcome.Originated)
            provider.originate(
                OriginationRequest.of(
                    RUN, ACCOUNT, WorldlineOrigination.A_TOKENIZATION, Map.of(), ObjectOrigin.UI)))
        .providerObjectId();
  }

  private String aTokenOn(String hostedTokenizationId) {
    provider.complete(
        HostedCompletionRequest.of(
            provider.hostedCompletion(RUN, hostedTokenizationId),
            RUN,
            hostedTokenizationId,
            PayerOutcome.PAID,
            WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS,
            ObjectOrigin.CONTROL_API));
    return String.valueOf(sessions.findById(RUN, hostedTokenizationId).orElseThrow().token());
  }

  private String aPaymentNaming(Map<String, String> extra) {
    Map<String, String> values = new LinkedHashMap<>(extra);
    values.put(WorldlineFields.AMOUNT, "5000");
    values.put(WorldlineFields.CURRENCY_CODE, "PLN");
    values.put(
        WorldlineFields.AUTHORIZATION_MODE, WorldlineAuthorizationMode.PRE_AUTHORIZATION.name());
    return ((OriginationOutcome.Originated)
            provider.originate(
                OriginationRequest.of(
                    RUN, ACCOUNT, WorldlineOrigination.A_PAYMENT, values, ObjectOrigin.UI)))
        .providerObjectId();
  }

  @Test
  void a_payment_that_names_every_optional_value_carries_each_of_them_as_a_related_object() {
    configure();
    String token = aTokenOn(aTokenization());
    String paymentId =
        aPaymentNaming(
            Map.of(
                WorldlineFields.TOKEN, token,
                WorldlineFields.MERCHANT_REFERENCE, "order-123",
                WorldlineFields.MERCHANT_CUSTOMER_ID, "customer-1"));

    Map<String, String> related = provider.state(RUN, paymentId).orElseThrow().relatedObjects();

    assertThat(related)
        .containsEntry(WorldlineMerchant.MERCHANT_ID_PROPERTY, MERCHANT)
        .containsEntry(
            WorldlineFields.AUTHORIZATION_MODE, WorldlineAuthorizationMode.PRE_AUTHORIZATION.name())
        .containsEntry(WorldlineFields.MERCHANT_REFERENCE, "order-123")
        .containsEntry(WorldlineFields.MERCHANT_CUSTOMER_ID, "customer-1")
        .containsEntry(WorldlineFields.TOKEN, token)
        .containsEntry(WorldlineFields.CARD_NUMBER, "************1111")
        .containsEntry(WorldlineFields.EXPIRY_DATE, "1230")
        .containsKey(WorldlineFields.STATUS_CATEGORY + "." + WorldlineFields.STATUS_CODE);
  }

  @Test
  void a_payment_charged_against_a_session_names_that_session_as_a_related_object() {
    configure();
    String sessionId = aTokenization();
    aTokenOn(sessionId);
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.HOSTED_TOKENIZATION_ID, sessionId));

    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .containsEntry(WorldlineFields.HOSTED_TOKENIZATION_ID, sessionId);
  }

  @Test
  void a_payment_that_names_no_optional_value_carries_none_of_them_as_a_related_object() {
    configure();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));

    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .doesNotContainKeys(
            WorldlineFields.MERCHANT_REFERENCE, WorldlineFields.MERCHANT_CUSTOMER_ID);
  }

  @Test
  void a_payment_charged_against_a_seeded_token_names_no_hosted_tokenization_session_at_all() {
    configure();
    aSeededFixture();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, "token-seeded"));

    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .doesNotContainKey(WorldlineFields.HOSTED_TOKENIZATION_ID)
        .containsEntry(WorldlineFields.CARD_NUMBER, "************4444");
  }

  @Test
  void a_payment_charged_against_a_token_a_session_minted_names_that_session() {
    configure();
    String sessionId = aTokenization();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(sessionId)));

    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .containsEntry(WorldlineFields.HOSTED_TOKENIZATION_ID, sessionId);
  }

  @Test
  void a_session_no_payer_has_finished_carries_no_token_and_no_card_as_a_related_object() {
    configure();

    Map<String, String> related =
        provider.state(RUN, aTokenization()).orElseThrow().relatedObjects();

    assertThat(related)
        .containsKeys(WorldlineMerchant.MERCHANT_ID_PROPERTY, WorldlineFields.EXPIRES_AT)
        .doesNotContainKeys(
            WorldlineFields.TOKEN, WorldlineFields.CARD_NUMBER, WorldlineFields.EXPIRY_DATE);
  }

  @Test
  void a_session_names_neither_address_of_its_payer_page_because_a_screen_prints_these_verbatim() {
    configure();

    Map<String, String> related =
        provider.state(RUN, aTokenization()).orElseThrow().relatedObjects();

    assertThat(related)
        .as(
            "the object screen and the timeline print every entry of relatedObjects verbatim and"
                + " redact nothing, so either address here is a working link to the payer page"
                + " this simulator serves, and ThePayerStep tells every tester that link is never"
                + " printed on a screen. The hostedTokenizationId both addresses are built from is"
                + " the session's own identity, so nothing a tester correlates by is lost")
        .doesNotContainKeys(
            WorldlineFields.HOSTED_TOKENIZATION_URL, WorldlineFields.PARTIAL_REDIRECT_URL);
    assertThat(related.values())
        .as(
            "a provider that stopped naming the address under its published name and kept handing"
                + " it over under another would pass the check above and print the same link")
        .noneMatch(value -> value.contains(CreateHostedTokenization.THE_PUBLISHED_FORM_PATH));
  }

  @Test
  void a_session_a_payer_finished_carries_the_token_and_the_card_it_stored() {
    configure();
    String sessionId = aTokenization();
    String token = aTokenOn(sessionId);

    assertThat(provider.state(RUN, sessionId).orElseThrow().relatedObjects())
        .containsEntry(WorldlineFields.TOKEN, token)
        .containsEntry(WorldlineFields.CARD_NUMBER, "************1111")
        .containsEntry(WorldlineFields.EXPIRY_DATE, "1230");
  }

  @Test
  void resetting_one_run_counts_every_store_this_module_holds_and_never_one_of_them_twice() {
    configure();
    aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    aSeededFixture();
    aRule();
    keys.stillRunningUnder(RUN, ACCOUNT, "a-key", WHEN);

    assertThat(provider.reset(RUN, ResetScope.TEST_RUN).clearedObjects()).isEqualTo(5);
  }

  @Test
  void resetting_everything_counts_every_store_this_module_holds_and_never_one_of_them_twice() {
    configure();
    aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    aSeededFixture();
    aRule();
    keys.stillRunningUnder(RUN, ACCOUNT, "a-key", WHEN);

    assertThat(provider.reset(RUN, ResetScope.ALL).clearedObjects()).isEqualTo(5);
  }

  @Test
  void the_provider_hands_the_screens_the_fields_and_mechanisms_its_own_classes_declare() {
    assertThat(provider.accountFields()).isSameAs(WorldlineMerchant.ACCOUNT_FIELDS);
    assertThat(provider.paymentMethodFields())
        .isSameAs(WorldlineFixtureCards.PAYMENT_METHOD_FIELDS);
    assertThat(provider.keyMechanisms()).isSameAs(WorldlineKeyMechanisms.MECHANISMS);
    assertThat(provider.vocabulary()).isSameAs(WorldlineOperations.VOCABULARY);
    assertThat(provider.originationFields()).isSameAs(WorldlineOrigination.DECLARED);
    assertThat(provider.objectActions()).isSameAs(WorldlineObjectActions.DECLARED);
  }

  @Test
  void forgetting_a_fixture_of_this_provider_removes_nothing_of_its_own_because_it_stores_none() {
    configure();
    PaymentMethodFixture seeded = aSeededFixture();

    assertThat(
            fixtures.remove(
                WorldlineApi.WORLDLINE, RUN, seeded.fixtureId(), provider.forgetFixture(seeded)))
        .isTrue();
  }

  @Test
  void a_payer_who_finished_a_page_is_reported_as_the_completion_of_that_page_and_no_other() {
    configure();
    String sessionId = aTokenization();

    HostedCompletionOutcome completed =
        provider.complete(
            HostedCompletionRequest.of(
                provider.hostedCompletion(RUN, sessionId),
                RUN,
                sessionId,
                PayerOutcome.PAID,
                WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS,
                ObjectOrigin.CONTROL_API));

    assertThat(completed)
        .asInstanceOf(InstanceOfAssertFactories.type(HostedCompletionOutcome.Completed.class))
        .satisfies(
            it -> {
              assertThat(it.providerObjectId()).isEqualTo(sessionId);
              assertThat(it.createdObjectId()).isNull();
              assertThat(it.outcome()).isEqualTo(PayerOutcome.PAID);
              assertThat(it.webhookJobIds()).isEmpty();
            });
  }

  @Test
  void a_payer_who_left_a_page_is_reported_as_having_left_it_and_not_as_having_paid() {
    configure();
    String sessionId = aTokenization();

    HostedCompletionOutcome completed =
        provider.complete(
            HostedCompletionRequest.of(
                provider.hostedCompletion(RUN, sessionId),
                RUN,
                sessionId,
                PayerOutcome.ABANDONED,
                null,
                ObjectOrigin.CONTROL_API));

    assertThat(completed)
        .asInstanceOf(InstanceOfAssertFactories.type(HostedCompletionOutcome.Completed.class))
        .extracting(HostedCompletionOutcome.Completed::outcome)
        .isEqualTo(PayerOutcome.ABANDONED);
    assertThat(sessions.findById(RUN, sessionId).orElseThrow().nativeStatus())
        .isEqualTo(WorldlineStatus.EXPIRED);
  }

  @Test
  void a_payer_who_left_a_page_another_request_had_already_moved_is_told_what_it_holds_now() {
    configure();
    String sessionId = aTokenization();
    sessions.movedByAnotherRequestFirst(sessionId);

    assertThat(
            provider.complete(
                HostedCompletionRequest.of(
                    provider.hostedCompletion(RUN, sessionId),
                    RUN,
                    sessionId,
                    PayerOutcome.ABANDONED,
                    null,
                    ObjectOrigin.CONTROL_API)))
        .isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
  }

  @Test
  void an_authorization_a_capture_emptied_is_no_longer_open_even_before_it_is_closed() {
    configure();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "5000", ObjectOrigin.UI));

    ProviderObjectState state = provider.state(RUN, paymentId).orElseThrow();

    assertThat(state.authorizationOpen()).isFalse();
    assertThat(state.capturesAccepted()).isFalse();
    assertThat(payments.findById(RUN, paymentId).orElseThrow().authorizationOpen()).isFalse();
  }

  @Test
  void a_captured_payment_reports_what_it_took_and_says_no_refund_of_this_slice_returns_it() {
    configure();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "5000", ObjectOrigin.UI));

    ProviderObjectState state = provider.state(RUN, paymentId).orElseThrow();

    assertThat(state.refundsAccepted())
        .as(WorldlineObjectActions.THE_REFUND_ROUTE_IS_OUT_OF_THIS_SLICE)
        .isFalse();
    assertThat(state.derivedBalances().orElseThrow().refundableRemainder().value())
        .isEqualTo("5000");
    assertThat(state.ledger().count(OperationKind.REFUND)).isZero();
  }

  @Test
  void an_authorization_a_partial_capture_left_money_on_is_still_open() {
    configure();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "2000", ObjectOrigin.UI));

    assertThat(provider.state(RUN, paymentId).orElseThrow().authorizationOpen()).isTrue();
    assertThat(provider.state(RUN, paymentId).orElseThrow().capturesAccepted()).isTrue();
  }

  @Test
  void a_partial_capture_leaves_the_payment_at_the_status_the_vendor_publishes_nothing_for() {
    configure();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "2000", ObjectOrigin.UI));

    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();

    assertThat(payment.nativeStatus()).isEqualTo(WorldlineStatus.PENDING_CAPTURE);
    assertThat(payment.statusCategory()).isEqualTo(WorldlineStatus.CATEGORY_PENDING_MERCHANT);
    assertThat(payment.statusCode()).isEqualTo(WorldlineStatus.CODE_PENDING_CAPTURE);
  }

  @Test
  void a_capture_that_closes_a_payment_leaves_it_at_the_landing_the_account_is_configured_for() {
    configure();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "5000", ObjectOrigin.UI));

    WorldlinePayment payment = payments.findById(RUN, paymentId).orElseThrow();

    assertThat(payment.nativeStatus()).isEqualTo(WorldlineCaptureLanding.CAPTURED_9.nativeStatus());
    assertThat(payment.statusCategory())
        .isEqualTo(WorldlineCaptureLanding.CAPTURED_9.statusCategory());
    assertThat(payment.statusCode()).isEqualTo(WorldlineCaptureLanding.CAPTURED_9.statusCode());
  }

  @Test
  void the_identifier_a_second_maintenance_operation_mints_counts_on_from_the_first() {
    configure();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "2000", ObjectOrigin.UI));
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "3000", ObjectOrigin.UI));

    assertThat(provider.state(RUN, paymentId).orElseThrow().identity().members())
        .containsExactly(
            paymentId,
            WorldlineIdentifiers.paymentIdOf(WorldlineIdentifiers.thePayIdIn(paymentId), 1),
            WorldlineIdentifiers.paymentIdOf(WorldlineIdentifiers.thePayIdIn(paymentId), 2));
  }

  @Test
  void one_answer_for_many_identifiers_holds_one_state_for_each_object_the_run_holds() {
    configure();
    String paymentId = aPaymentNaming(Map.of(WorldlineFields.TOKEN, aTokenOn(aTokenization())));
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "2000", ObjectOrigin.UI));
    String theIdentifierTheCaptureMinted =
        WorldlineIdentifiers.paymentIdOf(WorldlineIdentifiers.thePayIdIn(paymentId), 1);
    String sessionId = aTokenization();
    List<String> asked =
        List.of(
            sessionId,
            MERCHANT,
            theIdentifierTheCaptureMinted,
            paymentId,
            "nothing-of-this-run",
            paymentId);

    List<ProviderObjectState> batched = provider.states(RUN, asked);

    assertThat(batched)
        .as(
            "a screen labels every identifier an object names from this answer, so one answer for"
                + " many identifiers that held anything other than what asking one at a time"
                + " holds would change what a tester reads beside those names")
        .containsExactlyElementsOf(
            TheSequenceOneAnswerIsIn.askingOneAtATimeInTheOrderAsked(provider, RUN, asked));
    assertThat(batched)
        .extracting(ProviderObjectState::providerObjectId)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(sessionId, paymentId);
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(batched, asked))
        .as(TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(provider, RUN, asked));
    assertThat(theIdentifiersAnsweredIn(batched, asked))
        .as(
            "a payment answers to the identifier each maintenance call minted on it, and the"
                + " screen matches an asked identifier against the identity of the states this"
                + " answer carries, so every identifier the run holds an object under is labelled")
        .containsExactly(sessionId, theIdentifierTheCaptureMinted, paymentId, paymentId);
  }

  private static List<String> theIdentifiersAnsweredIn(
      List<ProviderObjectState> held, List<String> asked) {
    List<String> answered = new ArrayList<>();
    for (String id : asked) {
      held.stream()
          .filter(state -> state.identity().answersTo(id))
          .findFirst()
          .ifPresent(state -> answered.add(id));
    }
    return List.copyOf(answered);
  }

  private PaymentMethodFixture aSeededFixture() {
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
    return fixtures.of(WorldlineApi.WORLDLINE, RUN).get(0);
  }

  private void aRule() {
    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CREATE_PAYMENT),
            new ScenarioAction.FixedSuccess(),
            null));
  }
}
