package dev.marcinromanowski.pspsim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;

class OneQueryBringsEveryLedgerIntegrationTest extends PostgresBackedTest {

  private static final int EIGHT_OBJECTS = 8;

  private static final AtomicLong STATEMENTS = new AtomicLong();

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

  @Autowired private ProviderRegistry providers;

  @Autowired private JdbcClient jdbc;

  private SeededProviderObjects seeds() {
    return new SeededProviderObjects(jdbc);
  }

  @Test
  void growing_the_run_by_one_transaction_costs_the_grow_listing_no_further_statement() {
    TestRunId run = TestRunId.of("one-query-grow");
    jdbc.sql("delete from grow_transaction where test_run_id = ?").param(run.value()).update();

    theListingCostsTheSameWhateverItHolds(
        "grow",
        run,
        held -> seeds().aGrowTransaction(run, held),
        SeededProviderObjects::theGrowTransactionIdOf);
  }

  @Test
  void growing_the_run_by_one_payment_costs_the_azericard_listing_no_further_statement() {
    TestRunId run = TestRunId.of("one-query-azericard");
    jdbc.sql("delete from azericard_payment where test_run_id = ?").param(run.value()).update();

    theListingCostsTheSameWhateverItHolds(
        "azericard",
        run,
        held -> seeds().anAzericardPayment(run, held),
        SeededProviderObjects::theAzericardIntRefOf);
  }

  @Test
  void growing_the_run_by_one_intent_costs_the_stripe_listing_no_further_statement() {
    TestRunId run = TestRunId.of("one-query-stripe");
    jdbc.sql("delete from stripe_payment_intent where test_run_id = ?").param(run.value()).update();

    theListingCostsTheSameWhateverItHolds(
        "stripe",
        run,
        held -> seeds().aStripeIntent(run, held),
        SeededProviderObjects::theStripeIntentIdOf);
  }

  @Test
  void growing_the_run_by_one_payment_costs_the_worldline_listing_no_further_statement() {
    TestRunId run = TestRunId.of("one-query-worldline");
    jdbc.sql("delete from worldline_payment_identifier where test_run_id = ?")
        .param(run.value())
        .update();
    jdbc.sql("delete from worldline_payment where test_run_id = ?").param(run.value()).update();

    theListingCostsTheSameWhateverItHolds(
        "worldline",
        run,
        held -> seeds().aWorldlinePayment(run, held),
        SeededProviderObjects::theWorldlinePaymentIdOf);
  }

  @Test
  void growing_the_run_by_one_payment_costs_the_autra_listing_no_further_statement() {
    TestRunId run = TestRunId.of("one-query-autra");
    jdbc.sql("delete from autra_payment where test_run_id = ?").param(run.value()).update();

    theListingCostsTheSameWhateverItHolds(
        "autra",
        run,
        held -> seeds().anAutraPayment(run, held),
        SeededProviderObjects::theAutraPaymentIdOf);
  }

  @Test
  void growing_the_run_by_one_debit_costs_the_tranzila_listing_no_further_statement() {
    TestRunId run = TestRunId.of("one-query-tranzila");
    jdbc.sql("delete from tranzila_transaction where test_run_id = ?").param(run.value()).update();

    theListingCostsTheSameWhateverItHolds(
        "tranzila",
        run,
        held -> seeds().aTranzilaDebit(run, held),
        SeededProviderObjects::theTranzilaTransactionIdOf);
  }

  private void theListingCostsTheSameWhateverItHolds(
      String providerName, TestRunId run, IntConsumer seedTheNextObject, IntFunction<String> idOf) {
    SimulatorProvider module = providers.find(providerName).orElseThrow();
    List<Long> perListing = new ArrayList<>();
    for (int held = 1; held <= EIGHT_OBJECTS; held++) {
      seedTheNextObject.accept(held);
      perListing.add(Long.valueOf(statementsForTheListingOf(module, run)));
    }
    System.out.print(
        "\nMEASURED statements per "
            + providerName
            + " listing, 1.."
            + EIGHT_OBJECTS
            + " objects: "
            + perListing
            + "\n");

    assertThat(theCostOfEachFurtherObjectIn(perListing))
        .as(
            "the provider-neutral object listing of %s is read by the Payments screen, by an"
                + " object's own screen and by the control API, and a ledger read per object made"
                + " every one of them cost a statement per object it listed: %s",
            providerName, perListing)
        .containsOnly(Long.valueOf(0L));

    List<ProviderObjectState> listed = theObjectsListedBy(module, run);
    List<ProviderObjectState> oneAtATime = new ArrayList<>();
    for (int held = EIGHT_OBJECTS; held >= 1; held--) {
      oneAtATime.add(module.state(run, idOf.apply(held)).orElseThrow());
    }
    assertThat(listed)
        .as(
            "the one answer that brought every ledger must place each object's own operations"
                + " against that object, in the order the provider wrote them, and leave the page"
                + " in the order a tester reads it")
        .containsExactlyElementsOf(oneAtATime);
    assertThat(listed)
        .extracting(state -> Integer.valueOf(state.ledger().operations().size()))
        .containsOnly(Integer.valueOf(2));
  }

  private static List<Long> theCostOfEachFurtherObjectIn(List<Long> perListing) {
    List<Long> further = new ArrayList<>();
    for (int at = 1; at < perListing.size(); at++) {
      further.add(
          Long.valueOf(perListing.get(at).longValue() - perListing.get(at - 1).longValue()));
    }
    return List.copyOf(further);
  }

  private long statementsForTheListingOf(SimulatorProvider module, TestRunId run) {
    STATEMENTS.set(0);
    List<ProviderObjectState> listed = theObjectsListedBy(module, run);
    long counted = STATEMENTS.get();
    assertThat(listed).isNotEmpty();
    return counted;
  }

  private static List<ProviderObjectState> theObjectsListedBy(
      SimulatorProvider module, TestRunId run) {
    ProviderObjectPage page = module.objects(new ProviderObjectQuery(run, null, 100));
    return ((ProviderObjectPage.Listed) page).objects();
  }
}
