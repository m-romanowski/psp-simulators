package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderDouble;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

@Import(AResetThatFailsMidwayIntegrationTest.Wiring.class)
@DirtiesContext
class AResetThatFailsMidwayIntegrationTest extends PostgresBackedTest {

  static final TestRunId RUN = TestRunId.of("a-reset-that-fails-midway-it");

  static final String A_FAILURE_MIDWAY = "the reset failed after it had cleared part of the run";

  @LocalServerPort private int port;

  @Autowired private TestRuns testRuns;

  @TestConfiguration(proxyBeanMethods = false)
  static class Wiring {

    @Bean
    SimulatorProvider aProviderWhoseResetFailsMidway(JdbcClient jdbc) {
      ProviderDouble behind = new ProviderDouble("brittle");
      return (SimulatorProvider)
          Proxy.newProxyInstance(
              SimulatorProvider.class.getClassLoader(),
              new Class<?>[] {SimulatorProvider.class},
              (proxy, method, arguments) -> {
                if ("reset".equals(method.getName())) {
                  jdbc.sql("delete from simulator_test_run where test_run_id = ?")
                      .param(RUN.value())
                      .update();
                  throw new IllegalStateException(A_FAILURE_MIDWAY);
                }
                try {
                  return method.invoke(behind, arguments);
                } catch (InvocationTargetException thrown) {
                  throw thrown.getCause();
                }
              });
    }
  }

  @AfterEach
  void forgetTheRun() {
    testRuns.forget(RUN);
  }

  @Test
  void a_reset_failing_after_it_cleared_part_of_the_run_leaves_the_whole_run_as_it_was() {
    testRuns.register(RUN);

    ResponseEntity<String> answered =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
            .build()
            .post()
            .uri("/__simulator/brittle/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value()))
            .retrieve()
            .toEntity(String.class);

    assertThat(answered.getStatusCode().is5xxServerError()).isTrue();
    assertThat(testRuns.exists(RUN))
        .as("the part of the reset that ran before it failed was rolled back with it")
        .isTrue();
  }
}
