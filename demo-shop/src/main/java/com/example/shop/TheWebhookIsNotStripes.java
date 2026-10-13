package com.example.shop;

public final class TheWebhookIsNotStripes extends RuntimeException {

  private static final long serialVersionUID = 1L;

  TheWebhookIsNotStripes(String testRunId, Throwable cause) {
    super(
        "a webhook for "
            + testRunId
            + " carried no signature this run's signing secret made: "
            + cause.getMessage(),
        cause);
  }

  TheWebhookIsNotStripes(String testRunId, String why) {
    super("a webhook for " + testRunId + " carried " + why);
  }
}
