package com.example.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class StripeWebhooks {

  private static final Pattern A_TIMESTAMP = Pattern.compile("\\d{1,18}");

  private final StoryRuns runs;

  public StripeWebhooks(StoryRuns runs) {
    this.runs = runs;
  }

  public ReceivedEvent receive(String testRunId, String payload, String signature) {
    StoryRun run = runs.called(testRunId);
    if (!shapedLikeStripes(signature)) {
      throw new TheWebhookIsNotStripes(
          testRunId, "a Stripe-Signature header shaped unlike Stripe's");
    }
    Event event;
    try {
      event =
          Webhook.constructEvent(
              payload,
              signature,
              run.webhookSecret(),
              Webhook.DEFAULT_TOLERANCE,
              run.theClockTheSimulatorKeepsForIt());
    } catch (SignatureVerificationException notSigned) {
      throw new TheWebhookIsNotStripes(testRunId, notSigned);
    }
    ReceivedEvent received =
        new ReceivedEvent(event.getId(), event.getType(), theObjectItNames(testRunId, event));
    run.receivedAn(received);
    return received;
  }

  private static boolean shapedLikeStripes(String signature) {
    for (String item : signature.split(",", -1)) {
      int equals = item.indexOf('=');
      if (equals < 0) {
        return false;
      }
      String key = item.substring(0, equals);
      String value = item.substring(equals + 1);
      if (key.equals("t") && !A_TIMESTAMP.matcher(value).matches()) {
        return false;
      }
    }
    return true;
  }

  private static String theObjectItNames(String testRunId, Event event) {
    String object = event.getData() == null ? null : event.getDataObjectDeserializer().getRawJson();
    JsonElement id =
        object == null ? null : JsonParser.parseString(object).getAsJsonObject().get("id");
    if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
      throw new TheWebhookNamesNoObject(testRunId, event.getId());
    }
    return id.getAsString();
  }
}
