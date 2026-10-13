package dev.marcinromanowski.pspsim.kernel.bundle;

import org.jspecify.annotations.Nullable;

public record PortableWebhookTarget(@Nullable String accountId, @Nullable String url) {}
