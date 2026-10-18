package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldlineBodyTest {

  @Test
  void a_rendered_answer_is_kept_as_its_status_and_its_fields() {
    KeyedAnswer answer =
        WorldlineBody.of(201, WorldlineJsonReader.objectIn("{\"id\":\"p-1\"}").orElseThrow())
            .asAKeyedAnswer();

    assertThat(answer).isNotNull();
    assertThat(answer.httpStatus()).isEqualTo(201);
    assertThat(answer.fields()).isEqualTo(Map.of("id", List.of("p-1")));
  }

  @Test
  void a_body_nothing_rendered_is_kept_as_no_answer() {
    assertThat(WorldlineBody.nothingRendered().asAKeyedAnswer()).isNull();
  }
}
