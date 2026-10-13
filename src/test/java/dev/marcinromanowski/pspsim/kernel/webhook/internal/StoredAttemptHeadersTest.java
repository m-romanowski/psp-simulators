package dev.marcinromanowski.pspsim.kernel.webhook.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class StoredAttemptHeadersTest {

  private static final Map<String, List<String>> THE_WIRE =
      Map.of("Content-Type", List.of("application/x-www-form-urlencoded"));

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void the_wire_an_attempt_recorded_is_stored_as_the_headers_it_put_on_it() {
    assertThat(StoredAttemptHeaders.storedForm(THE_WIRE, json))
        .isEqualTo("{\"Content-Type\":[\"application/x-www-form-urlencoded\"]}");
  }

  @Test
  void an_attempt_that_recorded_no_wire_stores_nothing_rather_than_an_empty_set_of_headers() {
    assertThat(StoredAttemptHeaders.storedForm(null, json)).isNull();
  }

  @Test
  void a_stored_wire_reads_back_as_the_headers_the_attempt_put_on_it() {
    assertThat(
            StoredAttemptHeaders.theWireOf(StoredAttemptHeaders.storedForm(THE_WIRE, json), json))
        .containsExactlyInAnyOrderEntriesOf(THE_WIRE);
  }

  @Test
  void a_row_that_stored_no_wire_reads_back_as_nothing_recorded_rather_than_as_no_header_sent() {
    assertThat(StoredAttemptHeaders.theWireOf(null, json)).isNull();
  }

  @Test
  void a_row_that_stored_an_empty_set_of_headers_is_not_mistaken_for_one_that_stored_no_wire() {
    assertThat(StoredAttemptHeaders.theWireOf("{}", json)).isNotNull().isEmpty();
  }
}
