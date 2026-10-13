package dev.marcinromanowski.pspsim.platform;

import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public final class AsyncRequestSupport implements WebMvcConfigurer {

  private final FaultDelayCeiling ceiling;

  public AsyncRequestSupport(FaultDelayCeiling ceiling) {
    this.ceiling = ceiling;
  }

  public long defaultTimeoutMillis() {
    return ceiling.deliveryDeadline().toMillis();
  }

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setDefaultTimeout(defaultTimeoutMillis());
  }
}
