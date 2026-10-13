package dev.marcinromanowski.pspsim.kernel.fixture.internal;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtureRemoval;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.fixture.Seeding;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class JdbcPaymentMethodFixtures implements PaymentMethodFixtures {

  private static final TypeReference<Map<String, String>> PROVIDER_IDS = new TypeReference<>() {};

  private static final int FIXTURE_ID_LENGTH = 24;

  public static final String ALIAS = "alias";

  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final IdGenerator ids;
  private final SimulatorClock clock;
  private final TransactionTemplate unitOfWork;

  public JdbcPaymentMethodFixtures(
      JdbcClient jdbc,
      ObjectMapper json,
      IdGenerator ids,
      SimulatorClock clock,
      TransactionTemplate unitOfWork) {
    this.jdbc = jdbc;
    this.json = json;
    this.ids = ids;
    this.clock = clock;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public Seeding seed(
      NewPaymentMethodFixture fixture,
      PaymentMethodFixturePlan plan,
      Set<String> theKeysManyFixturesMayShare) {
    return Objects.requireNonNull(
        unitOfWork.execute(
            status -> seedInOneTransaction(status, fixture, plan, theKeysManyFixturesMayShare)),
        "seeding a payment-method fixture answered nothing");
  }

  private Seeding seedInOneTransaction(
      TransactionStatus status,
      NewPaymentMethodFixture fixture,
      PaymentMethodFixturePlan plan,
      Set<String> theKeysManyFixturesMayShare) {
    String fixtureId = "fx_" + ids.hex(FIXTURE_ID_LENGTH);
    Instant createdAt = clock.now(fixture.testRunId());
    FixtureCard card = fixture.card();
    int claimed =
        jdbc.sql(
                """
                insert into simulator_payment_method_fixture
                    (fixture_id, provider, test_run_id, merchant_id, owner_id, method_type,
                     fixture_alias, card_brand, card_last4, card_exp_month, card_exp_year,
                     card_funding, provider_ids, default_scenario, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (provider, test_run_id, merchant_id, fixture_alias) do nothing
                """)
            .param(fixtureId)
            .param(fixture.provider().value())
            .param(TestRunId.storableNamespace(fixture.testRunId()).value())
            .param(fixture.merchantId())
            .param(fixture.ownerId())
            .param(fixture.type().name())
            .param(fixture.fixtureAlias())
            .param(card == null ? null : card.brand())
            .param(card == null ? null : card.last4())
            .param(card == null ? null : card.expMonth())
            .param(card == null ? null : card.expYear())
            .param(card == null ? null : card.funding())
            .param(json.writeValueAsString(fixture.providerIds()))
            .param(fixture.defaultScenario())
            .param(createdAt.atOffset(ZoneOffset.UTC))
            .update();
    if (claimed == 0) {
      status.setRollbackOnly();
      return new Seeding.Claimed(ALIAS, fixture.fixtureAlias());
    }
    for (Map.Entry<String, String> named : fixture.providerIds().entrySet()) {
      boolean alone = !theKeysManyFixturesMayShare.contains(named.getKey());
      if (!claimProviderId(fixture, fixtureId, named.getKey(), named.getValue(), alone)) {
        status.setRollbackOnly();
        return new Seeding.Claimed("providerIds." + named.getKey(), named.getValue());
      }
    }
    PaymentMethodFixture seeded =
        new PaymentMethodFixture(
            fixtureId,
            fixture.provider(),
            fixture.testRunId(),
            fixture.merchantId(),
            fixture.ownerId(),
            fixture.type(),
            fixture.fixtureAlias(),
            card,
            fixture.providerIds(),
            fixture.defaultScenario(),
            createdAt);
    plan.store(seeded);
    return new Seeding.Seeded(seeded);
  }

  private boolean claimProviderId(
      NewPaymentMethodFixture fixture, String fixtureId, String key, String value, boolean alone) {
    return jdbc.sql(
                """
            insert into simulator_payment_method_fixture_claim
                (provider, test_run_id, id_key, id_value, fixture_id, claims_the_value_alone)
            values (?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """)
            .param(fixture.provider().value())
            .param(TestRunId.storableNamespace(fixture.testRunId()).value())
            .param(key)
            .param(value)
            .param(fixtureId)
            .param(alone)
            .update()
        == 1;
  }

  @Override
  public List<PaymentMethodFixture> of(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(
            """
            select * from simulator_payment_method_fixture
            where provider = ? and test_run_id = ?
            order by seq
            """)
        .params(provider.value(), TestRunId.storableNamespace(testRunId).value())
        .query(this::fixtureOf)
        .list();
  }

  @Override
  public Optional<PaymentMethodFixture> find(
      ProviderId provider, TestRunId testRunId, String fixtureId) {
    return jdbc.sql(
            """
            select * from simulator_payment_method_fixture
            where provider = ? and test_run_id = ? and fixture_id = ?
            """)
        .params(provider.value(), TestRunId.storableNamespace(testRunId).value(), fixtureId)
        .query(this::fixtureOf)
        .optional();
  }

  @Override
  public boolean remove(
      ProviderId provider,
      TestRunId testRunId,
      String fixtureId,
      PaymentMethodFixtureRemoval removal) {
    return Boolean.TRUE.equals(
        unitOfWork.execute(
            status -> removeInOneTransaction(provider, testRunId, fixtureId, removal)));
  }

  private boolean removeInOneTransaction(
      ProviderId provider,
      TestRunId testRunId,
      String fixtureId,
      PaymentMethodFixtureRemoval removal) {
    int removed =
        jdbc.sql(
                """
                delete from simulator_payment_method_fixture
                where provider = ? and test_run_id = ? and fixture_id = ?
                """)
            .params(provider.value(), TestRunId.storableNamespace(testRunId).value(), fixtureId)
            .update();
    if (removed == 0) {
      return false;
    }
    removal.remove();
    return true;
  }

  @Override
  public int clear(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(
            """
            delete from simulator_payment_method_fixture
            where provider = ? and test_run_id = ?
            """)
        .params(provider.value(), TestRunId.storableNamespace(testRunId).value())
        .update();
  }

  @Override
  public int clearAll(ProviderId provider) {
    return jdbc.sql(
            """
            delete from simulator_payment_method_fixture where provider = ?
            """)
        .param(provider.value())
        .update();
  }

  private PaymentMethodFixture fixtureOf(ResultSet rs, int row) throws SQLException {
    PaymentMethodKind type = PaymentMethodKind.valueOf(rs.getString("method_type"));
    return new PaymentMethodFixture(
        rs.getString("fixture_id"),
        ProviderId.of(rs.getString("provider")),
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("merchant_id"),
        rs.getString("owner_id"),
        type,
        rs.getString("fixture_alias"),
        cardOf(rs, type),
        json.readValue(rs.getString("provider_ids"), PROVIDER_IDS),
        rs.getString("default_scenario"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }

  private static @Nullable FixtureCard cardOf(ResultSet rs, PaymentMethodKind type)
      throws SQLException {
    if (!type.describesACard()) {
      return null;
    }
    return new FixtureCard(
        rs.getString("card_brand"),
        rs.getString("card_last4"),
        rs.getInt("card_exp_month"),
        rs.getInt("card_exp_year"),
        rs.getString("card_funding"));
  }
}
