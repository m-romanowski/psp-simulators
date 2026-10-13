package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class NoTemplateReachesTheApplicationTest {

  private static final String EVERY_TEMPLATE = "classpath*:templates/**/*.html";

  private static final String THE_SHELL_EVERY_SCREEN_DRAWS = "fragments.html";

  private final PathMatchingResourcePatternResolver resources =
      new PathMatchingResourcePatternResolver();

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void no_page_of_this_build_reaches_a_bean_of_this_application() throws IOException {
    Map<String, List<String>> reaching = new TreeMap<>();
    Resource[] templates = resources.getResources(EVERY_TEMPLATE);
    for (Resource template : templates) {
      List<String> beans = ATemplateReadsOnlyItsModel.theBeansItReaches(read(template));
      if (!beans.isEmpty()) {
        reaching.put(String.valueOf(template.getFilename()), beans);
      }
    }

    assertThat(templates)
        .as("nothing on the classpath matches " + EVERY_TEMPLATE + ", so this check read no page")
        .isNotEmpty();
    assertThat(reaching)
        .as(
            "a template expression naming a bean reads any bean of this application, and a screen"
                + " is contributed by dropping one html file under templates/extensions: the page"
                + " that reads the rail off a bean is the page that reads the datasource password"
                + " off one. What a page needs, the screen that renders it puts in the model. The"
                + " engine of this simulator refuses the reach at render time as well, and this"
                + " check is what stops the expression being written")
        .isEmpty();
  }

  @Test
  void the_shell_every_screen_draws_takes_the_navigation_from_the_model_it_was_handed()
      throws IOException {
    String shell =
        read(resources.getResource("classpath:templates/" + THE_SHELL_EVERY_SCREEN_DRAWS));

    assertThat(shell)
        .as(
            "every screen of this simulator draws this one shell, contributed ones included, so"
                + " this is the fragment whose navigation decides whether a page has to reach for"
                + " anything at all")
        .contains("th:each=\"group : ${" + TheShellOnEveryScreen.THE_RAIL + "}\"");
  }

  private static String read(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
