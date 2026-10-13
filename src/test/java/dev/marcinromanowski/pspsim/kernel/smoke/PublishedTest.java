package dev.marcinromanowski.pspsim.kernel.smoke;

import static dev.marcinromanowski.pspsim.kernel.smoke.Samples.A_PUBLISHED_EXAMPLE;
import static dev.marcinromanowski.pspsim.kernel.smoke.Samples.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PublishedTest {

  @Test
  void a_derived_value_keeps_the_provenance_of_the_sample_it_was_derived_from() {
    Published signature =
        published("t=1492774577,v1=5257a869...", A_PUBLISHED_EXAMPLE)
            .map(header -> header.substring(0, 2));

    assertThat(signature.value()).isEqualTo("t=");
    assertThat(signature.provenance()).isEqualTo(A_PUBLISHED_EXAMPLE);
    assertThat(signature).hasToString("t= from grow/create-payment-process-approved");
  }

  @Test
  void a_derivation_that_finds_nothing_in_the_sample_is_refused_where_the_check_is_declared() {
    Published body = published("{}", A_PUBLISHED_EXAMPLE);

    assertThatThrownBy(() -> body.map(text -> null))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("grow/create-payment-process-approved yields no value where a check reads one");
    assertThatThrownBy(() -> body.map(text -> " "))
        .isInstanceOf(MalformedVendorSampleException.class);
  }
}
