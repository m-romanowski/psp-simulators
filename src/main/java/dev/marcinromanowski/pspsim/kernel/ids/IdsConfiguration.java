package dev.marcinromanowski.pspsim.kernel.ids;

import dev.marcinromanowski.pspsim.kernel.ids.internal.SecureRandomIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdsConfiguration {

  @Bean
  public IdGenerator idGenerator() {
    return new SecureRandomIdGenerator();
  }
}
