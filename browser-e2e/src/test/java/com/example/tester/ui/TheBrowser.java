package com.example.tester.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TheBrowser implements AutoCloseable {

  public static final int A_PHONE_IS_THIS_WIDE = 390;

  public static final int A_PHONE_IS_THIS_TALL = 844;

  private static final int A_DESK_IS_THIS_WIDE = 1280;

  private static final int A_DESK_IS_THIS_TALL = 900;

  private static final double EVERY_STEP_WAITS_THIS_LONG = 15_000;

  private static final double A_PAGE_ARRIVES_WITHIN_THIS = 30_000;

  private static final String THE_ICON_NO_SCREEN_ASKS_FOR = "/favicon.ico";

  private static Playwright playwright;

  private static Browser browser;

  private final BrowserContext context;
  private final Page page;
  private final List<String> complaints = new CopyOnWriteArrayList<>();

  private TheBrowser(BrowserContext context, Page page) {
    this.context = context;
    this.page = page;
  }

  public static TheBrowser opened() {
    Browser running = theBrowserThisSuiteDrives();
    BrowserContext context =
        running.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(A_DESK_IS_THIS_WIDE, A_DESK_IS_THIS_TALL));
    context.setDefaultTimeout(EVERY_STEP_WAITS_THIS_LONG);
    context.setDefaultNavigationTimeout(A_PAGE_ARRIVES_WITHIN_THIS);
    Page page = context.newPage();
    TheBrowser opened = new TheBrowser(context, page);
    opened.watchWhatThePageDoes();
    return opened;
  }

  public static TheBrowser openedAsAPhone() {
    Browser running = theBrowserThisSuiteDrives();
    BrowserContext context =
        running.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(A_PHONE_IS_THIS_WIDE, A_PHONE_IS_THIS_TALL)
                .setDeviceScaleFactor(3)
                .setIsMobile(true)
                .setHasTouch(true));
    context.setDefaultTimeout(EVERY_STEP_WAITS_THIS_LONG);
    context.setDefaultNavigationTimeout(A_PAGE_ARRIVES_WITHIN_THIS);
    Page page = context.newPage();
    TheBrowser opened = new TheBrowser(context, page);
    opened.watchWhatThePageDoes();
    return opened;
  }

  public Page page() {
    return page;
  }

  public BrowserContext context() {
    return context;
  }

  public void asAPhoneWouldShowIt() {
    page.setViewportSize(A_PHONE_IS_THIS_WIDE, A_PHONE_IS_THIS_TALL);
  }

  public void asADeskWouldShowIt() {
    page.setViewportSize(A_DESK_IS_THIS_WIDE, A_DESK_IS_THIS_TALL);
  }

  public int howWideTheDocumentIs() {
    return ((Number) page.evaluate("() => Math.ceil(document.documentElement.scrollWidth)"))
        .intValue();
  }

  public int howWideTheViewportIs() {
    return ((Number) page.evaluate("() => window.innerWidth")).intValue();
  }

  public List<String> whatWentWrong() {
    return List.copyOf(complaints);
  }

  @Override
  public void close() {
    context.close();
  }

  private void watchWhatThePageDoes() {
    page.onConsoleMessage(this::recordAConsoleError);
    page.onPageError(uncaught -> complaints.add("the page threw: " + uncaught));
    page.onResponse(this::recordAnAnswerNoScreenShouldGive);
    page.onRequestFailed(
        failed ->
            complaints.add(
                "the browser could not fetch " + failed.url() + ": " + failed.failure()));
  }

  private void recordAConsoleError(ConsoleMessage message) {
    if (!"error".equals(message.type()) || itIsTheIconNoScreenAsksFor(message.location())) {
      return;
    }
    complaints.add("the console carried an error: " + message.text());
  }

  private void recordAnAnswerNoScreenShouldGive(Response answer) {
    if (answer.status() < 400 || itIsTheIconNoScreenAsksFor(answer.url())) {
      return;
    }
    complaints.add(answer.status() + " answered " + answer.request().method() + " " + answer.url());
  }

  private static boolean itIsTheIconNoScreenAsksFor(String where) {
    return where != null && where.contains(THE_ICON_NO_SCREEN_ASKS_FOR);
  }

  private static synchronized Browser theBrowserThisSuiteDrives() {
    if (browser == null) {
      playwright = Playwright.create();
      browser =
          playwright
              .chromium()
              .launch(
                  new BrowserType.LaunchOptions()
                      .setHeadless(!Boolean.getBoolean("browser.headed"))
                      .setSlowMo(Double.parseDouble(System.getProperty("browser.slowMo", "0"))));
      Runtime.getRuntime().addShutdownHook(new Thread(TheBrowser::shutDown, "close-browser"));
    }
    return browser;
  }

  private static void shutDown() {
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }
}
