package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DeclaredAccount(
    @Nullable String accountId, @Nullable Map<String, String> configuration) {}
