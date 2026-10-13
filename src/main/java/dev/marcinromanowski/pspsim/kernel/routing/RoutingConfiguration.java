package dev.marcinromanowski.pspsim.kernel.routing;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.routing.internal.JdbcRouteAliases;
import dev.marcinromanowski.pspsim.kernel.routing.internal.RouteAliasFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class RoutingConfiguration {

  @Bean
  public RouteAliases routeAliases(JdbcClient jdbc, SimulatorClock clock) {
    return new JdbcRouteAliases(jdbc, clock);
  }

  @Bean
  public FilterRegistrationBean<Filter> routeAliasFilter(RouteAliases aliases) {
    FilterRegistrationBean<Filter> registration =
        new FilterRegistrationBean<>(new RouteAliasFilter(aliases));
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
    return registration;
  }
}
