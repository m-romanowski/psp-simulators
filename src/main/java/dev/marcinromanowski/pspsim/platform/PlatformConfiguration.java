package dev.marcinromanowski.pspsim.platform;

import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.startup.SimulatorConfigurationValidator;
import dev.marcinromanowski.pspsim.kernel.startup.SimulatorProperties;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.validation.Validator;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SimulatorProperties.class)
@EnableScheduling
public class PlatformConfiguration {

  @Bean
  public static Validator configurationPropertiesValidator() {
    return new SimulatorConfigurationValidator();
  }

  @Bean
  public RetryPlan webhookRetryPlan(SimulatorProperties properties) {
    return new RetryPlan(properties.webhook().retryDelays());
  }

  @Bean
  public FaultDelayCeiling faultDelayCeiling(SimulatorProperties properties) {
    return new FaultDelayCeiling(properties.fault().longestDelay());
  }

  @Bean
  public AsyncRequestSupport asyncRequestSupport(FaultDelayCeiling ceiling) {
    return new AsyncRequestSupport(ceiling);
  }

  @Bean
  public ThreadPoolTaskScheduler taskScheduler(SimulatorProperties properties) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(properties.scheduler().poolSize());
    scheduler.setThreadNamePrefix("simulator-scheduler-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(5);
    return scheduler;
  }
}
