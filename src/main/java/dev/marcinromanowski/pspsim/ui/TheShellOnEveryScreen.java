package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.SmartView;

@Component
public final class TheShellOnEveryScreen implements HandlerInterceptor {

  public static final String THE_RAIL = "rail";

  public static final String THE_BOX_A_TEST_RUN_IS_TYPED_INTO = "test run";

  static final String THE_PROVIDER = "provider";

  static final String THE_PROVIDERS = "providers";

  static final String THE_PATH = "path";

  static final String THE_SCREEN_A_PAGE_NAMES_NO_PATH_FOR = "/ui";

  private static final String IT_SENDS_THE_TESTER_ELSEWHERE = "redirect:";

  private final TheRail rail;

  private final ProviderRegistry providers;

  public TheShellOnEveryScreen(TheRail rail, ProviderRegistry providers) {
    this.rail = rail;
    this.providers = providers;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      @Nullable ModelAndView modelAndView) {
    if (modelAndView == null || sendsTheTesterToAnotherScreen(modelAndView)) {
      return;
    }
    hand(modelAndView.getModel(), request.getParameter(THE_PROVIDER));
  }

  void hand(Map<String, Object> model, @Nullable String theProviderTheRequestNamed) {
    model.put(THE_RAIL, rail.of(theProviderTheScreenSettledOn(model, theProviderTheRequestNamed)));
    model.putIfAbsent(THE_PROVIDERS, providers.names());
    model.putIfAbsent(THE_PATH, THE_SCREEN_A_PAGE_NAMES_NO_PATH_FOR);
  }

  private static @Nullable String theProviderTheScreenSettledOn(
      Map<String, Object> model, @Nullable String theProviderTheRequestNamed) {
    if (model.get(THE_PROVIDER) instanceof String named) {
      return named;
    }
    return theProviderTheRequestNamed;
  }

  static boolean sendsTheTesterToAnotherScreen(ModelAndView modelAndView) {
    String named = modelAndView.getViewName();
    if (named != null) {
      return named.startsWith(IT_SENDS_THE_TESTER_ELSEWHERE);
    }
    return modelAndView.getView() instanceof SmartView sent && sent.isRedirectView();
  }
}
