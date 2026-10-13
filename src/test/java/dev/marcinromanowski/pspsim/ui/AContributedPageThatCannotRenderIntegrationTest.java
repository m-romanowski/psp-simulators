package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

class AContributedPageThatCannotRenderIntegrationTest extends PostgresBackedTest {

  private static final String THE_PAGE_THIS_BUILD_SERVES = "extensions/autra/pix.html";

  private static final String THE_EXPRESSION_NO_MODEL_ANSWERS =
      "<p th:text=\"${objects.theFieldIMeantToType}\"></p>";

  private static final int PROSE_ENOUGH_TO_FILL_A_SERVLET_BUFFER = 400;

  private static final String THE_MARKUP = aPageThatCannotRender();

  private static final Path THE_TEMPLATES_THIS_CONTEXT_READS =
      TemplatesOneTestReadsFrom.everyTemplateOfThisBuildWith(
          "a-contributed-page-that-cannot-render", THE_PAGE_THIS_BUILD_SERVES, THE_MARKUP);

  @DynamicPropertySource
  static void theTemplatesAreReadFromTheCopy(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.thymeleaf.prefix",
        () -> TemplatesOneTestReadsFrom.asAPrefixForThymeleaf(THE_TEMPLATES_THIS_CONTEXT_READS));
  }

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void aClientOfThisSimulator() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void a_typo_in_a_contributed_page_hands_the_tester_a_page_and_never_a_blank_one() {
    ResponseEntity<String> answered =
        http.get()
            .uri("/ui/x/autra/pix?provider=autra")
            .accept(MediaType.TEXT_HTML)
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);

    assertThat(answered.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(answered.getBody())
        .as(
            "the screen is contributed and served, and its own file cannot be read: this answered"
                + " 500 with a zero-byte body until the view stopped writing its output to the"
                + " response as it went, which left a committed response and nowhere to put the"
                + " error page. This page is longer than the buffer a servlet writes through, so"
                + " what comes before the typo has already been sent when the typo is reached,"
                + " which is the case that produced the blank page")
        .isNotNull()
        .isNotBlank()
        .contains("This request was not served")
        .contains("What this simulator was doing when it stopped")
        .contains("line " + theLineTheTypoIsOn())
        .contains("theFieldIMeantToType")
        .contains("cannot be found");
    assertThat(answered.getBody())
        .as("a tester who lands here is still inside the shell they were using")
        .contains("Skip the navigation")
        .contains("Payments");
    assertThat(answered.getBody())
        .as(
            "a cause above names a template and a line in it, so the paragraph that explains what"
                + " reads that file is explaining this page and not printed beside everything")
        .contains("A screen contributed as one html file under");
  }

  private static int theLineTheTypoIsOn() {
    String before = THE_MARKUP.substring(0, THE_MARKUP.indexOf(THE_EXPRESSION_NO_MODEL_ANSWERS));
    return 1 + (int) before.chars().filter(character -> character == '\n').count();
  }

  private static String aPageThatCannotRender() {
    StringBuilder markup = new StringBuilder(64000);
    markup.append("<!DOCTYPE html>\n");
    markup.append("<html xmlns:th=\"http://www.thymeleaf.org\">\n");
    markup.append("  <head th:replace=\"~{fragments :: head('Pix')}\"></head>\n");
    markup.append("  <body>\n");
    markup.append("    <div th:replace=\"~{fragments :: bar}\"></div>\n");
    markup.append("    <main>\n");
    for (int line = 0; line < PROSE_ENOUGH_TO_FILL_A_SERVLET_BUFFER; line++) {
      markup.append("      <p class=\"note wrap\">A Pix is paid by the payer copying a payload");
      markup.append(" into their own bank app, and this sentence is here to fill the buffer a");
      markup.append(
          " servlet writes through, so that what follows it has already been sent.</p>\n");
    }
    markup.append("      ").append(THE_EXPRESSION_NO_MODEL_ANSWERS).append('\n');
    markup.append("    </main>\n");
    markup.append("  </body>\n");
    markup.append("</html>\n");
    return markup.toString();
  }
}
