package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;

interface StripeCharges {

  boolean claimChargeId(TestRunId testRunId, String chargeId);

  Optional<StripeCharge> find(TestRunId testRunId, String chargeId);

  void clear(TestRunId testRunId);

  void clearAll();
}
