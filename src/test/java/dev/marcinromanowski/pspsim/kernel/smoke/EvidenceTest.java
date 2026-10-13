package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EvidenceTest {

  private static final LocalDate A_DAY = LocalDate.of(2026, 9, 10);

  @Test
  void every_class_the_evidence_classes_define_is_spelled_as_a_manifest_names_it() {
    assertThat(EvidenceClass.spelled("x/y", "published rule"))
        .isEqualTo(EvidenceClass.PUBLISHED_RULE);
    assertThat(EvidenceClass.spelled("x/y", "published example"))
        .isEqualTo(EvidenceClass.PUBLISHED_EXAMPLE);
    assertThat(EvidenceClass.spelled("x/y", "live probe")).isEqualTo(EvidenceClass.LIVE_PROBE);
    assertThat(EvidenceClass.spelled("x/y", "simulator contract"))
        .isEqualTo(EvidenceClass.SIMULATOR_CONTRACT);
  }

  @Test
  void a_class_the_evidence_classes_do_not_define_is_malformed() {
    assertThatThrownBy(() -> EvidenceClass.spelled("x/y", "published example, OK"))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage(
            "x/y names evidence class published example, OK, which is none of "
                + "[published rule, published example, live probe, simulator contract]");
  }

  @Test
  void an_evidence_renders_its_class_the_day_of_a_probe_and_whether_it_is_an_excerpt() {
    assertThat(Evidence.of(EvidenceClass.PUBLISHED_RULE).rendered()).isEqualTo("published rule");
    assertThat(Evidence.of(EvidenceClass.PUBLISHED_EXAMPLE).excerpted().rendered())
        .isEqualTo("published example, an excerpt");
    assertThat(Evidence.probedOn(A_DAY).rendered()).isEqualTo("live probe on 2026-09-10");
    assertThat(Evidence.probedOn(A_DAY).excerpted())
        .isEqualTo(new Evidence(EvidenceClass.LIVE_PROBE, A_DAY, true));
  }

  @Test
  void only_a_live_probe_is_dated_and_it_always_is() {
    assertThatThrownBy(() -> Evidence.of(EvidenceClass.LIVE_PROBE))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("a live probe, and nothing else, names the day it was made");
    assertThatThrownBy(() -> new Evidence(EvidenceClass.PUBLISHED_EXAMPLE, A_DAY, false))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("a live probe, and nothing else, names the day it was made");
  }

  @Test
  void a_published_rule_is_never_an_excerpt_of_an_answer_and_every_evidence_names_its_class() {
    assertThatThrownBy(() -> Evidence.of(EvidenceClass.PUBLISHED_RULE).excerpted())
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("a published rule has no answer to excerpt");
    assertThatThrownBy(() -> new Evidence(null, null, false))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("an evidence names its class");
  }
}
