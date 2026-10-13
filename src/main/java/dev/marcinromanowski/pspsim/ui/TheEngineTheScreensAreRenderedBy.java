package dev.marcinromanowski.pspsim.ui;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;

@Component
public final class TheEngineTheScreensAreRenderedBy implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(Object bean, String name) {
    if (bean instanceof SpringTemplateEngine engine) {
      handItTheDialectThatHandsNoBean(engine);
    }
    return bean;
  }

  static void handItTheDialectThatHandsNoBean(SpringTemplateEngine engine) {
    boolean compiled = engine.getEnableSpringELCompiler();
    boolean markersFirst = engine.getRenderHiddenMarkersBeforeCheckboxes();
    engine.setDialects(withoutTheOneThatHandsBeans(engine.getDialects()));
    engine.setEnableSpringELCompiler(compiled);
    engine.setRenderHiddenMarkersBeforeCheckboxes(markersFirst);
  }

  static Set<IDialect> withoutTheOneThatHandsBeans(Set<IDialect> dialects) {
    Set<IDialect> reachingNoBean = new LinkedHashSet<>();
    boolean found = false;
    for (IDialect dialect : dialects) {
      if (dialect instanceof SpringStandardDialect) {
        reachingNoBean.add(ATemplateReadsOnlyItsModel.asTheDialectOfTheEngine());
        found = true;
        continue;
      }
      reachingNoBean.add(dialect);
    }
    if (!found) {
      throw new IllegalStateException(
          "the template engine of this application carries no "
              + SpringStandardDialect.class.getSimpleName()
              + ", and this simulator replaces that dialect with one that hands a page no bean of"
              + " this application, so there is nothing here to replace and every template is back"
              + " to reading any bean it names. The engine is built by Spring Boot and the dialect"
              + " comes with it, so this is a framework this build has not been held against:"
              + " the dialects it handed over are "
              + dialects
              + ". Restore the boundary before serving a screen.");
    }
    return Set.copyOf(reachingNoBean);
  }
}
