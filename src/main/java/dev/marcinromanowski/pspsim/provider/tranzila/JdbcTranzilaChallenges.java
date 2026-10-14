package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcTranzilaChallenges implements TranzilaChallenges {

  private final JdbcClient jdbc;

  JdbcTranzilaChallenges(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int open(TranzilaChallenge challenge) {
    return jdbc.sql(
            """
            insert into tranzila_challenge
                (track_id, test_run_id, account_id, terminal_name, transaction_id, txn_type,
                 verify_mode, amount, currency_code, token, card_mask, card_type, expiry_month,
                 expiry_year, response_cvv, response_id, dc_disable, force_txn_on_3ds_fail,
                 original_request, redirect_url, redirect_params, hosted_fields,
                 hosted_form_data, origin, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """)
        .param(challenge.trackId())
        .param(challenge.testRunId().value())
        .param(challenge.accountId())
        .param(challenge.terminalName())
        .param(Long.valueOf(challenge.transactionId()))
        .param(challenge.type().wire())
        .param(challenge.verifyMode())
        .param(challenge.amount())
        .param(challenge.currencyCode())
        .param(challenge.token())
        .param(challenge.card().cardMask())
        .param(Integer.valueOf(challenge.card().cardType()))
        .param(Integer.valueOf(challenge.card().expiryMonth()))
        .param(Integer.valueOf(challenge.card().expiryYear()))
        .param(challenge.responseCvv())
        .param(challenge.responseId())
        .param(challenge.dcDisable())
        .param(Boolean.valueOf(challenge.forceTxnOnFail()))
        .param(challenge.originalRequest())
        .param(challenge.redirectUrl())
        .param(TranzilaStoredForm.encoded(challenge.redirectParams()))
        .param(TranzilaStoredForm.theHostedFieldsOf(challenge.hostedFields()))
        .param(TranzilaStoredForm.theFormDataOf(challenge.hostedFields()))
        .param(challenge.origin().name())
        .param(challenge.createdAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public Optional<TranzilaChallenge> find(String trackId) {
    return jdbc.sql("select * from tranzila_challenge where track_id = ?")
        .param(trackId)
        .query(JdbcTranzilaChallenges::toChallenge)
        .optional();
  }

  @Override
  public Optional<TranzilaChallenge> find(TestRunId testRunId, String trackId) {
    return jdbc.sql("select * from tranzila_challenge where test_run_id = ? and track_id = ?")
        .params(testRunId.value(), trackId)
        .query(JdbcTranzilaChallenges::toChallenge)
        .optional();
  }

  @Override
  public Optional<TranzilaChallenge> lockedUntilCompleted(
      TestRunId testRunId, String accountId, String trackId) {
    return jdbc.sql(
            """
            select * from tranzila_challenge
            where test_run_id = ? and account_id = ? and track_id = ? and completed_at is null
            for update
            """)
        .params(testRunId.value(), accountId, trackId)
        .query(JdbcTranzilaChallenges::toChallenge)
        .optional();
  }

  @Override
  public int answer(TranzilaChallenge answered) {
    return jdbc.sql(
            """
            update tranzila_challenge set payer_verdict = ?, answered_at = ?
            where track_id = ? and payer_verdict is null
            """)
        .param(TranzilaStoredForm.verdictNameOf(answered.payerVerdict()))
        .param(TranzilaStoredForm.timestampOf(answered.answeredAt()))
        .param(answered.trackId())
        .update();
  }

  @Override
  public int complete(TranzilaChallenge completed) {
    return jdbc.sql(
            """
            update tranzila_challenge set transaction_id = ?, completed_at = ?
            where track_id = ? and completed_at is null
            """)
        .param(Long.valueOf(completed.transactionId()))
        .param(TranzilaStoredForm.timestampOf(completed.completedAt()))
        .param(completed.trackId())
        .update();
  }

  @Override
  public List<TranzilaChallenge> allById(TestRunId testRunId, List<String> trackIds) {
    return jdbc.sql(
            """
            select * from tranzila_challenge where test_run_id = ? and track_id = any(?)
            order by arrival
            """)
        .params(testRunId.value(), trackIds.toArray(String[]::new))
        .query(JdbcTranzilaChallenges::toChallenge)
        .list();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String trackId) {
    return jdbc.sql("select arrival from tranzila_challenge where test_run_id = ? and track_id = ?")
        .params(testRunId.value(), trackId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival.longValue(), trackId));
  }

  @Override
  public List<ArrivalOf<TranzilaChallenge>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from tranzila_challenge
            where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(
            (rs, row) ->
                new ArrivalOf<>(rs.getLong("arrival"), JdbcTranzilaChallenges.toChallenge(rs, row)))
        .list();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from tranzila_challenge where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from tranzila_challenge").update();
  }

  private static TranzilaChallenge toChallenge(ResultSet rs, int row) throws SQLException {
    return new TranzilaChallenge(
        rs.getString("track_id"),
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("terminal_name"),
        rs.getLong("transaction_id"),
        TranzilaStoredForm.typeOf(rs.getString("txn_type")),
        rs.getObject("verify_mode", Integer.class),
        rs.getString("amount"),
        rs.getString("currency_code"),
        rs.getString("token"),
        new TranzilaCard(
            rs.getString("card_mask"),
            rs.getInt("card_type"),
            rs.getInt("expiry_month"),
            rs.getInt("expiry_year")),
        rs.getString("response_cvv"),
        rs.getString("response_id"),
        rs.getString("dc_disable"),
        rs.getBoolean("force_txn_on_3ds_fail"),
        rs.getString("original_request"),
        rs.getString("redirect_url"),
        TranzilaStoredForm.decoded(rs.getString("redirect_params")),
        TranzilaStoredForm.hostedFieldsOf(
            rs.getString("hosted_fields"), rs.getString("hosted_form_data")),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        TranzilaStoredForm.verdictOf(rs.getString("payer_verdict")),
        TranzilaStoredForm.instantOf(rs.getObject("answered_at", OffsetDateTime.class)),
        TranzilaStoredForm.instantOf(rs.getObject("completed_at", OffsetDateTime.class)));
  }
}
