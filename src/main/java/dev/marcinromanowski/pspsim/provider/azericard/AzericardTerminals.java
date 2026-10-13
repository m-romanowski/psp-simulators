package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;

interface AzericardTerminals {

  void merge(TestRunId testRunId, List<AzericardTerminal> terminals);

  void remove(TestRunId testRunId, String accountId);

  Optional<AzericardTerminal> ofTheRunHolding(TestRunId testRunId, String terminalId);

  List<AzericardTerminal> byTerminalId(String terminalId);

  Optional<AzericardTerminal> find(TestRunId testRunId, String accountId);
}
