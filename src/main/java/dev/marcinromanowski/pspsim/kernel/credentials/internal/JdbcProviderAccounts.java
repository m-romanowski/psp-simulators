package dev.marcinromanowski.pspsim.kernel.credentials.internal;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcProviderAccounts implements ProviderAccounts {

  private final JdbcClient jdbc;
  private final SimulatorClock clock;
  private final TransactionTemplate transactions;

  public JdbcProviderAccounts(
      JdbcClient jdbc, SimulatorClock clock, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.transactions = transactions;
  }

  @Override
  public void merge(
      ProviderId provider,
      TestRunId testRunId,
      List<String> accountIds,
      List<WebhookTarget> webhookTargets,
      ProviderConfigurationPlan plan) {
    transactions.executeWithoutResult(
        status -> mergeInOneTransaction(provider, testRunId, accountIds, webhookTargets, plan));
  }

  private void mergeInOneTransaction(
      ProviderId provider,
      TestRunId testRunId,
      List<String> accountIds,
      List<WebhookTarget> webhookTargets,
      ProviderConfigurationPlan plan) {
    plan.store();
    OffsetDateTime updatedAt = clock.now(testRunId).atOffset(ZoneOffset.UTC);
    for (String accountId : accountIds) {
      jdbc.sql(
              """
              insert into simulator_provider_account
                  (provider, test_run_id, account_id, updated_at)
              values (?, ?, ?, ?)
              on conflict (provider, test_run_id, account_id)
                  do update set updated_at = excluded.updated_at
              """)
          .param(provider.value())
          .param(testRunId.value())
          .param(accountId)
          .param(updatedAt)
          .update();
    }
    for (String accountId : theAccountsThisConfigurationNamed(accountIds, webhookTargets)) {
      jdbc.sql(
              """
              delete from simulator_webhook_target
              where provider = ? and test_run_id = ? and account_id = ?
              """)
          .params(provider.value(), testRunId.value(), accountId)
          .update();
    }
    for (WebhookTarget target : webhookTargets) {
      jdbc.sql(
              """
              insert into simulator_webhook_target
                  (provider, test_run_id, account_id, url, updated_at)
              values (?, ?, ?, ?, ?)
              on conflict (provider, test_run_id, account_id, url) do nothing
              """)
          .param(provider.value())
          .param(testRunId.value())
          .param(target.accountId())
          .param(target.url())
          .param(updatedAt)
          .update();
    }
  }

  @Override
  public boolean remove(
      ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
    return Boolean.TRUE.equals(
        transactions.execute(
            status -> removeInOneTransaction(provider, testRunId, accountId, plan)));
  }

  private boolean removeInOneTransaction(
      ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
    int identities =
        jdbc.sql(
                """
                delete from simulator_provider_account
                where provider = ? and test_run_id = ? and account_id = ?
                """)
            .params(provider.value(), testRunId.value(), accountId)
            .update();
    if (identities == 0) {
      return false;
    }
    jdbc.sql(
            """
            delete from simulator_webhook_target
            where provider = ? and test_run_id = ? and account_id = ?
            """)
        .params(provider.value(), testRunId.value(), accountId)
        .update();
    plan.remove();
    return true;
  }

  private static Set<String> theAccountsThisConfigurationNamed(
      List<String> accountIds, List<WebhookTarget> webhookTargets) {
    Set<String> named = new LinkedHashSet<>(accountIds);
    for (WebhookTarget target : webhookTargets) {
      named.add(target.accountId());
    }
    return named;
  }

  @Override
  public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(
            """
            select account_id
            from simulator_provider_account
            where provider = ? and test_run_id = ?
            order by account_id
            """)
        .params(provider.value(), testRunId.value())
        .query((rs, row) -> rs.getString("account_id"))
        .list();
  }

  @Override
  public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(
            """
            select account_id, url
            from simulator_webhook_target
            where provider = ? and test_run_id = ?
            order by account_id, url
            """)
        .params(provider.value(), testRunId.value())
        .query((rs, row) -> new WebhookTarget(rs.getString("account_id"), rs.getString("url")))
        .list();
  }
}
