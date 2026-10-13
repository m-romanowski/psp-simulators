package dev.marcinromanowski.pspsim;

import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MergedIdentities {

  private MergedIdentities() {}

  public static boolean outOf(
      Map<TestRunId, List<String>> identities,
      Map<TestRunId, List<WebhookTarget>> targets,
      TestRunId testRunId,
      String accountId) {
    List<String> held = identities.get(testRunId);
    if (held == null || !held.remove(accountId)) {
      return false;
    }
    List<WebhookTarget> delivering = targets.get(testRunId);
    if (delivering != null) {
      delivering.removeIf(target -> target.accountId().equals(accountId));
    }
    return true;
  }

  public static void into(
      Map<TestRunId, List<String>> identities,
      Map<TestRunId, List<WebhookTarget>> targets,
      TestRunId testRunId,
      List<String> accountIds,
      List<WebhookTarget> webhookTargets) {
    List<String> held = identities.computeIfAbsent(testRunId, run -> new ArrayList<>());
    for (String accountId : accountIds) {
      if (!held.contains(accountId)) {
        held.add(accountId);
      }
    }
    held.sort(String::compareTo);
    Set<String> named = new LinkedHashSet<>(accountIds);
    for (WebhookTarget target : webhookTargets) {
      named.add(target.accountId());
    }
    List<WebhookTarget> delivering = targets.computeIfAbsent(testRunId, run -> new ArrayList<>());
    delivering.removeIf(target -> named.contains(target.accountId()));
    delivering.addAll(webhookTargets);
  }
}
