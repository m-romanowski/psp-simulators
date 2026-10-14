package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface TranzilaRecords {

  Optional<TranzilaRecorded> byIndex(TestRunId testRunId, String accountId, long transactionId);

  List<TranzilaRecorded> between(
      TestRunId testRunId, String accountId, TranzilaLookup.Window window);

  long countedBetween(TestRunId testRunId, String accountId, LocalDate start, LocalDate end);

  List<TranzilaUserSlot> slotsOf(TestRunId testRunId, String accountId, int slots);
}
