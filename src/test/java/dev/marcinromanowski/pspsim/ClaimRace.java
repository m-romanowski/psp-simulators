package dev.marcinromanowski.pspsim;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public final class ClaimRace {

  private final TransactionTemplate apart;
  private final AtomicReference<Runnable> armed = new AtomicReference<>();

  ClaimRace(PlatformTransactionManager transactionManager) {
    this.apart = new TransactionTemplate(transactionManager);
    this.apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void commitsBeforeTheNextWrite(Runnable claim) {
    armed.set(claim);
  }

  public void disarm() {
    armed.set(null);
  }

  void commitTheArmedClaim() {
    Runnable claim = armed.getAndSet(null);
    if (claim != null) {
      apart.executeWithoutResult(status -> claim.run());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Wiring {

    @Bean
    ClaimRace claimRace(PlatformTransactionManager transactionManager) {
      return new ClaimRace(transactionManager);
    }

    @Bean
    @Primary
    ProviderAccounts claimingBeforeTheWrite(
        @Qualifier("providerAccounts") ProviderAccounts written, ClaimRace race) {
      return new ClaimingBeforeTheWrite(written, race);
    }
  }

  private record ClaimingBeforeTheWrite(ProviderAccounts written, ClaimRace race)
      implements ProviderAccounts {

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      race.commitTheArmedClaim();
      written.merge(provider, testRunId, accountIds, webhookTargets, plan);
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      return written.remove(provider, testRunId, accountId, plan);
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return written.identitiesOf(provider, testRunId);
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return written.webhookTargetsOf(provider, testRunId);
    }
  }
}
