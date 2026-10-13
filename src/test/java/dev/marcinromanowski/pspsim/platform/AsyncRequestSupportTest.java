package dev.marcinromanowski.pspsim.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

class AsyncRequestSupportTest {

  @Test
  void the_container_waits_longer_than_the_longest_delay_a_scenario_may_ever_hold() {
    AsyncRequestSupport support =
        new AsyncRequestSupport(new FaultDelayCeiling(Duration.ofSeconds(30)));

    assertThat(support.defaultTimeoutMillis()).isEqualTo(40_000);
    assertThat(support.defaultTimeoutMillis()).isGreaterThan(Duration.ofSeconds(30).toMillis());
  }

  @Test
  void a_shorter_ceiling_shortens_the_deadline_with_it_rather_than_leaving_a_fixed_one() {
    AsyncRequestSupport support =
        new AsyncRequestSupport(new FaultDelayCeiling(Duration.ofSeconds(5)));

    assertThat(support.defaultTimeoutMillis()).isEqualTo(15_000);
    support.configureAsyncSupport(new AsyncSupportConfigurer());
  }
}
