package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SequencedTableTest {

  private static final SequencedTable EVENTS =
      new SequencedTable("an_event", List.of("test_run_id", "payment_id"));

  @Test
  void an_append_takes_the_next_free_sequence_and_names_the_whole_primary_key_it_may_collide_on() {
    assertThat(EVENTS.appendingAtTheNextFreeSequence(List.of("kind", "at")))
        .isEqualTo(
            "insert into an_event (test_run_id, payment_id, seq, kind, at) select ?, ?,"
                + " coalesce(max(seq) + 1, 0), ?, ? from an_event where test_run_id = ? and"
                + " payment_id = ? on conflict (test_run_id, payment_id, seq) do nothing");
  }

  @Test
  void a_request_under_a_key_is_recorded_with_the_verdict_its_sequence_admits() {
    assertThat(EVENTS.recordingUnderAKey(List.of("body")))
        .isEqualTo(
            "insert into an_event (test_run_id, payment_id, seq, verdict, body) select ?, ?,"
                + " coalesce(max(seq) + 1, 0), case when coalesce(bool_or(verdict in (?, ?, ?)),"
                + " false) then ? else ? end, ? from an_event where test_run_id = ? and"
                + " payment_id = ? on conflict (test_run_id, payment_id, seq) do nothing");
  }

  @Test
  void a_table_numbered_under_no_key_is_refused() {
    assertThatThrownBy(() -> new SequencedTable("an_event", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("an_event");
  }
}
