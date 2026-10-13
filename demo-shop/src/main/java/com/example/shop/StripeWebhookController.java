package com.example.shop;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StripeWebhookController {

  static final String PATH = "/webhooks/stripe/";

  private final StripeWebhooks webhooks;

  StripeWebhookController(StripeWebhooks webhooks) {
    this.webhooks = webhooks;
  }

  @PostMapping(PATH + "{testRunId}")
  ResponseEntity<String> receive(
      @PathVariable String testRunId,
      @RequestBody String payload,
      @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
    if (signature == null) {
      return ResponseEntity.badRequest().body("no Stripe-Signature header");
    }
    ReceivedEvent received = webhooks.receive(testRunId, payload, signature);
    return ResponseEntity.ok("received " + received.eventId());
  }

  @ExceptionHandler({TheWebhookIsNotStripes.class, TheWebhookNamesNoObject.class})
  ResponseEntity<String> notAcceptable(RuntimeException refused) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(refused.getMessage());
  }

  @ExceptionHandler(NoSuchStoryRun.class)
  ResponseEntity<String> noSuchRun(NoSuchStoryRun missing) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(missing.getMessage());
  }
}
