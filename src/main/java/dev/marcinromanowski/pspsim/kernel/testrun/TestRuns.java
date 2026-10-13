package dev.marcinromanowski.pspsim.kernel.testrun;

import java.util.List;

public interface TestRuns {

  void register(TestRunId testRunId);

  boolean exists(TestRunId testRunId);

  List<TestRunId> all();

  int forget(TestRunId testRunId);
}
