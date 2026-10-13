package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.List;
import java.util.Map;

public record DisclosedAccount(String accountId, Map<String, List<String>> disclosed) {}
