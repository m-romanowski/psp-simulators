package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TheCausesOfAFailureTest {

  @Test
  void the_chain_is_read_as_deep_as_a_reader_goes_and_no_deeper() {
    Throwable deep = new IllegalStateException("the last one");
    for (int wrapped = 0; wrapped < 30; wrapped++) {
      deep = new IllegalStateException("wrapped " + wrapped, deep);
    }

    assertThat(TheCausesOfAFailure.of(deep))
        .as(
            "a chain this simulator wrapped thirty deep is read down to the reader's limit and"
                + " no further, and asserting the limit against the number the reader itself"
                + " publishes would pass with that number mutated")
        .hasSize(12)
        .first()
        .isEqualTo("IllegalStateException: wrapped 29");
    assertThat(TheCausesOfAFailure.of(deep)).last().isEqualTo("IllegalStateException: wrapped 18");
  }

  @Test
  void a_failure_with_fewer_causes_than_the_limit_is_read_to_its_end() {
    Throwable raised =
        new IllegalStateException("the outer one", new IllegalArgumentException("the inner one"));

    assertThat(TheCausesOfAFailure.of(raised))
        .containsExactly(
            "IllegalStateException: the outer one", "IllegalArgumentException: the inner one");
  }

  @Test
  void nothing_raised_leaves_nothing_to_read() {
    assertThat(TheCausesOfAFailure.of(null)).isEmpty();
  }

  @Test
  void a_failure_that_says_nothing_of_its_own_is_named_by_what_it_is() {
    assertThat(TheCausesOfAFailure.of(new NullPointerException()))
        .containsExactly("NullPointerException");
  }

  @Test
  void the_name_a_row_files_the_causes_under_is_the_one_the_screen_reads() {
    assertThat(TheCausesOfAFailure.THE_CAUSES).isEqualTo("causes");
  }

  @Test
  void a_cause_that_quotes_back_a_statement_leaves_the_statement_behind() {
    List<String> causes =
        TheCausesOfAFailure.of(
            new IllegalStateException(
                "StatementCallback; bad SQL grammar [select * from grow_transaction where"
                    + " test_run_id = 'r1']"));

    assertThat(causes)
        .as(
            "the trail travels in run bundles and in bug-report bundles, a different audience"
                + " from a tester at a terminal, and a diagnostic of this build names the field"
                + " and never the value")
        .containsExactly("IllegalStateException: StatementCallback; bad SQL grammar [redacted]");
  }

  @Test
  void a_cause_that_names_a_class_by_its_whole_package_is_recorded_by_its_simple_name() {
    List<String> causes =
        TheCausesOfAFailure.of(
            new IllegalStateException(
                "nested exception is org.springframework.jdbc.BadSqlGrammarException and"
                    + " dev.marcinromanowski.pspsim.kernel.audit.internal.JdbcAuditTrail raised it"));

    assertThat(causes)
        .containsExactly(
            "IllegalStateException: nested exception is BadSqlGrammarException and JdbcAuditTrail"
                + " raised it");
  }

  @Test
  void what_a_failure_said_about_itself_is_kept_because_it_is_the_whole_diagnosis() {
    assertThat(TheCausesOfAFailure.of(new IllegalStateException("a hold window is 0 days")))
        .containsExactly("IllegalStateException: a hold window is 0 days");
    assertThat(TheCausesOfAFailure.withoutWhatTravelsNowhere("nothing to take out here"))
        .isEqualTo("nothing to take out here");
  }
}
