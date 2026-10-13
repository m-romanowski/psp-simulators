package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutraAsksOnceForWhatItNamesTest {

  private static final TestRunId RUN = TestRunId.of("autra-asks-once");

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  private static final String ACCOUNT = "merchant-1";

  private static final String DOCUMENT = "25055662000190";

  private static final int EIGHT_PAYMENTS = 8;

  private static final int TEN_IDENTIFIERS = 10;

  private final Map<String, Integer> paymentCalls = new LinkedHashMap<>();

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments stored = new AutraDoubles.Payments();
  private final AutraPayments payments = counting(AutraPayments.class, stored, paymentCalls);
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final IdGenerator ids = new AutraDoubles.CountingIds();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final AutraDoubles.HeldResponses heldResponses = new AutraDoubles.HeldResponses();
  private final Map<String, Integer> pixCalls = new LinkedHashMap<>();
  private final Map<String, Integer> sessionCalls = new LinkedHashMap<>();
  private final AutraDoubles.PixPayments pixStore = new AutraDoubles.PixPayments();
  private final AutraDoubles.CardSessions sessionStore = new AutraDoubles.CardSessions();
  private final AutraPixPayments pixPayments = counting(AutraPixPayments.class, pixStore, pixCalls);
  private final AutraCardSessions cardSessions =
      counting(AutraCardSessions.class, sessionStore, sessionCalls);
  private final AutraDoubles.Accounts accounts = new AutraDoubles.Accounts();
  private final Map<String, Integer> clockCalls = new LinkedHashMap<>();
  private final SimulatorClock clock =
      counting(SimulatorClock.class, AutraDoubles.clockAt(NOW), clockCalls);

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
          clock,
          new OpenAcquiringSession(sessions, ids, scenarios),
          new OpenCardSession(cardSessions, ids, scenarios, AutraWiring.PUBLIC_BASE_URL),
          new ChargeStoredCard(
              payments,
              sessions,
              new AutraStoredCards(cardSessions, AutraDoubles.fixturesHoldingTheExamplePair(RUN)),
              ids,
              scenarios),
          new TokenizeOnTheHostedPage(cardSessions, new AutraPayerCards(fixtures), ids),
          new PreAuthorize(
              payments,
              sessions,
              new AutraStoredCards(cardSessions, AutraDoubles.fixturesHoldingTheExamplePair(RUN)),
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

  @Test
  void a_listing_asks_for_the_timelines_of_the_page_once_however_many_payments_the_page_carries() {
    configure();
    StringBuilder measured = new StringBuilder("\nMEASURED listing\n");
    for (int held = 1; held <= EIGHT_PAYMENTS; held++) {
      aHold("100.00");
      paymentCalls.clear();
      clockCalls.clear();
      ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 100));
      measured
          .append(((ProviderObjectPage.Listed) page).objects().size())
          .append(" objects listed: payments ")
          .append(paymentCalls)
          .append(", clock ")
          .append(clockCalls)
          .append('\n');
      assertThat(((ProviderObjectPage.Listed) page).objects()).hasSize(held);
      assertThat(paymentCalls)
          .as(
              "a listing that reads one timeline per payment grows a query per object listed, and"
                  + " the provider-neutral object list is read by the Payments screen, by this"
                  + " run's own screen and by the control API: %s payments listed",
              held)
          .containsExactly(entry("newestFirst", 1), entry("everyEventOf", 1));
    }
    System.out.print(measured);
  }

  @Test
  void one_payment_read_back_names_this_many_related_entries_of_which_this_many_are_distinct() {
    configure();
    String paymentId = aHold("100.00");
    capture(paymentId, "40.00");
    capture(paymentId, "20.00");

    Map<String, String> related = provider.state(RUN, paymentId).orElseThrow().relatedObjects();
    Set<String> distinct = new LinkedHashSet<>(related.values());

    System.out.print(
        "\nMEASURED one payment names "
            + related.size()
            + " related entries, "
            + distinct.size()
            + " of them distinct values\n"
            + related
            + "\n");
    assertThat(related).isNotEmpty();
  }

  @Test
  void one_state_lookup_of_an_identifier_this_run_holds_nothing_under_costs_this_many_calls() {
    configure();
    aHold("100.00");
    paymentCalls.clear();
    pixCalls.clear();
    sessionCalls.clear();
    clockCalls.clear();

    assertThat(provider.state(RUN, "nothing-of-this-run")).isEmpty();

    System.out.print(
        "\nMEASURED one miss: payments "
            + paymentCalls
            + " pix "
            + pixCalls
            + " cardSessions "
            + sessionCalls
            + " clock "
            + clockCalls
            + "\n");
  }

  @Test
  void a_screen_labelling_every_identifier_one_payment_names_asks_each_store_once() {
    configure();
    String paymentId = aHold("100.00");
    capture(paymentId, "40.00");
    List<String> named =
        List.copyOf(provider.state(RUN, paymentId).orElseThrow().relatedObjects().values());
    paymentCalls.clear();
    pixCalls.clear();
    sessionCalls.clear();
    clockCalls.clear();

    List<ProviderObjectState> held = provider.states(RUN, named);

    System.out.print(
        "\nMEASURED one screen labelling "
            + named.size()
            + " identifiers: payments "
            + paymentCalls
            + " pix "
            + pixCalls
            + " cardSessions "
            + sessionCalls
            + " clock "
            + clockCalls
            + "\n");
    assertThat(named).hasSizeGreaterThan(TEN_IDENTIFIERS);
    assertThat(held).isEmpty();
    assertThat(paymentCalls).containsExactly(entry("allById", 1), entry("everyEventOf", 1));
    assertThat(pixCalls).containsExactly(entry("allById", 1));
    assertThat(sessionCalls).containsExactly(entry("allById", 1));
    assertThat(clockCalls).containsExactly(entry("now", 1));
  }

  @Test
  void what_one_answer_for_many_identifiers_holds_is_what_asking_one_at_a_time_holds() {
    configure();
    String held = aHold("100.00");
    capture(held, "40.00");
    String pixId = aPix("99.90");
    String sessionId = aCardSession();
    List<String> asked =
        List.of(sessionId, "nothing-of-this-run", pixId, held, "CREDIT", sessionId);

    List<ProviderObjectState> batched = provider.states(RUN, asked);

    assertThat(batched)
        .as(
            "the labels a screen prints beside the identifiers an object names come from this"
                + " answer, so one answer for many identifiers that differed from asking one at a"
                + " time would change what a tester reads. %s",
            TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactlyElementsOf(
            TheSequenceOneAnswerIsIn.askingOneAtATimeInTheOrderAsked(provider, RUN, asked));
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(batched, asked))
        .as(TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(provider, RUN, asked));
    assertThat(batched)
        .extracting(ProviderObjectState::providerObjectId)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(sessionId, pixId, held);
  }

  @Test
  void a_listing_reads_back_the_payments_the_single_object_read_reads_back() {
    configure();
    String first = aHold("100.00");
    capture(first, "40.00");
    String second = aHold("200.00");

    ProviderObjectPage.Listed page =
        (ProviderObjectPage.Listed) provider.objects(new ProviderObjectQuery(RUN, null, 100));

    assertThat(page.objects())
        .as(
            "a listing that read the timelines of its whole page in one answer must place each"
                + " payment's own events against that payment, in the order the provider wrote"
                + " them")
        .containsExactly(
            provider.state(RUN, second).orElseThrow(), provider.state(RUN, first).orElseThrow());
  }

  private String aHold(String amount) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, amount);
    values.put(AutraFields.CURRENCY, "BRL");
    OriginationOutcome started =
        provider.originate(
            OriginationRequest.of(
                RUN, ACCOUNT, AutraOrigination.A_HOLD, values, ObjectOrigin.CONTROL_API));
    return ((OriginationOutcome.Originated) started).providerObjectId();
  }

  private String aPix(String amount) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, amount);
    values.put(AutraFields.CURRENCY, "BRL");
    OriginationOutcome started =
        provider.originate(
            OriginationRequest.of(
                RUN, ACCOUNT, AutraOrigination.A_PIX, values, ObjectOrigin.CONTROL_API));
    return ((OriginationOutcome.Originated) started).providerObjectId();
  }

  private String aCardSession() {
    OriginationOutcome started =
        provider.originate(
            OriginationRequest.of(
                RUN, ACCOUNT, AutraOrigination.A_CARD_SESSION, Map.of(), ObjectOrigin.CONTROL_API));
    return ((OriginationOutcome.Originated) started).providerObjectId();
  }

  private void capture(String paymentId, String amount) {
    provider.act(
        ObjectActionRequest.of(
            RUN, paymentId, ObjectAction.CAPTURE, amount, ObjectOrigin.CONTROL_API));
  }

  private void configure() {
    Map<String, String> declared = new LinkedHashMap<>();
    declared.put(AutraMerchant.CLIENT_ID_PROPERTY, "client-" + ACCOUNT);
    declared.put(AutraMerchant.CLIENT_SECRET_PROPERTY, "secret-" + ACCOUNT);
    declared.put(AutraMerchant.DOCUMENT_ID_PROPERTY, DOCUMENT);
    provider
        .parse(
            new ProviderConfiguration(
                RUN,
                List.of(new ConfiguredAccount(ACCOUNT, declared)),
                List.of(),
                List.of(),
                Map.of()))
        .store();
  }

  private static <T> T counting(Class<T> port, T store, Map<String, Integer> calls) {
    return port.cast(
        Proxy.newProxyInstance(
            port.getClassLoader(), new Class<?>[] {port}, new CountedCalls(store, calls)));
  }

  private record CountedCalls(Object store, Map<String, Integer> calls)
      implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method called, Object[] arguments) throws Throwable {
      calls.merge(called.getName(), 1, Integer::sum);
      try {
        return called.invoke(store, arguments);
      } catch (InvocationTargetException thrownInside) {
        throw thrownInside.getCause();
      }
    }
  }
}
