package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredWebhookTarget(@Nullable String accountId, @Nullable String url) {}
