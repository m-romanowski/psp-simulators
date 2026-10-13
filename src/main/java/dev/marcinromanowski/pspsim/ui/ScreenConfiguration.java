package dev.marcinromanowski.pspsim.ui;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ScreenConfiguration implements WebMvcConfigurer {

  private final TheShellOnEveryScreen shell;

  public ScreenConfiguration(TheShellOnEveryScreen shell) {
    this.shell = shell;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(shell);
  }
}
