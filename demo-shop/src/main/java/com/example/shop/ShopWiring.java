package com.example.shop;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ShopWiring {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
