package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrowRetraceTokenPaymentTest {

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String NOTIFY_URL = "https://merchant.example/callbacks/grow";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String CHARGE = "/api/light/server/1.0/createTransactionWithToken";
  private static final String RETRACE =
      "/api/light/server/1.0/getTokenTransactionsByExternalIdentifiers";

  private static final String MY_OWN_IDENTIFIER = "1001";

  private static final String A_RETRACE_THAT_WAS_ASKED_ONCE = "a-retrace-asked-once";

  private static final String RETRACED_ONCE = "RetracedOnce";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(DEFAULT_RUN);
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final SimulatorClock clock = SimulatorDoubles.clockAt(NOW);

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          clock,
          ids,
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new GrowCallbackAcknowledgement(accounts)),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final GrowLightApi api =
      new GrowLightApi(
          accounts,
          testRuns,
          audit,
          clock,
          ids,
          DEFAULT_RUN,
          new CreatePaymentProcess(
              processes, ids, "https://simulator.internal", scenarioEngine, GrowScalarType.INTEGER),
          new CreateTransactionWithToken(
              processes, transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
          new ApproveTransaction(processes, dispatcher, scenarioEngine, GrowScalarType.INTEGER),
          new SettleSuspendedTransaction(transactions, scenarioEngine, GrowScalarType.INTEGER),
          new RefundTransaction(transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
          new GetTransactionInfo(transactions, processes, scenarioEngine, GrowScalarType.INTEGER),
          new GetPaymentProcessInfo(
              processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
          new GetTokenTransactionsByExternalIdentifiers(
              processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
          new CancelBitTransaction(GrowScalarType.INTEGER),
          new UpdateMyUrl(dispatcher, GrowScalarType.INTEGER));

  private final SimulatorProvider provider =
      new GrowProvider(
          accounts,
          audit,
          processes,
          transactions,
          dispatcher,
          SimulatorDoubles.callbacks(processes, transactions, accounts, dispatcher, clock, ids),
          clock,
          new InMemoryPaymentMethodFixtures(),
          scenarioRules,
          SimulatorDoubles.heldResponses(),
          new SettleSuspendedTransaction(
              transactions, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          new RefundTransaction(
              transactions, ids, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          SimulatorDoubles.paymentProcessCreation(processes, scenarioRules, ids),
          SimulatorDoubles.storedCardCharge(processes, transactions, ids, scenarioRules),
          SimulatorDoubles.retraceOfAStoredCardCharge(processes, transactions, scenarioRules),
          SimulatorDoubles.completions(processes));

  @BeforeEach
  void configureTheBusiness() {
    accounts.with(SimulatorDoubles.account(RUN, MERCHANT, USER_ID, PAGE_CODE, Map.of()));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, NOTIFY_URL));
    testRuns.register(RUN);
  }

  @Test
  void a_harness_retraces_its_charge_by_the_identifier_it_chose_and_reads_the_row_back() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    GrowApiResponse retraced = retrace(aRetraceOf(cardToken));

    assertThat(retraced.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(retraced.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(retraced))
        .isEqualTo(
            "{\"status\":1,\"err\":\"\",\"data\":{\"transactions\":[{"
                + "\"asmachta\":\"10000002\","
                + "\"cardSuffix\":\"4580\","
                + "\"cardType\":\"Foreign\","
                + "\"cardTypeCode\":\"2\","
                + "\"cardBrand\":\"Visa\","
                + "\"cardBrandCode\":\"3\","
                + "\"cardExp\":\"0424\","
                + "\"firstPaymentSum\":\"0\","
                + "\"periodicalPaymentSum\":\"0\","
                + "\"status\":\"שולם\","
                + "\"statusCode\":\"2\","
                + "\"transactionTypeId\":\"1\","
                + "\"paymentType\":\"2\","
                + "\"sum\":\"89.90\","
                + "\"paymentsNum\":\"0\","
                + "\"allPaymentsNum\":\"1\","
                + "\"paymentDate\":\"28/11/26\","
                + "\"description\":\"A stored-card charge\","
                + "\"fullName\":\"Israel Israeli\","
                + "\"payerPhone\":\"0505503144\","
                + "\"payerEmail\":\"payer@merchant.example\","
                + "\"transactionId\":\"100003\","
                + "\"transactionToken\":\"000000000000000002\""
                + "}]}}");
  }

  @Test
  void the_retraced_charge_is_the_one_the_harness_submitted() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    GrowApiResponse retraced = retrace(aRetraceOf(cardToken));

    assertThat(retraced.providerObjectId()).isEqualTo(theOnlyStoredCardCharge().transactionId());
  }

  @Test
  void a_charge_that_never_reached_the_provider_retraces_as_no_transaction_at_all() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    GrowApiResponse retraced =
        retrace(aRetraceOf(cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "2002")));

    assertThat(retraced.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(retraced.providerObjectId()).isNull();
    assertThat(bodyOf(retraced))
        .isEqualTo("{\"status\":1,\"err\":\"\",\"data\":{\"transactions\":[]}}");
  }

  @Test
  void a_declined_charge_moved_no_money_and_retraces_as_no_transaction_at_all() {
    String cardToken = aSavedCard();
    declineEveryCharge();
    GrowApiResponse declined = charge(aChargeOf(cardToken));
    assertThat(declined.outcome()).isEqualTo(ProviderOutcome.FAILURE);

    assertThat(bodyOf(retrace(aRetraceOf(cardToken))))
        .isEqualTo("{\"status\":1,\"err\":\"\",\"data\":{\"transactions\":[]}}");
  }

  @Test
  void a_group_identifier_the_charge_was_not_submitted_under_retraces_nothing() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_GROUP_IDENTIFIER, "batch-7")));

    GrowApiResponse retraced =
        retrace(aRetraceOf(cardToken, Map.of(GrowFields.TRANSACTION_GROUP_IDENTIFIER, "batch-8")));

    assertThat(bodyOf(retraced))
        .isEqualTo("{\"status\":1,\"err\":\"\",\"data\":{\"transactions\":[]}}");
  }

  @Test
  void the_group_identifier_the_charge_was_submitted_under_retraces_it() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_GROUP_IDENTIFIER, "batch-7")));

    GrowApiResponse retraced =
        retrace(aRetraceOf(cardToken, Map.of(GrowFields.TRANSACTION_GROUP_IDENTIFIER, "batch-7")));

    assertThat(retraced.providerObjectId()).isEqualTo(theOnlyStoredCardCharge().transactionId());
  }

  @Test
  void answers_880_for_a_user_id_no_business_of_this_test_run_matches() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    GrowApiResponse retraced =
        retrace(aRetraceOf(cardToken, Map.of(GrowFields.USER_ID, "not-a-configured-user")));

    assertThat(retraced.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(retraced))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":880,"
                + "\"message\":\"לא נמצא עסק מתאים לUserId\"},\"data\":\"\"}");
  }

  @Test
  void answers_140_for_a_card_token_this_business_never_saved() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    GrowApiResponse retraced =
        retrace(aRetraceOf(cardToken, Map.of(GrowFields.CARD_TOKEN, "a-card-nobody-saved")));

    assertThat(bodyOf(retraced)).contains("\"id\":140");
  }

  @ParameterizedTest(name = "answers 54 naming the missing {0}")
  @ValueSource(strings = {"userId", "cardToken"})
  void names_the_missing_required_field(String field) {
    String cardToken = aSavedCard();
    Map<String, String> without = aRetraceOf(cardToken);
    without.remove(field);

    assertThat(bodyOf(retrace(without)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,"
                + "\"message\":\"חסרים נתונים:"
                + field
                + "\"},\"data\":\"\"}");
  }

  @Test
  void answers_104_when_the_caller_names_no_identifier_of_its_own() {
    String cardToken = aSavedCard();
    Map<String, String> without = aRetraceOf(cardToken);
    without.remove(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER);

    assertThat(bodyOf(retrace(without))).contains("\"id\":104");
  }

  @Test
  void answers_1013_for_an_identifier_grow_cannot_hold() {
    String cardToken = aSavedCard();

    GrowApiResponse retraced =
        retrace(
            aRetraceOf(cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "order-1001")));

    assertThat(bodyOf(retraced)).contains("\"id\":1013");
  }

  @Test
  void retraces_under_the_widest_identifier_grow_can_hold_in_thirty_two_bits() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "4294967295")));

    GrowApiResponse retraced =
        retrace(
            aRetraceOf(cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "4294967295")));

    assertThat(retraced.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(retraced.providerObjectId()).isEqualTo(theOnlyStoredCardCharge().transactionId());
  }

  @Test
  void answers_1013_for_an_identifier_one_past_the_widest_grow_can_hold() {
    String cardToken = aSavedCard();

    GrowApiResponse retraced =
        retrace(
            aRetraceOf(cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "4294967296")));

    assertThat(retraced.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(retraced)).contains("\"id\":1013");
  }

  @Test
  void a_tester_can_make_the_retrace_fail_the_way_a_scenario_names() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));
    failEveryRetraceWith("715");

    assertThat(bodyOf(retrace(aRetraceOf(cardToken)))).contains("\"id\":715");
  }

  @Test
  void audits_the_retrace_as_an_inbound_request_and_an_outbound_response() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    retrace(aRetraceOf(cardToken));

    assertThat(audit.all().stream().filter(r -> r.path().equals(RETRACE)).toList())
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
  }

  private void declineEveryCharge() {
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(GrowOperations.CREATE_TRANSACTION_WITH_TOKEN),
            new ScenarioAction.TerminalFailure("103"),
            null));
  }

  @Test
  void asking_this_question_twice_does_not_leave_the_test_run_as_it_was_when_a_story_decides_it() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));
    aStoryThatRefusesTheFirstRetraceAndAnswersTheSecond();

    ReadOutcome first = provider.read(theQuestionAskedWith(cardToken, MY_OWN_IDENTIFIER));
    ReadOutcome second = provider.read(theQuestionAskedWith(cardToken, MY_OWN_IDENTIFIER));

    assertThat(first)
        .as(
            "grow names this read among the operations a rule of a test run may be declared on,"
                + " and a rule carrying a story moves that story on when it decides the read, so"
                + " the second ask of one unchanged question meets the step after the first")
        .isInstanceOf(ReadOutcome.RefusedByTheProvider.class);
    assertThat(second).isInstanceOf(ReadOutcome.Answered.class);
    assertThat(scenarioRules.storyStatesOf(GrowLightApi.GROW, RUN, theStoryOfTheOnlyCharge()))
        .as("the state the run holds for this story is the one the first ask moved it to")
        .containsEntry(A_RETRACE_THAT_WAS_ASKED_ONCE, RETRACED_ONCE);
  }

  private void aStoryThatRefusesTheFirstRetraceAndAnswersTheSecond() {
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest()
                .naming(GrowOperations.GET_TOKEN_TRANSACTIONS_BY_EXTERNAL_IDENTIFIERS),
            new ScenarioAction.TerminalFailure("813"),
            new ScenarioStory(
                A_RETRACE_THAT_WAS_ASKED_ONCE,
                StoryKeying.FLOW,
                ScenarioStory.INITIAL_STATE,
                RETRACED_ONCE)));
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest()
                .naming(GrowOperations.GET_TOKEN_TRANSACTIONS_BY_EXTERNAL_IDENTIFIERS),
            new ScenarioAction.FixedSuccess(),
            new ScenarioStory(
                A_RETRACE_THAT_WAS_ASKED_ONCE, StoryKeying.FLOW, RETRACED_ONCE, null)));
  }

  private StoryKey theStoryOfTheOnlyCharge() {
    return new StoryKey(StoryKeying.FLOW, GrowStoryKey.ofTransaction(theOnlyStoredCardCharge()));
  }

  private void failEveryRetraceWith(String providerCode) {
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest()
                .naming(GrowOperations.GET_TOKEN_TRANSACTIONS_BY_EXTERNAL_IDENTIFIERS),
            new ScenarioAction.TerminalFailure(providerCode),
            null));
  }

  private GrowTransaction theOnlyStoredCardCharge() {
    List<GrowTransaction> charges =
        transactions.all().stream()
            .filter(t -> t.kind() == GrowTransactionKind.TOKEN_PAYMENT)
            .toList();
    assertThat(charges).hasSize(1);
    return charges.get(0);
  }

  @Test
  void
      the_read_this_provider_publishes_asks_for_the_card_this_run_saved_and_the_merchants_own_id() {
    DeclaredRead published =
        provider.reads().readNamed(GrowReads.A_CHARGE_ON_A_SAVED_CARD).orElseThrow();

    assertThat(published.keys())
        .as(
            "a tester asks grow this question with the two identifiers a merchant sent itself and"
                + " with no identifier grow gave back, because a call that timed out carries none")
        .containsExactly(
            GrowFields.CARD_TOKEN,
            GrowFields.TRANSACTION_UNIQUE_IDENTIFIER,
            GrowFields.TRANSACTION_GROUP_IDENTIFIER);
    assertThat(published.field(GrowFields.CARD_TOKEN).orElseThrow())
        .as(
            "the cards this run minted are what the box suggests, and grow is what resolves the"
                + " token a tester sends, so a token this run never minted still reaches grow and"
                + " is answered err.id 140 by grow")
        .satisfies(
            field -> {
              assertThat(field.namesAValueThisTestRunHasAlreadyProduced()).isTrue();
              assertThat(field.required()).isTrue();
            });
    assertThat(published.field(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER).orElseThrow().required())
        .isTrue();
    assertThat(published.field(GrowFields.TRANSACTION_GROUP_IDENTIFIER).orElseThrow().required())
        .as("grow reads the group identifier when one is sent and answers the charge without one")
        .isFalse();
  }

  @Test
  void the_card_a_hosted_page_saved_is_offered_for_the_field_the_read_picks_it_from() {
    String cardToken = aSavedCard();

    assertThat(
            provider.theValuesThisTestRunHasAlreadyProducedFor(
                RUN, MERCHANT, GrowFields.CARD_TOKEN))
        .as(
            "the retrace and the stored-card charge both pick the card out of this list, so an"
                + " empty one is a token a tester would have to type and grow mints nowhere else")
        .extracting(AValueAFieldOffers::value)
        .containsExactly(cardToken);
  }

  @Test
  void a_field_grow_mints_no_value_for_is_offered_nothing_rather_than_the_cards_it_saved() {
    aSavedCard();

    assertThat(
            provider.theValuesThisTestRunHasAlreadyProducedFor(
                RUN, MERCHANT, GrowFields.TRANSACTION_UNIQUE_IDENTIFIER))
        .as("the identifiers a merchant sent itself are the merchant's and not grow's to offer")
        .isEmpty();
  }

  @Test
  void a_tester_asking_the_read_by_its_own_identifier_is_answered_the_charge_as_facts() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    ReadOutcome answered = provider.read(theQuestionAskedWith(cardToken, MY_OWN_IDENTIFIER));

    assertThat(answered)
        .asInstanceOf(InstanceOfAssertFactories.type(ReadOutcome.Answered.class))
        .satisfies(
            found -> {
              assertThat(found.rows()).hasSize(1);
              AnsweredRow row = found.rows().get(0);
              assertThat(row.providerObjectId())
                  .isEqualTo(theOnlyStoredCardCharge().transactionId());
              assertThat(row.facts())
                  .as("every member grow answers on the row is a fact the screen can print")
                  .contains(
                      new AnsweredRow.Fact(GrowResponseFields.SUM, "89.90"),
                      new AnsweredRow.Fact(GrowResponseFields.CARD_SUFFIX, "4580"),
                      new AnsweredRow.Fact(GrowResponseFields.DESCRIPTION, "A stored-card charge"));
            });
  }

  @Test
  void a_tester_asking_the_read_with_an_identifier_this_business_never_sent_is_answered_nothing() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    ReadOutcome answered = provider.read(theQuestionAskedWith(cardToken, "2002"));

    assertThat(answered)
        .as(
            "an empty answer is grow's answer and the one a merchant whose charge never arrived"
                + " reads, so the screen is handed no row rather than a refusal")
        .isEqualTo(new ReadOutcome.Answered(List.of(), null));
  }

  @Test
  void a_tester_asking_the_read_with_a_card_this_business_never_saved_reads_grows_own_refusal() {
    aSavedCard();

    ReadOutcome answered =
        provider.read(theQuestionAskedWith("tok-nobody-saved", MY_OWN_IDENTIFIER));

    assertThat(answered)
        .asInstanceOf(InstanceOfAssertFactories.type(ReadOutcome.RefusedByTheProvider.class))
        .satisfies(
            refused ->
                assertThat(refused.inItsOwnWords())
                    .as("the refusal a tester reads is the one grow's own contract publishes")
                    .startsWith("140 "));
  }

  @Test
  void a_tester_asking_the_read_on_an_account_this_run_does_not_configure_is_told_so() {
    assertThat(
            provider.read(
                ReadRequest.of(
                    RUN,
                    "merchant-nobody-configured",
                    GrowReads.A_CHARGE_ON_A_SAVED_CARD,
                    Map.of(GrowFields.CARD_TOKEN, "tok-1"),
                    ObjectOrigin.UI)))
        .isEqualTo(new ReadOutcome.NoSuchAccount());
  }

  private static ReadRequest theQuestionAskedWith(String cardToken, String myOwnIdentifier) {
    Map<String, String> asked = new LinkedHashMap<>();
    asked.put(GrowFields.CARD_TOKEN, cardToken);
    asked.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, myOwnIdentifier);
    return ReadRequest.of(
        RUN, MERCHANT, GrowReads.A_CHARGE_ON_A_SAVED_CARD, asked, ObjectOrigin.UI);
  }

  private String aSavedCard() {
    GrowApiResponse created = api.createPaymentProcess(call(CREATE, aTokenOnlyRequest()));
    assertThat(created.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    String processId = String.valueOf(created.providerObjectId());
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    GrowPaymentProcess registered =
        processes.findByProcessId(RUN, processId).orElseThrow(AssertionError::new);
    assertThat(registered.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    GrowProcessOutcome outcome = registered.outcome();
    assertThat(outcome).isNotNull();
    return String.valueOf(outcome.cardToken());
  }

  private static Map<String, String> aTokenOnlyRequest() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CHARGE_TYPE, GrowFields.CHARGE_TYPE_TOKEN_ONLY);
    form.put(GrowFields.SUM, "1");
    form.put(GrowFields.SAVE_CARD_TOKEN, "1");
    form.put(GrowFields.DESCRIPTION, "A card registration");
    form.put(GrowFields.SUCCESS_URL, "https://merchant.example/thanks");
    form.put(GrowFields.CANCEL_URL, "https://merchant.example/cancel");
    form.put(GrowFields.NOTIFY_URL, NOTIFY_URL);
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    return form;
  }

  private static Map<String, String> aChargeOf(String cardToken) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.CARD_TOKEN, cardToken);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.SUM, "89.90");
    form.put(GrowFields.DESCRIPTION, "A stored-card charge");
    form.put(GrowFields.PAYMENT_TYPE, "2");
    form.put(GrowFields.PAYMENT_NUM, "1");
    form.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, MY_OWN_IDENTIFIER);
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    form.put(GrowFields.EMAIL, "payer@merchant.example");
    return form;
  }

  private static Map<String, String> aChargeOf(String cardToken, Map<String, String> overrides) {
    Map<String, String> form = aChargeOf(cardToken);
    form.putAll(overrides);
    return form;
  }

  private static Map<String, String> aRetraceOf(String cardToken) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CARD_TOKEN, cardToken);
    form.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, MY_OWN_IDENTIFIER);
    return form;
  }

  private static Map<String, String> aRetraceOf(String cardToken, Map<String, String> overrides) {
    Map<String, String> form = aRetraceOf(cardToken);
    form.putAll(overrides);
    return form;
  }

  private GrowApiResponse charge(Map<String, String> form) {
    return api.createTransactionWithToken(call(CHARGE, form));
  }

  private GrowApiResponse retrace(Map<String, String> form) {
    return api.getTokenTransactionsByExternalIdentifiers(call(RETRACE, form));
  }

  private static GrowApiCall call(String path, Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return new GrowApiCall(
        "POST", path, Map.of("Content-Type", List.of("multipart/form-data; boundary=x")), fields);
  }

  private static String bodyOf(GrowApiResponse response) {
    return GrowWireText.readable(new String(response.body(), UTF_8));
  }
}
