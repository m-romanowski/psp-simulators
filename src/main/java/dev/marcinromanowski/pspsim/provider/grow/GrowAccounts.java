package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

interface GrowAccounts {

  void merge(TestRunId testRunId, List<GrowAccount> accounts);

  void remove(TestRunId testRunId, String accountId);

  List<GrowAccount> ofTheRunHoldingEither(TestRunId testRunId, String userId, String pageCode);

  List<GrowAccount> byUserIdAndPageCode(String userId, String pageCode);

  List<GrowAccount> byUserId(String userId);

  List<GrowAccount> byPageCode(String pageCode);

  List<GrowAccount> byEitherCredential(@Nullable String userId, @Nullable String pageCode);

  Optional<GrowAccount> find(TestRunId testRunId, String accountId);

  List<GrowAccount> allById(TestRunId testRunId, List<String> accountIds);
}
