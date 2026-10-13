package com.example.checkout.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.model.StripeCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.UUID;

public final class VendorClientSteps {

  private final TheShopsWorld world;

  private Customer customer;
  private SetupIntent setup;
  private PaymentMethod savedCard;
  private PaymentIntent captured;
  private StripeException refusal;
  private RequestOptions theShopsIdempotencyKey;

  public VendorClientSteps(TheShopsWorld world) {
    this.world = world;
  }

  @When("the vendor's own client saves a card to a new customer")
  public void theVendorsClientSavesACardToANewCustomer() throws StripeException {
    StripeShop shop = world.stripeShop();
    customer =
        shop.vendorClient()
            .v1()
            .customers()
            .create(CustomerCreateParams.builder().setEmail("dana@example.test").build());
    SetupIntent created =
        shop.vendorClient()
            .v1()
            .setupIntents()
            .create(
                SetupIntentCreateParams.builder()
                    .setCustomer(customer.getId())
                    .setPaymentMethod(shop.theCardTheShopperSaved())
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .build());
    assertThat(created.getStatus()).isEqualTo("requires_confirmation");
    setup = shop.vendorClient().v1().setupIntents().confirm(created.getId());
    savedCard = shop.vendorClient().v1().paymentMethods().retrieve(shop.theCardTheShopperSaved());
  }

  @Then("the vendor's client parsed a customer, a setup intent and a payment method")
  public void theVendorsClientParsedTheThreeObjects() {
    assertThat(customer.getObject()).isEqualTo("customer");
    assertThat(customer.getId()).startsWith("cus_");
    assertThat(customer.getLivemode()).isFalse();
    assertThat(setup.getObject()).isEqualTo("setup_intent");
    assertThat(setup.getId()).startsWith("seti_");
    assertThat(setup.getStatus()).isEqualTo("succeeded");
    assertThat(setup.getUsage()).isEqualTo("off_session");
    assertThat(setup.getClientSecret()).isNotBlank();
    assertThat(savedCard.getObject()).isEqualTo("payment_method");
    assertThat(savedCard.getType()).isEqualTo("card");
    assertThat(savedCard.getCard().getLast4()).isEqualTo("4242");
    assertThat(savedCard.getCard().getExpMonth()).isEqualTo(12L);
    assertThat(savedCard.getCard().getExpYear()).isEqualTo(2030L);
  }

  @Then("the saved card is attached to that customer and listed under it")
  public void theSavedCardIsAttachedAndListed() throws StripeException {
    StripeShop shop = world.stripeShop();
    assertThat(savedCard.getCustomer()).isEqualTo(customer.getId());
    StripeCollection<PaymentMethod> listed =
        shop.vendorClient().v1().customers().paymentMethods().list(customer.getId());
    assertThat(listed.getObject()).isEqualTo("list");
    assertThat(listed.getHasMore()).isFalse();
    assertThat(listed.getData()).hasSize(1);
    assertThat(listed.getData().getFirst().getId()).isEqualTo(shop.theCardTheShopperSaved());
  }

  @When("the vendor's own client takes a hold of {word} off-session and captures it")
  public void theVendorsClientTakesAHoldAndCapturesIt(String amount) throws StripeException {
    StripeShop shop = world.stripeShop();
    PaymentIntent hold =
        shop.vendorClient()
            .v1()
            .paymentIntents()
            .create(
                PaymentIntentCreateParams.builder()
                    .setAmount(minorUnits(amount))
                    .setCurrency("usd")
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .setCustomer(customer.getId())
                    .setPaymentMethod(shop.theCardTheShopperSaved())
                    .setConfirm(true)
                    .setOffSession(true)
                    .build());
    assertThat(hold.getStatus()).isEqualTo("requires_capture");
    assertThat(hold.getAmountCapturable()).isEqualTo(minorUnits(amount));
    assertThat(hold.getLatestCharge()).isNotBlank();
    captured =
        shop.vendorClient()
            .v1()
            .paymentIntents()
            .capture(
                hold.getId(),
                PaymentIntentCaptureParams.builder()
                    .setAmountToCapture(minorUnits(amount))
                    .build());
    world.thePaymentIs(captured.getId());
  }

  @Then("the vendor's client parsed a captured payment intent worth {word}")
  public void theVendorsClientParsedACapturedIntent(String amount) throws StripeException {
    assertThat(captured.getObject()).isEqualTo("payment_intent");
    assertThat(captured.getStatus()).isEqualTo("succeeded");
    assertThat(captured.getAmountReceived()).isEqualTo(minorUnits(amount));
    assertThat(captured.getAmountCapturable()).isZero();
    assertThat(captured.getCaptureMethod()).isEqualTo("manual");
    assertThat(
            world
                .stripeShop()
                .vendorClient()
                .v1()
                .charges()
                .retrieve(captured.getLatestCharge())
                .getCaptured())
        .isTrue();
  }

  @Then("every answer the vendor's client read was application\\/json")
  public void everyAnswerTheVendorsClientReadWasJson() {
    assertThat(world.stripeShop().transcript().contentTypesTheVendorClientRead())
        .containsExactly("application/json");
  }

  @Then(
      "the vendor's client authenticated with bearer tokens, which this contract accepts beside"
          + " basic auth")
  public void theVendorsClientAuthenticatedWithBearerTokens() {
    assertThat(world.stripeShop().transcript().authenticationSchemesItSent())
        .containsExactly("Bearer");
  }

  @When("the vendor's own client confirms that setup a second time")
  public void theVendorsClientConfirmsThatSetupASecondTime() {
    try {
      world.stripeShop().vendorClient().v1().setupIntents().confirm(setup.getId());
      fail("the simulator confirmed a setup that had already succeeded");
    } catch (StripeException refused) {
      refusal = refused;
    }
  }

  @When("the vendor's own client captures it a second time")
  public void theVendorsClientCapturesItASecondTime() {
    try {
      world
          .stripeShop()
          .vendorClient()
          .v1()
          .paymentIntents()
          .capture(captured.getId(), PaymentIntentCaptureParams.builder().build());
      fail("the simulator captured a payment that had already succeeded");
    } catch (StripeException refused) {
      refusal = refused;
    }
  }

  @When("the vendor's own client asks for a payment intent that was never created")
  public void theVendorsClientAsksForAnIntentThatWasNeverCreated() {
    try {
      world
          .stripeShop()
          .vendorClient()
          .v1()
          .paymentIntents()
          .retrieve("pi_sim_nothing_was_ever_created_here");
      fail("the simulator answered a payment intent nobody created");
    } catch (StripeException refused) {
      refusal = refused;
    }
  }

  @Then("the vendor's client raised an invalid request naming {word} with status {int}")
  public void theVendorsClientRaisedAnInvalidRequest(String code, int status) {
    assertThat(refusal).isInstanceOf(InvalidRequestException.class);
    assertThat(refusal.getStatusCode()).isEqualTo(status);
    assertThat(refusal.getCode()).isEqualTo(code);
    assertThat(refusal.getRequestId()).isNotBlank();
  }

  @Then("the vendor's client raised an invalid request naming {word}")
  public void theVendorsClientRaisedAnInvalidRequest(String code) {
    assertThat(refusal).isInstanceOf(InvalidRequestException.class);
    assertThat(refusal.getCode()).isEqualTo(code);
  }

  @Then("the error it parsed embeds the payment intent, which reports itself as succeeded")
  public void theErrorItParsedEmbedsThePaymentIntent() {
    PaymentIntent embedded = refusal.getStripeError().getPaymentIntent();
    assertThat(embedded).isNotNull();
    assertThat(embedded.getId()).isEqualTo(captured.getId());
    assertThat(embedded.getStatus()).isEqualTo("succeeded");
    assertThat(embedded.getAmountCapturable()).isZero();
  }

  @Then("the error it parsed carries a type, a message and a documentation link")
  public void theErrorItParsedCarriesTheThreeThings() {
    assertThat(refusal.getStripeError()).isNotNull();
    assertThat(refusal.getStripeError().getType()).isEqualTo("invalid_request_error");
    assertThat(refusal.getStripeError().getMessage()).isNotBlank();
    assertThat(refusal.getStripeError().getDocUrl()).isNotBlank();
  }

  @When("the shop captures {word} under an idempotency key of its own")
  public void theShopCapturesUnderAnIdempotencyKeyOfItsOwn(String amount) {
    theShopsIdempotencyKey =
        RequestOptions.builder().setIdempotencyKey(UUID.randomUUID().toString()).build();
    world.theShopWasAnswered(
        world.stripeShop().captures(world.paymentId(), amount, theShopsIdempotencyKey));
  }

  @When("the shop captures {word} again under the same idempotency key")
  public void theShopCapturesAgainUnderTheSameIdempotencyKey(String amount) {
    world.theShopWasAnswered(
        world.stripeShop().captures(world.paymentId(), amount, theShopsIdempotencyKey));
  }

  @Then("the answer announced itself as a replay")
  public void theAnswerAnnouncedItselfAsAReplay() {
    assertThat(world.stripeShop().theLastAnswersHeader("Idempotent-Replayed")).contains("true");
  }

  @When("the vendor's own client captures {word} and meets the gateway instead of the provider")
  public void theVendorsClientMeetsTheGatewayInsteadOfTheProvider(String amount) {
    try {
      world
          .stripeShop()
          .vendorClient()
          .v1()
          .paymentIntents()
          .capture(
              world.paymentId(),
              PaymentIntentCaptureParams.builder().setAmountToCapture(minorUnits(amount)).build());
      fail("the simulator captured a payment the scenario answered 502 instead of capturing");
    } catch (StripeException answeredInFrontOfTheProvider) {
      refusal = answeredInFrontOfTheProvider;
    }
  }

  @Then("the vendor's client raised an exception of its own carrying status {int}")
  public void theVendorsClientRaisedAnExceptionOfItsOwnCarryingStatus(int status) {
    assertThat(refusal).isInstanceOf(StripeException.class);
    assertThat(refusal.getStatusCode()).isEqualTo(status);
  }

  @Then("the shop can tell it was the gateway that answered and not the provider")
  public void theShopCanTellItWasTheGatewayThatAnswered() {
    assertThat(refusal.getStripeError()).isNull();
    assertThat(refusal.getMessage()).contains("was answered in front of the provider");
    assertThat(world.stripeShop().transcript().contentTypesTheVendorClientRead())
        .contains("text/plain;charset=utf-8");
  }

  @Then("the answer arrived under content type {word}")
  public void theAnswerArrivedUnderContentType(String contentType) {
    assertThat(world.lastAnswer().contentType()).isEqualTo(contentType);
  }

  @Then("the same shop configured to answer as its published example uses {word}")
  public void theSameShopConfiguredToAnswerAsItsPublishedExampleUses(String contentType) {
    GrowShop shop = world.growShop();
    shop.answersInTheFormItsPublishedExampleShows(world.clockPinnedTo(), world.endpoint().url());
    assertThat(shop.theContentTypeOfItsAnswers("7.00")).isEqualTo(contentType);
  }

  private static long minorUnits(String amount) {
    return new BigDecimal(amount).movePointRight(2).longValueExact();
  }
}
