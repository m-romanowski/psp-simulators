package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

class TheScreenAnErrorIsDrawnOnTest {

  private static final Map<String, Object> WHAT_IT_COULD_NOT_DO =
      Map.of("status", 500, "path", "/ui/x/autra/pix");

  private final TheScreenAnErrorIsDrawnOn drawnOn = new TheScreenAnErrorIsDrawnOn();

  @Test
  void a_screen_that_could_not_be_served_is_drawn_on_a_screen_of_this_simulator() {
    ModelAndView page =
        drawnOn.resolveErrorView(
            asked("/ui/x/autra/pix"), HttpStatus.INTERNAL_SERVER_ERROR, WHAT_IT_COULD_NOT_DO);

    assertThat(page).isNotNull();
    assertThat(page.getViewName()).isEqualTo(TheScreenAnErrorIsDrawnOn.THE_VIEW);
    assertThat(page.getModel()).containsEntry("path", "/ui/x/autra/pix");
  }

  @Test
  void a_refresh_of_a_screen_route_that_only_answers_a_post_is_sent_back_to_that_screen() {
    ModelAndView page =
        drawnOn.resolveErrorView(
            askedWith("/ui/objects/complete", "grow", "tester-run"),
            HttpStatus.METHOD_NOT_ALLOWED,
            WHAT_IT_COULD_NOT_DO);

    assertThat(page).isNotNull();
    assertThat(page.getView())
        .as(
            "a tester who pressed F5 after a refused form was handed a status code and an escape"
                + " link that dropped them on Choose a provider, which cost them the run they were"
                + " working in")
        .isInstanceOf(RedirectView.class);
    assertThat(((RedirectView) page.getView()).getUrl())
        .isEqualTo("/ui/objects?provider=grow&testRunId=tester-run");
  }

  @Test
  void a_screen_route_that_stopped_for_any_other_reason_is_still_drawn_the_page_that_says_so() {
    ModelAndView page =
        drawnOn.resolveErrorView(
            askedWith("/ui/x/autra/pix", "autra", "tester-run"),
            HttpStatus.INTERNAL_SERVER_ERROR,
            WHAT_IT_COULD_NOT_DO);

    assertThat(page).isNotNull();
    assertThat(page.getViewName())
        .as(
            "only a method this address does not answer is a refresh of a form: every other"
                + " failure is a thing the tester needs the simulator's own sentence about, and"
                + " redirecting it away would hide it")
        .isEqualTo(TheScreenAnErrorIsDrawnOn.THE_VIEW);
  }

  @Test
  void every_other_address_is_answered_the_way_this_application_answered_it_before() {
    assertThat(
            drawnOn.resolveErrorView(
                asked("/api/light/server/1.0/createPaymentProcess"),
                HttpStatus.METHOD_NOT_ALLOWED,
                WHAT_IT_COULD_NOT_DO))
        .as(
            "a view named error is Spring Boot's view for every error of the application, so a"
                + " template of that name hands this simulator's operator navigation to a client of"
                + " a provider and to a payer who mistyped a hosted page address, neither of whom"
                + " has any business seeing it. This resolver is what keeps the page to the screens")
        .isNull();
    assertThat(
            drawnOn.resolveErrorView(
                asked("/azericard/payer"), HttpStatus.NOT_FOUND, WHAT_IT_COULD_NOT_DO))
        .isNull();
    assertThat(
            drawnOn.resolveErrorView(asked("/ui.css"), HttpStatus.NOT_FOUND, WHAT_IT_COULD_NOT_DO))
        .isNull();
    assertThat(
            drawnOn.resolveErrorView(
                new MockHttpServletRequest(), HttpStatus.NOT_FOUND, WHAT_IT_COULD_NOT_DO))
        .as("a request that names no address of its own is no screen either")
        .isNull();
  }

  @Test
  void the_page_it_names_is_the_one_this_build_ships_and_nothing_answers_a_status_of_its_own()
      throws IOException {
    PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();

    assertThat(
            resources
                .getResource("classpath:templates/" + TheScreenAnErrorIsDrawnOn.THE_VIEW + ".html")
                .exists())
        .isTrue();
    assertThat(resources.getResources("classpath*:templates/error*"))
        .as(
            "a template named error is Boot's global error view and would be drawn for every"
                + " address of this application, which is the thing this resolver exists to undo")
        .isEmpty();
    for (Resource named : resources.getResources("classpath*:templates/error/**")) {
      assertThat(named.exists()).isFalse();
    }
    assertThat(resources.getResources("classpath*:static/error/**"))
        .as(
            "declaring an ErrorViewResolver of our own leaves Boot's convention resolver out of the"
                + " context, and that is the one that would have answered an error/404.html or an"
                + " error/5xx.html: this build has none, and this is what keeps it that way")
        .isEmpty();
  }

  private static MockHttpServletRequest askedWith(
      String address, String provider, String testRunId) {
    MockHttpServletRequest stopped = asked(address);
    stopped.setParameter(WhereARefreshLands.THE_PROVIDER, provider);
    stopped.setParameter(WhereARefreshLands.THE_RUN, testRunId);
    return stopped;
  }

  private static MockHttpServletRequest asked(String address) {
    MockHttpServletRequest stopped = new MockHttpServletRequest("GET", "/error");
    stopped.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, address);
    return stopped;
  }
}
