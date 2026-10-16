package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface TranzilaKeyedAnswering {

  TranzilaKeyedAnswering AS_ANSWERED = result -> result.body().asAKeyedAnswer();

  @Nullable KeyedAnswer of(TranzilaOperationResult result);
}
