package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.smoke.Applicability;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheObjectsADeclarationPromises;
import dev.marcinromanowski.pspsim.kernel.spi.TheSingleIdentifierFloor;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.ui.APresetReadsBackInWords;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.AbstractThrowableAssert;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AutraProviderControlTest {

  private static final TestRunId RUN = TestRunId.of("autra-control");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String PROPERTY = "accounts[0].configuration.";
  private static final TestRunId ANOTHER_RUN = TestRunId.of("autra-control-elsewhere");
  private static final int TWO_PAYMENTS = 2;
  private static final int ONE_PAYMENT_ELSEWHERE = 1;
  private static final int THREE_PIX = 3;
  private static final int TWO_CARD_SESSIONS = 2;
  private static final int ONE_DELIVERY = 1;
  private static final int FIVE_SESSIONS = 5;
  private static final int ONE_FIXTURE = 1;
  private static final int ONE_RULE = 1;
  private static final int ONE_AUDIT_RECORD = 1;
  private static final int ONE_DELIVERY_ATTEMPT_RECORD = 1;
  private static final String A_CALLBACK_TARGET = "https://merchant.example/autra/acquiring";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final IdGenerator ids = new AutraDoubles.CountingIds();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final AutraDoubles.HeldResponses heldResponses = new AutraDoubles.HeldResponses();
  private final AutraDoubles.PixPayments pixPayments = new AutraDoubles.PixPayments();
  private final AutraDoubles.CardSessions cardSessions = new AutraDoubles.CardSessions();
  private final AutraDoubles.Accounts accounts = new AutraDoubles.Accounts();

  private final WebhookDispatcher callbackDeliveries =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          AutraDoubles.clockAt(NOW),
          new AutraDoubles.CountingIds(),
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(rules),
          List.of(new AutraCallbackAcknowledgement()),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final AutraCallbacks callbacks =
      new AutraCallbacks(
          pixPayments, merchants, accounts, callbackDeliveries, new AutraDoubles.CountingIds());

  private final SimulatorProvider provider =
      new AutraProvider(
          merchants,
          sessions,
          payments,
          pixPayments,
          cardSessions,
          audit,
          fixtures,
          rules,
          heldResponses,
          AutraDoubles.clockAt(NOW),
          new OpenAcquiringSession(sessions, ids, scenarios),
          new OpenCardSession(cardSessions, ids, scenarios, AutraWiring.PUBLIC_BASE_URL),
          new ChargeStoredCard(
              payments,
              sessions,
              new AutraStoredCards(
                  cardSessions, AutraDoubles.fixturesHoldingTheExamplePair(RUN, ANOTHER_RUN)),
              ids,
              scenarios),
          new TokenizeOnTheHostedPage(cardSessions, new AutraPayerCards(fixtures), ids),
          new PreAuthorize(
              payments,
              sessions,
              new AutraStoredCards(
                  cardSessions, AutraDoubles.fixturesHoldingTheExamplePair(RUN, ANOTHER_RUN)),
              ids,
              scenarios),
          new CaptureHold(payments, scenarios),
          new ReverseOrRelease(payments, scenarios),
          new CreatePixCheckout(pixPayments, sessions, ids, scenarios),
          new LookUpPayment(payments),
          new ListPayments(payments),
          new CompletePixPayment(pixPayments, callbacks, new RecordedUnitsOfWork()),
          new SettlePixPayment(pixPayments, callbacks, new RecordedUnitsOfWork()),
          callbacks,
          callbackDeliveries);

  private static Map<String, String> configuration(Map<String, String> extra) {
    Map<String, String> declared = new LinkedHashMap<>(extra);
    declared.putIfAbsent(AutraMerchant.CLIENT_ID_PROPERTY, "client-" + ACCOUNT);
    declared.putIfAbsent(AutraMerchant.CLIENT_SECRET_PROPERTY, "secret-" + ACCOUNT);
    declared.putIfAbsent(AutraMerchant.DOCUMENT_ID_PROPERTY, DOCUMENT);
    return declared;
  }

  private static ProviderConfiguration configurationOf(ConfiguredAccount... accounts) {
    return new ProviderConfiguration(RUN, List.of(accounts), List.of(), List.of(), Map.of());
  }

  private void configure(Map<String, String> declared) {
    provider.parse(configurationOf(new ConfiguredAccount(ACCOUNT, declared))).store();
  }

  @Test
  void the_wire_namespaces_and_the_answer_off_them_are_the_ones_this_module_declares() {
    assertThat(provider.wireRoutes()).isEqualTo(AutraWireFaults.ROUTES);
    assertThat(provider.wireRoutes().covers(AutraApiController.PIX_CHECKOUT))
        .as("a namespace this module forgot to claim leaves a client on the wrong answer")
        .isTrue();
    for (WireFault fault : WireFault.values()) {
      assertThat(provider.theAnswerOffItsRoutes(fault, AutraApiController.OAUTH_TOKEN))
          .as(fault.name())
          .isInstanceOf(ProviderWireAnswer.NothingThisContractPublishes.class);
    }
    assertThat(
            provider.theAnswerOffItsRoutes(
                WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, AutraApiController.PIX_CHECKOUT))
        .as(
            "autra publishes a 500 with a body on its acquiring namespace and none on the token"
                + " route, so a module that answers one answer everywhere answers one of the two"
                + " wrongly")
        .isInstanceOf(ProviderWireAnswer.InThisProvidersOwnWords.class);
  }

  @Test
  void this_module_answers_to_the_provider_id_every_other_surface_addresses_it_by() {
    assertThat(provider.id()).isEqualTo(AutraApi.AUTRA);
    assertThat(provider.id().value()).isEqualTo("autra");
  }

  @Test
  void
      the_account_form_asks_for_three_credentials_the_publication_settings_and_the_callback_secret() {
    ProviderAccountFields fields = provider.accountFields();

    assertThat(fields.provider()).isEqualTo(AutraApi.AUTRA);
    assertThat(fields.keys())
        .containsExactly(
            AutraMerchant.CLIENT_ID_PROPERTY,
            AutraMerchant.CLIENT_SECRET_PROPERTY,
            AutraMerchant.DOCUMENT_ID_PROPERTY,
            AutraOauthFailure.SETTING,
            AutraOauthFieldCasing.SETTING,
            AutraRequestShape.PRE_AUTHORIZATION_SETTING,
            AutraRequestShape.VOID_SETTING,
            AutraAuthorizerMessage.SETTING,
            AutraMerchantNotFound.SETTING,
            AutraSaleDecline.SETTING,
            AutraAcquirerReport.SETTING,
            AutraWebhookSecret.SETTING);
    assertThat(fields.secretKeys())
        .containsExactlyInAnyOrder(
            AutraMerchant.CLIENT_SECRET_PROPERTY, AutraWebhookSecret.SETTING);
  }

  @Test
  void this_provider_offers_all_three_actions_a_payment_of_this_simulator_can_be_asked_for() {
    ObjectActions actions = provider.objectActions();

    assertThat(actions.provider()).isEqualTo(AutraApi.AUTRA);
    assertThat(actions.offers(ObjectAction.CAPTURE)).isTrue();
    assertThat(actions.offers(ObjectAction.CANCEL)).isTrue();
    assertThat(actions.offers(ObjectAction.REFUND)).isTrue();
  }

  @Test
  void
      the_four_ways_this_provider_starts_an_object_are_the_hold_the_session_the_sale_and_the_pix() {
    ProviderOriginationFields fields = provider.originationFields();

    assertThat(fields.provider()).isEqualTo(AutraApi.AUTRA);
    assertThat(fields.names())
        .containsExactly(
            AutraOrigination.A_HOLD,
            AutraOrigination.A_CARD_SESSION,
            AutraOrigination.A_SALE,
            AutraOrigination.A_PIX);
    assertThat(fields.wayNamed(AutraOrigination.A_HOLD).orElseThrow().keys())
        .containsExactly(
            AutraFields.AMOUNT,
            AutraFields.CURRENCY,
            AutraFields.INSTALLMENTS,
            AutraFields.ORDER_ID);
    assertThat(fields.wayNamed(AutraOrigination.A_CARD_SESSION).orElseThrow().keys())
        .containsExactly(AutraFields.NICKNAME);
    assertThat(fields.wayNamed(AutraOrigination.A_SALE).orElseThrow().keys())
        .containsExactly(
            AutraFields.AMOUNT,
            AutraFields.CURRENCY,
            AutraFields.TRANSACTION_TYPE,
            AutraFields.INSTALLMENTS,
            AutraFields.SLUG_TOKEN,
            AutraFields.SLUG_STORED_CARD,
            AutraFields.ORDER_ID);
    assertThat(fields.wayNamed(AutraOrigination.A_PIX).orElseThrow().keys())
        .containsExactly(
            AutraFields.AMOUNT,
            AutraFields.CURRENCY,
            AutraFields.DESCRIPTION,
            AutraFields.ORDER_ID);
    assertThat(fields.fieldsItFillsInItself())
        .containsOnlyKeys(
            AutraFields.DOCUMENT_ID, AutraFields.TRANSACTIONAL_TOKEN, AutraFields.CARD);
  }

  @Test
  void the_sale_is_the_only_way_this_provider_lets_a_tester_choose_debit() {
    assertThat(
            provider
                .originationFields()
                .wayNamed(AutraOrigination.A_SALE)
                .orElseThrow()
                .field(AutraFields.TRANSACTION_TYPE)
                .orElseThrow()
                .theValuesItAccepts())
        .isEqualTo("CREDIT (CREDIT), DEBIT (DEBIT)");
  }

  @Test
  void a_configured_account_is_stored_under_the_keys_this_provider_declares_it_reads() {
    configure(configuration(Map.of()));

    assertThat(merchants.find(RUN, ACCOUNT)).isPresent();
    assertThat(
            provider
                .parse(configurationOf(new ConfiguredAccount(ACCOUNT, configuration(Map.of()))))
                .configurationKeysItReads())
        .isEqualTo(AutraMerchant.CONFIGURATION_KEYS);
  }

  @Test
  void the_account_secret_is_withheld_from_the_disclosure_and_every_other_value_is_shown() {
    configure(configuration(Map.of()));

    DisclosedAccount disclosed = provider.disclosureOf(RUN, ACCOUNT).orElseThrow();

    assertThat(disclosed.disclosed())
        .containsEntry(AutraMerchant.CLIENT_ID_PROPERTY, List.of("client-" + ACCOUNT))
        .containsEntry(AutraMerchant.DOCUMENT_ID_PROPERTY, List.of(DOCUMENT))
        .containsEntry(
            AutraMerchant.CLIENT_SECRET_PROPERTY,
            Redaction.redact(
                    Map.of(AutraMerchant.CLIENT_SECRET_PROPERTY, List.of("secret-" + ACCOUNT)),
                    AutraMerchant.ACCOUNT_FIELDS.secretKeys())
                .get(AutraMerchant.CLIENT_SECRET_PROPERTY));
    assertThat(disclosed.disclosed().get(AutraMerchant.CLIENT_SECRET_PROPERTY))
        .isNotEqualTo(List.of("secret-" + ACCOUNT));
  }

  @Test
  void the_publication_each_setting_chose_is_shown_on_the_disclosure_under_that_settings_own_key() {
    configure(
        configuration(
            Map.of(
                AutraOauthFailure.SETTING,
                AutraOauthFailure.UNAUTHORIZED_INVALID_CLIENT.name(),
                AutraOauthFieldCasing.SETTING,
                AutraOauthFieldCasing.SNAKE_CASE.name(),
                AutraRequestShape.PRE_AUTHORIZATION_SETTING,
                AutraRequestShape.PUBLISHED_GUIDE_EXAMPLE.name(),
                AutraRequestShape.VOID_SETTING,
                AutraRequestShape.PUBLISHED_GUIDE_EXAMPLE.name(),
                AutraAuthorizerMessage.SETTING,
                "51 - Not sufficient funds",
                AutraMerchantNotFound.SETTING,
                AutraMerchantNotFound.FORBIDDEN.name())));

    assertThat(provider.disclosureOf(RUN, ACCOUNT).orElseThrow().disclosed())
        .containsEntry(
            AutraOauthFailure.SETTING,
            List.of(AutraOauthFailure.UNAUTHORIZED_INVALID_CLIENT.name()))
        .containsEntry(
            AutraOauthFieldCasing.SETTING, List.of(AutraOauthFieldCasing.SNAKE_CASE.name()))
        .containsEntry(
            AutraRequestShape.PRE_AUTHORIZATION_SETTING,
            List.of(AutraRequestShape.PUBLISHED_GUIDE_EXAMPLE.name()))
        .containsEntry(
            AutraRequestShape.VOID_SETTING,
            List.of(AutraRequestShape.PUBLISHED_GUIDE_EXAMPLE.name()))
        .containsEntry(AutraAuthorizerMessage.SETTING, List.of("51 - Not sufficient funds"))
        .containsEntry(
            AutraMerchantNotFound.SETTING, List.of(AutraMerchantNotFound.FORBIDDEN.name()));
  }

  @Test
  void a_payment_names_the_acquirer_references_autra_answered_it_with_among_its_related_objects() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();
    AutraPayment stored = payments.findById(RUN, paymentId).orElseThrow();

    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .containsEntry("merchantId", ACCOUNT)
        .containsEntry(AutraFields.DOCUMENT_ID, DOCUMENT)
        .containsEntry(AutraFields.AUTHORIZATION_CODE, stored.authorizationCode())
        .containsEntry(AutraFields.RRN, stored.rrn())
        .containsEntry("trackingNumber", stored.trackingNumber())
        .doesNotContainKey("nsu");
  }

  @Test
  void each_credential_this_provider_reads_the_account_from_is_required() {
    assertThatRefuses(Map.of(AutraMerchant.CLIENT_ID_PROPERTY, ""))
        .hasMessageContaining(AutraMerchant.CLIENT_ID_IS_THE_ACCOUNT);
    assertThatRefuses(Map.of(AutraMerchant.CLIENT_SECRET_PROPERTY, ""))
        .hasMessageContaining(AutraMerchant.CLIENT_SECRET_IS_REQUIRED);
    assertThatRefuses(Map.of(AutraMerchant.DOCUMENT_ID_PROPERTY, ""))
        .hasMessageContaining(AutraMerchant.DOCUMENT_ID_IS_THE_ACQUIRING_IDENTITY);
  }

  @Test
  void a_masked_merchant_document_is_refused_because_autra_publishes_it_without_a_mask() {
    assertThatRefuses(Map.of(AutraMerchant.DOCUMENT_ID_PROPERTY, "25.055.662/0001-90"))
        .hasMessageContaining(AutraMerchant.DOCUMENT_ID_IS_DIGITS_ONLY);
  }

  @Test
  void the_grammar_the_authorizer_message_words_spell_out_is_the_one_its_shape_enforces() {
    assertThat(AutraAuthorizerMessage.of("settings.x", AutraAuthorizerMessage.DEFAULT))
        .as("the msg autra publishes is one its own shape accepts")
        .isEqualTo(AutraAuthorizerMessage.DEFAULT);
    assertThat(AutraAuthorizerMessage.of("settings.x", "51 - Not sufficient funds"))
        .isEqualTo("51 - Not sufficient funds");
    for (String outsideTheGrammar :
        List.of("declined", "5 - Do not honor", "005 - Do not honor", "05 Do not honor", "05 - ")) {
      assertThatThrownBy(() -> AutraAuthorizerMessage.of("settings.x", outsideTheGrammar))
          .as(
              "the words spell a two-digit issuer code, a space, a hyphen, a space and the text,"
                  + " and %s breaks one of those five, so no part of that sentence can drift"
                  + " away from the pattern beside it",
              outsideTheGrammar)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("a two-digit issuer code, a space, a hyphen, a space and the text");
    }
  }

  @Test
  void a_merchant_document_is_refused_in_the_characters_its_shape_admits_and_not_only_in_autras() {
    assertThatRefuses(Map.of(AutraMerchant.DOCUMENT_ID_PROPERTY, "25.055.662/0001-90"))
        .as(
            "a tester who has read no Brazilian tax form reads which characters the box takes,"
                + " and the vendor phrase stays behind it for whoever knows it. The words read"
                + " \"one character or more\" while a longest was enforced three lines below"
                + " them, so the longest the store holds is in the pattern the words measure")
        .hasMessageContaining(
            "documentId must match [0-9]{1,32}, which is at most 32 characters, each a digit, for"
                + " example 12345678000190")
        .hasMessageContaining("CPF ou CNPJ, sem mascara");
    assertThat(AutraMerchant.A_DOCUMENT.admits(AutraMerchant.A_DOCUMENT_AUTRA_PUBLISHES))
        .as("the document the words offer as an example is one the shape accepts")
        .isTrue();
  }

  @Test
  void a_merchant_document_of_exactly_the_width_the_store_holds_is_configured_without_complaint() {
    configure(
        configuration(
            Map.of(
                AutraMerchant.DOCUMENT_ID_PROPERTY,
                "1".repeat(AutraMerchant.LONGEST_DOCUMENT_THE_STORE_HOLDS))));

    assertThat(merchants.find(RUN, ACCOUNT).orElseThrow().documentId())
        .hasSize(AutraMerchant.LONGEST_DOCUMENT_THE_STORE_HOLDS);
  }

  @Test
  void a_setting_configured_as_blank_text_takes_the_publication_this_simulator_defaults_to() {
    configure(
        configuration(
            Map.of(
                AutraOauthFailure.SETTING, "",
                AutraOauthFieldCasing.SETTING, "",
                AutraRequestShape.PRE_AUTHORIZATION_SETTING, "",
                AutraRequestShape.VOID_SETTING, "",
                AutraAuthorizerMessage.SETTING, "",
                AutraMerchantNotFound.SETTING, "")));

    AutraMerchant configured = merchants.find(RUN, ACCOUNT).orElseThrow();
    assertThat(configured.oauthFailure())
        .isEqualTo(AutraOauthFailure.BAD_REQUEST_WITH_ERROR_STRING);
    assertThat(configured.oauthFieldCasing()).isEqualTo(AutraOauthFieldCasing.CAMEL_CASE);
    assertThat(configured.preAuthorizationShape()).isEqualTo(AutraRequestShape.PUBLISHED_SCHEMA);
    assertThat(configured.voidShape()).isEqualTo(AutraRequestShape.PUBLISHED_SCHEMA);
    assertThat(configured.authorizerRejectedMessage()).isEqualTo(AutraAuthorizerMessage.DEFAULT);
    assertThat(configured.merchantNotFoundStatus()).isEqualTo(AutraMerchantNotFound.NOT_FOUND);
  }

  @Test
  void a_merchant_document_wider_than_the_store_holds_is_refused_in_the_words_that_state_it() {
    assertThatRefuses(
            Map.of(
                AutraMerchant.DOCUMENT_ID_PROPERTY,
                "1".repeat(AutraMerchant.LONGEST_DOCUMENT_THE_STORE_HOLDS + 1)))
        .as(
            "a document of 33 digits used to be answered by a second sentence the measured words"
                + " could not see; one pattern now states the longest and one refusal says it,"
                + " with the store's reason behind it")
        .hasMessageContaining(
            "documentId must match [0-9]{1,32}, which is at most 32 characters, each a digit, for"
                + " example 12345678000190")
        .hasMessageContaining("it keeps the merchant document of every payment")
        .hasMessageContaining("provider autra publishes no length for this field at all");
  }

  @Test
  void a_value_naming_none_of_the_publications_a_setting_chooses_between_is_refused() {
    assertThatRefuses(Map.of(AutraOauthFailure.SETTING, "MAYBE"))
        .hasMessageContaining(AutraOauthFailure.SETTING);
    assertThatRefuses(Map.of(AutraOauthFieldCasing.SETTING, "KEBAB_CASE"))
        .hasMessageContaining(AutraOauthFieldCasing.SETTING);
    assertThatRefuses(Map.of(AutraRequestShape.PRE_AUTHORIZATION_SETTING, "NEITHER"))
        .hasMessageContaining(AutraRequestShape.PRE_AUTHORIZATION_SETTING);
    assertThatRefuses(Map.of(AutraRequestShape.VOID_SETTING, "NEITHER"))
        .hasMessageContaining(AutraRequestShape.VOID_SETTING);
    assertThatRefuses(Map.of(AutraMerchantNotFound.SETTING, "IM_A_TEAPOT"))
        .hasMessageContaining(AutraMerchantNotFound.SETTING);
    assertThatRefuses(Map.of(AutraAuthorizerMessage.SETTING, "declined"))
        .hasMessageContaining(AutraAuthorizerMessage.SETTING);
  }

  @Test
  void two_accounts_of_one_call_may_not_claim_one_client_id_or_one_merchant_document() {
    ProviderConfiguration twoAccountsOnOneClientId =
        configurationOf(
            new ConfiguredAccount(ACCOUNT, configuration(Map.of())),
            new ConfiguredAccount("merchant-2", configuration(Map.of())));

    assertThatThrownBy(() -> provider.parse(twoAccountsOnOneClientId))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(AutraMerchant.CLIENT_ID_PROPERTY);
    ProviderConfiguration twoAccountsOnOneDocumentId =
        configurationOf(
            new ConfiguredAccount(ACCOUNT, configuration(Map.of())),
            new ConfiguredAccount(
                "merchant-2", configuration(Map.of(AutraMerchant.CLIENT_ID_PROPERTY, "client-2"))));

    assertThatThrownBy(() -> provider.parse(twoAccountsOnOneDocumentId))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(AutraMerchant.DOCUMENT_ID_PROPERTY);
  }

  @Test
  void a_merchant_document_an_account_this_call_leaves_in_place_already_holds_is_refused() {
    configure(configuration(Map.of()));

    ProviderConfiguration takingTheDocumentOfAnotherAccount =
        configurationOf(
            new ConfiguredAccount(
                "merchant-2", configuration(Map.of(AutraMerchant.CLIENT_ID_PROPERTY, "client-2"))));

    assertThatThrownBy(() -> provider.parse(takingTheDocumentOfAnotherAccount))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(AutraMerchant.DOCUMENT_ID_PROPERTY);
  }

  @Test
  void a_client_id_an_account_this_call_leaves_in_place_already_holds_is_refused_by_name() {
    configure(configuration(Map.of()));

    ProviderConfiguration takingTheClientIdOfAnotherAccount =
        configurationOf(
            new ConfiguredAccount(
                "merchant-2", configuration(Map.of(AutraMerchant.DOCUMENT_ID_PROPERTY, "42"))));

    assertThatThrownBy(() -> provider.parse(takingTheClientIdOfAnotherAccount))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            PROPERTY
                + AutraMerchant.CLIENT_ID_PROPERTY
                + " is already configured for another account in this test run");
  }

  @Test
  void a_client_id_an_account_of_another_run_holds_is_free_for_this_run_to_take() {
    provider
        .parse(
            new ProviderConfiguration(
                ANOTHER_RUN,
                List.of(new ConfiguredAccount(ACCOUNT, configuration(Map.of()))),
                List.of(),
                List.of(),
                Map.of()))
        .store();

    provider
        .parse(
            configurationOf(
                new ConfiguredAccount(
                    "merchant-2", configuration(Map.of(AutraMerchant.DOCUMENT_ID_PROPERTY, "42")))))
        .store();

    assertThat(merchants.find(RUN, "merchant-2").orElseThrow().clientId())
        .isEqualTo("client-" + ACCOUNT);
  }

  @Test
  void
      the_story_key_this_provider_would_spell_for_a_configured_document_is_declared_as_a_witness() {
    assertThat(
            provider
                .parse(configurationOf(new ConfiguredAccount(ACCOUNT, configuration(Map.of()))))
                .longestStoryKeysItCouldSpell())
        .singleElement()
        .satisfies(
            witness -> {
              assertThat(witness.property())
                  .isEqualTo(PROPERTY + AutraMerchant.DOCUMENT_ID_PROPERTY);
              assertThat(witness.longestKeyItSpells()).contains(DOCUMENT);
            });
  }

  @Test
  void reconfiguring_one_account_under_the_document_it_already_holds_is_not_a_collision() {
    configure(configuration(Map.of()));

    configure(configuration(Map.of(AutraAuthorizerMessage.SETTING, "51 - Not sufficient funds")));

    assertThat(merchants.find(RUN, ACCOUNT).orElseThrow().authorizerRejectedMessage())
        .isEqualTo("51 - Not sufficient funds");
  }

  @Test
  void a_page_ended_on_a_cursor_carries_on_from_the_object_that_cursor_names() {
    configure(configuration(Map.of()));
    String first = ((OriginationOutcome.Originated) originate("100.00", null)).providerObjectId();
    String second = ((OriginationOutcome.Originated) originate("200.00", null)).providerObjectId();

    ProviderObjectPage.Listed page =
        (ProviderObjectPage.Listed) provider.objects(new ProviderObjectQuery(RUN, null, 1));
    ProviderObjectPage.Listed next =
        (ProviderObjectPage.Listed)
            provider.objects(new ProviderObjectQuery(RUN, page.cursorToCarryOnFrom(1), 1));

    assertThat(page.objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(second);
    assertThat(next.objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(first);
  }

  @Test
  void forgetting_an_account_removes_it_from_the_store_it_was_configured_into() {
    configure(configuration(Map.of()));

    provider.forget(RUN, ACCOUNT).remove();

    assertThat(merchants.find(RUN, ACCOUNT)).isEmpty();
  }

  @Test
  void the_order_is_declared_a_duplicate_check_whose_key_a_running_request_can_hold() {
    assertThat(provider.keyMechanisms().roleOf(AutraKeyMechanisms.ORDER_ID))
        .isEqualTo(KeyRole.DUPLICATE_DETECTION_KEY);
    assertThat(provider.keyMechanisms().claimOf(AutraKeyMechanisms.ORDER_ID))
        .isInstanceOf(KeyClaim.ClaimedBeforeTheOperationRuns.class);
    assertThat(provider.keyMechanisms().claimOf(AutraKeyMechanisms.ORDER_ID).describedAs())
        .contains("antes da chamada ao adquirente");
  }

  @Test
  void the_payment_path_segment_is_declared_a_correlation_key_no_request_can_find_held() {
    assertThat(provider.keyMechanisms().roleOf(AutraKeyMechanisms.PAYMENT_ID))
        .isEqualTo(KeyRole.CORRELATION_KEY);
    assertThat(provider.keyMechanisms().claimOf(AutraKeyMechanisms.PAYMENT_ID).canBeHeld())
        .isFalse();
  }

  @Test
  void this_provider_declares_that_it_publishes_no_true_idempotency_key() {
    assertThat(provider.keyMechanisms().has(KeyRole.TRUE_IDEMPOTENCY_KEY)).isFalse();
    assertThat(provider.keyMechanisms().theAbsenceOf(KeyRole.TRUE_IDEMPOTENCY_KEY))
        .contains("publishes no idempotency header");
  }

  @Test
  void the_vocabulary_exposes_the_eight_acquiring_operations_and_carries_no_customer() {
    assertThat(provider.vocabulary().operationNames())
        .containsExactly(
            AutraOperations.CARD_SESSION,
            AutraOperations.INITIALIZE,
            AutraOperations.SALE,
            AutraOperations.PRE_AUTHORIZATION,
            AutraOperations.CAPTURE,
            AutraOperations.VOID,
            AutraOperations.PIX_INITIALIZE,
            AutraOperations.PIX_CHECKOUT);
    assertThat(provider.vocabulary().factsItNeverCarries()).containsKey(MatchFact.CUSTOMER_ID);
  }

  @Test
  void a_rule_naming_a_code_this_contract_never_answers_a_refusal_with_is_refused() {
    assertThat(provider.vocabulary().failureCodes().admits(AutraErrors.AUTHORIZER_REJECTED))
        .isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("SOMETHING_ELSE")).isFalse();
  }

  @Test
  void no_operation_of_this_contract_answers_with_a_processing_state() {
    assertThat(provider.vocabulary().theOperationsThatReportAProcessingState()).isEmpty();
  }

  @Test
  void the_two_halves_of_the_stored_card_pair_are_the_two_fixture_keys_this_provider_reads() {
    ProviderPaymentMethodFields fields = provider.paymentMethodFields();

    assertThat(fields.keys())
        .containsExactly(
            ProviderPaymentMethodFields.TOKEN, ProviderPaymentMethodFields.PAYMENT_METHOD_ID);
    assertThat(fields.theRefusalOf(ProviderPaymentMethodFields.CUSTOMER_ID))
        .isEqualTo(AutraFixtureCards.NO_CUSTOMER);
  }

  @Test
  void neither_half_of_the_pair_is_required_on_its_own_and_neither_is_withheld_as_a_secret() {
    ProviderPaymentMethodFields fields = provider.paymentMethodFields();

    for (String key :
        List.of(ProviderPaymentMethodFields.TOKEN, ProviderPaymentMethodFields.PAYMENT_METHOD_ID)) {
      assertThat(fields.field(key).orElseThrow().required())
          .as("autra takes either half of the pair, so neither is required on its own")
          .isFalse();
      assertThat(fields.field(key).orElseThrow().secret())
          .as("a slug is a reference to a card and never a credential, so it is shown back")
          .isFalse();
    }
  }

  @Test
  void the_card_session_asks_a_tester_only_for_the_alias_the_cardholder_types_and_requires_none() {
    assertThat(
            provider
                .originationFields()
                .wayNamed(AutraOrigination.A_CARD_SESSION)
                .orElseThrow()
                .field(AutraFields.NICKNAME)
                .orElseThrow()
                .required())
        .isFalse();
  }

  @Test
  void the_sale_requires_the_three_fields_autra_publishes_and_leaves_the_rest_to_the_tester() {
    DeclaredOrigination sale =
        provider.originationFields().wayNamed(AutraOrigination.A_SALE).orElseThrow();

    assertThat(theRequiredFieldsOf(sale))
        .containsExactly(AutraFields.AMOUNT, AutraFields.CURRENCY, AutraFields.TRANSACTION_TYPE);
  }

  private static List<String> theRequiredFieldsOf(DeclaredOrigination way) {
    List<String> required = new ArrayList<>();
    for (AFieldATesterFillsIn field : way.fields()) {
      if (field.required()) {
        required.add(field.key());
      }
    }
    return List.copyOf(required);
  }

  @Test
  void a_seeded_card_naming_neither_half_of_the_pair_is_refused_under_the_provider_ids_property() {
    NewPaymentMethodFixture namingNoPair = aSeededCard(PaymentMethodKind.CARD, Map.of());

    assertThatThrownBy(() -> provider.seed(namingNoPair))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessageContaining(AutraFixtureCards.A_STORED_CARD_IS_NAMED_BY_A_PAIR);
  }

  @Test
  void a_seeded_wallet_is_refused_because_this_contract_tokenizes_a_card_and_nothing_else() {
    NewPaymentMethodFixture aTokenizedCardThatIsNoCard =
        aSeededCard(
            PaymentMethodKind.WALLET, Map.of(ProviderPaymentMethodFields.TOKEN, "slug-token-abc"));

    assertThatThrownBy(() -> provider.seed(aTokenizedCardThatIsNoCard))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessageContaining(AutraFixtureCards.A_TOKENIZED_CARD_IS_A_CARD);
  }

  @Test
  void a_seeded_card_naming_one_half_of_the_pair_stores_nothing_of_this_provider_own() {
    provider
        .seed(
            aSeededCard(
                PaymentMethodKind.CARD,
                Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "slug-stored-card-xyz")))
        .store(
            new PaymentMethodFixture(
                "fix_1",
                AutraApi.AUTRA,
                RUN,
                ACCOUNT,
                "owner-1",
                PaymentMethodKind.CARD,
                "a-card",
                new FixtureCard("visa", "1111", 12, 2030, "credit"),
                Map.of(),
                null,
                NOW));

    assertThat(fixtures.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  private static NewPaymentMethodFixture aSeededCard(
      PaymentMethodKind kind, Map<String, String> providerIds) {
    return new NewPaymentMethodFixture(
        AutraApi.AUTRA,
        RUN,
        ACCOUNT,
        "owner-1",
        kind,
        "a-card",
        kind.describesACard() ? new FixtureCard("visa", "1111", 12, 2030, "credit") : null,
        providerIds,
        null,
        ObjectOrigin.CONTROL_API);
  }

  @Test
  void this_provider_offers_no_payer_step_on_a_reservation_and_says_what_it_has_instead() {
    assertThat(provider.hostedCompletion(RUN, "any"))
        .isInstanceOf(HostedCompletionOffer.NotOffered.class);
    assertThat(
            ((HostedCompletionOffer.NotOffered) provider.hostedCompletion(RUN, "any"))
                .whatThisProviderHasInstead())
        .isEqualTo(CompletePixPayment.ONLY_A_PIX_HAS_A_PAYER_STEP);
  }

  @Test
  void a_callback_asked_for_an_object_no_pix_of_this_run_answers_to_names_what_does() {
    WebhookScheduleRequest namingNoPix =
        new WebhookScheduleRequest(RUN, "any", null, null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> provider.schedule(namingNoPix))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(AutraCallbacks.ONLY_A_PIX_HAS_A_CALLBACK);
  }

  @Test
  void a_payment_started_from_the_control_api_is_reserved_and_listed_under_the_id_it_reports() {
    configure(configuration(Map.of()));

    OriginationOutcome outcome = originate("500.00", null);

    assertThat(outcome).isInstanceOf(OriginationOutcome.Originated.class);
    String paymentId = ((OriginationOutcome.Originated) outcome).providerObjectId();
    ProviderObjectState state = provider.state(RUN, paymentId).orElseThrow();
    assertThat(state.nativeStatus()).isEqualTo("ACCEPTED");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(state.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(state.relatedObjects()).containsEntry(AutraFields.DOCUMENT_ID, DOCUMENT);
  }

  @Test
  void a_reservation_reports_an_open_authorization_and_a_capture_closes_it_but_keeps_a_reversal() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    assertThat(provider.state(RUN, paymentId).orElseThrow().authorizationOpen()).isTrue();
    assertThat(provider.state(RUN, paymentId).orElseThrow().capturesAccepted()).isTrue();
    assertThat(provider.state(RUN, paymentId).orElseThrow().refundsAccepted())
        .as("autra voids a reservation it has captured nothing of, so it has none of it to return")
        .isTrue();
    assertThat(
            provider
                .state(RUN, paymentId)
                .orElseThrow()
                .derivedBalances()
                .orElseThrow()
                .refundableRemainder())
        .isEqualTo(Amount.of("0"));

    provider.act(
        ObjectActionRequest.of(
            RUN, paymentId, ObjectAction.CAPTURE, "500.00", ObjectOrigin.CONTROL_API));

    assertThat(provider.state(RUN, paymentId).orElseThrow().authorizationOpen()).isFalse();
    assertThat(provider.state(RUN, paymentId).orElseThrow().capturesAccepted()).isFalse();
    assertThat(provider.state(RUN, paymentId).orElseThrow().refundsAccepted()).isTrue();
  }

  @Test
  void a_refusal_a_screen_reports_carries_the_status_and_the_code_the_provider_answered_with() {
    configure(configuration(Map.of()));

    OriginationOutcome outcome = originate("0", null);

    assertThat(((OriginationOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains("HTTP 400")
        .contains(AutraErrors.INVALID_AMOUNT)
        .contains(PreAuthorize.THE_AMOUNT_MUST_BE_ABOVE_ZERO);
  }

  @Test
  void the_preset_that_declines_a_capture_writes_the_issuer_refusal_code_on_the_capture_alone() {
    PresetExpansion.Rules expanded =
        (PresetExpansion.Rules)
            provider.expand(
                new PresetRequest(
                    ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
                    RUN,
                    10,
                    ScenarioMatch.anyRequest()));

    assertThat(expanded.rules())
        .satisfiesExactly(
            reservation -> {
              assertThat(reservation.match().operation())
                  .isEqualTo(AutraOperations.PRE_AUTHORIZATION);
              assertThat(reservation.action().theProviderCodeItComesToRestWith()).isNull();
            },
            capture -> {
              assertThat(capture.match().operation()).isEqualTo(AutraOperations.CAPTURE);
              assertThat(capture.action().theProviderCodeItComesToRestWith())
                  .isEqualTo(AutraErrors.AUTHORIZER_REJECTED);
            });
  }

  @Test
  void
      the_preset_that_answers_without_the_operation_first_uses_the_status_of_an_unreached_acquirer() {
    PresetExpansion.Rules expanded =
        (PresetExpansion.Rules)
            provider.expand(
                new PresetRequest(
                    ScenarioPreset.PSP_5XX_THEN_SUCCESS, RUN, 10, ScenarioMatch.anyRequest()));

    assertThat(expanded.rules())
        .filteredOn(
            rule -> rule.action() instanceof ScenarioAction.TransportFaultInsteadOfOperation)
        .isNotEmpty()
        .allSatisfy(
            rule ->
                assertThat(
                        ((TransportFault.ServerError)
                                ((ScenarioAction.TransportFaultInsteadOfOperation) rule.action())
                                    .fault())
                            .httpStatus())
                    .isEqualTo(502));
  }

  @Test
  void a_payment_started_with_an_order_reports_that_order_among_the_objects_it_names() {
    configure(configuration(Map.of()));

    OriginationOutcome outcome = originate("500.00", "pedido-abc-123");

    String paymentId = ((OriginationOutcome.Originated) outcome).providerObjectId();
    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .containsEntry(AutraFields.ORDER_ID, "pedido-abc-123");
  }

  @Test
  void starting_a_payment_on_an_account_this_run_does_not_configure_names_no_account() {
    assertThat(originate("500.00", null)).isInstanceOf(OriginationOutcome.NoSuchAccount.class);
  }

  @Test
  void starting_a_payment_this_provider_refuses_reports_the_refusal_in_the_providers_own_words() {
    configure(configuration(Map.of()));

    OriginationOutcome outcome = originate("0", null);

    assertThat(outcome).isInstanceOf(OriginationOutcome.RefusedByTheProvider.class);
    assertThat(((OriginationOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void capturing_a_reservation_from_the_control_api_applies_and_names_no_second_object() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    ObjectActionOutcome outcome =
        provider.act(
            ObjectActionRequest.of(
                RUN, paymentId, ObjectAction.CAPTURE, "480.00", ObjectOrigin.CONTROL_API));

    assertThat(outcome).isInstanceOf(ObjectActionOutcome.Applied.class);
    assertThat(((ObjectActionOutcome.Applied) outcome).createdObjectId()).isNull();
    assertThat(provider.state(RUN, paymentId).orElseThrow().normalizedStatus())
        .isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void cancelling_a_reservation_from_the_control_api_releases_the_whole_hold() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    provider.act(
        ObjectActionRequest.of(
            RUN, paymentId, ObjectAction.CANCEL, null, ObjectOrigin.CONTROL_API));

    assertThat(provider.state(RUN, paymentId).orElseThrow().normalizedStatus())
        .isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void refunding_a_captured_payment_from_the_control_api_returns_the_amount_it_names() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();
    provider.act(
        ObjectActionRequest.of(
            RUN, paymentId, ObjectAction.CAPTURE, "500.00", ObjectOrigin.CONTROL_API));

    provider.act(
        ObjectActionRequest.of(
            RUN, paymentId, ObjectAction.REFUND, "200.00", ObjectOrigin.CONTROL_API));

    assertThat(provider.state(RUN, paymentId).orElseThrow().normalizedStatus())
        .isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);
  }

  @Test
  void acting_on_an_object_this_run_does_not_hold_names_no_object() {
    configure(configuration(Map.of()));

    assertThat(
            provider.act(
                ObjectActionRequest.of(
                    RUN, "no-such-payment", ObjectAction.CANCEL, null, ObjectOrigin.CONTROL_API)))
        .isInstanceOf(ObjectActionOutcome.NoSuchObject.class);
  }

  @Test
  void acting_on_a_payment_whose_merchant_this_run_no_longer_configures_is_refused_and_says_why() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();
    merchants.remove(RUN, ACCOUNT);

    ObjectActionOutcome outcome =
        provider.act(
            ObjectActionRequest.of(
                RUN, paymentId, ObjectAction.CANCEL, null, ObjectOrigin.CONTROL_API));

    assertThat(outcome).isInstanceOf(ObjectActionOutcome.RefusedByTheProvider.class);
    assertThat(((ObjectActionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .isEqualTo(AutraObjectActions.THE_RUN_CONFIGURES_NO_MERCHANT);
  }

  @Test
  void an_action_this_provider_refuses_reports_the_refusal_in_the_providers_own_words() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    ObjectActionOutcome outcome =
        provider.act(
            ObjectActionRequest.of(
                RUN, paymentId, ObjectAction.CAPTURE, "900.00", ObjectOrigin.CONTROL_API));

    assertThat(((ObjectActionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void the_object_list_is_newest_first_and_a_cursor_it_never_issued_is_refused_as_such() {
    configure(configuration(Map.of()));
    String first = ((OriginationOutcome.Originated) originate("100.00", null)).providerObjectId();
    String second = ((OriginationOutcome.Originated) originate("200.00", null)).providerObjectId();

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(second, first);
    assertThat(provider.objects(new ProviderObjectQuery(RUN, "no-such-cursor", 10)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
  }

  @Test
  void a_pix_started_here_reads_back_with_the_qr_the_payer_scans_and_the_two_optional_fields() {
    configure(configuration(Map.of()));
    String pixId = originatedPix("99.90", "Pedido #1234", "pedido-abc-123");

    ProviderObjectState state = provider.state(RUN, pixId).orElseThrow();

    assertThat(state.providerObjectId()).isEqualTo(pixId);
    assertThat(state.nativeStatus()).isEqualTo(AutraStatus.PENDING);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.PROCESSING);
    assertThat(state.amount().value()).isEqualTo("99.90");
    assertThat(state.currency()).isEqualTo("BRL");
    assertThat(state.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(state.relatedObjects())
        .containsEntry("merchantId", ACCOUNT)
        .containsEntry(AutraFields.DOCUMENT_ID, DOCUMENT)
        .containsEntry(AutraFields.QR_CODE, CreatePixCheckout.theQrCodeOf(pixId))
        .containsEntry(AutraFields.EXPIRES_AT, "2026-09-22T10:05:00Z")
        .containsEntry(AutraFields.ORDER_ID, "pedido-abc-123")
        .containsEntry(AutraFields.DESCRIPTION, "Pedido #1234")
        .containsKeys(AutraFields.SLUG, AutraFields.MUID);
  }

  @Test
  void a_pix_started_without_a_description_or_an_order_carries_neither_on_what_it_left_behind() {
    configure(configuration(Map.of()));
    String pixId = originatedPix("99.90", null, null);

    ProviderObjectState state = provider.state(RUN, pixId).orElseThrow();

    assertThat(state.relatedObjects())
        .doesNotContainKey(AutraFields.ORDER_ID)
        .doesNotContainKey(AutraFields.DESCRIPTION)
        .containsKeys(
            "merchantId",
            AutraFields.DOCUMENT_ID,
            AutraFields.QR_CODE,
            AutraFields.EXPIRES_AT,
            AutraFields.SLUG,
            AutraFields.MUID);
  }

  @Test
  void a_pix_holds_no_open_authorization_and_accepts_no_reversal_because_autra_publishes_no_void() {
    configure(configuration(Map.of()));
    String pixId = originatedPix("99.90", null, null);

    ProviderObjectState waiting = provider.state(RUN, pixId).orElseThrow();
    assertThat(waiting.authorizationOpen()).isFalse();
    assertThat(waiting.refundsAccepted()).isFalse();
    assertThat(waiting.ledger().operations()).isEmpty();

    provider.complete(
        new HostedCompletionRequest(RUN, pixId, PayerOutcome.PAID, null, ObjectOrigin.CONTROL_API));

    ProviderObjectState paid = provider.state(RUN, pixId).orElseThrow();
    assertThat(paid.nativeStatus()).isEqualTo(AutraStatus.AUTHORIZED);
    assertThat(paid.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(paid.authorizationOpen()).isFalse();
    assertThat(paid.refundsAccepted()).isFalse();
    assertThat(paid.ledger().operations()).hasSize(1);
  }

  @Test
  void a_pix_and_a_reservation_of_one_run_are_listed_newest_first_across_the_two_stores() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("100.00", null)).providerObjectId();
    String pixId = originatedPix("99.90", null, null);

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(pixId, paymentId);
  }

  @Test
  void a_cursor_this_run_issued_for_a_pix_pages_past_it_to_the_reservation_before_it() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("100.00", null)).providerObjectId();
    String pixId = originatedPix("99.90", null, null);

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, pixId, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(paymentId);
  }

  @Test
  void a_cursor_this_run_issued_for_a_reservation_pages_past_it_to_nothing_when_it_is_oldest() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("100.00", null)).providerObjectId();
    originatedPix("99.90", null, null);

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, paymentId, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects()).isEmpty();
  }

  @Test
  void a_pix_offers_the_payer_step_and_a_reservation_of_the_same_run_offers_none() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("100.00", null)).providerObjectId();
    String pixId = originatedPix("99.90", null, null);

    assertThat(provider.hostedCompletion(RUN, pixId))
        .isEqualTo(
            new HostedCompletionOffer.OfferedWithoutACard(
                AutraApi.AUTRA,
                CompletePixPayment.THE_PAYER_PAYS_IN_THEIR_BANK_APP,
                CompletePixPayment.THE_PAYER_STEPS));
    assertThat(provider.hostedCompletion(RUN, paymentId))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                AutraApi.AUTRA, CompletePixPayment.ONLY_A_PIX_HAS_A_PAYER_STEP));
    assertThat(CompletePixPayment.THE_PAYER_PAYS_IN_THEIR_BANK_APP)
        .as("the sentence a tester reads beside a Pix is autra's own and not this simulator's")
        .contains("provider autra collects a Pix from the payer's own bank app and never from a")
        .contains("this Pix names no card and a payer step on it names none either");
    assertThat(CompletePixPayment.ONLY_A_PIX_HAS_A_PAYER_STEP)
        .contains("provider autra takes every payment of this contract server to server")
        .contains("This test run holds neither under that id");
  }

  @Test
  void an_action_of_this_provider_finds_no_such_object_when_a_pix_is_named_rather_than_a_payment() {
    configure(configuration(Map.of()));
    String pixId = originatedPix("99.90", null, null);

    assertThat(
            provider.act(
                ObjectActionRequest.of(
                    RUN, pixId, ObjectAction.CAPTURE, "99.90", ObjectOrigin.CONTROL_API)))
        .isEqualTo(new ObjectActionOutcome.NoSuchObject());
  }

  @Test
  void a_callback_of_a_settled_pix_is_resent_through_this_provider_and_names_the_job_it_queued() {
    configure(configuration(Map.of()));
    accounts.callingBack(RUN, ACCOUNT, A_CALLBACK_TARGET);
    String pixId = aPaidPix();
    provider.followUp(settle(pixId), NOW);

    List<ScheduledWebhook> jobs =
        provider.schedule(
            new WebhookScheduleRequest(
                RUN,
                pixId,
                AutraPixEvent.PAYOUT.eventType(),
                null,
                null,
                ObjectOrigin.CONTROL_API));

    assertThat(jobs).hasSize(1);
    assertThat(callbackDeliveries.of(AutraApi.AUTRA, RUN))
        .extracting(WebhookDelivery::deliveryId)
        .contains(jobs.get(0).webhookJobId());
    assertThat(callbackDeliveries.of(AutraApi.AUTRA, RUN))
        .extracting(WebhookDelivery::eventType)
        .contains(AutraPixEvent.PAYOUT.eventType());
  }

  @Test
  void a_payer_step_on_a_pix_this_provider_started_moves_it_and_queues_the_merchants_callback() {
    configure(configuration(Map.of()));
    accounts.callingBack(RUN, ACCOUNT, A_CALLBACK_TARGET);
    String pixId = originatedPix("99.90", null, null);

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN, pixId, PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API));

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.Completed.class);
    assertThat(provider.state(RUN, pixId).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.EXPIRED);
    assertThat(callbackDeliveries.of(AutraApi.AUTRA, RUN)).hasSize(1);
  }

  @Test
  void
      a_card_session_this_provider_started_reads_back_as_an_object_with_no_amount_and_no_currency() {
    configure(configuration(Map.of()));

    String sessionId = originatedCardSession("Personal Visa");

    ProviderObjectState state = provider.state(RUN, sessionId).orElseThrow();
    assertThat(state.nativeStatus()).isEqualTo(AutraStatus.PENDING);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.PROCESSING);
    assertThat(state.amount()).isNull();
    assertThat(state.currency()).isNull();
    assertThat(state.authorizationOpen())
        .as("a card session reserves nothing, so no authorization of one is ever open")
        .isFalse();
    assertThat(state.refundsAccepted())
        .as("a card session charges nothing, so nothing of one is ever reversible")
        .isFalse();
    assertThat(state.relatedObjects())
        .containsEntry("merchantId", ACCOUNT)
        .containsEntry(AutraFields.DOCUMENT_ID, DOCUMENT)
        .containsEntry(AutraFields.NICKNAME, "Personal Visa")
        .doesNotContainKey(AutraFields.HOSTED_CARD_URL)
        .containsKey(AutraFields.EXPIRES_AT)
        .doesNotContainKey(AutraFields.SLUG_TOKEN);
  }

  @Test
  void a_tokenized_session_reports_the_pair_a_later_sale_charges_and_the_card_facts_beside_it() {
    configure(configuration(Map.of()));
    String sessionId = originatedCardSession(null);

    provider.complete(
        new HostedCompletionRequest(
            RUN,
            sessionId,
            PayerOutcome.PAID,
            AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS,
            ObjectOrigin.CONTROL_API));

    ProviderObjectState state = provider.state(RUN, sessionId).orElseThrow();
    assertThat(state.nativeStatus()).isEqualTo(AutraCardSession.TOKENIZED);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(state.relatedObjects())
        .containsKey(AutraFields.SLUG_TOKEN)
        .containsKey(AutraFields.SLUG_STORED_CARD)
        .containsEntry(AutraFields.BRAND, AutraPayerCards.THE_PUBLISHED_BRAND)
        .containsEntry(AutraFields.FIRST4, AutraPayerCards.THE_PUBLISHED_FIRST4)
        .containsEntry(AutraFields.LAST4, AutraPayerCards.THE_PUBLISHED_LAST4)
        .containsEntry(AutraFields.TOKEN_EXPIRATION_DATE, "122030")
        .doesNotContainKey(AutraFields.NICKNAME);
  }

  @Test
  void a_sale_this_provider_started_reads_back_as_a_payment_that_took_the_money() {
    configure(configuration(Map.of()));

    String paymentId =
        ((OriginationOutcome.Originated) originateSale("150.00", null)).providerObjectId();

    ProviderObjectState state = provider.state(RUN, paymentId).orElseThrow();
    assertThat(state.nativeStatus()).isEqualTo(AutraStatus.ACCEPTED);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(state.authorizationOpen()).isFalse();
    assertThat(state.refundsAccepted()).isTrue();
    assertThat(state.relatedObjects())
        .containsEntry(AutraFields.SLUG_TOKEN, "slug-token-abc")
        .containsEntry(AutraFields.SLUG_STORED_CARD, "slug-stored-card-xyz");
  }

  @Test
  void a_capture_of_a_sale_is_refused_because_a_sale_has_nothing_reserved_left_to_take() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originateSale("150.00", null)).providerObjectId();

    ObjectActionOutcome outcome =
        provider.act(
            ObjectActionRequest.of(
                RUN, paymentId, ObjectAction.CAPTURE, "150.00", ObjectOrigin.CONTROL_API));

    assertThat(outcome).isInstanceOf(ObjectActionOutcome.RefusedByTheProvider.class);
    assertThat(((ObjectActionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains(AutraErrors.INVALID_STATUS)
        .contains(AutraPayment.A_SALE_HAS_NOTHING_RESERVED_LEFT_TO_TAKE);
  }

  @Test
  void
      a_sale_started_under_an_order_is_charged_once_and_the_repeat_is_refused_the_published_code() {
    configure(configuration(Map.of()));
    originateSale("150.00", "pedido-off-session-1");

    OriginationOutcome repeat = originateSale("150.00", "pedido-off-session-1");

    assertThat(repeat).isInstanceOf(OriginationOutcome.RefusedByTheProvider.class);
    assertThat(((OriginationOutcome.RefusedByTheProvider) repeat).inItsOwnWords())
        .contains(AutraErrors.ORDER_ALREADY_APPROVED);
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void an_action_naming_a_card_session_is_answered_no_such_object_because_it_is_not_a_payment() {
    configure(configuration(Map.of()));
    String sessionId = originatedCardSession(null);

    assertThat(
            provider.act(
                ObjectActionRequest.of(
                    RUN, sessionId, ObjectAction.CANCEL, null, ObjectOrigin.CONTROL_API)))
        .isEqualTo(new ObjectActionOutcome.NoSuchObject());
  }

  @Test
  void a_card_session_is_an_object_a_payer_finishes_and_names_the_account_and_the_operation() {
    configure(configuration(Map.of()));
    HostedSessionFacts.AnObjectAPayerFinishes session =
        theSessionFactsOf(originatedCardSession(null));

    assertThat(session.theAccountItBelongsTo()).isEqualTo(ACCOUNT);
    assertThat(session.theOperationItWasStartedFor()).isEqualTo(AutraOperations.CARD_SESSION);
  }

  @Test
  void a_card_session_is_created_when_the_run_stands_and_expires_fifteen_minutes_after_that() {
    configure(configuration(Map.of()));
    HostedSessionFacts.AnObjectAPayerFinishes session =
        theSessionFactsOf(originatedCardSession(null));

    assertThat(session.createdAt()).isEqualTo(NOW);
    assertThat(session.deadline())
        .isEqualTo(
            new HostedSessionFacts.Deadline.ItExpiresAt(
                NOW.plus(AutraCardSession.THE_PUBLISHED_TTL)));
  }

  @Test
  void a_card_session_names_no_party_and_carries_autras_own_words_for_that_absence() {
    configure(configuration(Map.of()));
    HostedSessionFacts.AnObjectAPayerFinishes session =
        theSessionFactsOf(originatedCardSession(null));

    assertThat(session.aPartyIsNamed()).isFalse();
    assertThat(session.thePartyAsItReads()).isEqualTo(AutraOperations.NO_CUSTOMER);
  }

  @Test
  void a_pix_is_an_object_a_payer_finishes_and_names_the_checkout_it_was_started_for() {
    configure(configuration(Map.of()));
    HostedSessionFacts.AnObjectAPayerFinishes session =
        theSessionFactsOf(originatedPix("99.90", null, null));

    assertThat(session.theAccountItBelongsTo()).isEqualTo(ACCOUNT);
    assertThat(session.theOperationItWasStartedFor()).isEqualTo(AutraOperations.PIX_CHECKOUT);
    assertThat(session.createdAt()).isEqualTo(NOW);
    assertThat(session.deadline())
        .isEqualTo(
            new HostedSessionFacts.Deadline.ItExpiresAt(
                NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR)));
  }

  @Test
  void a_pix_names_no_party_and_carries_autras_own_words_for_that_absence() {
    configure(configuration(Map.of()));
    HostedSessionFacts.AnObjectAPayerFinishes session =
        theSessionFactsOf(originatedPix("99.90", null, null));

    assertThat(session.aPartyIsNamed()).isFalse();
    assertThat(session.thePartyAsItReads()).isEqualTo(AutraOperations.NO_CUSTOMER);
  }

  @Test
  void a_payment_of_this_provider_is_not_an_object_a_payer_finishes() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    assertThat(provider.state(RUN, paymentId).orElseThrow().hostedSession())
        .isEqualTo(new HostedSessionFacts.NotAnObjectAPayerFinishes());
  }

  private HostedSessionFacts.AnObjectAPayerFinishes theSessionFactsOf(String providerObjectId) {
    return (HostedSessionFacts.AnObjectAPayerFinishes)
        provider.state(RUN, providerObjectId).orElseThrow().hostedSession();
  }

  @Test
  void the_object_list_carries_all_three_kinds_of_this_provider_newest_first() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();
    String pixId = originatedPix("99.90", null, null);
    String sessionId = originatedCardSession(null);

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(sessionId, pixId, paymentId);
  }

  @Test
  void every_object_of_the_list_says_which_of_this_providers_three_kinds_of_thing_it_is() {
    configure(configuration(Map.of()));
    originate("500.00", null);
    originatedPix("99.90", null, null);
    originatedCardSession(null);

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(object -> object.kind().asThisProviderNamesIt())
        .containsExactly("a hosted card page", "a Pix", "a hold on a card");
  }

  @Test
  void a_cursor_naming_a_card_session_walks_back_from_it_rather_than_reporting_no_such_cursor() {
    configure(configuration(Map.of()));
    String pixId = originatedPix("99.90", null, null);
    String sessionId = originatedCardSession(null);

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, sessionId, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(pixId);
  }

  @Test
  void a_cursor_naming_nothing_of_any_of_the_three_stores_is_answered_no_such_cursor() {
    configure(configuration(Map.of()));

    assertThat(provider.objects(new ProviderObjectQuery(RUN, "nothing-of-this-run", 10)))
        .isEqualTo(new ProviderObjectPage.NoSuchCursor());
  }

  @Test
  void
      a_card_session_offers_a_payer_step_that_names_cards_where_a_pix_offers_one_that_names_none() {
    configure(configuration(Map.of()));
    String sessionId = originatedCardSession(null);
    String pixId = originatedPix("99.90", null, null);

    assertThat(provider.hostedCompletion(RUN, sessionId))
        .isInstanceOf(HostedCompletionOffer.Offered.class);
    assertThat(provider.hostedCompletion(RUN, pixId))
        .isInstanceOf(HostedCompletionOffer.OfferedWithoutACard.class);
  }

  @Test
  void a_payer_step_on_a_card_session_tokenizes_the_card_and_queues_no_delivery_at_all() {
    configure(configuration(Map.of()));
    accounts.callingBack(RUN, ACCOUNT, A_CALLBACK_TARGET);
    String sessionId = originatedCardSession(null);

    HostedCompletionOutcome outcome =
        provider.complete(
            new HostedCompletionRequest(
                RUN,
                sessionId,
                PayerOutcome.PAID,
                AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS,
                ObjectOrigin.CONTROL_API));

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.Completed.class);
    assertThat(((HostedCompletionOutcome.Completed) outcome).webhookJobIds()).isEmpty();
    assertThat(callbackDeliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_callback_asked_for_a_card_session_names_what_this_provider_sends_instead_of_one() {
    configure(configuration(Map.of()));
    String sessionId = originatedCardSession(null);

    WebhookScheduleRequest namingACardSession =
        new WebhookScheduleRequest(RUN, sessionId, null, null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> provider.schedule(namingACardSession))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(AutraCallbacks.ONLY_A_PIX_HAS_A_CALLBACK);
  }

  @Test
  void a_reset_of_one_run_counts_every_store_it_cleared_and_leaves_another_run_holding_its_own() {
    everythingOneRunCanHold();

    ResetOutcome outcome = provider.reset(RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects())
        .isEqualTo(
            TWO_PAYMENTS
                + THREE_PIX
                + TWO_CARD_SESSIONS
                + ONE_DELIVERY
                + FIVE_SESSIONS
                + ONE_FIXTURE
                + ONE_RULE);
    assertThat(outcome.clearedAuditRecords())
        .isEqualTo(ONE_AUDIT_RECORD + ONE_DELIVERY_ATTEMPT_RECORD);
    assertThat(payments.all()).extracting(AutraPayment::testRunId).containsExactly(ANOTHER_RUN);
    assertThat(pixPayments.newestFirst(RUN, null, 10)).isEmpty();
    assertThat(cardSessions.newestFirst(RUN, null, 10)).isEmpty();
    assertThat(callbackDeliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
    assertThat(sessions.all()).isEmpty();
    assertThat(heldResponses.theRunsItWasAskedToCancel()).containsExactly(RUN);
    assertThat(heldResponses.howOftenItWasAskedToCancelEverything()).isZero();
  }

  @Test
  void a_reset_of_everything_counts_the_payment_of_the_other_run_among_what_it_cleared() {
    everythingOneRunCanHold();

    ResetOutcome outcome = provider.reset(RUN, ResetScope.ALL);

    assertThat(outcome.clearedObjects())
        .isEqualTo(
            TWO_PAYMENTS
                + ONE_PAYMENT_ELSEWHERE
                + THREE_PIX
                + TWO_CARD_SESSIONS
                + ONE_DELIVERY
                + FIVE_SESSIONS
                + ONE_FIXTURE
                + ONE_RULE);
    assertThat(outcome.clearedAuditRecords())
        .isEqualTo(ONE_AUDIT_RECORD + ONE_DELIVERY_ATTEMPT_RECORD);
    assertThat(payments.all()).isEmpty();
    assertThat(pixPayments.newestFirst(RUN, null, 10)).isEmpty();
    assertThat(cardSessions.newestFirst(RUN, null, 10)).isEmpty();
    assertThat(callbackDeliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
    assertThat(sessions.all()).isEmpty();
    assertThat(heldResponses.howOftenItWasAskedToCancelEverything()).isOne();
    assertThat(heldResponses.theRunsItWasAskedToCancel()).isEmpty();
  }

  private void everythingOneRunCanHold() {
    configure(configuration(Map.of()));
    accounts.callingBack(RUN, ACCOUNT, A_CALLBACK_TARGET);
    originate("100.00", null);
    originate("200.00", null);
    originatePix("10.00", null, null);
    originatePix("20.00", "Pedido #1234", null);
    String paid = originatedPix("30.00", null, "pedido-abc-123");
    provider.complete(
        new HostedCompletionRequest(RUN, paid, PayerOutcome.PAID, null, ObjectOrigin.CONTROL_API));
    String tokenized = originatedCardSession(null);
    provider.complete(
        new HostedCompletionRequest(
            RUN,
            tokenized,
            PayerOutcome.PAID,
            AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS,
            ObjectOrigin.CONTROL_API));
    originatedCardSession("Personal Visa");
    payments.holding(aReservationOf(ANOTHER_RUN, "660e8400-e29b-41d4-a716-446655440001", null));
    fixtures.seed(
        new NewPaymentMethodFixture(
            AutraApi.AUTRA,
            RUN,
            ACCOUNT,
            "owner-1",
            PaymentMethodKind.PSP_TOKEN,
            "a-card",
            null,
            Map.of(),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.CAPTURE),
            new ScenarioAction.FixedSuccess(),
            null));
    audit.record(anInboundRowOf(RUN));
  }

  private static AuditRecord anInboundRowOf(TestRunId run) {
    return new AuditRecord(
        "aud_000000000000000000000001",
        run,
        null,
        AutraApi.AUTRA,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        null,
        "POST",
        AutraOperations.INITIALIZE,
        null,
        null,
        null,
        null,
        1,
        NOW,
        Map.of(),
        Map.of(),
        null);
  }

  @Test
  void the_keyed_exchange_page_reports_the_orders_this_run_reserved_under() {
    configure(configuration(Map.of()));
    originate("500.00", "pedido-abc-123");

    assertThat(
            provider
                .keyedExchanges(new KeyedExchangeQuery(RUN, KeyedExchangeQuery.FROM_THE_NEWEST, 10))
                .exchanges())
        .singleElement()
        .satisfies(
            exchange -> {
              assertThat(exchange.keyName()).isEqualTo(AutraKeyMechanisms.ORDER_ID);
              assertThat(exchange.keyValue()).isEqualTo("pedido-abc-123");
            });
  }

  @ParameterizedTest
  @EnumSource(ScenarioPreset.class)
  void every_published_preset_either_expands_into_rules_or_says_what_this_contract_lacks(
      ScenarioPreset preset) {
    PresetExpansion expansion =
        provider.expand(new PresetRequest(preset, RUN, 10, ScenarioMatch.anyRequest()));

    if (expansion instanceof PresetExpansion.Rules expanded) {
      assertThat(provider.vocabulary().refusals(expanded.rules(), List.of())).isEmpty();
      return;
    }
    assertThat(((PresetExpansion.NotExpressible) expansion).reason()).isNotBlank();
  }

  @Test
  void the_preset_that_delays_a_callback_shapes_every_event_and_the_one_that_reorders_names_one() {
    NewScenarioRule delayed = theOneRuleOf(ScenarioPreset.WEBHOOK_DELAYED);
    NewScenarioRule duplicated = theOneRuleOf(ScenarioPreset.WEBHOOK_DUPLICATE);
    NewScenarioRule reordered = theOneRuleOf(ScenarioPreset.WEBHOOK_OUT_OF_ORDER);

    assertThat(delayed.match().eventType()).isNull();
    assertThat(duplicated.match().eventType()).isNull();
    assertThat(reordered.match().eventType())
        .isEqualTo(AutraPixEvent.FINANCIAL_TRANSACTION.eventType());
    assertThat(delayed.action())
        .isEqualTo(new ScenarioAction.DeliveryDelayed(AutraPresets.HELD_BACK_BY));
    assertThat(duplicated.action()).isEqualTo(new ScenarioAction.DeliveryDuplicated());
    assertThat(reordered.action())
        .isEqualTo(new ScenarioAction.DeliveryDelayed(AutraPresets.HELD_BACK_BY));
  }

  private NewScenarioRule theOneRuleOf(ScenarioPreset preset) {
    PresetExpansion.Rules expanded =
        (PresetExpansion.Rules)
            provider.expand(new PresetRequest(preset, RUN, 10, ScenarioMatch.anyRequest()));
    assertThat(expanded.rules()).hasSize(1);
    return expanded.rules().get(0);
  }

  @Test
  void the_five_hundred_then_success_preset_leaves_two_rules_on_every_operation_it_names() {
    PresetExpansion expanded =
        provider.expand(
            new PresetRequest(
                ScenarioPreset.PSP_5XX_THEN_SUCCESS, RUN, 10, ScenarioMatch.anyRequest()));

    List<NewScenarioRule> expandedRules = ((PresetExpansion.Rules) expanded).rules();
    assertThat(expandedRules)
        .as("every operation gets the fault that replaces it and the success that follows")
        .hasSize(2 * AutraOperations.VOCABULARY.operationNames().size());
    assertThat(expandedRules)
        .filteredOn(rule -> rule.action() instanceof ScenarioAction.FixedSuccess)
        .as("the second step of the story is what makes the repeat succeed")
        .hasSize(AutraOperations.VOCABULARY.operationNames().size());
  }

  @Test
  void
      the_presets_this_contract_cannot_express_are_the_callback_the_signature_and_the_lookup_ones() {
    assertThat(theUnexpressiblePresets())
        .containsExactlyInAnyOrder(
            ScenarioPreset.CARD_ADD_CALLBACK_DELAYED,
            ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS,
            ScenarioPreset.WEBHOOK_INVALID_SIGNATURE,
            ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_SUCCESS,
            ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_FAILED);
  }

  private List<ScenarioPreset> theUnexpressiblePresets() {
    List<ScenarioPreset> unexpressible = new ArrayList<>();
    for (ScenarioPreset preset : ScenarioPreset.values()) {
      if (provider.expand(new PresetRequest(preset, RUN, 10, ScenarioMatch.anyRequest()))
          instanceof PresetExpansion.NotExpressible) {
        unexpressible.add(preset);
      }
    }
    return unexpressible;
  }

  @Test
  void a_payment_started_while_a_fault_replaces_the_operation_reports_the_rule_that_answered() {
    configure(configuration(Map.of()));
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.PRE_AUTHORIZATION),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    OriginationOutcome outcome = originate("500.00", null);

    assertThat(outcome).isInstanceOf(OriginationOutcome.AnsweredByAScenarioInstead.class);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void an_action_a_fault_replaced_the_operation_of_reports_the_rule_that_answered_instead() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.CAPTURE),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    ObjectActionOutcome outcome =
        provider.act(
            ObjectActionRequest.of(
                RUN, paymentId, ObjectAction.CAPTURE, "480.00", ObjectOrigin.CONTROL_API));

    assertThat(outcome).isInstanceOf(ObjectActionOutcome.AnsweredByAScenarioInstead.class);
    assertThat(provider.state(RUN, paymentId).orElseThrow().normalizedStatus())
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_payment_that_named_a_stored_card_reports_that_card_among_the_objects_it_names() {
    payments.holding(aReservationPaidWith("slug-token-abc"));

    assertThat(
            provider
                .state(RUN, "550e8400-e29b-41d4-a716-446655440000")
                .orElseThrow()
                .relatedObjects())
        .containsEntry(AutraFields.SLUG_TOKEN, "slug-token-abc");
  }

  @Test
  void a_payment_names_what_the_two_read_routes_publish_about_it_among_its_related_objects() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .containsEntry(AutraFields.TYPE, AutraTimeline.PREAUTH)
        .containsEntry(AutraFields.TRANSACTION_TYPE, AutraFields.CREDIT)
        .containsEntry(AutraFields.TRANSACTION_METHOD, AutraFields.CARD_METHOD)
        .containsEntry(AutraFields.REFUNDED_AMOUNT, "0")
        .containsEntry(AutraFields.VOID_RETRYABLE, "true");
  }

  @Test
  void a_payment_names_each_step_of_its_timeline_among_its_related_objects() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .containsEntry("events.1", "PREAUTH ACCEPTED 500.00 2026-09-22T10:00:00Z");
  }

  @Test
  void a_step_that_was_refused_names_the_code_and_the_message_it_was_refused_with() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();
    payments.recordEvent(
        AutraPaymentEvent.refused(
            RUN,
            paymentId,
            AutraTimeline.VOID,
            AutraStatus.ACCEPTED,
            Amount.of("500.00"),
            AutraErrors.CANT_REFUND_TRANSACTION_FROM_TODAY,
            "not today",
            NOW));

    assertThat(provider.state(RUN, paymentId).orElseThrow().relatedObjects())
        .containsEntry(
            "events.2",
            "VOID "
                + AutraStatus.ACCEPTED
                + " 500.00 2026-09-22T10:00:00Z "
                + AutraErrors.CANT_REFUND_TRANSACTION_FROM_TODAY
                + " not today");
  }

  @Test
  void every_fact_the_read_back_screen_names_is_one_a_payment_of_this_provider_answers() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();
    Map<String, String> answered = provider.state(RUN, paymentId).orElseThrow().relatedObjects();

    List<String> namedByNoPayment = new ArrayList<>();
    for (String named : theFactsTheReadBackScreenPrintsUnguarded()) {
      if (!answered.containsKey(named)) {
        namedByNoPayment.add(named);
      }
    }

    assertThat(namedByNoPayment)
        .as(
            "the screen prints these out of relatedObjects by name and asks nothing about whether"
                + " they are there, so a name the provider stops answering leaves a blank cell on"
                + " the screen and no gate anywhere else sees it")
        .isEmpty();
    assertThat(theFactsTheReadBackScreenPrintsUnguarded())
        .contains(
            AutraFields.TYPE,
            AutraFields.RRN,
            AutraFields.VOID_RETRYABLE,
            AutraFields.REFUNDED_AMOUNT,
            AutraFields.TRANSACTION_METHOD)
        .doesNotContain(AutraFields.ORDER_ID);
  }

  private static List<String> theFactsTheReadBackScreenPrintsUnguarded() {
    String screen = theReadBackScreenThisBuildShips();
    Set<String> printed = theKeysNamedBy("get", screen);
    printed.removeAll(theKeysNamedBy("containsKey", screen));
    return List.copyOf(printed);
  }

  private static Set<String> theKeysNamedBy(String reader, String screen) {
    Matcher named =
        Pattern.compile("relatedObjects\\." + reader + "\\('([^']+)'\\)").matcher(screen);
    Set<String> keys = new LinkedHashSet<>();
    while (named.find()) {
      keys.add(named.group(1));
    }
    return keys;
  }

  private static String theReadBackScreenThisBuildShips() {
    try {
      return Files.readString(
          Path.of(
              "src", "main", "resources", "templates", "extensions", "autra", "read-back.html"));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  @Test
  void this_run_reports_no_state_for_a_payment_it_does_not_hold() {
    assertThat(provider.state(RUN, "no-such-payment")).isEmpty();
  }

  @Test
  void forgetting_a_fixture_of_this_provider_removes_no_row_because_it_stores_none_of_its_own() {
    fixtures.clearAll(AutraApi.AUTRA);

    provider
        .forgetFixture(
            new PaymentMethodFixture(
                "fixture-1",
                AutraApi.AUTRA,
                RUN,
                ACCOUNT,
                "owner-1",
                PaymentMethodKind.PSP_TOKEN,
                "a-card",
                null,
                Map.of(),
                null,
                NOW))
        .remove();

    assertThat(payments.all()).isEmpty();
  }

  @Test
  void completing_a_page_of_an_object_this_run_does_not_hold_reports_no_such_object() {
    assertThat(
            provider.complete(
                new HostedCompletionRequest(
                    RUN, "any", PayerOutcome.PAID, null, ObjectOrigin.CONTROL_API)))
        .isEqualTo(new HostedCompletionOutcome.NoSuchObject());
  }

  private static AutraPayment aReservationPaidWith(String slugToken) {
    return aReservationOf(RUN, "550e8400-e29b-41d4-a716-446655440000", slugToken);
  }

  private static AutraPayment aReservationOf(TestRunId run, String paymentId, String slugToken) {
    return new AutraPayment(
        run,
        paymentId,
        ACCOUNT,
        DOCUMENT,
        null,
        AutraTimeline.PREAUTH,
        AutraFields.CREDIT,
        Amount.of("500.00"),
        "BRL",
        1,
        AutraStatus.ACCEPTED,
        "A1B2C3",
        "112676199769",
        "900123456789",
        slugToken,
        null,
        Ledger.empty(),
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private OriginationOutcome originatePix(String amount, String description, String orderId) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, amount);
    values.put(AutraFields.CURRENCY, "BRL");
    if (description != null) {
      values.put(AutraFields.DESCRIPTION, description);
    }
    if (orderId != null) {
      values.put(AutraFields.ORDER_ID, orderId);
    }
    return provider.originate(
        OriginationRequest.of(
            RUN, ACCOUNT, AutraOrigination.A_PIX, values, ObjectOrigin.CONTROL_API));
  }

  @Test
  void a_pix_its_payer_paid_offers_the_settlement_and_nothing_else_this_provider_holds_does() {
    configure(configuration(Map.of()));
    String pixId = aPaidPix();
    String waiting = originatedPix("10.00", null, null);
    String cardSession = originatedCardSession(null);

    assertThat(provider.followUps(RUN, pixId, NOW))
        .containsExactly(SettlePixPayment.THE_SETTLEMENT);
    assertThat(provider.followUps(RUN, waiting, NOW)).isEmpty();
    assertThat(provider.followUps(RUN, cardSession, NOW)).isEmpty();
  }

  @Test
  void a_settlement_through_this_provider_reads_back_as_settled_and_still_as_paid() {
    configure(configuration(Map.of()));
    accounts.callingBack(RUN, ACCOUNT, A_CALLBACK_TARGET);
    String pixId = aPaidPix();

    List<ScheduledWebhook> jobs = provider.followUp(settle(pixId), NOW);

    ProviderObjectState settled = provider.state(RUN, pixId).orElseThrow();
    assertThat(settled.nativeStatus()).isEqualTo(AutraStatus.SETTLED);
    assertThat(settled.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(jobs).hasSize(1);
    assertThat(callbackDeliveries.of(AutraApi.AUTRA, RUN))
        .filteredOn(delivery -> delivery.deliveryId().equals(jobs.get(0).webhookJobId()))
        .extracting(WebhookDelivery::eventType)
        .containsExactly(AutraPixEvent.PAYOUT.eventType());
    assertThat(provider.followUps(RUN, pixId, NOW)).isEmpty();
  }

  @Test
  void a_card_session_offers_no_settlement_and_one_asked_directly_answers_no_pix_under_that_id() {
    configure(configuration(Map.of()));
    String cardSession = originatedCardSession(null);
    FollowUpRequest settle = settle(cardSession);

    assertThat(provider.followUps(RUN, cardSession, NOW)).isEmpty();
    assertThatThrownBy(() -> provider.followUp(settle, NOW))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(SettlePixPayment.THE_RUN_HOLDS_NO_SUCH_PIX);
  }

  private String aPaidPix() {
    String pixId = originatedPix("99.90", null, null);
    provider.complete(
        new HostedCompletionRequest(RUN, pixId, PayerOutcome.PAID, null, ObjectOrigin.CONTROL_API));
    return pixId;
  }

  private static FollowUpRequest settle(String providerObjectId) {
    return FollowUpRequest.of(
        RUN, providerObjectId, SettlePixPayment.SETTLE, ObjectOrigin.CONTROL_API);
  }

  private String originatedPix(String amount, String description, String orderId) {
    return ((OriginationOutcome.Originated) originatePix(amount, description, orderId))
        .providerObjectId();
  }

  private OriginationOutcome originateCardSession(@Nullable String nickname) {
    Map<String, String> values = new LinkedHashMap<>();
    if (nickname != null) {
      values.put(AutraFields.NICKNAME, nickname);
    }
    return provider.originate(
        OriginationRequest.of(
            RUN, ACCOUNT, AutraOrigination.A_CARD_SESSION, values, ObjectOrigin.CONTROL_API));
  }

  @Test
  void every_object_autra_reports_keeps_the_one_identifier_it_was_created_under() {
    configure(configuration(Map.of()));
    String pixId = originatedPix("99.90", "Pedido #1234", "pedido-abc-123");

    TheSingleIdentifierFloor.holdsFor(provider, RUN, pixId);
    TheSingleIdentifierFloor.holdsForEveryObjectOf(provider, RUN);
  }

  private String originatedCardSession(@Nullable String nickname) {
    return ((OriginationOutcome.Originated) originateCardSession(nickname)).providerObjectId();
  }

  private OriginationOutcome originateSale(String amount, @Nullable String orderId) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, amount);
    values.put(AutraFields.CURRENCY, "BRL");
    values.put(AutraFields.TRANSACTION_TYPE, AutraFields.CREDIT);
    values.put(AutraFields.INSTALLMENTS, "1");
    values.put(AutraFields.SLUG_TOKEN, "slug-token-abc");
    values.put(AutraFields.SLUG_STORED_CARD, "slug-stored-card-xyz");
    if (orderId != null) {
      values.put(AutraFields.ORDER_ID, orderId);
    }
    return provider.originate(
        OriginationRequest.of(
            RUN, ACCOUNT, AutraOrigination.A_SALE, values, ObjectOrigin.CONTROL_API));
  }

  private OriginationOutcome originate(String amount, String orderId) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, amount);
    values.put(AutraFields.CURRENCY, "BRL");
    if (orderId != null) {
      values.put(AutraFields.ORDER_ID, orderId);
    }
    return provider.originate(
        OriginationRequest.of(
            RUN, ACCOUNT, AutraOrigination.A_HOLD, values, ObjectOrigin.CONTROL_API));
  }

  private AbstractThrowableAssert<?, ?> assertThatRefuses(Map<String, String> overrides) {
    Map<String, String> declared = configuration(Map.of());
    declared.putAll(overrides);
    ProviderConfiguration configuration = configurationOf(new ConfiguredAccount(ACCOUNT, declared));
    return assertThatThrownBy(() -> provider.parse(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class);
  }

  @Test
  void the_pairing_autra_publishes_is_its_v1_path_the_token_exchange_and_its_three_credentials() {
    ProviderPairing pairing = provider.pairing();

    assertThat(pairing.provider()).isEqualTo(AutraApi.AUTRA);
    assertThat(pairing.apiBasePath()).isEqualTo("/v1");
    assertThat(pairing.settings())
        .extracting(PairingSetting::name)
        .containsExactly(
            "AUTRA_BASE_URL",
            "AUTRA_TOKEN_PATH",
            "AUTRA_CLIENT_ID",
            "AUTRA_CLIENT_SECRET",
            "AUTRA_DOCUMENT_ID",
            "SIMULATOR_ACCOUNT_ID",
            "SIMULATOR_TEST_RUN_HEADER",
            "SIMULATOR_TEST_RUN_ID");
    assertThat(pairing.accountFieldsItReads())
        .containsExactly("clientId", "clientSecret", "documentId");
    assertThat(pairing.howARequestNamesItsAccount())
        .contains("/v1/oauth/token?grant_type=client_credentials")
        .contains("Authorization: Basic base64(clientId:clientSecret)")
        .contains("Authorization: Bearer <accessToken>");
  }

  @Test
  void the_personas_autra_publishes_each_seed_the_pair_of_slugs_its_hosted_page_mints() {
    ProviderPersonas published = provider.personas(ids);

    assertThat(published.provider()).isEqualTo(AutraApi.AUTRA);
    assertThat(published.theNamesItPublishes())
        .containsExactly(
            "autra-br-card-payer-that-succeeds",
            "autra-br-card-payer-that-is-declined",
            "autra-br-card-payer-whose-capture-answer-is-lost");
    assertThat(published.intentsItPublishesNoneFor()).isEmpty();
    for (DeclaredPersona persona : published.personas()) {
      PersonaFixture instrument = persona.fixtures().get(0);
      assertThat(instrument.theProviderNativeKeysItCarries())
          .containsExactly("token", "paymentMethodId");
      Map<String, String> minted = instrument.mint();
      assertThat(minted.get("token")).matches("[0-9A-F]{16}");
      assertThat(minted.get("paymentMethodId")).matches("[0-9A-F]{16}");
      assertThat(minted.get("token")).isNotEqualTo(minted.get("paymentMethodId"));
      assertThat(instrument.card()).isNotNull();
      assertThat(instrument.card().brand()).isEqualTo("VISA");
      assertThat(instrument.card().last4()).isEqualTo("1111");
    }
  }

  @Test
  void every_persona_autra_publishes_writes_a_preset_this_module_can_expand() {
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
  void no_autra_persona_names_a_customer_because_this_contract_publishes_none() {
    List<DeclaredPersona> declared = provider.personas(ids).personas();

    assertThat(declared).hasSize(3);
    for (DeclaredPersona persona : declared) {
      assertThat(persona.fixtures().get(0).theProviderNativeKeysItCarries())
          .hasSize(2)
          .doesNotContain("customerId");
    }
  }

  @Test
  void the_compatibility_checks_autra_publishes_name_six_operations_and_withhold_none() {
    List<ContractCheck> checks = provider.compatibilityChecks(new CheckJson(), ids);

    assertThat(checks)
        .extracting(ContractCheck::operation, check -> check.provenance().sample())
        .containsExactly(
            tuple(AutraCompatibilityChecks.THE_TOKEN_IS_MINTED, "autra/oauth-token-ok"),
            tuple(
                AutraCompatibilityChecks.THE_CREDENTIAL_IS_REFUSED,
                "autra/oauth-invalid-client-secret"),
            tuple(AutraCompatibilityChecks.THE_SALE_IS_APPROVED, "autra/sale-approved"),
            tuple(AutraCompatibilityChecks.THE_SALE_IS_DECLINED, "autra/sale-declined"),
            tuple(AutraCompatibilityChecks.THE_CALLBACK_CARRIES, "autra/acquiring-webhook-headers"),
            tuple(AutraCompatibilityChecks.THE_TRAIL_RECORDS, "simulator/audit-record"));
    assertThat(checks)
        .filteredOn(check -> check.applicability() instanceof Applicability.DoesNotApply)
        .isEmpty();
    assertThat(checks).allSatisfy(check -> assertThat(check.expectations()).isNotEmpty());
  }

  @Test
  void the_callback_check_autra_publishes_asserts_the_header_and_agent_the_vendor_prints() {
    List<ContractCheck> checks = provider.compatibilityChecks(new CheckJson(), ids);

    assertThat(checks.get(4).expectations())
        .extracting(ContractExpectation::field, expectation -> expectation.published().value())
        .containsExactly(
            tuple("signature.prefix", "sha256="), tuple("userAgent", "Autra-Webhooks/1.0"));
  }

  @Test
  void declares_both_objects_a_payer_finishes_and_which_of_them_a_payer_names_a_card_on() {
    assertThat(provider.hostedPages())
        .isEqualTo(
            new HostedPageContract.ObjectsAPayerFinishes(
                List.of(
                    new HostedPageContract.AnObjectItKeepsWaiting("a card session", true),
                    new HostedPageContract.AnObjectItKeepsWaiting("a Pix", false))));
  }

  @Test
  void publishes_the_facts_of_both_objects_its_declaration_promises_and_of_no_payment_besides() {
    configure(configuration(Map.of()));
    String aPayment =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    assertThat(
            TheObjectsADeclarationPromises.of(
                provider,
                RUN,
                List.of(originatedCardSession(null), originatedPix("99.90", null, null), aPayment)))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }

  @Test
  void the_questions_a_tester_asks_this_module_are_the_ones_it_declares() {
    assertThat(provider.reads()).isEqualTo(AutraReads.DECLARED);
    assertThat(provider.reads().publishesAny())
        .as("a module declaring no read must say what it has instead, and this one has two")
        .isTrue();
  }

  @Test
  void a_question_asked_on_an_account_this_run_does_not_configure_is_answered_by_no_merchant() {
    assertThat(
            provider.read(
                ReadRequest.of(
                    RUN,
                    "merchant-nobody-configured",
                    AutraReads.ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS,
                    Map.of(
                        AutraFields.PAYMENT_ID_LOWERCASE_D, "550e8400-e29b-41d4-a716-446655440000"),
                    ObjectOrigin.UI)))
        .isInstanceOf(ReadOutcome.NoSuchAccount.class);
  }

  @Test
  void a_payment_this_run_took_is_read_back_through_the_module_under_the_question_it_names() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    ReadOutcome answered =
        provider.read(
            ReadRequest.of(
                RUN,
                ACCOUNT,
                AutraReads.ONE_PAYMENT_AND_THE_TIMELINE_OF_ITS_STEPS,
                Map.of(AutraFields.PAYMENT_ID_LOWERCASE_D, paymentId),
                ObjectOrigin.UI));

    assertThat(((ReadOutcome.Answered) answered).rows())
        .extracting(AnsweredRow::providerObjectId)
        .containsExactly(paymentId);
  }

  @Test
  void the_payments_this_run_took_are_what_the_identifier_of_the_look_up_offers_and_nothing_else() {
    configure(configuration(Map.of()));
    String paymentId =
        ((OriginationOutcome.Originated) originate("500.00", null)).providerObjectId();

    assertThat(
            provider.theValuesThisTestRunHasAlreadyProducedFor(
                RUN, ACCOUNT, AutraFields.PAYMENT_ID_LOWERCASE_D))
        .extracting(AValueAFieldOffers::value)
        .containsExactly(paymentId);
    assertThat(provider.theValuesThisTestRunHasAlreadyProducedFor(RUN, ACCOUNT, AutraFields.AMOUNT))
        .as("no other field of this module is drawn from what a run has produced")
        .isEmpty();
    assertThat(
            provider.theValuesThisTestRunHasAlreadyProducedFor(
                RUN, "merchant-nobody-configured", AutraFields.PAYMENT_ID_LOWERCASE_D))
        .as("an account this run does not configure names no establishment to list for")
        .isEmpty();
  }

  @ParameterizedTest(name = "reads {0} back as one line per rule")
  @EnumSource(ScenarioPreset.class)
  void every_ready_made_flow_reads_back_as_one_line_per_rule(ScenarioPreset preset) {
    APresetReadsBackInWords.assertItDoes(
        preset, provider.expand(new PresetRequest(preset, RUN, 0, ScenarioMatch.anyRequest())));
  }
}
