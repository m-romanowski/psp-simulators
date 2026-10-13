package dev.marcinromanowski.pspsim.kernel.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoredRefusalSubjectTest {

  @Test
  void every_arm_a_provider_may_state_comes_back_out_of_the_trail_as_the_arm_it_went_in_as() {
    List<RefusalSubject> stated =
        List.of(
            RefusalSubject.theObject("734754"),
            RefusalSubject.noObjectAnsweredTo("transactionId"),
            RefusalSubject.severalObjectsAnsweredTo("ORDER", 3),
            RefusalSubject.anObjectItWillNotName("provider grow names none"),
            RefusalSubject.noObjectWasNamed("provider grow addressed none"));

    for (RefusalSubject subject : stated) {
      assertThat(StoredRefusalSubject.from(StoredRefusalSubject.of(subject))).isEqualTo(subject);
    }
  }

  @Test
  void a_row_written_before_this_simulator_asked_for_a_subject_reads_as_one_not_recorded() {
    assertThat(StoredRefusalSubject.from(null)).isNull();
    assertThat(StoredRefusalSubject.from(List.of())).isNull();
  }

  @Test
  void an_arm_this_build_cannot_read_back_is_refused_rather_than_dropped_from_a_timeline() {
    List<String> storedColumns = List.of("AnArmFromAnotherBuild", "x");

    assertThatThrownBy(() -> StoredRefusalSubject.from(storedColumns))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("silently drop the refusals stored under it");
  }
}
