package dev.marcinromanowski.pspsim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

class SimulatorApplicationIntegrationTest extends PostgresBackedTest {

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void health_endpoint_reports_database_and_scheduler_readiness() {
    Map<String, Object> health =
        RestClient.create()
            .get()
            .uri("http://localhost:" + port + "/actuator/health")
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(health).isNotNull();
    assertThat(health).containsEntry("status", "UP");

    @SuppressWarnings("unchecked")
    Map<String, Object> components = (Map<String, Object>) health.get("components");
    assertThat(components).containsKeys("db", "scheduler");
    assertThat(componentStatus(components, "db")).isEqualTo("UP");
    assertThat(componentStatus(components, "scheduler")).isEqualTo("UP");
  }

  @Test
  void readiness_probe_covers_the_database_and_the_scheduler() {
    Map<String, Object> readiness =
        RestClient.create()
            .get()
            .uri("http://localhost:" + port + "/actuator/health/readiness")
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(readiness).isNotNull();
    assertThat(readiness).containsEntry("status", "UP");

    @SuppressWarnings("unchecked")
    Map<String, Object> components = (Map<String, Object>) readiness.get("components");
    assertThat(components).containsKeys("db", "scheduler");
  }

  @Test
  void flyway_has_applied_the_baseline_migration() {
    List<String> applied =
        jdbcTemplate.queryForList(
            "select version from flyway_schema_history where success = true order by installed_rank",
            String.class);

    assertThat(applied).contains("1");
  }

  @Test
  void the_datasource_points_at_the_container_the_suite_started() {
    String database = jdbcTemplate.queryForObject("select current_database()", String.class);

    assertThat(database).isEqualTo(POSTGRES.getDatabaseName());
  }

  private static String componentStatus(Map<String, Object> components, String name) {
    @SuppressWarnings("unchecked")
    Map<String, Object> component = (Map<String, Object>) components.get(name);
    return String.valueOf(component.get("status"));
  }
}
