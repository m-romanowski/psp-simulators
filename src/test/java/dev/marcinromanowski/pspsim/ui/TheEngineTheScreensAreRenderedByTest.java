package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class TheEngineTheScreensAreRenderedByTest {

  private static final String THE_SECRET = "the-password-no-page-may-print";

  private static final String READING_THE_PASSWORD =
      "<p th:text=\"${@environment.getProperty('spring.datasource.password')}\"></p>";

  private final TheEngineTheScreensAreRenderedBy handedOver =
      new TheEngineTheScreensAreRenderedBy();

  @Test
  void the_engine_spring_boot_built_is_handed_the_dialect_that_hands_no_bean() {
    SpringTemplateEngine asBootBuiltIt = asBootBuildsIt();

    Object same = handedOver.postProcessAfterInitialization(asBootBuiltIt, "templateEngine");

    assertThat(same).isSameAs(asBootBuiltIt);
    assertThatCode(() -> asBootBuiltIt.process(READING_THE_PASSWORD, aPage()))
        .as(
            "the engine is Spring Boot's, so this build inherits whatever Boot sets on it, and the"
                + " one thing it does not get to keep is a page's reach into the application")
        .hasStackTraceContaining("this page asked the application for the bean \"environment\"");
    assertThat(asBootBuiltIt.process("<p th:text=\"${provider}\"></p>", aPageNaming("autra")))
        .isEqualTo("<p>autra</p>");
  }

  @Test
  void what_boot_set_on_the_dialect_it_built_survives_the_swap() {
    SpringTemplateEngine asBootBuiltIt = asBootBuildsIt();
    asBootBuiltIt.setEnableSpringELCompiler(true);
    asBootBuiltIt.setRenderHiddenMarkersBeforeCheckboxes(true);

    handedOver.postProcessAfterInitialization(asBootBuiltIt, "templateEngine");

    assertThat(asBootBuiltIt.getEnableSpringELCompiler())
        .as(
            "spring.thymeleaf.enable-spring-el-compiler and"
                + " spring.thymeleaf.render-hidden-markers-before-checkboxes are set by Boot on the"
                + " dialect instance, not on the engine, so a dialect swapped in afterwards would"
                + " silently drop both")
        .isTrue();
    assertThat(asBootBuiltIt.getRenderHiddenMarkersBeforeCheckboxes()).isTrue();
  }

  @Test
  void a_bean_that_is_no_template_engine_is_handed_back_untouched() {
    String anythingElse = "a bean of some other kind";

    assertThat(handedOver.postProcessAfterInitialization(anythingElse, "somethingElse"))
        .isSameAs(anythingElse);
  }

  @Test
  void an_engine_carrying_no_spring_standard_dialect_is_refused_rather_than_quietly_left_open() {
    SpringTemplateEngine withoutTheSpringOne = new SpringTemplateEngine();
    withoutTheSpringOne.setDialect(new StandardDialect());

    assertThatThrownBy(
            () -> handedOver.postProcessAfterInitialization(withoutTheSpringOne, "templateEngine"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("carries no SpringStandardDialect")
        .hasMessageContaining("back to reading any bean it names")
        .hasMessageContaining("StandardDialect");
  }

  @Test
  void every_other_dialect_the_engine_was_given_is_kept() {
    StandardDialect anotherOne = new StandardDialect();

    Set<IDialect> left =
        TheEngineTheScreensAreRenderedBy.withoutTheOneThatHandsBeans(
            new LinkedHashSet<>(Set.of(anotherOne, new SpringStandardDialect())));

    assertThat(left).hasSize(2).contains(anotherOne);
    assertThat(left).noneMatch(dialect -> dialect.getClass().equals(SpringStandardDialect.class));
  }

  private static SpringTemplateEngine asBootBuildsIt() {
    SpringTemplateEngine engine = new SpringTemplateEngine();
    StringTemplateResolver theMarkupItself = new StringTemplateResolver();
    theMarkupItself.setCacheable(false);
    engine.setTemplateResolver(theMarkupItself);
    return engine;
  }

  private static Context aPage() {
    StaticApplicationContext application = new StaticApplicationContext();
    application.refresh();
    application
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "what a datasource is configured with",
                Map.of("spring.datasource.password", THE_SECRET)));
    Context page = new Context();
    page.setVariable(
        ATemplateReadsOnlyItsModel.THE_CONTEXT_THE_ENGINE_EVALUATES_IN,
        new ThymeleafEvaluationContext(application, null));
    return page;
  }

  private static Context aPageNaming(String provider) {
    Context page = aPage();
    page.setVariable("provider", provider);
    return page;
  }
}
