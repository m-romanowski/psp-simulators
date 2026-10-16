package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.scenario.OperationCodes;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class VocabularyCodes implements OperationCodes {

  private final Supplier<ProviderRegistry> providers;

  public VocabularyCodes(Supplier<ProviderRegistry> providers) {
    this.providers = providers;
  }

  @Override
  public boolean answers(ProviderId provider, @Nullable String operation, String code) {
    return providers
        .get()
        .find(provider.value())
        .map(module -> module.vocabulary().theCodesOf(operation).admits(code))
        .orElse(Boolean.TRUE);
  }
}
