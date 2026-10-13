package dev.marcinromanowski.pspsim.kernel.smoke;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class SmokeRunner {

  private final Supplier<Instant> clock;
  private final CheckRunAccounts accounts;
  private final AtomicBoolean running = new AtomicBoolean();

  public SmokeRunner(Supplier<Instant> clock, CheckRunAccounts accounts) {
    this.clock = clock;
    this.accounts = accounts;
  }

  public SmokeRun run(List<ContractCheck> checks, CheckTransport transport) {
    if (!running.compareAndSet(false, true)) {
      throw new ChecksAlreadyRunningException();
    }
    try {
      return alone(checks, transport);
    } finally {
      running.set(false);
    }
  }

  private SmokeRun alone(List<ContractCheck> checks, CheckTransport transport) {
    Instant startedAt = clock.get();
    List<SmokeVerdict> verdicts = new ArrayList<>();
    for (ContractCheck check : checks) {
      verdicts.add(verdictOf(check, transport));
      removeTheAccountsItConfigured(check.provider(), transport);
    }
    return new SmokeRun(startedAt, Duration.between(startedAt, clock.get()), verdicts);
  }

  private void removeTheAccountsItConfigured(ProviderId provider, CheckTransport transport) {
    for (String accountId : accounts.configuredFor(provider)) {
      transport.send(
          CheckRequest.delete(
              SmokeNamespace.CONTROL_ROUTES
                  + provider.value()
                  + "/config/accounts/"
                  + URLEncoder.encode(accountId, StandardCharsets.UTF_8).replace("+", "%20")));
    }
  }

  private SmokeVerdict verdictOf(ContractCheck check, CheckTransport transport) {
    if (check.applicability() instanceof Applicability.DoesNotApply withheld) {
      return new SmokeVerdict.NotApplicable(
          check.provider(), check.operation(), check.provenance(), withheld.reason());
    }
    List<ContractExpectation> expectations = check.expectations();
    if (expectations.isEmpty()) {
      return new SmokeVerdict.Failed(
          check.provider(),
          check.operation(),
          check.provenance(),
          check.operation(),
          FailureKind.ASSERTS_NOTHING);
    }
    Map<String, String> observed;
    try {
      observed = check.observe(transport);
    } catch (RuntimeException unreachable) {
      return new SmokeVerdict.Failed(
          check.provider(),
          check.operation(),
          check.provenance(),
          check.operation(),
          FailureKind.UNREACHABLE);
    }
    for (ContractExpectation expectation : expectations) {
      @Nullable String read = observed.get(expectation.field());
      if (read == null) {
        return new SmokeVerdict.Failed(
            check.provider(),
            check.operation(),
            expectation.published().provenance(),
            expectation.field(),
            FailureKind.NOT_OBSERVED);
      }
      if (!expectation.isMetBy(read)) {
        return new SmokeVerdict.Failed(
            check.provider(),
            check.operation(),
            expectation.published().provenance(),
            expectation.field(),
            FailureKind.NOT_AS_PUBLISHED);
      }
    }
    return new SmokeVerdict.Passed(
        check.provider(), check.operation(), check.provenance(), expectations.size());
  }
}
