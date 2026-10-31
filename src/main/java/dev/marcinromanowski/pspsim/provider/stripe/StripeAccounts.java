package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;

interface StripeAccounts {

  void merge(TestRunId testRunId, List<StripeAccount> accounts);

  void remove(TestRunId testRunId, String accountId);

  Optional<StripeAccount> ofTheRunHolding(TestRunId testRunId, String secretKey);

  List<StripeAccount> bySecretKey(String secretKey);

  Optional<StripeAccount> ofTheRunHoldingThePublishableKey(
      TestRunId testRunId, String publishableKey);

  List<StripeAccount> byPublishableKey(String publishableKey);

  Optional<StripeAccount> find(TestRunId testRunId, String accountId);
}
