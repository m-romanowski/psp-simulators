package com.example.shop;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("walks-the-real-simulator")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class BothStoriesWalkedInARealBrowserTest {

  private static final int THE_SHOPS_PORT = SimulatorUnderTest.aFreePort();

  private static final String THE_SHOP = "http://127.0.0.1:" + THE_SHOPS_PORT;

  private static final String SEND_THE_EVENT = "Send payment_intent.succeeded";

  private static final String A_LINK_KEPT_FOCUSED = "look-at-its-trail";

  private static Playwright playwright;
  private static Browser browser;

  private BrowserContext context;
  private Page page;
  private final List<String> whatWentWrong = new CopyOnWriteArrayList<>();

  @DynamicPropertySource
  static void theShopTalksToTheRealSimulator(DynamicPropertyRegistry registry) {
    String simulator = SimulatorUnderTest.running().baseUrl();
    registry.add("server.port", () -> THE_SHOPS_PORT);
    registry.add("server.address", () -> "127.0.0.1");
    registry.add("shop.simulator-api-url", () -> simulator);
    registry.add("shop.simulator-browser-url", () -> simulator);
    registry.add("shop.callback-base-url", () -> THE_SHOP);
  }

  @BeforeAll
  static void openTheBrowser() {
    PlaywrightAssertions.setDefaultAssertionTimeout(15_000);
    playwright = Playwright.create();
    browser =
        playwright
            .chromium()
            .launch(
                new BrowserType.LaunchOptions().setHeadless(!Boolean.getBoolean("browser.headed")));
  }

  @AfterAll
  static void closeTheBrowser() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  @BeforeEach
  void openAWindow() {
    context = browser.newContext();
    context.setDefaultTimeout(15_000);
    page = context.newPage();
    page.onConsoleMessage(
        message -> {
          if ("error".equals(message.type()) && !message.location().contains("favicon.ico")) {
            whatWentWrong.add("the console carried an error: " + message.text());
          }
        });
    page.onPageError(uncaught -> whatWentWrong.add("the page threw: " + uncaught));
    page.onResponse(
        answer -> {
          if (answer.status() >= 400 && !answer.url().contains("favicon.ico")) {
            whatWentWrong.add(answer.status() + " answered " + answer.url());
          }
        });
  }

  @AfterEach
  void tidyUpAndCloseTheWindow() {
    page.navigate(THE_SHOP + "/");
    Page.GetByRoleOptions tidyUp = new Page.GetByRoleOptions().setName("Tidy up");
    if (page.getByRole(AriaRole.BUTTON, tidyUp).count() > 0) {
      page.getByRole(AriaRole.BUTTON, tidyUp).click();
    }
    context.close();
  }

  @Test
  @DisplayName("the decline story ends with the shop showing the capture refused as expired_card")
  void theDeclineStoryEndsWithTheCaptureRefused() {
    String testRunId = aStoryIsPlayedAndTheProductBought("A decline the test asked for");

    press("Capture 120.50 USD");

    assertThat(page.getByTestId("order-status")).hasText("Capture refused");
    assertThat(page.getByTestId("capture-refusal")).containsText("expired_card");
    assertThat(page.getByText("The story has ended.")).isVisible();

    String theRule = page.getByTestId("capture-refusal-rule").textContent();
    Page trail = theSimulatorOpens("Its trail");
    assertThat(trail)
        .hasURL(Pattern.compile(".*/ui/audit\\?provider=stripe&testRunId=" + testRunId + ".*"));
    assertThat(trail.getByRole(AriaRole.CELL, new Page.GetByRoleOptions().setName(theRule)).first())
        .isVisible();

    assertThat(whatWentWrong).isEmpty();
  }

  @Test
  @DisplayName(
      "the happy story ends captured, with Stripe's signed webhook shown in the story as it arrives")
  void theHappyStoryEndsCapturedWithTheWebhookReceived() {
    String testRunId = aStoryIsPlayedAndTheProductBought("A capture that goes through");

    press("Capture 120.50 USD");
    assertThat(page.getByTestId("order-status")).hasText("Captured");
    assertThat(page.getByText("None yet.")).isVisible();
    assertThat(page.locator("#order-live")).not().hasAttribute("hx-trigger", Pattern.compile(".*"));

    theSimulatorHoldsTheWebhookBackUntilTheClockMoves(testRunId);
    press(SEND_THE_EVENT);

    assertThat(page.getByTestId("event-awaited")).isVisible();
    assertThat(theButton(SEND_THE_EVENT)).hasCount(0);
    assertThat(page.getByTestId("story-ended")).hasCount(0);
    assertThat(page.getByText("None yet.")).isVisible();
    page.waitForResponse(
        answer ->
            answer.url().endsWith("/runs/" + testRunId + "/order/live") && answer.status() == 200,
        () -> {});

    page.locator("#" + A_LINK_KEPT_FOCUSED).focus();
    page.evaluate(
        "() => { window.theRegionAnnouncingTheEnding = document.getElementById('story-ending'); }");

    theRunsClockMovesOn(testRunId);

    assertThat(page.getByTestId("webhooks-received")).containsText("payment_intent.succeeded");
    assertThat(page.getByTestId("webhooks-received").locator("tbody tr")).hasCount(1);
    assertThat(page.getByTestId("story-ended")).isVisible();
    assertThat(page.getByTestId("event-awaited")).hasCount(0);
    assertThat(theButton(SEND_THE_EVENT)).hasCount(0);
    assertThat(page.getByTestId("order-status")).hasText("Captured");
    assertThat(page.locator("#order-live")).not().hasAttribute("hx-trigger", Pattern.compile(".*"));
    assertThat(page.locator("#story-ending").getByTestId("story-ended")).isVisible();
    assertThat(
            page.evaluate(
                "() => window.theRegionAnnouncingTheEnding !== null"
                    + " && window.theRegionAnnouncingTheEnding"
                    + " === document.getElementById('story-ending')"))
        .isEqualTo(true);
    assertThat(page.evaluate("() => document.activeElement.id")).isEqualTo(A_LINK_KEPT_FOCUSED);

    Page webhooks = theSimulatorOpens("Its webhooks");
    assertThat(webhooks)
        .hasURL(Pattern.compile(".*/ui/webhooks\\?provider=stripe&testRunId=" + testRunId + ".*"));
    Locator theDelivery =
        webhooks
            .getByRole(AriaRole.ROW)
            .filter(new Locator.FilterOptions().setHasText("payment_intent.succeeded"));
    assertThat(theDelivery).hasCount(1);
    assertThat(theDelivery.getByText("DELIVERED", new Locator.GetByTextOptions().setExact(true)))
        .isVisible();

    assertThat(whatWentWrong).isEmpty();
  }

  @Test
  @DisplayName(
      "tidying up clears the run's payments and removes its account in the simulator, and forgets"
          + " the run in the shop")
  void tidyingUpClearsTheRunsPaymentsAndRemovesItsAccount() {
    String testRunId = aStoryIsPlayedAndTheProductBought("A capture that goes through");
    assertThat(paymentIntentsTheSimulatorHoldsFor(testRunId)).hasSize(1);
    assertThat(accountsTheSimulatorHoldsFor(testRunId)).containsExactly(StoryRun.ACCOUNT);

    page.navigate(THE_SHOP + "/");
    press("Tidy up");

    assertThat(page.getByText("None. A story's run is kept here")).isVisible();
    assertThat(objectsTheSimulatorHoldsFor(testRunId)).isEqualTo("[]");
    assertThat(accountsTheSimulatorHoldsFor(testRunId)).isEmpty();
    assertThat(whatWentWrong).isEmpty();
  }

  private String aStoryIsPlayedAndTheProductBought(String story) {
    page.navigate(THE_SHOP + "/");
    press("Play " + story);
    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Espresso machine")))
        .isVisible();
    String testRunId = page.url().replaceAll(".*/runs/([^/]+)/product$", "$1");
    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Go to checkout")).click();
    press("Pay 120.50 USD");
    assertThat(page.getByTestId("order-status")).hasText("Paid, awaiting capture");
    assertThat(page.evaluate("() => typeof window.htmx")).isEqualTo("object");
    return testRunId;
  }

  private void press(String button) {
    theButton(button).click();
  }

  private Locator theButton(String button) {
    return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(button));
  }

  private Page theSimulatorOpens(String link) {
    Page opened =
        context.waitForPage(
            () -> page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(link)).click());
    opened.waitForLoadState();
    return opened;
  }

  private static String objectsTheSimulatorHoldsFor(String testRunId) {
    return theControlApi("GET", "/objects?testRunId=" + testRunId, null, 200);
  }

  private static List<String> paymentIntentsTheSimulatorHoldsFor(String testRunId) {
    List<String> held = new ArrayList<>();
    JsonParser.parseString(objectsTheSimulatorHoldsFor(testRunId))
        .getAsJsonArray()
        .forEach(
            object -> {
              String id = object.getAsJsonObject().get("providerObjectId").getAsString();
              if (id.startsWith("pi_")) {
                held.add(id);
              }
            });
    return held;
  }

  private static List<String> accountsTheSimulatorHoldsFor(String testRunId) {
    JsonElement accounts =
        JsonParser.parseString(
                theControlApi("GET", "/run-bundle?testRunId=" + testRunId, null, 200))
            .getAsJsonObject()
            .get("accounts");
    List<String> held = new ArrayList<>();
    if (accounts != null && accounts.isJsonArray()) {
      accounts
          .getAsJsonArray()
          .forEach(account -> held.add(account.getAsJsonObject().get("accountId").getAsString()));
    }
    return held;
  }

  private static void theSimulatorHoldsTheWebhookBackUntilTheClockMoves(String testRunId) {
    theControlApi(
        "POST",
        "/scenarios",
        "{\"testRunId\":\""
            + testRunId
            + "\",\"priority\":100,\"match\":{\"eventType\":\"payment_intent.succeeded\"},"
            + "\"action\":{\"type\":\"DELIVERY_DELAYED\",\"delayMs\":1000}}",
        201);
  }

  private static void theRunsClockMovesOn(String testRunId) {
    theControlApi(
        "POST",
        "/clock/advance",
        "{\"testRunId\":\"" + testRunId + "\",\"advanceBy\":\"PT1S\"}",
        200);
  }

  private static String theControlApi(String method, String path, String json, int expected) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
            URI.create(SimulatorUnderTest.running().baseUrl() + "/__simulator/stripe" + path));
    if (json == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(json));
    }
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> answer =
          client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      assertThat(answer.statusCode())
          .as(method + " " + path + ": " + answer.body())
          .isEqualTo(expected);
      return answer.body();
    } catch (IOException unreachable) {
      throw new IllegalStateException("the simulator could not be reached", unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted talking to the simulator", interrupted);
    }
  }
}
