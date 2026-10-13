package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObservedTest {

  private final Map<String, String> read = new LinkedHashMap<>();

  @Test
  void a_field_the_answer_carried_is_recorded_under_its_name() {
    Observed.put(read, "status", "1");

    assertThat(read).containsExactly(Map.entry("status", "1"));
  }

  @Test
  void a_field_the_answer_did_not_carry_is_left_out_so_the_check_reports_it_unobserved() {
    Observed.put(read, "status", null);

    assertThat(read).isEmpty();
  }
}
