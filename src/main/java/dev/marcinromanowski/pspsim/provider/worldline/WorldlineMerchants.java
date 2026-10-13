package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;

interface WorldlineMerchants {

  void merge(TestRunId testRunId, List<WorldlineMerchant> configured);

  void remove(TestRunId testRunId, String accountId);

  Optional<WorldlineMerchant> find(TestRunId testRunId, String accountId);

  Optional<WorldlineMerchant> ofTheRunHolding(TestRunId testRunId, String merchantId);

  List<WorldlineMerchant> byApiKey(String apiKey);
}
