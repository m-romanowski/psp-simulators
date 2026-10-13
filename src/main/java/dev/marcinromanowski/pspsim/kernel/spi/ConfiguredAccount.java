package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Map;

public record ConfiguredAccount(String accountId, Map<String, String> configuration) {}
