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

final class JdbcTranzilaPages implements TranzilaPages {

  private final JdbcClient jdbc;

  JdbcTranzilaPages(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int open(TranzilaPage page) {
    return jdbc.sql(
            """
            insert into tranzila_page
                (page_id, test_run_id, account_id, terminal_name, sum, currency, tranmode,
                 success_url, fail_url, notify_url, dc_disable, thtk, request_params, echoed,
                 origin, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """)
        .param(page.pageId())
        .param(page.testRunId().value())
        .param(page.accountId())
        .param(page.terminalName())
        .param(page.sum())
        .param(page.currency().numeric())
        .param(page.tranmode().wire())
        .param(page.successUrl())
        .param(page.failUrl())
        .param(page.notifyUrl())
        .param(page.dcDisable())
        .param(page.thtk())
        .param(page.requestParams())
        .param(TranzilaStoredForm.encoded(page.echoed()))
        .param(page.origin().name())
        .param(page.createdAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public Optional<TranzilaPage> find(String pageId) {
    return jdbc.sql("select * from tranzila_page where page_id = ?")
        .param(pageId)
        .query(JdbcTranzilaPages::toPage)
        .optional();
  }

  @Override
  public Optional<TranzilaPage> find(TestRunId testRunId, String pageId) {
    return jdbc.sql("select * from tranzila_page where test_run_id = ? and page_id = ?")
        .params(testRunId.value(), pageId)
        .query(JdbcTranzilaPages::toPage)
        .optional();
  }

  @Override
  public Optional<TranzilaPage> lockedWhileOpen(TestRunId testRunId, String pageId) {
    return jdbc.sql(
            """
            select * from tranzila_page
            where test_run_id = ? and page_id = ? and outcome is null
            for update
            """)
        .params(testRunId.value(), pageId)
        .query(JdbcTranzilaPages::toPage)
        .optional();
  }

  @Override
  public Optional<TranzilaPage> ofTheTransaction(TestRunId testRunId, long transactionId) {
    return jdbc.sql("select * from tranzila_page where test_run_id = ? and transaction_id = ?")
        .params(testRunId.value(), Long.valueOf(transactionId))
        .query(JdbcTranzilaPages::toPage)
        .optional();
  }

  @Override
  public List<TranzilaPage> allById(TestRunId testRunId, List<String> pageIds) {
    return jdbc.sql(
            """
            select * from tranzila_page where test_run_id = ? and page_id = any(?)
            order by arrival
            """)
        .params(testRunId.value(), pageIds.toArray(String[]::new))
        .query(JdbcTranzilaPages::toPage)
        .list();
  }

  @Override
  public void settle(TranzilaPage settled) {
    jdbc.sql(
            """
            update tranzila_page
            set outcome = ?, transaction_id = ?, response = ?, token = ?, card_mask = ?,
                card_type = ?, settled_at = ?
            where test_run_id = ? and page_id = ?
            """)
        .param(TranzilaStoredForm.nameOf(settled.outcome()))
        .param(settled.transactionId())
        .param(settled.response())
        .param(settled.token())
        .param(settled.cardMask())
        .param(settled.cardType())
        .param(TranzilaStoredForm.timestampOf(settled.settledAt()))
        .param(settled.testRunId().value())
        .param(settled.pageId())
        .update();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String pageId) {
    return jdbc.sql("select arrival from tranzila_page where test_run_id = ? and page_id = ?")
        .params(testRunId.value(), pageId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival.longValue(), pageId));
  }

  @Override
  public List<ArrivalOf<TranzilaPage>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from tranzila_page
            where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(
            (rs, row) -> new ArrivalOf<>(rs.getLong("arrival"), JdbcTranzilaPages.toPage(rs, row)))
        .list();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from tranzila_page where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from tranzila_page").update();
  }

  private static TranzilaPage toPage(ResultSet rs, int row) throws SQLException {
    return new TranzilaPage(
        rs.getString("page_id"),
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("terminal_name"),
        rs.getString("sum"),
        TranzilaStoredForm.currencyOf(rs.getString("currency")),
        TranzilaStoredForm.tranmodeOf(rs.getString("tranmode")),
        rs.getString("success_url"),
        rs.getString("fail_url"),
        rs.getString("notify_url"),
        rs.getString("dc_disable"),
        rs.getString("thtk"),
        rs.getString("request_params"),
        TranzilaStoredForm.decoded(rs.getString("echoed")),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        TranzilaStoredForm.outcomeOf(rs.getString("outcome")),
        rs.getObject("transaction_id", Long.class),
        rs.getString("response"),
        rs.getString("token"),
        rs.getString("card_mask"),
        rs.getObject("card_type", Integer.class),
        TranzilaStoredForm.instantOf(rs.getObject("settled_at", OffsetDateTime.class)));
  }
}
