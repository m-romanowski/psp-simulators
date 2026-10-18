package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;

interface TranzilaTerminals {

  void merge(TestRunId testRunId, List<TranzilaTerminal> configured);

  void remove(TestRunId testRunId, String accountId);

  Optional<TranzilaTerminal> find(TestRunId testRunId, String accountId);

  List<TranzilaTerminal> ofTheRun(TestRunId testRunId);

  List<TranzilaTerminal> byAppKey(String appKey);

  List<TranzilaTerminal> named(String terminalName);
}
