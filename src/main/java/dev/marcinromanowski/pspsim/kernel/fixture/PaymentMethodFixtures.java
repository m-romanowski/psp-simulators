package dev.marcinromanowski.pspsim.kernel.fixture;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PaymentMethodFixtures {

  Seeding seed(
      NewPaymentMethodFixture fixture,
      PaymentMethodFixturePlan plan,
      Set<String> theKeysManyFixturesMayShare);

  List<PaymentMethodFixture> of(ProviderId provider, TestRunId testRunId);

  Optional<PaymentMethodFixture> find(ProviderId provider, TestRunId testRunId, String fixtureId);

  boolean remove(
      ProviderId provider,
      TestRunId testRunId,
      String fixtureId,
      PaymentMethodFixtureRemoval removal);

  int clear(ProviderId provider, TestRunId testRunId);

  int clearAll(ProviderId provider);
}
