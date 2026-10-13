package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ATesterReadsThePublishedApiTest extends AJourneyInARealBrowser {

  private static final Pattern THE_API_PAGE = Pattern.compile("/openapi/index\\.html(\\?.*)?$");

  private static final String THE_INDEX_THE_PAGE_READS =
      "() => fetch('index.json').then(answered => answered.json())";

  private static final String WHAT_A_DOCUMENT_PUBLISHES =
      "url => fetch(url).then(answered => answered.json())"
          + ".then(api => ({ title: api.info.title, paths: Object.keys(api.paths),"
          + " operations: Object.entries(api.paths).flatMap(([path, methods]) =>"
          + " Object.keys(methods).map(method => method.toUpperCase() + ' ' + path)) }))";

  private static final String EVERY_OPERATION_THE_PAGE_LISTS =
      "found => [...new Set(found.map(summary =>"
          + " summary.querySelector('.opblock-summary-method').textContent.trim()"
          + " + ' ' + summary.querySelector('.opblock-summary-path').dataset.path))]";

  private static final String THE_PAGE_LISTS_EXACTLY_THESE_OPERATIONS =
      "expected => {"
          + " const found = new Set([...document.querySelectorAll('.opblock-summary')].map("
          + " summary => summary.querySelector('.opblock-summary-method').textContent.trim()"
          + " + ' ' + summary.querySelector('.opblock-summary-path').dataset.path));"
          + " const wanted = new Set(expected);"
          + " return found.size === wanted.size"
          + " && [...wanted].every(operation => found.has(operation)); }";

  private record Published(
      String name, String url, String title, List<String> paths, List<String> operations) {}

  @Test
  @DisplayName(
      "a tester follows the API link from a screen, reads a definition's operations and switches"
          + " between every published document")
  void aTesterFollowsTheApiLinkAndReadsEveryPublishedDocument() {
    page().navigate(at("/ui?provider=" + GROW + "&testRunId=" + testRunId()));
    page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("API").setExact(true))
        .click();
    PlaywrightAssertions.assertThat(page()).hasURL(THE_API_PAGE);

    List<Published> published = everyDocumentTheIndexLists();
    assertThat(published)
        .as("the page exists to choose among several published documents")
        .hasSizeGreaterThanOrEqualTo(2);

    theDefinitionIsShownWithItsOperations(published.getFirst());

    Locator theChooser = page().getByLabel("Select a definition");
    PlaywrightAssertions.assertThat(theChooser).isVisible();
    PlaywrightAssertions.assertThat(theChooser.locator("option")).hasCount(published.size());
    assertThat(theChooser.locator("option").allInnerTexts())
        .as("the chooser offers every document index.json lists, in its order")
        .containsExactlyElementsOf(published.stream().map(Published::name).toList());

    for (Published document : published.reversed()) {
      theChooser.selectOption(new SelectOption().setLabel(document.name()));
      theDefinitionIsShownWithItsOperations(document);
    }

    thePageDoesNotScrollSidewaysAtPhoneWidth("the API page");
    theBrowserSawNothingGoWrong();
  }

  private void theDefinitionIsShownWithItsOperations(Published document) {
    PlaywrightAssertions.assertThat(page().getByText("No API definition provided")).hasCount(0);
    PlaywrightAssertions.assertThat(page().locator(".swagger-ui .info h1.title"))
        .containsText(document.title());
    for (String path : document.paths()) {
      PlaywrightAssertions.assertThat(
              page().locator(".opblock-summary-path[data-path=\"" + path + "\"]").first())
          .isVisible();
    }
    try {
      page().waitForFunction(THE_PAGE_LISTS_EXACTLY_THESE_OPERATIONS, document.operations());
    } catch (TimeoutError stillRendering) {
    }
    assertThat(everyOperationThePageLists())
        .as(
            document.name()
                + " lists the method and path of every operation it publishes, and no other")
        .containsExactlyInAnyOrderElementsOf(document.operations());
  }

  private List<String> everyOperationThePageLists() {
    List<String> operations = new ArrayList<>();
    for (Object operation :
        (List<?>) page().locator(".opblock-summary").evaluateAll(EVERY_OPERATION_THE_PAGE_LISTS)) {
      operations.add(String.valueOf(operation));
    }
    return operations;
  }

  private List<Published> everyDocumentTheIndexLists() {
    List<Published> documents = new ArrayList<>();
    for (Object entry : (List<?>) page().evaluate(THE_INDEX_THE_PAGE_READS)) {
      Map<?, ?> listed = (Map<?, ?>) entry;
      String url = String.valueOf(listed.get("url"));
      Map<?, ?> api = (Map<?, ?>) page().evaluate(WHAT_A_DOCUMENT_PUBLISHES, url);
      List<String> paths = new ArrayList<>();
      for (Object path : (List<?>) api.get("paths")) {
        paths.add(String.valueOf(path));
      }
      List<String> operations = new ArrayList<>();
      for (Object operation : (List<?>) api.get("operations")) {
        operations.add(String.valueOf(operation));
      }
      documents.add(
          new Published(
              String.valueOf(listed.get("name")),
              url,
              String.valueOf(api.get("title")),
              paths,
              operations));
    }
    return documents;
  }
}
