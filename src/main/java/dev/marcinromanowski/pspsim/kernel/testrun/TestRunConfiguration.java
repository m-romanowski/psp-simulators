package dev.marcinromanowski.pspsim.kernel.testrun;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.internal.JdbcFingerprintKeyStore;
import dev.marcinromanowski.pspsim.kernel.testrun.internal.JdbcTestRuns;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class TestRunConfiguration {

  @Bean
  public TestRuns testRuns(
      JdbcClient jdbc, @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new JdbcTestRuns(jdbc, TestRunId.of(defaultTestRunId));
  }

  @Bean
  public FingerprintKeys fingerprintKeys(JdbcClient jdbc, IdGenerator ids) {
    return new MintedFingerprintKeys(new JdbcFingerprintKeyStore(jdbc), ids);
  }
}
