package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class AJourneyInAMobileAppsWebview {

  static final String ACCOUNT = "merchant-1";

  private static final double EVERY_ASSERTION_RETRIES_FOR_THIS_LONG = 15_000;

  private SimulatorUnderTest simulator;
  private TheTestSetup setup;
  private TheProviderApi providerApi;
  private NotThroughTheBrowser shopsServer;
  private TheMerchant merchant;
  private TheWebview webview;
  private String testRunId;
  private final Set<String> held = new HashSet<>();

  abstract String provider();

  @BeforeEach
  void theShopsServerAndTheAppAreReady() {
    PlaywrightAssertions.setDefaultAssertionTimeout(EVERY_ASSERTION_RETRIES_FOR_THIS_LONG);
    simulator = SimulatorUnderTest.running();
    setup = new TheTestSetup(simulator.baseUrl());
    providerApi = new TheProviderApi();
    shopsServer = new NotThroughTheBrowser(simulator.baseUrl(), providerApi);
    merchant = new TheMerchant();
    webview = TheWebview.opened();
    testRunId = "webview-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @AfterEach
  void neitherTheAppNorTheShopsServerAskedWhatOnlyATesterMayNorSawACardNumber() {
    Throwable broken = null;
    try {
      SoftAssertions.assertSoftly(this::neitherAskedWhatOnlyATesterMayNorSawACardNumber);
    } catch (AssertionError | RuntimeException guardFailed) {
      broken = guardFailed;
    }
    try {
      closeEverythingAndForgetTheRun();
    } catch (RuntimeException cleanupFailed) {
      if (broken == null) {
        throw cleanupFailed;
      }
      broken.addSuppressed(cleanupFailed);
    }
    if (broken instanceof AssertionError guardFailed) {
      throw guardFailed;
    }
    if (broken instanceof RuntimeException guardCouldNotLook) {
      throw guardCouldNotLook;
    }
  }

  private void neitherAskedWhatOnlyATesterMayNorSawACardNumber(SoftAssertions softly) {
    softly
        .assertThat(webview == null ? List.of() : webview.whatItAskedOfATesterRoute())
        .as(
            "a mobile app's webview knows only the provider's own pages, so it never asks for a"
                + " /__simulator or /ui address")
        .isEmpty();
    softly
        .assertThat(providerApi == null ? List.of() : providerApi.whatItRefusedToCall())
        .as(
            "a shop's server knows only the provider's own API, so it never calls a"
                + " /__simulator or /ui address")
        .isEmpty();
    softly
        .assertThat(cardNumbersThatLeaked())
        .as(
            "no provider answer, no callback, no page the webview rendered and no address it"
                + " visited carries a card number beyond its last four digits")
        .isEmpty();
  }

  private List<String> cardNumbersThatLeaked() {
    List<String> leaks = new ArrayList<>();
    if (providerApi != null) {
      providerApi
          .everyAnswerItGot()
          .forEach(answer -> leaks.addAll(ACardNumber.leakedIn("a provider answer", answer, held)));
    }
    if (merchant != null) {
      merchant
          .callbacksReceived()
          .forEach(callback -> leaks.addAll(ACardNumber.leakedIn("a callback", callback, held)));
      merchant
          .returnsReceived()
          .forEach(
              posted -> leaks.addAll(ACardNumber.leakedIn("a return to the shop", posted, held)));
      merchant
          .hostedFieldsAnswersReceived()
          .forEach(
              answered ->
                  leaks.addAll(
                      ACardNumber.leakedIn(
                          "an answer of the hosted fields the shop's page posted",
                          answered,
                          held)));
    }
    if (webview != null) {
      webview
          .everythingItSaw()
          .forEach(
              seen ->
                  leaks.addAll(
                      ACardNumber.leakedIn("the webview: " + seen.what(), seen.text(), held)));
    }
    return leaks.stream().distinct().toList();
  }

  private void closeEverythingAndForgetTheRun() {
    try {
      if (webview != null) {
        webview.close();
      }
    } finally {
      try {
        if (merchant != null) {
          merchant.close();
        }
      } finally {
        if (setup != null) {
          setup.forgetTheTestRunAndItsAccount(provider(), testRunId, ACCOUNT);
        }
      }
    }
  }

  void thePayerHoldsTheCard(String cardNumber) {
    held.add(cardNumber);
  }

  Page page() {
    return webview.page();
  }

  TheMerchant merchant() {
    return merchant;
  }

  NotThroughTheBrowser shopsServer() {
    return shopsServer;
  }

  TheProviderApi providerApi() {
    return providerApi;
  }

  TheTestSetup setup() {
    return setup;
  }

  String testRunId() {
    return testRunId;
  }

  String at(String path) {
    return simulator.baseUrl() + path;
  }

  List<String> everythingTheWebviewSaw() {
    List<String> seen = new ArrayList<>();
    webview.everythingItSaw().forEach(page -> seen.add(page.text()));
    return seen;
  }

  void theWebviewSawNothingGoWrong() {
    assertThat(webview.whatWentWrong())
        .as("the webview saw no console error, no uncaught exception and no 4xx or 5xx")
        .isEmpty();
  }

  void thePageDoesNotScrollSideways(String whichScreen) {
    assertThat(webview.howWideTheDocumentIs())
        .as(whichScreen + " does not scroll sideways in a phone's webview")
        .isLessThanOrEqualTo(TheBrowser.A_PHONE_IS_THIS_WIDE);
  }
}
