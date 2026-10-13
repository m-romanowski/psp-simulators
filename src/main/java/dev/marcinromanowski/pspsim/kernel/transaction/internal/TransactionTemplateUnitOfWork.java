package dev.marcinromanowski.pspsim.kernel.transaction.internal;

import dev.marcinromanowski.pspsim.kernel.transaction.AfterCommit;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.InsideATransactionException;
import dev.marcinromanowski.pspsim.kernel.transaction.KeptAfterADiscardedUnitException;
import dev.marcinromanowski.pspsim.kernel.transaction.NoUnitOwnsThisTransactionException;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class TransactionTemplateUnitOfWork implements UnitOfWork, AfterCommit {

  static final String THE_WORK_ANSWERED_NOTHING = "a unit of work answered nothing";

  private static final ThreadLocal<List<Runnable>> AFTER_THE_OUTERMOST_COMMIT = new ThreadLocal<>();

  private final TransactionTemplate transactions;
  private final Set<String> claims;

  public TransactionTemplateUnitOfWork(
      TransactionTemplate transactions, List<ClaimConstraints> claims) {
    this.transactions = transactions;
    this.claims =
        claims.stream()
            .flatMap(declared -> declared.names().stream())
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public <T> T keepingOnly(Predicate<? super T> kept, Supplier<T> work) {
    if (AFTER_THE_OUTERMOST_COMMIT.get() != null
        || TransactionSynchronizationManager.isActualTransactionActive()) {
      return inOneTransaction(kept, work);
    }
    List<Runnable> afterCommit = new ArrayList<>();
    AFTER_THE_OUTERMOST_COMMIT.set(afterCommit);
    T done;
    try {
      done = inOneTransaction(kept, work);
    } finally {
      AFTER_THE_OUTERMOST_COMMIT.remove();
    }
    if (kept.test(done)) {
      runEveryOne(afterCommit);
    }
    return done;
  }

  private static void runEveryOne(List<Runnable> afterCommit) {
    RuntimeException first = null;
    for (Runnable work : afterCommit) {
      try {
        work.run();
      } catch (RuntimeException failed) {
        if (first == null) {
          first = failed;
        } else {
          first.addSuppressed(failed);
        }
      }
    }
    if (first != null) {
      throw first;
    }
  }

  @Override
  public void run(Runnable work) {
    List<Runnable> afterCommit = AFTER_THE_OUTERMOST_COMMIT.get();
    if (afterCommit != null) {
      afterCommit.add(work);
    } else if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new NoUnitOwnsThisTransactionException(
          NoUnitOwnsThisTransactionException.WORK_AFTER_A_COMMIT_NEEDS_A_UNIT_THAT_OWNS_IT);
    } else {
      work.run();
    }
  }

  @Override
  public <T> T outsideAnyTransaction(Supplier<T> work) {
    if (AFTER_THE_OUTERMOST_COMMIT.get() != null
        || TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new InsideATransactionException(
          InsideATransactionException.WHAT_REPORTS_WHAT_IT_DID_RUNS_OUTSIDE_ANY_TRANSACTION);
    }
    return work.get();
  }

  private <T> T inOneTransaction(Predicate<? super T> kept, Supplier<T> work) {
    try {
      return Objects.requireNonNull(
          transactions.execute(
              status -> {
                T done = work.get();
                if (!kept.test(done)) {
                  status.setRollbackOnly();
                } else if (status.isRollbackOnly()) {
                  throw new KeptAfterADiscardedUnitException(
                      KeptAfterADiscardedUnitException
                          .A_DISCARDED_UNIT_CANNOT_BE_KEPT_BY_THE_UNIT_AROUND_IT);
                }
                return done;
              }),
          THE_WORK_ANSWERED_NOTHING);
    } catch (DuplicateKeyException duplicate) {
      if (theConstraintViolatedBy(duplicate).filter(claims::contains).isPresent()) {
        throw new ClaimedMeanwhileException(
            ClaimedMeanwhileException.A_VALUE_THIS_REQUEST_WRITES_WAS_CLAIMED_MEANWHILE);
      }
      throw duplicate;
    }
  }

  private static Optional<String> theConstraintViolatedBy(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof PSQLException reported) {
        return Optional.ofNullable(reported.getServerErrorMessage())
            .map(TransactionTemplateUnitOfWork::constraintOf);
      }
    }
    return Optional.empty();
  }

  private static @Nullable String constraintOf(ServerErrorMessage message) {
    return message.getConstraint();
  }
}
