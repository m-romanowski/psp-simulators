package dev.marcinromanowski.pspsim.kernel.transaction;

import java.util.function.Predicate;
import java.util.function.Supplier;

public interface UnitOfWork {

  <T> T keepingOnly(Predicate<? super T> kept, Supplier<T> work);
}
