package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcStripeCharges implements StripeCharges {

  private final JdbcClient jdbc;

  JdbcStripeCharges(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean claimChargeId(TestRunId testRunId, String chargeId) {
    return jdbc.sql(
                """
                insert into stripe_charge_id_claim (test_run_id, charge_id)
                values (?, ?)
                on conflict (test_run_id, charge_id) do nothing
                """)
            .params(testRunId.value(), chargeId)
            .update()
        == 1;
  }

  @Override
  public Optional<StripeCharge> find(TestRunId testRunId, String chargeId) {
    return jdbc.sql("select * from stripe_charge where test_run_id = ? and charge_id = ?")
        .params(testRunId.value(), chargeId)
        .query(JdbcStripeCharges::toCharge)
        .optional();
  }

  @Override
  public void clear(TestRunId testRunId) {
    jdbc.sql("delete from stripe_charge_id_claim where test_run_id = ?")
        .param(testRunId.value())
        .update();
    jdbc.sql("delete from stripe_charge where test_run_id = ?").param(testRunId.value()).update();
  }

  @Override
  public void clearAll() {
    jdbc.sql("delete from stripe_charge_id_claim").update();
    jdbc.sql("delete from stripe_charge").update();
  }

  private static StripeCharge toCharge(ResultSet rs, int row) throws SQLException {
    return new StripeCharge(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("charge_id"),
        rs.getString("intent_id"),
        rs.getString("account_id"),
        Amount.of(rs.getString("amount")),
        rs.getString("currency"),
        rs.getString("payment_method"),
        StripeChargeStatus.of(rs.getString("status")).orElseThrow(),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
