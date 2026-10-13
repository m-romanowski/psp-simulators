package dev.marcinromanowski.pspsim.platform;

import dev.marcinromanowski.pspsim.kernel.ThisSimulatorsOwnAddresses;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class BareHostNameConfiguration implements WebMvcConfigurer {

  public static final String THE_ADDRESS_A_TESTER_TYPES =
      ThisSimulatorsOwnAddresses.THE_ADDRESS_A_TESTER_TYPES;

  public static final String THE_SCREEN_IT_OPENS = ThisSimulatorsOwnAddresses.THE_SCREENS;

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry
        .addRedirectViewController(THE_ADDRESS_A_TESTER_TYPES, THE_SCREEN_IT_OPENS)
        .setKeepQueryParams(true);
  }
}
