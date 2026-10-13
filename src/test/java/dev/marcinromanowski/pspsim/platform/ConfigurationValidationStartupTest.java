package dev.marcinromanowski.pspsim.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.startup.SimulatorProperties;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class ConfigurationValidationStartupTest {

  private static final List<String> DOCUMENTED_RETRY_PLAN =
      List.of("PT0S", "PT1S", "PT5S", "PT30S", "PT2M");

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PlatformConfiguration.class);

  static List<Arguments> rejectedConfigurations() {
    return List.of(
        Arguments.of(
            "a missing public base URL",
            configuration().withPublicBaseUrl(""),
            "simulator.public-base-url must be set"),
        Arguments.of(
            "a malformed public base URL",
            configuration().withPublicBaseUrl("http://a host"),
            "simulator.public-base-url is not a valid URI"),
        Arguments.of(
            "a relative public base URL",
            configuration().withPublicBaseUrl("/callbacks"),
            "simulator.public-base-url must be an absolute URL with a host"),
        Arguments.of(
            "a public base URL that is not http",
            configuration().withPublicBaseUrl("ftp://simulator.internal"),
            "simulator.public-base-url must use the http or https scheme"),
        Arguments.of(
            "a public base URL carrying a query string",
            configuration().withPublicBaseUrl("http://simulator.internal?run=1"),
            "simulator.public-base-url must not carry a query string or fragment"),
        Arguments.of(
            "a public base URL carrying a fragment",
            configuration().withPublicBaseUrl("http://simulator.internal#top"),
            "simulator.public-base-url must not carry a query string or fragment"),
        Arguments.of(
            "a public base URL with a trailing slash",
            configuration().withPublicBaseUrl("http://simulator.internal/"),
            "simulator.public-base-url must not end with '/'"),
        Arguments.of(
            "a test run id with a space in it",
            configuration().withDefaultTestRunId("run 1"),
            "simulator.default-test-run-id must match"),
        Arguments.of(
            "an empty test run id",
            configuration().withDefaultTestRunId(""),
            "simulator.default-test-run-id must match"),
        Arguments.of(
            "a test run id starting with a dash",
            configuration().withDefaultTestRunId("-leading-dash"),
            "simulator.default-test-run-id must match"),
        Arguments.of(
            "the test run id reserved for a failed resolution",
            configuration().withDefaultTestRunId("UNKNOWN"),
            "simulator.default-test-run-id must name a test run"),
        Arguments.of(
            "no webhook section at all",
            configuration().withoutWebhookSection(),
            "simulator.webhook must be configured"),
        Arguments.of(
            "an empty retry plan",
            configuration().withRetryDelays(List.of()),
            "simulator.webhook.retry-delays must list one delay per delivery attempt"),
        Arguments.of(
            "a retry plan longer than the attempt cap",
            configuration().withRetryDelays(repeated("PT1S", 21).withFirstImmediate()),
            "simulator.webhook.retry-delays must not describe more than 20 delivery attempts"),
        Arguments.of(
            "a first attempt that is not immediate",
            configuration().withRetryDelays(List.of("PT1S", "PT5S")),
            "the first entry of simulator.webhook.retry-delays must be zero"),
        Arguments.of(
            "a retry plan that goes backwards",
            configuration().withRetryDelays(List.of("PT0S", "PT5S", "PT1S")),
            "simulator.webhook.retry-delays must not decrease"),
        Arguments.of(
            "a connect timeout of zero",
            configuration().withConnectTimeout("PT0S"),
            "simulator.webhook.connect-timeout must be positive"),
        Arguments.of(
            "a negative read timeout",
            configuration().withReadTimeout("PT-1S"),
            "simulator.webhook.read-timeout must be positive"),
        Arguments.of(
            "a poll interval of zero",
            configuration().withPollInterval("PT0S"),
            "simulator.webhook.poll-interval must be positive"),
        Arguments.of(
            "a response preview that keeps nothing",
            configuration().withMaxResponsePreviewBytes("0"),
            "simulator.webhook.max-response-preview-bytes must be between 1 and 65536"),
        Arguments.of(
            "a response preview that would archive merchant pages",
            configuration().withMaxResponsePreviewBytes("65537"),
            "simulator.webhook.max-response-preview-bytes must be between 1 and 65536"),
        Arguments.of(
            "no fault section at all",
            configuration().withoutFaultSection(),
            "simulator.fault must be configured"),
        Arguments.of(
            "a delay ceiling of zero, which refuses every delay it accepted",
            configuration().withLongestFaultDelay("PT0S"),
            "simulator.fault.longest-delay must be positive"),
        Arguments.of(
            "a negative delay ceiling",
            configuration().withLongestFaultDelay("PT-1S"),
            "simulator.fault.longest-delay must be positive"),
        Arguments.of(
            "a delay ceiling longer than a transport fault may ever hold a connection",
            configuration().withLongestFaultDelay("PT5M1S"),
            "simulator.fault.longest-delay must not exceed PT5M"),
        Arguments.of(
            "no scheduler section at all",
            configuration().withoutSchedulerSection(),
            "simulator.scheduler must be configured"),
        Arguments.of(
            "a scheduler pool below the range",
            configuration().withPoolSize("0"),
            "simulator.scheduler.pool-size must be between 1 and 64"),
        Arguments.of(
            "a scheduler pool above the range",
            configuration().withPoolSize("65"),
            "simulator.scheduler.pool-size must be between 1 and 64"));
  }

  @ParameterizedTest(name = "refuses to start on {0}")
  @MethodSource("rejectedConfigurations")
  void refuses_to_start(String description, Configuration configuration, String operatorMessage) {
    contextRunner
        .withPropertyValues(configuration.propertyValues())
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context)
                  .getFailure()
                  .rootCause()
                  .isInstanceOf(BindValidationException.class)
                  .hasMessageContaining(operatorMessage);
            });
  }

  static List<Arguments> acceptedConfigurations() {
    return List.of(
        Arguments.of("the shipped defaults", configuration()),
        Arguments.of(
            "a public base URL with a path prefix",
            configuration().withPublicBaseUrl("https://sim.internal:8443/psp")),
        Arguments.of(
            "a hyphenated test run id", configuration().withDefaultTestRunId("e2e-run-123")),
        Arguments.of(
            "a dated test run id",
            configuration().withDefaultTestRunId("manual-marcin-2026-09-07")),
        Arguments.of("the smallest scheduler pool", configuration().withPoolSize("1")),
        Arguments.of("the largest scheduler pool", configuration().withPoolSize("64")),
        Arguments.of(
            "a single immediate attempt", configuration().withRetryDelays(List.of("PT0S"))),
        Arguments.of(
            "an all-zero retry plan, the fastest legitimate CI configuration",
            configuration().withRetryDelays(List.of("PT0S", "PT0S", "PT0S", "PT0S", "PT0S"))),
        Arguments.of(
            "a retry plan at the attempt cap",
            configuration().withRetryDelays(repeated("PT1S", 20).withFirstImmediate())),
        Arguments.of(
            "the smallest response preview that keeps anything at all",
            configuration().withMaxResponsePreviewBytes("1")),
        Arguments.of(
            "the largest response preview", configuration().withMaxResponsePreviewBytes("65536")),
        Arguments.of(
            "the shortest delay ceiling", configuration().withLongestFaultDelay("PT0.001S")),
        Arguments.of("the longest delay ceiling", configuration().withLongestFaultDelay("PT5M")));
  }

  @ParameterizedTest(name = "starts on {0}")
  @MethodSource("acceptedConfigurations")
  void starts(String description, Configuration configuration) {
    contextRunner
        .withPropertyValues(configuration.propertyValues())
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void binds_the_documented_configuration() {
    contextRunner
        .withPropertyValues(configuration().propertyValues())
        .run(
            (AssertableApplicationContext context) -> {
              assertThat(context).hasSingleBean(SimulatorProperties.class);
              assertThat(context).hasSingleBean(ThreadPoolTaskScheduler.class);

              SimulatorProperties properties = context.getBean(SimulatorProperties.class);
              assertThat(properties.publicBaseUrl()).isEqualTo("http://localhost:8080");
              assertThat(properties.defaultTestRunId()).isEqualTo("default");
              assertThat(properties.webhook().retryDelays()).hasSize(5);
              assertThat(properties.webhook().maxAttempts()).isEqualTo(5);
              assertThat(properties.webhook().connectTimeout()).isEqualTo(Duration.ofSeconds(2));
              assertThat(properties.webhook().readTimeout()).isEqualTo(Duration.ofSeconds(5));
              assertThat(properties.webhook().maxResponsePreviewBytes()).isEqualTo(2048);
              assertThat(properties.webhook().pollInterval()).isEqualTo(Duration.ofSeconds(1));
              assertThat(properties.scheduler().poolSize()).isEqualTo(4);
              assertThat(properties.fault().longestDelay()).isEqualTo(Duration.ofSeconds(30));

              FaultDelayCeiling ceiling = context.getBean(FaultDelayCeiling.class);
              assertThat(ceiling.longest()).isEqualTo(Duration.ofSeconds(30));
              assertThat(ceiling.deliveryDeadline()).isEqualTo(Duration.ofSeconds(40));
              RetryPlan plan = context.getBean(RetryPlan.class);
              assertThat(plan.attemptCap()).isEqualTo(5);
              assertThat(plan.delayBefore(1)).isEqualTo(Duration.ZERO);
              assertThat(plan.delayBefore(5)).isEqualTo(Duration.ofMinutes(2));
            });
  }

  @Test
  void the_attempt_cap_follows_the_retry_plan_rather_than_a_second_property() {
    contextRunner
        .withPropertyValues(
            configuration().withRetryDelays(List.of("PT0S", "PT2S")).propertyValues())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(SimulatorProperties.class).webhook().maxAttempts())
                  .isEqualTo(2);
            });
  }

  @Test
  void scheduler_is_sized_from_configuration() {
    contextRunner
        .withPropertyValues(configuration().withPoolSize("7").propertyValues())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              ThreadPoolTaskScheduler scheduler = context.getBean(ThreadPoolTaskScheduler.class);
              assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(7);
            });
  }

  @Test
  void an_absent_webhook_timeout_is_refused_as_missing_rather_than_as_not_positive() {
    contextRunner
        .withPropertyValues(configuration().withoutConnectTimeout().propertyValues())
        .run(
            context -> {
              assertThat(context).hasFailed();

              String rendered = renderedFailure(context);
              assertThat(rendered).contains("simulator.webhook.connect-timeout must be set");
              assertThat(rendered)
                  .doesNotContain("simulator.webhook.connect-timeout must be positive");
            });
  }

  @Test
  void the_refusal_of_a_backwards_retry_plan_names_the_late_entry_and_the_one_it_undercuts() {
    contextRunner
        .withPropertyValues(
            configuration()
                .withRetryDelays(List.of("PT0S", "PT5S", "PT1S", "PT9S"))
                .propertyValues())
        .run(
            context -> {
              assertThat(context).hasFailed();

              String rendered = renderedFailure(context);
              assertThat(rendered)
                  .contains(
                      "simulator.webhook.retry-delays must not decrease: entry 2 is shorter than"
                          + " entry 1");
              assertThat(rendered).doesNotContain("entry 3");
            });
  }

  @Test
  void a_rejection_names_the_property_and_never_the_value() {
    String secretLookingValue = "http://tenant:sk-live-0123456789@simulator.internal/";

    contextRunner
        .withPropertyValues(configuration().withPublicBaseUrl(secretLookingValue).propertyValues())
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context)
                  .getFailure()
                  .rootCause()
                  .hasMessageContaining("simulator.public-base-url must not end with '/'");

              String rendered = renderedFailure(context);
              assertThat(rendered).doesNotContain("sk-live-0123456789");
              assertThat(rendered).doesNotContain(secretLookingValue);
            });
  }

  private static String renderedFailure(AssertableApplicationContext context) {
    StringBuilder rendered = new StringBuilder();
    for (Throwable cause = context.getStartupFailure(); cause != null; cause = cause.getCause()) {
      rendered.append(cause.getMessage()).append('\n');
    }
    return rendered.toString();
  }

  private static Configuration configuration() {
    return new Configuration();
  }

  private static TheRetryPlanAPropertyFileDeclares repeated(String delay, int attempts) {
    List<String> delays = new ArrayList<>();
    for (int i = 0; i < attempts; i++) {
      delays.add(delay);
    }
    return new TheRetryPlanAPropertyFileDeclares(delays);
  }

  private record TheRetryPlanAPropertyFileDeclares(List<String> delays) {

    List<String> withFirstImmediate() {
      List<String> immediateFirst = new ArrayList<>(delays);
      immediateFirst.set(0, "PT0S");
      return immediateFirst;
    }
  }

  private static final class Configuration {

    private String publicBaseUrl = "http://localhost:8080";
    private String defaultTestRunId = "default";
    private List<String> retryDelays = DOCUMENTED_RETRY_PLAN;
    private String connectTimeout = "PT2S";
    private String readTimeout = "PT5S";
    private String maxResponsePreviewBytes = "2048";
    private String pollInterval = "PT1S";
    private String poolSize = "4";
    private String longestFaultDelay = "PT30S";
    private boolean webhookSection = true;
    private boolean connectTimeoutPresent = true;
    private boolean faultSection = true;
    private boolean schedulerSection = true;

    Configuration withPublicBaseUrl(String value) {
      this.publicBaseUrl = value;
      return this;
    }

    Configuration withDefaultTestRunId(String value) {
      this.defaultTestRunId = value;
      return this;
    }

    Configuration withRetryDelays(List<String> value) {
      this.retryDelays = value;
      return this;
    }

    Configuration withConnectTimeout(String value) {
      this.connectTimeout = value;
      return this;
    }

    Configuration withReadTimeout(String value) {
      this.readTimeout = value;
      return this;
    }

    Configuration withMaxResponsePreviewBytes(String value) {
      this.maxResponsePreviewBytes = value;
      return this;
    }

    Configuration withPollInterval(String value) {
      this.pollInterval = value;
      return this;
    }

    Configuration withoutConnectTimeout() {
      this.connectTimeoutPresent = false;
      return this;
    }

    Configuration withoutWebhookSection() {
      this.webhookSection = false;
      return this;
    }

    Configuration withLongestFaultDelay(String value) {
      this.longestFaultDelay = value;
      return this;
    }

    Configuration withoutFaultSection() {
      this.faultSection = false;
      return this;
    }

    Configuration withPoolSize(String value) {
      this.poolSize = value;
      return this;
    }

    Configuration withoutSchedulerSection() {
      this.schedulerSection = false;
      return this;
    }

    String[] propertyValues() {
      List<String> values = new ArrayList<>();
      values.add("simulator.public-base-url=" + publicBaseUrl);
      values.add("simulator.default-test-run-id=" + defaultTestRunId);
      if (webhookSection) {
        values.add("simulator.webhook.retry-delays=" + String.join(",", retryDelays));
        if (connectTimeoutPresent) {
          values.add("simulator.webhook.connect-timeout=" + connectTimeout);
        }
        values.add("simulator.webhook.read-timeout=" + readTimeout);
        values.add("simulator.webhook.max-response-preview-bytes=" + maxResponsePreviewBytes);
        values.add("simulator.webhook.poll-interval=" + pollInterval);
      }
      if (faultSection) {
        values.add("simulator.fault.longest-delay=" + longestFaultDelay);
      }
      if (schedulerSection) {
        values.add("simulator.scheduler.pool-size=" + poolSize);
      }
      return values.toArray(new String[0]);
    }

    @Override
    public String toString() {
      return String.join(" ", propertyValues());
    }
  }
}
