package dev.marcinromanowski.pspsim.kernel.transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class RecordedUnitsOfWork implements UnitOfWork {

  public enum Ending {
    KEPT,
    DISCARDED,
    THREW
  }

  private final List<Ending> endings = new ArrayList<>();
  private int depth;
  private int opened;
  private boolean rollbackOnly;

  @Override
  public <T> T keepingOnly(Predicate<? super T> kept, Supplier<T> work) {
    boolean outermost = depth == 0;
    if (outermost) {
      opened = endings.size();
      rollbackOnly = false;
    }
    endings.add(Ending.THREW);
    depth++;
    try {
      T done = work.get();
      if (!kept.test(done)) {
        rollbackOnly = true;
      } else if (rollbackOnly) {
        throw new KeptAfterADiscardedUnitException(
            KeptAfterADiscardedUnitException.A_DISCARDED_UNIT_CANNOT_BE_KEPT_BY_THE_UNIT_AROUND_IT);
      }
      if (outermost) {
        settle(rollbackOnly ? Ending.DISCARDED : Ending.KEPT);
      }
      return done;
    } catch (RuntimeException failed) {
      rollbackOnly = true;
      if (outermost) {
        settle(Ending.THREW);
      }
      throw failed;
    } finally {
      depth--;
    }
  }

  private void settle(Ending ending) {
    Collections.fill(endings.subList(opened, endings.size()), ending);
  }

  public List<Ending> endings() {
    return List.copyOf(endings);
  }
}
