package com.example.shop;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SimulatorScreens {

  private static final String PROVIDER = "stripe";

  private final String browserUrl;

  SimulatorScreens(ShopSettings settings) {
    this.browserUrl = settings.simulatorBrowserUrl();
  }

  public String configuration(String testRunId) {
    return screen("/ui/configuration", testRunId).toUriString();
  }

  public String savedCards(String testRunId) {
    return screen("/ui/payment-methods", testRunId).toUriString();
  }

  public String rules(String testRunId) {
    return screen("/ui/scenarios", testRunId).toUriString();
  }

  public String trail(String testRunId) {
    return screen("/ui/audit", testRunId).toUriString();
  }

  public String payment(String testRunId, String paymentIntentId) {
    return screen("/ui/objects", testRunId)
        .queryParam("providerObjectId", paymentIntentId)
        .toUriString();
  }

  public String timeline(String testRunId, String paymentIntentId) {
    return screen("/ui/timeline", testRunId).queryParam("key", paymentIntentId).toUriString();
  }

  public String trailOf(String testRunId, String paymentIntentId) {
    return screen("/ui/audit", testRunId)
        .queryParam("providerObjectId", paymentIntentId)
        .toUriString();
  }

  public String webhooks(String testRunId, String paymentIntentId) {
    return screen("/ui/webhooks", testRunId)
        .queryParam("providerObjectId", paymentIntentId)
        .toUriString();
  }

  private UriComponentsBuilder screen(String path, String testRunId) {
    return UriComponentsBuilder.fromUriString(browserUrl)
        .path(path)
        .queryParam("provider", PROVIDER)
        .queryParam("testRunId", testRunId);
  }
}
