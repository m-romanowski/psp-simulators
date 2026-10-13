package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzericardApiResponseTest {

  @Test
  void
      a_diagnostic_the_simulator_answers_with_is_plain_text_carrying_the_headers_of_every_answer() {
    AzericardApiResponse diagnostic =
        AzericardApiResponse.aDiagnostic(
            504, "the answer was held too long", Map.of("X-Answered-By", List.of("simulator")));

    assertThat(diagnostic.httpStatus()).isEqualTo(504);
    assertThat(diagnostic.contentType()).isEqualTo("text/plain; charset=utf-8");
    assertThat(new String(diagnostic.body(), UTF_8)).isEqualTo("the answer was held too long");
    assertThat(diagnostic.headers()).containsEntry("X-Answered-By", List.of("simulator"));
    assertThat(diagnostic.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(diagnostic.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(diagnostic.providerObjectId()).isNull();
  }
}
