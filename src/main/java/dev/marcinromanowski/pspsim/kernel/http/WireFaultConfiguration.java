package dev.marcinromanowski.pspsim.kernel.http;

import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.http.internal.AWireFaultIsAnsweredByWhoeverWasAskedFilter;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAProviderAnswersOffItsRoutes;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import java.util.EnumSet;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class WireFaultConfiguration {

  @Bean
  public WhatAProviderAnswersOffItsRoutes whatAProviderAnswersOffItsRoutes(
      ProviderRegistry providers) {
    return new WhatAProviderAnswersOffItsRoutes(providers);
  }

  @Bean
  public WhoeverWasAsked whoeverWasAsked(
      WhatAProviderAnswersOffItsRoutes answers,
      ProviderRegistry providers,
      List<ASurfaceOfThisSimulator> surfaces,
      ActionTrail trail) {
    return new WhoeverWasAsked(answers, providers, surfaces, trail);
  }

  @Bean
  public FilterRegistrationBean<Filter> wireFaultFilter(WhoeverWasAsked whoeverWasAsked) {
    FilterRegistrationBean<Filter> registration =
        new FilterRegistrationBean<>(
            new AWireFaultIsAnsweredByWhoeverWasAskedFilter(whoeverWasAsked));
    registration.addUrlPatterns("/*");
    registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
    return registration;
  }
}
