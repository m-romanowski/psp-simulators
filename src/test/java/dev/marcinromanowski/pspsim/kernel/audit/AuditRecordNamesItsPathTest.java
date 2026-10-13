package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AuditRecordNamesItsPathTest {

  @Test
  void only_a_callback_not_sent_may_name_no_path() {
    for (AuditDirection direction : AuditDirection.values()) {
      if (direction == AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT) {
        assertThat(row(direction, null).path()).isNull();
        continue;
      }
      assertThatThrownBy(() -> row(direction, null))
          .isInstanceOf(AuditRowNamesNoPathException.class)
          .hasMessage(AuditRowNamesNoPathException.ONLY_A_CALLBACK_WITH_NO_TARGET_NAMES_NO_PATH);
    }
  }

  @Test
  void the_called_path_is_the_path_a_row_names_and_a_row_naming_none_has_none_to_give() {
    assertThat(row(AuditDirection.INBOUND_REQUEST, "/v1/things").theCalledPath())
        .isEqualTo("/v1/things");
    assertThatThrownBy(() -> row(AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT, null).theCalledPath())
        .isInstanceOf(AuditRowNamesNoPathException.class);
  }

  private static AuditRecord row(AuditDirection direction, @Nullable String path) {
    return new AuditRecord(
        "aud_1",
        TestRunId.of("run"),
        null,
        ProviderId.of("grow"),
        null,
        ObjectOrigin.SCHEDULED_JOB,
        direction,
        null,
        "POST",
        path,
        null,
        null,
        null,
        null,
        1,
        Instant.parse("2026-09-29T12:00:00Z"),
        Map.of(),
        Map.of(),
        null);
  }
}
