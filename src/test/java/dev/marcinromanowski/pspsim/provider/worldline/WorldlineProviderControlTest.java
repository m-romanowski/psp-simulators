package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.smoke.Applicability;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionControl;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheObjectsADeclarationPromises;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.ui.APresetReadsBackInWords;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorldlineProviderControlTest {

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

  private void configure(Map<String, String> declared) {
    Map<String, String> configuration = new LinkedHashMap<>(declared);
    configuration.putIfAbsent(WorldlineMerchant.MERCHANT_ID_PROPERTY, MERCHANT);
    configuration.putIfAbsent(WorldlineMerchant.API_KEY_PROPERTY, "key-" + ACCOUNT);
    configuration.putIfAbsent(WorldlineMerchant.API_SECRET_PROPERTY, "secret-" + ACCOUNT);
    provider
        .parse(
            new ProviderConfiguration(
                RUN,
                List.of(new ConfiguredAccount(ACCOUNT, configuration)),
                List.of(),
                List.of(),
                Map.of()))
        .store();
  }

  private String aTokenization() {
    OriginationOutcome started =
        provider.originate(
            OriginationRequest.of(
                RUN, ACCOUNT, WorldlineOrigination.A_TOKENIZATION, Map.of(), ObjectOrigin.UI));
    return ((OriginationOutcome.Originated) started).providerObjectId();
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

  private String aHold() {
    String token = aTokenOn(aTokenization());
    OriginationOutcome started =
        provider.originate(
            OriginationRequest.of(
                RUN,
                ACCOUNT,
                WorldlineOrigination.A_PAYMENT,
                Map.of(
                    WorldlineFields.AMOUNT,
                    "5000",
                    WorldlineFields.CURRENCY_CODE,
                    "PLN",
                    WorldlineFields.AUTHORIZATION_MODE,
                    WorldlineAuthorizationMode.PRE_AUTHORIZATION.name(),
                    WorldlineFields.TOKEN,
                    token),
                ObjectOrigin.UI));
    return ((OriginationOutcome.Originated) started).providerObjectId();
  }

  @Test
  void the_wire_namespaces_and_the_answer_off_them_are_the_ones_this_module_declares() {
    assertThat(provider.wireRoutes()).isEqualTo(WorldlineWireFaults.ROUTES);
    assertThat(provider.wireRoutes().covers(WorldlineApiController.thePaymentsOf(MERCHANT)))
        .as("a namespace this module forgot to claim leaves a client on the wrong answer")
        .isTrue();
    assertThat(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    provider.theAnswerOffItsRoutes(
                        WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
                        WorldlineApiController.VERSION))
                .httpStatus())
        .isEqualTo(WorldlineAnswers.NOT_FOUND);
    assertThat(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    provider.theAnswerOffItsRoutes(
                        WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER,
                        WorldlineApiController.VERSION))
                .httpStatus())
        .isEqualTo(WorldlineWireFaults.METHOD_NOT_ALLOWED);
    assertThat(
            ((ProviderWireAnswer.NothingThisContractPublishes)
                    provider.theAnswerOffItsRoutes(
                        WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, WorldlineApiController.VERSION))
                .whatThisContractPublishesInstead())
        .isEqualTo(WorldlineWireFaults.WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
  }

  @Test
  void the_provider_answers_to_the_name_every_route_and_every_screen_addresses_it_by() {
    assertThat(provider.id().value()).isEqualTo("worldline");
  }

  @Test
  void an_account_is_required_to_carry_the_merchant_the_path_of_every_route_spells() {
    Map<String, String> withoutAMerchantId = mapWithout(WorldlineMerchant.MERCHANT_ID_PROPERTY);

    assertThatThrownBy(() -> parse(withoutAMerchantId))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(WorldlineMerchant.MERCHANT_ID_IS_IN_EVERY_PATH);
  }

  @Test
  void an_account_is_required_to_carry_the_api_key_the_authorization_header_names_it_by() {
    Map<String, String> withoutAnApiKey = mapWithout(WorldlineMerchant.API_KEY_PROPERTY);

    assertThatThrownBy(() -> parse(withoutAnApiKey))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(WorldlineMerchant.API_KEY_IS_THE_ACCOUNT);
  }

  @Test
  void an_account_is_required_to_carry_the_secret_the_signature_is_made_with() {
    Map<String, String> withoutAnApiSecret = mapWithout(WorldlineMerchant.API_SECRET_PROPERTY);

    assertThatThrownBy(() -> parse(withoutAnApiSecret))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(WorldlineMerchant.API_SECRET_IS_WHAT_THE_SIGNATURE_IS_MADE_WITH);
  }

  private void parse(Map<String, String> configuration) {
    provider
        .parse(
            new ProviderConfiguration(
                RUN,
                List.of(new ConfiguredAccount(ACCOUNT, configuration)),
                List.of(),
                List.of(),
                Map.of()))
        .store();
  }

  private static Map<String, String> mapWithout(String property) {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put(WorldlineMerchant.MERCHANT_ID_PROPERTY, MERCHANT);
    configuration.put(WorldlineMerchant.API_KEY_PROPERTY, "key");
    configuration.put(WorldlineMerchant.API_SECRET_PROPERTY, "secret");
    configuration.remove(property);
    return configuration;
  }

  @Test
  void a_merchant_id_carrying_a_slash_would_address_a_different_route_and_is_refused() {
    Map<String, String> carryingASlash = Map.of(WorldlineMerchant.MERCHANT_ID_PROPERTY, "one/two");

    assertThatThrownBy(() -> configure(carryingASlash))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(WorldlineMerchant.MERCHANT_ID_IS_ONE_PATH_SEGMENT);
  }

  @Test
  void the_three_characters_the_merchant_id_words_name_are_the_three_its_shape_refuses() {
    for (String named : List.of("one/two", "one?two", "one#two")) {
      assertThatThrownBy(() -> configure(Map.of(WorldlineMerchant.MERCHANT_ID_PROPERTY, named)))
          .as(
              "the words beside this pattern name a slash, a question mark and a hash, and %s"
                  + " carries one of them, so the sentence cannot drift into naming a character"
                  + " the pattern lets through",
              named)
          .isInstanceOf(ProviderConfigurationRejectedException.class)
          .hasMessageContaining("must match [^/?#]+")
          .hasMessageContaining("a slash, a question mark or a hash");
    }
    assertThatCode(
            () ->
                configure(
                    Map.of(
                        WorldlineMerchant.MERCHANT_ID_PROPERTY,
                        WorldlineMerchant.A_MERCHANT_ID_WORLDLINE_PUBLISHES)))
        .as(
            "the words name three characters and no more, and the id worldline publishes as an"
                + " example is one this simulator takes")
        .doesNotThrowAnyException();
  }

  @Test
  void a_merchant_id_wider_than_the_store_holds_is_refused_where_it_is_configured() {
    Map<String, String> widerThanTheStoreHolds =
        Map.of(
            WorldlineMerchant.MERCHANT_ID_PROPERTY,
            "m".repeat(WorldlineMerchant.LONGEST_MERCHANT_ID_THE_STORE_HOLDS + 1));

    assertThatThrownBy(() -> configure(widerThanTheStoreHolds))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(WorldlineMerchant.MERCHANT_ID_IS_KEPT_WITH_EVERY_OBJECT);
  }

  @Test
  void two_accounts_of_one_run_may_not_share_a_merchant_id() {
    ProviderConfiguration twoAccountsOnOneMerchantId =
        new ProviderConfiguration(
            RUN,
            List.of(
                new ConfiguredAccount(
                    "a",
                    Map.of(
                        WorldlineMerchant.MERCHANT_ID_PROPERTY, MERCHANT,
                        WorldlineMerchant.API_KEY_PROPERTY, "k1",
                        WorldlineMerchant.API_SECRET_PROPERTY, "s")),
                new ConfiguredAccount(
                    "b",
                    Map.of(
                        WorldlineMerchant.MERCHANT_ID_PROPERTY, MERCHANT,
                        WorldlineMerchant.API_KEY_PROPERTY, "k2",
                        WorldlineMerchant.API_SECRET_PROPERTY, "s"))),
            List.of(),
            List.of(),
            Map.of());

    assertThatThrownBy(() -> provider.parse(twoAccountsOnOneMerchantId))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(WorldlineMerchant.MERCHANT_ID_PROPERTY);
  }

  @Test
  void two_accounts_of_one_run_may_not_share_an_api_key() {
    ProviderConfiguration twoAccountsOnOneApiKey =
        new ProviderConfiguration(
            RUN,
            List.of(
                new ConfiguredAccount(
                    "a",
                    Map.of(
                        WorldlineMerchant.MERCHANT_ID_PROPERTY, "m1",
                        WorldlineMerchant.API_KEY_PROPERTY, "k",
                        WorldlineMerchant.API_SECRET_PROPERTY, "s")),
                new ConfiguredAccount(
                    "b",
                    Map.of(
                        WorldlineMerchant.MERCHANT_ID_PROPERTY, "m2",
                        WorldlineMerchant.API_KEY_PROPERTY, "k",
                        WorldlineMerchant.API_SECRET_PROPERTY, "s"))),
            List.of(),
            List.of(),
            Map.of());

    assertThatThrownBy(() -> provider.parse(twoAccountsOnOneApiKey))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(WorldlineMerchant.API_KEY_PROPERTY);
  }

  @Test
  void the_secret_is_the_one_account_field_the_disclosure_redacts() {
    configure(Map.of());
    assertThat(provider.disclosureOf(RUN, ACCOUNT))
        .get()
        .extracting(DisclosedAccount::disclosed)
        .satisfies(
            disclosed -> {
              assertThat(disclosed.get(WorldlineMerchant.API_SECRET_PROPERTY))
                  .containsExactly("[redacted]");
              assertThat(disclosed.get(WorldlineMerchant.API_KEY_PROPERTY))
                  .containsExactly("key-" + ACCOUNT);
            });
  }

  @Test
  void forgetting_an_account_removes_the_rows_that_run_held_for_it() {
    configure(Map.of());
    provider.forget(RUN, ACCOUNT).remove();
    assertThat(merchants.find(RUN, ACCOUNT)).isEmpty();
  }

  @Test
  void the_two_ways_of_starting_a_payment_are_the_two_routes_this_slice_serves() {
    assertThat(provider.originationFields().names())
        .containsExactly(WorldlineOrigination.A_TOKENIZATION, WorldlineOrigination.A_PAYMENT);
  }

  @Test
  void worldline_fills_the_merchant_and_the_authorization_in_itself_and_says_where_each_is_from() {
    assertThat(provider.originationFields().fieldsItFillsInItself())
        .containsOnlyKeys(WorldlineMerchant.MERCHANT_ID_PROPERTY, WorldlineFields.AUTHORIZATION)
        .hasEntrySatisfying(
            WorldlineMerchant.MERCHANT_ID_PROPERTY,
            why ->
                assertThat(why)
                    .contains("puts the merchant in the path of every route of this contract")
                    .contains("configured on the account this payment is started on"))
        .hasEntrySatisfying(
            WorldlineFields.AUTHORIZATION,
            why ->
                assertThat(why)
                    .contains("Authorization: GCS v1HMAC:<your-api-key>:<base64-encoded-signature>")
                    .contains("never reaches the wire, so there is nothing for it to be signed"));
  }

  @Test
  void starting_a_payment_on_an_account_this_run_does_not_configure_names_no_account() {
    assertThat(
            provider.originate(
                OriginationRequest.of(
                    RUN, "nobody", WorldlineOrigination.A_TOKENIZATION, Map.of(), ObjectOrigin.UI)))
        .isInstanceOf(OriginationOutcome.NoSuchAccount.class);
  }

  @Test
  void a_tokenization_started_from_a_screen_is_listed_as_an_object_of_the_run() {
    configure(Map.of());
    String id = aTokenization();
    assertThat(provider.state(RUN, id))
        .get()
        .extracting(ProviderObjectState::nativeStatus)
        .isEqualTo(WorldlineStatus.AWAITING_THE_PAYER);
  }

  @Test
  void a_tokenization_carries_no_amount_and_no_currency_because_the_vendor_answers_neither() {
    configure(Map.of());
    ProviderObjectState state = provider.state(RUN, aTokenization()).orElseThrow();
    assertThat(state.amount()).isNull();
    assertThat(state.currency()).isNull();
  }

  @Test
  void a_tokenization_is_an_object_a_payer_finishes_and_names_the_account_and_the_operation() {
    configure(Map.of());
    HostedSessionFacts.AnObjectAPayerFinishes session = theSessionFactsOfATokenization();

    assertThat(session.theAccountItBelongsTo()).isEqualTo(ACCOUNT);
    assertThat(session.theOperationItWasStartedFor())
        .isEqualTo(WorldlineOperations.CREATE_HOSTED_TOKENIZATION);
  }

  @Test
  void a_tokenization_is_created_when_the_run_stands_and_expires_three_hours_after_that() {
    configure(Map.of());
    HostedSessionFacts.AnObjectAPayerFinishes session = theSessionFactsOfATokenization();

    assertThat(session.createdAt()).isEqualTo(WHEN);
    assertThat(session.deadline())
        .isEqualTo(
            new HostedSessionFacts.Deadline.ItExpiresAt(
                WHEN.plus(WorldlineTokenization.THE_PUBLISHED_LIFE_SPAN)));
  }

  @Test
  void a_tokenization_names_no_party_and_carries_worldlines_own_words_for_that_absence() {
    configure(Map.of());
    HostedSessionFacts.AnObjectAPayerFinishes session = theSessionFactsOfATokenization();

    assertThat(session.aPartyIsNamed()).isFalse();
    assertThat(session.thePartyAsItReads())
        .isEqualTo(WorldlineOperations.NO_CUSTOMER_ON_A_TOKENIZATION);
  }

  @Test
  void a_payment_is_not_an_object_a_payer_finishes() {
    configure(Map.of());

    assertThat(provider.state(RUN, aHold()).orElseThrow().hostedSession())
        .isEqualTo(new HostedSessionFacts.NotAnObjectAPayerFinishes());
  }

  private HostedSessionFacts.AnObjectAPayerFinishes theSessionFactsOfATokenization() {
    return (HostedSessionFacts.AnObjectAPayerFinishes)
        provider.state(RUN, aTokenization()).orElseThrow().hostedSession();
  }

  @Test
  void
      a_tokenization_names_neither_of_the_two_urls_the_published_create_response_carries_because_the_control_answer_prints_them_to_a_tester() {
    configure(Map.of());
    assertThat(provider.state(RUN, aTokenization()).orElseThrow().relatedObjects())
        .as(
            "this is the same map the object screen and the timeline print, so the two addresses"
                + " the create response carries are a working payer link in a tester's hands here,"
                + " which is what ThePayerStep says is never printed. The create response still"
                + " carries both, because that is worldline's own contract and a shop's browser"
                + " journey opens hostedTokenizationUrl out of it")
        .doesNotContainKeys(
            WorldlineFields.HOSTED_TOKENIZATION_URL, WorldlineFields.PARTIAL_REDIRECT_URL);
  }

  @Test
  void a_payment_names_the_authorization_mode_and_the_status_code_it_is_at() {
    configure(Map.of());
    assertThat(provider.state(RUN, aHold()).orElseThrow().relatedObjects())
        .containsEntry(
            WorldlineFields.AUTHORIZATION_MODE,
            WorldlineAuthorizationMode.PRE_AUTHORIZATION.name());
  }

  @Test
  void a_payment_started_from_a_screen_reports_a_balance_the_capture_action_is_drawn_from() {
    configure(Map.of());
    assertThat(provider.state(RUN, aHold()).orElseThrow().derivedBalances())
        .get()
        .extracting(balances -> balances.capturableRemainder().value())
        .isEqualTo("5000");
  }

  @Test
  void the_object_list_of_this_run_holds_both_kinds_this_provider_has_newest_first() {
    configure(Map.of());
    String paymentId = aHold();
    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));
    assertThat(page).isInstanceOf(ProviderObjectPage.Listed.class);
    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .startsWith(paymentId)
        .hasSize(2);
  }

  @Test
  void a_payment_and_the_hosted_page_it_was_tokenized_on_are_named_apart_in_the_same_list() {
    configure(Map.of());
    aHold();

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(object -> object.kind().asThisProviderNamesIt())
        .containsExactly("a hold on a card", "a hosted card page");
  }

  @Test
  void a_cursor_this_run_holds_no_object_under_is_refused_rather_than_answered_with_a_page() {
    configure(Map.of());
    assertThat(provider.objects(new ProviderObjectQuery(RUN, "nothing", 10)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
  }

  @Test
  void a_cursor_naming_a_tokenization_pages_from_that_tokenization() {
    configure(Map.of());
    String payment = aHold();
    String tokenization = aTokenization();

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, tokenization, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .hasSize(2)
        .contains(payment)
        .doesNotContain(tokenization);
  }

  @Test
  void a_capture_asked_for_on_a_screen_mints_a_second_identifier_the_object_answers_to() {
    configure(Map.of());
    String paymentId = aHold();
    ObjectActionOutcome applied =
        provider.act(
            ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "5000", ObjectOrigin.UI));
    assertThat(applied).isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(provider.state(RUN, paymentId).orElseThrow().identity().members()).hasSize(2);
  }

  @Test
  void a_capture_addressed_by_a_later_identifier_names_which_one_it_was_addressed_by() {
    configure(Map.of());
    String paymentId = aHold();
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "2000", ObjectOrigin.UI));
    String minted = provider.state(RUN, paymentId).orElseThrow().identity().members().get(1);
    AddressedProviderObject addressed =
        AddressedProviderObject.read(provider, RUN, minted).orElseThrow();
    assertThat(addressed.addressedBy()).isEqualTo(minted);
    assertThat(addressed.canonical()).isEqualTo(paymentId);
    assertThat(addressed.addressedByItsCanonicalMember()).isFalse();
  }

  @Test
  void a_cancel_asked_for_on_a_screen_leaves_the_payment_cancelled() {
    configure(Map.of());
    String paymentId = aHold();
    assertThat(
            provider.act(
                ObjectActionRequest.of(RUN, paymentId, ObjectAction.CANCEL, null, ObjectOrigin.UI)))
        .isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(provider.state(RUN, paymentId).orElseThrow().normalizedStatus())
        .isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void an_action_on_an_identifier_this_run_holds_no_payment_under_names_no_object() {
    configure(Map.of());
    assertThat(
            provider.act(
                ObjectActionRequest.of(
                    RUN, "0000000000_0", ObjectAction.CANCEL, null, ObjectOrigin.UI)))
        .isInstanceOf(ObjectActionOutcome.NoSuchObject.class);
  }

  @Test
  void an_action_on_a_payment_whose_merchant_this_run_no_longer_configures_says_so() {
    configure(Map.of());
    String paymentId = aHold();
    provider.forget(RUN, ACCOUNT).remove();
    assertThat(
            provider.act(
                ObjectActionRequest.of(RUN, paymentId, ObjectAction.CANCEL, null, ObjectOrigin.UI)))
        .asInstanceOf(
            InstanceOfAssertFactories.type(ObjectActionOutcome.RefusedByTheProvider.class))
        .extracting(ObjectActionOutcome.RefusedByTheProvider::inItsOwnWords)
        .isEqualTo(WorldlineObjectActions.THE_RUN_CONFIGURES_NO_MERCHANT);
  }

  @Test
  void a_refund_asked_of_a_payment_leaves_the_hold_standing_and_answers_in_worldlines_own_words() {
    configure(Map.of());
    String paymentId = aHold();

    ObjectActionOutcome refused =
        ObjectActionControl.perform(
            provider,
            ObjectActionRequest.of(RUN, paymentId, ObjectAction.REFUND, "5000", ObjectOrigin.UI));

    assertThat(refused)
        .isEqualTo(
            new ObjectActionOutcome.NotOffered(
                WorldlineObjectActions.THE_REFUND_ROUTE_IS_OUT_OF_THIS_SLICE));
    assertThat(provider.state(RUN, paymentId).orElseThrow().normalizedStatus())
        .as("the cancel route worldline's own action mapping falls through to was never taken")
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void this_provider_offers_a_capture_and_a_cancel_and_says_what_it_has_instead_of_a_refund() {
    assertThat(provider.objectActions().offers(ObjectAction.CAPTURE)).isTrue();
    assertThat(provider.objectActions().offers(ObjectAction.CANCEL)).isTrue();
    assertThat(provider.objectActions().offers(ObjectAction.REFUND)).isFalse();
    assertThat(provider.objectActions().whatItHasInsteadOf(ObjectAction.REFUND))
        .isEqualTo(WorldlineObjectActions.THE_REFUND_ROUTE_IS_OUT_OF_THIS_SLICE);
  }

  @Test
  void a_payer_step_is_offered_on_a_tokenization_and_never_on_a_payment() {
    configure(Map.of());
    assertThat(provider.hostedCompletion(RUN, aTokenization()))
        .isInstanceOf(HostedCompletionOffer.Offered.class);
    assertThat(provider.hostedCompletion(RUN, aHold()))
        .isInstanceOf(HostedCompletionOffer.NotOffered.class);
  }

  @Test
  void completing_an_object_this_run_holds_no_tokenization_under_names_no_object() {
    configure(Map.of());
    assertThat(
            provider.complete(
                new HostedCompletionRequest(
                    RUN,
                    aHold(),
                    PayerOutcome.PAID,
                    WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS,
                    ObjectOrigin.CONTROL_API)))
        .isInstanceOf(HostedCompletionOutcome.NoSuchObject.class);
  }

  @Test
  void this_module_schedules_no_webhook_and_says_in_its_own_words_why_not() {
    WebhookScheduleRequest asking =
        new WebhookScheduleRequest(RUN, "anything", null, null, null, ObjectOrigin.UI);

    assertThatThrownBy(() -> provider.schedule(asking))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining(WorldlineProvider.NO_WEBHOOK_LEAVES_THIS_MODULE);
  }

  @Test
  void resetting_one_run_clears_both_object_kinds_and_cancels_its_held_deliveries() {
    configure(Map.of());
    aHold();
    assertThat(provider.reset(RUN, ResetScope.TEST_RUN).clearedObjects()).isEqualTo(2);
    assertThat(held.theRunsItWasAskedToCancel()).containsExactly(RUN);
    assertThat(provider.objects(new ProviderObjectQuery(RUN, null, 10)))
        .asInstanceOf(InstanceOfAssertFactories.type(ProviderObjectPage.Listed.class))
        .extracting(ProviderObjectPage.Listed::objects)
        .asInstanceOf(InstanceOfAssertFactories.list(Object.class))
        .isEmpty();
  }

  @Test
  void resetting_everything_clears_both_object_kinds_of_every_run() {
    configure(Map.of());
    aHold();
    assertThat(provider.reset(RUN, ResetScope.ALL).clearedObjects()).isEqualTo(2);
    assertThat(held.howOftenItWasAskedToCancelEverything()).isEqualTo(1);
  }

  @Test
  void a_fixture_of_this_provider_is_a_card_named_by_the_token_a_payment_charges() {
    assertThat(
            provider.seed(
                new NewPaymentMethodFixture(
                    WorldlineApi.WORLDLINE,
                    RUN,
                    "fx_1",
                    "alias",
                    PaymentMethodKind.CARD,
                    "alias",
                    aCard(),
                    Map.of(ProviderPaymentMethodFields.TOKEN, "token-seeded"),
                    null,
                    ObjectOrigin.CONTROL_API)))
        .isNotNull();
  }

  @Test
  void a_fixture_naming_no_token_names_no_card_any_payment_of_this_contract_could_charge() {
    NewPaymentMethodFixture namingNoToken =
        new NewPaymentMethodFixture(
            WorldlineApi.WORLDLINE,
            RUN,
            "fx_1",
            "alias",
            PaymentMethodKind.CARD,
            "alias",
            aCard(),
            Map.of(),
            null,
            ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> provider.seed(namingNoToken))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessageContaining(WorldlineFixtureCards.A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN);
  }

  @Test
  void a_fixture_of_a_kind_this_slice_serves_no_input_for_is_refused() {
    NewPaymentMethodFixture aTokenWithoutACard =
        new NewPaymentMethodFixture(
            WorldlineApi.WORLDLINE,
            RUN,
            "fx_1",
            "alias",
            PaymentMethodKind.WALLET,
            "alias",
            null,
            Map.of(ProviderPaymentMethodFields.TOKEN, "t"),
            null,
            ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> provider.seed(aTokenWithoutACard))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessageContaining(WorldlineFixtureCards.A_TOKENIZED_CARD_IS_A_CARD);
  }

  @Test
  void the_preset_for_a_hold_and_its_capture_expands_into_the_two_routes_this_slice_serves() {
    PresetExpansion expanded =
        provider.expand(
            new PresetRequest(
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
                RUN,
                1,
                ScenarioMatch.anyRequest()));
    assertThat(expanded)
        .asInstanceOf(InstanceOfAssertFactories.type(PresetExpansion.Rules.class))
        .extracting(PresetExpansion.Rules::rules)
        .asInstanceOf(InstanceOfAssertFactories.list(Object.class))
        .hasSize(2);
  }

  @Test
  void a_webhook_preset_names_something_this_slice_cannot_express_and_says_why() {
    assertThat(
            provider.expand(
                new PresetRequest(
                    ScenarioPreset.WEBHOOK_DELAYED, RUN, 1, ScenarioMatch.anyRequest())))
        .isInstanceOf(PresetExpansion.NotExpressible.class);
  }

  @Test
  void a_refund_preset_names_a_route_the_operator_put_outside_this_slice() {
    assertThat(
            provider.expand(
                new PresetRequest(
                    ScenarioPreset.REFUND_FULL_SUCCESS, RUN, 1, ScenarioMatch.anyRequest())))
        .asInstanceOf(InstanceOfAssertFactories.type(PresetExpansion.NotExpressible.class))
        .extracting(PresetExpansion.NotExpressible::reason)
        .asString()
        .contains(WorldlineObjectActions.THE_REFUND_ROUTE_IS_OUT_OF_THIS_SLICE);
  }

  @Test
  void the_two_maintenance_operations_declare_a_key_the_object_they_address_may_replace() {
    assertThat(theOperation(WorldlineOperations.CAPTURE_PAYMENT).storyKeyLifetime())
        .isEqualTo(StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE);
    assertThat(theOperation(WorldlineOperations.CANCEL_PAYMENT).storyKeyLifetime())
        .isEqualTo(StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE);
  }

  @Test
  void the_three_operations_that_create_or_read_an_object_keep_one_identifier_for_life() {
    assertThat(theOperation(WorldlineOperations.CREATE_PAYMENT).storyKeyLifetime())
        .isEqualTo(StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE);
    assertThat(theOperation(WorldlineOperations.CREATE_HOSTED_TOKENIZATION).storyKeyLifetime())
        .isEqualTo(StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE);
    assertThat(theOperation(WorldlineOperations.GET_HOSTED_TOKENIZATION).storyKeyLifetime())
        .isEqualTo(StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE);
  }

  @Test
  void a_flow_keyed_story_spanning_a_capture_is_refused_where_the_rule_is_written() {
    List<ScenarioRefusal> refused =
        provider
            .vocabulary()
            .refusals(
                List.of(
                    aStep(WorldlineOperations.CREATE_PAYMENT, "start", "held"),
                    aStep(WorldlineOperations.CAPTURE_PAYMENT, "held", null)),
                List.of());
    assertThat(refused).extracting(ScenarioRefusal::property).contains("story");
  }

  @Test
  void a_story_keyed_on_the_whole_test_run_is_not_narrowed_by_the_identifier_a_capture_mints() {
    List<ScenarioRefusal> refused =
        provider
            .vocabulary()
            .refusals(
                List.of(
                    aTestRunStep(WorldlineOperations.CREATE_PAYMENT, "start", "held"),
                    aTestRunStep(WorldlineOperations.CAPTURE_PAYMENT, "held", null)),
                List.of());
    assertThat(refused).isEmpty();
  }

  @Test
  void a_rule_naming_an_event_type_can_never_fire_because_this_slice_serves_no_webhook() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        WorldlineApi.WORLDLINE,
                        RUN,
                        1,
                        ScenarioMatch.anyRequest().reporting("payment.created"),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .extracting(ScenarioRefusal::property)
        .contains("match.eventType");
  }

  @Test
  void this_provider_answers_no_operation_with_a_processing_state_and_says_what_it_has_instead() {
    assertThat(provider.vocabulary().theOperationsThatReportAProcessingState()).isEmpty();
  }

  @Test
  void a_keyed_exchange_page_of_this_run_is_drawn_from_this_provider_own_mechanisms() {
    configure(Map.of());
    assertThat(provider.keyedExchanges(new KeyedExchangeQuery(RUN, Long.MAX_VALUE, 10)))
        .isNotNull();
  }

  private static FixtureCard aCard() {
    return new FixtureCard("VISA", "1111", 12, 2030, "credit");
  }

  private static ProviderOperation theOperation(String name) {
    return WorldlineOperations.VOCABULARY.operation(name).orElseThrow();
  }

  private static NewScenarioRule aStep(String operation, String from, String to) {
    return new NewScenarioRule(
        WorldlineApi.WORLDLINE,
        RUN,
        1,
        ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.FixedSuccess(),
        new ScenarioStory("a-story", StoryKeying.FLOW, from, to));
  }

  private static NewScenarioRule aTestRunStep(String operation, String from, String to) {
    return new NewScenarioRule(
        WorldlineApi.WORLDLINE,
        RUN,
        1,
        ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.FixedSuccess(),
        new ScenarioStory("a-story", StoryKeying.TEST_RUN, from, to));
  }

  @Test
  void a_state_read_of_an_identifier_no_object_of_this_run_answers_to_finds_nothing() {
    configure(Map.of());
    assertThat(provider.state(RUN, "nothing")).isEmpty();
  }

  @Test
  void every_identifier_a_payment_has_answered_to_reaches_the_same_object() {
    configure(Map.of());
    String paymentId = aHold();
    provider.act(
        ObjectActionRequest.of(RUN, paymentId, ObjectAction.CAPTURE, "2000", ObjectOrigin.UI));
    List<String> members = provider.state(RUN, paymentId).orElseThrow().identity().members();
    for (String member : members) {
      Optional<ProviderObjectState> reached = provider.state(RUN, member);
      assertThat(reached)
          .get()
          .extracting(ProviderObjectState::providerObjectId)
          .isEqualTo(paymentId);
    }
  }

  @Test
  void the_pairing_worldline_publishes_is_its_v2_path_and_the_hmac_scheme_it_signs_with() {
    ProviderPairing pairing = provider.pairing();

    assertThat(pairing.provider()).isEqualTo(WorldlineApi.WORLDLINE);
    assertThat(pairing.apiBasePath()).isEqualTo("/v2");
    assertThat(pairing.settings())
        .extracting(PairingSetting::name)
        .containsExactly(
            "WORLDLINE_BASE_URL",
            "WORLDLINE_MERCHANT_ID",
            "WORLDLINE_API_KEY",
            "WORLDLINE_API_SECRET",
            "WORLDLINE_AUTHORIZATION_SCHEME",
            "SIMULATOR_ACCOUNT_ID",
            "SIMULATOR_TEST_RUN_HEADER",
            "SIMULATOR_TEST_RUN_ID");
    assertThat(pairing.accountFieldsItReads()).containsExactly("merchantId", "apiKey", "apiSecret");
    assertThat(pairing.howARequestNamesItsAccount())
        .contains("Authorization: GCS v1HMAC:<apiKey>:<signature>")
        .contains("HMAC-SHA256, keyed with apiSecret")
        .contains("first path segment after /v2");
  }

  @Test
  void
      the_personas_worldline_publishes_each_seed_a_token_of_the_shape_its_tokenization_hands_back() {
    ProviderPersonas published = provider.personas(ids);

    assertThat(published.provider()).isEqualTo(WorldlineApi.WORLDLINE);
    assertThat(published.theNamesItPublishes())
        .containsExactly(
            "worldline-customer-with-tokenized-card",
            "worldline-tokenized-card-that-is-declined",
            "worldline-tokenized-card-whose-capture-answer-is-lost");
    assertThat(published.intentsItPublishesNoneFor()).isEmpty();
    for (DeclaredPersona persona : published.personas()) {
      PersonaFixture instrument = persona.fixtures().get(0);
      assertThat(instrument.theProviderNativeKeysItCarries()).containsExactly("token");
      assertThat(instrument.mint().get("token")).matches("[0-9a-f]{32}");
      assertThat(instrument.triggersTheRuleAt()).isEqualTo(0);
      assertThat(instrument.card()).isNotNull();
      assertThat(instrument.card().last4()).isEqualTo("1111");
      assertThat(instrument.card().expMonth()).isEqualTo(12);
      assertThat(instrument.card().expYear()).isEqualTo(2030);
    }
  }

  @Test
  void every_persona_worldline_publishes_writes_a_preset_this_module_can_expand() {
    for (DeclaredPersona persona : provider.personas(ids).personas()) {
      for (String named : persona.thePresetNamesItWrites()) {
        ScenarioPreset preset = ScenarioPreset.of(named).orElseThrow();
        assertThat(
                provider.expand(
                    new PresetRequest(
                        preset, TestRunId.of("run-a"), 0, ScenarioMatch.anyRequest())))
            .isInstanceOf(PresetExpansion.Rules.class);
      }
    }
  }

  @Test
  void the_intent_each_worldline_persona_answers_is_the_business_shape_a_tester_asked_for() {
    ProviderPersonas published = provider.personas(ids);

    assertThat(published.named("worldline-customer-with-tokenized-card").orElseThrow().intent())
        .isEqualTo(PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS);
    assertThat(published.named("worldline-tokenized-card-that-is-declined").orElseThrow().intent())
        .isEqualTo(PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED);
    assertThat(
            published
                .named("worldline-tokenized-card-whose-capture-answer-is-lost")
                .orElseThrow()
                .intent())
        .isEqualTo(PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST);
  }

  @Test
  void the_compatibility_checks_worldline_publishes_name_six_operations_and_withhold_none() {
    List<ContractCheck> checks = provider.compatibilityChecks(new CheckJson(), ids);

    assertThat(checks)
        .extracting(ContractCheck::operation, check -> check.provenance().sample())
        .containsExactly(
            tuple(
                WorldlineCompatibilityChecks.THE_GATEWAY_HOLDS,
                "worldline/create-payment-pending-capture"),
            tuple(
                WorldlineCompatibilityChecks.THE_MAINTENANCE_MINTS_AN_ID,
                "worldline/create-payment-pending-capture"),
            tuple(
                WorldlineCompatibilityChecks.THE_GATEWAY_REFUSES_A_SIGNATURE,
                "worldline/access-to-merchant-not-allowed"),
            tuple(
                WorldlineCompatibilityChecks.THE_GATEWAY_KNOWS_NO_SUCH_PAYMENT,
                "worldline/unknown-payment-id"),
            tuple(
                WorldlineCompatibilityChecks.THE_GATEWAY_REPLAYS_A_KEY,
                "worldline/idempotent-replay"),
            tuple(WorldlineCompatibilityChecks.THE_TRAIL_RECORDS, "simulator/audit-record"));
    assertThat(checks)
        .filteredOn(check -> check.applicability() instanceof Applicability.DoesNotApply)
        .isEmpty();
    assertThat(checks).allSatisfy(check -> assertThat(check.expectations()).isNotEmpty());
  }

  @Test
  void the_maintenance_check_worldline_publishes_asserts_the_two_payidsubs_the_vendor_prints() {
    List<ContractCheck> checks = provider.compatibilityChecks(new CheckJson(), ids);

    assertThat(checks.get(1).expectations())
        .extracting(ContractExpectation::field, expectation -> expectation.published().value())
        .containsExactly(tuple("payment.payIdSub", "_0"), tuple("capture.payIdSub", "_1"));
  }

  @Test
  void declares_that_its_contract_has_a_page_a_payer_finishes_and_names_the_object_that_waits() {
    assertThat(provider.hostedPages())
        .isEqualTo(
            new HostedPageContract.ObjectsAPayerFinishes(
                List.of(
                    new HostedPageContract.AnObjectItKeepsWaiting(
                        "a hosted tokenization session", true))));
  }

  @Test
  void publishes_the_facts_of_the_one_object_its_declaration_promises_and_of_no_payment_besides() {
    configure(Map.of());

    assertThat(TheObjectsADeclarationPromises.of(provider, RUN, List.of(aTokenization(), aHold())))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }

  @ParameterizedTest(name = "reads {0} back as one line per rule")
  @EnumSource(ScenarioPreset.class)
  void every_ready_made_flow_reads_back_as_one_line_per_rule(ScenarioPreset preset) {
    APresetReadsBackInWords.assertItDoes(
        preset, provider.expand(new PresetRequest(preset, RUN, 0, ScenarioMatch.anyRequest())));
  }
}
