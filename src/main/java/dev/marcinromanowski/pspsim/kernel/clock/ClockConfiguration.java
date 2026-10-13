package dev.marcinromanowski.pspsim.kernel.clock;

import dev.marcinromanowski.pspsim.kernel.clock.internal.HostBackedSimulatorClock;
import dev.marcinromanowski.pspsim.kernel.clock.internal.JdbcFixedTimes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

  @Bean
  public FixedTimes fixedTimes(JdbcClient jdbc) {
    return new JdbcFixedTimes(jdbc);
  }

  @Bean
  public SimulatorClock simulatorClock(FixedTimes fixedTimes) {
    return new HostBackedSimulatorClock(fixedTimes);
  }
}
