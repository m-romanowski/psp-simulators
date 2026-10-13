package com.example.shop;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("shop")
public record ShopSettings(
    String simulatorApiUrl,
    String simulatorBrowserUrl,
    String callbackBaseUrl,
    @DefaultValue("2s") Duration simulatorConnectTimeout,
    @DefaultValue("10s") Duration simulatorReadTimeout) {

  public ShopSettings {
    simulatorApiUrl = withoutTrailingSlash(simulatorApiUrl);
    simulatorBrowserUrl = withoutTrailingSlash(simulatorBrowserUrl);
    callbackBaseUrl = withoutTrailingSlash(callbackBaseUrl);
  }

  public String whereStripeCallsBackFor(String testRunId) {
    return callbackBaseUrl + StripeWebhookController.PATH + testRunId;
  }

  private static String withoutTrailingSlash(String url) {
    return url.replaceAll("/+$", "");
  }
}
