package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class WhatAScreenCouldNotDoTest {

  private static final String A_PROVIDER_ROUTE = "/api/light/server/1.0/createPaymentProcess";

  private static final String A_SCREEN = "/ui/x/autra/pix";

  private final WhatAScreenCouldNotDo answered = new WhatAScreenCouldNotDo();

  @Test
  void a_route_a_client_application_calls_is_answered_with_nothing_of_this_simulator_s_insides() {
    Map<String, @Nullable Object> attributes =
        answered.getErrorAttributes(
            whenItStopped(A_PROVIDER_ROUTE, 500, aTemplateThatCouldNotBeRead()),
            ErrorAttributeOptions.defaults());

    assertThat(attributes)
        .as(
            "an application written against the real provider's API is pointed at this simulator"
                + " and must not notice, and no payment provider on earth answers a client with a"
                + " Java stack trace, a Java exception message or the name of a Java class")
        .doesNotContainKeys(WhatAScreenCouldNotDo.THE_CAUSES, "message", "trace", "exception");
    assertThat(attributes).containsKeys(WhatAScreenCouldNotDo.THE_STATUS, "path", "timestamp");
  }

  @Test
  void a_screen_that_could_not_be_drawn_says_what_stopped_it_and_names_the_file_and_the_line() {
    Map<String, @Nullable Object> attributes =
        answered.getErrorAttributes(
            whenItStopped(A_SCREEN, 500, aTemplateThatCouldNotBeRead()),
            ErrorAttributeOptions.defaults());

    assertThat(attributes).containsEntry("message", "An error happened during template parsing");
    assertThat(attributes.get(WhatAScreenCouldNotDo.THE_CAUSES))
        .asInstanceOf(InstanceOfAssertFactories.list(String.class))
        .as("a contributor reading this needs the line the engine stopped on, not a stack trace")
        .containsExactly(
            "IllegalStateException: An error happened during template parsing",
            "IllegalArgumentException: Exception evaluating SpringEL expression:"
                + " \"objects.theFieldIMeantToType\" (template: \"extensions/autra/pix\" - line 17,"
                + " col 23)",
            "NoSuchFieldException");
    assertThat(attributes).doesNotContainKey("trace");
  }

  @Test
  void a_refusal_this_simulator_decided_is_the_whole_story_and_carries_no_chain_of_causes() {
    Map<String, @Nullable Object> attributes =
        answered.getErrorAttributes(
            whenItStopped(
                A_SCREEN,
                404,
                new UnknownContributedScreenException("no page of this build is contributed at")),
            ErrorAttributeOptions.defaults());

    assertThat(attributes)
        .containsEntry("message", "no page of this build is contributed at")
        .doesNotContainKey(WhatAScreenCouldNotDo.THE_CAUSES);
  }

  @Test
  void an_answer_carrying_no_failure_of_its_own_is_left_as_it_is() {
    Map<String, @Nullable Object> attributes =
        answered.getErrorAttributes(
            whenItStopped(A_SCREEN, 500, null), ErrorAttributeOptions.defaults());

    assertThat(attributes).doesNotContainKey(WhatAScreenCouldNotDo.THE_CAUSES);
  }

  @Test
  void a_page_that_could_not_be_served_names_the_run_a_tester_was_in_and_where_it_is_read() {
    Map<String, @Nullable Object> attributes =
        answered.getErrorAttributes(
            whenItWasAskedWith(A_SCREEN, "autra", "tester-run", 500),
            ErrorAttributeOptions.defaults());

    assertThat(attributes)
        .as(
            "the page offered one escape, Back to the test run you were looking at, and sent it to"
                + " /ui?provider=&testRunId=: it had the run in the address it was asked at and"
                + " read it from nowhere")
        .containsEntry("provider", "autra")
        .containsEntry("testRunId", "tester-run")
        .containsEntry(
            WhatAScreenCouldNotDo.WHERE_A_REFRESH_WOULD_HAVE_GONE,
            "/ui?provider=autra&testRunId=tester-run");
  }

  @Test
  void a_page_asked_at_an_address_naming_no_run_offers_the_screen_and_invents_no_run() {
    Map<String, @Nullable Object> attributes =
        answered.getErrorAttributes(
            whenItStopped(A_SCREEN, 500, aTemplateThatCouldNotBeRead()),
            ErrorAttributeOptions.defaults());

    assertThat(attributes)
        .containsEntry("provider", null)
        .containsEntry(WhatAScreenCouldNotDo.WHERE_A_REFRESH_WOULD_HAVE_GONE, "/ui");
  }

  @Test
  void the_engine_reading_a_template_is_explained_only_where_a_cause_names_one() {
    assertThat(
            answered.getErrorAttributes(
                whenItStopped(A_SCREEN, 500, aTemplateThatCouldNotBeRead()),
                ErrorAttributeOptions.defaults()))
        .containsEntry(WhatAScreenCouldNotDo.A_TEMPLATE_NAMES_THE_LINE_IT_STOPPED_ON, true);
    assertThat(
            answered.getErrorAttributes(
                whenItStopped(A_SCREEN, 500, new IllegalStateException("the pool is closed")),
                ErrorAttributeOptions.defaults()))
        .as(
            "a reader handed an explanation of the template engine under a failure that had"
                + " nothing to do with a template learns to skip explanations, and the next one"
                + " they skip is the one they needed")
        .containsEntry(WhatAScreenCouldNotDo.A_TEMPLATE_NAMES_THE_LINE_IT_STOPPED_ON, false);
    assertThat(
            answered.getErrorAttributes(
                whenItWasAskedWith(A_SCREEN, "autra", "tester-run", 405),
                ErrorAttributeOptions.defaults()))
        .doesNotContainKey(WhatAScreenCouldNotDo.A_TEMPLATE_NAMES_THE_LINE_IT_STOPPED_ON);
  }

  @Test
  void a_cause_is_read_for_the_file_and_the_line_it_names_and_not_for_the_word_template() {
    assertThat(WhatAScreenCouldNotDo.aCauseNamesATemplate(List.of())).isFalse();
    assertThat(
            WhatAScreenCouldNotDo.aCauseNamesATemplate(
                List.of("IllegalStateException: the template was fine, the database was not")))
        .isFalse();
    assertThat(
            WhatAScreenCouldNotDo.aCauseNamesATemplate(
                List.of("one", "two (template: \"extensions/autra/pix\" - line 17, col 23)")))
        .isTrue();
  }

  @Test
  void an_address_outside_the_screens_is_never_read_as_one_of_them() {
    assertThat(WhatAScreenCouldNotDo.aScreenWasAskedFor("/ui")).isTrue();
    assertThat(WhatAScreenCouldNotDo.aScreenWasAskedFor("/ui/x/autra/pix")).isTrue();
    assertThat(WhatAScreenCouldNotDo.aScreenWasAskedFor("/__simulator/grow/reset")).isFalse();
    assertThat(WhatAScreenCouldNotDo.aScreenWasAskedFor(A_PROVIDER_ROUTE)).isFalse();
    assertThat(WhatAScreenCouldNotDo.aScreenWasAskedFor("/ui.css/nope"))
        .as(
            "a screen of this simulator is /ui itself or something under it, and not a name that"
                + " happens to begin with those three characters")
        .isFalse();
    assertThat(WhatAScreenCouldNotDo.aScreenWasAskedFor("/uifornia/payer")).isFalse();
    assertThat(WhatAScreenCouldNotDo.aScreenWasAskedFor(null)).isFalse();
    assertThat(WhatAScreenCouldNotDo.aScreenWasAskedFor(500)).isFalse();
  }

  @Test
  void the_answers_this_simulator_decided_are_told_apart_from_the_ones_nothing_did() {
    assertThat(WhatAScreenCouldNotDo.nothingDecidedThisAnswer(Map.of("status", 500))).isTrue();
    assertThat(WhatAScreenCouldNotDo.nothingDecidedThisAnswer(Map.of("status", 503))).isTrue();
    assertThat(WhatAScreenCouldNotDo.nothingDecidedThisAnswer(Map.of("status", 499))).isFalse();
    assertThat(WhatAScreenCouldNotDo.nothingDecidedThisAnswer(Map.of("status", "500"))).isFalse();
    assertThat(WhatAScreenCouldNotDo.nothingDecidedThisAnswer(Map.of())).isFalse();
  }

  private static Throwable aTemplateThatCouldNotBeRead() {
    return new IllegalStateException(
        "An error happened during template parsing",
        new IllegalArgumentException(
            "Exception evaluating SpringEL expression: \"objects.theFieldIMeantToType\" (template:"
                + " \"extensions/autra/pix\" - line 17, col 23)",
            new NoSuchFieldException()));
  }

  private static WebRequest whenItWasAskedWith(
      String address, String provider, String testRunId, int status) {
    MockHttpServletRequest stopped = new MockHttpServletRequest("GET", "/error");
    stopped.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, address);
    stopped.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
    stopped.setParameter(WhereARefreshLands.THE_PROVIDER, provider);
    stopped.setParameter(WhereARefreshLands.THE_RUN, testRunId);
    return new ServletWebRequest(stopped);
  }

  private static WebRequest whenItStopped(String address, int status, @Nullable Throwable error) {
    MockHttpServletRequest stopped = new MockHttpServletRequest("GET", "/error");
    stopped.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, address);
    stopped.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
    if (error != null) {
      stopped.setAttribute(RequestDispatcher.ERROR_EXCEPTION, error);
    }
    return new ServletWebRequest(stopped);
  }
}
