package dev.marcinromanowski.pspsim.kernel.spi;

public class WebhookNotSchedulableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public WebhookNotSchedulableException(String ruleAndProperty) {
    super(ruleAndProperty);
  }
}
