package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SequencedRowsTest {

  @Test
  void a_row_recorded_at_the_first_attempt_is_tried_once() {
    AtomicInteger attempts = new AtomicInteger();

    new SequencedRows()
        .recorded(
            "a row",
            () -> {
              attempts.incrementAndGet();
              return 1;
            });

    assertThat(attempts).hasValue(1);
  }

  @Test
  void a_row_that_lost_its_sequence_is_tried_again_until_it_is_recorded() {
    AtomicInteger attempts = new AtomicInteger();

    new SequencedRows().recorded("a row", () -> attempts.incrementAndGet() == 3 ? 1 : 0);

    assertThat(attempts).hasValue(3);
  }

  @Test
  void a_row_recorded_at_the_last_attempt_is_recorded() {
    AtomicInteger attempts = new AtomicInteger();

    new SequencedRows()
        .recorded("a row", () -> attempts.incrementAndGet() == SequencedRows.ATTEMPTS ? 1 : 0);

    assertThat(attempts).hasValue(SequencedRows.ATTEMPTS);
  }

  @Test
  void a_row_no_attempt_could_record_is_refused_after_the_one_bound_every_table_shares() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                new SequencedRows()
                    .recorded(
                        "the exchange under ORDER",
                        () -> {
                          attempts.incrementAndGet();
                          return 0;
                        }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("the exchange under ORDER could not be recorded in 100 attempts");
    assertThat(attempts).hasValue(100);
  }

  @Test
  void a_row_counted_twice_by_its_insert_is_no_row_recorded() {
    assertThatThrownBy(() -> new SequencedRows().recorded("a row", () -> 2))
        .isInstanceOf(IllegalStateException.class);
  }
}
