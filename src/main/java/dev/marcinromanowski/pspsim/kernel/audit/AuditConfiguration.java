package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.audit.internal.JdbcAuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class AuditConfiguration {

  @Bean
  public JdbcAuditTrail auditTrail(JdbcClient jdbc, ObjectMapper json) {
    return new JdbcAuditTrail(jdbc, json);
  }

  @Bean
  public ActionTrail actionTrail(AuditTrail trail, SimulatorClock clock, IdGenerator ids) {
    return new ActionTrail(trail, clock, ids);
  }
}
