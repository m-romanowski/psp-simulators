package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public sealed interface Selection {

  record Chosen(SimulatorProvider module, TestRunId testRunId) implements Selection {

    public String provider() {
      return module.id().value();
    }
  }

  record Unnamed(List<String> known) implements Selection {}

  record Refused(String message) implements Selection {}

  default boolean namesWhatARowIsFiledUnder() {
    return this instanceof Chosen;
  }

  static Selection of(
      @Nullable String provider,
      @Nullable String testRunId,
      ProviderRegistry providers,
      TestRunId whenTestRunAbsent) {
    return theProviderNamed(
        provider, providers, () -> theNamespaceWrittenUnder(testRunId, whenTestRunAbsent));
  }

  static Selection ofAScreenWhoseSubjectIsTheTrail(
      @Nullable String provider,
      @Nullable String testRunId,
      ProviderRegistry providers,
      TestRunId whenTestRunAbsent) {
    return theProviderNamed(
        provider, providers, () -> theNamespaceRead(testRunId, whenTestRunAbsent));
  }

  private static TestRunId theNamespaceWrittenUnder(
      @Nullable String testRunId, TestRunId whenTestRunAbsent) {
    if (TestRunId.UNKNOWN.value().equals(testRunId)) {
      throw new IllegalArgumentException(
          TheReservedRunOnAScreen.theRefusalOfAScreenThatShowsWhatARunHolds(whenTestRunAbsent));
    }
    return TestRunId.declaredOr(testRunId, whenTestRunAbsent);
  }

  private static TestRunId theNamespaceRead(
      @Nullable String testRunId, TestRunId whenTestRunAbsent) {
    if (testRunId == null || testRunId.isBlank()) {
      return whenTestRunAbsent;
    }
    return TestRunId.storedNamespace(testRunId.strip());
  }

  private static Selection theProviderNamed(
      @Nullable String provider, ProviderRegistry providers, Supplier<TestRunId> theRunItNames) {
    List<String> known = providers.names();
    if (known.isEmpty()) {
      return new Refused("this simulator has no provider module, so there is nothing to show");
    }
    if (provider == null || provider.isBlank()) {
      return new Unnamed(known);
    }
    Optional<SimulatorProvider> module = providers.find(provider.strip());
    if (module.isEmpty()) {
      return new Refused(providers.theRefusalOfAnUnknownProvider());
    }
    try {
      return new Chosen(module.get(), theRunItNames.get());
    } catch (IllegalArgumentException malformed) {
      return new Refused(String.valueOf(malformed.getMessage()));
    }
  }
}
