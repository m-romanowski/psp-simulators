package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class InspectorOffersNoWriteIntegrationTest extends PostgresBackedTest {

  private static final String THE_SCREEN = "/ui/idempotency";

  private static final String THE_CONTROL_READ = "/__simulator/{provider}/idempotency";

  private static final String A_TESTER_COULD_REWRITE_AN_ESTABLISHED_REPLAY =
      "these mappings answer the retry and idempotency inspector on something other than a GET, so"
          + " a tester could send one and change the response this simulator has already"
          + " established under a key. The published requirement is that a tester may force the"
          + " next response only by configuring a scenario for a future or new request, and that"
          + " the inspector must not allow changing the response of an already established true"
          + " idempotent replay";

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  @Test
  void every_mapping_the_inspector_screen_answers_on_admits_a_read_and_nothing_else() {
    assertThat(theMethodsAdmittedBy(THE_SCREEN))
        .as(A_TESTER_COULD_REWRITE_AN_ESTABLISHED_REPLAY)
        .isNotEmpty()
        .containsOnly(RequestMethod.GET);
  }

  @Test
  void every_mapping_the_inspector_control_read_answers_on_admits_a_read_and_nothing_else() {
    assertThat(theMethodsAdmittedBy(THE_CONTROL_READ))
        .as(A_TESTER_COULD_REWRITE_AN_ESTABLISHED_REPLAY)
        .isNotEmpty()
        .containsOnly(RequestMethod.GET);
  }

  @Test
  void a_mapping_that_named_no_method_at_all_would_be_read_here_as_admitting_every_one_of_them() {
    assertThat(theMappingsThatNameNoMethodOf(THE_SCREEN))
        .as(A_TESTER_COULD_REWRITE_AN_ESTABLISHED_REPLAY)
        .isEmpty();
    assertThat(theMappingsThatNameNoMethodOf(THE_CONTROL_READ))
        .as(A_TESTER_COULD_REWRITE_AN_ESTABLISHED_REPLAY)
        .isEmpty();
  }

  private List<RequestMethod> theMethodsAdmittedBy(String path) {
    List<RequestMethod> admitted = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> mapped :
        mappings.getHandlerMethods().entrySet()) {
      if (answersOn(mapped.getKey(), path)) {
        admitted.addAll(mapped.getKey().getMethodsCondition().getMethods());
      }
    }
    return admitted;
  }

  private List<String> theMappingsThatNameNoMethodOf(String path) {
    List<String> silent = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> mapped :
        mappings.getHandlerMethods().entrySet()) {
      if (answersOn(mapped.getKey(), path)
          && mapped.getKey().getMethodsCondition().getMethods().isEmpty()) {
        silent.add(String.valueOf(mapped.getValue()));
      }
    }
    return silent;
  }

  private static boolean answersOn(RequestMappingInfo mapping, String path) {
    Set<String> patterns = mapping.getPatternValues();
    for (String pattern : patterns) {
      if (pattern.equals(path)) {
        return true;
      }
    }
    return false;
  }
}
