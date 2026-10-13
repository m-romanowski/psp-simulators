package dev.marcinromanowski.pspsim.kernel.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

final class WhatTheLogRemembers extends AppenderBase<ILoggingEvent> {

  private final List<ILoggingEvent> remembered = new CopyOnWriteArrayList<>();
  private final Logger watched;

  private WhatTheLogRemembers(Logger watched) {
    this.watched = watched;
  }

  static WhatTheLogRemembers attachedTo(String logger) {
    WhatTheLogRemembers reading = new WhatTheLogRemembers((Logger) LoggerFactory.getLogger(logger));
    reading.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    reading.start();
    reading.watched.addAppender(reading);
    return reading;
  }

  void stopReading() {
    watched.detachAppender(this);
    stop();
  }

  List<String> saidAt(Level level) {
    List<String> said = new ArrayList<>();
    for (ILoggingEvent event : remembered) {
      if (level.equals(event.getLevel())) {
        said.add(event.getFormattedMessage());
      }
    }
    return List.copyOf(said);
  }

  List<String> theCausesItReported() {
    List<String> said = new ArrayList<>();
    for (ILoggingEvent event : remembered) {
      if (!Level.ERROR.equals(event.getLevel())) {
        continue;
      }
      for (IThrowableProxy raised = event.getThrowableProxy();
          raised != null;
          raised = raised.getCause()) {
        said.add(raised.getClassName() + ": " + raised.getMessage());
      }
    }
    return List.copyOf(said);
  }

  @Override
  protected void append(ILoggingEvent event) {
    remembered.add(event);
  }
}
