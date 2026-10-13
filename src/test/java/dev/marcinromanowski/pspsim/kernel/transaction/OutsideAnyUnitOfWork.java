package dev.marcinromanowski.pspsim.kernel.transaction;

import java.util.function.Supplier;

public final class OutsideAnyUnitOfWork {

  public static final AfterCommit RUNS_AT_ONCE =
      new AfterCommit() {
        @Override
        public void run(Runnable work) {
          work.run();
        }

        @Override
        public <T> T outsideAnyTransaction(Supplier<T> work) {
          return work.get();
        }
      };

  private OutsideAnyUnitOfWork() {}
}
