package dev.marcinromanowski.pspsim.kernel.transaction;

import java.util.function.Supplier;

public interface AfterCommit {

  void run(Runnable work);

  <T> T outsideAnyTransaction(Supplier<T> work);
}
