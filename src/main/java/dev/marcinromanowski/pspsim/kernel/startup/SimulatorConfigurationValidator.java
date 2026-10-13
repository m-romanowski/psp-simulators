package dev.marcinromanowski.pspsim.kernel.startup;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public final class SimulatorConfigurationValidator implements Validator {

  private static final int MAX_WEBHOOK_ATTEMPTS = 20;
  private static final int MAX_SCHEDULER_POOL_SIZE = 64;

  private static final int MAX_RESPONSE_PREVIEW_BYTES = 64 * 1024;

  @Override
  public boolean supports(Class<?> clazz) {
    return SimulatorProperties.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    SimulatorProperties properties = (SimulatorProperties) target;
    validatePublicBaseUrl(properties.publicBaseUrl(), errors);
    validateDefaultTestRunId(properties.defaultTestRunId(), errors);
    validateWebhook(properties.webhook(), errors);
    validateFault(properties.fault(), errors);
    validateScheduler(properties.scheduler(), errors);
  }

  private void validatePublicBaseUrl(@Nullable String publicBaseUrl, Errors errors) {
    if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
      errors.reject(
          "simulator.public-base-url.missing",
          "simulator.public-base-url must be set; it is the base of every callback and redirect URL"
              + " the simulator hands to a merchant");
      return;
    }
    URI uri;
    try {
      uri = new URI(publicBaseUrl);
    } catch (URISyntaxException notAUri) {
      errors.reject(
          "simulator.public-base-url.malformed", "simulator.public-base-url is not a valid URI");
      return;
    }
    if (!uri.isAbsolute() || uri.getHost() == null) {
      errors.reject(
          "simulator.public-base-url.not-absolute",
          "simulator.public-base-url must be an absolute URL with a host");
      return;
    }
    String scheme = uri.getScheme();
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      errors.reject(
          "simulator.public-base-url.scheme",
          "simulator.public-base-url must use the http or https scheme");
    }
    if (uri.getQuery() != null || uri.getFragment() != null) {
      errors.reject(
          "simulator.public-base-url.not-a-base",
          "simulator.public-base-url must not carry a query string or fragment");
    }
    if (publicBaseUrl.endsWith("/")) {
      errors.reject(
          "simulator.public-base-url.trailing-slash",
          "simulator.public-base-url must not end with '/'");
    }
  }

  private void validateDefaultTestRunId(@Nullable String defaultTestRunId, Errors errors) {
    if (defaultTestRunId == null || !TestRunId.SHAPE.admits(defaultTestRunId)) {
      errors.reject(
          "simulator.default-test-run-id.invalid",
          TestRunId.SHAPE.theRefusalOf("simulator.default-test-run-id"));
      return;
    }
    try {
      TestRunId.of(defaultTestRunId);
    } catch (IllegalArgumentException reserved) {
      errors.reject(
          "simulator.default-test-run-id.reserved",
          "simulator.default-test-run-id must name a test run: " + reserved.getMessage());
    }
  }

  private void validateWebhook(SimulatorProperties.@Nullable Webhook webhook, Errors errors) {
    if (webhook == null) {
      errors.reject("simulator.webhook.missing", "simulator.webhook must be configured");
      return;
    }
    List<Duration> delays = webhook.retryDelays();
    validateTransport(webhook, errors);
    if (delays == null || delays.isEmpty()) {
      errors.reject(
          "simulator.webhook.retry-delays.missing",
          "simulator.webhook.retry-delays must list one delay per delivery attempt; its length is"
              + " the attempt cap");
      return;
    }
    if (delays.size() > MAX_WEBHOOK_ATTEMPTS) {
      errors.reject(
          "simulator.webhook.retry-delays.too-many",
          "simulator.webhook.retry-delays must not describe more than "
              + MAX_WEBHOOK_ATTEMPTS
              + " delivery attempts");
    }
    if (!delays.get(0).isZero()) {
      errors.reject(
          "simulator.webhook.retry-delays.first-not-immediate",
          "the first webhook attempt must be immediate, so the first entry of"
              + " simulator.webhook.retry-delays must be zero");
    }
    for (int i = 1; i < delays.size(); i++) {
      if (delays.get(i).compareTo(delays.get(i - 1)) < 0) {
        errors.reject(
            "simulator.webhook.retry-delays.decreasing",
            "simulator.webhook.retry-delays must not decrease: entry "
                + i
                + " is shorter than entry "
                + (i - 1));
        return;
      }
    }
  }

  private void validateTransport(SimulatorProperties.Webhook webhook, Errors errors) {
    validatePositiveTimeout(webhook.connectTimeout(), "simulator.webhook.connect-timeout", errors);
    validatePositiveTimeout(webhook.readTimeout(), "simulator.webhook.read-timeout", errors);
    validatePositiveTimeout(webhook.pollInterval(), "simulator.webhook.poll-interval", errors);
    int preview = webhook.maxResponsePreviewBytes();
    if (preview < 1 || preview > MAX_RESPONSE_PREVIEW_BYTES) {
      errors.reject(
          "simulator.webhook.max-response-preview-bytes.range",
          "simulator.webhook.max-response-preview-bytes must be between 1 and "
              + MAX_RESPONSE_PREVIEW_BYTES);
    }
  }

  private void validatePositiveTimeout(
      @Nullable Duration timeout, String propertyPath, Errors errors) {
    if (timeout == null) {
      errors.reject(propertyPath + ".missing", propertyPath + " must be set");
      return;
    }
    if (timeout.isZero() || timeout.isNegative()) {
      errors.reject(propertyPath + ".not-positive", propertyPath + " must be positive");
    }
  }

  private void validateFault(SimulatorProperties.@Nullable Fault fault, Errors errors) {
    if (fault == null) {
      errors.reject("simulator.fault.missing", "simulator.fault must be configured");
      return;
    }
    Duration longest = fault.longestDelay();
    validatePositiveTimeout(longest, "simulator.fault.longest-delay", errors);
    if (longest != null && longest.compareTo(TransportFault.LONGEST_DELAY) > 0) {
      errors.reject(
          "simulator.fault.longest-delay.too-long",
          "simulator.fault.longest-delay must not exceed "
              + TransportFault.LONGEST_DELAY
              + ", the longest a transport fault may hold a client's connection open");
    }
  }

  private void validateScheduler(SimulatorProperties.@Nullable Scheduler scheduler, Errors errors) {
    if (scheduler == null) {
      errors.reject("simulator.scheduler.missing", "simulator.scheduler must be configured");
      return;
    }
    if (scheduler.poolSize() < 1 || scheduler.poolSize() > MAX_SCHEDULER_POOL_SIZE) {
      errors.reject(
          "simulator.scheduler.pool-size.range",
          "simulator.scheduler.pool-size must be between 1 and " + MAX_SCHEDULER_POOL_SIZE);
    }
  }
}
