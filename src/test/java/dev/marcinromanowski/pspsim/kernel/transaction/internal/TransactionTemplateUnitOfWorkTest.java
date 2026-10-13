package dev.marcinromanowski.pspsim.kernel.transaction.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.InsideATransactionException;
import dev.marcinromanowski.pspsim.kernel.transaction.KeptAfterADiscardedUnitException;
import dev.marcinromanowski.pspsim.kernel.transaction.NoUnitOwnsThisTransactionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionTemplateUnitOfWorkTest {

  private static final String A_CLAIM = "grow_account_by_user_id";

  private static final String ANOTHER_CLAIM = "simulator_route_alias_pkey";

  private static final String NOT_A_CLAIM = "simulator_test_run_pkey";

  private final Ledger ledger = new Ledger();
  private final TransactionTemplateUnitOfWork unitOfWork =
      new TransactionTemplateUnitOfWork(
          new TransactionTemplate(ledger),
          List.of(
              new ClaimConstraints(Set.of(A_CLAIM)), new ClaimConstraints(Set.of(ANOTHER_CLAIM))));

  @Test
  void work_whose_answer_is_kept_is_committed_and_answered() {
    String answered = unitOfWork.keepingOnly("written"::equals, () -> "written");

    assertThat(answered).isEqualTo("written");
    assertThat(ledger.endings()).containsExactly("committed");
  }

  @Test
  void work_whose_answer_is_not_kept_is_rolled_back_and_still_answered() {
    String answered = unitOfWork.keepingOnly("written"::equals, () -> "refused");

    assertThat(answered).isEqualTo("refused");
    assertThat(ledger.endings()).containsExactly("rolled back");
  }

  @Test
  void a_value_the_database_finds_claimed_under_a_declared_claim_is_answered_as_a_claim() {
    assertThatThrownBy(() -> unitOfWork.keepingOnly(answer -> true, violating(A_CLAIM)))
        .isInstanceOf(ClaimedMeanwhileException.class)
        .hasMessage(ClaimedMeanwhileException.A_VALUE_THIS_REQUEST_WRITES_WAS_CLAIMED_MEANWHILE);
    assertThat(ledger.endings()).containsExactly("rolled back");
  }

  @Test
  void every_module_that_declares_a_claim_is_heard_not_only_the_first() {
    assertThatThrownBy(() -> unitOfWork.keepingOnly(answer -> true, violating(ANOTHER_CLAIM)))
        .isInstanceOf(ClaimedMeanwhileException.class);
  }

  @Test
  void a_unique_violation_no_module_declares_a_claim_reaches_the_caller_as_it_was() {
    DuplicateKeyException duplicate = aDuplicateOf(NOT_A_CLAIM);

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    answer -> true,
                    () -> {
                      throw duplicate;
                    }))
        .isSameAs(duplicate);
    assertThat(ledger.endings()).containsExactly("rolled back");
  }

  @Test
  void a_unique_violation_whose_constraint_the_driver_did_not_report_reaches_the_caller() {
    DuplicateKeyException unnamed =
        new DuplicateKeyException(
            "duplicate key value violates unique constraint \"" + A_CLAIM + "\"");

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    answer -> true,
                    () -> {
                      throw unnamed;
                    }))
        .isSameAs(unnamed);
  }

  @Test
  void a_unique_violation_the_server_described_without_a_message_reaches_the_caller() {
    DuplicateKeyException undescribed =
        new DuplicateKeyException("duplicate", new PSQLException("duplicate", null));

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    answer -> true,
                    () -> {
                      throw undescribed;
                    }))
        .isSameAs(undescribed);
  }

  @Test
  void a_claim_the_driver_reports_below_another_cause_is_still_found() {
    DuplicateKeyException wrapped =
        new DuplicateKeyException(
            "duplicate", new IllegalStateException("batch", thePostgresErrorOf(A_CLAIM)));

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    answer -> true,
                    () -> {
                      throw wrapped;
                    }))
        .isInstanceOf(ClaimedMeanwhileException.class);
  }

  @Test
  void any_other_failure_rolls_back_and_reaches_the_caller_as_it_was() {
    IllegalArgumentException failure = new IllegalArgumentException("not a claim");

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    answer -> true,
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(ledger.endings()).containsExactly("rolled back");
  }

  @Test
  void a_unit_that_keeps_what_a_unit_nested_in_it_discarded_fails_naming_the_mistake() {
    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    answer -> true,
                    () -> unitOfWork.keepingOnly("written"::equals, () -> "refused")))
        .isInstanceOf(KeptAfterADiscardedUnitException.class)
        .hasMessage(
            KeptAfterADiscardedUnitException.A_DISCARDED_UNIT_CANNOT_BE_KEPT_BY_THE_UNIT_AROUND_IT);
    assertThat(ledger.endings()).containsExactly("rolled back");
  }

  @Test
  void a_unit_that_discards_after_a_unit_nested_in_it_discarded_rolls_back_and_answers() {
    String answered =
        unitOfWork.keepingOnly(
            "written"::equals, () -> unitOfWork.keepingOnly("written"::equals, () -> "refused"));

    assertThat(answered).isEqualTo("refused");
    assertThat(ledger.endings()).containsExactly("rolled back");
  }

  @Test
  void a_unit_that_keeps_what_every_unit_nested_in_it_kept_commits() {
    String answered =
        unitOfWork.keepingOnly(
            "written"::equals, () -> unitOfWork.keepingOnly("written"::equals, () -> "written"));

    assertThat(answered).isEqualTo("written");
    assertThat(ledger.endings()).containsExactly("committed");
  }

  @Test
  void work_handed_on_while_no_unit_is_open_runs_at_once() {
    List<String> ran = new ArrayList<>();

    unitOfWork.run(() -> ran.add("sent"));

    assertThat(ran).containsExactly("sent");
    assertThat(ledger.endings()).isEmpty();
  }

  @Test
  void work_handed_on_inside_a_kept_unit_runs_once_that_unit_has_committed() {
    List<String> ran = new ArrayList<>();
    List<String> seenInside = new ArrayList<>();

    unitOfWork.keepingOnly(
        "written"::equals,
        () -> {
          unitOfWork.run(() -> ran.add("sent after " + ledger.endings()));
          seenInside.addAll(ran);
          return "written";
        });

    assertThat(seenInside).isEmpty();
    assertThat(ran).containsExactly("sent after [committed]");
  }

  @Test
  void work_handed_on_inside_a_nested_unit_waits_for_the_outermost_commit() {
    List<String> ran = new ArrayList<>();
    List<String> seenBetween = new ArrayList<>();

    unitOfWork.keepingOnly(
        "outer"::equals,
        () -> {
          unitOfWork.keepingOnly(
              "inner"::equals,
              () -> {
                unitOfWork.run(() -> ran.add("sent after " + ledger.endings()));
                return "inner";
              });
          seenBetween.addAll(ran);
          return "outer";
        });

    assertThat(seenBetween).isEmpty();
    assertThat(ran).containsExactly("sent after [committed]");
  }

  @Test
  void work_handed_on_inside_a_discarded_unit_never_runs() {
    List<String> ran = new ArrayList<>();

    unitOfWork.keepingOnly(
        "written"::equals,
        () -> {
          unitOfWork.run(() -> ran.add("sent"));
          return "refused";
        });

    assertThat(ran).isEmpty();
    assertThat(ledger.endings()).containsExactly("rolled back");
  }

  @Test
  void work_handed_on_inside_a_unit_that_failed_never_runs_and_the_next_runs_at_once() {
    List<String> ran = new ArrayList<>();
    DuplicateKeyException duplicate = aDuplicateOf(NOT_A_CLAIM);

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    answer -> true,
                    () -> {
                      unitOfWork.run(() -> ran.add("sent"));
                      throw duplicate;
                    }))
        .isSameAs(duplicate);
    unitOfWork.run(() -> ran.add("sent with no unit"));

    assertThat(ran).containsExactly("sent with no unit");
  }

  @Test
  void work_handed_on_inside_a_transaction_no_unit_owns_is_refused_and_never_runs() {
    List<String> ran = new ArrayList<>();

    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(() -> unitOfWork.run(() -> ran.add("sent")))
          .isInstanceOf(NoUnitOwnsThisTransactionException.class)
          .hasMessage(
              NoUnitOwnsThisTransactionException.WORK_AFTER_A_COMMIT_NEEDS_A_UNIT_THAT_OWNS_IT);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    assertThat(ran).isEmpty();
  }

  @Test
  void work_handed_on_inside_a_unit_opened_within_a_transaction_no_unit_owns_is_refused() {
    List<String> ran = new ArrayList<>();

    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(
              () ->
                  unitOfWork.keepingOnly(
                      answer -> true,
                      () -> {
                        unitOfWork.run(() -> ran.add("sent"));
                        return "written";
                      }))
          .isInstanceOf(NoUnitOwnsThisTransactionException.class);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    assertThat(ran).isEmpty();
    assertThat(ledger.endings()).containsExactly("rolled back");
  }

  @Test
  void a_failing_task_after_a_commit_does_not_stop_the_tasks_after_it_and_the_first_is_thrown() {
    List<String> ran = new ArrayList<>();
    ClaimedMeanwhileException first = new ClaimedMeanwhileException("the first task failed");
    ClaimedMeanwhileException third = new ClaimedMeanwhileException("the third task failed");

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    "written"::equals,
                    () -> {
                      unitOfWork.run(
                          () -> {
                            throw first;
                          });
                      unitOfWork.run(() -> ran.add("second"));
                      unitOfWork.run(
                          () -> {
                            throw third;
                          });
                      return "written";
                    }))
        .isSameAs(first);

    assertThat(ran).containsExactly("second");
    assertThat(first.getSuppressed()).containsExactly(third);
    assertThat(ledger.endings()).containsExactly("committed");
  }

  @Test
  void what_reports_what_it_did_runs_and_answers_when_no_unit_or_transaction_is_open() {
    assertThat(unitOfWork.outsideAnyTransaction(() -> "attempted")).isEqualTo("attempted");
    assertThat(ledger.endings()).isEmpty();
  }

  @Test
  void what_reports_what_it_did_is_refused_inside_a_unit() {
    List<String> ran = new ArrayList<>();

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    answer -> true,
                    () -> unitOfWork.outsideAnyTransaction(() -> ran.add("attempted"))))
        .isInstanceOf(InsideATransactionException.class)
        .hasMessage(
            InsideATransactionException.WHAT_REPORTS_WHAT_IT_DID_RUNS_OUTSIDE_ANY_TRANSACTION);

    assertThat(ran).isEmpty();
  }

  @Test
  void what_reports_what_it_did_is_refused_inside_a_transaction_no_unit_owns() {
    List<String> ran = new ArrayList<>();

    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(() -> unitOfWork.outsideAnyTransaction(() -> ran.add("attempted")))
          .isInstanceOf(InsideATransactionException.class);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    assertThat(ran).isEmpty();
  }

  private static Supplier<String> violating(String constraint) {
    return () -> {
      throw aDuplicateOf(constraint);
    };
  }

  private static DuplicateKeyException aDuplicateOf(String constraint) {
    return new DuplicateKeyException("duplicate key", thePostgresErrorOf(constraint));
  }

  private static PSQLException thePostgresErrorOf(String constraint) {
    return new PSQLException(
        new ServerErrorMessage(
            "SERROR\0C23505\0Mduplicate key value violates unique constraint\0n"
                + constraint
                + "\0"));
  }

  private static final class Ledger implements PlatformTransactionManager {

    private final List<String> endings = new ArrayList<>();
    private SimpleTransactionStatus open = new SimpleTransactionStatus();
    private int depth;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      if (depth == 0) {
        open = new SimpleTransactionStatus();
      }
      depth++;
      return depth == 1 ? open : new Participating(open);
    }

    @Override
    public void commit(TransactionStatus status) {
      depth--;
      if (status instanceof Participating joined) {
        joined.leave();
        return;
      }
      endings.add(status.isRollbackOnly() ? "rolled back" : "committed");
    }

    @Override
    public void rollback(TransactionStatus status) {
      depth--;
      if (status instanceof Participating joined) {
        joined.outer.setRollbackOnly();
        return;
      }
      endings.add("rolled back");
    }

    List<String> endings() {
      return List.copyOf(endings);
    }
  }

  private static final class Participating extends SimpleTransactionStatus {

    private final SimpleTransactionStatus outer;

    Participating(SimpleTransactionStatus outer) {
      super(false);
      this.outer = outer;
    }

    void leave() {
      if (isRollbackOnly()) {
        outer.setRollbackOnly();
      }
    }
  }
}
