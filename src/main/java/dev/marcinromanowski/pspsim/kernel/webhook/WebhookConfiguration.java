package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.transaction.AfterCommit;
import dev.marcinromanowski.pspsim.kernel.webhook.internal.HttpMerchantEndpoint;
import dev.marcinromanowski.pspsim.kernel.webhook.internal.JdbcWebhookDeliveries;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class WebhookConfiguration {

  @Bean
  public WebhookDeliveries webhookDeliveries(JdbcClient jdbc, ObjectMapper json) {
    return new JdbcWebhookDeliveries(jdbc, json);
  }

  @Bean(destroyMethod = "close")
  public MerchantEndpoint merchantEndpoint(
      @Value("${simulator.webhook.connect-timeout}") Duration connectTimeout,
      @Value("${simulator.webhook.read-timeout}") Duration readTimeout,
      @Value("${simulator.webhook.max-response-preview-bytes}") int maxResponsePreviewBytes) {
    return new HttpMerchantEndpoint(connectTimeout, readTimeout, maxResponsePreviewBytes);
  }

  @Bean
  @Primary
  public CallbackRetryPlans callbackRetryPlans(
      DeclaredProviderSettings declared, TestRunSettings configured, RetryPlan webhookRetryPlan) {
    return new ProviderRetryPlans(declared, configured, webhookRetryPlan);
  }

  @Bean
  public WebhookDispatcher webhookDispatcher(
      WebhookDeliveries deliveries,
      MerchantEndpoint endpoint,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      CallbackRetryPlans callbackRetryPlans,
      DeliveryShaping deliveryShaping,
      List<AcknowledgementPolicy> policies,
      List<AttemptRendering> renderings,
      AfterCommit afterCommit) {
    return new WebhookDispatcher(
        deliveries,
        endpoint,
        audit,
        clock,
        ids,
        callbackRetryPlans,
        deliveryShaping,
        policies,
        renderings,
        afterCommit);
  }

  @Bean
  public WebhookDeliveryPoll webhookDeliveryPoll(WebhookDispatcher dispatcher) {
    return new WebhookDeliveryPoll(dispatcher);
  }
}
