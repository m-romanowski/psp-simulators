package com.example.checkout.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CheckoutSteps {

  private final TheShopsWorld world;

  public CheckoutSteps(TheShopsWorld world) {
    this.world = world;
  }

  @After
  public void everythingIsPutBack(Scenario scenario) {
    if (scenario.isFailed() && !world.theVendorClientTranscript().isEmpty()) {
      scenario.log(
          "every exchange the vendor's own client made, with its secrets withheld:\n"
              + world.theVendorClientTranscript());
    }
    world.everythingIsPutBack();
  }

  @Given("a test run of our own, with the clock pinned to {word}")
  public void aTestRunOfOurOwn(String instant) {
    world.theClockIsPinnedTo(instant);
  }

  @Given("a shop that takes payments through {word}")
  public void aShopThatTakesPaymentsThrough(String provider) {
    Shop shop = world.newShopFor(provider);
    shop.opensForBusiness(world.clockPinnedTo(), world.endpoint().url());
    world.theShopIs(shop);
  }

  @Given(
      "a shop that takes payments through Grow, which acknowledges callbacks with a separate"
          + " call")
  public void aGrowShopThatAcknowledgesWithASeparateCall() {
    GrowShop shop = (GrowShop) world.newShopFor("Grow");
    shop.acknowledgesCallbacksWithASeparateCall(world.clockPinnedTo(), world.endpoint().url());
    world.theShopIs(shop);
  }

  @Given("the next capture will be refused because the card has expired")
  public void theNextCaptureWillBeRefusedBecauseTheCardHasExpired() {
    Shop shop = world.shop();
    shop.control()
        .writeRule(
            Http.fields("operation", shop.theOperationThatCaptures()),
            Http.fields(
                "type",
                "TERMINAL_FAILURE",
                "providerCode",
                shop.theScenarioNameFor(Shop.Refusal.AN_EXPIRED_CARD)),
            100);
  }

  @Given("the answer to the next capture will be lost on the way back")
  public void theAnswerToTheNextCaptureWillBeLost() {
    world.shop().control().expandPreset("capture-accepted-response-lost", 100);
  }

  @Given("the next capture will be answered {int} without reaching the provider at all")
  public void theNextCaptureWillBeAnsweredWithoutReachingTheProvider(int status) {
    assertThat(status).isEqualTo(502);
    world.shop().control().expandPreset("psp-5xx-then-success", 100);
  }

  @Given("every callback of this run will be dropped instead of sent")
  public void everyCallbackOfThisRunWillBeDropped() {
    world.shop().control().writeRule(Map.of(), Http.fields("type", "DELIVERY_DROPPED"), 100);
  }

  @Given("the run's retry plan is three attempts with no delay between them")
  public void theRunsRetryPlanIsThreeAttemptsWithNoDelay() {
    world.shop().control().applySettings(Map.of("webhook.retryPlan", "PT0S,PT0S,PT0S"));
  }

  @Given("the shop answers every callback with {int}")
  public void theShopAnswersEveryCallbackWith(int status) {
    world.endpoint().answerEveryCallbackWith(status);
  }

  @When("a payer authorises a hold of {word}")
  public void aPayerAuthorisesAHoldOf(String amount) {
    world.thePaymentIs(world.shop().aPayerAuthorisesAHoldOf(amount));
  }

  @When("a payer pays for a hosted payment of {word}")
  public void aPayerPaysForAHostedPaymentOf(String amount) {
    world.growShop().aPayerPaysForAHostedPaymentOf(amount);
  }

  @When("the shop captures {word}")
  @When("the shop captures {word} again")
  public void theShopCaptures(String amount) {
    world.theShopWasAnswered(world.shop().captures(world.paymentId(), amount));
  }

  @When("the shop refunds {word}")
  @When("the shop refunds {word} again")
  @When("a shop that ignores that answer refunds {word} anyway")
  public void theShopRefunds(String amount) {
    world.theShopWasAnswered(world.shop().refunds(world.paymentId(), amount));
  }

  @When("a shop that reads that answer decides whether to refund the remaining {word}")
  public void aShopThatReadsThatAnswerDecidesWhetherToRefund(String amount) {
    if (!world.shop().control().aRefundWouldBeReturnedOn(world.paymentId())) {
      return;
    }
    world.theShopWasAnswered(world.shop().refunds(world.paymentId(), amount));
    world.theShopAskedForAFurtherRefund();
  }

  @When("a day passes on the simulator's clock")
  public void aDayPassesOnTheSimulatorsClock() {
    daysPassOnTheSimulatorsClock(1);
  }

  @When("{int} days pass on the simulator's clock")
  public void daysPassOnTheSimulatorsClock(int days) {
    world.shop().control().advanceClockBy("PT" + days * 24 + "H");
  }

  @When("the shop reconciles the payment with the provider's own lookup")
  public void theShopReconcilesWithTheProvidersOwnLookup() {
    world.theReconciliationSaid(world.shop().reconciles(world.paymentId()));
  }

  @When("the simulator sends that callback again under the same event id")
  public void theSimulatorSendsThatCallbackAgainUnderTheSameEventId() {
    JsonNode delivery = world.shop().control().theOnlyDelivery();
    world
        .shop()
        .control()
        .sendCallbackAbout(
            delivery.get("providerObjectId").asText(),
            delivery.get("eventType").asText(),
            delivery.get("eventId").asText());
  }

  @When("the simulator sends the provider's own callback about the finished payment")
  public void theSimulatorSendsTheProvidersOwnCallback() {
    world
        .shop()
        .control()
        .sendCallbackAbout(world.paymentId(), world.shop().theEventOfAFinishedPayment(), null);
  }

  @When("the simulator delivers whatever has become due")
  public void theSimulatorDeliversWhateverHasBecomeDue() {
    world.shop().control().deliverWhateverIsDue();
  }

  @When("the simulator retries the delivery the provider had given up on")
  @When("the simulator retries that delivery")
  public void theSimulatorRetriesTheDelivery() {
    world
        .shop()
        .control()
        .retryDelivery(world.shop().control().theOnlyDelivery().get("webhookJobId").asText());
  }

  @When("the shop acknowledges the callback with the provider's own call")
  public void theShopAcknowledgesTheCallback() {
    world.growShop().acknowledgesTheCallbackOf(world.paymentId());
  }

  @Then("the simulator holds the payment as {word}")
  public void theSimulatorHoldsThePaymentAs(String normalizedStatus) {
    assertThat(world.shop().control().normalizedStatusOf(world.paymentId()))
        .isEqualTo(normalizedStatus);
  }

  @Then("the simulator says a capture would still be taken")
  public void theSimulatorSaysACaptureWouldStillBeTaken() {
    assertThat(world.shop().control().aCaptureWouldBeTakenOn(world.paymentId()))
        .withFailMessage("the simulator says no capture would be taken on this payment")
        .isTrue();
  }

  @Then("the simulator says no capture would be taken")
  public void theSimulatorSaysNoCaptureWouldBeTaken() {
    assertThat(world.shop().control().aCaptureWouldBeTakenOn(world.paymentId()))
        .withFailMessage("the simulator says a capture would still be taken on this payment")
        .isFalse();
  }

  @Then("the simulator says a refund would still be returned")
  public void theSimulatorSaysARefundWouldStillBeReturned() {
    assertThat(world.shop().control().aRefundWouldBeReturnedOn(world.paymentId()))
        .withFailMessage("the simulator says no refund would be returned on this payment")
        .isTrue();
  }

  @Then("the simulator says no refund would be returned")
  public void theSimulatorSaysNoRefundWouldBeReturned() {
    assertThat(world.shop().control().aRefundWouldBeReturnedOn(world.paymentId()))
        .withFailMessage("the simulator says a refund would still be returned on this payment")
        .isFalse();
  }

  @Then("the simulator holds the payment as {word}, with {word} of it taken and not returned")
  public void theSimulatorHoldsThePaymentAsWithTakenAndNotReturned(
      String normalizedStatus, String remainder) {
    assertThat(world.shop().control().normalizedStatusOf(world.paymentId()))
        .isEqualTo(normalizedStatus);
    theRemainderIs(remainder);
  }

  @Then("no further refund was asked for, and {word} of it is taken and not returned")
  public void noFurtherRefundWasAskedFor(String remainder) {
    assertThat(world.furtherRefundsTheShopAskedFor())
        .withFailMessage("the shop asked for a further refund after reading the answer")
        .isZero();
    theRemainderIs(remainder);
  }

  @Then("the further refund was accepted, and {word} of it is taken and not returned")
  public void theFurtherRefundWasAccepted(String remainder) {
    assertThat(world.furtherRefundsTheShopAskedFor())
        .withFailMessage("the shop asked for no further refund after reading the answer")
        .isEqualTo(1);
    theRefundIsAccepted();
    theRemainderIs(remainder);
  }

  @Then("the simulator holds the payment as {word}, with {word} of it never captured")
  public void theSimulatorHoldsThePaymentAsWithNeverCaptured(
      String normalizedStatus, String remainder) {
    assertThat(world.shop().control().normalizedStatusOf(world.paymentId()))
        .isEqualTo(normalizedStatus);
    assertThat(world.shop().control().capturableRemainderOf(world.paymentId()))
        .isEqualTo(remainder);
  }

  @Then("the capture is accepted")
  public void theCaptureIsAccepted() {
    assertThat(world.lastAnswer().outcome())
        .withFailMessage("the provider answered %s", world.lastAnswer())
        .isEqualTo(Shop.Outcome.ACCEPTED);
  }

  @Then("the capture is refused because the card has expired")
  public void theCaptureIsRefusedBecauseTheCardHasExpired() {
    theCaptureIsRefusedWith(Shop.Refusal.AN_EXPIRED_CARD);
  }

  @Then("the capture is refused because the authorisation is too old")
  public void theCaptureIsRefusedBecauseTheAuthorisationIsTooOld() {
    theCaptureIsRefusedWith(Shop.Refusal.AN_AUTHORISATION_PAST_ITS_WINDOW);
  }

  @Then("the refund is accepted")
  public void theRefundIsAccepted() {
    assertThat(world.lastAnswer().outcome())
        .withFailMessage("the provider answered %s", world.lastAnswer())
        .isEqualTo(Shop.Outcome.ACCEPTED);
  }

  @Then("the refund is refused because this transaction has already been refunded")
  public void theRefundIsRefusedBecauseItHasAlreadyBeenRefunded() {
    theRefundIsRefusedWith(world.growShop().theCodeForASecondRefund());
  }

  @Then("the refund is refused because a partial refund cannot be made on the day of the charge")
  public void theRefundIsRefusedBecauseItIsPartialOnTheDayOfTheCharge() {
    theRefundIsRefusedWith(world.growShop().theCodeForAPartialRefundOnTheDayOfTheCharge());
  }

  @Then("no answer came back at all")
  public void noAnswerCameBackAtAll() {
    assertThat(world.lastAnswer().outcome())
        .withFailMessage("the provider answered %s", world.lastAnswer())
        .isEqualTo(Shop.Outcome.NO_ANSWER_AT_ALL);
  }

  @Then("the shop was handed a server error with no provider envelope in it")
  public void theShopWasHandedAServerError() {
    assertThat(world.lastAnswer().outcome())
        .withFailMessage("the provider answered %s", world.lastAnswer())
        .isEqualTo(Shop.Outcome.SERVER_ERROR);
  }

  @Then("no money has moved at all")
  public void noMoneyHasMovedAtAll() {
    assertThat(world.shop().control().capturedAmountOf(world.paymentId())).isEqualTo("0");
  }

  @Then("{word} has been captured")
  public void hasBeenCaptured(String amount) {
    assertThat(world.shop().control().capturedAmountOf(world.paymentId()))
        .isEqualTo(world.shop().theSimulatorsWordFor(amount));
  }

  @Then("{word} has been captured, once")
  public void hasBeenCapturedOnce(String amount) {
    hasBeenCaptured(amount);
    JsonNode state = world.shop().control().stateOf(world.paymentId()).json();
    List<String> captures = new ArrayList<>();
    for (JsonNode operation : state.get("operations")) {
      if ("CAPTURE".equals(operation.get("kind").asText())) {
        captures.add(operation.get("amount").asText());
      }
    }
    assertThat(captures).containsExactly(world.shop().theSimulatorsWordFor(amount));
  }

  @Then("the lookup says the money did move, and {word} has been captured")
  public void theLookupSaysTheMoneyDidMove(String amount) {
    Shop.Reconciled reconciled = world.lastReconciliation();
    assertThat(reconciled.capturedAmount()).isEqualTo(amount);
    assertThat(reconciled.providerStatus()).isIn("2", "succeeded");
    hasBeenCaptured(amount);
  }

  @Then("the provider's own process lookup says the payer did pay")
  public void theProvidersOwnProcessLookupSaysThePayerDidPay() {
    assertThat(world.growShop().theShopLooksThePaymentProcessUp().providerStatus()).isEqualTo("11");
  }

  @Then("the trail names the rule that decided the capture")
  public void theTrailNamesTheRuleThatDecidedTheCapture() {
    assertThat(world.shop().control().theRuleThatDecided(pathOfTheCapture()))
        .withFailMessage("no row of the trail carries the scenario that decided the capture")
        .isPresent();
  }

  @Then("the shop received {int} callback")
  @Then("the shop received {int} callbacks")
  public void theShopReceivedCallbacks(int howMany) {
    assertThat(world.endpoint().everythingReceived()).hasSize(howMany);
  }

  @Then("the shop received no callback at all")
  public void theShopReceivedNoCallbackAtAll() {
    assertThat(world.endpoint().everythingReceived()).isEmpty();
  }

  @Then("the callback says a hold is waiting, and names the transaction and its token")
  public void theCallbackSaysAHoldIsWaiting() {
    MerchantEndpoint.Received callback = world.endpoint().theOnlyThingReceived();
    assertThat(callback.header("content-type")).contains("application/x-www-form-urlencoded");
    assertThat(callback.field("data[statusCode]")).contains("11");
    assertThat(callback.field("data[transactionId]")).isPresent();
    assertThat(callback.field("data[transactionToken]")).isPresent();
  }

  @Then("both callbacks carried exactly the same bytes")
  public void bothCallbacksCarriedExactlyTheSameBytes() {
    List<MerchantEndpoint.Received> all = world.endpoint().everythingReceived();
    assertThat(all.get(1).body()).isEqualTo(all.get(0).body());
  }

  @Then("the run recorded the delivery as dropped, with no attempt made")
  public void theRunRecordedTheDeliveryAsDropped() {
    JsonNode delivery = world.shop().control().theOnlyDelivery();
    assertThat(delivery.get("status").asText()).isEqualTo("DROPPED");
    assertThat(delivery.get("attemptsMade").asInt()).isZero();
  }

  @Then("the delivery has been attempted {int} time and is still pending")
  public void theDeliveryHasBeenAttemptedAndIsStillPending(int attempts) {
    JsonNode delivery = world.shop().control().theOnlyDelivery();
    assertThat(delivery.get("attemptsMade").asInt()).isEqualTo(attempts);
    assertThat(delivery.get("status").asText()).isEqualTo("PENDING");
  }

  @Then("the delivery has run out of attempts after {int} of them")
  public void theDeliveryHasRunOutOfAttempts(int attempts) {
    JsonNode delivery = world.shop().control().theOnlyDelivery();
    assertThat(delivery.get("attemptsMade").asInt()).isEqualTo(attempts);
    assertThat(delivery.get("status").asText()).isEqualTo("EXHAUSTED");
  }

  @Then("every attempt names the target it was posted to and the verdict it came back with")
  public void everyAttemptNamesItsTargetAndVerdict() {
    JsonNode delivery = world.shop().control().theOnlyDelivery();
    assertThat(delivery.get("attempts")).isNotEmpty();
    for (JsonNode attempt : delivery.get("attempts")) {
      assertThat(attempt.get("targetUrl").asText()).isEqualTo(world.endpoint().url());
      assertThat(attempt.get("verdict").asText()).isEqualTo("REFUSED");
      assertThat(attempt.get("httpStatus").asInt()).isEqualTo(500);
    }
  }

  @Then("the delivery is marked delivered after {int} attempts")
  @Then("the delivery is marked delivered after {int} attempt")
  public void theDeliveryIsMarkedDelivered(int attempts) {
    JsonNode delivery = world.shop().control().theOnlyDelivery();
    assertThat(delivery.get("status").asText()).isEqualTo("DELIVERED");
    assertThat(delivery.get("attemptsMade").asInt()).isEqualTo(attempts);
  }

  @Then("the delivery is still waiting for a separate acknowledgement")
  public void theDeliveryIsStillWaitingForASeparateAcknowledgement() {
    JsonNode delivery = world.shop().control().theOnlyDelivery();
    assertThat(delivery.get("status").asText()).isEqualTo("PENDING");
    assertThat(verdictsOf(delivery)).containsExactly("AWAITING_SEPARATE_CALL");
  }

  @Then("the callback carried a signature header")
  public void theCallbackCarriedASignatureHeader() {
    assertThat(world.endpoint().theOnlyThingReceived().header("stripe-signature")).isPresent();
  }

  @Then("the second attempt was signed afresh, so it does not carry the first one's signature")
  public void theSecondAttemptWasSignedAfresh() {
    List<MerchantEndpoint.Received> all = world.endpoint().everythingReceived();
    assertThat(all.get(0).header("stripe-signature")).isPresent();
    assertThat(all.get(1).header("stripe-signature"))
        .isPresent()
        .isNotEqualTo(all.get(0).header("stripe-signature"));
    JsonNode attempts = world.shop().control().theOnlyDelivery().get("attempts");
    assertThat(attempts.get(1).get("attemptedAt").asText())
        .isNotEqualTo(attempts.get(0).get("attemptedAt").asText());
  }

  private void theRemainderIs(String remainder) {
    assertThat(world.shop().control().refundableRemainderOf(world.paymentId()))
        .isEqualTo(world.shop().theSimulatorsWordFor(remainder));
  }

  private void theRefundIsRefusedWith(String providerCode) {
    assertThat(world.lastAnswer().outcome())
        .withFailMessage("the provider answered %s", world.lastAnswer())
        .isEqualTo(Shop.Outcome.REFUSED);
    assertThat(world.lastAnswer().providerCode()).isEqualTo(providerCode);
  }

  private void theCaptureIsRefusedWith(Shop.Refusal refusal) {
    assertThat(world.lastAnswer().outcome())
        .withFailMessage("the provider answered %s", world.lastAnswer())
        .isEqualTo(Shop.Outcome.REFUSED);
    assertThat(world.lastAnswer().providerCode()).isEqualTo(world.shop().theCodeFor(refusal));
  }

  private String pathOfTheCapture() {
    return world.shop() instanceof GrowShop ? "settleSuspendedTransaction" : "capture";
  }

  private static List<String> verdictsOf(JsonNode delivery) {
    List<String> verdicts = new ArrayList<>();
    for (JsonNode attempt : delivery.get("attempts")) {
      verdicts.add(attempt.get("verdict").asText());
    }
    return verdicts;
  }
}
