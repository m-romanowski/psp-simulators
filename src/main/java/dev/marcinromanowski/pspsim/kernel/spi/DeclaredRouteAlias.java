package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredRouteAlias(@Nullable String from, @Nullable String to) {}
