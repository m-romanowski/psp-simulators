package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import org.jspecify.annotations.Nullable;

public interface OperationCodes {

  OperationCodes EVERY_CODE = (provider, operation, code) -> true;

  boolean answers(ProviderId provider, @Nullable String operation, String code);
}
