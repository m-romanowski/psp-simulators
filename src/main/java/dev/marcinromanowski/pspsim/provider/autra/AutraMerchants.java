package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;

interface AutraMerchants {

  void merge(TestRunId testRunId, List<AutraMerchant> configured);

  void remove(TestRunId testRunId, String accountId);

  Optional<AutraMerchant> find(TestRunId testRunId, String accountId);

  Optional<AutraMerchant> ofTheRunHolding(TestRunId testRunId, String documentId);

  List<AutraMerchant> byClientId(String clientId);
}
