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

class AContributedPageThatReachesForABeanIntegrationTest extends PostgresBackedTest {

  private static final String A_PROPERTY_ONLY_THE_APPLICATION_HOLDS =
      "a-property-standing-in-for-the-datasource-password";

  private static final String WHAT_IT_HOLDS_HERE = "a-value-no-contributed-page-may-print";

  private static final String THE_PAGE_THIS_BUILD_SERVES = "extensions/autra/pix.html";

  private static final String THE_MARKUP =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
        <head th:replace="~{fragments :: head('Pix')}"></head>
        <body>
          <div th:replace="~{fragments :: bar}"></div>
          <main>
            <p th:text="${@environment.getProperty('%s')}"></p>
          </main>
        </body>
      </html>
      """
          .formatted(A_PROPERTY_ONLY_THE_APPLICATION_HOLDS);

  private static final Path THE_TEMPLATES_THIS_CONTEXT_READS =
      TemplatesOneTestReadsFrom.everyTemplateOfThisBuildWith(
          "a-contributed-page-that-reaches-for-a-bean", THE_PAGE_THIS_BUILD_SERVES, THE_MARKUP);

  @DynamicPropertySource
  static void theTemplatesAreReadFromTheCopy(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.thymeleaf.prefix",
        () -> TemplatesOneTestReadsFrom.asAPrefixForThymeleaf(THE_TEMPLATES_THIS_CONTEXT_READS));
    registry.add(A_PROPERTY_ONLY_THE_APPLICATION_HOLDS, () -> WHAT_IT_HOLDS_HERE);
  }

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void aTesterWithABrowser() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  void a_page_edited_into_reaching_a_bean_prints_nothing_of_the_application_it_asked() {
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
            "this is the attack the boundary exists to stop, driven through the engine Spring Boot"
                + " built for this application: one contributed html file asking the Environment"
                + " bean for a property, which is how the password of the database was printed on a"
                + " screen a tester opened. A file already on the classpath is refused at startup,"
                + " and a file edited while the simulator runs is refused here, by the engine")
        .doesNotContain(WHAT_IT_HOLDS_HERE)
        .contains("asked the application for the bean")
        .contains("environment")
        .contains("reads the model its screen handed it and nothing else");
  }
}
