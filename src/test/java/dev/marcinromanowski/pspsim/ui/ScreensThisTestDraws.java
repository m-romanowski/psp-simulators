package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.ui.Model;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

final class ScreensThisTestDraws {

  private ScreensThisTestDraws() {}

  static String drawn(String view, Model model, ContributedScreens contributed, String provider) {
    return theEngineTheScreensAreRenderedBy()
        .process(view, theContextOf(model, contributed, provider));
  }

  private static WebContext theContextOf(
      Model model, ContributedScreens contributed, String provider) {
    MockServletContext servletContext = new MockServletContext();
    IWebExchange exchange =
        JakartaServletWebApplication.buildApplication(servletContext)
            .buildExchange(
                new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
    WebContext context = new WebContext(exchange);
    for (Map.Entry<String, Object> attribute : model.asMap().entrySet()) {
      context.setVariable(attribute.getKey(), attribute.getValue());
    }
    context.setVariable(
        TheShellOnEveryScreen.THE_RAIL,
        new TheRail(contributed, new ProviderRegistry(List.of(new ProviderModuleDouble(provider))))
            .of(provider));
    context.setVariable(
        ATemplateReadsOnlyItsModel.THE_CONTEXT_THE_ENGINE_EVALUATES_IN,
        new ThymeleafEvaluationContext(new StaticApplicationContext(), null));
    return context;
  }

  private static SpringTemplateEngine theEngineTheScreensAreRenderedBy() {
    ClassLoaderTemplateResolver whereTheScreensAre = new ClassLoaderTemplateResolver();
    whereTheScreensAre.setPrefix("templates/");
    whereTheScreensAre.setSuffix(".html");
    whereTheScreensAre.setTemplateMode(TemplateMode.HTML);
    whereTheScreensAre.setCharacterEncoding("UTF-8");
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(whereTheScreensAre);
    TheEngineTheScreensAreRenderedBy.handItTheDialectThatHandsNoBean(engine);
    return engine;
  }
}
