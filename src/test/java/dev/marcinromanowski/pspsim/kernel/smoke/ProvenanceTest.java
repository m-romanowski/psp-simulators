package dev.marcinromanowski.pspsim.kernel.smoke;

import static dev.marcinromanowski.pspsim.kernel.smoke.Samples.A_LIVE_PROBE;
import static dev.marcinromanowski.pspsim.kernel.smoke.Samples.A_PUBLISHED_EXAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProvenanceTest {

  private static final Evidence A_PROBE = Evidence.probedOn(LocalDate.of(2026, 9, 10));

  @Test
  void a_provenance_renders_the_sample_its_evidence_its_source_and_its_reference() {
    assertThat(A_PUBLISHED_EXAMPLE.rendered())
        .isEqualTo(
            "grow/create-payment-process-approved: published example, "
                + "https://developers.grow.business/reference/delayed-payment-response "
                + "(grow.md, Create Hosted Payment Process)");
    assertThat(A_PUBLISHED_EXAMPLE).hasToString(A_PUBLISHED_EXAMPLE.rendered());
  }

  @Test
  void a_live_probe_renders_the_day_it_was_made_and_an_excerpt_says_that_it_is_one() {
    assertThat(A_LIVE_PROBE.rendered())
        .isEqualTo(
            "grow/create-payment-process-unknown-business: live probe on 2026-09-15, "
                + "https://sandbox.meshulam.co.il/api/light/server/1.0/createPaymentProcess "
                + "(grow.md, Invalid Credentials)");
    assertThat(
            new Provenance("x/y", A_PROBE.excerpted(), "https://x", "x.md, X", "keeps three")
                .rendered())
        .isEqualTo("x/y: live probe on 2026-09-10, an excerpt, https://x (x.md, X); keeps three");
  }

  @Test
  void a_provenance_naming_no_sample_is_refused() {
    assertThatThrownBy(() -> new Provenance(" ", A_PROBE, "https://x", "x.md, X"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sample");
    assertThatThrownBy(() -> new Provenance(null, A_PROBE, "https://x", "x.md, X"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_sample_naming_no_evidence_no_source_no_reference_or_a_blank_note_is_malformed() {
    assertThatThrownBy(() -> new Provenance("x/y", null, "https://x", "x.md, X"))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("x/y names no evidence class");
    assertThatThrownBy(() -> new Provenance("x/y", A_PROBE, " ", "x.md, X"))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("x/y names no source");
    assertThatThrownBy(() -> new Provenance("x/y", A_PROBE, null, "x.md, X"))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("x/y names no source");
    assertThatThrownBy(() -> new Provenance("x/y", A_PROBE, "https://x", " "))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("x/y names no reference");
    assertThatThrownBy(() -> new Provenance("x/y", A_PROBE, "https://x", null))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("x/y names no reference");
    assertThatThrownBy(() -> new Provenance("x/y", A_PROBE, "https://x", "x.md, X", " "))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("x/y gives a note with no text");
  }
}
