package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.SeededProviderObjects;
import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.client.RestClient;

class AScreenAsksOnceForWhatItNamesIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "one-query-measured";

  private static final String ACCOUNT = "merchant-1";

  private static final String DOCUMENT = "25055662000190";

  private static final String CLIENT_ID = "one-query-client-id";

  private static final String CLIENT_SECRET = "one-query-client-secret";

  private static final int SIX_PAYMENTS = 6;

  private static final int TEN_IDENTIFIERS = 10;

  private static final int EIGHT_IDENTIFIERS = 8;

  private static final AtomicLong STATEMENTS = new AtomicLong();

  @LocalServerPort private int port;

  @Autowired private ProviderRegistry providers;

  @Autowired private JdbcClient store;

  private SeededProviderObjects seeds() {
    return new SeededProviderObjects(store);
  }

  private RestClient http;

  private final HttpClient wire = HttpClient.newHttpClient();

  @TestConfiguration
  static class EveryStatementIsCounted {

    @Bean
    static BeanPostProcessor theCountingDataSource() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
          return bean instanceof DataSource pool && !(bean instanceof CountingDataSource)
              ? new CountingDataSource(pool)
              : bean;
        }
      };
    }
  }

  static final class CountingDataSource extends DelegatingDataSource {

    CountingDataSource(DataSource pool) {
      super(pool);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return counted(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return counted(super.getConnection(username, password));
    }

    private static Connection counted(Connection open) {
      return (Connection)
          Proxy.newProxyInstance(
              CountingDataSource.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              new CountEveryStatement(open));
    }
  }

  record CountEveryStatement(Connection open) implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method called, Object[] arguments) throws Throwable {
      if (called.getName().startsWith("prepare") || called.getName().startsWith("create")) {
        STATEMENTS.incrementAndGet();
      }
      try {
        return called.invoke(open, arguments);
      } catch (InvocationTargetException thrownInside) {
        throw thrownInside.getCause();
      }
    }
  }

  @BeforeEach
  void configureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(true);
  }

  @AfterEach
  void unconfigureTheMerchant() {
    reset();
    configure(false);
  }

  @Test
  void the_payments_screen_costs_the_same_number_of_statements_whatever_the_run_holds() {
    List<Long> perPage = new ArrayList<>();
    for (int wanted = 1; wanted <= SIX_PAYMENTS; wanted++) {
      aHold(null);
      perPage.add(statementsForTheListing());
    }
    System.out.print("\nMEASURED statements per listing, 1..6 payments: " + perPage + "\n");

    assertThat(theCostOfEachFurtherPaymentIn(perPage))
        .as(
            "the provider-neutral object listing is read by the Payments screen, by an object's"
                + " own screen and by the control API, and a timeline read or a ledger read per"
                + " payment made every one of them cost a statement per object it listed: %s",
            perPage)
        .containsOnly(0L);
  }

  @Test
  void an_objects_screen_costs_the_same_whether_that_object_names_twelve_things_or_sixteen() {
    String fewest = aHold(null);
    String most = aHold("pedido-one-query-1");
    long namedOnFewest = relatedOn(fewest);
    long namedOnMost = relatedOn(most);
    long screenOfFewest = statementsForTheScreenOf(fewest);
    long screenOfMost = statementsForTheScreenOf(most);

    System.out.print(
        "\nMEASURED one object screen: "
            + namedOnFewest
            + " identifiers cost "
            + screenOfFewest
            + " statements, "
            + namedOnMost
            + " identifiers cost "
            + screenOfMost
            + ", the listing both draw costs "
            + statementsForTheListing()
            + "\n");

    assertThat(namedOnMost).isGreaterThan(namedOnFewest);
    assertThat(namedOnFewest).isGreaterThan(TEN_IDENTIFIERS);
    assertThat(screenOfMost - screenOfFewest)
        .as(
            "a screen that asks the store once per identifier pays a lookup for every field the"
                + " provider names, and a lookup of this provider walks three stores: %s"
                + " identifiers cost %s statements and %s identifiers cost %s",
            namedOnFewest, screenOfFewest, namedOnMost, screenOfMost)
        .isZero();
  }

  @Test
  void the_payment_methods_screen_costs_the_same_whatever_number_of_cards_the_run_holds() {
    List<Long> perScreen = new ArrayList<>();
    for (int seeded = 1; seeded <= SIX_PAYMENTS; seeded++) {
      aFurtherSeededCard(seeded);
      perScreen.add(statementsForThePaymentMethodsScreen());
    }
    System.out.print(
        "\nMEASURED statements per Payment methods screen, 1..6 cards: " + perScreen + "\n");

    assertThat(theCostOfEachFurtherPaymentIn(perScreen))
        .as(
            "every seeded method names the provider-native identifiers a client drives it by, and"
                + " the screen labels each of them with what the provider holds under it, so a"
                + " read per identifier made the screen cost a statement per seeded card: %s",
            perScreen)
        .containsOnly(0L);
  }

  private void aFurtherSeededCard(int seeded) {
    Map<String, Object> card = new LinkedHashMap<>();
    card.put("brand", "visa");
    card.put("last4", "1111");
    card.put("expMonth", Integer.valueOf(12));
    card.put("expYear", Integer.valueOf(2030));
    card.put("funding", "credit");
    Map<String, Object> asked = new LinkedHashMap<>();
    asked.put("testRunId", RUN);
    asked.put("merchantId", ACCOUNT);
    asked.put("ownerId", "owner-1");
    asked.put("type", "card");
    asked.put("alias", "a-further-pair-" + seeded);
    asked.put("card", card);
    asked.put(
        "providerIds",
        Map.of("token", "slug-token-" + seeded, "paymentMethodId", "slug-stored-card-" + seeded));
    http.post()
        .uri("/__simulator/autra/fixtures/payment-methods")
        .contentType(MediaType.APPLICATION_JSON)
        .body(asked)
        .retrieve()
        .toBodilessEntity();
  }

  private long statementsForThePaymentMethodsScreen() {
    STATEMENTS.set(0);
    assertThat(get("/ui/payment-methods?provider=autra&testRunId=" + RUN).statusCode())
        .isEqualTo(200);
    return STATEMENTS.get();
  }

  @Test
  void naming_one_more_grow_transaction_costs_the_screen_that_labels_them_no_further_statement() {
    TestRunId run = TestRunId.of("one-name-grow");
    store.sql("delete from grow_transaction where test_run_id = ?").param(run.value()).update();

    labellingOneMoreIdentifierCostsNothing(
        "grow",
        run,
        held -> seeds().aGrowTransaction(run, held),
        SeededProviderObjects::theGrowTransactionIdOf);
  }

  @Test
  void naming_one_more_azericard_payment_costs_the_screen_that_labels_them_no_further_statement() {
    TestRunId run = TestRunId.of("one-name-azericard");
    store.sql("delete from azericard_payment where test_run_id = ?").param(run.value()).update();

    labellingOneMoreIdentifierCostsNothing(
        "azericard",
        run,
        held -> seeds().anAzericardPayment(run, held),
        SeededProviderObjects::theAzericardIntRefOf);
  }

  @Test
  void naming_one_more_stripe_intent_costs_the_screen_that_labels_them_no_further_statement() {
    TestRunId run = TestRunId.of("one-name-stripe");
    store
        .sql("delete from stripe_payment_intent where test_run_id = ?")
        .param(run.value())
        .update();

    labellingOneMoreIdentifierCostsNothing(
        "stripe",
        run,
        held -> seeds().aStripeIntent(run, held),
        SeededProviderObjects::theStripeIntentIdOf);
  }

  @Test
  void naming_one_more_worldline_payment_costs_the_screen_that_labels_them_no_further_statement() {
    TestRunId run = TestRunId.of("one-name-worldline");
    store
        .sql("delete from worldline_payment_identifier where test_run_id = ?")
        .param(run.value())
        .update();
    store.sql("delete from worldline_payment where test_run_id = ?").param(run.value()).update();

    labellingOneMoreIdentifierCostsNothing(
        "worldline",
        run,
        held -> seeds().aWorldlinePayment(run, held),
        SeededProviderObjects::theWorldlinePaymentIdOf);
  }

  @Test
  void naming_one_more_autra_payment_costs_the_screen_that_labels_them_no_further_statement() {
    TestRunId run = TestRunId.of("one-name-autra");
    store.sql("delete from autra_payment where test_run_id = ?").param(run.value()).update();

    labellingOneMoreIdentifierCostsNothing(
        "autra",
        run,
        held -> seeds().anAutraPayment(run, held),
        SeededProviderObjects::theAutraPaymentIdOf);
  }

  private void labellingOneMoreIdentifierCostsNothing(
      String providerName, TestRunId run, IntConsumer seedTheNextObject, IntFunction<String> idOf) {
    SimulatorProvider module = providers.find(providerName).orElseThrow();
    for (int held = 1; held <= EIGHT_IDENTIFIERS; held++) {
      seedTheNextObject.accept(held);
    }
    assertThat(module.states(run, theFirst(1, idOf))).hasSize(1);
    List<Long> perNumberNamed = new ArrayList<>();
    for (int named = 1; named <= EIGHT_IDENTIFIERS; named++) {
      List<String> asked = theFirst(named, idOf);
      STATEMENTS.set(0);
      List<ProviderObjectState> labelled = module.states(run, asked);
      perNumberNamed.add(Long.valueOf(STATEMENTS.get()));
      assertThat(labelled).hasSize(named);
    }
    List<String> everyIdentifier = theOldestAndTheNewestAlternating(EIGHT_IDENTIFIERS, idOf);
    STATEMENTS.set(0);
    List<ProviderObjectState> namingNothingThisRunHolds =
        module.states(run, theFirst(EIGHT_IDENTIFIERS, held -> "nothing-of-this-run-" + held));
    long theCostOfLabellingEightMisses = STATEMENTS.get();

    System.out.print(
        "\nMEASURED statements for one "
            + providerName
            + " screen labelling 1.."
            + EIGHT_IDENTIFIERS
            + " named identifiers: "
            + perNumberNamed
            + ", and "
            + theCostOfLabellingEightMisses
            + " for eight identifiers this run holds nothing under\n");

    assertThat(theCostOfEachFurtherIdentifierIn(perNumberNamed))
        .as(
            "an object of %s names other objects of %s, and the two screens that label those"
                + " names resolve them through this one call: an object's own screen, and the"
                + " Payment methods screen for the identifiers each seeded card is driven by."
                + " A read per identifier makes both cost a statement per name. The control"
                + " answer and the correlation inspector print these identifiers without"
                + " labelling them, so they never resolved them one at a time and this"
                + " measurement says nothing about either: %s",
            providerName, providerName, perNumberNamed)
        .containsOnly(Long.valueOf(0L));
    assertThat(theCostOfLabellingEightMisses)
        .as(
            "an identifier a provider names need not be an object this run holds, and resolving"
                + " eight of those must cost no more than resolving eight it does hold")
        .isLessThanOrEqualTo(perNumberNamed.get(perNumberNamed.size() - 1).longValue());
    assertThat(namingNothingThisRunHolds).isEmpty();

    List<ProviderObjectState> oneAnswer = module.states(run, everyIdentifier);
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(oneAnswer, everyIdentifier))
        .as(
            "%s. The labels a tester reads beside the identifiers an object of %s names come out"
                + " of this one answer, each object carrying its own operations and its own named"
                + " objects",
            TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL, providerName)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(module, run, everyIdentifier));
    assertThat(oneAnswer)
        .as(
            "%s. These identifiers are asked for oldest, newest, second oldest, second newest and"
                + " so on, which is neither the order a store reading newest first returns them"
                + " in nor the order one reading oldest first does, so for every provider here an"
                + " answer in the order its own stores keep these objects in fails this",
            TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactlyElementsOf(
            TheSequenceOneAnswerIsIn.askingOneAtATimeInTheOrderAsked(module, run, everyIdentifier));
    assertThat(oneAnswer)
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactlyElementsOf(everyIdentifier);
    assertThat(oneAnswer).hasSameSizeAs(everyIdentifier);
    assertThat(oneAnswer)
        .extracting(state -> Integer.valueOf(state.ledger().operations().size()))
        .containsOnly(Integer.valueOf(2));
  }

  private static List<String> theOldestAndTheNewestAlternating(
      int named, IntFunction<String> idOf) {
    List<String> asked = new ArrayList<>();
    int oldest = 1;
    int newest = named;
    while (oldest <= newest) {
      asked.add(idOf.apply(oldest));
      if (oldest != newest) {
        asked.add(idOf.apply(newest));
      }
      oldest++;
      newest--;
    }
    return List.copyOf(asked);
  }

  private static List<String> theFirst(int named, IntFunction<String> idOf) {
    List<String> asked = new ArrayList<>();
    for (int at = 1; at <= named; at++) {
      asked.add(idOf.apply(at));
    }
    return List.copyOf(asked);
  }

  private static List<Long> theCostOfEachFurtherIdentifierIn(List<Long> perNumberNamed) {
    List<Long> further = new ArrayList<>();
    for (int at = 1; at < perNumberNamed.size(); at++) {
      further.add(
          Long.valueOf(
              perNumberNamed.get(at).longValue() - perNumberNamed.get(at - 1).longValue()));
    }
    return List.copyOf(further);
  }

  private long relatedOn(String paymentId) {
    Object related = stateOf(paymentId).get("relatedObjects");
    return related instanceof Map<?, ?> named ? named.size() : -1;
  }

  private static List<Long> theCostOfEachFurtherPaymentIn(List<Long> perPage) {
    List<Long> further = new ArrayList<>();
    for (int page = 1; page < perPage.size(); page++) {
      further.add(Long.valueOf(perPage.get(page).longValue() - perPage.get(page - 1).longValue()));
    }
    return List.copyOf(further);
  }

  private long statementsForTheListing() {
    STATEMENTS.set(0);
    assertThat(get("/ui/objects?provider=autra&testRunId=" + RUN).statusCode()).isEqualTo(200);
    return STATEMENTS.get();
  }

  private long statementsForTheScreenOf(String paymentId) {
    STATEMENTS.set(0);
    assertThat(
            get("/ui/objects?provider=autra&testRunId=" + RUN + "&providerObjectId=" + paymentId)
                .statusCode())
        .isEqualTo(200);
    return STATEMENTS.get();
  }

  private Map<String, Object> stateOf(String paymentId) {
    return http.get()
        .uri("/__simulator/autra/state/" + paymentId + "?testRunId=" + RUN)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private String aHold(@Nullable String orderId) {
    String token = anAccessToken();
    String transactionalToken =
        valueIn(
            json(
                    "/v1/acquiring/payments/initialize",
                    token,
                    "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            "transactionalToken");
    return valueIn(
        json(
                "/v1/acquiring/payments/pre-authorization",
                token,
                "{\"transactionalToken\":\""
                    + transactionalToken
                    + "\",\"documentId\":\""
                    + DOCUMENT
                    + "\",\"transactionType\":\"CREDIT\",\"currency\":\"BRL\",\"amount\":500.00"
                    + ",\"installments\":1"
                    + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
                    + ",\"tokenData\":{\"slugToken\":\"slug-token-abc\""
                    + (orderId == null ? "" : ",\"slugStoredCard\":\"slug-stored-card-xyz\"")
                    + "}}")
            .body(),
        "paymentID");
  }

  private String anAccessToken() {
    HttpResponse<String> minted =
        send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + "/v1/oauth/token?grant_type=client_credentials"))
                .header(
                    "Authorization",
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(UTF_8)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    assertThat(minted.statusCode()).isEqualTo(200);
    return valueIn(minted.body(), "accessToken");
  }

  private void theExamplePairIsACardOfTheRun() {
    Map<String, Object> card = new LinkedHashMap<>();
    card.put("brand", "visa");
    card.put("last4", "1111");
    card.put("expMonth", Integer.valueOf(12));
    card.put("expYear", Integer.valueOf(2030));
    card.put("funding", "credit");
    Map<String, Object> seeded = new LinkedHashMap<>();
    seeded.put("testRunId", RUN);
    seeded.put("merchantId", ACCOUNT);
    seeded.put("ownerId", "owner-1");
    seeded.put("type", "card");
    seeded.put("alias", "the-example-pair");
    seeded.put("card", card);
    seeded.put(
        "providerIds",
        Map.of("token", "slug-token-abc", "paymentMethodId", "slug-stored-card-xyz"));
    http.post()
        .uri("/__simulator/autra/fixtures/payment-methods")
        .contentType(MediaType.APPLICATION_JSON)
        .body(seeded)
        .retrieve()
        .toBodilessEntity();
  }

  private void configure(boolean withTheMerchant) {
    List<Map<String, Object>> accounts =
        withTheMerchant
            ? List.of(
                Map.of(
                    "accountId",
                    ACCOUNT,
                    "configuration",
                    Map.of(
                        "clientId",
                        CLIENT_ID,
                        "clientSecret",
                        CLIENT_SECRET,
                        "documentId",
                        DOCUMENT)))
            : List.of();
    http.put()
        .uri("/__simulator/autra/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN, "accounts", accounts))
        .retrieve()
        .toBodilessEntity();
    if (withTheMerchant) {
      theExamplePairIsACardOfTheRun();
    }
  }

  private void reset() {
    http.post()
        .uri("/__simulator/autra/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN))
        .retrieve()
        .toBodilessEntity();
  }

  private HttpResponse<String> get(String path) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build());
  }

  private HttpResponse<String> json(String path, String bearer, String body) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + bearer)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (IOException unreachable) {
      throw new UncheckedIOException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String valueIn(String json, String field) {
    String marked = "\"" + field + "\":\"";
    int at = json.indexOf(marked);
    assertThat(at).as(field + " in " + json).isNotNegative();
    int from = at + marked.length();
    return json.substring(from, json.indexOf('"', from));
  }
}
