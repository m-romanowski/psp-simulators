package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

class TheShellOnEveryScreenTest {

  private static final String A_PIX_TAB = "Pix";

  private final TheShellOnEveryScreen shell =
      new TheShellOnEveryScreen(
          new TheRail(
              new ContributedScreens(
                  List.of(
                      new ContributedScreen("autra", "pix", A_PIX_TAB, RailGroup.DAILY_WORK, 15))),
              new ProviderRegistry(List.of(new ProviderModuleDouble("autra")))),
          new ProviderRegistry(List.of(new ProviderModuleDouble("autra"))));

  @Test
  void a_screen_is_handed_the_navigation_it_draws_rather_than_reaching_for_it() {
    ModelAndView screen = new ModelAndView("objects");
    screen.addObject(TheShellOnEveryScreen.THE_PROVIDER, "autra");
    screen.addObject(TheShellOnEveryScreen.THE_PATH, "/ui/objects");

    shell.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), this, screen);

    assertThat(theTabsOf(screen))
        .as(
            "the rail arrives as a model attribute, because the only other way for the shell to"
                + " have it is an expression that reads a bean, and a page that can read one bean"
                + " can read the one holding the database password")
        .contains("Payments", A_PIX_TAB);
    assertThat(screen.getModel())
        .containsEntry(TheShellOnEveryScreen.THE_PATH, "/ui/objects")
        .containsEntry(TheShellOnEveryScreen.THE_PROVIDERS, List.of("autra"));
  }

  @Test
  void a_page_this_simulator_answers_with_no_screen_of_its_own_still_draws_the_navigation() {
    ModelAndView theErrorPage = new ModelAndView("error");

    shell.postHandle(aRequestNaming("autra"), new MockHttpServletResponse(), this, theErrorPage);

    assertThat(theTabsOf(theErrorPage))
        .as(
            "an error page carries no provider of its own in the model, so the provider the request"
                + " named is the one whose rail it draws")
        .contains("Payments", A_PIX_TAB);
    assertThat(theErrorPage.getModel())
        .containsEntry(
            TheShellOnEveryScreen.THE_PATH,
            TheShellOnEveryScreen.THE_SCREEN_A_PAGE_NAMES_NO_PATH_FOR)
        .containsEntry(TheShellOnEveryScreen.THE_PROVIDERS, List.of("autra"));
  }

  @Test
  void the_provider_the_screen_settled_on_is_the_one_whose_navigation_it_draws() {
    ModelAndView screen = new ModelAndView("objects");
    screen.addObject(TheShellOnEveryScreen.THE_PROVIDER, "grow");

    shell.postHandle(aRequestNaming("autra"), new MockHttpServletResponse(), this, screen);

    assertThat(theTabsOf(screen))
        .as(
            "the screen decides which provider a tester is looking at, and a tab contributed for"
                + " another provider is not on this rail")
        .doesNotContain(A_PIX_TAB);
  }

  @Test
  void a_model_naming_no_provider_of_its_own_falls_back_to_the_one_the_request_named() {
    ModelAndView screen = new ModelAndView("objects");
    screen.addObject(TheShellOnEveryScreen.THE_PROVIDER, 42);

    shell.postHandle(aRequestNaming("autra"), new MockHttpServletResponse(), this, screen);

    assertThat(theTabsOf(screen)).contains(A_PIX_TAB);
  }

  @Test
  void a_submit_that_sends_the_tester_to_another_screen_is_handed_no_navigation_to_draw() {
    ModelAndView sentElsewhere = new ModelAndView("redirect:/ui/objects");

    shell.postHandle(aRequestNaming("autra"), new MockHttpServletResponse(), this, sentElsewhere);

    assertThat(sentElsewhere.getModel())
        .as(
            "a redirect writes its model into the query string of the address it sends a browser"
                + " to, and the navigation is not something a browser should carry there")
        .isEmpty();
  }

  @Test
  void a_bare_address_that_redirects_to_a_screen_carries_no_navigation_into_its_query_string() {
    ModelAndView sentElsewhere = new ModelAndView(new RedirectView("/ui"));

    shell.postHandle(aRequestNaming("autra"), new MockHttpServletResponse(), this, sentElsewhere);

    assertThat(sentElsewhere.getModel())
        .as(
            "a redirect a view controller answers with names no view of its own, and a"
                + " RedirectView writes every simple model attribute into the address it sends the"
                + " browser to: the tester would read /ui?providers=autra&path=/ui")
        .isEmpty();
  }

  @Test
  void a_screen_whose_view_is_an_object_rather_than_a_name_is_handed_its_navigation() {
    ModelAndView screen = new ModelAndView(aPageThatWritesItself());
    screen.addObject(TheShellOnEveryScreen.THE_PROVIDER, "autra");

    shell.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), this, screen);

    assertThat(theTabsOf(screen)).contains(A_PIX_TAB);
  }

  @Test
  void a_request_this_simulator_answered_with_no_page_at_all_is_left_alone() {
    assertThatCode(
            () ->
                shell.postHandle(
                    aRequestNaming("autra"), new MockHttpServletResponse(), this, null))
        .as("every route of the control API and of a provider answers a body and no view")
        .doesNotThrowAnyException();
  }

  private static View aPageThatWritesItself() {
    return (model, request, response) -> response.getWriter().write("a page of its own");
  }

  private static MockHttpServletRequest aRequestNaming(String provider) {
    MockHttpServletRequest asked = new MockHttpServletRequest();
    asked.setParameter(TheShellOnEveryScreen.THE_PROVIDER, provider);
    return asked;
  }

  private static List<String> theTabsOf(ModelAndView rendered) {
    Map<String, Object> model = rendered.getModel();
    List<String> labels = new ArrayList<>();
    Object rail = model.get(TheShellOnEveryScreen.THE_RAIL);
    if (!(rail instanceof List<?> groups)) {
      return labels;
    }
    for (Object group : groups) {
      for (RailDestination destination : ((RailGroupOnScreen) group).items()) {
        labels.add(destination.label());
      }
    }
    return labels;
  }
}
