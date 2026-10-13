package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EveryContributedScreenObeysTheCoreTest extends AJourneyInARealBrowser {

  private static final String THE_PROVIDERS_ON_OFFER =
      """
      () => [...document.querySelectorAll('#provider option')]
        .map((option) => option.value)
        .filter((value) => value !== '')
      """;

  private static final String THE_CONTRIBUTED_TABS =
      """
      () => [...document.querySelectorAll('nav.rail a')]
        .filter((tab) => new URL(tab.href, location.href).pathname.startsWith('/ui/x/'))
        .map((tab) => tab.textContent.trim())
      """;

  private static final String THE_TAB_THIS_SCREEN_MARKS_AS_ITS_OWN =
      """
      () => {
        const current = document.querySelector('nav.rail a[aria-current="page"]');
        return current == null ? '' : current.textContent.trim();
      }
      """;

  @Test
  @DisplayName("every screen a provider contributed is served, labelled, and prints no card number")
  void everyContributedScreenIsHeldToWhatTheCoreMayInsistOn() {
    List<String> complaints = new ArrayList<>();
    List<String> read = new ArrayList<>();

    for (String provider : theProvidersThisSimulatorCarries()) {
      for (String tab : theContributedTabsOn(provider)) {
        read.add(provider + " offers the tab " + tab);
        complaints.addAll(whatThatScreenMayNotDo(provider, tab));
      }
    }

    assertThat(read)
        .as(
            "this test is driven by what the navigation offers rather than by a list written here,"
                + " because the list is the thing being extended; a build that contributes no"
                + " screen at all proves nothing about what a contributed screen is held to")
        .isNotEmpty();
    assertThat(complaints).isEmpty();
    theBrowserSawNothingGoWrong();
  }

  private List<String> whatThatScreenMayNotDo(String provider, String tab) {
    List<String> complaints = new ArrayList<>();
    theRailOf(provider);
    page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(tab).setExact(true))
        .click();
    String where = "the contributed screen " + provider + "/" + tab;
    String markup = page().content();
    complaints.addAll(ACardNumber.leakedIn(where, page().innerText("body"), Set.of()));
    complaints.addAll(ACardNumber.leakedIn(where + " as it is delivered", markup, Set.of()));
    if (markup.contains("<!--")) {
      complaints.add(where + " reaches a browser carrying an HTML comment");
    }
    String current = String.valueOf(page().evaluate(THE_TAB_THIS_SCREEN_MARKS_AS_ITS_OWN));
    if (!tab.equals(current)) {
      complaints.add(
          where + " marks \"" + current + "\" as the tab a reader is on, and not its own");
    }
    if (page().locator("nav.rail").count() != 1) {
      complaints.add(
          where + " is served without the navigation every screen of this simulator has");
    }
    browser().asAPhoneWouldShowIt();
    if (browser().howWideTheDocumentIs() > browser().howWideTheViewportIs()) {
      complaints.add(
          where
              + " pushes the page sideways at "
              + TheBrowser.A_PHONE_IS_THIS_WIDE
              + "px: the document is "
              + browser().howWideTheDocumentIs()
              + "px wide");
    }
    browser().asADeskWouldShowIt();
    return complaints;
  }

  private List<String> theProvidersThisSimulatorCarries() {
    theRailOf(GROW);
    return asNames(page().evaluate(THE_PROVIDERS_ON_OFFER));
  }

  private List<String> theContributedTabsOn(String provider) {
    theRailOf(provider);
    return asNames(page().evaluate(THE_CONTRIBUTED_TABS));
  }

  private void theRailOf(String provider) {
    page().navigate(at("/ui/objects?provider=" + provider + "&testRunId=" + testRunId()));
  }

  private static List<String> asNames(Object evaluated) {
    List<String> names = new ArrayList<>();
    for (Object each : (List<?>) evaluated) {
      names.add(String.valueOf(each));
    }
    return List.copyOf(names);
  }
}
