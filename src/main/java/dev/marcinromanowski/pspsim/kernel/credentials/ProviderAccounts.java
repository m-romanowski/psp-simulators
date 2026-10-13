package dev.marcinromanowski.pspsim.kernel.credentials;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;

public interface ProviderAccounts {

  void merge(
      ProviderId provider,
      TestRunId testRunId,
      List<String> accountIds,
      List<WebhookTarget> webhookTargets,
      ProviderConfigurationPlan plan);

  boolean remove(
      ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan);

  List<String> identitiesOf(ProviderId provider, TestRunId testRunId);

  List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId);
}
