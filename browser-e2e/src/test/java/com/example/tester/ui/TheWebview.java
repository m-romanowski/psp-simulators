package com.example.tester.ui;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TheWebview implements AutoCloseable {

  public record Seen(String what, String text) {}

  private final TheBrowser phone;
  private final List<String> askedOfATesterRoute = new CopyOnWriteArrayList<>();
  private final List<Seen> everythingItSaw = new CopyOnWriteArrayList<>();

  private TheWebview(TheBrowser phone) {
    this.phone = phone;
  }

  public static TheWebview opened() {
    TheWebview webview = new TheWebview(TheBrowser.openedAsAPhone());
    webview.refusesEveryTesterRoute();
    webview.remembersEverythingItSees();
    return webview;
  }

  public Page page() {
    return phone.page();
  }

  public List<String> whatWentWrong() {
    return phone.whatWentWrong();
  }

  public List<String> whatItAskedOfATesterRoute() {
    return askedOfATesterRoute.stream().distinct().toList();
  }

  public List<Seen> everythingItSaw() {
    everythingItSaw.add(new Seen("the page it showed last", whatItShowsNow()));
    return List.copyOf(everythingItSaw);
  }

  public int howWideTheDocumentIs() {
    return phone.howWideTheDocumentIs();
  }

  @Override
  public void close() {
    phone.close();
  }

  private void refusesEveryTesterRoute() {
    BrowserContext context = phone.context();
    context.onRequest(
        request -> {
          everythingItSaw.add(new Seen("an address it asked for", request.url()));
          if (ATesterRoute.isNamedBy(request.url())) {
            askedOfATesterRoute.add(request.method() + " " + request.url());
          }
        });
    context.route(
        ATesterRoute::isNamedBy,
        route -> {
          askedOfATesterRoute.add(route.request().method() + " " + route.request().url());
          route.abort("blockedbyclient");
        });
  }

  private void remembersEverythingItSees() {
    phone.context().onResponse(this::rememberADocument);
    page()
        .onFrameNavigated(
            frame -> everythingItSaw.add(new Seen("an address a frame went to", frame.url())));
    page().onLoad(loaded -> everythingItSaw.add(new Seen("a page it rendered", whatItShowsNow())));
  }

  private void rememberADocument(Response response) {
    if (!"document".equals(response.request().resourceType())) {
      return;
    }
    try {
      everythingItSaw.add(
          new Seen("a document it received", response.url() + "\n" + response.text()));
    } catch (PlaywrightException noBody) {
      everythingItSaw.add(new Seen("a document it received", response.url()));
    }
  }

  private String whatItShowsNow() {
    StringBuilder shown = new StringBuilder("the webview showed " + page().url());
    if (page().isClosed()) {
      return shown.toString();
    }
    for (Frame frame : page().frames()) {
      try {
        shown.append('\n').append(frame.url()).append('\n').append(frame.content());
      } catch (PlaywrightException detached) {
        shown.append('\n').append(frame.url()).append(" detached");
      }
    }
    return shown.toString();
  }
}
