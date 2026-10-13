package com.example.checkout.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class CallbackWireSteps {

  private static final String CONTENT_TYPE = "Content-Type";

  private static final String STRIPE_SIGNATURE = "Stripe-Signature";

  private final TheShopsWorld world;

  public CallbackWireSteps(TheShopsWorld world) {
    this.world = world;
  }

  @Then("every attempt says it went out as {word}, which is what the shop received")
  public void everyAttemptSaysWhatContentTypeItWentOutAs(String contentType) {
    JsonNode attempts = world.shop().control().theOnlyDelivery().get("attempts");
    assertThat(attempts).isNotEmpty();
    for (JsonNode attempt : attempts) {
      JsonNode sent = attempt.get("sentHeaders");
      assertThat(sent)
          .withFailMessage("the trail recorded no wire for attempt %s", attempt.get("number"))
          .isNotNull();
      assertThat(sent.isNull()).isFalse();
      assertThat(sent.isEmpty()).isFalse();
      assertThat(headerIn(sent, CONTENT_TYPE)).isEqualTo(contentType);
    }
    assertThat(world.endpoint().everythingReceived()).isNotEmpty();
    for (MerchantEndpoint.Received received : world.endpoint().everythingReceived()) {
      assertThat(received.header("content-type")).contains(contentType);
    }
  }

  @Then("each attempt says it went out as {word} beside the signature that signed it")
  public void eachAttemptSaysItWentOutAsJsonBesideItsOwnSignature(String contentType) {
    JsonNode attempts = world.shop().control().theOnlyDelivery().get("attempts");
    List<MerchantEndpoint.Received> received = world.endpoint().everythingReceived();
    assertThat(attempts).hasSize(received.size());
    List<String> recordedSignatures = new ArrayList<>();
    Iterator<JsonNode> made = attempts.iterator();
    for (MerchantEndpoint.Received arrived : received) {
      JsonNode sent = made.next().get("sentHeaders");
      assertThat(headerIn(sent, CONTENT_TYPE)).isEqualTo(contentType);
      String recorded = headerIn(sent, STRIPE_SIGNATURE);
      assertThat(recorded).isEqualTo(arrived.header("stripe-signature").orElseThrow());
      recordedSignatures.add(recorded);
    }
    assertThat(recordedSignatures).doesNotHaveDuplicates();
  }

  private static String headerIn(JsonNode sentHeaders, String name) {
    Iterator<String> named = sentHeaders.fieldNames();
    while (named.hasNext()) {
      String field = named.next();
      if (field.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
        return sentHeaders.get(field).get(0).asText();
      }
    }
    throw new AssertionError(
        "the trail recorded no " + name + " for this attempt, only " + sentHeaders);
  }
}
